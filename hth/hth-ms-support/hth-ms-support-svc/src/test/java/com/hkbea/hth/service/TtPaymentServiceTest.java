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
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.Tt;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
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
import com.hkbea.hth.model.IntermediaryAgent;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.service.PaymentCommonService;
import com.hkbea.hth.utils.ScameterValidatorUtil;
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
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void invalidDebitStatusRejectsBeforePaymentSideEffects() {
        for (String status : java.util.Arrays.asList(null, "", " ", "I", "FROZEN", "CLOSED", " A ")) {
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
            verify(localPaymentApi, never()).submit(any(BatchRoutingCreditTransferRequest.class));
            verifyNoInteractions(paymentPayoutRecordMapper, ttPaymentServiceSupport);
        }
    }

    @Test
    void missingDebitAccountRejectsBeforePaymentSideEffects() {
        when(hthUserAccessAccountMapper.selectFromAccountInfo(
                nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(Collections.emptyList());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> ttPaymentService.processPayment(buildTtPayment()));

        assertEquals(600020, exception.getErrCode());
        assertEquals("The specified account does not exist or is not accessible.", exception.getMessage());
        verify(localPaymentApi, never()).submit(any(BatchRoutingCreditTransferRequest.class));
        verifyNoInteractions(paymentPayoutRecordMapper, ttPaymentServiceSupport);
    }

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
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
        org.mockito.Mockito.lenient().when(localPaymentApi.route(any(BatchRoutingCreditTransferRequest.class)))
                .thenAnswer(invocation -> PaymentRoutingTestSupport.routeResponse(invocation.getArgument(0)));
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

    private TransferModelApiResponse buildPaymentResponse(
            PaymentResultQueryResponse.StatusEnum status, String errorMessage) {
        TransferPaymentResultQueryResponse result = new TransferPaymentResultQueryResponse();
        result.setEndToEndId("IPE-TXN-TT");
        result.setStatus(toRoutingStatus(status));
        result.setErrorMessage(errorMessage);
        result.setIpeReferenceId("IPE-REF-TT-001");

        TransferModelApiResponse response = new TransferModelApiResponse();
        response.setCode("00");
        response.setData(result);
        return response;
    }

    private static TransferPaymentResultQueryResponse.StatusEnum toRoutingStatus(
            PaymentResultQueryResponse.StatusEnum status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SUCCESS -> TransferPaymentResultQueryResponse.StatusEnum.COMPLETED;
            case FAILED -> TransferPaymentResultQueryResponse.StatusEnum.FAILED;
            case PENDING -> TransferPaymentResultQueryResponse.StatusEnum.PENDING;
            case PROCESSING -> TransferPaymentResultQueryResponse.StatusEnum.PROCESSING;
            case MANUAL_REVIEW -> TransferPaymentResultQueryResponse.StatusEnum.MANUAL_REVIEW;
        };
    }

    @Nested
    class SuccessTests {

        @Test
        void success_mapsIpeRequestAndPersistence() {
            when(localPaymentApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(internalPayeeMapper.selectInternationalPayeeId(anyString(), anyString())).thenReturn("DOMESTIC-PAYEE-1");

            LocalPayment request = buildTtPayment(Currency.HKD);
            Payment result = ttPaymentService.processPayment(request);

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            assertNotNull(result.getTxnRef());
            assertEquals(MESSAGE_ID, result.getRejectMessage() == null ? MESSAGE_ID : result.getRejectMessage());

            ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                    ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
            verify(localPaymentApi).submit(ipeCaptor.capture());
            BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
            assertEquals(PaymentInformation.ChannelEnum.HTH, ipeRequest.getPaymentInformation().getChannel());
            assertEquals("BG3-SIT-OUTWARD", ipeRequest.getPaymentInformation().getChannelReference());
            assertEquals(PaymentInformation.PaymentMethodEnum.TT,
                    ipeRequest.getPaymentInformation().getPaymentMethod());
            assertEquals(GroupHeader.CreationDateTimeEnum.NOW,
                    ipeRequest.getGroupHeader().getCreationDateTime());
            assertEquals(1, ipeRequest.getGroupHeader().getNumberOfTransactions());
            assertEquals(0, new BigDecimal("100.00").compareTo(ipeRequest.getGroupHeader().getControlSum()));
            assertEquals("BG3 internal outward H2H priority", ipeRequest.getTransactions().getFirst()
                    .getRemittanceInformation().getCustomerRemarks());

            RoutingTransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
            assertEquals(ipeRequest.getGroupHeader().getMessageId(), txn.getPaymentIdentification().getEndToEndId());
            assertEquals("HKD", txn.getAmount().getSettlementAmount().getCurrency());
            assertEquals("HKD", txn.getCharges().getChargeAmount().getCurrency());
            assertEquals("S", txn.getCharges().getChargeBearer());
            assertEquals("S", txn.getCharges().getFeeCode());
            assertEquals(com.hkbea.hth.ipe.model.Creditor.TypeEnum.ACCOUNT,
                    txn.getCreditor().getType());
            assertEquals(BENEFICIARY_ACCOUNT, txn.getCreditorAccount().getIdentification());
            assertEquals("HKD", txn.getCreditorAccount().getCurrency());
            assertEquals("BKCHHKHHXXX", txn.getCreditorAgent().getFinancialInstitution().getBicfi());
            assertEquals("TT BENEFICIARY", txn.getCreditor().getName());
            // payee is pre-registered (mock returns a payeeId) -> CB97
            assertEquals("CB97", txn.getSupplementaryData().getCreditorAccount().getProductCode());
            assertEquals("N", txn.getSupplementaryData().getCreditorAccount().getIndicator());

            Tt tt = txn.getSupplementaryData().getTt();
            assertNotNull(tt);
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
            when(localPaymentApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                    .thenThrow(new RuntimeException("IPE timeout"));

            Payment result = ttPaymentService.processPayment(buildTtPayment());

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
            verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
            verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
        }

        @Test
        void pendingInsertException_swallowed() {
            when(localPaymentApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));
            when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                    .thenThrow(new RuntimeException("db down"));

            Payment result = ttPaymentService.processPayment(buildTtPayment());

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        }

        @Test
        void payoutUpdateException_swallowed() {
            when(localPaymentApi.submit(any(BatchRoutingCreditTransferRequest.class)))
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
            request.getCreditor().setCreditorName(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID, exception.getErrCode());
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
            when(localPaymentApi.submit(any(BatchRoutingCreditTransferRequest.class)))
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
            request.getCreditor().getPostalAddress().setAddressLine1("");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void blankTownName_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().getPostalAddress().setTownName(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> ttPaymentService.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void invalidCountry_throws() {
            LocalPayment request = buildTtPayment();
            request.getCreditor().getPostalAddress().setCountry("CHN");

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
            verify(localPaymentApi, never()).submit(any());
        }

        @Test
        void validationFailure_skipsScameter() {
            LocalPayment request = buildTtPayment();
            request.setToAccount(null);

            assertThrows(ParameterException.class, () -> ttPaymentService.processPayment(request));
            verify(scameterValidatorUtil, never()).checkChats(any());
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
        creditor.setCreditorName("TT BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setAddressLine1("Flat 12A 88 Queens Road");
        address.setTownName("Hong Kong");
        address.setCountry("HK");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi("BKCHHKHHXXX");
        // TT identifies the bank by BIC; memberId must stay blank (mutual-exclusion rule)
        financialInstitutionId.setClearingSystemMemberId("");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setCode("GSCB");
        categoryPurpose.setProprietary("CXBSNS");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-TT");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID("UT-TT-CUST-TXN-000001");
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.TT);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setIntermediaryAgent(intermediaryAgent());
        payment.setCategoryPurpose(categoryPurpose);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setCreationDateTime("2026-09-20T10:15:30+08:00");
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(new java.util.ArrayList<>(Collections.singletonList("tt-detail")));
        return payment;
    }

    private IntermediaryAgent intermediaryAgent() {
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi("SNNRHKH2XXX");
        IntermediaryAgent intermediaryAgent = new IntermediaryAgent();
        intermediaryAgent.setFinancialInstitutionId(financialInstitutionId);
        return intermediaryAgent;
    }
}
