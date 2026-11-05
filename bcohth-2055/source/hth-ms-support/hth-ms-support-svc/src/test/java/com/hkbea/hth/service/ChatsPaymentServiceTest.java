package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.ChatsRemittance;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
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
import com.hkbea.hth.service.payment.ChatsPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

/**
 * CHATS payment: validation error codes, the enrich -> submit -> persist flow
 * and its degradation paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatsPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";
    private static final String MESSAGE_ID = "UT-CHATS-MESSAGE-000001";
    private static final String CUSTOMER_TXN_ID = "UT-CHATS-CUST-TXN-000001";
    private static final String BATCH_ID = "BATCH-CHATS-1";

    @InjectMocks
    private ChatsPaymentService chatsPaymentService;

    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private ScameterValidatorUtil scameterValidatorUtil;
    @Mock
    private AccountApi accountApi;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private CommonHeader headers;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private CacheHelperUtils cacheHelperUtils;

    private MockedStatic<LocalDateTime> localDateTimeMock;

    @BeforeEach
    void setUp() {
        // Mock LocalDateTime.now() to return a time within CHATS service window (Mon-Fri 9:00-17:00)
        localDateTimeMock = Mockito.mockStatic(LocalDateTime.class, Mockito.CALLS_REAL_METHODS);
        LocalDateTime fixedDateTime = LocalDateTime.of(2024, 1, 15, 10, 30); // Monday 10:30
        localDateTimeMock.when(LocalDateTime::now).thenReturn(fixedDateTime);

        new HTHSupportErrorMapping();
        lenient().when(headers.getMessageId()).thenReturn(MESSAGE_ID);
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString())).thenReturn("PAYER NAME");
        lenient().when(accountApi.enquireGenericBalance(any()))
                .thenReturn(OverseasPaymentServiceTest.buildBalanceResponse("999999999", Currency.HKD));
        lenient().when(internalPayeeMapper.selectDomesticPayeeId(anyString(), anyString()))
                .thenReturn("DOMESTIC-PAYEE-1");
        lenient().when(internalPayeeMapper.findPreDesignatedAccounts(anyString(), anyString())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        if (localDateTimeMock != null) {
            localDateTimeMock.close();
        }
    }

    // ===================== Validation =====================

    @Test
    @DisplayName("null payment is rejected as missing mandatory parameter")
    void nullPayment_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(null, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account is rejected with DEBIT_ACCOUNT_MISSING")
    void missingDebitAccount_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.setFromAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID")
    void malformedDebitAccountNumber_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getFromAccount().setAccountNumber("015123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debit currency is rejected with DEBIT_CURRENCY_MISSING_OR_INVALID")
    void missingDebitCurrency_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getFromAccount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account type is rejected with DEBIT_ACCOUNT_TYPE_MISSING")
    void missingDebitAccountType_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getFromAccount().setAccountType(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency outside HKD/CNY/USD/EUR is rejected with CHATS_DEBIT_CURRENCY_NOT_SUPPORTED")
    void unsupportedDebitCurrency_throws() {
        LocalPayment request = buildChatsPayment(Currency.JPY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CHATS_DEBIT_CURRENCY_NOT_SUPPORTED, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency outside HKD/CNY/USD/EUR is rejected with CHATS_PAYMENT_CURRENCY_NOT_SUPPORTED")
    void unsupportedPaymentCurrency_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getTxnAmount().setCurrency(Currency.JPY);
        request.getToAccount().setCurrency(Currency.JPY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CHATS_PAYMENT_CURRENCY_NOT_SUPPORTED, exception.getErrCode());
    }

    @Test
    @DisplayName("beneficiary currency outside HKD/CNY/USD/EUR is rejected with CHATS_CREDITOR_CURRENCY_NOT_SUPPORTED")
    void unsupportedBeneficiaryCurrency_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getToAccount().setCurrency(Currency.JPY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CHATS_CREDITOR_CURRENCY_NOT_SUPPORTED, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency differing from payment currency is rejected")
    void debitCurrencyMismatch_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getTxnAmount().setCurrency(Currency.USD);
        request.getToAccount().setCurrency(Currency.USD);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency differing from beneficiary currency is rejected")
    void paymentCurrencyMismatch_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("missing instructedAmount is rejected with TXN_AMOUNT_MISSING")
    void missingTxnAmount_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.setTxnAmount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("zero amount is rejected with AMOUNT_MISSING_OR_INVALID")
    void zeroAmount_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getTxnAmount().setAmount(BigDecimal.ZERO);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing toAccount is rejected with CREDITOR_ACCOUNT_MISSING")
    void missingToAccount_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed beneficiary account number is rejected")
    void malformedBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getToAccount().setAccountNumber("ABC-123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank payee name is rejected with BENEFICIARY_ACCOUNT_NAME_MISSING")
    void blankPayeeName_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.getCreditor().setCreditorName(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("invalid chargeBearer is rejected by the shared CHATS rules")
    void missingChargesOption_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.setChargesOption(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TT_AND_CHATS_CHARGEBEARER_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("CHATS must not carry an intermediary agent BICFI")
    void intermediaryBicfiMustBeBlank_throws() {
        LocalPayment request = buildChatsPayment(Currency.HKD);
        FinancialInstitutionId fid = new FinancialInstitutionId();
        fid.setBicfi("SNNRHKH2XXX");
        IntermediaryAgent intermediaryAgent = new IntermediaryAgent();
        intermediaryAgent.setFinancialInstitutionId(fid);
        request.setIntermediaryAgent(intermediaryAgent);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("insufficient balance is rejected before any IPE submission")
    void insufficientBalance_throws() {
        when(accountApi.enquireGenericBalance(any()))
                .thenReturn(OverseasPaymentServiceTest.buildBalanceResponse("1.00", Currency.HKD));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.INSUFFICIENT_FROM_ACCOUNT_BALANCE, exception.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
        verify(scameterValidatorUtil, never()).checkChats(any());
        verify(paymentPayoutRecordMapper, never()).insert(any(PaymentPayoutRecord.class));
    }

    // ===================== Success flow =====================

    @Test
    @DisplayName("success: enriches the routing request, submits to IPE and persists the payout record")
    void success_mapsIpeRequestAndPersistence() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals("E2E-CHATS", result.getTxnRef());
        assertEquals("E2E-CHATS", result.getOriginalEndtoendID());
        assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());

        ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(ipeCaptor.capture());
        BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
        assertEquals(PaymentConstants.BG3_CHANNEL_REFERENCE_OUTWARD,
                ipeRequest.getPaymentInformation().getChannelReference());
        assertEquals(PaymentInformation.PaymentMethodEnum.CHATS,
                ipeRequest.getPaymentInformation().getPaymentMethod());

        RoutingTransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
        assertEquals("PAYER NAME", txn.getDebtor().getName());
        assertEquals("CHATS BENEFICIARY", txn.getCreditor().getName());
        assertEquals(BENEFICIARY_ACCOUNT, txn.getCreditorAccount().getIdentification());
        assertEquals("HKD", txn.getCreditorAccount().getCurrency());
        assertEquals("004", txn.getCreditorAgent().getFinancialInstitution().getMemberId());
        assertEquals("S", txn.getCharges().getChargeBearer());

        ChatsRemittance chatsRemittance = txn.getSupplementaryData().getChatsRemittance();
        assertNotNull(chatsRemittance);
        assertEquals("", chatsRemittance.getChatAcTpCode());
        assertEquals("Y", chatsRemittance.getPrntAdvInd());
        assertEquals("y", chatsRemittance.getLmInd());
        assertEquals("CHATS BENEFICIARY", chatsRemittance.getDepositAccountName1());
        assertEquals("6304357992232185", chatsRemittance.getDepositAccountName2());

        verify(scameterValidatorUtil).checkChats(any());

        ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
        PaymentPayoutRecord pending = insertCaptor.getValue();
        assertEquals("CHATS", pending.getPaymentType());
        assertEquals(MESSAGE_ID, pending.getMessageId());
        assertEquals(PaymentConstants.MOCK_PARTY_NAME, pending.getPartyName());
        assertEquals("DOMESTIC-PAYEE-1", pending.getPayeeId());
        assertEquals(PaymentConstants.PAYEE_IDFN_ACCTNO, pending.getPayeeIdfn());
        assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
        assertEquals("HKD", pending.getDebitAccountCur());
        assertEquals("CUR", pending.getDebitAccountType());
        assertEquals("6802", pending.getProdCode());
        assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
        assertEquals("HKD", pending.getCreditAccountCur());
        assertEquals("SHA", pending.getSvcType());
        assertEquals("chats-detail", pending.getRemarks());
        assertNull(pending.getStatus());
        assertEquals("A", pending.getStatusFlag());

        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        PaymentPayoutRecord updated = updateCaptor.getValue();
        assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
        assertEquals(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS.value(), updated.getIpeStatus());
        assertEquals("IPE-REF-CHATS-001", updated.getIpeRefNo());
    }

    @Test
    @DisplayName("IPE submission failure returns PROCESSING and still persists the payout record")
    void ipeException_returnsProcessingAndPersists() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenThrow(new RuntimeException("IPE timeout"));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("pending payout insert failure is swallowed")
    void pendingInsertException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = assertDoesNotThrow(
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("payout update failure is swallowed")
    void payoutUpdateException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.updateById(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = assertDoesNotThrow(
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    // ===================== Debtor account status / persistence fallback =====================

    @Test
    @DisplayName("debtor accounts missing from party_account are rejected as not found")
    void debitAccountNotFound_throws() {
        when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(null);

        com.hkbea.common.exception.ServiceException exception = assertThrows(
                com.hkbea.common.exception.ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("closed debtor accounts are rejected")
    void debitAccountClosed_throws() {
        PartyAccount closed = activePartyAccount();
        closed.setStatus("CLOSED");
        when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(closed);

        com.hkbea.common.exception.ServiceException exception = assertThrows(
                com.hkbea.common.exception.ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank party ids fall back to the mock party and skip the payee lookup")
    void pendingInsert_blankPartyId_usesMockPartyId() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));
        LocalPayment request = buildChatsPayment(Currency.HKD);
        request.setPartyId(" ");

        Payment result = chatsPaymentService.processPayment(request, buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
        assertEquals(PaymentConstants.MOCK_PARTY_ID, insertCaptor.getValue().getPartyId());
        assertNull(insertCaptor.getValue().getPayeeId());
    }

    // ===================== BCOH2H-2055 submission results =====================

    @Test
    void acceptedSubmission_remainsProcessing() {
        when(payemtRoutApi.submit(any())).thenReturn(buildSubmitResponse(
                TransferPaymentResultQueryResponse.StatusEnum.PROCESSING));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(update.capture());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), update.getValue().getStatus());
    }

    @Test
    void ipeInsufficientBalance_returnsAndPersistsOriginalRejection() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.FAILED);
        response.getData().setErrorCode("TEST-INSUFFICIENT-BALANCE");
        response.getData().setErrorMessage("Insufficient balance including bank charges");
        response.getData().setStatusReason("HOST_REJECTED");
        when(payemtRoutApi.submit(any())).thenReturn(response);

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("TEST-INSUFFICIENT-BALANCE", result.getRejectCode());
        assertEquals("Insufficient balance including bank charges", result.getRejectMessage());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(update.capture());
        assertEquals(Payment.StatusEnum.REJECTED.value(), update.getValue().getStatus());
        assertEquals(result.getRejectMessage(), update.getValue().getRejectMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectionWithoutErrorMessage_fallsBackToStatusReason(String missingReason) {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.FAILED);
        response.getData().setErrorCode("TEST-IPE-REJECTED");
        response.getData().setErrorMessage(missingReason);
        response.getData().setStatusReason("INVALID_VALUE_DATE");
        when(payemtRoutApi.submit(any())).thenReturn(response);

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("TEST-IPE-REJECTED", result.getRejectCode());
        assertEquals("INVALID_VALUE_DATE", result.getRejectMessage());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(update.capture());
        assertEquals(Payment.StatusEnum.REJECTED.value(), update.getValue().getStatus());
        assertEquals("INVALID_VALUE_DATE", update.getValue().getRejectMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectionWithoutAnyReason_usesExistingGenericFallback(String missingReason) {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.FAILED);
        response.getData().setErrorMessage(missingReason);
        response.getData().setStatusReason(missingReason);
        when(payemtRoutApi.submit(any())).thenReturn(response);

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertNull(result.getRejectCode());
        assertEquals("Rejected by payment engine (no detail returned)", result.getRejectMessage());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(update.capture());
        assertEquals(result.getRejectMessage(), update.getValue().getRejectMessage());
    }

    @Test
    void overCharge_isCachedWithoutOverwritingServiceFee() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS);
        response.getData().setOverChargeAmount(new BigDecimal("30.00"));
        response.getData().setOverChargeCcy("HKD");
        when(payemtRoutApi.submit(any())).thenReturn(response);
        var mtbInfo = new com.hkbea.hth.dto.MtbInfoDto();
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<com.hkbea.hth.dto.MtbInfoDto> updater = invocation.getArgument(0);
            updater.accept(mtbInfo);
            return null;
        }).when(cacheHelperUtils).saveMtbInfo(any());

        chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals("HKD", mtbInfo.getOverChargeCcy());
        assertEquals(new BigDecimal("30.00"), mtbInfo.getOverChargeAmount());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(update.capture());
        assertNull(update.getValue().getSvcAmount());
        assertNull(update.getValue().getSvcCur());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildChatsPayment(Currency currency) {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(currency.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setId(BENEFICIARY_ACCOUNT);
        toAccount.setAccountNumber(BENEFICIARY_ACCOUNT);
        toAccount.setCurrency(currency);
        toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(currency);

        Creditor creditor = new Creditor();
        creditor.setCreditorName("CHATS BENEFICIARY");
        PostalAddress address = new PostalAddress();
        address.setAddressLine1("Flat 12A 88 Queens Road");
        address.setTownName("Hong Kong");
        address.setCountry("HK");
        creditor.setPostalAddress(address);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setClearingSystemMemberId("004");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        CategoryPurpose categoryPurpose = new CategoryPurpose();
        categoryPurpose.setCode("GSCB");
        categoryPurpose.setProprietary("CXBSNS");

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-CHATS");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID(CUSTOMER_TXN_ID);
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.CHATS);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setCategoryPurpose(categoryPurpose);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(new java.util.ArrayList<>(List.of("chats-detail")));
        return payment;
    }

    private BatchRoutingCreditTransferRequest buildRoutingRequest() {
        BatchRoutingCreditTransferRequest request = new BatchRoutingCreditTransferRequest();
        request.setPaymentInformation(new PaymentInformation());
        RoutingTransactionDetailRequest txn = new RoutingTransactionDetailRequest();
        com.hkbea.hth.ipe.model.PaymentIdentification identification =
                new com.hkbea.hth.ipe.model.PaymentIdentification();
        identification.setEndToEndId("E2E-CHATS");
        txn.setPaymentIdentification(identification);
        request.setTransactions(List.of(txn));
        GroupHeader groupHeader = new GroupHeader();
        groupHeader.setMessageId(BATCH_ID);
        request.setGroupHeader(groupHeader);
        return request;
    }

    private TransferModelApiResponse buildSubmitResponse(
            TransferPaymentResultQueryResponse.StatusEnum status) {
        TransferPaymentResultQueryResponse result = new TransferPaymentResultQueryResponse();
        result.setEndToEndId("IPE-TXN-CHATS");
        result.setStatus(status);
        result.setIpeReferenceId("IPE-REF-CHATS-001");

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
}
