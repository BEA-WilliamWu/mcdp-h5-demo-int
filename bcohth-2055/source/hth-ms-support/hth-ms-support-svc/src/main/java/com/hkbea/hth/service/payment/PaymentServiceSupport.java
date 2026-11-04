package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hkbea.common.constant.CommonConstant;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.BalanceDetail;
import com.hkbea.hth.model.BatchAccountResult;
import com.hkbea.hth.model.BatchEnquiryResponse;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.GenericBalanceParam;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.TxnAmount;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared payment constants and validation helpers.
 */
public final class PaymentServiceSupport {

    static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    private PaymentServiceSupport() {
    }

    /**
     * Strict BEA internal account check: non-blank, digits only, exactly 14-15
     * chars and starting with "015" (internal member id). Used for debtor accounts.
     */
    static void validateLocalAccountNumber(Logger log, String accountNumber,
                                           int missingCode, int formatCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw logAndCreateException(log, missingCode);
        }
        if (!NUMERIC_PATTERN.matcher(accountNumber).matches()) {
            throw logAndCreateException(log, formatCode);
        }
        if (accountNumber.length() != 14 && accountNumber.length() != 15) {
            throw logAndCreateException(log, formatCode);
        }
        if (!accountNumber.startsWith(PaymentConstants.MEMBER_CODE_015)) {
            throw logAndCreateException(log, formatCode);
        }
    }

    /**
     * Generic account number check: non-blank and matching
     * {@link CommonConstant#ACCOUNT_NUMBER_PATTERN}. Used for creditor accounts.
     */
    static void validateAccountNumber(Logger log, String accountNumber,
                                      int missingCode, int formatCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw logAndCreateException(log, missingCode);
        }
        if (!CommonConstant.ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()) {
            throw logAndCreateException(log, formatCode);
        }
    }

    /**
     * Non-null currency that must be a known {@link Currency} enum value.
     */
    static void validateCurrency(Logger log, Currency currency, int errorCode) {
        if (currency == null) {
            throw logAndCreateException(log, errorCode);
        }
        try {
            Currency.fromValue(currency.toString());
        } catch (IllegalArgumentException e) {
            throw logAndCreateException(log, errorCode, e);
        }
    }

    /**
     * Creditor name for submission; falls back to "N/A" when creditor or its name
     * is absent so downstream builders never see null.
     */
    static String accountNameOrFallback(LocalPayment localPayment) {
        Creditor creditor = localPayment.getCreditor();
        if (creditor == null) {
            return PaymentConstants.ACCOUNT_NAME_NA;
        }
        String name = creditor.getCreditorName();
        return StringUtils.isNotBlank(name) ? name : PaymentConstants.ACCOUNT_NAME_NA;
    }

    /**
     * Validates a payment amount shared by all payment types: positive, within
     * {@link CommonConstant#MAX_AMOUNT}, and with at most 2 decimals (integer
     * amounts only for JPY).
     */
    static void validateAmount(Logger log, BigDecimal amount, Currency currency, int errorCode) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(CommonConstant.MAX_AMOUNT) > 0) {
            throw logAndCreateException(log, errorCode);
        }
        if ("JPY".equalsIgnoreCase(currency.toString())) {
            if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw logAndCreateException(log, errorCode);
            }
        } else if (amount.stripTrailingZeros().scale() > 2) {
            throw logAndCreateException(log, errorCode);
        }
    }

    /**
     * Shared txnAmount validation for CHATS/On-Us: presence, payment currency
     * and the common amount rules.
     */
    static void validateTxnAmount(Logger log, TxnAmount txnAmount) {
        if (txnAmount == null) {
            throw logAndCreateException(log, HTHSupportErrorMapping.TXN_AMOUNT_MISSING);
        }
        validateCurrency(log, txnAmount.getCurrency(),
                HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID);
        validateAmount(log, txnAmount.getAmount(), txnAmount.getCurrency(),
                HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID);
    }

    /**
     * Creditor name check: non-blank and matching
     * {@link CommonConstant#ACCOUNT_NAME_PATTERN}.
     */
    static void validateAccountName(Logger log, String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw logAndCreateException(log, HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_MISSING);
        }
        if (!CommonConstant.ACCOUNT_NAME_PATTERN.matcher(accountName).matches()) {
            throw logAndCreateException(log, HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_INVALID);
        }
    }

    /**
     * Enquires the generic balance and checks the available balance covers
     * {@code requiredAmount} in the given currency.
     */
    static boolean hasEnoughBalance(AccountApi accountApi, Logger log,
                                    String accountNumber, String currency,
                                    BigDecimal requiredAmount) {
        GenericBalanceParam param = new GenericBalanceParam();
        param.setAccountNumber(accountNumber);
        param.setCurrencies(List.of(Currency.fromValue(currency)));
        log.info("BatchEnquiry：{}", List.of(param));
        BatchEnquiryResponse response = accountApi.enquireGenericBalance(List.of(param));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return false;
        }
        BatchAccountResult result = response.getResults().getFirst();
        if (!"SUCCESS".equals(result.getQueryStatus().value()) || result.getBalances() == null) {
            return false;
        }
        for (BalanceDetail balance : result.getBalances()) {
            if (currency.equalsIgnoreCase(balance.getCurrency().toString())) {
                return balance.getAvailableBalance().compareTo(requiredAmount) >= 0;
            }
        }
        return false;
    }

    /**
     * Throws {@code INSUFFICIENT_BALANCE} when {@link #hasEnoughBalance} reports
     * the available balance does not cover the payment amount.
     */
    static void validateSufficientBalance(AccountApi accountApi, Logger log,
                                          String accountNumber, String currency,
                                          BigDecimal amount) {
        if (!hasEnoughBalance(accountApi, log, accountNumber, currency, amount)) {
            throw new ParameterException(HTHSupportErrorMapping.INSUFFICIENT_FROM_ACCOUNT_BALANCE);
        }
    }

    /**
     * Submits the routing request (the exact object validated by smart routing)
     * to IPE and applies the {@link TransferModelApiResponse} directly onto
     * {@code payment}. Returns the raw response payload for persistence, or
     * {@code null} when the submission failed (payment left as PROCESSING).
     */
    static TransferPaymentResultQueryResponse submitRoutingRequest(
            PayemtRoutApi payemtRoutApi, Logger log,
            BatchRoutingCreditTransferRequest routingRequest, Payment payment,
            String transactionId, PaymentResponseSupport.RejectMessagePolicy policy) {
        try {
            TransferModelApiResponse response =
                    PaymentRoutingSubmitter.submit(payemtRoutApi, routingRequest);
            return PaymentResponseSupport.apply(response, payment, transactionId, policy);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            payment.setStatus(Payment.StatusEnum.PROCESSING);
            return null;
        }
    }

    /**
     * The limit API keys customers by partyId, so the debtor account is cloned
     * with {@code accountNumber = partyId} purely for limit calls.
     */
    public static BeaAccount limitAccount(BeaAccount source, String partyId) {
        BeaAccount account = new BeaAccount();
        account.setId(source.getId());
        account.setType(source.getType());
        account.setAccountNumber(partyId);
        account.setAccountType(source.getAccountType());
        account.setCurrency(source.getCurrency());
        return account;
    }

    /**
     * Post-submission sparse update of a pending HTH_PAYMENT_PAYOUT row: status
     * and reject message follow the parsed payment response; IPE fields come from
     * the submission result (null values are skipped by MyBatis-Plus, keeping the
     * pending state). Shared by CHATS/FPS/TT.
     */
    static void updatePayoutRecord(HthPaymentPayoutMapper mapper, Logger log, String paymentNetwork,
                                   String id, Payment resp, TransferPaymentResultQueryResponse result) {
        try {
            PaymentPayoutRecord update = new PaymentPayoutRecord();
            update.setId(id);
            update.setStatus(resp.getStatus() != null
                    ? resp.getStatus().value()
                    : Payment.StatusEnum.PROCESSING.value());
            update.setRejectMessage(resp.getRejectMessage());
            update.setIpeStatus(result != null && result.getStatus() != null
                    ? result.getStatus().value() : null);
            update.setIpeStatusReason(result != null ? result.getStatusReason() : null);
            update.setIpeRefNo(result != null ? result.getIpeReferenceId() : null);
            if ("CHATS".equalsIgnoreCase(paymentNetwork) && result != null) {
                update.setIpeTerminal(result.getTerminal());
            }
            update.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            update.setLastUpdatedDate(LocalDateTime.now());
            if ("CHATS".equalsIgnoreCase(paymentNetwork)) {
                // An enquiry may finish while submission is still returning.
                // Only replace the untouched initial row, never a newer result.
                int updated = mapper.update(update, new LambdaUpdateWrapper<PaymentPayoutRecord>()
                        .eq(PaymentPayoutRecord::getId, id)
                        .eq(PaymentPayoutRecord::getStatus, Payment.StatusEnum.PROCESSING.value())
                        .isNull(PaymentPayoutRecord::getIpeStatus)
                        .isNull(PaymentPayoutRecord::getIpeTerminal));
                if (updated == 0) {
                    log.info("CHATS submission update skipped; initial record no longer matches, id={}", id);
                    return;
                }
            } else {
                mapper.updateById(update);
            }
            log.info("{} payout record updated, id={}, status={}",
                    paymentNetwork, id, update.getStatus());
        } catch (Exception e) {
            log.error("Failed to update {} payout record, id={}, error={}",
                    paymentNetwork, id, e.getMessage(), e);
        }
    }

    /**
     * Post-submission sparse update of a pending HTH_PAYMENT_INTERNAL row: status
     * and reject message follow the parsed payment response; IPE fields come from
     * the submission result (null values are skipped by MyBatis-Plus, keeping the
     * pending state). Shared by DR/On-Us.
     */
    static void updateInternalRecord(PaymentInternalMapper mapper, Logger log, String paymentType,
                                     String id, Payment resp, TransferPaymentResultQueryResponse result) {
        try {
            PaymentInternal update = new PaymentInternal();
            update.setId(id);
            update.setStatus(resp.getStatus() != null
                    ? resp.getStatus().value()
                    : Payment.StatusEnum.PROCESSING.value());
            update.setRejectMessage(resp.getRejectMessage());
            update.setIpeStatus(result != null && result.getStatus() != null
                    ? result.getStatus().value() : null);
            update.setIpeStatusReason(result != null ? result.getStatusReason() : null);
            update.setIpeRefNo(result != null ? result.getIpeReferenceId() : null);
            update.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            update.setLastUpdatedDate(new Date());
            mapper.updateById(update);
            log.info("{} payment internal record updated, id={}, status={}",
                    paymentType, id, update.getStatus());
        } catch (Exception e) {
            log.error("Failed to update {} payment internal record, id={}, error={}",
                    paymentType, id, e.getMessage(), e);
        }
    }

    /**
     * Joins validated paymentDetails segments with "; " for persistence.
     */
    static String joinPaymentDetails(List<String> paymentDetails) {
        if (paymentDetails == null || paymentDetails.isEmpty()) {
            return null;
        }
        return String.join("; ", paymentDetails);
    }

    /**
     * Formats a 14+ char account number as xxx-xxx-xx-xxxxx-x for display;
     * shorter values are returned unchanged.
     */
    static String formatPayerAccountNoFmt(String externalAccountNo) {
        String account = externalAccountNo == null ? "" : externalAccountNo.trim();
        if (account.length() < 14) {
            return account;
        }
        return account.substring(0, 3) + "-" + account.substring(3, 6) + "-" + account.substring(6, 8)
                + "-" + account.substring(8, 13) + "-" + account.substring(13, 14);
    }

    /** Logs the failure and creates a {@link ParameterException}. */
    static ParameterException logAndCreateException(Logger log, int errorCode) {
        log.error("Payment validation failed. Error Code: {}", errorCode);
        return new ParameterException(errorCode);
    }

    /** Same as above, additionally logging the underlying cause. */
    static ParameterException logAndCreateException(Logger log, int errorCode, Throwable cause) {
        log.error("Payment validation failed. Error Code: {}. Reason: {}", errorCode, cause.getMessage(), cause);
        return new ParameterException(errorCode);
    }
}
