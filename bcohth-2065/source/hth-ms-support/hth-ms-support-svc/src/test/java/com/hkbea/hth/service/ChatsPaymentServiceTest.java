package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.dto.PartyPostalAddress;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.model.BeaAccount;
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
import com.hkbea.hth.service.payment.ChatsPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.PaymentModelConvert;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
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
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(), anyString())).thenReturn("PAYER NAME");
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
        com.hkbea.hth.model.IntermediaryAgent intermediaryAgent = new com.hkbea.hth.model.IntermediaryAgent();
        intermediaryAgent.setFinancialInstitutionId(fid);
        request.setIntermediaryAgent(intermediaryAgent);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("CHATS submits to IPE without calling the local balance API")
    void balanceApiUnavailable_stillSubmitsToIpe() {
        when(accountApi.enquireGenericBalance(any()))
                .thenThrow(new IllegalStateException("Balance API must not be called for CHATS"));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        when(payemtRoutApi.submit(routingRequest)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verifyNoInteractions(accountApi);
        verify(payemtRoutApi).submit(routingRequest);
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("IPE insufficient-funds rejection is returned and persisted with the existing reason fallback")
    void ipeInsufficientFunds_returnsRejectedAndPersists() {
        for (boolean hasErrorMessage : new boolean[]{true, false}) {
            reset(payemtRoutApi, paymentPayoutRecordMapper, accountApi);
            TransferModelApiResponse response = buildSubmitResponse(StatusEnum.FAILED);
            // Synthetic IPE error fixture; no new HTH error code is introduced.
            response.getData().setErrorCode("TEST-IPE-INSUFFICIENT-FUNDS");
            response.getData().setErrorMessage(hasErrorMessage ? "Insufficient available balance" : null);
            response.getData().setStatusReason("Insufficient funds including charges");
            when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class))).thenReturn(response);
            BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

            Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), routingRequest);

            String expectedReason = hasErrorMessage
                    ? "Insufficient available balance" : "Insufficient funds including charges";
            assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
            assertEquals("TEST-IPE-INSUFFICIENT-FUNDS", result.getRejectCode());
            assertEquals(expectedReason, result.getRejectMessage());
            verifyNoInteractions(accountApi);
            var updateCaptor = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
            var order = inOrder(paymentPayoutRecordMapper, payemtRoutApi);
            order.verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
            order.verify(payemtRoutApi).submit(routingRequest);
            order.verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
            assertEquals("Rejected", updateCaptor.getValue().getStatus());
            assertEquals("FAILED", updateCaptor.getValue().getIpeStatus());
            assertEquals(expectedReason, updateCaptor.getValue().getRejectMessage());
            assertEquals("IPE-REF-CHATS-001", updateCaptor.getValue().getIpeRefNo());
        }
    }

    // ===================== Success flow =====================

    @Test
    @DisplayName("success: enriches the routing request, submits to IPE and persists the payout record")
    void success_mapsIpeRequestAndPersistence() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(BATCH_ID, result.getTxnRef());
        assertEquals("E2E-CHATS", result.getOriginalEndtoendID());
        assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());

        ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(ipeCaptor.capture());
        BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
        assertEquals(PaymentConstants.BG3_CHANNEL_REFERENCE_OUTWARD,
                ipeRequest.getPaymentInformation().getChannelReference());
        assertEquals(com.hkbea.hth.ipe.model.PaymentMethodEnum.CHATS,
                ipeRequest.getPaymentInformation().getPaymentMethod());

        RoutingTransactionDetailRequest txn = ipeRequest.getTransactions().getFirst();
        assertEquals("PAYER NAME", txn.getDebtor().getName());
        assertEquals("CHATS BENEFICIARY", txn.getCreditor().getName());
        assertEquals(BENEFICIARY_ACCOUNT, txn.getCreditorAccount().getIdentification());
        assertEquals("HKD", txn.getCreditorAccount().getCurrency());
        assertEquals("004", txn.getCreditorAgent().getFinancialInstitution().getMemberId());
        assertEquals("S", txn.getCharges().getChargeBearer());

        com.hkbea.hth.ipe.model.SupplementaryData supplementaryData = txn.getSupplementaryData();
        assertNotNull(supplementaryData);
        com.hkbea.hth.ipe.model.ChatsRemittance chatsRemittance = supplementaryData.getChatsRemittance();
        assertNotNull(chatsRemittance);
        assertEquals("", chatsRemittance.getChatAcTpCode());
        assertEquals("Y", chatsRemittance.getPrntAdvInd());
        assertEquals("y", chatsRemittance.getLmInd());
        assertEquals("CHATS BENEFICIARY", chatsRemittance.getDepositAccountName1());
        assertEquals("CHATS BENEFICIARY", chatsRemittance.getDepositAccountName2());

        ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
        PaymentPayoutRecord pending = insertCaptor.getValue();
        assertEquals(BATCH_ID, pending.getPaymentId());
        assertEquals("E2E-CHATS", pending.getEndToEndId());
        assertEquals(CUSTOMER_TXN_ID, pending.getCustomerTransactionId());
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
        assertEquals(StatusEnum.SUCCESS.value(), updated.getIpeStatus());
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
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
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
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
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

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("closed debtor accounts are rejected")
    void debitAccountClosed_throws() {
        PartyAccount closed = activePartyAccount();
        closed.setStatus("CLOSED");
        when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(closed);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank party ids fall back to the mock party and skip the payee lookup")
    void pendingInsert_blankPartyId_usesMockPartyId() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
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

    // ===================== BCOH2H-2065 v0.8 / IPE corrections =====================

    @Test
    void customerEndToEndIdIsSubmittedWithoutReplacingTheHthReference() {
        for (String customerId : Arrays.asList(null, "", "test123456end", " ", "  payer reference  ", "12345678901234567890123456789012345")) {
            reset(payemtRoutApi, paymentPayoutRecordMapper, accountApi);
            LocalPayment input = buildChatsPayment(Currency.HKD);
            input.getPaymentIdentification().setEndToEndId(customerId);
            BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
            String batchId = request.getGroupHeader().getMessageId();
            String expectedId = customerId == null || customerId.isBlank() ? batchId : customerId;
            // Missing/blank payer references are echoed back as the NOTPROVIDED default.
            String expectedEcho = customerId == null || customerId.isBlank()
                    ? PaymentConstants.END_TO_END_ID : customerId;
            // Also verify the request that would be sent to IPE routing.
            assertEquals(expectedId, request.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

            Payment result = chatsPaymentService.processPayment(input, request);

            assertEquals(expectedId, request.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            assertEquals(batchId, result.getTxnRef());
            if (customerId != null && !customerId.isBlank()) {
            assertNotEquals(expectedId, result.getTxnRef());
            }
            assertEquals(expectedEcho, result.getOriginalEndtoendID());
            assertEquals(CUSTOMER_TXN_ID, result.getOriginalTransactionID());
            var insert = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
            verify(paymentPayoutRecordMapper).insert(insert.capture());
            assertEquals(batchId, insert.getValue().getPaymentId());
            assertEquals(expectedEcho, insert.getValue().getEndToEndId());
            assertEquals(CUSTOMER_TXN_ID, insert.getValue().getCustomerTransactionId());
            verify(payemtRoutApi).submit(request);
            verifyNoInteractions(accountApi);
        }
    }

    @Test
    void missingIdentificationDefaultsForIpeWithoutChangingResponseEcho() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        input.setPaymentIdentification(null);
        BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(input, request);

        assertEquals(request.getGroupHeader().getMessageId(), request.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
        assertEquals(request.getGroupHeader().getMessageId(), result.getTxnRef());
        assertEquals(PaymentConstants.END_TO_END_ID, result.getOriginalEndtoendID());
    }

    @Test
    void automaticChatsReplacesRoutingIdButRetainsGeneratedPaymentId() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        input.setPaymentType(LocalPayment.PaymentTypeEnum.OTHERS);
        BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
        String batchId = request.getGroupHeader().getMessageId();
        assertEquals(batchId, request.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
        input.setPaymentType(LocalPayment.PaymentTypeEnum.CHATS);
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(input, request);

        assertEquals("E2E-CHATS", request.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
        assertEquals(batchId, result.getTxnRef());
        var captured = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(captured.capture());
        assertSame(request, captured.getValue());
    }

    @Test
    void oversizedEndToEndIdIsRejectedBeforeSubmission() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        input.getPaymentIdentification().setEndToEndId("E".repeat(36));

        ParameterException error = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(input, buildRoutingRequest()));

        assertEquals(HTHSupportErrorMapping.END_TO_END_ID_INVALID, error.getErrCode());
        verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper);
    }

    @Test
    void creditorNameIsPreservedAcrossDepositNameSegments() {
        for (int length : new int[]{1, 34, 35, 36, 69, 70, 71, 104, 105, 106, 139, 140}) {
            reset(payemtRoutApi);
            LocalPayment input = buildChatsPayment(Currency.HKD);
            String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(6).substring(0, length);
            input.getCreditor().setCreditorName(name);
            BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
            when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

            Payment result = chatsPaymentService.processPayment(input, request);

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            var txn = request.getTransactions().getFirst();
            assertEquals(name, txn.getCreditor().getName());
            assertEquals(name, txn.getCreditorAccount().getAccountName());
            var remittance = txn.getSupplementaryData().getChatsRemittance();
            assertEquals(name.substring(0, Math.min(35, length)), remittance.getDepositAccountName1());
            assertEquals(length <= 35 ? name : name.substring(35, Math.min(70, length)),
                    remittance.getDepositAccountName2());
            assertEquals(length <= 70 ? null : name.substring(70, Math.min(105, length)),
                    remittance.getDepositAccountName3());
            assertEquals(length <= 105 ? null : name.substring(105), remittance.getDepositAccountName4());
            verify(payemtRoutApi).submit(request);
        }
    }

    @Test
    void creditorNameAbove140IsRejectedBeforeSubmission() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        input.getCreditor().setCreditorName("N".repeat(141));

        ParameterException error = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(input, buildRoutingRequest()));

        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_INVALID, error.getErrCode());
        verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper, scameterValidatorUtil);
    }

    @Test
    void debtorAddressFromBcoPreservesAllFiveLinesInIpeRequest() {
        PartyPostalAddress source = new PartyPostalAddress();
        source.setAddress1("  " + "A".repeat(40) + "  ");
        source.setAddress2("B".repeat(40));
        source.setAddress3("C".repeat(40));
        source.setAddress4("D".repeat(40));
        source.setAddress5("E".repeat(40));
        when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                .thenReturn(source);
        BatchRoutingCreditTransferRequest request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), request);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        var address = request.getTransactions().getFirst().getDebtor().getPostalAddress();
        String expected = String.join(" ", "A".repeat(40), "B".repeat(40), "C".repeat(40),
                "D".repeat(40), "E".repeat(40));
        assertEquals(expected.substring(0, 70), address.getAddressLine1());
        assertEquals(expected.substring(70, 140), address.getAddressLine2());
        assertEquals(expected.substring(140), address.getAddressLine3());
        assertNull(address.getAddressLine4());
        assertNull(address.getTownName());
        assertNull(address.getCountry());
        assertEquals("Flat 12A 88 Queens Road", request.getTransactions().getFirst()
                .getCreditor().getPostalAddress().getAddressLine1());
        verify(partyPreferenceMapper).selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802");
        verify(payemtRoutApi).submit(request);
    }

    @Test
    void debtorAddressCanBeMissingOrAllBlank() {
        for (PartyPostalAddress source : Arrays.asList(null, new PartyPostalAddress())) {
            reset(payemtRoutApi, partyPreferenceMapper);
            when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(), anyString())).thenReturn("PAYER NAME");
            if (source != null) {
                source.setAddress1("   ");
                source.setAddress5("");
            }
            when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                    .thenReturn(source);
            BatchRoutingCreditTransferRequest request = buildRoutingRequest();
            when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

            Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), request);

            assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
            assertNull(request.getTransactions().getFirst().getDebtor().getPostalAddress());
            verify(payemtRoutApi).submit(request);
        }
    }

    @Test
    void debtorAddressSkipsEmptyLinesWithoutDroppingTheFifthLine() {
        PartyPostalAddress source = new PartyPostalAddress();
        source.setAddress2("  SECOND LINE  ");
        source.setAddress4(" ");
        source.setAddress5("LAST LINE");
        when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                .thenReturn(source);
        BatchRoutingCreditTransferRequest request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), request);

        var address = request.getTransactions().getFirst().getDebtor().getPostalAddress();
        assertEquals("SECOND LINE LAST LINE", address.getAddressLine1());
        assertNull(address.getAddressLine2());
    }

    @Test
    void debtorAddressRespectsIpeLineBoundaries() {
        for (int length : new int[]{70, 71, 280}) {
            reset(payemtRoutApi, partyPreferenceMapper);
            when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(), anyString())).thenReturn("PAYER NAME");
            PartyPostalAddress source = new PartyPostalAddress();
            source.setAddress1("A".repeat(length));
            when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                    .thenReturn(source);
            BatchRoutingCreditTransferRequest request = buildRoutingRequest();
            when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

            chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), request);

            var address = request.getTransactions().getFirst().getDebtor().getPostalAddress();
            String joined = Stream.of(address.getAddressLine1(), address.getAddressLine2(),
                            address.getAddressLine3(), address.getAddressLine4())
                    .filter(Objects::nonNull).collect(Collectors.joining());
            assertEquals("A".repeat(length), joined);
            Stream.of(address.getAddressLine1(), address.getAddressLine2(),
                            address.getAddressLine3(), address.getAddressLine4())
                    .filter(Objects::nonNull).forEach(line -> assertTrue(line.length() <= 70));
        }
    }

    @Test
    void debtorAddressBeyondFourLinesIsRejectedWithoutTruncation() {
        PartyPostalAddress source = new PartyPostalAddress();
        source.setAddress1("A".repeat(281));
        when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                .thenReturn(source);

        ParameterException error = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));

        assertEquals(HTHSupportErrorMapping.PARAMETER_INVALID_EXCEPTION, error.getErrCode());
        assertEquals("A".repeat(281), source.getAddress1());
        verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper);
    }

    @Test
    void debtorAddressDatabaseFailureStopsSubmission() {
        when(partyPreferenceMapper.selectDebtorPostalAddress(PARTY_ID, DEBIT_ACCOUNT, "6802"))
                .thenThrow(new IllegalStateException("BCO address query failed"));

        assertThrows(IllegalStateException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));

        verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper);
    }

    @Test
    void thirtyFiveCharacterNameAndTownAndSeventyCharacterLinesArePreserved() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        String name = "N".repeat(35);
        input.getCreditor().setCreditorName(name);
        input.getCreditor().getPostalAddress().setAddressLine1("A".repeat(70));
        input.getCreditor().getPostalAddress().setAddressLine2("B".repeat(70));
        input.getCreditor().getPostalAddress().setTownName("T".repeat(35));
        BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        chatsPaymentService.processPayment(input, request);

        var txn = request.getTransactions().getFirst();
        assertEquals(name, txn.getCreditor().getName());
        assertEquals(name, txn.getCreditorAccount().getAccountName());
        assertEquals(name, txn.getSupplementaryData().getChatsRemittance().getDepositAccountName1());
        assertEquals(name, txn.getSupplementaryData().getChatsRemittance().getDepositAccountName2());
        assertEquals("A".repeat(70), txn.getCreditor().getPostalAddress().getAddressLine1());
        assertEquals("B".repeat(70), txn.getCreditor().getPostalAddress().getAddressLine2());
        assertEquals("T".repeat(35), txn.getCreditor().getPostalAddress().getTownName());
    }

    @Test
    void townMustFollowV08LengthAndCharset() {
        for (String town : Arrays.asList("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", "Hong Kong中")) {
            reset(payemtRoutApi, paymentPayoutRecordMapper);
            LocalPayment input = buildChatsPayment(Currency.HKD);
            input.getCreditor().getPostalAddress().setTownName(town);

            ParameterException error = assertThrows(ParameterException.class,
                    () -> chatsPaymentService.processPayment(input, buildRoutingRequest()));

            assertEquals(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID, error.getErrCode());
            verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper);
        }
    }

    @Test
    void oversizedOrUnsupportedAddressLinesAreRejected() {
        for (String field : Arrays.asList("addressLine1", "addressLine2")) {
            for (String value : List.of("A".repeat(71), "中", " ".repeat(71))) {
                reset(payemtRoutApi, paymentPayoutRecordMapper);
                LocalPayment input = buildChatsPayment(Currency.HKD);
                if (field.equals("addressLine1")) {
                    input.getCreditor().getPostalAddress().setAddressLine1(value);
                } else {
                    input.getCreditor().getPostalAddress().setAddressLine2(value);
                }
                ParameterException error = assertThrows(ParameterException.class,
                        () -> chatsPaymentService.processPayment(input, buildRoutingRequest()));
                assertEquals(field.equals("addressLine1") ? 500141 : 500144, error.getErrCode());
            }
            verifyNoInteractions(payemtRoutApi, paymentPayoutRecordMapper);
        }
    }

    @Test
    void optionalAddressCanBeOmittedAndPayoutIsStillInserted() {
        LocalPayment input = buildChatsPayment(Currency.HKD);
        input.getCreditor().setPostalAddress(null);
        BatchRoutingCreditTransferRequest request = PaymentModelConvert.toRoutingRequest(input);
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        assertEquals(Payment.StatusEnum.SUCCESS, chatsPaymentService.processPayment(input, request).getStatus());

        assertNull(request.getTransactions().getFirst().getCreditor().getPostalAddress());
        var captured = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(captured.capture());
        assertNull(captured.getValue().getCreditAddressLine1());
        assertNull(captured.getValue().getCreditTownName());
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
            StatusEnum status) {
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
