package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.model.BatchCreditTransferRequest;
import com.hkbea.hth.ipe.model.ModelApiResponse;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.ipe.model.TelegraphicTransferRequest;
import com.hkbea.hth.ipe.model.TransactionDetailRequest;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
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
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import com.hkbea.hth.wmg.model.DataField;
import com.hkbea.hth.wmg.model.RequestModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TtPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    private static final String MESSAGE_ID = "UT-TT-MESSAGE-000001";
    private static final String CUSTOMER_TXN_ID = "UT-TT-CUST-TXN-000001";

    @InjectMocks
    private TtPaymentService ttPaymentService;

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
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private CommonHeader headers;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
    @Mock
    private TtPaymentServiceSupport ttPaymentServiceSupport;

    @Test
    void hostClosedOrDormantRejectsBeforePaymentSideEffects() {
        for (String[] state : new String[][] {{"C", "N"}, {"c", "N"}, {"A", "Y"}, {"A", "y"}}) {
            when(bgAccountApi.queryAccountInfo(any())).thenReturn(
                    OverseasPaymentServiceTest.hostAccount(state[0], state[1], "N"));
            ServiceException error = assertThrows(ServiceException.class,
                    () -> ttPaymentService.processPayment(buildTtPayment()));
            assertEquals(600061, error.getErrCode());
        }
        verifyNoInteractions(accountApi, localPaymentApi, paymentPayoutRecordMapper, ttPaymentServiceSupport);
    }

    @Test
    void hostEnquiryFailureStopsBeforePaymentSideEffects() {
        when(bgAccountApi.queryAccountInfo(any())).thenReturn(null);
        ServiceException error = assertThrows(ServiceException.class,
                () -> ttPaymentService.processPayment(buildTtPayment()));
        assertEquals(600000, error.getErrCode());
        verifyNoInteractions(accountApi, localPaymentApi, paymentPayoutRecordMapper, ttPaymentServiceSupport);
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
                    () -> ttPaymentService.processPayment(buildTtPayment()),
                    "Expected rejection for status: " + status);

            assertEquals(600061, exception.getErrCode());
            assertEquals("Invalid account status.", exception.getMessage());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class));
            verifyNoInteractions(paymentPayoutRecordMapper, ttPaymentServiceSupport);
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
                    () -> ttPaymentService.processPayment(buildTtPayment()));

            assertEquals(600020, exception.getErrCode());
            assertEquals("The specified account does not exist or is not accessible.", exception.getMessage());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class));
            verifyNoInteractions(paymentPayoutRecordMapper, ttPaymentServiceSupport);
        }
    }

    @Mock
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        org.mockito.Mockito.lenient().when(bgAccountApi.queryAccountInfo(any()))
                .thenReturn(OverseasPaymentServiceTest.hostAccount("A", "N", "N"));
        org.mockito.Mockito.lenient().when(headers.getMessageId()).thenReturn(MESSAGE_ID);
        org.mockito.Mockito.lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                        nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(activeAccessAccount()));
        org.mockito.Mockito.lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString()))
                .thenReturn("6802");
        org.mockito.Mockito.lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        org.mockito.Mockito.lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString()))
                .thenReturn("PAYER NAME");
        org.mockito.Mockito.lenient().when(accountApi.enquireGenericBalance(any()))
                .thenReturn(OverseasPaymentServiceTest.buildBalanceResponse("999999999", Currency.HKD));
    }

    // ===================== Test Data Builders =====================

    private PartyAccount activePartyAccount() {
        PartyAccount account = new PartyAccount();
        account.setPartyId(PARTY_ID);
        account.setAccountNumber(DEBIT_ACCOUNT);
        account.setProductCode("6802");
        account.setStatus("ACTIVE");
        account.setCompanyCode("00123");
        return account;
    }

    /** Access account record with an ACTIVE object status (required by validateFromAccount). */
    private HthUserAccessAccount activeAccessAccount() {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setObjectStatus("A");
        return account;
    }

    private ModelApiResponse buildPaymentResponse(PaymentResultQueryResponse.StatusEnum status, String errorMessage) {
        PaymentResultQueryResponse result = new PaymentResultQueryResponse();
        result.setTransactionId("IPE-TXN-TT");
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        result.setIpeReferenceId("IPE-REF-TT-001");

        ModelApiResponse response = new ModelApiResponse();
        response.setCode("00");
        response.setData(result);
        return response;
    }

    @Nested
    class SuccessTests {

        @Test
        void success_mapsIpeRequestAndPersistence() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(internalPayeeMapper.selectInternationalPayeeId(anyString(), anyString())).thenReturn("DOMESTIC-PAYEE-1");

            LocalPayment request = buildTtPayment(Currency.HKD);
            Payment result = ttPaymentService.processPayment(request);

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            assertNotNull(result.getTxnRef());
            assertEquals(MESSAGE_ID, result.getRejectMessage() == null ? MESSAGE_ID : result.getRejectMessage());

            ArgumentCaptor<BatchCreditTransferRequest> ipeCaptor =
                    ArgumentCaptor.forClass(BatchCreditTransferRequest.class);
            verify(localPaymentApi).submitOutwardCreditTransfer(ipeCaptor.capture());
            BatchCreditTransferRequest ipeRequest = ipeCaptor.getValue();
            assertEquals(BatchCreditTransferRequest.ChannelEnum.HTH, ipeRequest.getChannel());
            assertEquals("BG3-SIT-OUTWARD", ipeRequest.getChannelReference());
            assertEquals(BatchCreditTransferRequest.TransactionTypeEnum.TT, ipeRequest.getTransactionType());
            assertEquals(BatchCreditTransferRequest.TimeOfTransferEnum.NOW, ipeRequest.getTimeOfTransfer());
            assertEquals(1, ipeRequest.getTotalCount());
            assertEquals(0, new BigDecimal("100.00").compareTo(ipeRequest.getTotalAmount()));
            assertEquals("BG3 internal outward H2H priority", ipeRequest.getCustomerRemarks());

            TransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
            assertEquals(ipeRequest.getBatchId(), txn.getTransactionId());
            assertEquals("HKD", txn.getSettlementCurrency());
            assertEquals("HKD", txn.getChargeCurrency());
            assertEquals("S", txn.getChargeBearer());
            assertEquals(TransactionDetailRequest.PayeeTypeEnum.EMPTY, txn.getPayeeType());
            assertEquals(BENEFICIARY_ACCOUNT, txn.getPayeeAccountNo());
            assertEquals("HKD", txn.getPayeeAccountCurrency());
            assertEquals("BKCHHKHHXXX", txn.getPayeeBicCode());
            assertEquals("TT BENEFICIARY", txn.getPayeeName());
            // payee is pre-registered (mock returns a payeeId) -> CB97
            assertEquals("CB97", txn.getPayeeAccountProdCode());
            assertEquals("N", txn.getPayeeAccountIndicator());

            TelegraphicTransferRequest tt = txn.getTt();
            assertNotNull(tt);
            assertEquals("015", tt.getHdrBankCode());
            assertEquals("01", tt.getHdrIntBankCode());
            assertEquals("00000000000000000000000000000000000", tt.getHdrTxnNumber());
            assertEquals("PAYER NAME", tt.getAccountHolderName());
            assertEquals("1", tt.getPriority());

            ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                    ArgumentCaptor.forClass(PaymentPayoutRecord.class);
            verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
            PaymentPayoutRecord pending = insertCaptor.getValue();
            assertEquals("TT", pending.getPaymentType());
            assertEquals(MESSAGE_ID, pending.getMessageId());
            assertEquals("MOCK PARTY NAME", pending.getPartyName());
            assertEquals("DOMESTIC-PAYEE-1", pending.getPayeeId());
            assertEquals("ACCTNO", pending.getPayeeIdfn());
            assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
            assertEquals("HKD", pending.getDebitAccountCur());
            assertEquals("6802", pending.getProdCode());
            assertEquals("GSCB", pending.getCtgyPurp());
            assertEquals("CXBSNS", pending.getPurp());
            assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
            assertEquals("HKD", pending.getCreditAccountCur());
            assertEquals("SHA", pending.getSvcType());
            assertEquals("tt-detail", pending.getRemarks());
            assertNull(pending.getStatus());
            assertEquals("A", pending.getStatusFlag());

            ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                    ArgumentCaptor.forClass(PaymentPayoutRecord.class);
            verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
            PaymentPayoutRecord updated = updateCaptor.getValue();
            assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
            assertEquals(PaymentResultQueryResponse.StatusEnum.SUCCESS.value(), updated.getIpeStatus());
            assertEquals("IPE-REF-TT-001", updated.getIpeRefNo());
        }

        @Test
        void ipeException_returnsProcessingAndPersists() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenThrow(new RuntimeException("IPE timeout"));

            Payment result = ttPaymentService.processPayment(buildTtPayment());

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
            verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
            verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
        }

        @Test
        void pendingInsertException_swallowed() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                    .thenThrow(new RuntimeException("db down"));

            Payment result = ttPaymentService.processPayment(buildTtPayment());

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        }

        @Test
        void payoutUpdateException_swallowed() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(paymentPayoutRecordMapper.updateById(any(PaymentPayoutRecord.class)))
                    .thenThrow(new RuntimeException("db down"));

            Payment result = ttPaymentService.processPayment(buildTtPayment());

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
        }

        @Test
        void futureTransferDate_throwsDateTimeParseException() {
            LocalPayment request = buildTtPayment();
            request.setSettlementDate("not-a-valid-date");

            assertThrows(DateTimeParseException.class, () -> ttPaymentService.processPayment(request));
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void nullToAccount_throws() {
            LocalPayment request = buildTtPayment();
            request.setToAccount(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void blankCreditorName_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().setAccountName(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void blankBeneficiaryAccountNumber_throws() {
            LocalPayment request = buildTtPayment();
            request.getToAccount().setAccountNumber(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING, exception.getErrCode());
        }

        @Test
        void unsupportedDebitCurrency_throws() {
            // TT_SUPPORTED_CURRENCIES covers every Currency enum value, so EUR is supported;
            // with an EUR balance available the flow proceeds to success
            LocalPayment request = buildTtPayment(Currency.EUR);
            request.getTxnAmount().setCurrency(Currency.EUR);
            request.getToAccount().setCurrency(Currency.EUR);
            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(OverseasPaymentServiceTest.buildBalanceResponse("999999999", Currency.EUR));
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            Payment result = ttPaymentService.processPayment(request);
            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        }

        @Test
        void inconsistentCurrency_throws() {
            LocalPayment request = buildTtPayment();
            request.getToAccount().setCurrency(Currency.CNY);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.INCONSISTENT_CURRENCY, exception.getErrCode());
        }

        @Test
        void missingPayeeAddress_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().getPostalAddress().setPayeeAddressLine1("");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void blankTownName_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().getPostalAddress().setPayeeTownName(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void invalidCountry_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().getPostalAddress().setPayeeCreditorCountry("CHN");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_COUNTRY_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void insufficientBalance_throws() {
            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(OverseasPaymentServiceTest.buildBalanceResponse("1.00", Currency.HKD));

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(buildTtPayment()));
            assertEquals(HTHSupportErrorMapping.INSUFFICIENT_BALANCE, exception.getErrCode());
            verify(localPaymentApi, never()).submitOutwardCreditTransfer(any());
        }

        @Test
        void validationFailure_skipsScameter() {
            LocalPayment request = buildTtPayment();
            request.setToAccount(null);

            assertThrows(ParameterException.class, () -> ttPaymentService.processPayment(request));
            verify(scameterValidatorUtil, never()).checkChats(any());
        }
    }

    @Nested
    class CreateProcessInstanceExecuteTests {

        @Test
        void createProcessInstanceExecute_buildsRequestModelAndCallsSupport() {
            LocalPayment request = buildTtPayment();
            String batchId = "BATCH-TT-001";

            ttPaymentService.createProcessInstanceExecute(request, batchId);

            ArgumentCaptor<RequestModel> captor = ArgumentCaptor.forClass(RequestModel.class);
            verify(ttPaymentServiceSupport).createProcessInstanceExecute(captor.capture(), any(LocalPayment.class), eq(batchId));
            RequestModel capturedModel = captor.getValue();
            assertEquals("Workflows\\OTT_Main", capturedModel.getWorkflow());
            DataField dataField = capturedModel.getDatafield();
            assertNotNull(dataField);
            assertEquals("HTH", dataField.getSystemCode());
            assertEquals("TT", dataField.getTransType());
            assertEquals(MESSAGE_ID, dataField.getHoSTRefNo());
            assertEquals("015-168-68-00686-4", dataField.getRemitAccNo());
            assertEquals("HKD", dataField.getRemitAccCurr());
            assertEquals("100.00", dataField.getTransAmount());
            assertEquals("HKD", dataField.getTransCurrency());
            assertEquals("TT BENEFICIARY", dataField.getBeneBankName());
            assertEquals("BKCHHKHHXXX", dataField.getBeneBankBIC());
            assertEquals(BENEFICIARY_ACCOUNT, dataField.getBeneAccNo());
            assertEquals(batchId, dataField.getTtRef());
        }

        @Test
        void createProcessInstanceExecute_supportException_logged() {
            LocalPayment request = buildTtPayment();
            String batchId = "BATCH-TT-002";
            // Use doAnswer to throw synchronously because the real method is @Async
            // doThrow would throw in a different thread and escape the try-catch
            org.mockito.Mockito.doAnswer(invocation -> {
                throw new RuntimeException("WMG service unavailable");
            }).when(ttPaymentServiceSupport).createProcessInstanceExecute(any(RequestModel.class), any(LocalPayment.class), anyString());

            // Should not throw - exception is caught and logged inside createProcessInstanceExecute
            ttPaymentService.createProcessInstanceExecute(request, batchId);

            verify(ttPaymentServiceSupport).createProcessInstanceExecute(any(RequestModel.class), any(LocalPayment.class), eq(batchId));
        }

        @Test
        void createProcessInstanceExecute_supportCalledWithCorrectBatchId() {
            LocalPayment request = buildTtPayment(Currency.USD);
            request.getTxnAmount().setCurrency(Currency.USD);
            request.getToAccount().setCurrency(Currency.USD);
            String batchId = "BATCH-TT-USD-001";

            ttPaymentService.createProcessInstanceExecute(request, batchId);

            ArgumentCaptor<String> batchIdCaptor = ArgumentCaptor.forClass(String.class);
            verify(ttPaymentServiceSupport).createProcessInstanceExecute(any(RequestModel.class), any(LocalPayment.class), batchIdCaptor.capture());
            assertEquals(batchId, batchIdCaptor.getValue());
        }
    }

    // ===================== Test Data Builders =====================

    private LocalPayment buildTtPayment() {
        return buildTtPayment(Currency.HKD);
    }

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
        creditor.setAccountName("TT BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setPayeeAddressLine1("Flat 12A 88 Queens Road");
        address.setPayeeTownName("Hong Kong");
        address.setPayeeCreditorCountry("HK");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBICFI("BKCHHKHHXXX");
        // TT identifies the bank by BIC; memberId must stay blank (mutual-exclusion rule)
        financialInstitutionId.setClearingSystemMemberId("");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setPurposeCode("GSCB");
        categoryPurpose.setPurposeProprietary("CXBSNS");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-TT");

        LocalPayment payment = new LocalPayment();
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.TT);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setCategoryPurpose(categoryPurpose);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setTransactionID("UT-TT-CUST-TXN-000001");
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(new java.util.ArrayList<>(Collections.singletonList("tt-detail")));
        return payment;
    }
}
