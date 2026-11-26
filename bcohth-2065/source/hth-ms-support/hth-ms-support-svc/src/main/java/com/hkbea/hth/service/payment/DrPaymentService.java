package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.DirectRemittance;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.utils.CurrencyUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Direct Remittance (DR) overseas payment (CNY only).
 * Two-phase FPS-style persistence into HTH_PAYMENT_INTERNAL
 * (PaymentInternal entity, shared with INTERNAL/FX, paymentType="DR",
 * pending insert before the IPE submission + result update after).
 * NO chargesOption/accountName validation, NO Scameter screening,
 * NO limit handling (EBK deducted).
 */
@Service
public class DrPaymentService extends OverseasPaymentService {

    // DR supported currencies：HKD, USD, CNY
    private static final Set<Currency> DR_SUPPORTED_CURRENCIES = EnumSet.of(Currency.HKD, Currency.USD,Currency.CNY);
    /**
     * China a/c type code：'01' – Corporate（hardcode value）
     */
    private static final String DR_CHINA_AC_TYPE_CORPORATE = "01";

    private static final String DR_PAYOUT_PAYMENT_TYPE = "DR";

    @Override
    protected String routingPaymentMethod() {
        return DR_PAYOUT_PAYMENT_TYPE;
    }

    /**
     * DR persistence phase 1: inserts the pending HTH_PAYMENT_INTERNAL row
     * (basic info; STATUS/IPE_STATUS/IPE_REF_NO stay null) before the IPE
     * submission; returns the row id for the post-submission update.
     * Best-effort (log-only on failure).
     */
    @Override
    protected String insertPendingRecord(LocalPayment localPayment,
                                         BatchRoutingCreditTransferRequest routingRequest,
                                         Payment payment) {
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            PaymentInternal entity = new PaymentInternal();
            entity.setId(id);
            entity.setPartyId(localPayment.getPartyId());
            entity.setPartyName(PaymentConstants.MOCK_PARTY_NAME);
            entity.setPaymentType(DR_PAYOUT_PAYMENT_TYPE);
            entity.setPaymentId(payment.getTxnRef());
            entity.setCustomerTransactionId(localPayment.getTransactionID());
            entity.setExecutionDate(localPayment.getSettlementDate());
            entity.setDebitAccountId(localPayment.getFromAccount().getAccountNumber());
            entity.setDebitAccountCur(localPayment.getFromAccount().getCurrency().toString());
            entity.setDebitAccountType(localPayment.getFromAccount().getAccountType().value());
            entity.setInstdAmount(localPayment.getTxnAmount().getAmount());
            entity.setProductCode(PaymentRoutingPayload.debtorProductCode(routingRequest));
            if (localPayment.getToAccount() != null) {
                entity.setCreditAccountId(localPayment.getToAccount().getAccountNumber());
                entity.setCreditAccountCur(localPayment.getToAccount().getCurrency().toString());
                entity.setCreditAccountName(accountNameOrFallback(localPayment));
            }
            entity.setHkdEquivalentAmount(localPayment.getTxnAmount().getAmount());
            entity.setSvcCur(localPayment.getTxnAmount().getCurrency().toString());
            entity.setSvcAmount(PaymentConstants.MOCK_SVC_AMOUNT);
            entity.setStatus(null);
            entity.setIpeStatus(null);
            entity.setIpeRefNo(null);
            entity.setCreationDate(LocalDateTime.now());
            entity.setCreatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedDate(new Date());
            entity.setMessageId(headers.getMessageId());
            entity.setAdcSuspiciousInd(localPayment.getSuspiciousInd());
            entity.setEndToEndId(PaymentModelConvert.EndToEndId(localPayment));
            entity.setPaymentDate(new Date());
            entity.setDebitInternalAccount(localPayment.getFromAccount().getInternalAccountNumber());
            paymentInternalMapper.insert(entity);
            log.info("DR pending payment internal record inserted, customerTransactionId={}",
                    entity.getCustomerTransactionId());
        } catch (Exception e) {
            log.error("Failed to insert DR pending payment internal record, customerTransactionId={}, error={}",
                    localPayment.getTransactionID(), e.getMessage(), e);
        }
        return id;
    }

    /**
     * DR persistence phase 2: sparse update of the pending row with the IPE
     * outcome (null values are skipped by MyBatis-Plus). Best-effort
     * (log-only on failure).
     */
    @Override
    protected void persistPaymentRecord(String recordId,
                                        LocalPayment localPayment,
                                        BatchRoutingCreditTransferRequest routingRequest,
                                        Payment payment,
                                        TransferPaymentResultQueryResponse result) {
        PaymentServiceSupport.updateInternalRecord(paymentInternalMapper, log,
                DR_PAYOUT_PAYMENT_TYPE, recordId, payment, result, localPayment);
    }

    /**
     * DR-specific checks on top of the shared overseas validation: payee info
     * presence and supported currencies. IPE checks balances.
     */
    @Override
    protected void validateSpecific(LocalPayment payment) {
        validateDrToAccount(payment);
        validateDrCurrency(payment);
    }

    /**
     * DR payee info presence: toAccount, creditor name and account number.
     */
    private void validateDrToAccount(LocalPayment payment) {
        PaymentToAccount toAccount = payment.getToAccount();
        if (toAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING);
        }
        if (StringUtils.isBlank(payment.getCreditor().getCreditorName())) {
            throw logAndCreateException(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID);
        }
        if (StringUtils.isBlank(toAccount.getAccountNumber())) {
            throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING);
        }
    }

    /**
     * DR currency rule: debit and payment currencies must be in
     * DR_SUPPORTED_CURRENCIES (HKD/USD/CNY).
     */
    private void validateDrCurrency(LocalPayment payment) {
        Currency fromCurrency = CurrencyUtils.parseLenient(payment.getFromAccount().getCurrency());
        Currency txnCurrency = payment.getTxnAmount().getCurrency();
        Currency toCurrency = payment.getToAccount().getCurrency();

        if (!DR_SUPPORTED_CURRENCIES.contains(fromCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.DR_DEBIT_CURRENCY_NOT_SUPPORTED);
        }

        if (!DR_SUPPORTED_CURRENCIES.contains(txnCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.DR_PAYMENT_CURRENCY_NOT_SUPPORTED);
        }
        if (fromCurrency != txnCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES);
        }
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    /**
     * Fills DR-specific routing blocks: blank legacy codes, payee account name/BIC
     * and the direct-remittance detail (corporate China account).
     */
    @Override
    protected void enrichRoutingDetail(RoutingTransactionDetailRequest txn, LocalPayment localPayment) {
        clearLegacyCodes(txn.getSupplementaryData());

        // DR service charge is always zero and the fee code is sent blank.
        txn.setCharges(PaymentRoutingPayload.charges(serviceChargeAmount(), null, null, ""));

//        String creditorName = localPayment.getCreditor().getCreditorName();
//        txn.setCreditorAccount(PaymentRoutingPayload.creditorAccount(creditorName, null, null, null, null, null, null));

//        String bicfi = localPayment.getCreditorAgent() != null
//                && localPayment.getCreditorAgent().getFinancialInstitutionId() != null
//                ? localPayment.getCreditorAgent().getFinancialInstitutionId().getBicfi() : null;
//        txn.setCreditorAgent(PaymentRoutingPayload.creditorAgent(bicfi, null, null, null, null, null, " "));

        PaymentToAccount toAccount = localPayment.getToAccount();
        Creditor creditor = localPayment.getCreditor();

        DirectRemittance directRemittance = new DirectRemittance();
        String chinaAcCcy = localPayment.getTxnAmount().getCurrency().toString();
        directRemittance.setChinaAcCcy(DirectRemittance.ChinaAcCcyEnum.fromValue(chinaAcCcy));
        directRemittance.setChinaAcType(DR_CHINA_AC_TYPE_CORPORATE);
        if (creditor != null) {
            directRemittance.setChinaAcno(toAccount.getAccountNumber());
            directRemittance.setChinaAccEnglishName(PaymentRoutingPayload.ebkFixedWidth(creditor.getCreditorName(), 34));
        }
        directRemittance.setChinaAccChineseName(" ".repeat(62));
        txn.getSupplementaryData().setDirectRemittance(directRemittance);
        String payeeId = internalPayeeMapper.selectInternalPayeeId(
                localPayment.getPartyId(), localPayment.getToAccount().getAccountNumber());
        CustomerNotificationInformRequest notification = paymentCommonService.buildCustomerNotificationInfo(
                localPayment, PaymentConstants.NOTIFICATION_TYPE_EMAIL_SMS, payeeId);
        txn.setNotification(PaymentRoutingPayload.notification(notification));
    }

    /** DR service charge is always zero. */
    @Override
    protected java.math.BigDecimal serviceChargeAmount() {
        return java.math.BigDecimal.ZERO;
    }

    /**
     * DR sends blank values for the FPS-era hardcodes the shared block carries.
     */
    private static void clearLegacyCodes(com.hkbea.hth.ipe.model.SupplementaryData supplementaryData) {
        if (supplementaryData == null) {
            return;
        }
        supplementaryData.setPaymentSource("");
        supplementaryData.setEnquiryType("");
        supplementaryData.setCostCenterCode("");
        supplementaryData.setAlnovaLimitCheckIndicator("");
        supplementaryData.setApplicationTelNo("");
    }
}
