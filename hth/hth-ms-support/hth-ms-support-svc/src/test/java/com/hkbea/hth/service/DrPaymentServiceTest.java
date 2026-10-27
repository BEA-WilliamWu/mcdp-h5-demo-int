package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.model.BatchCreditTransferRequest;
import com.hkbea.hth.ipe.model.DirectRemittanceRequest;
import com.hkbea.hth.ipe.model.ModelApiResponse;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.ipe.model.TransactionDetailRequest;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.BalanceDetail;
import com.hkbea.hth.model.BatchAccountResult;
import com.hkbea.hth.model.BatchEnquiryResponse;
import com.hkbea.hth.model.CategoryPurpose;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.FinancialInstitutionId;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import com.hkbea.hth.service.PaymentCommonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    private static final String MESSAGE_ID = "UT-DR-MESSAGE-000001";
    private static final String CUSTOMER_TXN_ID = "UT-DR-CUST-TXN-000001";

    @InjectMocks
    private DrPaymentService drPaymentService;

    @Mock
    private LocalPaymentApi localPaymentApi;
    @Mock
    private ScameterValidatorUtil scameterValidatorUtil;
    @Mock
    private AccountApi accountApi;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private PaymentInternalMapper paymentInternalMapper;
    @Mock
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private CommonHeader headers;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;

    @Test
    void hostClosedOrDormantRejectsBeforePaymentSideEffects() {
        for (String[] state : new String[][] {{"C", "N"}, {"c", "N"}, {"A", "Y"}, {"A", "y"}}) {
            when(bgAccountApi.queryAccountInfo(any())).thenReturn(
                    OverseasPaymentServiceTest.hostAccount(state[0], state[1], "N"));
            ServiceException error = assertThrows(ServiceException.class,
                    () -> drPaymentService.processPayment(buildDrPayment()));
            assertEquals(600061, error.getErrCode());
        }
        verifyNoInteractions(accountApi, localPaymentApi, paymentInternalMapper, paymentPayoutRecordMapper);
    }

    @Test
    void hostEnquiryFailureStopsBeforePaymentSideEffects() {
        when(bgAccountApi.queryAccountInfo(any())).thenReturn(null);
        ServiceException error = assertThrows(ServiceException.class,
                () -> drPaymentService.processPayment(buildDrPayment()));
        assertEquals(600000, error.getErrCode());
        verifyNoInteractions(accountApi, localPaymentApi, paymentInternalMapper, paymentPayoutRecordMapper);
    }

    @Test
    void invalidDebitStatusRejectsBeforePaymentSideEffects() {
        for (String status : Arrays.asList(null, "", " ", "I", "FROZEN", "CLOSED", " A ")) {
            HthUserAccessAccount account = activeAccessAccount();
            account.setObjectStatus(status);
            when(hthUserAccessAccountMapper.selectFromAccountInfo(
                    nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(List.of(account));

            ServiceException exception = assertThrows(ServiceException.class,
                    () -> drPaymentService.processPayment(buildDrPayment()),
                    "Expected rejection for status: " + status);

            assertEquals(600061, exception.getErrCode());
            assertEquals("Invalid account status.", exception.getMessage());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class));
            verifyNoInteractions(paymentInternalMapper, paymentPayoutRecordMapper);
        }
    }

    @Test
    void missingDebitAccountRejectsBeforePaymentSideEffects() {
        List<List<HthUserAccessAccount>> results = Arrays.asList(
                null, Collections.emptyList(), Collections.singletonList(null));
        for (List<HthUserAccessAccount> accounts : results) {
            when(hthUserAccessAccountMapper.selectFromAccountInfo(
                    nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(accounts);

            ServiceException exception = assertThrows(ServiceException.class,
                    () -> drPaymentService.processPayment(buildDrPayment()));

            assertEquals(600020, exception.getErrCode());
            assertEquals("The specified account does not exist or is not accessible.", exception.getMessage());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class));
            verifyNoInteractions(paymentInternalMapper, paymentPayoutRecordMapper);
        }
    }

    @Mock
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        org.mockito.Mockito.lenient().when(bgAccountApi.queryAccountInfo(any()))
                .thenReturn(OverseasPaymentServiceTest.hostAccount("A", "N", "N"));
        lenient().when(headers.getMessageId()).thenReturn(MESSAGE_ID);
        lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                        nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(activeAccessAccount()));
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString())).thenReturn("PAYER NAME");
        lenient().when(accountApi.enquireGenericBalance(any()))
                .thenReturn(buildBalanceResponse("999999999", Currency.CNY));
    }

    // ===================== Test Data Builders =====================

    /** Access account record with an ACTIVE object status (required by validateFromAccount). */
    private HthUserAccessAccount activeAccessAccount() {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setObjectStatus("A");
        return account;
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

    private LocalPayment buildDrPayment() {
        return buildDrPayment(Currency.CNY);
    }

    private LocalPayment buildDrPayment(Currency currency) {
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
        creditor.setAccountName("DR BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setPayeeAddressLine1("Shenzhen Road 88");
        address.setPayeeTownName("Shenzhen");
        address.setPayeeCreditorCountry("CN");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBICFI("BKCHCNBJ110");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setPurposeCode("GSCB");
        categoryPurpose.setPurposeProprietary("GOODS");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
//        paymentIdentification.setCustomerTransactionId(CUSTOMER_TXN_ID);
        paymentIdentification.setEndToEndId("E2E-DR");

        LocalPayment payment = new LocalPayment();
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.DR);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setCategoryPurpose(categoryPurpose);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setTransactionID(CUSTOMER_TXN_ID);
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(new java.util.ArrayList<>(Collections.singletonList("dr-detail")));
        return payment;
    }

    private ModelApiResponse buildPaymentResponse(PaymentResultQueryResponse.StatusEnum status, String errorMessage) {
        PaymentResultQueryResponse result = new PaymentResultQueryResponse();
        result.setTransactionId("IPE-TXN-DR");
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        result.setErrorCode("DR-ERR-001");
        result.setIpeReferenceId("IPE-REF-DR-001");

        ModelApiResponse response = new ModelApiResponse();
        response.setCode("00");
        response.setData(result);
        return response;
    }

    private BatchEnquiryResponse buildBalanceResponse(String availableBalance, Currency currency) {
        BalanceDetail balance = new BalanceDetail();
        balance.setCurrency(currency);
        balance.setAvailableBalance(new BigDecimal(availableBalance));

        BatchAccountResult result = new BatchAccountResult();
        result.setQueryStatus(BatchAccountResult.QueryStatusEnum.SUCCESS);
        result.setBalances(Collections.singletonList(balance));

        BatchEnquiryResponse response = new BatchEnquiryResponse();
        response.setResults(Collections.singletonList(result));
        return response;
    }

    @Nested
    class SuccessTests {

        @Test
        void success_mapsIpeRequestAndPersistence() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            LocalPayment request = buildDrPayment();
            Payment result = drPaymentService.processPayment(request);

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            assertNotNull(result.getTxnRef());

            ArgumentCaptor<BatchCreditTransferRequest> ipeCaptor =
                    ArgumentCaptor.forClass(BatchCreditTransferRequest.class);
            verify(localPaymentApi).submitOutwardCreditTransfer(ipeCaptor.capture());
            BatchCreditTransferRequest ipeRequest = ipeCaptor.getValue();
            assertEquals(BatchCreditTransferRequest.TransactionTypeEnum.DR, ipeRequest.getTransactionType());
            assertEquals(0, BigDecimal.ZERO.compareTo(ipeRequest.getServiceChargeAmount()));

            TransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
            assertEquals(" ", txn.getPayeeBankCountyCode());
            assertEquals("", txn.getPaymentSource());
            assertEquals("", txn.getEnquiryType());
            assertEquals("", txn.getCostCenterCode());
            assertEquals("", txn.getFeeCode());
            assertEquals("", txn.getAlnovaLimitCheckInd());
            // DR does not set chargeBearer on the IPE request (enrichment line is commented out)
            assertNull(txn.getChargeBearer());
            assertEquals("BKCHCNBJ110", txn.getPayeeBicCode());

            DirectRemittanceRequest dr = txn.getDirectRemittanceRequest();
            assertEquals("I", dr.getHdrChSrcInd());
            assertEquals("REMC1", dr.getHdrMessageId());
            assertEquals("32", dr.getHdrOpt());
            assertEquals("015", dr.getHdrBankCode());
            assertEquals("01", dr.getHdrIntBankCode());
            assertEquals("CDC", dr.getHdrMediaCode());
            assertEquals("01", dr.getHdrSubOpt());
            assertEquals("03", dr.getHdrTxnType());
            assertEquals("C001505216810034096REM02TFR00466185", dr.getHdrTxnNumber());
            assertEquals("CNY", dr.getChinaAcCcy());
            assertEquals("01", dr.getChinaAcType());
            assertEquals(BENEFICIARY_ACCOUNT, dr.getChinaAcno());
            assertEquals("DR BENEFICIARY", dr.getChinaAccEnglishName());
            assertEquals(0, BigDecimal.ZERO.compareTo(dr.getCrAmt()));

            ArgumentCaptor<PaymentInternal> insertCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
            verify(paymentInternalMapper).insert(insertCaptor.capture());
            PaymentInternal inserted = insertCaptor.getValue();
            assertEquals("DR", inserted.getPaymentType());
            assertEquals(PARTY_ID, inserted.getPartyId());
            assertEquals("MOCK PARTY NAME", inserted.getPartyName());
            assertEquals(result.getTxnRef(), inserted.getPaymentId());
            assertEquals(CUSTOMER_TXN_ID, inserted.getCustomerTransactionId());
            assertEquals(DEBIT_ACCOUNT, inserted.getDebitAccountId());
            assertEquals("CNY", inserted.getDebitAccountCur());
            assertEquals(BENEFICIARY_ACCOUNT, inserted.getCreditAccountId());
            assertEquals("DR BENEFICIARY", inserted.getCreditAccountName());
            assertEquals("System", inserted.getCreatedBy());
            assertEquals(MESSAGE_ID, inserted.getMessageId());
            assertEquals(Payment.StatusEnum.SUCCESS.value(), inserted.getStatus());
            assertEquals(PaymentResultQueryResponse.StatusEnum.SUCCESS.value(), inserted.getIpeStatus());
            assertEquals("IPE-REF-DR-001", inserted.getIpeRefNo());
        }

        @Test
        void ipeException_returnsProcessingAndPersists() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenThrow(new RuntimeException("IPE timeout"));

            Payment result = drPaymentService.processPayment(buildDrPayment());

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
            verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        }

        @Test
        void insertException_swallowed() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(paymentInternalMapper.insert(any(PaymentInternal.class)))
                    .thenThrow(new RuntimeException("db down"));

            Payment result = drPaymentService.processPayment(buildDrPayment());

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        }

        @Test
        void ipeFailed_mapsRejectedAndPersists() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.FAILED, "cny rejected"));

            Payment result = drPaymentService.processPayment(buildDrPayment());

            assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
            assertEquals("cny rejected", result.getRejectMessage());

            ArgumentCaptor<PaymentInternal> insertCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
            verify(paymentInternalMapper).insert(insertCaptor.capture());
            PaymentInternal inserted = insertCaptor.getValue();
            assertEquals(Payment.StatusEnum.REJECTED.value(), inserted.getStatus());
            assertEquals("cny rejected", inserted.getRejectMessage());
            assertEquals("DR", inserted.getPaymentType());
        }

        @Test
        void serviceChargeAmount_isZero() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            drPaymentService.processPayment(buildDrPayment());

            ArgumentCaptor<BatchCreditTransferRequest> captor =
                    ArgumentCaptor.forClass(BatchCreditTransferRequest.class);
            verify(localPaymentApi).submitOutwardCreditTransfer(captor.capture());
            assertEquals(0, BigDecimal.ZERO.compareTo(
                    captor.getValue().getServiceChargeAmount() == null
                            ? BigDecimal.ZERO : captor.getValue().getServiceChargeAmount()));
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void nullToAccount_throws() {
            LocalPayment request = buildDrPayment();
            request.setToAccount(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> drPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void blankCreditorName_throws() {
            LocalPayment request = buildDrPayment();
            request.getCreditor().setAccountName(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> drPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void blankBeneficiaryAccountNumber_throws() {
            LocalPayment request = buildDrPayment();
            request.getToAccount().setAccountNumber(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> drPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void nonCnyCurrency_throws() {
            // DR supports HKD/USD/CNY; JPY is outside the supported set.
            // validateDrCurrency rejects with DEBIT_CURRENCY_MISSING_OR_INVALID (500103)
            LocalPayment request = buildDrPayment(Currency.JPY);
            request.getToAccount().setCurrency(Currency.JPY);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> drPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void insufficientBalance_throws() {
            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(buildBalanceResponse("1.00", Currency.CNY));

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> drPaymentService.processPayment(buildDrPayment()));
            assertEquals(HTHSupportErrorMapping.INSUFFICIENT_BALANCE, exception.getErrCode());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any());
        }

        @Test
        void drFlow_skipsScameter() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            drPaymentService.processPayment(buildDrPayment());

            verify(scameterValidatorUtil, never()).checkChats(any());
            verify(scameterValidatorUtil, never()).checkFps(any());
            verify(scameterValidatorUtil, never()).checkBankTransfer(any());
        }
    }
}
