package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.DirectRemittance;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.PaymentMethodEnum;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
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
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.service.payment.DrPaymentService;
import com.hkbea.hth.service.payment.PaymentModelConvert;
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.service.payment.DebtorAddressService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CacheHelperUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct Remittance (DR) payment: validation error codes, the enrich -> submit
 * -> persist flow (HTH_PAYMENT_INTERNAL) and its degradation paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String CLOSE_ID = "CLOSE001";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    private static final String MESSAGE_ID = "UT-DR-MESSAGE-000001";
    private static final String CUSTOMER_TXN_ID = "UT-DR-CUST-TXN-000001";
    private static final String BATCH_ID = "BATCH-DR-1";

    @InjectMocks
    private DrPaymentService drPaymentService;

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
    private DebtorAddressService debtorAddressService;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
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
        lenient().doNothing().when(cacheHelperUtils).validatePaymentResult(any());
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===================== Validation =====================

    @Test
    @DisplayName("missing toAccount is rejected with CREDITOR_ACCOUNT_MISSING")
    void nullToAccount_throws() {
        LocalPayment request = buildDrPayment();
        request.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("blank creditor name is rejected by the shared DR rules")
    void blankCreditorName_throws() {
        LocalPayment request = buildDrPayment();
        request.getCreditor().setCreditorName(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor name longer than 140 characters is rejected")
    void creditorNameTooLong_throws() {
        LocalPayment request = buildDrPayment();
        request.getCreditor().setCreditorName("A".repeat(141));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DR_CREDITORNAME_LENGTH_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor name with invalid characters is rejected")
    void creditorNameInvalidChars_throws() {
        LocalPayment request = buildDrPayment();
        request.getCreditor().setCreditorName("张三");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITORNAME_ENGLISH_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("blank beneficiary account number is rejected")
    void blankBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildDrPayment();
        request.getToAccount().setAccountNumber(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("chargesOption present for DR is rejected with NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING")
    void chargesOptionPresent_throws() {
        LocalPayment request = buildDrPayment();
        request.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("remittanceInformationUnstructured present for DR is rejected")
    void remittanceInformationUnstructuredPresent_throws() {
        LocalPayment request = buildDrPayment();
        request.setRemittanceInformationUnstructured("unstructured info");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency outside HKD/USD/CNY is rejected with DR_DEBIT_CURRENCY_NOT_SUPPORTED")
    void unsupportedDebitCurrency_throws() {
        LocalPayment request = buildDrPayment();
        request.getFromAccount().setCurrency(Currency.JPY.toString());
        request.getTxnAmount().setCurrency(Currency.JPY);
        request.getToAccount().setCurrency(Currency.JPY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DR_DEBIT_CURRENCY_NOT_SUPPORTED, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency outside HKD/USD/CNY is rejected with DR_PAYMENT_CURRENCY_NOT_SUPPORTED")
    void unsupportedPaymentCurrency_throws() {
        LocalPayment request = buildDrPayment();
        request.getTxnAmount().setCurrency(Currency.JPY);
        request.getToAccount().setCurrency(Currency.JPY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DR_PAYMENT_CURRENCY_NOT_SUPPORTED, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency differing from payment currency is rejected")
    void debitCurrencyMismatch_throws() {
        LocalPayment request = buildDrPayment();
        request.getFromAccount().setCurrency(Currency.HKD.toString());

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency differing from beneficiary currency is rejected")
    void paymentCurrencyMismatch_throws() {
        LocalPayment request = buildDrPayment();
        request.getToAccount().setCurrency(Currency.HKD);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> drPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    void submitsWithAddressFromDebitAccountOrOmitsEmptyAddress() {
        for (boolean hasAddress : new boolean[]{true, false}) {
            org.mockito.Mockito.reset(debtorAddressService, payemtRoutApi);
            String debitAccount = AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C", "CNY");
            var address = hasAddress ? new com.hkbea.hth.ipe.model.PostalAddress()
                    .addressLine1("PE3 DEBTOR ADDRESS").townName("CENTRAL").country("HK") : null;
            when(debtorAddressService.lookup(debitAccount)).thenReturn(address);
            var request = buildRoutingRequest();
            when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

            drPaymentService.processPayment(buildDrPayment(), request);

            verify(debtorAddressService).lookup(debitAccount);
            verify(payemtRoutApi).submit(request);
            assertEquals(address, request.getTransactions().getFirst().getDebtor().getPostalAddress());
        }
    }

    @Test
    void pe3TransportFailureOmitsAddressAndSubmits() {
        Pe3Api pe3Api = mock(Pe3Api.class);
        when(pe3Api.getCustomerContactInfo(any())).thenThrow(new IllegalStateException("PE3 unavailable"));
        setField(drPaymentService, "debtorAddressService", new DebtorAddressService(pe3Api, headers));
        var request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = drPaymentService.processPayment(buildDrPayment(), request);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertNull(request.getTransactions().getFirst().getDebtor().getPostalAddress());
        verify(pe3Api).getCustomerContactInfo(any());
        verify(payemtRoutApi).submit(request);
    }

    @Test
    @DisplayName("payment reaches IPE without querying the local balance API")
    void balanceApiUnavailable_stillSubmitsToIpe() {
        when(accountApi.enquireGenericBalance(any()))
                .thenThrow(new IllegalStateException("Balance API must not be called during payment"));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        when(payemtRoutApi.submit(routingRequest)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = drPaymentService.processPayment(buildDrPayment(), routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verifyNoInteractions(accountApi);
        verify(payemtRoutApi).submit(routingRequest);
        verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        verify(paymentInternalMapper).updateById(any(PaymentInternal.class));
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

        Payment result = drPaymentService.processPayment(buildDrPayment(), routingRequest);

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("TEST-IPE-INSUFFICIENT-FUNDS", result.getRejectCode());
        assertEquals("Insufficient available balance", result.getRejectMessage());
        verifyNoInteractions(accountApi);
        var updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        var order = inOrder(paymentInternalMapper, payemtRoutApi);
        order.verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        order.verify(payemtRoutApi).submit(routingRequest);
        order.verify(paymentInternalMapper).updateById(updateCaptor.capture());
        assertEquals("Rejected", updateCaptor.getValue().getStatus());
        assertEquals("FAILED", updateCaptor.getValue().getIpeStatus());
        assertEquals("Insufficient available balance", updateCaptor.getValue().getRejectMessage());
        verify(bgAccountApi).queryAccountInfo(any(com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request.class));
    }

    // ===================== Success flow =====================

    @Test
    @DisplayName("success: pending internal record is inserted before IPE submission and updated with the outcome")
    void success_persistsInternalRecord() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = drPaymentService.processPayment(buildDrPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals("E2E-DR", result.getTxnRef());
        assertEquals("E2E-DR", result.getOriginalEndtoendID());
        assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());

        ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(ipeCaptor.capture());
        BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
        assertEquals(PaymentMethodEnum.DR,
                ipeRequest.getPaymentInformation().getPaymentMethod());

        RoutingTransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
        // Production note: DrPaymentService.enrichRoutingDetail no longer fills the
        // routing-level creditorAccount/creditorAgent blocks (commented out); the
        // payee data is carried exclusively in the DirectRemittance block below.
        assertNull(txn.getCreditorAccount());
        DirectRemittance directRemittance = txn.getSupplementaryData().getDirectRemittance();
        assertEquals("01", directRemittance.getChinaAcType());
        assertEquals(BENEFICIARY_ACCOUNT, directRemittance.getChinaAcno());
        assertEquals("DR BENEFICIARY" + " ".repeat(20), directRemittance.getChinaAccEnglishName());
        assertEquals(" ".repeat(62), directRemittance.getChinaAccChineseName());
        var remittance = txn.getSupplementaryData().getChatsRemittance();
        assertEquals(0, remittance.getPinCnt());
        assertNull(remittance.getDepositAccountName1());
        assertNull(remittance.getDepositAccountName2());
        assertNull(remittance.getDepositAccountName3());
        assertNull(remittance.getDepositAccountName4());

        InOrder inOrder = inOrder(paymentInternalMapper, payemtRoutApi);
        ArgumentCaptor<PaymentInternal> insertCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        inOrder.verify(paymentInternalMapper).insert(insertCaptor.capture());
        inOrder.verify(payemtRoutApi).submit(any(BatchRoutingCreditTransferRequest.class));
        PaymentInternal pending = insertCaptor.getValue();
        assertEquals("DR", pending.getPaymentType());
        assertEquals("E2E-DR", pending.getPaymentId());
        assertEquals(CUSTOMER_TXN_ID, pending.getCustomerTransactionId());
        assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
        assertEquals("CNY", pending.getDebitAccountCur());
        assertEquals(new BigDecimal("100.00"), pending.getInstdAmount());
        assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
        assertEquals("CNY", pending.getCreditAccountCur());
        assertEquals("DR BENEFICIARY", pending.getCreditAccountName());
        assertEquals("6802", pending.getProductCode());
        assertNull(pending.getStatus());
        assertNull(pending.getIpeStatus());
        assertNull(pending.getIpeRefNo());
        assertEquals("CNY", pending.getSvcCur());

        ArgumentCaptor<PaymentInternal> updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        inOrder.verify(paymentInternalMapper).updateById(updateCaptor.capture());
        PaymentInternal updated = updateCaptor.getValue();
        assertEquals(pending.getId(), updated.getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
        assertEquals(StatusEnum.SUCCESS.value(), updated.getIpeStatus());
        assertEquals("IPE-REF-DR-001", updated.getIpeRefNo());
    }

    @ParameterizedTest
    @ValueSource(ints = {34, 35, 140})
    void submitsFixedWidthEnglishNameToIpe(int length) {
        LocalPayment payment = buildDrPayment();
        String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(6).substring(0, length);
        payment.getCreditor().setCreditorName(name);
        var request = PaymentModelConvert.toRoutingRequest(payment);
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = drPaymentService.processPayment(payment, request);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(payemtRoutApi).submit(request);
        var txn = request.getTransactions().getFirst();
        assertEquals(name, txn.getCreditor().getName());
        assertEquals(name, txn.getCreditorAccount().getAccountName());
        assertEquals(name.substring(0, 34), txn.getSupplementaryData().getDirectRemittance().getChinaAccEnglishName());
    }

    @Test
    @DisplayName("IPE submission failure returns PROCESSING and still persists the internal record")
    void ipeException_returnsProcessingAndPersists() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenThrow(new RuntimeException("IPE timeout"));

        Payment result = drPaymentService.processPayment(buildDrPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        ArgumentCaptor<PaymentInternal> updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        verify(paymentInternalMapper).updateById(updateCaptor.capture());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), updateCaptor.getValue().getStatus());
        assertNull(updateCaptor.getValue().getIpeStatus());
    }

    @Test
    @DisplayName("internal record insert failure is swallowed")
    void insertException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        doThrow(new RuntimeException("db down"))
                .when(paymentInternalMapper).insert(any(PaymentInternal.class));

        Payment result = assertDoesNotThrow(
                () -> drPaymentService.processPayment(buildDrPayment(), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentInternalMapper).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("internal record update failure is swallowed")
    void updateException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        doThrow(new RuntimeException("db down"))
                .when(paymentInternalMapper).updateById(any(PaymentInternal.class));

        Payment result = assertDoesNotThrow(
                () -> drPaymentService.processPayment(buildDrPayment(), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildDrPayment() {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(Currency.CNY.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setAccountNumber(BENEFICIARY_ACCOUNT);
        toAccount.setCurrency(Currency.CNY);
        toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(Currency.CNY);

        Creditor creditor = new Creditor();
        creditor.setCreditorName("DR BENEFICIARY");

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi("BKCHHKHHXXX");
        financialInstitutionId.setClearingSystemMemberId("");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-DR");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID(CUSTOMER_TXN_ID);
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.DR);
        payment.setPartyId(PARTY_ID);
        payment.setCloseId(CLOSE_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setPaymentDetails(new java.util.ArrayList<>(List.of("dr-detail")));
        return payment;
    }

    private BatchRoutingCreditTransferRequest buildRoutingRequest() {
        BatchRoutingCreditTransferRequest request = new BatchRoutingCreditTransferRequest();
        request.setPaymentInformation(new PaymentInformation());
        RoutingTransactionDetailRequest txn = new RoutingTransactionDetailRequest();
        com.hkbea.hth.ipe.model.PaymentIdentification identification =
                new com.hkbea.hth.ipe.model.PaymentIdentification();
        identification.setEndToEndId("E2E-DR");
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
        result.setEndToEndId("IPE-TXN-DR");
        result.setStatus(status);
        result.setIpeReferenceId("IPE-REF-DR-001");

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
