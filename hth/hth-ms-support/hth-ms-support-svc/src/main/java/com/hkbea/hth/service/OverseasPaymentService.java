package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.constant.CommonConstant;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.convert.PaymentModelConvert;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.model.*;
import com.hkbea.hth.mapper.*;
import com.hkbea.hth.model.*;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Abstract base for overseas payments (TT / DR).
 * Mirrors ChatsPaymentService (TT) / OnUsPaymentService (DR):
 * validate -> build -> IPE submit -> parse -> persist.
 * Persistence differs by type:
 * TT -> HTH_PAYMENT_PAYOUT (PaymentPayoutRecord, shared with FPS/CHATS, pending + update)
 * DR -> HTH_NWPAYMENT_INTERNAL (PaymentInternal, shared with INTERNAL/FX, single insert)
 * EBK already deducts the transfer limit; H2H performs NO limit handling here.
 */
public abstract class OverseasPaymentService {

    protected static final Logger log = LoggerFactory.getLogger(OverseasPaymentService.class);

    protected static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    protected static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");

    protected static final String SUCCESS_CODE = "00";
    protected static final String CHANNEL = "HTH";
    protected static final String OVERSEAS_CHANNEL_REFERENCE = "BG3-SIT-OUTWARD";
    protected static final String OVERSEAS_CUSTOMER_REMARKS = "BG3 internal outward H2H priority";
    protected static final String TT_PAYER_ACCOUNT_TYPE = "CSA";
    protected static final Integer OVERSEAS_ACCOUNT_STATUS = 0;

    // mock values for pending records (same as ChatsPaymentService / OnUsPaymentService)
    protected static final String MOCK_PARTY_ID = "MOCK_PARTY_ID";
    protected static final String MOCK_PARTY_NAME = "MOCK PARTY NAME";
    protected static final String MOCK_OBJECT_STATUS = "A";
    protected static final BigDecimal MOCK_SVC_AMOUNT = new BigDecimal("0.00");
    protected static final String SYSTEM_USER = "System";
    protected static final String ACCOUNT_NAME_NA = "N/A";
    protected static final String ACCOUNT_STATUS_CLOSED = "CLOSED";
    private static final DateTimeFormatter TRANSFER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String TT_PAYEE_BICCODE_REGEX = "[A-Z0-9]{4,4}[A-Z]{2,2}[A-Z0-9]{2,2}([A-Z0-9]{3,3}){0,1}";


    @Autowired
    protected LocalPaymentApi localPaymentApi;
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
    protected AccountApi accountApi;
    @Autowired
    protected CommonHeader headers;
    @Autowired
    private PaymentCommonService paymentCommonService;
    @Autowired
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;

    protected abstract void persistPaymentRecord(LocalPayment localPayment, BatchCreditTransferRequest request,
                                                 Payment payment, PaymentResultQueryResponse result);

    protected abstract BatchCreditTransferRequest.TransactionTypeEnum transactionType();

    protected abstract void validateSpecific(LocalPayment localPayment);

    protected abstract void enrichTransaction(TransactionDetailRequest txn, LocalPayment localPayment,
                                              String batchId, String productCode);

    protected void createProcessInstanceExecute(LocalPayment localPayment, String batchId) {
        // TT-specific
    }

    public final Payment processPayment(LocalPayment localPayment) {
        return processPayment(localPayment, prepareRoutingRequest(localPayment));
    }

    Payment processPayment(LocalPayment localPayment,
                           BatchRoutingCreditTransferRequest routingRequest) {
        BatchRoutingCreditTransferRequest submitRequest = routingRequest != null
                ? routingRequest : prepareRoutingRequest(localPayment);
        return buildResponse(localPayment, submitRequest);
    }

    BatchRoutingCreditTransferRequest prepareRoutingRequest(LocalPayment localPayment) {
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
        BatchRoutingCreditTransferRequest request = PaymentCommonRoutingBuilder
                .prepareCommonRoutingRequest(localPayment, partyAccountMapper);
        PaymentModelConvert.applyTypeSpecificRoutingDetails(request,
                PaymentModelConvert.toRoutingRequest(buildRequest(localPayment, newBatchId())));
        return request;
    }

    protected BatchCreditTransferRequest buildRequest(LocalPayment localPayment) {
        String batchId = newBatchId();
        //call wmg
        createProcessInstanceExecute(localPayment, batchId);
        return buildRequest(localPayment, batchId);
    }

    private BatchCreditTransferRequest buildRequest(LocalPayment localPayment, String batchId) {
        BatchCreditTransferRequest request = buildEnvelope(localPayment, batchId);
        TransactionDetailRequest txn = buildTransactionDetail(localPayment, batchId);
        enrichTransaction(txn, localPayment, batchId, txn.getPayerAcProdCode());
        request.setTransactions(Collections.singletonList(txn));
        return request;
    }

    private String newBatchId() {
        return CHANNEL + LocalDateTime.now().format(TIMESTAMP_FMT) + AccountUtils.generateDigits();
    }

    protected void validateOverseas(LocalPayment payment) {
        if (payment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateFromAccount(payment.getFromAccount(), payment.getPartyId(), payment.getCloseId());
        validateTxnAmount(payment.getTxnAmount());
//        validateTimeOfTransfer(payment.getTimeOfTransfer());
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

    private void validateFromAccount(BeaAccount fromAccount, String partyId, String closeId) {
        if (fromAccount == null) {
            throw logAndCreateException("debtorAccount", HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateLocalAccountNumber(
                fromAccount.getAccountNumber(),
                "debitAccountNumber",
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID);
        validateCurrency(
                CurrencyUtils.parseLenient(fromAccount.getCurrency()),
                "debitCurrency",
                HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        if (fromAccount.getAccountType() == null) {
            throw logAndCreateException("payerAccountType", HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                fromAccount.getAccountNumber(), fromAccount.getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, partyId);
        List<HthUserAccessAccount> accessAccounts =
                hthUserAccessAccountMapper.selectFromAccountInfo(partyId, closeId, fromAccount.getAccountNumber());
        HthUserAccessAccount first = accessAccounts == null || accessAccounts.isEmpty()
                ? null : accessAccounts.getFirst();
//        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
//                : partyAccountMapper.selectAccountStatus(
//                partyId, fromAccount.getAccountNumber(), productCode);
//        if (partyAccount == null) {
//            log.warn("Debit account does not exist, partyId={}, accountNumber={}, productCode={}",
//                    partyId, fromAccount.getAccountNumber(), productCode);
//            throw new ServiceException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
//        }
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
    }

    protected void validateTxnAmount(TxnAmount txnAmount) {
        if (txnAmount == null) {
            throw logAndCreateException("instructedAmount", HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateCurrency(
                txnAmount.getCurrency(),
                "",
                HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID);
        validateAmount(txnAmount.getAmount(), txnAmount.getCurrency());
    }

    /** Spec: "0 <= decimal td = 15 fd = 2" / "format 9(15).9(2)" - td=15 is the total significant digits. */
    private static final int MAX_TOTAL_DIGITS = 15;

    protected void validateAmount(BigDecimal amount, Currency currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(CommonConstant.MAX_AMOUNT) > 0) {
            // error code aligned with test expectations: 500161 instead of 500106
            throw logAndCreateException(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
        }
        // total significant digits check (case 76: 99999999999999.98 has 16 significant digits -> reject)
        if (amount.precision() > MAX_TOTAL_DIGITS) {
            throw logAndCreateException(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
        }
        if ("JPY".equalsIgnoreCase(currency.toString())) {
            if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw logAndCreateException(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
            }
        } else if (amount.stripTrailingZeros().scale() > 2) {
            throw logAndCreateException(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID);
        }
    }

    protected void validateTimeOfTransfer(LocalPayment.TimeOfTransferEnum timeOfTransfer) {
        if (timeOfTransfer == null) {
            throw logAndCreateException(
                    "timeOfTransfer", HTHSupportErrorMapping.TIME_OF_TRANSFER_MISSING_OR_INVALID);
        }
    }

    protected void validateSufficientBalance(
            String accountNumber, String currency, BigDecimal amount) {
        if (!hasEnoughBalance(accountNumber, currency, amount)) {
            throw new ParameterException("debtorAccount ", HTHSupportErrorMapping.INSUFFICIENT_BALANCE);
        }
    }

    protected boolean hasEnoughBalance(
            String accountNumber, String currency, BigDecimal requiredAmount) {
        GenericBalanceParam param = new GenericBalanceParam();
        param.setAccountNumber(accountNumber);
        param.setCurrencies(List.of(Currency.fromValue(currency)));
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

    protected final Payment buildResponse(LocalPayment localPayment,
                                          BatchRoutingCreditTransferRequest routingRequest) {
        Payment payment = new Payment();
        BatchCreditTransferRequest request = buildRequest(localPayment, newBatchId());
        PaymentRoutingSubmitter.alignLegacyIdentifiers(request, routingRequest);
        // TT creates the WMG process after validation, using the same batch id
        // that was accepted by smart-routing and will be submitted to IPE.
        createProcessInstanceExecute(localPayment, request.getBatchId());
        String transactionId = request.getTransactions().getFirst().getTransactionId();
        payment.setTxnRef(transactionId);
        PaymentResultQueryResponse result = null;
        try {
            BatchRoutingCreditTransferRequest submitRequest = routingRequest != null
                    ? routingRequest : PaymentModelConvert.toRoutingRequest(request);
            TransferModelApiResponse routingResponse =
                    PaymentRoutingSubmitter.submit(localPaymentApi, submitRequest);
            ModelApiResponse response = PaymentModelConvert.toLegacyApiResponse(routingResponse);
            result = parsePaymentResponse(response, payment);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            payment.setStatus(Payment.StatusEnum.PROCESSING);
        }
        payment.setTxnTime(OffsetDateTime.now().withNano(0));
        payment.setOriginalEndtoendID(localPayment.getPaymentIdentification().getEndToEndId());
        payment.setOriginalTransactionID(localPayment.getTransactionID());
        persistPaymentRecord(localPayment, request, payment, result);
        return payment;
    }

    protected BatchCreditTransferRequest buildEnvelope(LocalPayment localPayment, String batchId) {
        BatchCreditTransferRequest request = new BatchCreditTransferRequest();
        request.setChannel(BatchCreditTransferRequest.ChannelEnum.HTH);
        request.setBatchId(batchId);
        request.setChannelReference(OVERSEAS_CHANNEL_REFERENCE);
        request.setTransactionType(transactionType());
        LocalDate date = LocalDate.parse(localPayment.getSettlementDate());
        if (date.isAfter(LocalDate.now())) {
            request.setTimeOfTransfer(BatchCreditTransferRequest.TimeOfTransferEnum.LATER);
            request.setExecutionDate(date);
        } else {
            request.setTimeOfTransfer(BatchCreditTransferRequest.TimeOfTransferEnum.NOW);
            request.setExecutionDate(date);
        }
        request.setTotalCount(1);
        request.setTotalAmount(localPayment.getTxnAmount().getAmount());
        request.setCustomerRemarks(OVERSEAS_CUSTOMER_REMARKS);
//        if (localPayment.getTimeOfTransfer() == LocalPayment.TimeOfTransferEnum.L) {
//            request.setExecutionDate(LocalDate.parse(localPayment.getTransferDate()));
//        }
        request.setServiceChargeAmount(serviceChargeAmount());
        return request;
    }

    protected java.math.BigDecimal serviceChargeAmount() {
        return null;
    }

    protected TransactionDetailRequest buildTransactionDetail(LocalPayment localPayment, String batchId) {

        TransactionDetailRequest txn = new TransactionDetailRequest();
        //TODO Need to convert the amount into the equivalent in Hong Kong dollars.
        txn.setHkdEquivalentAmount(localPayment.getTxnAmount().getAmount());
        txn.setTransactionId(batchId);
        txn.setDebitAmount(localPayment.getTxnAmount().getAmount());
        txn.setDebitAmountCcy(localPayment.getFromAccount().getCurrency().toString());
        txn.setPaymentDetails(localPayment.getPaymentDetails());
        txn.setPurposeCode(localPayment.getCategoryPurpose() != null
                ? localPayment.getCategoryPurpose().getCode() : null);
        txn.setPurposeProprietary(localPayment.getCategoryPurpose() != null
                ? localPayment.getCategoryPurpose().getProprietary() : null);

        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                localPayment.getFromAccount().getAccountNumber(),
                localPayment.getFromAccount().getAccountType().value().substring(0, 1));
        txn.setPayerType(TransactionDetailRequest.PayerTypeEnum.CORPORATE);
        txn.setPayerAccountNo(debitInternalAccountNo);
        txn.setPayerInternalAccountNo(debitInternalAccountNo);
        txn.setDebitInternalAccount(debitInternalAccountNo);
        txn.setPayerAccountNoFmt(formatPayerAccountNoFmt(localPayment.getFromAccount().getAccountNumber()));
        txn.setPayerAccountCurrency(localPayment.getFromAccount().getCurrency().toString());
        txn.setPayerName(partyPreferenceMapper.selectCustomerNameByPartyId(localPayment.getPartyId()));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, localPayment.getPartyId());
        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                localPayment.getPartyId(), localPayment.getFromAccount().getAccountNumber(), productCode);
        txn.setPayerAcCompCode(partyAccount == null || StringUtils.isBlank(partyAccount.getCompanyCode())
                ? null
                : Integer.valueOf(partyAccount.getCompanyCode().replaceFirst("^0+", "")));
        txn.setPayerAcProdCode(productCode);
        txn.setProductType(productCode);
        txn.setPayerAccountType("CSA");
        txn.setAcStatus(0);
        txn.setHdrCibExtAcNo(localPayment.getFromAccount().getAccountNumber());
        txn.setHdrCibIntAcNo(debitInternalAccountNo);

        RequestRemittanceCreditTransferRequest remittanceRequest = new RequestRemittanceCreditTransferRequest();
        remittanceRequest.setPrntAdvInd(null);
        //TODO Need to get the rate
        remittanceRequest.setPinCnt(0);
        remittanceRequest.setPinListArea(null);
        remittanceRequest.setLmInd(null);
        remittanceRequest.setDepositAccountName1(localPayment.getCreditor().getCreditorName());
        //TODO DepositAc2 is bener address , if registered select from bco db DIGX_PY_DOMESTIC_PAYEE ADDRESS_LINE1
        remittanceRequest.setDepositAccountName2(null);
        //DepositAc3 is bener address , if registered select from bco db DIGX_PY_DOMESTIC_PAYEE ADDRESS_LINE2
        //remittanceRequest.setDepositAccountName3(CHATS_DEPOSIT_ACCOUNT_NAME_3);
        //DepositAc4 is bener address , if registered select from bco db DIGX_PY_DOMESTIC_PAYEE ADDRESS_LINE3
        //remittanceRequest.setDepositAccountName4(CHATS_DEPOSIT_ACCOUNT_NAME_4);
        txn.setRequestRemittanceCreditTransferRequest(remittanceRequest);
        String payeeId = internalPayeeMapper.selectInternationalPayeeId(localPayment.getPartyId(), localPayment.getToAccount().getAccountNumber());
        // notify high-risk
        CustomerNotificationInformRequest customerNotificationInformRequest =
                paymentCommonService.buildCustomerNotificationInfo(localPayment, "EMAIL_SMS",payeeId );
        txn.setCustomerNotificationInformRequest(customerNotificationInformRequest);

        return txn;
    }

    protected String formatPayerAccountNoFmt(String externalAccountNo) {
        String acc = externalAccountNo == null ? "" : externalAccountNo.trim();
        if (acc.length() < 14) {
            return acc;
        }
        return acc.substring(0, 3) + "-" + acc.substring(3, 6) + "-" + acc.substring(6, 8)
                + "-" + acc.substring(8, 13) + "-" + acc.substring(13, 14);
    }

    protected PaymentResultQueryResponse parsePaymentResponse(ModelApiResponse response, Payment payment) {
        if (!SUCCESS_CODE.equals(response.getCode())) {
            log.warn("IPE returned non-success code: {}", response.getCode());
            payment.setStatus(Payment.StatusEnum.PROCESSING);
            return null;
        }
        PaymentResultQueryResponse result = (PaymentResultQueryResponse) response.getData();
        if (result == null) {
            log.warn("IPE response data is null");
            payment.setStatus(Payment.StatusEnum.PROCESSING);
            return null;
        }
        log.info("Overseas payment result received, transactionId={}, status={}",
                result.getTransactionId(), result.getStatus());
        payment.setStatus(toPaymentStatus(result.getStatus()));
        if (result.getStatus() == PaymentResultQueryResponse.StatusEnum.FAILED) {
            // Rejection reason fallback chain: errorMessage -> statusReason -> fixed hint,
            // so that rejectMessage is never null (cases 116/133 previously lost the reason).
            String reason = result.getErrorMessage();
            if (StringUtils.isBlank(reason)) {
                reason = result.getStatusReason();
            }
            if (StringUtils.isBlank(reason)) {
                reason = "Rejected by payment engine (no detail returned)";
                log.warn("IPE returned FAILED without errorMessage/statusReason, transactionId={}, "
                        + "downstreamSystem={}, downstreamReference={}",
                        result.getTransactionId(), result.getDownstreamSystem(), result.getDownstreamReference());
            }
            payment.setRejectMessage(reason);
            payment.setRejectCode(result.getErrorCode());
        }
        return result;
    }

    protected static Payment.StatusEnum toPaymentStatus(
            PaymentResultQueryResponse.StatusEnum ipeStatus) {
        if (ipeStatus == null) {
            return Payment.StatusEnum.PROCESSING;
        }
        switch (ipeStatus) {
            case SUCCESS:
                return Payment.StatusEnum.SUCCESS;
            case FAILED:
                return Payment.StatusEnum.REJECTED;
            case PROCESSING:
            case PENDING:
            case MANUAL_REVIEW:
            default:
                return Payment.StatusEnum.PROCESSING;
        }
    }

    protected void validateLocalAccountNumber(
            String accountNumber, String fieldName, int missingCode, int formatCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw logAndCreateException(fieldName, missingCode);
        }
        if (!NUMERIC_PATTERN.matcher(accountNumber).matches()) {
            throw logAndCreateException(fieldName, formatCode);
        }
        if (accountNumber.length() != 14 && accountNumber.length() != 15) {
            throw logAndCreateException(fieldName, formatCode);
        }
        if (!accountNumber.startsWith("015")) {
            throw logAndCreateException(fieldName, formatCode);
        }
    }

    protected void validateCurrency(Currency currency, String fieldName, int errorCode) {
        if (currency == null) {
            throw logAndCreateException(fieldName, errorCode);
        }
        try {
            Currency.fromValue(currency.toString());
        } catch (IllegalArgumentException e) {
            throw logAndCreateException(fieldName, errorCode, e);
        }
    }

    protected String accountNameOrFallback(LocalPayment localPayment) {
        Creditor creditor = localPayment.getCreditor();
        if (creditor == null) {
            return ACCOUNT_NAME_NA;
        }
        String name = creditor.getCreditorName();
        return name != null && !name.isBlank() ? name : ACCOUNT_NAME_NA;
    }

    protected ParameterException logAndCreateException(int errorCode) {
        log.error("Overseas payment validation failed, errorCode={}", errorCode);
        return new ParameterException(errorCode);
    }

    protected ParameterException logAndCreateException(String fieldName, int errorCode) {
        log.error("Overseas payment validation failed, field={}, errorCode={}", fieldName, errorCode);
        return new ParameterException(fieldName, errorCode);
    }

    protected ParameterException logAndCreateException(String fieldName, int errorCode, Throwable cause) {
        log.error("Overseas payment validation failed, field={}, errorCode={}", fieldName, errorCode, cause);
        return new ParameterException(fieldName, errorCode);
    }
}
