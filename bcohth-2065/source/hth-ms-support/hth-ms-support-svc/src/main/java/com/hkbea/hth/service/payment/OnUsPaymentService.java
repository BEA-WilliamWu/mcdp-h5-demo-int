package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.bg3.model.Bgec0760Request;
import com.hkbea.hth.bg3.model.Bgec0760RequestDfhcommarea;
import com.hkbea.hth.bg3.model.Bgec0760Response;
import com.hkbea.hth.bg3.model.Bgec076Response;
import com.hkbea.hth.bg3.model.E076Input;
import com.hkbea.hth.bg3.model.QaechccResponse;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.DfhcommareaReq7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0CacI;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0Input;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Eh7e0Record;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.QaechccReq7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Eh7e0Output;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.QaechccRes7e0;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.dto.TransferLimit;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.ProductDetails;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BaseResponseHttpEntity;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.NameMatchRequest;
import com.hkbea.hth.ipe.model.SupplementaryData;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.mapper.ProductDetailsMapper;
import com.hkbea.hth.model.AccountProductType;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.pe3.model.Peechi11Request;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommarea;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechcc;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11Input;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11InputEhi11CiIdI;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11InputEhi11KeyAccI;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner;
import com.hkbea.hth.pe3.model.Peechi11Response;
import com.hkbea.hth.service.FxServiceImpl;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class OnUsPaymentService {
    private static final Logger log = LoggerFactory.getLogger(OnUsPaymentService.class);

    // ONUS supported currencies：HKD, USD, EUR, AUD, CAD, CHF, CNY, GBP, JPY, NZD, SGD
    private static final Set<Currency> ONUS_SUPPORTED_CURRENCIES = EnumSet.of(Currency.HKD, Currency.USD,Currency.CNY
            ,Currency.EUR,Currency.AUD,Currency.CAD
            ,Currency.CHF,Currency.GBP,Currency.JPY
            ,Currency.NZD,Currency.SGD);

    // ===================== Bank Transfer =====================
    private static final String INTERNAL = "INTERNAL";
    private static final String BGEC0760_SUCCESS_CODE = PaymentConstants.BG_RETURN_CODE_0000;
    private static final String TRANSFER_BLOCK = "Y";

    @Resource
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;
    @Autowired
    private DebtorAddressService debtorAddressService;
    @Autowired
    private Pe3Api pe3Api;
    @Autowired
    private ScameterValidatorUtil scameterValidatorUtil;
    @Autowired
    private LocalPaymentApi localPaymentApi;
    @Autowired
    private PaymentInternalMapper paymentInternalMapper;
    @Autowired
    private CommonHeader headers;
    @Autowired
    private InternalPayeeMapper internalPayeeMapper;
    @Autowired
    private FxServiceImpl fxApiService;
    @Autowired
    private PartyAccountMapper partyAccountMapper;
    @Autowired
    private TransferLimitService transferLimitService;
    @Autowired
    private PaymentCommonService paymentCommonService;
    @Autowired
    private ProductDetailsMapper productDetailsMapper;
    @Autowired
    private PayemtRoutApi payemtRoutApi;
    @Autowired
    private CacheHelperUtils cacheHelperUtils;

    /**
     * On-Us (internal transfer) payment flow: validate, enrich the routing
     * request accepted by smart-routing in place, then submit under the
     * internal transfer-limit cycle.
     */
    public Payment processPayment(LocalPayment localPayment, BatchRoutingCreditTransferRequest routingRequest) {
        if (localPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        PaymentValidationSupport.validateOnUsRules(localPayment);
        prepareOnUsCreditorAccount(localPayment);
        validateBankTransfer(localPayment);

        String toAccount = AccountUtils.ext2intAccNo(localPayment.getToAccount().getAccountNumber(),
                localPayment.getToAccount().getAccountType().value().substring(0, 1), localPayment.getToAccount().getCurrency().toString());
        localPayment.getToAccount().setInternalAccountNumber(toAccount);
        String fromAccount = AccountUtils.ext2intAccNo(localPayment.getFromAccount().getAccountNumber(),
                localPayment.getFromAccount().getAccountType().value().substring(0, 1), localPayment.getFromAccount().getCurrency());
        localPayment.getFromAccount().setInternalAccountNumber(fromAccount);
        validateFromAndToAccount(fromAccount, toAccount);
        validateBeneficiaryAccount(toAccount, localPayment.getTxnAmount().getCurrency().toString());
        validatePaymentTransfer(toAccount, localPayment.getTxnAmount().getCurrency().toString(),
                fromAccount, localPayment.getToAccount().getAccountNumber(),
                localPayment.getCreditor().getCreditorName());
        scameterValidatorUtil.checkBankTransfer(localPayment);
        localPayment.setPayeeId(resolvePayeeId(localPayment));

        applyOnUsRoutingDetails(routingRequest, localPayment);
        return processWithPaymentLimit(localPayment, buildInternalLimit(localPayment), routingRequest);
    }

    /**
     * Writes the On-Us (internal book transfer) details onto the routing request
     * that was already validated by smart-routing. Field values mirror the
     * previous persistence-request mapping.
     */
    private void applyOnUsRoutingDetails(BatchRoutingCreditTransferRequest routingRequest, LocalPayment localPayment) {
        RoutingDetail detail = new RoutingDetail(localPayment);
        PaymentRoutingPayload.applyTypeEnvelope(routingRequest,
                PaymentConstants.BG3_CHANNEL_REFERENCE_ONUS, PaymentConstants.PAYMENT_METHOD_INTERNAL);
        var txn = PaymentRoutingPayload.firstTransaction(routingRequest);

        txn.setRemittanceInformation(PaymentRoutingPayload.remittance(
                PaymentConstants.BG3_CUSTOMER_REMARKS_ONUS, localPayment.getPaymentDetails()));

        // Internal transfers carry the proprietary purpose code only, as before.
        String proprietary = localPayment.getCategoryPurpose() == null
                ? null : localPayment.getCategoryPurpose().getProprietary();
        if (proprietary != null) {
            com.hkbea.hth.ipe.model.Purpose purpose = new com.hkbea.hth.ipe.model.Purpose();
            purpose.setProprietary(proprietary);
            txn.setPurpose(purpose);
        }

        // Remitter name is intentionally blank for BG3 internal transfers.
        txn.setDebtor(PaymentRoutingPayload.corporateDebtor(localPayment.getFromAccount().getName()));
        txn.getDebtor().setPostalAddress(debtorAddressService.lookup(detail.debitInternal));

        String productCode = partyAccountMapper.selectProductCode(detail.debitInternal, localPayment.getPartyId());
        PartyAccount partyAccount = com.baomidou.mybatisplus.core.toolkit.StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                localPayment.getPartyId(), localPayment.getFromAccount().getAccountNumber(), productCode);

        txn.setDebtorAccount(PaymentRoutingPayload.debtorAccount(detail.debitInternal, detail.debitInternal,
                null, PaymentConstants.PAYER_ACCOUNT_TYPE_CSA, detail.fromCurrency,
                PaymentRoutingPayload.parseCompanyCode(partyAccount == null ? null : partyAccount.getCompanyCode()), detail.productCode));

        txn.setCreditor(PaymentRoutingPayload.creditor(
                com.hkbea.hth.ipe.model.Creditor.TypeEnum.ACCOUNT, detail.creditorName,
                null, null, null, null, null, null));

        txn.setCreditorAccount(PaymentRoutingPayload.creditorAccount(detail.creditorName,
                detail.creditInternal, detail.toCurrency, null, null, null, null));

        txn.setCreditorAgent(PaymentRoutingPayload.creditorAgent(
                null, detail.memberId, null, null, null, null, null));

        SupplementaryData supplementaryData = new SupplementaryData();
        supplementaryData.setDebitInternalAccount(detail.debitInternal);
        supplementaryData.setCreditInternalAccount(detail.creditInternal);
        supplementaryData.setProductType(detail.productCode);
        txn.setSupplementaryData(supplementaryData);

        CustomerNotificationInformRequest notification = paymentCommonService.buildCustomerNotificationInfo(
                localPayment, PaymentConstants.NOTIFICATION_TYPE_EMAIL_SMS, localPayment.getPayeeId());
        txn.setNotification(PaymentRoutingPayload.notification(notification));
    }

    /**
     * Values shared by the On-Us routing payload blocks (computed once per call).
     */
    private final class RoutingDetail {
        private final String debitInternal;
        private final String creditInternal;
        private final String fromCurrency;
        private final String toCurrency;
        private final String creditorName;
        private final String memberId;
        /**
         * Product code looked up by EXTERNAL debtor account, as the legacy builder did.
         */
        private final String productCode;

        private RoutingDetail(LocalPayment localPayment) {
            this.debitInternal =  AccountUtils.ext2intAccNo(localPayment.getFromAccount().getAccountNumber(),
                    localPayment.getFromAccount().getAccountType().value().substring(0, 1), localPayment.getFromAccount().getCurrency());
            this.creditInternal = AccountUtils.ext2intAccNo(
                    localPayment.getToAccount().getAccountNumber(),
                    localPayment.getToAccount().getAccountType().value().substring(0, 1), localPayment.getToAccount().getCurrency().toString());
            this.fromCurrency = localPayment.getFromAccount().getCurrency();
            this.toCurrency = localPayment.getToAccount().getCurrency().toString();
            this.creditorName = localPayment.getCreditor().getCreditorName();
            this.memberId = localPayment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId();
            this.productCode = partyAccountMapper.selectProductCode(this.debitInternal, localPayment.getPartyId());
        }
    }

    /**
     * Executes the payment inside the internal transfer-limit cycle (deduct ->
     * submit -> reverse on rejection).
     */
    private Payment processWithPaymentLimit(LocalPayment localPayment, TransferLimit limits, BatchRoutingCreditTransferRequest routingRequest) {
        // The limit API identifies the customer by partyId, while the payment
        // contract and persistence must keep the debtor account number.
        BeaAccount limitAccount = PaymentServiceSupport.limitAccount(localPayment.getFromAccount(), localPayment.getPartyId());

        // 1. Deduct limit; abort the payment if EBK rejects the deduction
        TransferLimit remaining = transferLimitService.updateTransferLimit(limits, limitAccount);
        if (remaining == null) {
            log.error("Limit deduction failed, payment aborted. Account={}", limitAccount.getAccountNumber());
            throw logAndCreateException(HTHSupportErrorMapping.LIMIT_DEDUCTION_FAILED);
        }

        // 2. Execute the payment
        Payment payment = buildBankTransferResponse(localPayment, routingRequest);

        // 3. Recover the deducted limit if the payment was rejected
        if (payment.getStatus() == Payment.StatusEnum.REJECTED) {
            transferLimitService.reverseTransferLimit(limits, limitAccount);
        }
        return payment;
    }

    /**
     * Persists a pending internal payment record, submits the enriched routing
     * request to IPE and updates the record with the outcome (always updated,
     * even when the IPE submission fails).
     */
    private Payment buildBankTransferResponse(LocalPayment localPayment, BatchRoutingCreditTransferRequest routingRequest) {
        Payment payment = new Payment();
        payment.setTxnRef(PaymentRoutingPayload.endToEndId(routingRequest));
        // Phase 1: basic payment info is on record before the IPE submission.
        String recordId = insertPendingInternalRecord(localPayment, routingRequest, payment);
        TransferPaymentResultQueryResponse paymentResult = PaymentServiceSupport.submitRoutingRequest(
                payemtRoutApi, log, routingRequest, payment,
                PaymentRoutingPayload.endToEndId(routingRequest),
                PaymentResponseSupport.RejectMessagePolicy.ERROR_MESSAGE_ONLY);
        payment.setTxnTime(OffsetDateTime.now().withNano(0));
        payment.setOriginalEndtoendID(PaymentModelConvert.EndToEndId(localPayment));
        payment.setOriginalTransactionID(localPayment.getTransactionID());
        payment.setDebitAccountNumber(localPayment.getFromAccount().getInternalAccountNumber());
        payment.setCreditAccountNumber(localPayment.getToAccount().getInternalAccountNumber());
        // Phase 2: the pending record is updated with the IPE outcome.
        PaymentServiceSupport.validatePaymentResult(localPayment, paymentResult);
        PaymentServiceSupport.updateInternalRecord(paymentInternalMapper, log, INTERNAL, recordId, payment, paymentResult, localPayment);
        cacheHelperUtils.validatePaymentResult(paymentResult);
        return payment;
    }

    /**
     * Inserts a pending payment record into HTH_PAYMENT_INTERNAL before the IPE
     * submission. STATUS, IPE_STATUS and IPE_REF_NO are left null on the pending
     * row and populated by the post-submission update. Best-effort (log-only on
     * failure).
     */
    private String insertPendingInternalRecord(LocalPayment localPayment, BatchRoutingCreditTransferRequest routingRequest, Payment payment) {
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            PaymentInternal entity = new PaymentInternal();
            entity.setId(id);
            entity.setDebitAccountId(localPayment.getFromAccount().getAccountNumber());
            entity.setDebitAccountCur(localPayment.getFromAccount().getCurrency());
            entity.setDebitAccountType(localPayment.getFromAccount().getAccountType().value());
            entity.setInstdAmount(localPayment.getTxnAmount().getAmount());
            entity.setProductCode(PaymentRoutingPayload.debtorProductCode(routingRequest));
            entity.setPartyId(localPayment.getPartyId());
            entity.setPaymentType(INTERNAL);
            entity.setCreditAccountId(localPayment.getToAccount().getAccountNumber());
            entity.setCreditAccountCur(localPayment.getToAccount().getCurrency().toString());
            entity.setCreditAccountName(localPayment.getCreditor().getCreditorName());
            entity.setCustomerTransactionId(localPayment.getTransactionID());
            entity.setExecutionDate(localPayment.getSettlementDate());
            entity.setCreditMemberId(localPayment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId());
            entity.setPaymentId(payment.getTxnRef());
            entity.setStatus(null);
            entity.setIpeStatus(null);
            entity.setIpeRefNo(null);
            entity.setCreationDate(LocalDateTime.now());
            entity.setPaymentDate(new Date());
            entity.setCreatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedDate(new Date());
            entity.setMessageId(headers.getMessageId());
            entity.setAdcSuspiciousInd(localPayment.getSuspiciousInd());
            entity.setEndToEndId(PaymentModelConvert.EndToEndId(localPayment));
            entity.setDebitInternalAccount(localPayment.getFromAccount().getInternalAccountNumber());
            paymentInternalMapper.insert(entity);
            log.info("On-Us pending payment internal record inserted, customerTransactionId={}",
                    localPayment.getTransactionID());
        } catch (Exception e) {
            log.error("Failed to insert On-Us pending payment internal record, customerTransactionId={}, error={}",
                    localPayment.getTransactionID(), e.getMessage(), e);
        }
        return id;
    }

    /**
     * Enriches the creditor account for On-Us: resolves account type and
     * currency via the BG9CH7E0 account enquiry when the caller did not
     * provide them (SSA/AIO accounts default to SSA/HKD).
     */
    private void prepareOnUsCreditorAccount(LocalPayment localPayment) {
        PaymentToAccount toAccount = localPayment.getToAccount();
        if (toAccount == null) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING);
        }
        String toAccountType = toAccount.getAccountType() == null ? "" : toAccount.getAccountType().value();
        String toAccountCurrency = toAccount.getCurrency() == null ? "" : toAccount.getCurrency().toString();
        String accountNumber = PaymentValidationSupport.creditorAccountNumber(toAccount);
        if (!StringUtils.hasText(accountNumber)) {
            throw new ParameterException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING);
        }
        if (toAccount.getAccountType() != null && toAccount.getCurrency() != null) {
            return;
        }
        String accountId18 = AccountUtils.ext2intAccNo(accountNumber, toAccountType, toAccountCurrency);
        Eh7e0Output accountInfo = queryOnUsAccountInfo(accountId18);
        if (accountInfo == null) {
            throw new ParameterException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
        }

        if (StringUtils.hasText(toAccountType)) {
            toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.valueOf(toAccountType));
        } else {
            String accountType = AccountProductType.getValueByProductCode(accountInfo.getEh7e0CodProductO());
            if (StringUtils.hasText(accountType)) {
                toAccount.setAccountType(
                        AccountProductType.containsProductType(accountInfo.getEh7e0CodProductO(), AccountProductType.SSA)
                                || AccountProductType.containsProductType(accountInfo.getEh7e0CodProductO(), AccountProductType.AIO)
                                ? PaymentToAccount.AccountTypeEnum.SSA
                                : PaymentToAccount.AccountTypeEnum.valueOf(accountType));
            } else {
                ProductDetails productDetails = getProductDetails(accountInfo.getEh7e0CodProductO());
                String productCurrency = productDetails == null ? null : productDetails.getProductCurrency();
                if (productCurrency != null && (productCurrency.equalsIgnoreCase("NA")
                        || productCurrency.equalsIgnoreCase("-"))) {
                    toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);
                }
            }
        }

        cacheHelperUtils.saveMtbInfo(mtbInfo -> mtbInfo.setToAccountType(toAccount.getAccountType().name()));

        if (toAccount.getCurrency() == null) {
            if (toAccount.getAccountType() == null) {
                throw new ParameterException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
            }
            if (AccountProductType.SSA.toString().equals(toAccount.getAccountType().toString())
                    || AccountProductType.AIO.toString().equals(toAccount.getAccountType().toString())) {
                toAccount.setCurrency(Currency.HKD);
            } else {
                toAccount.setCurrency(localPayment.getTxnAmount().getCurrency());
            }
        }
    }

    /**
     * Queries BG9CH7E0 for creditor account info; returns the output record on
     * success code "00", otherwise {@code null}.
     */
    private Eh7e0Output queryOnUsAccountInfo(String accountId18) {
        Bgech7e0Request request = buildOnUsAccountInfoRequest(accountId18);
        Bgech7e0Response response = bgAccountApi.queryAccountInfo(request);
        if (response == null || response.getDfhcommarea() == null
                || response.getDfhcommarea().getQaechcc() == null
                || response.getDfhcommarea().getQaechcc().getBgech7e0() == null) {
            return null;
        }
        Eh7e0Output output = response.getDfhcommarea().getQaechcc().getBgech7e0().getEh7e0Output();
        return output != null && PaymentConstants.SUCC_CODE_00.equals(output.getEh7e0CodReturnO())
                ? output : null;
    }

    /**
     * Builds the BG9CH7E0 enquiry request, splitting the 18-char internal
     * account into entity/registration/account segments.
     */
    private Bgech7e0Request buildOnUsAccountInfoRequest(String acct18) {
        Bgech7e0Request request = new Bgech7e0Request();
        DfhcommareaReq7e0 dfh = new DfhcommareaReq7e0();
        QaechccReq7e0 qaechcc = new QaechccReq7e0();
        qaechcc.setQaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN);
        qaechcc.setQaechccApplId(PaymentConstants.APPLICATION_ID_BG);
        qaechcc.setQaechccCollecName(PaymentConstants.COLLECTION_NAME_BG9CH7E0);
        qaechcc.setQaechccCollecFunc("");
        qaechcc.setQaechccDateOp("");
        qaechcc.setQaechccCodEntity(PaymentConstants.ENTITY_CODE_0015);
        qaechcc.setQaechccCodBrn("");
        qaechcc.setQaechccCodTerm("");
        qaechcc.setQaechccCodUser("");
        qaechcc.setQaechccErrData("");
        qaechcc.setQaechccFrArea("");

        Eh7e0CacI cac = new Eh7e0CacI();
        cac.setEh7e0EntI(Integer.parseInt(acct18.substring(PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS)));
        cac.setEh7e0CenRegI(Integer.parseInt(acct18.substring(PaymentConstants.EH7E0_CEN_REG_ST_POS, PaymentConstants.EH7E0_CEN_REG_END_POS)));
        cac.setEh7e0AccI(Long.parseLong(acct18.substring(PaymentConstants.EH7E0_ACC_ST_POS)));
        Eh7e0Input input = new Eh7e0Input();
        input.setEh7e0CacI(cac);
        input.setEh7e0FccI("");
        Eh7e0Record record = new Eh7e0Record();
        record.setEh7e0Input(input);
        Bgech7e0 bgech7e0 = new Bgech7e0();
        bgech7e0.setEh7e0Record(record);
        qaechcc.setBgech7e0(bgech7e0);
        dfh.setQaechcc(qaechcc);
        request.setDfhcommarea(dfh);
        return request;
    }

    /**
     * Product lookup used to derive creditor account type/currency.
     */
    private ProductDetails getProductDetails(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return null;
        }
        LambdaQueryWrapper<ProductDetails> query = Wrappers.lambdaQuery();
        query.eq(ProductDetails::getProductCode, productCode);
        return productDetailsMapper.selectOne(query);
    }

    /**
     * Determines the limit category for an INTERNAL transfer:
     * pre-designated (desLim) when the payee is a registered internal payee of the
     * payer's party, otherwise non-designated (ndesLim).
     */
    private TransferLimit buildInternalLimit(LocalPayment localPayment) {
        //  amount should convert to hkd
        BigDecimal amount = localPayment.getTxnAmount().getAmount();
        try {
            if (!Currency.HKD.toString().equals(localPayment.getFromAccount().getCurrency())) {
                amount = fxApiService.calculateHkdAmount(localPayment.getFromAccount().getCurrency(), amount);
            }
        } catch (Exception e) {
            log.error("amount  convert to hkd:{}", e.getMessage(), e);
        }
        if (isOwnAccount(localPayment)) {
            return TransferLimit.ownAccount(amount);
        }
//        String payeeId = resolvePayeeId(localPayment);
//        localPayment.setPayeeId(payeeId);
        return localPayment.getPayeeId() != null
                ? TransferLimit.preDesignatedTransfer(amount)
                : TransferLimit.nonDesignatedTransfer(amount);
    }

    /**
     * True when both debtor and creditor internal accounts belong to the
     * caller's own CSA account list (own-account transfer).
     */
    private boolean isOwnAccount(LocalPayment localPayment) {
        List<String> strings = partyAccountMapper.selectActiveCsaAccountNumbers(localPayment.getPartyId());
        if (CollectionUtils.isEmpty(strings)) {
            return false;
        }
        String toAccount = AccountUtils.ext2intAccNo(localPayment.getToAccount().getAccountNumber(),
                localPayment.getToAccount().getAccountType().value().substring(0, 1), localPayment.getToAccount().getCurrency().toString());
        String fromAccount = AccountUtils.ext2intAccNo(localPayment.getFromAccount().getAccountNumber(),
                localPayment.getFromAccount().getAccountType().value().substring(0, 1), localPayment.getFromAccount().getCurrency());
        return strings.contains(toAccount) && strings.contains(fromAccount);
    }

    /**
     * Looks up the registered internal payee id for the creditor account.
     */
    private String resolvePayeeId(LocalPayment localPayment) {
        return internalPayeeMapper.selectInternalPayeeId(localPayment.getPartyId(), localPayment.getToAccount().getAccountNumber());
    }

    /**
     * Builds the BG9CH7E0 payment-transfer enquiry request for an 18-char
     * internal account and currency.
     */
    private Bgech7e0Request buildBGECH7E0Request(String alnovaAcc, String currency) {
        Integer eh7e0EntI = Integer.valueOf(alnovaAcc.substring(PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS));
        Integer eh7e0CenRegI = Integer.valueOf(alnovaAcc.substring(PaymentConstants.EH7E0_CEN_REG_ST_POS, PaymentConstants.EH7E0_CEN_REG_END_POS));
        Long eh7e0AccI = Long.valueOf(alnovaAcc.substring(PaymentConstants.EH7E0_ACC_ST_POS));

        Eh7e0CacI cacI = new Eh7e0CacI();
        cacI.setEh7e0EntI(eh7e0EntI);
        cacI.setEh7e0CenRegI(eh7e0CenRegI);
        cacI.setEh7e0AccI(eh7e0AccI);

        Eh7e0Input eh7e0Input = new Eh7e0Input();
        eh7e0Input.setEh7e0CacI(cacI);
        eh7e0Input.setEh7e0FccI(currency);

        Eh7e0Record eh7e0Record = new Eh7e0Record();
        eh7e0Record.setEh7e0Input(eh7e0Input);

        Bgech7e0 bgech7e0 = new Bgech7e0();
        bgech7e0.setEh7e0Record(eh7e0Record);

        QaechccReq7e0 qaechcc = new QaechccReq7e0();
        qaechcc.setQaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN);
        qaechcc.setQaechccApplId(PaymentConstants.APPLICATION_ID_BG);
        qaechcc.setQaechccCollecName(PaymentConstants.COLLECTION_NAME_BG9CH7E0);
        qaechcc.setQaechccCollecFunc("");
        qaechcc.setQaechccDateOp(LocalDate.now().toString());
        qaechcc.setQaechccCodEntity(alnovaAcc.substring(PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS));
        qaechcc.setQaechccCodBrn("");
        qaechcc.setQaechccCodTerm("");
        qaechcc.setQaechccCodUser("");
        qaechcc.setQaechccErrData("");
        qaechcc.setBgech7e0(bgech7e0);
        qaechcc.setQaechccFrArea("");

        DfhcommareaReq7e0 dfhcommarea = new DfhcommareaReq7e0();
        dfhcommarea.setQaechcc(qaechcc);

        Bgech7e0Request request = new Bgech7e0Request();
        request.setDfhcommarea(dfhcommarea);

        return request;
    }

    /**
     * Compare whether two accounts belong to the same customer by HKID information.
     * Rules:
     * 1. Call PE9CHI11 by buildRequest().
     * 2. Verify ehi11CodReturn = "00".
     * 3. Get the first record from ehi11DtaBasicO.
     * 4. Compare EHI11-COD-ID-O, EHI11-KEY-ID-O and EHI11-COD-ISS-CTRY-O.
     * 5. If both customer records are null, return true.
     * 6. If only one customer record is null, return false.
     *
     * @param accountNo1 source account
     * @param accountNo2 target account
     * @return true if same customer, otherwise false
     */
    public boolean isSameCustomer(String accountNo1, String accountNo2) {

        log.info("Start compare customer, accountNo1={}, accountNo2={}", accountNo1, accountNo2);

        Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner customer1 = null;
        Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner customer2 = null;

        try {
            Peechi11Response response1 = pe3Api.getCustomerBasicInfo(buildRequest(accountNo1));

            if (response1 != null && response1.getDfhcommarea() != null && response1.getDfhcommarea().getQaechcc() != null) {

                var answer1 = response1.getDfhcommarea().getQaechcc().getPeechi11().getEhi11Output().getEhi11Answer();

                if (answer1 != null && PaymentConstants.SUCC_CODE_00.equals(answer1.getEhi11CodReturn())) {

                    List<Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner> basicList1 =
                            response1.getDfhcommarea().getQaechcc().getPeechi11().getEhi11Output().getEhi11Data().getEhi11DtaBasicO();

                    if (basicList1 != null && !basicList1.isEmpty()) {
                        customer1 = basicList1.getFirst();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Get customer info failed, accountNo={}", accountNo1, e);
        }

        try {
            Peechi11Response response2 = pe3Api.getCustomerBasicInfo(buildRequest(accountNo2));

            if (response2 != null && response2.getDfhcommarea() != null && response2.getDfhcommarea().getQaechcc() != null) {

                var answer2 = response2.getDfhcommarea().getQaechcc().getPeechi11().getEhi11Output().getEhi11Answer();

                if (answer2 != null && PaymentConstants.SUCC_CODE_00.equals(answer2.getEhi11CodReturn())) {

                    List<Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner> basicList2 =
                            response2.getDfhcommarea().getQaechcc().getPeechi11().getEhi11Output().getEhi11Data().getEhi11DtaBasicO();

                    if (basicList2 != null && !basicList2.isEmpty()) {
                        customer2 = basicList2.getFirst();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Get customer info failed, accountNo={}", accountNo2, e);
        }

        if (customer1 == null && customer2 == null) {
            log.info("Both customer info are null, accountNo1={}, accountNo2={}, result=true", accountNo1, accountNo2);
            return true;
        }

        if (customer1 == null || customer2 == null) {
            log.info("One customer info is null, accountNo1={}, accountNo2={}, result=false", accountNo1, accountNo2);
            return false;
        }

        boolean result = Objects.equals(customer1.getEhi11CodIdO(), customer2.getEhi11CodIdO())
                && Objects.equals(customer1.getEhi11KeyIdO(), customer2.getEhi11KeyIdO())
                && Objects.equals(customer1.getEhi11CodIssCtryO(), customer2.getEhi11CodIssCtryO());

        log.info("Compare result={}, accountNo1[codId={},keyId={},issCtry={}], accountNo2[codId={},keyId={},issCtry={}]",
                result,
                customer1.getEhi11CodIdO(),
                customer1.getEhi11KeyIdO(),
                customer1.getEhi11CodIssCtryO(),
                customer2.getEhi11CodIdO(),
                customer2.getEhi11KeyIdO(),
                customer2.getEhi11CodIssCtryO());

        return result;
    }

    /**
     * Builds the PE9CHI11 customer-info request for an 18-char internal
     * account (entity + branch + account segments).
     */
    private Peechi11Request buildRequest(String accountNo) {

        String entity = accountNo.substring(PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS);
        String branch = accountNo.substring(PaymentConstants.EH7E0_CEN_REG_ST_POS, PaymentConstants.EH7E0_CEN_REG_END_POS);
        String account = accountNo.substring(PaymentConstants.EH7E0_ACC_ST_POS);

        return new Peechi11Request()
                .dfhcommarea(
                        new Peechi11RequestDfhcommarea()
                                .qaechcc(
                                        new Peechi11RequestDfhcommareaQaechcc()
                                                .qaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN)
                                                .qaechccApplId("PE")
                                                .qaechccCollecName("PE9CHI11")
                                                .qaechccCollecFunc("")
                                                .qaechccDateOp(LocalDate.now().toString())
                                                .qaechccCodEntity(entity)
                                                .qaechccCodBrn("")
                                                .qaechccCodTerm("")
                                                .qaechccCodUser("")
                                                .qaechccErrData("")
                                                .peechi11(
                                                        new Peechi11RequestDfhcommareaQaechccPeechi11()
                                                                .ehi11Input(
                                                                        new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11Input()
                                                                                .ehi11CodEntityI(entity)
                                                                                .ehi11CiIdI(
                                                                                        new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11InputEhi11CiIdI()
                                                                                                .ehi11NumCusI("")
                                                                                )
                                                                                .ehi11KeyAccI(
                                                                                        new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11InputEhi11KeyAccI()
                                                                                                .ehi11KeyEntityI(entity)
                                                                                                .ehi11BrnOpenI(branch)
                                                                                                .ehi11NumAccountI(account)
                                                                                )
                                                                )
                                                )
                                                .qaechccFrArea("")
                                )
                );
    }

    private void validateAccountName(String accountName) {
        PaymentServiceSupport.validateAccountName(log, accountName);
    }

    /**
     * Build BGEC0760 request.
     * <p>
     * alnovaAcc format:
     * Entity(4) + Registration(4) + Account(10)
     * <p>
     * Example:
     * 001505216812032777
     * ->
     * e076Ent = 0015
     * e076CenRegistration = 0521
     * e076Acc = 6812032777
     */
    private Bgec0760Request buildBGEC0760Request(String alnovaAcc, String currency) {
        String e076Ent = alnovaAcc.substring(PaymentConstants.EH7E0_ENT_ST_POS, PaymentConstants.EH7E0_ENT_END_POS);
        String e076CenRegistration = alnovaAcc.substring(PaymentConstants.EH7E0_CEN_REG_ST_POS, PaymentConstants.EH7E0_CEN_REG_END_POS);
        String e076Acc = alnovaAcc.substring(PaymentConstants.EH7E0_ACC_ST_POS);

        Bgec0760Request request = new Bgec0760Request();

        Bgec0760RequestDfhcommarea dfhcommarea = new Bgec0760RequestDfhcommarea();

        QaechccResponse qaechcc =
                new QaechccResponse()
                        .qaechccTypChannel(PaymentConstants.CHANNEL_TYPE_IN)
                        .qaechccApplId(PaymentConstants.APPLICATION_ID_BG)
                        .qaechccCollecName(PaymentConstants.COLLECTION_NAME_BG9C0760)
                        .qaechccCollecFunc("")
                        .qaechccDateOp(LocalDate.now().toString())
                        .qaechccCodEntity(e076Ent)
                        .qaechccCodBrn("")
                        .qaechccCodTerm("")
                        .qaechccCodUser("")
                        .qaechccErrData("");

        E076Input e076Input =
                new E076Input()
                        .e076Ent(e076Ent)
                        .e076CenRegistration(e076CenRegistration)
                        .e076Acc(e076Acc)
                        .e076Fcc(currency)
                        .e076FlgNames("")
                        .e076Option(PaymentConstants.BGEC0760_OPTION);

        Bgec076Response bgec076 =
                new Bgec076Response()
                        .e076Input(e076Input);
        qaechcc.setBgec076(bgec076);
        qaechcc.setQaechccFrArea("");
        dfhcommarea.setQaechcc(qaechcc);
        request.setDfhcommarea(dfhcommarea);

        return request;
    }

    // ===================== Shared Validation Methods =====================

    /**
     * Validate payment transfer eligibility by BG9CH7E0 account enquiry.
     * Validation Rule:
     * 1. qaechccErrData = "0000"
     * 2. eh7e0PaymentTransfer = "Y"
     *
     * @param toAcc    internal account no
     * @param currency account currency
     */
    private void validatePaymentTransfer(String toAcc, String currency, String fromAcc, String exToAcc, String accountName) {

        try {
            Bgech7e0Response response = bgAccountApi.queryAccountInfo(buildBGECH7E0Request(toAcc, currency));

            if (response == null || response.getDfhcommarea() == null || response.getDfhcommarea().getQaechcc() == null) {

                log.error("Payment transfer validation failed, response is null");
                throw new ParameterException(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK);
            }

            QaechccRes7e0 qaechcc = response.getDfhcommarea().getQaechcc();

            if (!BGEC0760_SUCCESS_CODE.equals(qaechcc.getQaechccErrData())) {

                log.error("Payment transfer validation failed, qaechccErrData={}", qaechcc.getQaechccErrData());

                throw new ParameterException(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK);
            }
            if (StringUtils.hasText(accountName)) {
                String alnovaAccountName = qaechcc.getBgech7e0().getEh7e0Output().getEh7e0AccLongEnameO();
                NameMatchRequest request = new NameMatchRequest();
                request.alnovaAccountNum(toAcc);
                request.setAccountType("person");
                request.setChannel(PaymentConstants.CHANNEL_HTH);
                request.setAlnovaAccountName(alnovaAccountName);
                request.setCurrencyCode(currency);
                request.setIsJointAccount("N");
                request.setSenderRefNum(toAcc);
                request.setOsnUrNum(exToAcc);
                request.setSwiftBeneficialName(accountName);
                BaseResponseHttpEntity baseResponseHttpEntity = localPaymentApi.nameMatch(request);
                if (!baseResponseHttpEntity.getResult()) {
                    throw new ParameterException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_NOMATCHING);
                }
            }

            String paymentTransfer = qaechcc.getBgech7e0().getEh7e0Output().getEh7e0PaymentTransfer();

            if (TRANSFER_BLOCK.equalsIgnoreCase(paymentTransfer)) {
                boolean sameCustomer = isSameCustomer(fromAcc, toAcc);
                if (!sameCustomer) {
                    log.info("Payment transfer validation failed, paymentTransfer={}", paymentTransfer);
                    throw new ParameterException(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK);
                }


            }

            log.info("Payment transfer validation passed, paymentTransfer={}", paymentTransfer);

        } catch (ParameterException e) {
            throw e;
        } catch (Exception e) {
            log.error("BG9CH7E0 payment transfer validation failed, account={}", toAcc, e);

            throw new ParameterException(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK);
        }
    }

    /**
     * Validate beneficiary account by BGEC0760.
     * <p>
     * Validation Rule:
     * 1. API call successful
     * 2. qaechccErrData = "0000"
     *
     * @param alnovaAcc Internal account no (18 digits)
     * @param currency  Account currency
     */
    private void validateBeneficiaryAccount(String alnovaAcc, String currency) {

        try {
            Bgec0760Response response = bgAccountApi.checkAccountExist(buildBGEC0760Request(alnovaAcc, currency));

            if (response == null || response.getDfhcommarea() == null || response.getDfhcommarea().getQaechcc() == null) {

                throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NOT_FOUND);
            }

            String qaechccErrData = response.getDfhcommarea().getQaechcc().getQaechccErrData();

            if (!BGEC0760_SUCCESS_CODE.equals(qaechccErrData)) {

                log.error("BGEC0760 account not found, account={}, qaechccErrData={}", alnovaAcc, qaechccErrData);

                throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NOT_FOUND);
            }
        } catch (ParameterException e) {
            throw e;
        } catch (Exception e) {
            log.error("BGEC0760 account enquiry failed, account={}", alnovaAcc, e);
            throw logAndCreateException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NOT_FOUND);
        }
    }

    /**
     * Rejects transfers where debtor and creditor accounts are identical.
     */
    private void validateFromAndToAccount(String fromAccount, String toAccount) {
        if (fromAccount.equals(toAccount)) {
            throw logAndCreateException(HTHSupportErrorMapping.ACCOUNT_CANNOT_BE_THE_SAME);
        }

    }

    /**
     * On-Us validation orchestrator: debtor account, amount, creditor account,
     * payee name and currency consistency. IPE owns balance sufficiency checks.
     */
    private void validateBankTransfer(LocalPayment localPayment) {
        if (localPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        validateLocalPaymentFromAccount(localPayment.getFromAccount());
        validateTxnAmount(localPayment.getTxnAmount());
        validateToAccountBea(localPayment.getToAccount());
        validateAccountName(localPayment.getCreditor().getCreditorName());
        validateOnUsCurrencyConsistency(localPayment);
        validateCurrencyConsistency(CurrencyUtils.parseLenient(localPayment.getFromAccount().getCurrency()),
                                    localPayment.getTxnAmount().getCurrency(),
                                    localPayment.getToAccount().getCurrency());

    }

    /**
     * Debtor account checks: 14-15 digit "015" account and valid currency.
     */
    private void validateLocalPaymentFromAccount(BeaAccount fromAccount) {
        if (fromAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING);
        }
        validateLocalAccountNumber(fromAccount.getAccountNumber());
        validateCurrency(CurrencyUtils.parseLenient(fromAccount.getCurrency()), HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        if (fromAccount.getAccountType() == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING);
        }
    }

    private void validateTxnAmount(TxnAmount txnAmount) {
        PaymentServiceSupport.validateTxnAmount(log, txnAmount);
    }

    /**
     * Creditor account checks: account number format and currency.
     */
    private void validateToAccountBea(PaymentToAccount toAccount) {
        if (toAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING);
        }

        validateAccountNumber(toAccount.getAccountNumber());
        validateCurrency(toAccount.getCurrency(), HTHSupportErrorMapping.CREDITOR_CURRENCY_MISSING_OR_INVALID);
    }

    private void validateAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    private void validateLocalAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateLocalAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    private void validateCurrency(Currency currency, int errorCode) {
        PaymentServiceSupport.validateCurrency(log, currency, errorCode);
    }

    /**
     * On-Us currency rules: debit currency must be supported; payment currency
     * must equal creditor currency.
     */
    private void validateOnUsCurrencyConsistency(LocalPayment localPayment) {
        Currency fromCurrency = CurrencyUtils.parseLenient(localPayment.getFromAccount().getCurrency());
        Currency txnCurrency = localPayment.getTxnAmount().getCurrency();
        Currency toCurrency = localPayment.getToAccount().getCurrency();

        // FPS only supports HKD/CNY for debit currency
        if (!ONUS_SUPPORTED_CURRENCIES.contains(fromCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        }

        // txnCurrency must match toCurrency
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    /**
     * Full consistency: debit currency must equal payment currency, and payment
     * currency must equal creditor currency (On-Us is single-currency only).
     */
    private void validateCurrencyConsistency(Currency fromCurrency, Currency txnCurrency, Currency toCurrency) {
        if (fromCurrency != txnCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES);
        }
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    // ===================== Exception Factory =====================

    private ParameterException logAndCreateException(int errorCode) {
        return PaymentServiceSupport.logAndCreateException(log, errorCode);
    }
}
