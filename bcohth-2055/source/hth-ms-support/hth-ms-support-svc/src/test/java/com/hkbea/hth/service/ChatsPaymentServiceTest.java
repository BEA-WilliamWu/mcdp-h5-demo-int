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
import com.hkbea.hth.service.payment.PaymentCommonRoutingBuilder;
import com.hkbea.hth.service.payment.PaymentInstructionConvert;
import com.hkbea.hth.service.payment.PaymentRoutingSubmitter;
import com.hkbea.hth.utils.AccountUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.cache.CacheManager;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
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
    private CacheManager cacheManager;
    private MockedStatic<LocalDateTime> serviceTime;

    @BeforeEach
    void setUp() {
        LocalDateTime businessTime = LocalDateTime.of(2026, 11, 4, 10, 0);
        serviceTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
        serviceTime.when(LocalDateTime::now).thenReturn(businessTime);
        new HTHSupportErrorMapping();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "chats-posting-test"),
                PaymentPayoutRecord.class);
        when(paymentPayoutRecordMapper.update(any(PaymentPayoutRecord.class), any())).thenReturn(1);
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class))).thenReturn(1);
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
        serviceTime.close();
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
        assertEquals(Payment.StatusEnum.PROCESSING.value(), pending.getStatus());
        assertEquals("A", pending.getStatusFlag());

        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).update(updateCaptor.capture(), any());
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
        verify(paymentPayoutRecordMapper).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    @DisplayName("pending payout insert failure prevents IPE submission")
    void pendingInsertException_preventsSubmission() {
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));
        var error = assertThrows(com.hkbea.common.exception.ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.GENERAL_SERVICE_EXCEPTION, error.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
        verify(paymentPayoutRecordMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    @DisplayName("payout update failure is swallowed")
    void payoutUpdateException_swallowed() {
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.update(any(PaymentPayoutRecord.class), any()))
                .thenThrow(new RuntimeException("db down"));

        Payment result = assertDoesNotThrow(
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).update(any(PaymentPayoutRecord.class), any());
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

    // ===================== BCOH2H-2065 instruction fidelity =====================

    @ParameterizedTest
    @ValueSource(strings = {"ABSYHKH1", "ABSYHKH1XXX"})
    void beneficiaryBic_isPreservedInSubmittedJson(String bic) {
        LocalPayment payment = buildChatsPayment(Currency.CNY);
        payment.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId(null);
        payment.getCreditorAgent().getFinancialInstitutionId().setBicfi(bic);

        BatchRoutingCreditTransferRequest submitted = submitConvertedInstruction(payment);
        com.fasterxml.jackson.databind.JsonNode txn = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules().valueToTree(submitted).path("transactions").get(0);

        assertEquals(bic, txn.at("/creditorAgent/financialInstitution/bicfi").asText());
        assertEquals("BEASHKHHXXX", txn.at("/debtorAgent/financialInstitution/bicfi").asText());
        assertEquals("/IFT01/", txn.at("/purpose/proprietary").asText());
        assertTrue(txn.at("/purpose/code").isMissingNode());
        assertEquals(AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                txn.at("/debtorAccount/internalAccountNo").asText());
        assertEquals("6304357992232185", txn.at("/supplementaryData/chatsRemittance/depositAccountName2").asText());
    }

    @Test
    void memberIdOnly_doesNotInventABeneficiaryBic() {
        var txn = submitConvertedInstruction(buildChatsPayment(Currency.HKD)).getTransactions().getFirst();
        assertEquals("004", txn.getCreditorAgent().getFinancialInstitution().getMemberId());
        assertNull(txn.getCreditorAgent().getFinancialInstitution().getBicfi());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "ABSYHKH1XXXX", "ABSYHKH1XXX "})
    void invalidBeneficiaryBic_isRejectedBeforeSubmission(String bic) {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId(null);
        payment.getCreditorAgent().getFinancialInstitutionId().setBicfi(bic);
        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(payment, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID, exception.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
        verify(paymentPayoutRecordMapper, never()).insert(any(PaymentPayoutRecord.class));
    }

    @Test
    void bicAndMemberIdTogether_areRejectedBeforeSubmission() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.getCreditorAgent().getFinancialInstitutionId().setBicfi("ABSYHKH1XXX");
        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(payment, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
    }

    @Test
    void missingBankIdentifier_isRejectedBeforeSubmission() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.setCreditorAgent(null);
        ParameterException exception = assertThrows(ParameterException.class,
                () -> chatsPaymentService.processPayment(payment, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
    }

    @Test
    void missingCategoryPurpose_usesBankOwnedChatsPurpose() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.setCategoryPurpose(null);
        var txn = submitConvertedInstruction(payment).getTransactions().getFirst();
        assertEquals("/IFT01/", txn.getPurpose().getProprietary());
        assertNull(txn.getPurpose().getCode());
    }

    @ParameterizedTest
    @EnumSource(value = Currency.class, names = {"HKD", "CNY", "USD", "EUR"})
    void amountCurrencyAndSettlementDate_arePreserved(Currency currency) {
        LocalPayment payment = buildChatsPayment(currency);
        payment.getTxnAmount().setAmount(new BigDecimal("1008.01"));
        payment.setSettlementDate("2026-11-05");
        BatchRoutingCreditTransferRequest submitted = submitConvertedInstruction(payment);
        var amount = submitted.getTransactions().getFirst().getAmount();
        assertNull(submitted.getTransactions().getFirst().getForeignExchangeDetails());
        assertEquals(new BigDecimal("1008.01"), amount.getInstructedAmount().getAmount());
        assertEquals(new BigDecimal("1008.01"), amount.getSettlementAmount().getAmount());
        assertEquals(currency.toString(), amount.getInstructedAmount().getCurrency());
        assertEquals(currency.toString(), amount.getSettlementAmount().getCurrency());
        assertEquals(LocalDate.of(2026, 11, 5), submitted.getPaymentInformation().getRequestedExecutionDate());
        assertEquals(new BigDecimal("1008.01"), submitted.getGroupHeader().getControlSum());
    }

    @ParameterizedTest
    @EnumSource(value = LocalPayment.ChargesOptionEnum.class, names = {"SHA", "OUR"})
    void chargeBearer_keepsTheOneCharacterIpeContract(LocalPayment.ChargesOptionEnum bearer) {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.setChargesOption(bearer);
        var txn = submitConvertedInstruction(payment).getTransactions().getFirst();
        assertEquals(bearer == LocalPayment.ChargesOptionEnum.SHA ? "S" : "O",
                txn.getCharges().getChargeBearer());
    }

    @Test
    void fullLengthInstruction_preservesCanonicalFieldsWithoutTruncation() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        String name = "N".repeat(140);
        String address1 = "A".repeat(70);
        String address2 = "B".repeat(70);
        String town = "T".repeat(35);
        String reference = "R".repeat(140);
        String endToEndId = "E".repeat(35);
        payment.setTransactionID("C".repeat(35));
        payment.getTxnAmount().setAmount(new BigDecimal("999999999999999.99"));
        payment.getFromAccount().setAccountNumber("015521680295051");
        payment.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId(null);
        payment.getCreditorAgent().getFinancialInstitutionId().setBicfi("ABSYHKH1XXX");
        payment.getCreditor().setCreditorName(name);
        payment.getCreditor().getPostalAddress().setAddressLine1(address1);
        payment.getCreditor().getPostalAddress().setAddressLine2(address2);
        payment.getCreditor().getPostalAddress().setTownName(town);
        payment.getCreditor().getPostalAddress().setCountry("CN");
        payment.getToAccount().setAccountNumber("1".repeat(18));
        payment.getPaymentIdentification().setEndToEndId(endToEndId);
        payment.setRemittanceInformationUnstructured(reference);
        when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID)).thenReturn("D".repeat(140));

        var txn = submitConvertedInstruction(payment).getTransactions().getFirst();
        assertEquals(new BigDecimal("999999999999999.99"), txn.getAmount().getSettlementAmount().getAmount());
        assertEquals(AccountUtils.ext2intAccNo("015521680295051", "C"), txn.getDebtorAccount().getInternalAccountNo());
        assertEquals("ABSYHKH1XXX", txn.getCreditorAgent().getFinancialInstitution().getBicfi());
        assertEquals("D".repeat(140), txn.getDebtor().getName());
        assertEquals(name, txn.getCreditor().getName());
        assertEquals(name, txn.getCreditorAccount().getAccountName());
        assertEquals(address1, txn.getCreditor().getPostalAddress().getAddressLine1());
        assertEquals(address2, txn.getCreditor().getPostalAddress().getAddressLine2());
        assertEquals(town, txn.getCreditor().getPostalAddress().getTownName());
        assertEquals("CN", txn.getCreditor().getPostalAddress().getCountry());
        assertEquals("CN", txn.getCreditorAccount().getCountry());
        assertEquals("1".repeat(18), txn.getCreditorAccount().getIdentification());
        assertEquals(4, txn.getRemittanceInformation().getUnstructured().size());
        assertTrue(txn.getRemittanceInformation().getUnstructured().stream().allMatch(s -> s.length() == 35));
        assertEquals(reference, String.join("", txn.getRemittanceInformation().getUnstructured()));
        // Legacy supplementary fields are unchanged; full-length downstream handling remains unconfirmed.
        assertEquals("6304357992232185", txn.getSupplementaryData().getChatsRemittance().getDepositAccountName2());
        ArgumentCaptor<PaymentPayoutRecord> record = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(record.capture());
        assertEquals(txn.getPaymentIdentification().getEndToEndId(), record.getValue().getPaymentId());
        assertEquals(name, record.getValue().getCreditAccountName());
        assertEquals(address2, record.getValue().getCreditAddressLine2());
    }

    @Test
    void missingCustomerEndToEndId_doesNotReplaceTheUniqueIpeIdentifier() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.setPaymentIdentification(null);
        BatchRoutingCreditTransferRequest submitted = submitConvertedInstruction(payment);
        String ipeId = submitted.getTransactions().getFirst().getPaymentIdentification().getEndToEndId();
        assertEquals(submitted.getGroupHeader().getMessageId(), ipeId);
        assertNotEquals("NOTPROVIDED", ipeId);
        ArgumentCaptor<PaymentPayoutRecord> record = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(record.capture());
        assertEquals(ipeId, record.getValue().getEndToEndId());
        assertEquals(ipeId, record.getValue().getPaymentId());
    }

    @Test
    void chatsEnrichment_preservesTrustedDebtorAddressAndRoutedIdentifiers() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.getFromAccount().setName("PAYER NAME");
        BatchRoutingCreditTransferRequest routed = PaymentCommonRoutingBuilder
                .prepareCommonRoutingRequest(payment, partyAccountMapper);
        var txn = routed.getTransactions().getFirst();
        var address = new com.hkbea.hth.ipe.model.PostalAddress();
        address.setAddressLine1("TRUSTED CUSTOMER ADDRESS");
        address.setCountry("HK");
        txn.getDebtor().setPostalAddress(address);
        String transactionId = txn.getPaymentIdentification().getEndToEndId();
        String batchId = routed.getGroupHeader().getMessageId();
        when(payemtRoutApi.submit(any())).thenReturn(buildSubmitResponse(
                TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));

        Payment response = chatsPaymentService.processPayment(payment, routed);

        verify(payemtRoutApi).submit(same(routed));
        assertSame(address, txn.getDebtor().getPostalAddress());
        assertEquals(transactionId, txn.getPaymentIdentification().getEndToEndId());
        assertEquals(batchId, routed.getGroupHeader().getMessageId());
        assertEquals(transactionId, response.getTxnRef());
        assertEquals("E2E-CHATS", response.getOriginalEndtoendID());
    }

    @Test
    void smartRoutingSelectedChats_appliesChatsBankAndPurpose() {
        LocalPayment payment = buildChatsPayment(Currency.HKD);
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.OTHERS);
        var submitted = submitConvertedInstruction(payment);
        assertEquals(PaymentInformation.PaymentMethodEnum.CHATS, submitted.getPaymentInformation().getPaymentMethod());
        assertEquals("BEASHKHHXXX", submitted.getTransactions().getFirst().getDebtorAgent().getFinancialInstitution().getBicfi());
        assertEquals("/IFT01/", submitted.getTransactions().getFirst().getPurpose().getProprietary());
    }

    private BatchRoutingCreditTransferRequest submitConvertedInstruction(LocalPayment source) {
        // Exercise both public-contract conversion and real routing construction.
        var instruction = PaymentInstructionConvert.toPaymentInstructionRequest(source);
        LocalPayment payment = PaymentInstructionConvert.toLocalPayment(instruction);
        payment.setPartyId(PARTY_ID);
        payment.getFromAccount().setName("PAYER NAME");
        payment.setPaymentDetails(PaymentServiceImpl.splitPaymentDetails(
                payment.getRemittanceInformationUnstructured()));
        when(accountApi.enquireGenericBalance(any())).thenReturn(OverseasPaymentServiceTest
                .buildBalanceResponse("999999999999999.99", payment.getTxnAmount().getCurrency()));
        BatchRoutingCreditTransferRequest routed = PaymentCommonRoutingBuilder
                .prepareCommonRoutingRequest(payment, partyAccountMapper);
        when(payemtRoutApi.route(same(routed)))
                .thenReturn(PaymentRoutingTestSupport.routeResponseForType("CHATS"));
        payment.setPaymentType(PaymentRoutingSubmitter.route(payemtRoutApi, routed));
        String ipeId = routed.getTransactions().getFirst().getPaymentIdentification().getEndToEndId();
        when(payemtRoutApi.submit(same(routed)))
                .thenReturn(buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));

        Payment response = chatsPaymentService.processPayment(payment, routed);

        verify(payemtRoutApi).route(same(routed));
        verify(payemtRoutApi).submit(same(routed));
        assertEquals(ipeId, response.getTxnRef());
        assertEquals(payment.getPaymentIdentification().getEndToEndId(), response.getOriginalEndtoendID());
        assertEquals(Payment.StatusEnum.SUCCESS, response.getStatus());
        return routed;
    }

    // ===================== BCOH2H-2055 posting lifecycle =====================

    @Test
    void pendingRecord_isDurableBeforeIpeSubmission() {
        when(payemtRoutApi.submit(any())).thenReturn(buildSubmitResponse(
                TransferPaymentResultQueryResponse.StatusEnum.PROCESSING));
        chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        var order = org.mockito.Mockito.inOrder(paymentPayoutRecordMapper, payemtRoutApi);
        var inserted = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        order.verify(paymentPayoutRecordMapper).insert(inserted.capture());
        order.verify(payemtRoutApi).submit(any());
        order.verify(paymentPayoutRecordMapper).update(any(PaymentPayoutRecord.class), any());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), inserted.getValue().getStatus());
        assertNull(inserted.getValue().getIpeTerminal());
    }

    @Test
    void zeroRowsInserted_preventsIpeSubmission() {
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class))).thenReturn(0);
        var error = assertThrows(com.hkbea.common.exception.ServiceException.class,
                () -> chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.GENERAL_SERVICE_EXCEPTION, error.getErrCode());
        verify(payemtRoutApi, never()).submit(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postingSuccess_persistsIpeFinalitySeparatelyFromSuccess(boolean terminal) {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS);
        response.getData().setTerminal(terminal);
        response.getData().setStatusReason(terminal ? "SETTLED" : "PENDING_INTERBANK_SETTLEMENT");
        when(payemtRoutApi.submit(any())).thenReturn(response);
        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).update(update.capture(), any());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), update.getValue().getStatus());
        assertEquals(terminal, update.getValue().getIpeTerminal());
        assertEquals(response.getData().getStatusReason(), update.getValue().getIpeStatusReason());
    }

    @Test
    void acceptedSubmission_remainsProcessing() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.PROCESSING);
        response.getData().setTerminal(false);
        when(payemtRoutApi.submit(any())).thenReturn(response);
        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).update(update.capture(), any());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), update.getValue().getStatus());
        assertEquals(false, update.getValue().getIpeTerminal());
    }

    @Test
    void ebkInsufficientBalance_returnsAndPersistsRejected() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.FAILED);
        response.getData().setTerminal(true);
        response.getData().setErrorCode("EBK-INSUFFICIENT-BALANCE");
        response.getData().setErrorMessage("Insufficient balance including bank charges");
        when(payemtRoutApi.submit(any())).thenReturn(response);
        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("EBK-INSUFFICIENT-BALANCE", result.getRejectCode());
        assertEquals("Insufficient balance including bank charges", result.getRejectMessage());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).update(update.capture(), any());
        assertEquals(Payment.StatusEnum.REJECTED.value(), update.getValue().getStatus());
        assertEquals(true, update.getValue().getIpeTerminal());
        assertEquals(result.getRejectMessage(), update.getValue().getRejectMessage());
    }

    @Test
    void rejectionWithoutErrorMessage_fallsBackToStageReason() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.FAILED);
        response.getData().setErrorMessage(" ");
        response.getData().setStatusReason("INVALID_VALUE_DATE");
        when(payemtRoutApi.submit(any())).thenReturn(response);
        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("INVALID_VALUE_DATE", result.getRejectMessage());
    }

    @Test
    void rejectionWithoutAnyReason_usesExistingGenericFallback() {
        when(payemtRoutApi.submit(any())).thenReturn(buildSubmitResponse(
                TransferPaymentResultQueryResponse.StatusEnum.FAILED));
        Payment result = chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        assertEquals("Rejected by payment engine (no detail returned)", result.getRejectMessage());
    }

    @Test
    void submissionUpdate_isConditionalOnUntouchedPendingRow() {
        when(payemtRoutApi.submit(any())).thenReturn(buildSubmitResponse(
                TransferPaymentResultQueryResponse.StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.update(any(PaymentPayoutRecord.class), any())).thenReturn(0);
        chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        @SuppressWarnings("rawtypes")
        var condition = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(paymentPayoutRecordMapper).update(any(PaymentPayoutRecord.class), condition.capture());
        String sql = condition.getValue().getSqlSegment();
        assertTrue(sql.contains("IPE_STATUS IS NULL"));
        assertTrue(sql.contains("IPE_TERMINAL IS NULL"));
        assertTrue(sql.contains("STATUS ="));
        assertTrue(condition.getValue().getParamNameValuePairs().containsValue(Payment.StatusEnum.PROCESSING.value()));
        verify(paymentPayoutRecordMapper, never()).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    void overCharge_isNotAssumedToBeTheTotalBankCharge() {
        var response = buildSubmitResponse(TransferPaymentResultQueryResponse.StatusEnum.SUCCESS);
        response.getData().setOverChargeAmount(new BigDecimal("30.00"));
        response.getData().setOverChargeCcy("HKD");
        when(payemtRoutApi.submit(any())).thenReturn(response);
        chatsPaymentService.processPayment(buildChatsPayment(Currency.HKD), buildRoutingRequest());
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).update(update.capture(), any());
        assertNull(update.getValue().getSvcAmount());
        assertNull(update.getValue().getSvcCur());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildChatsPayment(Currency currency) {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(currency.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);
        fromAccount.setType("CUR");

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
