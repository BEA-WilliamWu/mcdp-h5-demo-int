package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.PaymentMethodEnum;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
import com.hkbea.hth.ipe.model.Tt;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.CategoryPurpose;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.FinancialInstitutionId;
import com.hkbea.hth.model.IntermediaryAgent;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.TtPaymentService;
import com.hkbea.hth.service.payment.TtPaymentServiceSupport;
import com.hkbea.hth.utils.CacheHelperUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Telegraphic Transfer (TT) payment: validation error codes, the enrich ->
 * submit -> persist flow and its degradation paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TtPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String CLOSE_ID = "CLOSE001";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    private static final String MESSAGE_ID = "UT-TT-MESSAGE-000001";
    private static final String CUSTOMER_TXN_ID = "UT-TT-CUST-TXN-000001";
    private static final String BATCH_ID = "BATCH-TT-1";

    @InjectMocks
    private TtPaymentService ttPaymentService;

    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private PaymentInternalMapper paymentInternalMapper;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private AccountApi accountApi;
    @Mock
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;
    @Mock
    private CommonHeader headers;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
    @Mock
    private TtPaymentServiceSupport ttPaymentServiceSupport;
    @Mock
    private CacheHelperUtils cacheHelperUtils;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        lenient().when(headers.getMessageId()).thenReturn(MESSAGE_ID);
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(), anyString())).thenReturn("PAYER NAME");
        lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                        nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(activeAccessAccount()));
        lenient().when(bgAccountApi.queryAccountInfo(
                        any(com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request.class)))
                .thenReturn(OverseasPaymentServiceTest.buildAccountInfoResponse());
        lenient().when(internalPayeeMapper.selectInternationalPayeeId(anyString(), anyString()))
                .thenReturn(null);
        lenient().doNothing().when(cacheHelperUtils).validatePaymentResult(any());
        lenient().doNothing().when(cacheHelperUtils).saveMtbInfo(any());
    }

    // ===================== Validation =====================

    @ParameterizedTest
    @ValueSource(ints = {1, 34, 35, 36, 69, 70, 71, 104, 105, 106, 139, 140})
    void creditorNameIsPreservedAcrossDepositNameSegments(int length) {
        LocalPayment input = buildTtPayment(Currency.HKD);
        String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(6).substring(0, length);
        input.getCreditor().setCreditorName(name);
        BatchRoutingCreditTransferRequest request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = ttPaymentService.processPayment(input, request);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        var txn = request.getTransactions().getFirst();
        assertEquals(name, txn.getCreditor().getName());
        assertEquals(name, txn.getCreditorAccount().getAccountName());
        var remittance = txn.getSupplementaryData().getChatsRemittance();
        assertNotNull(remittance);
        assertEquals(name.substring(0, Math.min(35, length)), remittance.getDepositAccountName1());
        assertEquals(length <= 35 ? name : name.substring(35, Math.min(70, length)),
                remittance.getDepositAccountName2());
        assertEquals(length <= 70 ? null : name.substring(70, Math.min(105, length)),
                remittance.getDepositAccountName3());
        assertEquals(length <= 105 ? null : name.substring(105), remittance.getDepositAccountName4());
        verify(payemtRoutApi).submit(request);
    }

    @Test
    @DisplayName("missing toAccount is rejected with TT_PAYEE_INFO_MISSING")
    void nullToAccount_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("blank creditor name is rejected by the shared TT rules with CREDITORNAME_MISSING_OR_INVALID")
    void blankCreditorName_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().setCreditorName(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank beneficiary account number is rejected with TT_PAYEE_INFO_MISSING")
    void blankBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getToAccount().setAccountNumber(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing beneficiary currency defaults to the debit currency during the TT flow")
    void missingBeneficiaryCurrency_defaultsToDebitCurrency() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getToAccount().setCurrency(null);
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = assertDoesNotThrow(
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(Currency.HKD, request.getToAccount().getCurrency());
    }

    @Test
    @DisplayName("payment currency differing from beneficiary currency is rejected")
    void paymentCurrencyMismatch_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency differing from payment currency is rejected")
    void debitCurrencyMismatch_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getFromAccount().setCurrency(Currency.USD.toString());

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payee address line 1 is rejected")
    void missingPayeeAddress_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().getPostalAddress().setAddressLine1("");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank payee town name is rejected")
    void blankTownName_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().getPostalAddress().setTownName(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("non two-letter payee country is rejected")
    void invalidCountry_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().getPostalAddress().setCountry("CHN");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_COUNTRY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("non-ISO payee country is rejected")
    void nonIsoCountry_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().getPostalAddress().setCountry("ZZ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_COUNTRY_INVALID_ISO_CODE, exception.getErrCode());
    }

    @Test
    @DisplayName("non-English payee address characters are rejected")
    void invalidAddressCharacters_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.getCreditor().getPostalAddress().setAddressLine1("深圳路88号");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("TT non-CNY must not carry an intermediary agent BICFI")
    void intermediaryBicfiMustBeBlank_throws() {
        LocalPayment request = buildTtPayment(Currency.HKD);
        request.setIntermediaryAgent(intermediaryAgent("SNNRHKH2XXX"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("TT CNY to a non-CN creditor agent requires an intermediary agent BICFI")
    void ttCnyRequiresIntermediaryBicfi_throws() {
        LocalPayment request = buildTtPayment(Currency.CNY);
        // CNY TT must carry an approved category-purpose code; use a valid one
        // so the payment reaches the intermediary-agent rule.
        request.getCategoryPurpose().setProprietary("COCADR");
        request.setIntermediaryAgent(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> ttPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CATEGORY_PURPOSE_CODE_PROPRIETARY_CONFLICT, exception.getErrCode());
    }

    @Test
    @DisplayName("payment reaches IPE without querying the local balance API")
    void balanceApiUnavailable_stillSubmitsToIpe() {
        when(accountApi.enquireGenericBalance(any()))
                .thenThrow(new IllegalStateException("Balance API must not be called during payment"));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        when(payemtRoutApi.submit(routingRequest)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verifyNoInteractions(accountApi);
        verify(payemtRoutApi).submit(routingRequest);
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("IPE insufficient-funds rejection is returned and persisted")
    void ipeInsufficientFunds_returnsRejectedAndPersists() {
        TransferModelApiResponse response = buildSubmitResponse(StatusEnum.FAILED);
        // Synthetic IPE error fixture; no new HTH error code is introduced.
        response.getData().setErrorCode("TEST-IPE-INSUFFICIENT-FUNDS");
        response.getData().setErrorMessage("Insufficient available balance");
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class))).thenReturn(response);
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), routingRequest);

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("TEST-IPE-INSUFFICIENT-FUNDS", result.getRejectCode());
        assertEquals("Insufficient available balance", result.getRejectMessage());
        verifyNoInteractions(accountApi);
        var updateCaptor = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        var order = inOrder(paymentPayoutRecordMapper, payemtRoutApi);
        order.verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        order.verify(payemtRoutApi).submit(routingRequest);
        order.verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        assertEquals("Rejected", updateCaptor.getValue().getStatus());
        assertEquals("FAILED", updateCaptor.getValue().getIpeStatus());
        assertEquals("Insufficient available balance", updateCaptor.getValue().getRejectMessage());
        verify(bgAccountApi).queryAccountInfo(any(com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request.class));
    }

    // ===================== Success flow =====================

    @Test
    @DisplayName("success: pending payout record is inserted before IPE submission and updated with the outcome")
    void success_mapsIpeRequestAndPersistence() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        when(internalPayeeMapper.selectInternationalPayeeId(anyString(), anyString()))
                .thenReturn("DOMESTIC-PAYEE-1");

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals("E2E-TT", result.getTxnRef());
        assertEquals("E2E-TT", result.getOriginalEndtoendID());
        assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());

        ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(ipeCaptor.capture());
        BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
        assertEquals(PaymentConstants.BG3_CHANNEL_REFERENCE_OUTWARD,
                ipeRequest.getPaymentInformation().getChannelReference());
        assertEquals(PaymentMethodEnum.TT,
                ipeRequest.getPaymentInformation().getPaymentMethod());

        RoutingTransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
        assertEquals("PAYER NAME", txn.getDebtor().getName());
        assertEquals("CSA", txn.getDebtorAccount().getAccountType());
        assertEquals("6802", txn.getDebtorAccount().getProductCode());
        assertEquals("HKD", txn.getDebtorAccount().getCurrency());
        assertEquals(PaymentConstants.BG3_CUSTOMER_REMARKS_OUTWARD,
                txn.getRemittanceInformation().getCustomerRemarks());
        assertEquals("TT BENEFICIARY", txn.getCreditor().getName());
        assertEquals(BENEFICIARY_ACCOUNT, txn.getCreditorAccount().getIdentification());
        assertEquals("HKD", txn.getCreditorAccount().getCurrency());
        assertEquals("BKCHHKHHXXX", txn.getCreditorAgent().getFinancialInstitution().getBicfi());
        assertEquals("S", txn.getCharges().getChargeBearer());
        assertEquals("CB97", txn.getSupplementaryData().getCreditorAccount().getProductCode());
        assertEquals("N", txn.getSupplementaryData().getCreditorAccount().getIndicator());

        Tt tt = txn.getSupplementaryData().getTt();
        assertNotNull(tt);
        assertEquals("PAYER NAME", tt.getAccountHolderName());
        assertEquals("1", tt.getPriority());

        verify(ttPaymentServiceSupport).createProcessInstanceExecute(
                any(com.hkbea.hth.wmg.model.RequestModel.class), any(LocalPayment.class), eq(BATCH_ID));

        InOrder persistenceOrder = inOrder(paymentPayoutRecordMapper, payemtRoutApi);
        ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        persistenceOrder.verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
        persistenceOrder.verify(payemtRoutApi).submit(any(BatchRoutingCreditTransferRequest.class));
        PaymentPayoutRecord pending = insertCaptor.getValue();
        assertEquals("TT", pending.getPaymentType());
        assertEquals(MESSAGE_ID, pending.getMessageId());
        assertEquals(PaymentConstants.MOCK_PARTY_NAME, pending.getPartyName());
        assertEquals("DOMESTIC-PAYEE-1", pending.getPayeeId());
        assertEquals(PaymentConstants.PAYEE_IDFN_ACCTNO, pending.getPayeeIdfn());
        assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
        assertEquals("HKD", pending.getDebitAccountCur());
        assertEquals("6802", pending.getProdCode());
        assertEquals("GSCB", pending.getCtgyPurp());
        assertNull(pending.getPurp());
        assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
        assertEquals("HKD", pending.getCreditAccountCur());
        assertEquals("SHA", pending.getSvcType());
        assertEquals("tt-detail", pending.getRemarks());
        assertNull(pending.getStatus());
        assertEquals("A", pending.getStatusFlag());

        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        persistenceOrder.verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        PaymentPayoutRecord updated = updateCaptor.getValue();
        assertEquals(pending.getId(), updated.getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
        assertEquals(StatusEnum.SUCCESS.value(), updated.getIpeStatus());
        assertEquals("IPE-REF-TT-001", updated.getIpeRefNo());
    }

    @Test
    @DisplayName("WMG failure is swallowed and does not block the submission")
    void wmgException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        doThrow(new RuntimeException("wmg down"))
                .when(ttPaymentServiceSupport)
                .createProcessInstanceExecute(any(com.hkbea.hth.wmg.model.RequestModel.class),
                        any(LocalPayment.class), anyString());

        Payment result = assertDoesNotThrow(
                () -> ttPaymentService.processPayment(buildTtPayment(Currency.HKD), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
    }

    @Test
    @DisplayName("IPE submission failure returns PROCESSING and still persists the payout record")
    void ipeException_returnsProcessingAndPersists() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenThrow(new RuntimeException("IPE timeout"));

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("pending payout insert failure is swallowed")
    void pendingInsertException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("payout update failure is swallowed")
    void payoutUpdateException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.updateById(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = ttPaymentService.processPayment(buildTtPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    // ===================== Fixtures =====================

    private LocalPayment buildTtPayment(Currency currency) {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(currency.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setAccountNumber(BENEFICIARY_ACCOUNT);
        toAccount.setCurrency(currency);
        toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(currency);

        Creditor creditor = new Creditor();
        creditor.setCreditorName("TT BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setAddressLine1("Flat 12A 88 Queens Road");
        address.setTownName("Hong Kong");
        address.setCountry("HK");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi("BKCHHKHHXXX");
        financialInstitutionId.setClearingSystemMemberId("");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setCode("GSCB");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-TT");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID(CUSTOMER_TXN_ID);
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.TT);
        payment.setPartyId(PARTY_ID);
        payment.setCloseId(CLOSE_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setCategoryPurpose(categoryPurpose);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(new java.util.ArrayList<>(List.of("tt-detail")));
        return payment;
    }

    private IntermediaryAgent intermediaryAgent(String bicfi) {
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi(bicfi);
        IntermediaryAgent intermediaryAgent = new IntermediaryAgent();
        intermediaryAgent.setFinancialInstitutionId(financialInstitutionId);
        return intermediaryAgent;
    }

    private BatchRoutingCreditTransferRequest buildRoutingRequest() {
        BatchRoutingCreditTransferRequest request = new BatchRoutingCreditTransferRequest();
        request.setPaymentInformation(new PaymentInformation());
        RoutingTransactionDetailRequest txn = new RoutingTransactionDetailRequest();
        com.hkbea.hth.ipe.model.PaymentIdentification identification =
                new com.hkbea.hth.ipe.model.PaymentIdentification();
        identification.setEndToEndId("E2E-TT");
        txn.setPaymentIdentification(identification);
        request.setTransactions(List.of(txn));
        GroupHeader groupHeader = new GroupHeader();
        groupHeader.setMessageId(BATCH_ID);
        request.setGroupHeader(groupHeader);
        return request;
    }

    private TransferModelApiResponse buildSubmitResponse(
            StatusEnum status) {
        TransferPaymentResultQueryResponse result = new TransferPaymentResultQueryResponse();
        result.setEndToEndId("IPE-TXN-TT");
        result.setStatus(status);
        result.setIpeReferenceId("IPE-REF-TT-001");

        TransferModelApiResponse response = new TransferModelApiResponse();
        response.setCode(PaymentConstants.API_SUCCESS_CODE);
        response.setData(result);
        return response;
    }

    private PartyAccount activePartyAccount() {
        PartyAccount account = new PartyAccount();
        account.setPartyId(PARTY_ID);
        account.setAccountNumber(DEBIT_ACCOUNT);
        account.setProductCode("6802");
        account.setStatus("ACTIVE");
        account.setCompanyCode("00123");
        return account;
    }

    private HthUserAccessAccount activeAccessAccount() {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setObjectStatus("A");
        return account;
    }
}
