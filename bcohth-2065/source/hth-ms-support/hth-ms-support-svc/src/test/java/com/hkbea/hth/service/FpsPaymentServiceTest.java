package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ebk.constant.PaymentLimitApiConstants;
import com.hkbea.hth.ebk.model.Fpu01Limits;
import com.hkbea.hth.ebk.model.Fpu01Result;
import com.hkbea.hth.ebk.model.Fpu01ReturnArea;
import com.hkbea.hth.ebk.model.Fpr01Limits;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.FpsBankMapper;
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
import com.hkbea.hth.pe3.api.Pe3Api;
import com.hkbea.hth.service.payment.DebtorAddressService;
import com.hkbea.hth.service.payment.FpsPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.TransferLimitService;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FPS payment validation: every rejection path raises a field-specific
 * {@link ParameterException} before any IPE submission or Scameter check.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FpsPaymentServiceTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";

    @InjectMocks
    private FpsPaymentService fpsPaymentService;

    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private ScameterValidatorUtil scameterValidatorUtil;
    @Mock
    private AccountApi accountApi;
    @Mock
    private FpsBankMapper fpsBankMapper;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private CommonHeader headers;
    @Mock
    private TransferLimitService transferLimitService;
    @Mock
    private DebtorAddressService debtorAddressService;
    @Mock
    private PaymentCommonService paymentCommonService;
    // savePaymentFee dereferences the cache manager when caching IPE fee data.
    @Mock
    private CacheHelperUtils cacheHelperUtils;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        lenient().when(headers.getMessageId()).thenReturn("UT-FPS-MESSAGE-000001");
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
    }

    // ===================== Validation =====================

    @Test
    @DisplayName("null payment is rejected as missing mandatory parameter")
    void nullPayment_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(null, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor agent with both BICFI and member id is rejected")
    void bothBicAndMemberId_throws() {
        LocalPayment request = buildFpsPayment();
        FinancialInstitutionId fid = request.getCreditorAgent().getFinancialInstitutionId();
        fid.setBicfi("BKCHHKHHXXX");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("member id not matching the beneficiary bank code is rejected")
    void memberIdMismatch_throws() {
        LocalPayment request = buildFpsPayment();
        request.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId("004");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.MEMBER_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account is rejected with DEBIT_ACCOUNT_MISSING")
    void missingDebitAccount_throws() {
        LocalPayment request = buildFpsPayment();
        request.setFromAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID")
    void malformedDebitAccountNumber_throws() {
        LocalPayment request = buildFpsPayment();
        request.getFromAccount().setAccountNumber("015123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debit currency is rejected with DEBIT_CURRENCY_MISSING_OR_INVALID")
    void missingDebitCurrency_throws() {
        LocalPayment request = buildFpsPayment();
        request.getFromAccount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account type is rejected with DEBIT_ACCOUNT_TYPE_MISSING")
    void missingDebitAccountType_throws() {
        LocalPayment request = buildFpsPayment();
        request.getFromAccount().setAccountType(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing instructedAmount is rejected with TXN_AMOUNT_MISSING")
    void missingTxnAmount_throws() {
        LocalPayment request = buildFpsPayment();
        request.setTxnAmount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payment currency is rejected with PAYMENT_CURRENCY_MISSING_OR_INVALID")
    void missingPaymentCurrency_throws() {
        LocalPayment request = buildFpsPayment();
        request.getTxnAmount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency outside HKD/CNY is rejected")
    void unsupportedPaymentCurrency_throws() {
        LocalPayment request = buildFpsPayment();
        request.getTxnAmount().setCurrency(Currency.USD);
        request.getToAccount().setCurrency(Currency.USD);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("zero amount is rejected with AMOUNT_MISSING_OR_INVALID")
    void zeroAmount_throws() {
        LocalPayment request = buildFpsPayment();
        request.getTxnAmount().setAmount(BigDecimal.ZERO);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing toAccount is rejected with CREDITOR_ACCOUNT_MISSING")
    void missingToAccount_throws() {
        LocalPayment request = buildFpsPayment();
        request.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("blank beneficiary account number is rejected")
    void blankBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildFpsPayment();
        request.getToAccount().setAccountNumber(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("non-numeric beneficiary account number is rejected")
    void malformedBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildFpsPayment();
        request.getToAccount().setAccountNumber("ABC123");
        request.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId("ABC");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank payee name is rejected with BENEFICIARY_ACCOUNT_NAME_MISSING")
    void blankPayeeName_throws() {
        LocalPayment request = buildFpsPayment();
        request.getCreditor().setCreditorName(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency outside HKD/CNY is rejected")
    void unsupportedDebitCurrency_throws() {
        LocalPayment request = buildFpsPayment();
        request.getFromAccount().setCurrency(Currency.GBP.toString());

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("debit currency differing from payment currency is rejected")
    void debitCurrencyMismatch_throws() {
        LocalPayment request = buildFpsPayment();
        request.getTxnAmount().setCurrency(Currency.CNY);
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @Test
    @DisplayName("payment currency differing from beneficiary currency is rejected")
    void paymentCurrencyMismatch_throws() {
        LocalPayment request = buildFpsPayment();
        request.getToAccount().setCurrency(Currency.CNY);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, exception.getErrCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void submitsWithAddressFromDebitAccountOrOmitsEmptyAddress(boolean hasAddress) {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(populatedFpu01Result());
        String debitAccount = AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C", "HKD");
        var address = hasAddress ? new com.hkbea.hth.ipe.model.PostalAddress()
                .addressLine1("PE3 DEBTOR ADDRESS").townName("CENTRAL").country("HK") : null;
        when(debtorAddressService.lookup(debitAccount)).thenReturn(address);
        var request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        fpsPaymentService.processPayment(buildFpsPayment(), request);

        verify(debtorAddressService).lookup(debitAccount);
        verify(payemtRoutApi).submit(request);
        assertEquals(address, request.getTransactions().getFirst().getDebtor().getPostalAddress());
    }

    @Test
    void pe3TransportFailureOmitsAddressAndSubmits() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(populatedFpu01Result());
        Pe3Api pe3Api = mock(Pe3Api.class);
        when(pe3Api.getCustomerContactInfo(any())).thenThrow(new IllegalStateException("PE3 unavailable"));
        ReflectionTestUtils.setField(fpsPaymentService, "debtorAddressService", new DebtorAddressService(pe3Api, headers));
        var request = buildRoutingRequest();
        when(payemtRoutApi.submit(request)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), request);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertNull(request.getTransactions().getFirst().getDebtor().getPostalAddress());
        verify(pe3Api).getCustomerContactInfo(any());
        verify(payemtRoutApi).submit(request);
    }

    @Test
    @DisplayName("payment reaches IPE without querying the local balance API")
    void balanceApiUnavailable_stillSubmitsToIpe() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any()))
                .thenReturn(populatedFpu01Result());
        when(accountApi.enquireGenericBalance(any()))
                .thenThrow(new IllegalStateException("Balance API must not be called during payment"));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        when(payemtRoutApi.submit(routingRequest)).thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verifyNoInteractions(accountApi);
        verify(payemtRoutApi).submit(routingRequest);
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("IPE insufficient-funds rejection is returned and persisted")
    void ipeInsufficientFunds_returnsRejectedAndPersists() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any()))
                .thenReturn(populatedFpu01Result());
        TransferModelApiResponse response = buildSubmitResponse(StatusEnum.FAILED);
        // Synthetic IPE error fixture; no new HTH error code is introduced.
        response.getData().setErrorCode("TEST-IPE-INSUFFICIENT-FUNDS");
        response.getData().setErrorMessage("Insufficient available balance");
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class))).thenReturn(response);
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), routingRequest);

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
        verify(scameterValidatorUtil).checkFps(any());
        verify(transferLimitService).releaseFasterPaymentLimit(any(), any());
    }

    // ===================== Success flow =====================

    @Test
    @DisplayName("success: pending payout record is inserted before IPE submission and updated with the outcome")
    void success_persistsPendingThenUpdates() {
        LocalPayment request = buildFpsPayment();
        request.setPaymentDetails(new java.util.ArrayList<>(List.of("FPS-DETAIL-1", "FPS-DETAIL-2")));
        when(internalPayeeMapper.selectDomesticPayeeId(anyString(), anyString()))
                .thenReturn("DOMESTIC-PAYEE-1");
        when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(),anyString())).thenReturn("PAYER NAME");
        when(transferLimitService.updateFasterPaymentLimit(any(), any()))
                .thenReturn(populatedFpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

        Payment result = fpsPaymentService.processPayment(request, routingRequest);

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        assertEquals("E2E-FPS", result.getTxnRef());

        InOrder flowOrder = inOrder(transferLimitService, paymentPayoutRecordMapper, payemtRoutApi);
        flowOrder.verify(transferLimitService).updateFasterPaymentLimit(any(), any());
        ArgumentCaptor<PaymentPayoutRecord> insertCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        flowOrder.verify(paymentPayoutRecordMapper).insert(insertCaptor.capture());
        ArgumentCaptor<BatchRoutingCreditTransferRequest> ipeCaptor =
                ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        flowOrder.verify(payemtRoutApi).submit(ipeCaptor.capture());
        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        flowOrder.verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        ArgumentCaptor<Fpr01Limits> releaseCaptor = ArgumentCaptor.forClass(Fpr01Limits.class);
        flowOrder.verify(transferLimitService).releaseFasterPaymentLimit(releaseCaptor.capture(), any());

        // routing request enrichment
        BatchRoutingCreditTransferRequest ipeRequest = ipeCaptor.getValue();
        var txn = ipeRequest.getTransactions().getFirst();
        assertEquals(PaymentConstants.BG3_CUSTOMER_REMARKS_FPS,
                txn.getRemittanceInformation().getCustomerRemarks());
        assertEquals("CXBSNS", txn.getPurpose().getProprietary());
        assertEquals("PAYER NAME", txn.getDebtor().getName());
        assertEquals(com.hkbea.hth.utils.AccountUtils.ext2intAccNo(DEBIT_ACCOUNT, "C", "HKD"),
                txn.getDebtorAccount().getInternalAccountNo());
        assertEquals("6802", txn.getDebtorAccount().getProductCode());
        assertEquals("FPS BENEFICIARY", txn.getCreditor().getName());
        assertEquals(BENEFICIARY_ACCOUNT, txn.getCreditorAccount().getIdentification());
        assertEquals("015", txn.getCreditorAgent().getFinancialInstitution().getMemberId());
        assertEquals("CCB", txn.getSupplementaryData().getPaymentSource());
        assertEquals("FEE", txn.getSupplementaryData().getEnquiryType());
        assertEquals("0101459", txn.getSupplementaryData().getCostCenterCode());
        assertEquals("Y", txn.getSupplementaryData().getAlnovaLimitCheckIndicator());

        // pending payout row (phase 1)
        PaymentPayoutRecord pending = insertCaptor.getValue();
        assertEquals("FPS", pending.getPaymentType());
        assertEquals("UT-FPS-MESSAGE-000001", pending.getMessageId());
        assertEquals("BATCH-FPS-1", pending.getPaymentId());
        assertEquals("UT-FPS-CUST-TXN-000001", pending.getCustomerTransactionId());
        assertEquals("E2E-FPS", pending.getEndToEndId());
        assertEquals("DOMESTIC-PAYEE-1", pending.getPayeeId());
        assertEquals(PaymentConstants.PAYEE_IDFN_ACCTNO, pending.getPayeeIdfn());
        assertEquals(DEBIT_ACCOUNT, pending.getDebitAccountId());
        assertEquals("HKD", pending.getDebitAccountCur());
        assertEquals(BENEFICIARY_ACCOUNT, pending.getCreditAccountId());
        assertEquals("FPS BENEFICIARY", pending.getCreditAccountName());
        assertEquals(new BigDecimal("100.00"), pending.getInstdAmount());
        assertEquals("HKD", pending.getInstdCurrency());
        assertEquals("6802", pending.getProdCode());
        assertEquals("015", pending.getCreditMemberId());
        assertNull(pending.getStatus());
        assertNull(pending.getIpeStatus());
        assertNull(pending.getIpeRefNo());
        assertEquals(PaymentConstants.MOCK_OBJECT_STATUS, pending.getStatusFlag());

        // outcome update (phase 2)
        PaymentPayoutRecord updated = updateCaptor.getValue();
        assertEquals(pending.getId(), updated.getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), updated.getStatus());
        assertEquals(StatusEnum.SUCCESS.value(), updated.getIpeStatus());
        assertEquals("IPE-REF-FPS-001", updated.getIpeRefNo());

        // limit confirm (payment not rejected)
        Fpr01Limits release = releaseCaptor.getValue();
        assertEquals(PaymentLimitApiConstants.FPS_ACT_CODE_CONFIRM, release.getEbzwk00iFpr01ActCode());
        assertEquals("FPS-DETAIL-1FPS-DETAIL-2", release.getEbzwk00iFpr01FpTxRef());
        assertEquals(new BigDecimal("780.00"), release.getEbzwk00iFpr01ReturnArea().getEbzwk00iFpr01AmtHkd());
    }

    @Test
    @DisplayName("unregistered payees fall back to the ad-hoc proxy")
    void success_unregisteredPayee_adhocProxy() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(new Fpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        ArgumentCaptor<Fpu01Limits> deductCaptor = ArgumentCaptor.forClass(Fpu01Limits.class);
        verify(transferLimitService).updateFasterPaymentLimit(deductCaptor.capture(), any());
        assertEquals(PaymentLimitApiConstants.FPS_PROXY_TYPE_ADHOC,
                deductCaptor.getValue().getEbzwk00iFpu01FpProxyIdTy());
        assertEquals(BENEFICIARY_ACCOUNT, deductCaptor.getValue().getEbzwk00iFpu01FpProxyId());
    }

    @Test
    @DisplayName("rejected payments release the reserved limit and persist the rejection")
    void rejectedPayment_releasesLimit() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(new Fpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.FAILED));

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.REJECTED, result.getStatus());
        assertEquals("FPS rejected by IPE", result.getRejectMessage());
        ArgumentCaptor<Fpr01Limits> releaseCaptor = ArgumentCaptor.forClass(Fpr01Limits.class);
        verify(transferLimitService).releaseFasterPaymentLimit(releaseCaptor.capture(), any());
        assertEquals(PaymentLimitApiConstants.FPS_ACT_CODE_RELEASE, releaseCaptor.getValue().getEbzwk00iFpr01ActCode());
        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        assertEquals(Payment.StatusEnum.REJECTED.value(), updateCaptor.getValue().getStatus());
        assertEquals(StatusEnum.FAILED.value(), updateCaptor.getValue().getIpeStatus());
        assertEquals("FPS rejected by IPE", updateCaptor.getValue().getRejectMessage());
    }

    @Test
    @DisplayName("failed limit deduction aborts the payment before any persistence")
    void limitDeductionFails_throws() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.LIMIT_DEDUCTION_FAILED, exception.getErrCode());
        verify(paymentPayoutRecordMapper, never()).insert(any(PaymentPayoutRecord.class));
        verify(payemtRoutApi, never()).submit(any());
        verify(transferLimitService, never()).releaseFasterPaymentLimit(any(), any());
    }

    @Test
    @DisplayName("IPE submission failure returns PROCESSING, persists and confirms the limit")
    void ipeException_returnsProcessingAndPersists() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(new Fpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenThrow(new RuntimeException("IPE timeout"));

        Payment result = fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest());

        assertEquals(Payment.StatusEnum.PROCESSING, result.getStatus());
        verify(paymentPayoutRecordMapper).insert(any(PaymentPayoutRecord.class));
        ArgumentCaptor<PaymentPayoutRecord> updateCaptor =
                ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).updateById(updateCaptor.capture());
        assertEquals(Payment.StatusEnum.PROCESSING.value(), updateCaptor.getValue().getStatus());
        assertNull(updateCaptor.getValue().getIpeStatus());
        ArgumentCaptor<Fpr01Limits> releaseCaptor = ArgumentCaptor.forClass(Fpr01Limits.class);
        verify(transferLimitService).releaseFasterPaymentLimit(releaseCaptor.capture(), any());
        assertEquals(PaymentLimitApiConstants.FPS_ACT_CODE_CONFIRM, releaseCaptor.getValue().getEbzwk00iFpr01ActCode());
    }

    @Test
    @DisplayName("pending payout insert failure is swallowed")
    void pendingInsertException_swallowed() {
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(new Fpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));
        when(paymentPayoutRecordMapper.insert(any(PaymentPayoutRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        Payment result = assertDoesNotThrow(
                () -> fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest()));

        assertEquals(Payment.StatusEnum.SUCCESS, result.getStatus());
        verify(paymentPayoutRecordMapper).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("FPS transaction references longer than 40 chars are truncated")
    void fpsTxRef_truncated() {
        LocalPayment request = buildFpsPayment();
        request.setPaymentDetails(new java.util.ArrayList<>(List.of("X".repeat(50))));
        when(transferLimitService.updateFasterPaymentLimit(any(), any())).thenReturn(new Fpu01Result());
        when(payemtRoutApi.submit(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(buildSubmitResponse(StatusEnum.SUCCESS));

        fpsPaymentService.processPayment(request, buildRoutingRequest());

        ArgumentCaptor<Fpr01Limits> releaseCaptor = ArgumentCaptor.forClass(Fpr01Limits.class);
        verify(transferLimitService).releaseFasterPaymentLimit(releaseCaptor.capture(), any());
        assertEquals("X".repeat(40), releaseCaptor.getValue().getEbzwk00iFpr01FpTxRef());
    }

    // ===================== Debtor account status =====================

    @Test
    @DisplayName("debtor accounts missing from party_account are rejected as not found")
    void debitAccountNotFound_throws() {
        when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("closed debtor accounts are rejected")
    void debitAccountClosed_throws() {
        PartyAccount closed = activePartyAccount();
        closed.setStatus("CLOSED");
        when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(closed);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> fpsPaymentService.processPayment(buildFpsPayment(), buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_MISSING")
    void missingDebitAccountNumber_throws() {
        LocalPayment request = buildFpsPayment();
        request.getFromAccount().setAccountNumber(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("beneficiary account numbers longer than 31 digits are rejected")
    void oversizedBeneficiaryAccountNumber_throws() {
        LocalPayment request = buildFpsPayment();
        request.getToAccount().setAccountNumber("015" + "1".repeat(29));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> fpsPaymentService.processPayment(request, buildRoutingRequest()));
        assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildFpsPayment() {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(Currency.HKD.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setAccountNumber(BENEFICIARY_ACCOUNT);
        toAccount.setCurrency(Currency.HKD);
        toAccount.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(Currency.HKD);

        Creditor creditor = new Creditor();
        creditor.setCreditorName("FPS BENEFICIARY");
        // FpsPaymentService.applyFpsRoutingDetails dereferences the creditor
        // postal address when enriching the routing request.
        PostalAddress postalAddress = new PostalAddress();
        postalAddress.setAddressLine1("Flat 1A 88 Gloucester Road");
        postalAddress.setAddressLine2("Wanchai");
        postalAddress.setTownName("Hong Kong");
        postalAddress.setCountry("HK");
        creditor.setPostalAddress(postalAddress);

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setClearingSystemMemberId("015");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        PaymentIdentification paymentIdentification = new PaymentIdentification();
        paymentIdentification.setEndToEndId("E2E-FPS");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID("UT-FPS-CUST-TXN-000001");
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.FPS);
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
        identification.setEndToEndId("E2E-FPS");
        txn.setPaymentIdentification(identification);
        request.setTransactions(List.of(txn));
        com.hkbea.hth.ipe.model.GroupHeader groupHeader = new com.hkbea.hth.ipe.model.GroupHeader();
        groupHeader.setMessageId("BATCH-FPS-1");
        request.setGroupHeader(groupHeader);
        return request;
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

    /** FPU01 result carrying a populated return area for the release mapping. */
    private Fpu01Result populatedFpu01Result() {
        return new Fpu01Result()
                .ebzwk00oFpu01RelCompCode(1)
                .ebzwk00oFpu01RelAcSeq(2)
                .ebzwk00oFpu01RelAcCcy("HKD")
                .ebzwk00oFpu01RelAcNo(DEBIT_ACCOUNT)
                .ebzwk00oFpu01DbAmtCcy("HKD")
                .ebzwk00oFpu01DbAmt(new BigDecimal("100.00"))
                .ebzwk00oFpu01FpProxyIdTy(PaymentLimitApiConstants.FPS_PROXY_TYPE_REGISTERED)
                .ebzwk00oFpu01FpProxyId(BENEFICIARY_ACCOUNT)
                .ebzwk00oFpu01ReturnArea(new Fpu01ReturnArea()
                        .ebzwk00oFpu01AmtHkd(new BigDecimal("780.00")));
    }

    private TransferModelApiResponse buildSubmitResponse(
            StatusEnum status) {
        TransferPaymentResultQueryResponse result = new TransferPaymentResultQueryResponse();
        result.setStatus(status);
        result.setIpeReferenceId("IPE-REF-FPS-001");
        if (status == StatusEnum.FAILED) {
            result.setErrorMessage("FPS rejected by IPE");
        }

        TransferModelApiResponse response = new TransferModelApiResponse();
        response.setCode(PaymentConstants.API_SUCCESS_CODE);
        response.setData(result);
        return response;
    }
}
