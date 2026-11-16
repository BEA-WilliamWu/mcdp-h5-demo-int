package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ebk.constant.PaymentLimitApiConstants;
import com.hkbea.hth.ebk.model.*;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.SupplementaryData;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.*;
import com.hkbea.hth.model.*;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * FPS Processing Flow:
 * 1. Fine-grained entitlement check
 *    - Invoke common method.
 *    - Reject the request immediately if the check fails.
 * 2. FPS parameter validation
 *    - Validate FromAcc, TxnAmount, ToAcc, Currency, etc.
 *    - Reject the request immediately if validation fails.
 * 3. ADC high-risk validation
 *    - Invoke API.
 *    - Reject the request immediately if validation fails.
 * 4. Limit reservation
 *    - Invoke API.
 *    - Reject the request immediately if reservation fails.
 * 5. Insert HTH_PAYMENT_PAYOUT record
 *    - Set IPE-related parameters to null.
 * 6. Enrich the smart-routing accepted request and request IPE
 * 7. Update transaction record.
 * 8. Confirm or cancel the reserved limit.
 * 9. FMO processing.
 * 10. MTB processing.
 * 11. Write audit log.
 * 12. Build and return the response.
 */
@Service
public class FpsPaymentService {
    private static final Logger log = LoggerFactory.getLogger(FpsPaymentService.class);

    // FPS supported currencies：HKD, CNY
    private static final Set<Currency> FPS_SUPPORTED_CURRENCIES = EnumSet.of(Currency.HKD, Currency.CNY);

    private static final String FPS_NETWORK = "FPS";
    private static final String FPS_PAYMENT_SOURCE = "CCB";
    private static final String FPS_ENQUIRY_TYPE_ACCOUNT = "FEE";
    private static final String FPS_COST_CENTER_CODE = "0101459";
    private static final String FPS_PAYEE_ID_TYPE_ACCOUNT = "BBAN";
    private static final String FPS_ALNOVA_LIMIT_IND = "Y";
    private static final String FPS_PURPOSE_PROPRIETARY = "CXBSNS";
    private static final int BANK_CODE_LENGTH = 3;
    private static final int FPS_TX_REF_MAX_LENGTH = 40;


    @Autowired
    private PayemtRoutApi payemtRoutApi;
    @Autowired
    private ScameterValidatorUtil scameterValidatorUtil;
    @Autowired
    private FpsBankMapper fpsBankMapper;
    @Autowired
    private InternalPayeeMapper internalPayeeMapper;
    @Autowired
    private PartyAccountMapper partyAccountMapper;
    @Autowired
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Autowired
    private PartyPreferenceMapper partyPreferenceMapper;
    @Autowired
    private CommonHeader headers;
    @Autowired
    private TransferLimitService transferLimitService;
    @Autowired
    private PaymentCommonService paymentCommonService;
    @Autowired
    private CacheHelperUtils cacheHelperUtils;

    /**
     * FPS payment flow: validate, enrich the routing request accepted by
     * smart-routing in place, then submit wrapped in the FPS transfer-limit
     * update/release cycle.
     */
    public Payment processPayment(LocalPayment localPayment,
                                  BatchRoutingCreditTransferRequest routingRequest) {
        if (localPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        PaymentValidationSupport.validateFpsRules(localPayment);
        PaymentValidationSupport.useDebitCurrencyForCreditorAccount(localPayment);
        validateFps(localPayment);
        scameterValidatorUtil.checkFps(localPayment);
        isRegisteredFpsPayee(localPayment);
        applyFpsRoutingDetails(routingRequest, localPayment);
        return processWithFasterPaymentLimit(localPayment, routingRequest);
    }

    /**
     * Writes the FPS-specific blocks onto the routing request that was already
     * validated by smart-routing. Field values mirror the previous
     * persistence-request mapping (proxy payee by account number, CCB hardcodes).
     */
    private void applyFpsRoutingDetails(BatchRoutingCreditTransferRequest routingRequest,
                                        LocalPayment localPayment) {
        PaymentRoutingPayload.applyTypeEnvelope(routingRequest,
                PaymentConstants.FPS_CHANNEL_REFERENCE, FPS_NETWORK);
        var txn = PaymentRoutingPayload.firstTransaction(routingRequest);

        String debitCurrency = localPayment.getTxnAmount().getCurrency().toString();
        String debitInternalAccNo = AccountUtils.ext2intAccNo(
                localPayment.getFromAccount().getAccountNumber(),
                localPayment.getFromAccount().getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(
                debitInternalAccNo, localPayment.getPartyId());
        String payerName = partyPreferenceMapper.selectCustomerNameByPartyId(localPayment.getPartyId(),localPayment.getFromAccount().getAccountNumber());

        txn.setRemittanceInformation(PaymentRoutingPayload.remittance(
                PaymentConstants.BG3_CUSTOMER_REMARKS_FPS, localPayment.getPaymentDetails()));

        com.hkbea.hth.ipe.model.Purpose purpose = new com.hkbea.hth.ipe.model.Purpose();
        purpose.setProprietary(FPS_PURPOSE_PROPRIETARY);
        txn.setPurpose(purpose);

        String feeCode = "HKD".equals(debitCurrency)
                ? PaymentConstants.FEE_CODE_FPS_HKD : PaymentConstants.FEE_CODE_FPS_OTHER;
        txn.setCharges(PaymentRoutingPayload.charges(null, debitCurrency, null, feeCode));

        txn.setDebtor(PaymentRoutingPayload.corporateDebtor(payerName));
        txn.setDebtorAccount(PaymentRoutingPayload.debtorAccount(
                localPayment.getFromAccount().getAccountNumber(), debitInternalAccNo, null,
                PaymentConstants.PAYER_ACCOUNT_TYPE_CSA,
                localPayment.getFromAccount().getCurrency(), null, productCode));

        String creditorName = localPayment.getCreditor().getCreditorName();
        txn.setCreditor(PaymentRoutingPayload.creditor(
                com.hkbea.hth.ipe.model.Creditor.TypeEnum.ACCOUNT, creditorName,
                localPayment.getToAccount().getMobileNumber(),
                localPayment.getToAccount().getEmailAddress(),
                localPayment.getCreditor().getPostalAddress().getAddressLine1(),
                localPayment.getCreditor().getPostalAddress().getAddressLine2(),
                localPayment.getCreditor().getPostalAddress().getTownName(),
                localPayment.getCreditor().getPostalAddress().getCountry()));
        txn.setCreditorAccount(PaymentRoutingPayload.creditorAccount(
                creditorName, localPayment.getToAccount().getAccountNumber(),
                localPayment.getTxnAmount().getCurrency().toString(), null, FPS_PAYEE_ID_TYPE_ACCOUNT,
                localPayment.getCreditor().getPostalAddress().getCountry(), null));

        String memberId = localPayment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId();
        txn.setCreditorAgent(PaymentRoutingPayload.creditorAgent(null, memberId, null,
                localPayment.getCreditor().getPostalAddress().getAddressLine1(),
                localPayment.getCreditor().getPostalAddress().getAddressLine2(), null, null));

        SupplementaryData supplementaryData = new SupplementaryData();
        //BCO hardcode CCB
        supplementaryData.setPaymentSource(FPS_PAYMENT_SOURCE);
        //ACCTNO-->FEE , EMAIL-->TGT, FPSID-->ALL, MOBILE-->TGT
        supplementaryData.setEnquiryType(FPS_ENQUIRY_TYPE_ACCOUNT);
        //BCO hardcode 0101459
        supplementaryData.setCostCenterCode(FPS_COST_CENTER_CODE);
        //BCO hardcode Y
        supplementaryData.setAlnovaLimitCheckIndicator(FPS_ALNOVA_LIMIT_IND);
        supplementaryData.setDebitInternalAccount(debitInternalAccNo);
        supplementaryData.setProductType(productCode);
        txn.setSupplementaryData(supplementaryData);

        CustomerNotificationInformRequest notification = paymentCommonService.buildCustomerNotificationInfo(
                localPayment, PaymentConstants.NOTIFICATION_TYPE_EMAIL_SMS, localPayment.getPayeeId());
        txn.setNotification(PaymentRoutingPayload.notification(notification));
    }

    /**
     * Submits the FPS payment inside the FPU01 limit cycle:
     * 1. deduct the FPS transfer limit (identified by partyId, not account);
     * 2. submit to IPE and build the response;
     * 3. release the limit when rejected/failed, confirm otherwise.
     * Abort with {@code LIMIT_DEDUCTION_FAILED} when the deduction call fails.
     */
    private Payment processWithFasterPaymentLimit(LocalPayment localPayment,
                                                  BatchRoutingCreditTransferRequest routingRequest) {
        // The limit API identifies the customer by partyId, while the payment
        // contract and persistence must keep the debtor account number.
        BeaAccount limitAccount = PaymentServiceSupport.limitAccount(
                localPayment.getFromAccount(), localPayment.getPartyId());

        Fpu01Result fpu01Result = transferLimitService.updateFasterPaymentLimit(
                buildFpsUpdateLimit(localPayment), limitAccount);
        if (fpu01Result == null) {
            log.error("FPS limit deduction (FPU01) failed, payment aborted. Account={}",
                    limitAccount.getAccountNumber());
            throw logAndCreateException(HTHSupportErrorMapping.LIMIT_DEDUCTION_FAILED);
        }

        Payment payment = buildFpsResponse(localPayment, routingRequest);

        String actCode = isFpsLimitRelease(payment)
                ? PaymentLimitApiConstants.FPS_ACT_CODE_RELEASE
                : PaymentLimitApiConstants.FPS_ACT_CODE_CONFIRM;
        transferLimitService.releaseFasterPaymentLimit(
                buildFpsReleaseLimit(fpu01Result, localPayment, actCode),
                limitAccount);

        return payment;
    }

    /**
     * Whether the limit hold should be released (payment missing/unspecified or
     * outright rejected) rather than confirmed.
     */
    private boolean isFpsLimitRelease(Payment payment) {
        return payment == null
                || payment.getStatus() == null
                || payment.getStatus() == Payment.StatusEnum.REJECTED;
    }

    /**
     * Builds the FPU01 limit-update payload: debit/credit in payment currency,
     * FPS proxy identity and transaction metadata constants.
     */
    public Fpu01Limits buildFpsUpdateLimit(LocalPayment localPayment) {
        BeaAccount fromAccount = localPayment.getFromAccount();
        PaymentToAccount toAccount = localPayment.getToAccount();
        TxnAmount txnAmount = localPayment.getTxnAmount();

        Fpu01Limits limits = new Fpu01Limits();
        limits.setEbzwk00iFpu01RelCompCode(PaymentLimitApiConstants.COMP_CODE);
        limits.setEbzwk00iFpu01RelAcSeq(0);
        limits.setEbzwk00iFpu01RelAcStatus(0);
        limits.setEbzwk00iFpu01RelAcCcy(fromAccount.getCurrency());

        String payCcy = txnAmount.getCurrency().toString();
        BigDecimal amount = txnAmount.getAmount();
        limits.setEbzwk00iFpu01DbAmtCcy(payCcy);
        limits.setEbzwk00iFpu01DbAmt(amount);
        limits.setEbzwk00iFpu01CrAmtCcy(payCcy);
        limits.setEbzwk00iFpu01CrAmt(amount);

        String proxyType = resolveFpsProxyType(localPayment);
        limits.setEbzwk00iFpu01FpProxyIdTy(proxyType);
        limits.setEbzwk00iFpu01FpProxyId(resolveFpsProxyId(toAccount));
        limits.setEbzwk00iFpu01DesAcPrdCd(
                PaymentLimitApiConstants.FPS_PROXY_TYPE_REGISTERED.equals(proxyType)
                        ? PaymentLimitApiConstants.FPS_DES_AC_PROD_CD_REGISTERED
                        : PaymentLimitApiConstants.FPS_DES_AC_PROD_CD_DEFAULT);
        limits.setEbzwk00iFpu01TxnCrDrInd(PaymentLimitApiConstants.FPS_TX_CCY_IND);
        limits.setEbzwk00iFpu01TxnType(PaymentLimitApiConstants.FPS_TX_TYPE);
        limits.setEbzwk00iFpu01TxnNature(PaymentLimitApiConstants.FPS_TXN_NATURE);
        limits.setEbzwk00iFpu01Bea2BeaInd(PaymentLimitApiConstants.FPS_BEA_TO_BEA);
        return limits;
    }

    /**
     * Maps creditorAccount.accountIdentifier to the limit API's proxy type
     * (registered payee vs ad-hoc).
     */
    public String resolveFpsProxyType(LocalPayment localPayment) {
        PaymentToAccount.AccountIdentifierEnum identifier =
                localPayment.getToAccount().getAccountIdentifier();
        if (identifier == null) {
            return PaymentLimitApiConstants.FPS_PROXY_TYPE_ADHOC;
        }
        switch (identifier) {
            case _02:
                return PaymentLimitApiConstants.FPS_PROXY_TYPE_MOBILE;
            case _03:
                return PaymentLimitApiConstants.FPS_PROXY_TYPE_EMAIL;
            case _04:
                return PaymentLimitApiConstants.FPS_PROXY_TYPE_FPSID;
            case _01:
            default:
                return isRegisteredFpsPayee(localPayment)
                        ? PaymentLimitApiConstants.FPS_PROXY_TYPE_REGISTERED
                        : PaymentLimitApiConstants.FPS_PROXY_TYPE_ADHOC;
        }
    }

    /**
     * FPS proxy identifier sent to the limit API: account number for registered
     * payees, otherwise the proxy id field.
     */
    public String resolveFpsProxyId(PaymentToAccount toAccount) {
        PaymentToAccount.AccountIdentifierEnum identifier = toAccount.getAccountIdentifier();
        if (identifier == null) {
            return toAccount.getAccountNumber();
        }
        switch (identifier) {
            case _02:
                return toAccount.getMobileNumber();
            case _03:
                return toAccount.getEmailAddress();
            case _04:
                return toAccount.getFpsId();
            case _01:
            default:
                return toAccount.getAccountNumber();
        }
    }

    /**
     * Queries FPS registration (FPR01) to determine whether the creditor proxy
     * is a registered payee.
     */
    private boolean isRegisteredFpsPayee(LocalPayment localPayment) {
        String partyId = localPayment.getPartyId();
        String accountNumber = localPayment.getToAccount().getAccountNumber();
        String payeeId = internalPayeeMapper.selectDomesticPayeeId(partyId, accountNumber);
        localPayment.setPayeeId(payeeId);
        return accountNumber != null
                && payeeId != null;
    }

    public Fpr01Limits buildFpsReleaseLimit(Fpu01Result fpu01Result, LocalPayment localPayment,
                                             String actCode) {
        Fpr01Limits limits = new Fpr01Limits();
        limits.setEbzwk00iFpr01RelCompCode(fpu01Result.getEbzwk00oFpu01RelCompCode());
        limits.setEbzwk00iFpr01RelAcSeq(fpu01Result.getEbzwk00oFpu01RelAcSeq());
        limits.setEbzwk00iFpr01RelAcPrdCode(fpu01Result.getEbzwk00oFpu01RelAcPrdCode());
        limits.setEbzwk00iFpr01RelAcCcy(fpu01Result.getEbzwk00oFpu01RelAcCcy());
        limits.setEbzwk00iFpr01RelAcStatus(fpu01Result.getEbzwk00oFpu01RelAcStatus());
        limits.setEbzwk00iFpr01RelAcNo(fpu01Result.getEbzwk00oFpu01RelAcNo());
        limits.setEbzwk00iFpr01RelAcNoFmt(fpu01Result.getEbzwk00oFpu01RelAcNoFmt());
        limits.setEbzwk00iFpr01DbAmtCcy(fpu01Result.getEbzwk00oFpu01DbAmtCcy());
        limits.setEbzwk00iFpr01DbAmt(fpu01Result.getEbzwk00oFpu01DbAmt());
        limits.setEbzwk00iFpr01CrAmtCcy(fpu01Result.getEbzwk00oFpu01CrAmtCcy());
        limits.setEbzwk00iFpr01CrAmt(fpu01Result.getEbzwk00oFpu01CrAmt());
        limits.setEbzwk00iFpr01FpProxyIdTy(fpu01Result.getEbzwk00oFpu01FpProxyIdTy());
        limits.setEbzwk00iFpr01FpProxyId(fpu01Result.getEbzwk00oFpu01FpProxyId());
        limits.setEbzwk00iFpr01ReturnArea(toFpr01ReturnArea(fpu01Result.getEbzwk00oFpu01ReturnArea()));

        limits.setEbzwk00iFpr01ActCode(actCode);
        limits.setEbzwk00iFpr01TxnCrDrInd(PaymentLimitApiConstants.FPS_TX_CCY_IND);
        limits.setEbzwk00iFpr01TxnType(PaymentLimitApiConstants.FPS_TX_TYPE);
        limits.setEbzwk00iFpr01TxnNature(PaymentLimitApiConstants.FPS_TXN_NATURE);
        limits.setEbzwk00iFpr01Bea2BeaInd(PaymentLimitApiConstants.FPS_BEA_TO_BEA);
        limits.setEbzwk00iFpr01FpTxRef(buildFpTxRef(localPayment));
        // Preserve the current payload exactly: txReference is intentionally sent as blank.
        limits.setEbzwk00iFpr01TxReference("");
        limits.setEbzwk00iFpr01AgentId1(PaymentLimitApiConstants.FPS_AGENT_SPACES);
        limits.setEbzwk00iFpr01AgentId2(PaymentLimitApiConstants.FPS_AGENT_SPACES);
        limits.setEbzwk00iFpr01AgentId3(PaymentLimitApiConstants.FPS_AGENT_SPACES);
        limits.setEbzwk00iFpr01Filler01(PaymentLimitApiConstants.FPS_FILLER_104);
        return limits;
    }

    /** Converts the FPU01 response area into the FPR01 enquiry input shape. */
    private Fpr01ReturnArea toFpr01ReturnArea(Fpu01ReturnArea src) {
        Fpr01ReturnArea target = new Fpr01ReturnArea();
        if (src == null) {
            return target;
        }
        target.setEbzwk00iFpr01AmtHkd(src.getEbzwk00oFpu01AmtHkd());
        target.setEbzwk00iFpr01AltCode(src.getEbzwk00oFpu01AltCode());
        target.setEbzwk00iFpr01AltFlg(src.getEbzwk00oFpu01AltFlg());
        target.setEbzwk00iFpr01LimUpdDate(src.getEbzwk00oFpu01LimUpdDate());
        target.setEbzwk00iFpr01LimUpdTime(src.getEbzwk00oFpu01LimUpdTime());
        target.setEbzwk00iFpr01TxValDate(src.getEbzwk00oFpu01TxValDate());
        target.setEbzwk00iFpr01DesAcFlg(src.getEbzwk00oFpu01DesAcFlg());
        target.setEbzwk00iFpr01DesAcName(src.getEbzwk00oFpu01DesAcName());
        target.setEbzwk00iFpr01ExRate(src.getEbzwk00oFpu01ExRate());
        return target;
    }

    /** Builds the FPS transaction reference stored/sent with the request. */
    private String buildFpTxRef(LocalPayment localPayment) {
        List<String> details = localPayment.getPaymentDetails();
        if (details == null || details.isEmpty()) {
            return "";
        }
        String joined = String.join("", details);
        return joined.length() > FPS_TX_REF_MAX_LENGTH ? joined.substring(0, FPS_TX_REF_MAX_LENGTH) : joined;
    }

    /**
     * FPS validation orchestrator: debtor account, amount, creditor account,
     * currency consistency. IPE owns balance sufficiency checks.
     */
    private void validateFps(LocalPayment fpsPayment) {
        if (fpsPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateFromAccount(fpsPayment.getFromAccount(),fpsPayment.getPartyId());
        validateFpsTxnAmount(fpsPayment.getTxnAmount());
        validateFpsToAccount(fpsPayment);
        validateFpsCurrencyConsistency(fpsPayment);
    }

    /**
     * Debtor account checks: 14-15 digit "015" account, valid currency/type,
     * and product/status lookup (rejects closed accounts).
     */
    private void validateFromAccount(BeaAccount fromAccount, String partyId) {
        if (fromAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING);
        }

        validateLocalAccountNumber(fromAccount.getAccountNumber());
        validateCurrency(CurrencyUtils.parseLenient(fromAccount.getCurrency()),
                HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        if (fromAccount.getAccountType() == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING);
        }
        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                fromAccount.getAccountNumber(), fromAccount.getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, partyId);
        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                partyId, fromAccount.getAccountNumber(), productCode);
        if (partyAccount == null) {
            log.warn("Debit account does not exist, partyId={}, accountNumber={}, productCode={}",
                    partyId, fromAccount.getAccountNumber(), productCode);
            throw new ServiceException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
        }
        if (PaymentConstants.ACCOUNT_STATUS_CLOSED.equalsIgnoreCase(partyAccount.getStatus())) {
            log.warn("Debit account status is invalid, partyId={}, accountNumber={}, productCode={}, status={}",
                    partyId, fromAccount.getAccountNumber(), productCode, partyAccount.getStatus());
            throw new ServiceException(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
        }
    }

    /** FPS amount checks: presence, currency (HKD/CNY) and value rules. */
    private void validateFpsTxnAmount(TxnAmount txnAmount) {
        if (txnAmount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.TXN_AMOUNT_MISSING);
        }
        validateCurrency(txnAmount.getCurrency(),
                HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID);
        if (!FPS_SUPPORTED_CURRENCIES.contains(txnAmount.getCurrency())) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID);
        }
        validateAmount(txnAmount.getAmount(), txnAmount.getCurrency());
    }

    /**
     * Creditor account checks: account number format, supported FPS bank code
     * prefix and creditor name.
     */
    private void validateFpsToAccount(LocalPayment fpsPayment) {
        PaymentToAccount toAccount = fpsPayment.getToAccount();
        if (toAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING);
        }
//        if (toAccount.getAccountIdentifier() == null) {
//            throw logAndCreateException("accountIdentifier", HTHSupportErrorMapping.ACCOUNT_IDENTIFIER_MISSING);
//        }
//        if (toAccount.getAccountIdentifier() == PaymentToAccount.AccountIdentifierEnum._02) {
//            throw logAndCreateException(HTHSupportErrorMapping.FPS_MOBILE_IDENTIFIER_NOT_SUPPORTED);
//        }
        if (toAccount.getAccountNumber() == null || toAccount.getAccountNumber().isBlank()) {
            throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING);
        }

//        String bankCode = toAccount.getAccountNumber().substring(0, BANK_CODE_LENGTH);
//        if (!isFpsBankCodesSupported(bankCode)) {
//            throw logAndCreateException(
//                    "accountNumber", HTHSupportErrorMapping.BENEFICIARY_BANK_CODE_UNSUPPORTED);
//        }

        // Only account-number payees are accepted; the identifier is normalised
        // to _01 before the account-number format check.
        toAccount.setAccountIdentifier(PaymentToAccount.AccountIdentifierEnum._01);
        if (!PaymentServiceSupport.NUMERIC_PATTERN.matcher(toAccount.getAccountNumber()).matches()
                || toAccount.getAccountNumber().length() > 31) {
            throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID);
        }
        validateAccountName(fpsPayment.getCreditor().getCreditorName());
    }

    /** True when the bank code is in the configured FPS participant list. */
    private boolean isFpsBankCodesSupported(String bankCode) {
        List<String> activeCodes = fpsBankMapper.selectActiveParticipantCodes();
        if (activeCodes == null || activeCodes.isEmpty()) {
            return false;
        }
        for (String code : activeCodes) {
            if (bankCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * FPS currency rules: payment currency must equal creditor currency; debit
     * account and payment currencies must both be FPS-supported.
     */
    private void validateFpsCurrencyConsistency(LocalPayment localPayment) {
        Currency fromCurrency = CurrencyUtils.parseLenient(localPayment.getFromAccount().getCurrency());
        Currency txnCurrency = localPayment.getTxnAmount().getCurrency();
        Currency toCurrency = localPayment.getToAccount().getCurrency();
        if (!FPS_SUPPORTED_CURRENCIES.contains(fromCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        }
        if (fromCurrency != txnCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES);
        }
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    private void validateLocalAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateLocalAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    private void validateCurrency(Currency currency, int errorCode) {
        PaymentServiceSupport.validateCurrency(log, currency, errorCode);
    }

    private void validateAmount(BigDecimal amount, Currency currency) {
        PaymentServiceSupport.validateAmount(
                log, amount, currency, HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID);
    }

    private void validateAccountName(String accountName) {
        PaymentServiceSupport.validateAccountName(log, accountName);
    }

    /**
     * Persists a pending payout record, submits the enriched routing request to
     * IPE and updates the record with the outcome.
     */
    private Payment buildFpsResponse(LocalPayment localPayment,
                                     BatchRoutingCreditTransferRequest routingRequest) {
        Payment payment = new Payment();
        String transactionId = PaymentRoutingPayload.endToEndId(routingRequest);
        payment.setTxnRef(transactionId);
        String payoutId = insertPendingPayoutRecord(localPayment, routingRequest);
        TransferPaymentResultQueryResponse result = PaymentServiceSupport.submitRoutingRequest(
                payemtRoutApi, log, routingRequest, payment, transactionId,
                PaymentResponseSupport.RejectMessagePolicy.ERROR_MESSAGE_ONLY);
        payment.setTxnTime(OffsetDateTime.now().withNano(0));
        payment.setOriginalEndtoendID(localPayment.getPaymentIdentification().getEndToEndId());
        payment.setOriginalTransactionID(localPayment.getTransactionID());
        PaymentServiceSupport.updatePayoutRecord(
                paymentPayoutRecordMapper, log, FPS_NETWORK, payoutId, payment, result);
        cacheHelperUtils.validatePaymentResult(result);
        return payment;
    }

    /**
     * Inserts a pending payout record into HTH_PAYMENT_PAYOUT before the IPE submission.
     * The batch/end-to-end identifiers come from the routing request accepted by
     * smart-routing; MESSAGE_ID from the inbound header; IPE_REF_NO is left null until the
     * IPE response is available. STATUS, IPE_STATUS and IPE_STATUS_REASON are also left null
     * on the pending row and populated by the post-submission update.
     * PAYMENT_TYPE carries the FPS network identifier so FPS rows can be distinguished from
     * CHATS rows in the shared HTH_PAYMENT_PAYOUT table.
     */
    private String insertPendingPayoutRecord(LocalPayment req, BatchRoutingCreditTransferRequest routingRequest) {
        String batchId = PaymentRoutingPayload.batchId(routingRequest);
        String customerTransactionId = PaymentRoutingPayload.endToEndId(routingRequest);
        String id = UUID.randomUUID().toString().replace("-", "");

        String partyId = req.getPartyId();
        String payeeId = partyId != null
                ? internalPayeeMapper.selectDomesticPayeeId(
                partyId, req.getToAccount().getAccountNumber())
                : null;
        PaymentToAccount toAccount = req.getToAccount();

        try {
            PaymentPayoutRecord entity = new PaymentPayoutRecord();
            entity.setId(id);
            // key identifiers (IPE_REF_NO stays null until the IPE response arrives)
            entity.setMessageId(headers.getMessageId());
            entity.setPaymentId(customerTransactionId);
            entity.setCustomerTransactionId(req.getTransactionID());
            entity.setEndToEndId(batchId);
            entity.setExecutionDate(req.getSettlementDate());
            entity.setIpeRefNo(null);
            // audit / time fields
            entity.setCreationDate(LocalDateTime.now());
            entity.setCreditMemberId(req.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId());

            entity.setPaymentDate(LocalDateTime.now());
            entity.setCreatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedDate(LocalDateTime.now());
            // payment type (discriminator; preserves the FPS network identity in the shared table)
            entity.setPaymentType(FPS_NETWORK);
            // party / payee
            entity.setPartyId(partyId);
            entity.setPayeeId(payeeId);
            entity.setPayeeIdfn(PaymentConstants.PAYEE_IDFN_ACCTNO);
            // debit account
            entity.setDebitAccountId(req.getFromAccount().getAccountNumber());
            entity.setDebitAccountCur(req.getFromAccount().getCurrency().toString());
            entity.setDebitAccountType(req.getFromAccount().getAccountType().value());
            // credit account
            entity.setCreditAccountId(toAccount.getAccountNumber());
            entity.setCreditAccountName(PaymentServiceSupport.accountNameOrFallback(req));
            // amount
            entity.setInstdAmount(req.getTxnAmount().getAmount());
            entity.setInstdCurrency(req.getTxnAmount().getCurrency().toString());
            entity.setHkdEquivalentAmount(req.getTxnAmount().getAmount());
            // service charge
            entity.setSvcCur(req.getTxnAmount().getCurrency().toString());
            // remarks
            entity.setRemarks(PaymentServiceSupport.joinPaymentDetails(req.getPaymentDetails()));
            entity.setProdCode(PaymentRoutingPayload.debtorProductCode(routingRequest));
            // status (pending before IPE)
            entity.setStatus(null);
            entity.setIpeStatus(null);
            entity.setIpeStatusReason(null);
            entity.setStatusFlag(PaymentConstants.MOCK_OBJECT_STATUS);
            paymentPayoutRecordMapper.insert(entity);
            log.info("{} pending payout record inserted, customerTransactionId={}, status={}",
                    FPS_NETWORK, customerTransactionId, entity.getStatus());
        } catch (Exception e) {
            log.error("Failed to insert {} pending payout record, customerTransactionId={}, error={}",
                    FPS_NETWORK, customerTransactionId, e.getMessage(), e);
        }
        return id;
    }

    private ParameterException logAndCreateException(int errorCode) {
        return PaymentServiceSupport.logAndCreateException(log, errorCode);
    }

}
