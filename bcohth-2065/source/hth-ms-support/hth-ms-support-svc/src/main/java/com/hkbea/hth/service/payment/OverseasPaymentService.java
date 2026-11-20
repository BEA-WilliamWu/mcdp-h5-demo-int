package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.exception.ErrorMapping;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.DfhcommareaReq7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0CacI;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0Input;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0Record;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.QaechccReq7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.ChatsRemittance;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.PaymentTypeInformation;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.SupplementaryData;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.AccountStatus;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.FinancialInstitutionId;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Abstract base for overseas payments (TT / DR).
 * Flow: validate -> enrich the routing request accepted by smart-routing in
 * place -> persist pending -> IPE submit -> parse -> update.
 * Persistence differs by type (two-phase, FPS-style):
 * TT -> HTH_PAYMENT_PAYOUT (PaymentPayoutRecord, shared with FPS/CHATS, pending + update)
 * DR -> HTH_PAYMENT_INTERNAL (PaymentInternal, shared with INTERNAL/FX, pending + update)
 * EBK already deducts the transfer limit; H2H performs NO limit handling here.
 */
public abstract class OverseasPaymentService {

    protected static final Logger log = LoggerFactory.getLogger(OverseasPaymentService.class);

    private static final String TT_PAYEE_BICCODE_REGEX = "[A-Z0-9]{4,4}[A-Z]{2,2}[A-Z0-9]{2,2}([A-Z0-9]{3,3}){0,1}";

    @Autowired
    protected PayemtRoutApi payemtRoutApi;
    @Autowired
    protected HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Autowired
    protected PaymentInternalMapper paymentInternalMapper;
    @Autowired
    protected PartyAccountMapper partyAccountMapper;
    @Autowired
    protected PartyPreferenceMapper partyPreferenceMapper;
    @Autowired
    protected InternalPayeeMapper internalPayeeMapper;
    @Autowired
    protected com.hkbea.hth.bg3.api.AccountApi bgAccountApi;
    @Autowired
    protected CommonHeader headers;
    @Autowired
    protected PaymentCommonService paymentCommonService;
    @Autowired
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
    @Autowired
    private CacheHelperUtils cacheHelperUtils;

    /**
     * Phase 1: inserts the pending payment record (basic info, status unknown)
     * before the IPE submission; returns the record id for the phase-2 update.
     */
    protected abstract String insertPendingRecord(LocalPayment localPayment,
                                                  BatchRoutingCreditTransferRequest routingRequest,
                                                  Payment payment);

    /**
     * Phase 2: updates the pending record with the submission outcome.
     */
    protected abstract void persistPaymentRecord(String recordId,
                                                 LocalPayment localPayment,
                                                 BatchRoutingCreditTransferRequest routingRequest,
                                                 Payment payment,
                                                 TransferPaymentResultQueryResponse result);

    /** Routing payment method value for this overseas type (TT / DR). */
    protected abstract String routingPaymentMethod();

    protected abstract void validateSpecific(LocalPayment localPayment);

    /**
     * Type-specific blocks written onto the routing transaction detail after the
     * shared overseas blocks have been applied (charges, creditor blocks, tt/dr
     * blocks and the related payment-type information).
     */
    protected abstract void enrichRoutingDetail(RoutingTransactionDetailRequest txn,
                                                LocalPayment localPayment);

    /** Service charge amount written into the batch charges block; DR zero, TT unset. */
    protected BigDecimal serviceChargeAmount() {
        return null;
    }

    /**
     * Optional hook invoked before submission with the accepted batch id; TT uses
     * it to kick off the WMG process instance. No-op by default.
     */
    protected void createProcessInstanceExecute(LocalPayment localPayment, String batchId) {
        // TT-specific
    }

    /**
     * Overseas payment flow (TT/DR template): validate, enrich the routing
     * request accepted by smart-routing, then submit and persist via the
     * subclass hooks.
     */
    public final Payment processPayment(LocalPayment localPayment,
                                         BatchRoutingCreditTransferRequest routingRequest) {
        validatePayment(localPayment);
        applyOverseasRoutingDetails(localPayment, routingRequest);
        return submitPayment(localPayment, routingRequest);
    }

    /**
     * Shared preparation pipeline: TT/DR field rules -> creditor currency
     * defaulting -> common overseas checks (debtor account, amount, payee
     * BIC/memberId) -> subclass-specific checks.
     */
    private void validatePayment(LocalPayment localPayment) {
        if (localPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        if (localPayment.getPaymentType() == LocalPayment.PaymentTypeEnum.TT) {
            PaymentValidationSupport.validateTtRules(localPayment);
        } else if (localPayment.getPaymentType() == LocalPayment.PaymentTypeEnum.DR) {
            PaymentValidationSupport.validateDrRules(localPayment);
        }
        PaymentValidationSupport.useDebitCurrencyForCreditorAccount(localPayment);
        validateOverseas(localPayment);
        validateSpecific(localPayment);
    }

    /**
     * Applies the shared overseas blocks to the accepted routing request, then
     * delegates to the subclass type-specific enrichment.
     */
    private void applyOverseasRoutingDetails(LocalPayment localPayment,
                                             BatchRoutingCreditTransferRequest routingRequest) {
        PaymentRoutingPayload.applyTypeEnvelope(routingRequest,
                PaymentConstants.BG3_CHANNEL_REFERENCE_OUTWARD, routingPaymentMethod());
        RoutingTransactionDetailRequest txn = PaymentRoutingPayload.firstTransaction(routingRequest);

        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                localPayment.getFromAccount().getAccountNumber(),
                localPayment.getFromAccount().getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(
                debitInternalAccountNo, localPayment.getPartyId());
        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                localPayment.getPartyId(), localPayment.getFromAccount().getAccountNumber(), productCode);
        String payerName = partyPreferenceMapper.selectCustomerNameByPartyId(localPayment.getPartyId(),localPayment.getFromAccount().getAccountNumber());

        txn.setRemittanceInformation(PaymentRoutingPayload.remittance(
                PaymentConstants.BG3_CUSTOMER_REMARKS_OUTWARD, localPayment.getPaymentDetails()));

        if (localPayment.getCategoryPurpose() != null) {
            String code = localPayment.getCategoryPurpose().getCode();
            String proprietary = localPayment.getCategoryPurpose().getProprietary();
            if (code != null || proprietary != null) {
                com.hkbea.hth.ipe.model.Purpose purpose = new com.hkbea.hth.ipe.model.Purpose();
                purpose.setCode(code);
                purpose.setProprietary(proprietary);
                txn.setPurpose(purpose);
            }
        }

        txn.setDebtor(PaymentRoutingPayload.corporateDebtor(payerName));
        txn.setDebtorAccount(PaymentRoutingPayload.debtorAccount(
                debitInternalAccountNo, debitInternalAccountNo,
                formatPayerAccountNoFmt(localPayment.getFromAccount().getAccountNumber()),
                PaymentConstants.PAYER_ACCOUNT_TYPE_CSA,
                localPayment.getFromAccount().getCurrency(),
                PaymentRoutingPayload.parseCompanyCode(partyAccount == null ? null : partyAccount.getCompanyCode()),
                productCode));

        ChatsRemittance chatsRemittance =
                PaymentRoutingPayload.chatsRemittance(localPayment.getCreditor().getCreditorName());
        chatsRemittance.setPinCnt(0);

        SupplementaryData supplementaryData = new SupplementaryData();
        supplementaryData.setDebitInternalAccount(debitInternalAccountNo);
        supplementaryData.setProductType(productCode);
        supplementaryData.setHdrCibExtAcNo(localPayment.getPartyId());
        supplementaryData.setHdrCibIntAcNo(AccountUtils.ext2intAccNo(localPayment.getPartyId(), ""));
        supplementaryData.setChatsRemittance(chatsRemittance);
        supplementaryData.setCnapsCodeword(localPayment.getCategoryPurpose() == null
                ? null : localPayment.getCategoryPurpose().getProprietary());
        txn.setSupplementaryData(supplementaryData);

        String payeeId = internalPayeeMapper.selectInternationalPayeeId(
                localPayment.getPartyId(), localPayment.getToAccount().getAccountNumber());
        CustomerNotificationInformRequest notification = paymentCommonService.buildCustomerNotificationInfo(
                localPayment, PaymentConstants.NOTIFICATION_TYPE_EMAIL_SMS, payeeId);
        txn.setNotification(PaymentRoutingPayload.notification(notification));

        enrichRoutingDetail(txn, localPayment);
    }

    /**
     * Runs the WMG hook with the accepted batch id, persists the pending record
     * (phase 1), submits the enriched routing request to IPE, then updates the
     * record with the outcome (phase 2) via the subclass hooks. FAILED reject
     * messages fall back to statusReason.
     */
    private Payment submitPayment(LocalPayment localPayment,
                                  BatchRoutingCreditTransferRequest routingRequest) {
        Payment payment = new Payment();
        String batchId = PaymentRoutingPayload.batchId(routingRequest);
        // TT creates the WMG process after validation, using the same batch id
        // that was accepted by smart-routing and is subsequently submitted to IPE.
        createProcessInstanceExecute(localPayment, batchId);
        payment.setTxnRef(PaymentRoutingPayload.endToEndId(routingRequest));
        // Phase 1: basic payment info is on record before the IPE submission.
        String recordId = insertPendingRecord(localPayment, routingRequest, payment);
        TransferPaymentResultQueryResponse result = PaymentServiceSupport.submitRoutingRequest(
                payemtRoutApi, log, routingRequest, payment, null,
                PaymentResponseSupport.RejectMessagePolicy.ERROR_MESSAGE_WITH_FALLBACK);
        payment.setTxnTime(OffsetDateTime.now().withNano(0));
        payment.setOriginalEndtoendID(PaymentModelConvert.EndToEndId(localPayment));
        payment.setOriginalTransactionID(localPayment.getTransactionID());
        // Phase 2: the pending record is updated with the IPE outcome.
        PaymentServiceSupport.validatePaymentResult(localPayment, result);
        persistPaymentRecord(recordId, localPayment, routingRequest, payment, result);
        cacheHelperUtils.validatePaymentResult(result);
        return payment;
    }

    /**
     * Overseas validation orchestrator (shared by TT/DR): debtor account,
     * amount, then the creditorAgent BIC/memberId rules.
     */
    public void validateOverseas(LocalPayment payment) {
        if (payment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateFromAccount(payment.getFromAccount(), payment.getPartyId(), payment.getCloseId());
        validateTxnAmount(payment.getTxnAmount());
        validatePayeeBicCode(payment);
    }

    /**
     * Validates creditorAgent.financialInstitutionId per the field spec:
     * 1) BICFI and clearingSystemMemberId are mutually exclusive (both provided -> 500177);
     * 2) at least one of them must be provided (both blank -> 500176);
     * 3) when BICFI is provided it must match BIC 8 / BIC 11 (existing 500140 behavior kept).
     * Check order: mutual exclusion -> either-or -> format.
     */
    protected void validatePayeeBicCode(LocalPayment payment) {
        CreditorAgent agent = payment.getCreditorAgent();
        FinancialInstitutionId fid = agent == null ? null : agent.getFinancialInstitutionId();
        String bicfi = fid == null ? null : fid.getBicfi();
        String memberId = fid == null ? null : fid.getClearingSystemMemberId();

        boolean bicfiProvided = StringUtils.isNotBlank(bicfi);
        boolean memberIdProvided = StringUtils.isNotBlank(memberId);

        // 1) mutual exclusion: both provided -> reject (case 277)
        if (bicfiProvided && memberIdProvided) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITOR_AGENT_BIC_MEMBER_ID_CONFLICT);
        }

        // 2) either-or: both missing -> reject (cases 257/258/259/260)
        if (!bicfiProvided && !memberIdProvided) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITOR_AGENT_BICFI_MISSING_OR_INVALID);
        }

        // 3) format: validate when BICFI is provided (existing behavior, cases 262/263 rely on 500140)
        if (bicfiProvided && (!bicfi.equals(bicfi.trim()) || !bicfi.matches(TT_PAYEE_BICCODE_REGEX))) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID);
        }
    }

    /**
     * Debtor account checks: 14-15 digit "015" account, valid currency/type,
     * and the account must exist and be ACTIVE for the party.
     */
    private void validateFromAccount(BeaAccount fromAccount, String partyId, String closeId) {
        if (fromAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING);
        }
        validateLocalAccountNumber(fromAccount.getAccountNumber());
        validateCurrency(
                CurrencyUtils.parseLenient(fromAccount.getCurrency()),
                HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        if (fromAccount.getAccountType() == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING);
        }
        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                fromAccount.getAccountNumber(), fromAccount.getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, partyId);
        List<HthUserAccessAccount> accessAccounts =
                hthUserAccessAccountMapper.selectFromAccountInfo(partyId, closeId, fromAccount.getAccountNumber());
        HthUserAccessAccount first = accessAccounts == null || accessAccounts.isEmpty()
                ? null : accessAccounts.getFirst();
        if (first == null) {
            log.warn("Debit account does not exist, partyId={}, accountNumber={}, productCode={}",
                    partyId, fromAccount.getAccountNumber(), productCode);
            throw new ServiceException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
        }
        String status = first.getObjectStatus();
        boolean active = "A".equalsIgnoreCase(status)
                || AccountStatus.ACTIVE.value().equalsIgnoreCase(status);
        if (!active) {
            throw new ServiceException(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
        }
        validateDebitAccountStatus(debitInternalAccountNo, fromAccount.getCurrency());
    }

    private void validateDebitAccountStatus(String internalAccountNumber, String currency) {
        Bgech7e0Response response;
        try {
            response = bgAccountApi.queryAccountInfo(buildDebitAccountInfoRequest(internalAccountNumber, currency));
        } catch (Exception e) {
            log.warn("Debit account status enquiry failed", e);
            throw new ServiceException(ErrorMapping.GENERAL_SERVICE_EXCEPTION);
        }
        if (response == null || response.getDfhcommarea() == null
                || response.getDfhcommarea().getQaechcc() == null) {
            throw new ServiceException(ErrorMapping.GENERAL_SERVICE_EXCEPTION);
        }
        var envelope = response.getDfhcommarea().getQaechcc();
        if (!PaymentConstants.BG_RETURN_CODE_0000.equals(envelope.getQaechccErrData()) || envelope.getBgech7e0() == null
                || envelope.getBgech7e0().getEh7e0Output() == null) {
            throw new ServiceException(ErrorMapping.GENERAL_SERVICE_EXCEPTION);
        }
        var output = envelope.getBgech7e0().getEh7e0Output();
        if (!PaymentConstants.SUCC_CODE_00.equals(output.getEh7e0CodReturnO())) {
            throw new ServiceException(ErrorMapping.GENERAL_SERVICE_EXCEPTION);
        }
        // Match TransactionServiceImpl.mapStatus: other values fall through; Hold is not checked.
        if ("C".equalsIgnoreCase(output.getEh7e0FlgStatusO())
                || "Y".equalsIgnoreCase(output.getEh7e0FlgDormantO())) {
            throw new ServiceException(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
        }
    }

    private Bgech7e0Request buildDebitAccountInfoRequest(String accountNumber, String currency) {
        Eh7e0CacI account = new Eh7e0CacI();
        account.setEh7e0EntI(Integer.valueOf(accountNumber.substring(
                PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS)));
        account.setEh7e0CenRegI(Integer.valueOf(accountNumber.substring(
                PaymentConstants.EH7E0_CEN_REG_ST_POS, PaymentConstants.EH7E0_CEN_REG_END_POS)));
        account.setEh7e0AccI(Long.valueOf(accountNumber.substring(PaymentConstants.EH7E0_ACC_ST_POS)));
        Eh7e0Input input = new Eh7e0Input();
        input.setEh7e0CacI(account);
        input.setEh7e0FccI(currency);
        Eh7e0Record record = new Eh7e0Record();
        record.setEh7e0Input(input);
        Bgech7e0 enquiry = new Bgech7e0();
        enquiry.setEh7e0Record(record);
        QaechccReq7e0 envelope = new QaechccReq7e0();
        envelope.setQaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN);
        envelope.setQaechccApplId(PaymentConstants.APPLICATION_ID_BG);
        envelope.setQaechccCollecName(PaymentConstants.COLLECTION_NAME_BG9CH7E0);
        envelope.setQaechccCodEntity(accountNumber.substring(
                PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS));
        envelope.setQaechccDateOp(LocalDate.now().toString());
        envelope.setQaechccCollecFunc("");
        envelope.setQaechccCodBrn("");
        envelope.setQaechccCodTerm("");
        envelope.setQaechccCodUser("");
        envelope.setQaechccErrData("");
        envelope.setQaechccFrArea("");
        envelope.setBgech7e0(enquiry);
        DfhcommareaReq7e0 area = new DfhcommareaReq7e0();
        area.setQaechcc(envelope);
        Bgech7e0Request request = new Bgech7e0Request();
        request.setDfhcommarea(area);
        return request;
    }

    /**
     * instructedAmount checks: presence, valid currency and the shared amount
     * rules.
     */
    protected void validateTxnAmount(TxnAmount txnAmount) {
        if (txnAmount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.TXN_AMOUNT_MISSING);
        }
        validateCurrency(
                txnAmount.getCurrency(),
                HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID);
        validateAmount(txnAmount.getAmount(), txnAmount.getCurrency());
    }

    /**
     * Spec: "0 <= decimal td = 15 fd = 2" / "format 9(15).9(2)" - td=15 is the total significant digits.
     */
    private static final int MAX_TOTAL_DIGITS = 15;

    /**
     * Amount rules for overseas payments: shared rules plus a 15-significant-
     * digit cap.
     */
    protected void validateAmount(BigDecimal amount, Currency currency) {
        // error code aligned with test expectations: 500161 instead of 500106
        PaymentServiceSupport.validateAmount(
                log, amount, currency, HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
        // total significant digits check (case 76: 99999999999999.98 has 16 significant digits -> reject)
        if (amount.precision() > MAX_TOTAL_DIGITS) {
            throw logAndCreateException(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
        }
    }

    protected String formatPayerAccountNoFmt(String externalAccountNo) {
        return PaymentServiceSupport.formatPayerAccountNoFmt(externalAccountNo);
    }

    protected void validateLocalAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateLocalAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    protected void validateCurrency(Currency currency, int errorCode) {
        PaymentServiceSupport.validateCurrency(log, currency, errorCode);
    }

    public String accountNameOrFallback(LocalPayment localPayment) {
        return PaymentServiceSupport.accountNameOrFallback(localPayment);
    }

    protected String joinPaymentDetails(List<String> paymentDetails) {
        return PaymentServiceSupport.joinPaymentDetails(paymentDetails);
    }

    protected ParameterException logAndCreateException(int errorCode) {
        return PaymentServiceSupport.logAndCreateException(log, errorCode);
    }


    /** Convenience for the subclass enrichment: creditor postal address (may be null). */
    protected static PostalAddress creditorPostalAddress(LocalPayment localPayment) {
        Creditor creditor = localPayment.getCreditor();
        return creditor == null ? null : creditor.getPostalAddress();
    }

    /** Convenience for the subclass enrichment: payment-type-information block. */
    protected static PaymentTypeInformation instructionPriority(String priority) {
        PaymentTypeInformation target = new PaymentTypeInformation();
        target.setInstructionPriority(priority);
        return target;
    }
}
