package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.bg3.model.Bgec0760QaechccRequest;
import com.hkbea.hth.bg3.model.Bgec0760Request;
import com.hkbea.hth.bg3.model.Bgec0760Response;
import com.hkbea.hth.bg3.model.Bgec0760ResponseDfhcommarea;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Res;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.DfhcommareaRes7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Eh7e0Output;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.QaechccRes7e0;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.dto.TransferLimit;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.ProductDetails;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BaseResponseHttpEntity;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.NameMatchRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.mapper.ProductDetailsMapper;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.FinancialInstitutionId;
import com.hkbea.hth.model.IntermediaryAgent;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommarea;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechcc;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11Output;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11Answer;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11Data;
import com.hkbea.hth.pe3.model.Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner;
import com.hkbea.hth.pe3.model.Peechi11Response;
import com.hkbea.hth.service.payment.OnUsPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.TransferLimitService;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * On-Us (internal transfer) validation: every rejection path raises a
 * field-specific {@link ParameterException} before any IPE submission.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnUsPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";

    @InjectMocks
    private OnUsPaymentService onUsPaymentService;

    @Mock
    private AccountApi accountApi;
    @Mock
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;
    @Mock
    private Pe3Api pe3Api;
    @Mock
    private ScameterValidatorUtil scameterValidatorUtil;
    @Mock
    private com.hkbea.hth.ipe.api.LocalPaymentApi localPaymentApi;
    @Mock
    private PaymentInternalMapper paymentInternalMapper;
    @Mock
    private CommonHeader headers;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private com.hkbea.hth.service.FxServiceImpl fxApiService;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private TransferLimitService transferLimitService;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private org.springframework.cache.CacheManager cacheManager;
    @Mock
    private ProductDetailsMapper productDetailsMapper;
    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private CacheHelperUtils cacheHelperUtils;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
    }

    // ===================== On-Us specific rules =====================

    @ParameterizedTest
    @ValueSource(ints = {1, 34, 35, 36, 69, 70, 71, 104, 105, 106, 139, 140})
    void creditorNameIsPreservedAcrossDepositNameSegments(int length) {
        stubSuccessFlow();
        LocalPayment input = buildOnUsPayment();
        String name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(6).substring(0, length);
        input.getCreditor().setCreditorName(name);
        BatchRoutingCreditTransferRequest request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = onUsPaymentService.processPayment(input, request);

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
    @DisplayName("null payment is rejected as missing mandatory parameter")
    void nullPayment_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(null, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor agent member id other than 015 is rejected")
    void memberIdMismatch_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId("004");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor agent with both BICFI and member id is rejected")
    void bothBicAndMemberId_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getCreditorAgent().getFinancialInstitutionId().setBicfi("BKCHHKHHXXX");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("On-Us must not carry an intermediary agent BICFI")
    void intermediaryBicfiMustBeBlank_throws() {
        LocalPayment request = buildOnUsPayment();
        FinancialInstitutionId fid = new FinancialInstitutionId();
        fid.setBicfi("SNNRHKH2XXX");
        IntermediaryAgent intermediaryAgent = new IntermediaryAgent();
        intermediaryAgent.setFinancialInstitutionId(fid);
        request.setIntermediaryAgent(intermediaryAgent);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("chargesOption present is rejected with NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING")
    void chargesOptionPresent_throws() {
        LocalPayment request = buildOnUsPayment();
        request.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("category purpose present is rejected with CATEGORY_PURPOSE_MUST_BE_BLANK")
    void categoryPurposePresent_throws() {
        LocalPayment request = buildOnUsPayment();
        com.hkbea.hth.model.CategoryPurpose categoryPurpose = new com.hkbea.hth.model.CategoryPurpose();
        categoryPurpose.setProprietary("CXBSNS-ONUS");
        request.setCategoryPurpose(categoryPurpose);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CATEGORY_PURPOSE_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("purpose present is rejected with PURPOSE_MUST_BE_BLANK")
    void purposePresent_throws() {
        LocalPayment request = buildOnUsPayment();
        com.hkbea.hth.model.Purpose purpose = new com.hkbea.hth.model.Purpose();
        purpose.setProprietary("CXBSNS-ONUS");
        request.setPurpose(purpose);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PURPOSE_MUST_BE_BLANK, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor account not starting with 015 is rejected with CREDITOR_ACCOUNT_NOT_INTERNAL")
    void creditorAccountNotStartingWith015_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber("00405216812024898");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITOR_ACCOUNT_NOT_INTERNAL, exception.getErrCode());
    }

    @Test
    @DisplayName("On-Us must not carry remittance information")
    void remittanceProvided_throws() {
        LocalPayment request = buildOnUsPayment();
        request.setRemittanceInformationUnstructured("invoice 123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING, exception.getErrCode());
    }

    // ===================== Debtor account =====================

    @Test
    @DisplayName("missing debtor account is rejected with DEBIT_ACCOUNT_MISSING")
    void missingDebitAccount_throws() {
        LocalPayment request = buildOnUsPayment();
        request.setFromAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID")
    void malformedDebitAccountNumber_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getFromAccount().setAccountNumber("015123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debit currency is rejected with DEBIT_CURRENCY_MISSING_OR_INVALID")
    void missingDebitCurrency_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getFromAccount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account type is rejected with DEBIT_ACCOUNT_TYPE_MISSING")
    void missingDebitAccountType_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getFromAccount().setAccountType(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING, exception.getErrCode());
    }

    // ===================== Amount =====================

    @Test
    @DisplayName("missing instructedAmount is rejected with TXN_AMOUNT_MISSING")
    void missingTxnAmount_throws() {
        LocalPayment request = buildOnUsPayment();
        request.setTxnAmount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payment currency is rejected with PAYMENT_CURRENCY_MISSING_OR_INVALID")
    void missingPaymentCurrency_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getTxnAmount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("zero amount is rejected with AMOUNT_MISSING_OR_INVALID")
    void zeroAmount_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getTxnAmount().setAmount(BigDecimal.ZERO);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    // ===================== Creditor account =====================

    @Test
    @DisplayName("missing toAccount is rejected with CREDITOR_ACCOUNT_MISSING")
    void missingToAccount_throws() {
        LocalPayment request = buildOnUsPayment();
        request.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("blank beneficiary account number is rejected")
    void blankBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed beneficiary account number is rejected")
    void malformedBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber("015-ABC-123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank payee name is rejected with BENEFICIARY_ACCOUNT_NAME_MISSING")
    void blankPayeeName_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getCreditor().setCreditorName(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_MISSING, exception.getErrCode());
    }

    // ===================== Currency consistency =====================

    @Test
    @DisplayName("debit currency differing from payment currency is rejected")
    void debitCurrencyMismatch_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getTxnAmount().setCurrency(Currency.CNY);
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency differing from beneficiary currency is rejected")
    void paymentCurrencyMismatch_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    // ===================== Account checks =====================

    @Test
    @DisplayName("transferring to the same account is rejected with ACCOUNT_CANNOT_BE_THE_SAME")
    void sameAccount_throws() {
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber(DEBIT_ACCOUNT);
        request.getToAccount().setAccountType(PaymentToAccount.AccountTypeEnum.CUR);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_CANNOT_BE_THE_SAME, exception.getErrCode());
    }

    @Test
    @DisplayName("beneficiary account not found at BGEC0760 is rejected")
    void beneficiaryAccountNotFound_throws() {
        LocalPayment request = buildOnUsPayment();
        when(bgAccountApi.checkAccountExist(any(Bgec0760Request.class))).thenReturn(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NOT_FOUND, exception.getErrCode());
    }

    @Test
    @DisplayName("payment reaches IPE without querying the local balance API")
    void balanceApiUnavailable_stillSubmitsToIpe() {
        stubSuccessFlow();
        when(accountApi.enquireGenericBalance(any()))
                .thenThrow(new IllegalStateException("Balance API must not be called during payment"));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        when(payemtRoutApi.submit(routingRequest)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verifyNoInteractions(accountApi);
        verify(payemtRoutApi).submit(routingRequest);
        verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        verify(paymentInternalMapper).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("IPE insufficient-funds rejection is returned and persisted")
    void ipeInsufficientFunds_returnsRejectedAndPersists() {
        stubSuccessFlow();
        TransferModelApiResponse response = buildSubmitResponse(StatusEnum.FAILED);
        // Synthetic IPE error fixture; no new HTH error code is introduced.
        response.getData().setErrorCode("TEST-IPE-INSUFFICIENT-FUNDS");
        response.getData().setErrorMessage("Insufficient available balance");
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class))).thenReturn(response);
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), routingRequest);

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
        verify(scameterValidatorUtil).checkBankTransfer(any());
        verify(transferLimitService).reverseTransferLimit(any(), any());
    }

    @Test
    @DisplayName("success: pending internal record is inserted before IPE submission and updated with the outcome")
    void success_persistsPendingThenUpdates() {
        when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        when(partyAccountMapper.selectActiveCsaAccountNumbers(anyString())).thenReturn(List.of());
        when(bgAccountApi.checkAccountExist(any(Bgec0760Request.class)))
                .thenReturn(buildAccountExistResponse());
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(OverseasPaymentServiceTest.buildAccountInfoResponse());
        when(localPaymentApi.nameMatch(any(NameMatchRequest.class)))
                .thenReturn(new BaseResponseHttpEntity().result(true));
        when(transferLimitService.updateTransferLimit(any(), any()))
                .thenReturn(TransferLimit.nonDesignatedTransfer(BigDecimal.ONE));
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals("E2E-ONUS", result.getTxnRef());

        InOrder inOrder = inOrder(paymentInternalMapper, payemtRoutApi);
        ArgumentCaptor<PaymentInternal> insertCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        inOrder.verify(paymentInternalMapper).insert(insertCaptor.capture());
        inOrder.verify(payemtRoutApi).submit(any(BatchRoutingCreditTransferRequest.class));
        ArgumentCaptor<PaymentInternal> updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        inOrder.verify(paymentInternalMapper).updateById(updateCaptor.capture());

        PaymentInternal pending = insertCaptor.getValue();
        assertEquals("INTERNAL", pending.getPaymentType());
        assertEquals("E2E-ONUS", pending.getPaymentId());
        assertEquals(PARTY_ID, pending.getPartyId());
        assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
        assertEquals("HKD", pending.getDebitAccountCur());
        assertEquals(new BigDecimal("100.00"), pending.getInstdAmount());
        assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
        assertEquals("HKD", pending.getCreditAccountCur());
        assertEquals("ONUS BENEFICIARY", pending.getCreditAccountName());
        assertEquals("6802", pending.getProductCode());
        assertEquals("015", pending.getCreditMemberId());
        assertNull(pending.getStatus());
        assertNull(pending.getIpeStatus());
        assertNull(pending.getIpeRefNo());

        PaymentInternal updated = updateCaptor.getValue();
        assertEquals(pending.getId(), updated.getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
        assertEquals(StatusEnum.SUCCESS.value(), updated.getIpeStatus());
        assertEquals("IPE-REF-ONUS-001", updated.getIpeRefNo());
    }

    // ===================== Creditor account resolution =====================

    @Test
    @DisplayName("SSA/AIO creditor product codes default the creditor account to SSA/HKD")
    void success_creditorResolution_ssaProduct() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("1025", null));
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountType(null);
        request.getToAccount().setCurrency(null);

        Payment result = onUsPaymentService.processPayment(request, buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(PaymentToAccount.AccountTypeEnum.SSA, request.getToAccount().getAccountType());
        assertEquals(Currency.HKD, request.getToAccount().getCurrency());
    }

    @Test
    @DisplayName("non-SSA creditor product codes resolve the type and default the currency to the payment currency")
    void success_creditorResolution_curProduct_usesTxnCurrency() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("6802", null));
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountType(null);
        request.getToAccount().setCurrency(null);

        Payment result = onUsPaymentService.processPayment(request, buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(PaymentToAccount.AccountTypeEnum.CUR, request.getToAccount().getAccountType());
        assertEquals(Currency.HKD, request.getToAccount().getCurrency());
    }

    @Test
    @DisplayName("a preset creditor account type skips the product lookup and defaults SSA currency to HKD")
    void creditorResolution_typeSet_ssaCurrencyDefault() {
        stubSuccessFlow();
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setCurrency(null);

        Payment result = onUsPaymentService.processPayment(request, buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(Currency.HKD, request.getToAccount().getCurrency());
    }

    @Test
    @DisplayName("creditor accounts the BG9CH7E0 enquiry cannot confirm are rejected as not found")
    void creditorResolution_accountInfoNull_throws() {
        stubSuccessFlow();
        // force the resolution path and make the enquiry return a failure code
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountType(null);
        request.getToAccount().setCurrency(null);
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoFailureResponse());

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("unmapped creditor products with a non-currency product default to SSA")
    void creditorResolution_unmappedProduct_naCurrency_defaultsSsa() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("9999", null));
        when(productDetailsMapper.selectOne(any())).thenReturn(productDetails("NA"));
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountType(null);
        request.getToAccount().setCurrency(null);

        Payment result = onUsPaymentService.processPayment(request, buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(PaymentToAccount.AccountTypeEnum.SSA, request.getToAccount().getAccountType());
    }

    @Test
    @DisplayName("unmapped creditor products without product details are rejected as not found")
    void creditorResolution_unmappedProduct_noDetails_throws() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("9999", null));
        when(productDetailsMapper.selectOne(any())).thenReturn(null);
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountType(null);
        request.getToAccount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor accounts without a number are rejected")
    void missingCreditorAccountNumber_throws() {
        stubSuccessFlow();
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    // ===================== Limit cycle and persistence branches =====================

    @Test
    @DisplayName("failed limit deduction aborts the payment before any persistence")
    void limitDeductionFails_throws() {
        stubSuccessFlow();
        when(transferLimitService.updateTransferLimit(any(), any())).thenReturn(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.LIMIT_DEDUCTION_FAILED, exception.getErrCode());
        verify(paymentInternalMapper, never()).insert(any(PaymentInternal.class));
        verify(payemtRoutApi, never()).submit(any());
    }

    @Test
    @DisplayName("rejected payments reverse the deducted limit and persist the rejection")
    void rejectedPayment_reversesLimit() {
        stubSuccessFlow();
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.FAILED));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        verify(transferLimitService).reverseTransferLimit(any(), any());
        ArgumentCaptor<PaymentInternal> updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        verify(paymentInternalMapper).updateById(updateCaptor.capture());
        assertEquals(Payment.StatusEnum.REJECTED.value(), updateCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("pending internal insert failure is swallowed")
    void pendingInsertException_swallowed() {
        stubSuccessFlow();
        when(paymentInternalMapper.insert(any(PaymentInternal.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = assertDoesNotThrow(
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentInternalMapper).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("IPE submission failure returns PROCESSING and still persists the internal record")
    void ipeException_returnsProcessingAndPersists() {
        stubSuccessFlow();
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenThrow(new RuntimeException("IPE timeout"));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        verify(paymentInternalMapper).insert(any(PaymentInternal.class));
        ArgumentCaptor<PaymentInternal> updateCaptor = ArgumentCaptor.forClass(PaymentInternal.class);
        verify(paymentInternalMapper).updateById(updateCaptor.capture());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), updateCaptor.getValue().getStatus());
        assertNull(updateCaptor.getValue().getIpeStatus());
    }

    @Test
    @DisplayName("own-account transfers deduct the own-account limit category")
    void ownAccountTransfer_usesOwnAccountLimit() {
        stubSuccessFlow();
        when(partyAccountMapper.selectActiveCsaAccountNumbers(anyString())).thenReturn(List.of(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        ArgumentCaptor<TransferLimit> limitCaptor = ArgumentCaptor.forClass(TransferLimit.class);
        verify(transferLimitService).updateTransferLimit(limitCaptor.capture(), any());
        assertEquals(new BigDecimal("100.00"), limitCaptor.getValue().getRelTfrLim());
        assertEquals(BigDecimal.ZERO, limitCaptor.getValue().getNdesLim());
    }

    @Test
    @DisplayName("non-HKD amounts are converted to HKD for the limit deduction")
    void nonHkdAmount_convertedForLimit() {
        stubSuccessFlow();
        when(fxApiService.calculateHkdAmount("USD", new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("780.00"));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(Currency.USD), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        ArgumentCaptor<TransferLimit> limitCaptor = ArgumentCaptor.forClass(TransferLimit.class);
        verify(transferLimitService).updateTransferLimit(limitCaptor.capture(), any());
        assertEquals(new BigDecimal("780.00"), limitCaptor.getValue().getNdesTfrLim());
    }

    // ===================== Payment transfer / beneficiary branches =====================

    @Test
    @DisplayName("a null BG9CH7E0 response blocks the payment transfer")
    void paymentTransfer_responseNull_throws() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class))).thenReturn(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK, exception.getErrCode());
    }

    @Test
    @DisplayName("a failed BG9CH7E0 return code blocks the payment transfer")
    void paymentTransfer_errDataInvalid_throws() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoErrDataResponse("9999"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK, exception.getErrCode());
    }

    @Test
    @DisplayName("BG9CH7E0 failures block the payment transfer")
    void paymentTransfer_apiThrows_throws() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenThrow(new RuntimeException("bg down"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK, exception.getErrCode());
    }

    @Test
    @DisplayName("a failed name match blocks the payment")
    void paymentTransfer_nameMismatch_throws() {
        stubSuccessFlow();
        when(localPaymentApi.nameMatch(any(NameMatchRequest.class)))
                .thenReturn(new BaseResponseHttpEntity().result(false));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_NOMATCHING, exception.getErrCode());
    }

    @Test
    @DisplayName("transfer-blocked accounts of different customers are rejected")
    void paymentTransfer_blocked_differentCustomer_throws() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("6802", "Y"));
        when(pe3Api.getCustomerBasicInfo(any()))
                .thenReturn(peechi11Response("00", "C111", "K1", "HK"),
                        peechi11Response("00", "C222", "K2", "HK"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_TRANSFER_BLOCK, exception.getErrCode());
    }

    @Test
    @DisplayName("transfer-blocked accounts of the same customer pass")
    void paymentTransfer_blocked_sameCustomer_passes() {
        stubSuccessFlow();
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("6802", "Y"));
        when(pe3Api.getCustomerBasicInfo(any()))
                .thenReturn(peechi11Response("00", "C111", "K1", "HK"));

        Payment result = onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
    }

    @Test
    @DisplayName("BGEC0760 failures reject the beneficiary account")
    void beneficiaryCheck_apiThrows_throws() {
        stubSuccessFlow();
        when(bgAccountApi.checkAccountExist(any(Bgec0760Request.class)))
                .thenThrow(new RuntimeException("bg down"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(buildOnUsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NOT_FOUND, exception.getErrCode());
    }

    @Test
    @DisplayName("transfers between identical accounts are rejected")
    void sameAccountTransfer_throws() {
        stubSuccessFlow();
        LocalPayment request = buildOnUsPayment();
        request.getToAccount().setAccountNumber(DEBIT_ACCOUNT);
        request.getToAccount().setAccountType(PaymentToAccount.AccountTypeEnum.CUR);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_CANNOT_BE_THE_SAME, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency differing from the payment currency is rejected")
    void payerPayeeCurrencyMismatch_throws() {
        stubSuccessFlow();
        LocalPayment request = buildOnUsPayment(Currency.HKD);
        request.getTxnAmount().setCurrency(Currency.CNY);
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> onUsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    // ===================== isSameCustomer =====================

    @Test
    @DisplayName("isSameCustomer: missing customer info on both sides returns true")
    void isSameCustomer_bothNull_true() {
        assertTrue(onUsPaymentService.isSameCustomer(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));
    }

    @Test
    @DisplayName("isSameCustomer: identical HKID triples return true")
    void isSameCustomer_sameCustomer_true() {
        when(pe3Api.getCustomerBasicInfo(any()))
                .thenReturn(peechi11Response("00", "C111", "K1", "HK"));

        assertTrue(onUsPaymentService.isSameCustomer(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));
    }

    @Test
    @DisplayName("isSameCustomer: differing HKID triples return false")
    void isSameCustomer_differentCustomer_false() {
        when(pe3Api.getCustomerBasicInfo(any()))
                .thenReturn(peechi11Response("00", "C111", "K1", "HK"),
                        peechi11Response("00", "C222", "K2", "HK"));

        assertFalse(onUsPaymentService.isSameCustomer(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));
    }

    @Test
    @DisplayName("isSameCustomer: customer info on one side only returns false")
    void isSameCustomer_oneNull_false() {
        when(pe3Api.getCustomerBasicInfo(any()))
                .thenReturn(peechi11Response("00", "C111", "K1", "HK"), null);

        assertFalse(onUsPaymentService.isSameCustomer(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));
    }

    @Test
    @DisplayName("isSameCustomer: PE9CHI11 failures on both sides return true")
    void isSameCustomer_apiThrows_true() {
        when(pe3Api.getCustomerBasicInfo(any())).thenThrow(new RuntimeException("pe3 down"));

        assertTrue(onUsPaymentService.isSameCustomer(
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C"),
                com.hkbea.hth.utils.AccountUtils.ext2intAccNo(BENEFICIARY_ACCOUNT, "S")));
    }

    // ===================== Fixtures =====================

    /** Shared happy-path stubs for the On-Us success flow. */
    private void stubSuccessFlow() {
        when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        when(partyAccountMapper.selectActiveCsaAccountNumbers(anyString())).thenReturn(List.of());
        when(bgAccountApi.checkAccountExist(any(Bgec0760Request.class)))
                .thenReturn(buildAccountExistResponse());
        when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(onUsAccountInfoResponse("6802", null));
        when(localPaymentApi.nameMatch(any(NameMatchRequest.class)))
                .thenReturn(new BaseResponseHttpEntity().result(true));
        when(transferLimitService.updateTransferLimit(any(), any()))
                .thenReturn(TransferLimit.nonDesignatedTransfer(BigDecimal.ONE));
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
    }

    /** BG9CH7E0 response with a given product code and payment-transfer flag. */
    private Bgech7e0Response onUsAccountInfoResponse(String productCode, String paymentTransfer) {
        Eh7e0Output output = new Eh7e0Output();
        output.setEh7e0CodReturnO(PaymentConstants.SUCC_CODE_00);
        output.setEh7e0CodProductO(productCode);
        output.setEh7e0PaymentTransfer(paymentTransfer);
        return wrapAccountInfo(output, PaymentConstants.BG_RETURN_CODE_0000);
    }

    /** BG9CH7E0 response whose return code marks the enquiry as failed. */
    private Bgech7e0Response onUsAccountInfoFailureResponse() {
        Eh7e0Output output = new Eh7e0Output();
        output.setEh7e0CodReturnO("99");
        return wrapAccountInfo(output, PaymentConstants.BG_RETURN_CODE_0000);
    }

    /** BG9CH7E0 response with a failing qaechcc error data. */
    private Bgech7e0Response onUsAccountInfoErrDataResponse(String errData) {
        Eh7e0Output output = new Eh7e0Output();
        output.setEh7e0CodReturnO(PaymentConstants.SUCC_CODE_00);
        return wrapAccountInfo(output, errData);
    }

    private Bgech7e0Response wrapAccountInfo(Eh7e0Output output, String errData) {
        Bgech7e0Res bgech7e0 = new Bgech7e0Res();
        bgech7e0.setEh7e0Output(output);
        QaechccRes7e0 qaechcc = new QaechccRes7e0();
        qaechcc.setQaechccErrData(errData);
        qaechcc.setBgech7e0(bgech7e0);
        DfhcommareaRes7e0 dfhcommarea = new DfhcommareaRes7e0();
        dfhcommarea.setQaechcc(qaechcc);
        Bgech7e0Response response = new Bgech7e0Response();
        response.setDfhcommarea(dfhcommarea);
        return response;
    }

    private ProductDetails productDetails(String productCurrency) {
        ProductDetails product = new ProductDetails();
        product.setProductCode("9999");
        product.setProductCurrency(productCurrency);
        return product;
    }

    /** PE9CHI11 response carrying one customer record (HKID triple). */
    private Peechi11Response peechi11Response(String codReturn, String codId, String keyId, String issCtry) {
        Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner customer =
                new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11DataEhi11DtaBasicOInner()
                        .ehi11CodIdO(codId)
                        .ehi11KeyIdO(keyId)
                        .ehi11CodIssCtryO(issCtry);
        return new Peechi11Response()
                .dfhcommarea(new Peechi11RequestDfhcommarea()
                        .qaechcc(new Peechi11RequestDfhcommareaQaechcc()
                                .peechi11(new Peechi11RequestDfhcommareaQaechccPeechi11()
                                        .ehi11Output(new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11Output()
                                                .ehi11Answer(new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11Answer()
                                                        .ehi11CodReturn(codReturn))
                                                .ehi11Data(new Peechi11RequestDfhcommareaQaechccPeechi11Ehi11OutputEhi11Data()
                                                        .ehi11DtaBasicO(List.of(customer)))))));
    }

    private LocalPayment buildOnUsPayment() {
        return buildOnUsPayment(Currency.HKD);
    }

    private LocalPayment buildOnUsPayment(Currency currency) {
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
        creditor.setCreditorName("ONUS BENEFICIARY");

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setClearingSystemMemberId("015");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-ONUS");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID("UT-ONUS-CUST-TXN-000001");
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.ONUS);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        payment.setPaymentIdentification(paymentIdentification);
        payment.setSettlementDate(LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE));
        return payment;
    }

    private BatchRoutingCreditTransferRequest buildRoutingRequest() {
        BatchRoutingCreditTransferRequest request = new BatchRoutingCreditTransferRequest();
        request.setPaymentInformation(new com.hkbea.hth.ipe.model.PaymentInformation());
        com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest txn =
                new com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest();
        com.hkbea.hth.ipe.model.PaymentIdentification identification =
                new com.hkbea.hth.ipe.model.PaymentIdentification();
        identification.setEndToEndId("E2E-ONUS");
        txn.setPaymentIdentification(identification);
        request.setTransactions(List.of(txn));
        com.hkbea.hth.ipe.model.GroupHeader groupHeader = new com.hkbea.hth.ipe.model.GroupHeader();
        groupHeader.setMessageId("BATCH-ONUS-1");
        request.setGroupHeader(groupHeader);
        return request;
    }

    /** BGEC0760 response confirming the beneficiary account exists. */
    private Bgec0760Response buildAccountExistResponse() {
        Bgec0760QaechccRequest qaechcc = new Bgec0760QaechccRequest();
        qaechcc.setQaechccErrData("0000");
        Bgec0760ResponseDfhcommarea dfhcommarea = new Bgec0760ResponseDfhcommarea();
        dfhcommarea.setQaechcc(qaechcc);
        Bgec0760Response response = new Bgec0760Response();
        response.setDfhcommarea(dfhcommarea);
        return response;
    }

    private TransferModelApiResponse buildSubmitResponse(
            StatusEnum status) {
        TransferPaymentResultQueryResponse data = new TransferPaymentResultQueryResponse();
        data.setStatus(status);
        data.setIpeReferenceId("IPE-REF-ONUS-001");

        TransferModelApiResponse response = new TransferModelApiResponse();
        response.setCode(PaymentConstants.API_SUCCESS_CODE);
        response.setData(data);
        return response;
    }
}

