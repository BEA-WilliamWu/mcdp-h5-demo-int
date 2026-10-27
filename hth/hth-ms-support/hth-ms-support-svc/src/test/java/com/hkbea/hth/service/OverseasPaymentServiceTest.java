package com.hkbea.hth.service;

import com.hkbea.common.constant.CommonConstant;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.model.BatchCreditTransferRequest;
import com.hkbea.hth.ipe.model.ModelApiResponse;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.ipe.model.TransactionDetailRequest;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.BalanceDetail;
import com.hkbea.hth.model.BatchAccountResult;
import com.hkbea.hth.model.BatchEnquiryResponse;
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
import com.hkbea.hth.service.PaymentCommonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.nullable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverseasPaymentServiceTest {

    protected static final String PARTY_ID = "01552168032777";
    protected static final String DEBIT_ACCOUNT = "01516868006864";
    protected static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    protected static final String MESSAGE_ID = "UT-OS-MESSAGE-000001";
    protected static final String CUSTOMER_TXN_ID = "UT-OS-CUST-TXN-000001";

    static class TestOverseasPaymentService extends OverseasPaymentService {
        @Override
        protected BatchCreditTransferRequest.TransactionTypeEnum transactionType() {
            return BatchCreditTransferRequest.TransactionTypeEnum.TT;
        }

        @Override
        protected void validateSpecific(LocalPayment localPayment) {
        }

        @Override
        protected void persistPaymentRecord(LocalPayment localPayment, BatchCreditTransferRequest request,
                                            Payment payment, PaymentResultQueryResponse result) {
        }

        @Override
        protected void enrichTransaction(TransactionDetailRequest txn, LocalPayment localPayment,
                                         String batchId, String productCode) {
        }
    }

    @InjectMocks
    private TestOverseasPaymentService service;

    @Mock
    protected LocalPaymentApi localPaymentApi;
    @Mock
    protected HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    protected PaymentInternalMapper paymentInternalMapper;
    @Mock
    protected PartyAccountMapper partyAccountMapper;
    @Mock
    protected PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    protected InternalPayeeMapper internalPayeeMapper;
    @Mock
    protected AccountApi accountApi;
    @Mock
    protected CommonHeader headers;
    @Mock
    protected PaymentCommonService paymentCommonService;
    @Mock
    protected HthUserAccessAccountMapper hthUserAccessAccountMapper;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        lenient().when(headers.getMessageId()).thenReturn(MESSAGE_ID);
        lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                        nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(activeAccessAccount()));
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString())).thenReturn("PAYER NAME");
        lenient().when(accountApi.enquireGenericBalance(any()))
                .thenReturn(buildBalanceResponse("999999999", Currency.HKD));
    }

    /** Access account record with an ACTIVE object status (required by validateFromAccount). */
    static HthUserAccessAccount activeAccessAccount() {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setObjectStatus("A");
        return account;
    }

    @Nested
    @DisplayName("BCOH2H-1386 debit account status")
    class DebitAccountStatusTests {
        private void givenAccounts(List<HthUserAccessAccount> accounts) {
            when(hthUserAccessAccountMapper.selectFromAccountInfo(
                    nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(accounts);
        }

        private HthUserAccessAccount accountWithStatus(String status) {
            HthUserAccessAccount account = activeAccessAccount();
            account.setObjectStatus(status);
            return account;
        }

        @Test
        void activeStatusAliasesAndCaseVariantsPass() {
            for (String status : List.of("A", "a", "ACTIVE", "active", "Active")) {
                givenAccounts(List.of(accountWithStatus(status)));
                assertDoesNotThrow(() -> service.validateOverseas(buildOverseasPayment(Currency.HKD)),
                        "Expected active status: " + status);
            }
        }

        @Test
        void nonActiveAndMissingStatusesReturnBusinessError() {
            for (String status : Arrays.asList(null, "", " ", "\t", "I", "INACTIVE",
                    "FROZEN", "frozen", "CLOSED", "DORMANT", "HOLD", "UNKNOWN", " A ", " ACTIVE ")) {
                givenAccounts(List.of(accountWithStatus(status)));
                ServiceException exception = assertThrows(ServiceException.class,
                        () -> service.validateOverseas(buildOverseasPayment(Currency.HKD)),
                        "Expected rejection for status: " + status);
                assertEquals(600061, exception.getErrCode());
                assertEquals("Invalid account status.", exception.getMessage());
            }
        }

        @Test
        void missingAccountResultsReturnAccountNotFound() {
            List<List<HthUserAccessAccount>> results = Arrays.asList(
                    null, Collections.emptyList(), Collections.singletonList(null));
            for (List<HthUserAccessAccount> accounts : results) {
                givenAccounts(accounts);
                ServiceException exception = assertThrows(ServiceException.class,
                        () -> service.validateOverseas(buildOverseasPayment(Currency.HKD)));
                assertEquals(600020, exception.getErrCode());
                assertEquals("The specified account does not exist or is not accessible.",
                        exception.getMessage());
            }
        }

        @Test
        void firstActiveAccountWinsOverLaterInactiveAccount() {
            givenAccounts(List.of(accountWithStatus("A"), accountWithStatus("FROZEN")));
            assertDoesNotThrow(() -> service.validateOverseas(buildOverseasPayment(Currency.HKD)));
        }

        @Test
        void firstInactiveAccountIsNotBypassedByLaterActiveAccount() {
            givenAccounts(List.of(accountWithStatus("FROZEN"), accountWithStatus("A")));
            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.validateOverseas(buildOverseasPayment(Currency.HKD)));
            assertEquals(600061, exception.getErrCode());
        }
    }

    // ===================== Test Data Builders =====================

    static LocalPayment buildOverseasPayment(LocalPayment.PaymentTypeEnum type, Currency currency) {
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
        creditor.setAccountName("OVERSEAS BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setPayeeAddressLine1("Flat 12A 88 Queens Road");
        address.setPayeeTownName("Hong Kong");
        address.setPayeeCreditorCountry("HK");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBICFI("BKCHHKHHXXX");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setPurposeCode("GSCB");
        categoryPurpose.setPurposeProprietary("CXBSNS");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
//        paymentIdentification.setCustomerTransactionId(CUSTOMER_TXN_ID);
        paymentIdentification.setEndToEndId("E2E-OVERSEAS");

        LocalPayment payment = new LocalPayment();
        payment.setPaymentType(type);
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
        payment.setPaymentDetails(new java.util.ArrayList<>(Collections.singletonList("overseas-detail")));
        return payment;
    }

    static LocalPayment buildOverseasPayment(Currency currency) {
        return buildOverseasPayment(LocalPayment.PaymentTypeEnum.TT, currency);
    }

    static ModelApiResponse buildPaymentResponse(PaymentResultQueryResponse.StatusEnum status,
                                                 String errorMessage, String statusReason, String ipeRefId) {
        PaymentResultQueryResponse result = new PaymentResultQueryResponse();
        result.setTransactionId("IPE-TXN-001");
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        result.setStatusReason(statusReason);
        result.setIpeReferenceId(ipeRefId);

        ModelApiResponse response = new ModelApiResponse();
        response.setCode("00");
        response.setData(result);
        return response;
    }

    static ModelApiResponse buildPaymentResponse(PaymentResultQueryResponse.StatusEnum status, String errorMessage) {
        return buildPaymentResponse(status, errorMessage, null, null);
    }

    static BatchEnquiryResponse buildBalanceResponse(String availableBalance, Currency currency) {
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

    static PartyAccount activePartyAccount() {
        PartyAccount account = new PartyAccount();
        account.setPartyId(PARTY_ID);
        account.setAccountNumber(DEBIT_ACCOUNT);
        account.setProductCode("6802");
        account.setStatus("ACTIVE");
        account.setCompanyCode("00123");
        return account;
    }

    @Nested
    class ProcessPaymentTests {

        @Test
        void success_flow() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS,
                            null, null, "IPE-REF-001"));

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            assertNotNull(result.getTxnRef());
            assertNotNull(result.getTxnTime());
            assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());
            assertEquals("E2E-OVERSEAS", result.getOriginalEndtoendID());
            verify(localPaymentApi).submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class));
        }

        @Test
        void ipeFailed_mapsRejected() {
            PaymentResultQueryResponse failed = new PaymentResultQueryResponse();
            failed.setTransactionId("IPE-TXN-001");
            failed.setStatus(PaymentResultQueryResponse.StatusEnum.FAILED);
            failed.setErrorMessage("beneficiary rejected");
            failed.setErrorCode("DOWNSTREAM-001");
            failed.setStatusReason("downstream-error");
            failed.setIpeReferenceId("IPE-REF-002");
            ModelApiResponse response = new ModelApiResponse();
            response.setCode("00");
            response.setData(failed);
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(response);

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));

            assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
            assertEquals("beneficiary rejected", result.getRejectMessage());
            assertEquals("DOWNSTREAM-001", result.getRejectCode());
        }

        @Test
        void nonSuccessCode_returnsProcessing() {
            ModelApiResponse response = new ModelApiResponse();
            response.setCode("99");
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(response);

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        }

        @Test
        void nullResponseData_returnsProcessing() {
            ModelApiResponse response = new ModelApiResponse();
            response.setCode("00");
            response.setData(null);
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(response);

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        }

        @Test
        void ipeException_returnsProcessingAndPersists() {
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenThrow(new RuntimeException("IPE timeout"));

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));

            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
            assertNotNull(result.getTxnRef());
        }

        @Test
        void nonTerminalStatuses_mapProcessing() {
            for (PaymentResultQueryResponse.StatusEnum status : java.util.List.of(
                    PaymentResultQueryResponse.StatusEnum.PENDING,
                    PaymentResultQueryResponse.StatusEnum.PROCESSING,
                    PaymentResultQueryResponse.StatusEnum.MANUAL_REVIEW)) {
                when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                        .thenReturn(buildPaymentResponse(status, null));
                Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));
                assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
            }

            // null 状态
            PaymentResultQueryResponse nullStatus = new PaymentResultQueryResponse();
            nullStatus.setTransactionId("IPE-TXN-NULL");
            ModelApiResponse response = new ModelApiResponse();
            response.setCode("00");
            response.setData(nullStatus);
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(response);
            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));
            assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        }
    }

    @Nested
    @DisplayName("validateOverseas")
    class ValidateOverseasTests {

        @Test
        void nullPayment_throws() {
            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(null));
            assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
        }

        @Test
        void nullFromAccount_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.setFromAccount(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
        }

        @Test
        void blankDebitAccountNumber_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setAccountNumber(" ");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
        }

        @Test
        void nonNumericDebitAccount_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setAccountNumber("015ABC68006864");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID,
                    exception.getErrCode());
        }

        @Test
        void invalidDebitAccountNumberLength_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setAccountNumber("015123");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID,
                    exception.getErrCode());
        }

        @Test
        void invalidDebitAccountNumberPrefix_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setAccountNumber("12345678901234");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID,
                    exception.getErrCode());
        }

        @Test
        void nullDebitCurrency_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setCurrency(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void nullDebitAccountType_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getFromAccount().setAccountType(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
        }

        @Test
        void blankProductCode_flowContinues_currentBehavior() {
            // current behavior: a blank product code does not abort the flow
            // (the former ACCOUNT_NOT_FOUND check on productCode has been removed)
            when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn(" ");
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            Payment result = service.processPayment(buildOverseasPayment(Currency.HKD));
            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        }

        @Test
        void debitAccountNotFound_throws() {
            when(hthUserAccessAccountMapper.selectFromAccountInfo(
                            nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(Collections.emptyList());

            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.processPayment(buildOverseasPayment(Currency.HKD)));
            assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
        }

        @Test
        void inactiveDebitAccount_throws() {
            HthUserAccessAccount inactive = activeAccessAccount();
            inactive.setObjectStatus("I");
            when(hthUserAccessAccountMapper.selectFromAccountInfo(
                            nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn(List.of(inactive));

            ServiceException exception = assertThrows(ServiceException.class,
                    () -> service.processPayment(buildOverseasPayment(Currency.HKD)));
            assertEquals(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID, exception.getErrCode());
        }

        @Test
        void nullTxnAmount_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.setTxnAmount(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
        }

        @Test
        void nullPaymentCurrency_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getTxnAmount().setCurrency(null);

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void invalidPayeeBicCode_throws() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getCreditorAgent().getFinancialInstitutionId().setBICFI("invalid-bic");

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.processPayment(request));
            assertEquals(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID, exception.getErrCode());
        }

        @Test
        void blankPayeeBicCode_passes() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.getCreditorAgent().getFinancialInstitutionId().setBICFI(" ");
            // either-or rule: memberId provided while BICFI is blank keeps the request valid
            request.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId("015");
            when(localPaymentApi.submitOutwardCreditTransfer(any(BatchCreditTransferRequest.class)))
                    .thenReturn(buildPaymentResponse(PaymentResultQueryResponse.StatusEnum.SUCCESS, null));

            Payment result = service.processPayment(request);
            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        }
    }

    @Nested
    @DisplayName("validateAmount / validateTimeOfTransfer")
    class AmountTests {

        @Test
        void nullAmount_throws() {
            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.validateAmount(null, Currency.HKD));
            assertEquals(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void invalidAmounts_throws() {
            assertThrows(ParameterException.class, () -> service.validateAmount(BigDecimal.ZERO, Currency.HKD));
            assertThrows(ParameterException.class,
                    () -> service.validateAmount(new BigDecimal("-1.00"), Currency.HKD));
            assertThrows(ParameterException.class,
                    () -> service.validateAmount(CommonConstant.MAX_AMOUNT.add(BigDecimal.ONE), Currency.HKD));
        }

        @Test
        void jpyFractional_throws() {
            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.validateAmount(new BigDecimal("100.50"), Currency.JPY));
            assertEquals(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        void excessiveScale_throws() {
            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.validateAmount(new BigDecimal("100.123"), Currency.HKD));
            assertEquals(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
        }

        @Test
        @DisplayName("timeOfTransfer 为 null → TIME_OF_TRANSFER_MISSING_OR_INVALID")
        void nullTimeOfTransfer_throws() {
            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.validateTimeOfTransfer(null));
            assertEquals(HTHSupportErrorMapping.TIME_OF_TRANSFER_MISSING_OR_INVALID, exception.getErrCode());
        }
    }

    @Nested
    class BalanceTests {

        @Test
        void sufficientBalance() {
            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(buildBalanceResponse("999999999", Currency.HKD));
            assertTrue(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", new BigDecimal("100")));
        }

        @Test
        void insufficientBalance_throws() {
            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(buildBalanceResponse("99.99", Currency.HKD));

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> service.validateSufficientBalance(DEBIT_ACCOUNT, "HKD", new BigDecimal("100")));
            assertEquals(HTHSupportErrorMapping.INSUFFICIENT_BALANCE, exception.getErrCode());
        }

        @Test
        void inconclusiveBalance_variants() {
            when(accountApi.enquireGenericBalance(any())).thenReturn(null);
            assertFalse(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", BigDecimal.ONE));

            BatchEnquiryResponse empty = new BatchEnquiryResponse();
            empty.setResults(Collections.emptyList());
            when(accountApi.enquireGenericBalance(any())).thenReturn(empty);
            assertFalse(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", BigDecimal.ONE));

            BatchAccountResult failed = new BatchAccountResult();
            failed.setQueryStatus(BatchAccountResult.QueryStatusEnum.FAILED);
            BatchEnquiryResponse failedResponse = new BatchEnquiryResponse();
            failedResponse.setResults(Collections.singletonList(failed));
            when(accountApi.enquireGenericBalance(any())).thenReturn(failedResponse);
            assertFalse(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", BigDecimal.ONE));

            BatchAccountResult nullBalances = new BatchAccountResult();
            nullBalances.setQueryStatus(BatchAccountResult.QueryStatusEnum.SUCCESS);
            nullBalances.setBalances(null);
            BatchEnquiryResponse nullBalancesResponse = new BatchEnquiryResponse();
            nullBalancesResponse.setResults(Collections.singletonList(nullBalances));
            when(accountApi.enquireGenericBalance(any())).thenReturn(nullBalancesResponse);
            assertFalse(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", BigDecimal.ONE));

            when(accountApi.enquireGenericBalance(any()))
                    .thenReturn(buildBalanceResponse("999999999", Currency.USD));
            assertFalse(service.hasEnoughBalance(DEBIT_ACCOUNT, "HKD", BigDecimal.ONE));
        }
    }

    @Nested
    class UtilTests {

        @Test
        void formatPayerAccountNoFmt_14digits() {
            assertEquals("015-521-68-02557-7", service.formatPayerAccountNoFmt("01552168025577"));
        }

        @Test
        void formatPayerAccountNoFmt_15digits() {
            assertEquals("015-521-68-02557-7", service.formatPayerAccountNoFmt("015521680255770"));
        }

        @Test
        void formatPayerAccountNoFmt_shortAndNull() {
            assertEquals("015123", service.formatPayerAccountNoFmt("015123"));
            assertEquals("", service.formatPayerAccountNoFmt(null));
            assertEquals("", service.formatPayerAccountNoFmt("   "));
        }

        @Test
        void accountNameOrFallback_variants() {
            assertEquals("N/A", service.accountNameOrFallback(new LocalPayment()));

            LocalPayment blank = buildOverseasPayment(Currency.HKD);
            blank.getCreditor().setAccountName(" ");
            assertEquals("N/A", service.accountNameOrFallback(blank));

            assertEquals("OVERSEAS BENEFICIARY", service.accountNameOrFallback(buildOverseasPayment(Currency.HKD)));
        }

        @Test
        void validateLocalAccountNumber_variants() {
            service.validateLocalAccountNumber(DEBIT_ACCOUNT, "f", 1, 2);

            assertThrows(ParameterException.class,
                    () -> service.validateLocalAccountNumber(null, "f", 1, 2));
            assertThrows(ParameterException.class,
                    () -> service.validateLocalAccountNumber("015ABC68006864", "f", 1, 2));
            assertThrows(ParameterException.class,
                    () -> service.validateLocalAccountNumber("015123", "f", 1, 2));
            assertThrows(ParameterException.class,
                    () -> service.validateLocalAccountNumber("12345678901234", "f", 1, 2));
        }

        @Test
        void validateCurrency_null_throws() {
            service.validateCurrency(Currency.HKD, "f", 1);
            assertThrows(ParameterException.class, () -> service.validateCurrency(null, "f", 1));
        }
    }

    @Nested
    @DisplayName("buildRequest / buildEnvelope / buildTransactionDetail")
    class BuildRequestTests {

        @Test
        void buildRequest_nowBranch() {
            BatchCreditTransferRequest request = service.buildRequest(buildOverseasPayment(Currency.HKD));

            assertEquals(BatchCreditTransferRequest.ChannelEnum.HTH, request.getChannel());
            assertNotNull(request.getBatchId());
            assertTrue(request.getBatchId().startsWith("HTH"));
            assertEquals("BG3-SIT-OUTWARD", request.getChannelReference());
            assertEquals(BatchCreditTransferRequest.TransactionTypeEnum.TT, request.getTransactionType());
            assertEquals(BatchCreditTransferRequest.TimeOfTransferEnum.NOW, request.getTimeOfTransfer());
            assertEquals(Integer.valueOf(1), request.getTotalCount());
            assertEquals(0, new BigDecimal("100.00").compareTo(request.getTotalAmount()));
            assertEquals("BG3 internal outward H2H priority", request.getCustomerRemarks());
            assertNull(request.getServiceChargeAmount());

            assertEquals(1, request.getTransactions().size());
            TransactionDetailRequest txn = request.getTransactions().getFirst();
            assertEquals(request.getBatchId(), txn.getTransactionId());
            assertEquals(0, new BigDecimal("100.00").compareTo(txn.getDebitAmount()));
            assertEquals(0, new BigDecimal("100.00").compareTo(txn.getHkdEquivalentAmount()));
            assertEquals("HKD", txn.getDebitAmountCcy());
            assertEquals("001501686812006864", txn.getPayerAccountNo());
            assertEquals("015-168-68-00686-4", txn.getPayerAccountNoFmt());
            assertEquals("HKD", txn.getPayerAccountCurrency());
            assertEquals("PAYER NAME", txn.getPayerName());
            assertEquals("CSA", txn.getPayerAccountType());
            assertEquals(Integer.valueOf(0), txn.getAcStatus());
            assertEquals(DEBIT_ACCOUNT, txn.getHdrCibExtAcNo());
            assertEquals("001501686812006864", txn.getHdrCibIntAcNo());
            assertEquals("6802", txn.getPayerAcProdCode());
            assertEquals(Integer.valueOf(123), txn.getPayerAcCompCode());
            assertEquals("GSCB", txn.getPurposeCode());
            assertEquals("CXBSNS", txn.getPurposeProprietary());
            assertEquals("OVERSEAS BENEFICIARY", txn.getRequestRemittanceCreditTransferRequest().getDepositAccountName1());
        }

        @Test
        void buildTransactionDetail_blankCompanyCode() {
            PartyAccount noCompany = activePartyAccount();
            noCompany.setCompanyCode(" ");
            when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                    .thenReturn(noCompany);

            TransactionDetailRequest txn = service.buildTransactionDetail(buildOverseasPayment(Currency.HKD), "BATCH-1");
            assertNull(txn.getPayerAcCompCode());
        }

        @Test
        void buildTransactionDetail_nullCategoryPurpose() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.setCategoryPurpose(null);

            TransactionDetailRequest txn = service.buildTransactionDetail(request, "BATCH-1");
            assertNull(txn.getPurposeCode());
            assertNull(txn.getPurposeProprietary());
        }

        @Test
        void futureTransferDate_setsLaterTimeOfTransfer() {
            LocalPayment request = buildOverseasPayment(Currency.HKD);
            request.setSettlementDate("2027-01-01");

            BatchCreditTransferRequest buildRequest = service.buildRequest(request);

            assertEquals(BatchCreditTransferRequest.TimeOfTransferEnum.LATER, buildRequest.getTimeOfTransfer());
        }
    }
}
