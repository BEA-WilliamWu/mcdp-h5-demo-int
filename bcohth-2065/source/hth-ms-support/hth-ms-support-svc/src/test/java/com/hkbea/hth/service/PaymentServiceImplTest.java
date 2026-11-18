package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.bg3.api.AccountApi;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Res;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.DfhcommareaRes7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Eh7e0Output;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.QaechccRes7e0;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ebk.constant.PaymentLimitApiConstants;
import com.hkbea.hth.ebk.model.Fpu01RespOArea;
import com.hkbea.hth.ebk.model.ResponseHeader;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.entity.ProductDetails;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.*;
import com.hkbea.hth.mapper.*;
import com.hkbea.hth.model.*;
import com.hkbea.hth.model.CreditorAccount;
import com.hkbea.hth.model.InstructedAmount;
import com.hkbea.hth.model.PaymentIdentification;
import com.hkbea.hth.service.payment.*;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.FineGrainedEntitlementUtil;
import com.hkbea.hth.utils.PartyResolver;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Payment entry validation (mandatory/common fields, duplicate transaction ids,
 * payment detail splitting) and the smart-routing dispatch to the five
 * payment-type services.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final String PARTY_ID = "01552168032777";
    private static final String CLOSE_ID = "CLOSE456";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String BENEFICIARY_ACCOUNT = "01505216812024898";

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private LocalPaymentApi localPaymentApi;
    @Mock
    private PaymentInternalMapper paymentInternalMapper;
    @Mock
    private PartyResolver partyResolver;
    @Mock
    private HthNwPaymentPayoutMapper payoutMapper;
    @Mock
    private FpsPaymentService fpsPaymentService;
    @Mock
    private ChatsPaymentService chatsPaymentService;
    @Mock
    private OnUsPaymentService onUsPaymentService;
    @Mock
    private ProductDetailsMapper productDetailsMapper;
    @Mock
    private TtPaymentService ttPaymentService;
    @Mock
    private DrPaymentService drPaymentService;
    @Mock
    private HthPaymentPayoutMapper hthPaymentPayoutMapper;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private FineGrainedEntitlementUtil fineGrainedEntitlementUtil;
    @Mock
    private AccountApi accountApi;
    @Mock
    private TransferLimitService transferLimitService;
    @Mock
    private CacheHelperUtils cacheHelperUtils;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        lenient().when(partyResolver.resolvePartyId()).thenReturn(PARTY_ID);
        lenient().when(partyResolver.resolveCloseId()).thenReturn(CLOSE_ID);
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(any(), any())).thenReturn("PAYER NAME");
        lenient().when(paymentInternalMapper.selectCount(any())).thenReturn(0L);
        lenient().when(hthPaymentPayoutMapper.selectCount(any())).thenReturn(0L);
        lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                any(), any(), any()))
                .thenReturn(List.of(accessAccount("6802")));
        lenient().when(productDetailsMapper.selectOne(any())).thenReturn(productDetails());
        lenient().when(uriInfo.getPath()).thenReturn("/payments");
        lenient().when(fineGrainedEntitlementUtil.isAuthorizedAccount(anyString(), anyString())).thenReturn(true);
        // BG9CH7E0 account lookup used by validateAccountId: return code 00, no account hold
        lenient().when(accountApi.queryAccountInfo(any())).thenReturn(accountInfoResponse());
        // Mock transfer limit service to return a valid Fpu01RespOArea with success response code
        lenient().when(transferLimitService.selectFasterPaymentLimit(any(), any()))
                .thenReturn(createFpu01RespOArea());
        // Mock cacheHelperUtils.saveMtbInfo to prevent NPE during routing
        lenient().doNothing().when(cacheHelperUtils).saveMtbInfo(any());
    }

    // ===================== Mandatory fields =====================

    @Test
    @DisplayName("null payment is rejected as missing mandatory parameter")
    void nullPayment_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(null));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("blank transactionID is rejected")
    void blankTransactionId_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setTransactionID(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account is rejected")
    void missingDebtorAccount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setFromAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing amount is rejected")
    void missingAmount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setTxnAmount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payment currency is rejected")
    void missingPaymentCurrency_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getTxnAmount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_CURRENCY_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing creditor name is rejected")
    void missingCreditorName_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setCreditor(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing creditor account is rejected")
    void missingCreditorAccount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.CREDITORACCOUNT_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("blank settlement date is rejected")
    void blankSettlementDate_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setSettlementDate(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID, exception.getErrCode());
    }

    // ===================== Common validation =====================

    @Test
    @DisplayName("settlement date in the future is rejected")
    void futureSettlementDate_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateExecutionDate(
                        LocalDate.now().plusDays(1).toString()));
        assertEquals(HTHSupportErrorMapping.EXECUTION_DATE_NOT_CURRENT_DATE, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed settlement date is rejected")
    void malformedSettlementDate_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateExecutionDate("2026/10/31"));
        assertEquals(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("duplicate transactionID within two years is rejected")
    void duplicateTransactionId_throws() {
        when(paymentInternalMapper.selectCount(any())).thenReturn(1L);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateTransactionId(buildValidPayment()));
        assertEquals(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_DUPLICATE, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed debtor account id is rejected by the common account check")
    void malformedDebtorAccountId_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setId("015");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("remittance information is split into 35-char segments")
    void splitPaymentDetails_segments() {
        List<String> segments = PaymentServiceImpl.splitPaymentDetails("A".repeat(80));

        assertEquals(3, segments.size());
        assertEquals(35, segments.get(0).length());
        assertEquals(35, segments.get(1).length());
        assertEquals(10, segments.get(2).length());
    }

    @Test
    @DisplayName("blank remittance information yields an empty list")
    void splitPaymentDetails_blank() {
        assertTrue(PaymentServiceImpl.splitPaymentDetails(" ").isEmpty());
    }

    @Test
    @DisplayName("Chinese remittance information is rejected")
    void splitPaymentDetails_chinese_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> PaymentServiceImpl.splitPaymentDetails("深圳路88号"));
        assertEquals(HTHSupportErrorMapping.PAYMENTDEATAIL_CHINESE_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("negative amounts are rejected by the common amount check")
    void negativeAmount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getTxnAmount().setAmount(new BigDecimal("-1.00"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("transactionID longer than 35 chars is rejected")
    void oversizedTransactionId_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setTransactionID("X".repeat(36));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("endToEndId longer than 35 chars is rejected")
    void oversizedEndToEndId_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setPaymentIdentification(new PaymentIdentification());
        payment.getPaymentIdentification().setEndToEndId("X".repeat(36));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.END_TO_END_ID_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor account ids longer than 34 chars are rejected")
    void oversizedCreditorAccountId_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getToAccount().setId("A".repeat(35));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.CREDITORACCOUNT_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("a missing creditor account is rejected by the common account check")
    void missingCreditorAccountInCommonCheck_throws() {
        LocalPayment payment = buildValidPayment();
        payment.setToAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.CREDITORACCOUNT_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payment currency is rejected by the mandatory field check")
    void commonMissingCurrency_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getTxnAmount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateMandatoryFields(payment));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_CURRENCY_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("preset debtor account currencies are kept")
    void presetDebtorCurrency_kept() {
        ProductDetails product = productDetails();
        product.setProductCurrency("USD");
        when(productDetailsMapper.selectOne(any())).thenReturn(product);

        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        payment.getFromAccount().setCurrency("USD");

        paymentService.validateCommon(payment);

        assertEquals("USD", payment.getFromAccount().getCurrency());
    }

    @Test
    @DisplayName("blank settlement dates are rejected by the execution-date check")
    void blankExecutionDate_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateExecutionDate(" "));
        assertEquals(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("unparseable settlement dates are rejected")
    void unparseableExecutionDate_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateExecutionDate("2026-99-99"));
        assertEquals(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID, exception.getErrCode());
    }

    // ===================== Debtor account resolution =====================

    @Test
    @DisplayName("SSA/AIO product codes default the debtor account to SAV/HKD")
    void ssaProductCode_defaultsToSsaHkd() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts("6805");

        paymentService.validateCommon(payment);

        assertEquals(BeaAccount.AccountTypeEnum.SAV, payment.getFromAccount().getAccountType());
        assertEquals("HKD", payment.getFromAccount().getCurrency());
    }

    @Test
    @DisplayName("non-SSA accounts default the currency to the payment currency")
    void curProductCode_defaultsCurrencyFromPayment() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts("6802");

        paymentService.validateCommon(payment);

        assertEquals(BeaAccount.AccountTypeEnum.CUR, payment.getFromAccount().getAccountType());
        assertEquals("HKD", payment.getFromAccount().getCurrency());
    }

    @Test
    @DisplayName("multiple product codes resolve the account type from the first code")
    void multipleProductCodes_useFirstCode() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        when(hthUserAccessAccountMapper.selectFromAccountInfo(any(), any(), any()))
                .thenReturn(List.of(accessAccount("6802"), accessAccount("1010")));

        paymentService.validateCommon(payment);

        assertEquals(BeaAccount.AccountTypeEnum.CUR, payment.getFromAccount().getAccountType());
    }

    @Test
    @DisplayName("collection accounts are rejected")
    void collectionAccount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts("4041");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.COL_REJECT_ACCOUNT_TYPE, exception.getErrCode());
    }

    @Test
    @DisplayName("time-deposit accounts are rejected")
    void timeDepositAccount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts("3030");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.TD_REJECT_ACCOUNT_TYPE, exception.getErrCode());
    }

    @Test
    @DisplayName("debtor accounts without access records fail with ParameterException")
    void unknownDebtorAccount_throws() {
        LocalPayment payment = buildValidPayment();
        when(hthUserAccessAccountMapper.selectFromAccountInfo(any(), any(), any()))
                .thenReturn(List.of());

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("debtor access records without a usable product code are rejected as not found")
    void noProductCodeDebtorAccount_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("unmapped product codes are rejected with a domain error instead of NPE")
    void unmappedProductCode_throws() {
        LocalPayment payment = buildValidPayment();
        payment.getFromAccount().setType(null);
        stubAccessAccounts("9999");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.validateCommon(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING, exception.getErrCode());
    }

    // ===================== Transaction enquiry =====================

    @Test
    @DisplayName("blank txnRef is rejected as missing mandatory parameter")
    void enquire_blankTxnRef_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> paymentService.enquireTransactionRecord(" "));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("internal records in a final state are returned without an IPE refresh")
    void enquire_internalFinalStatus_returnedWithoutRefresh() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("SUCCESS"));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals("PAY-INTERNAL-1", detail.getReferenceNumber());
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, detail.getStatus());
        assertEquals(PaymentTransaction.PaymentTypeEnum.INTERNAL, detail.getPaymentType());
        assertEquals(new BigDecimal("100.00"), detail.getTransferAmount());
        assertEquals("HKD", detail.getTransferCurrency());
        assertEquals(BENEFICIARY_ACCOUNT, detail.getBeneficiaryAccountNumber());
        assertEquals("BENEFICIARY", detail.getBeneficiaryAccountName());
        assertEquals(DEBIT_ACCOUNT, detail.getRemitterAccountNumber());
        assertEquals("HKD", detail.getRemitterAccountCurrency());
        assertEquals("CUR", detail.getRemitterAccountType());
        assertEquals("Current Account", detail.getRemitterProductName());
        assertNotNull(detail.getInitiatedDate());
        assertNotNull(detail.getAsOf());
        assertEquals("E2E-1", detail.getExternalReferenceId());
        assertEquals("payment remark", detail.getNote());
        verify(localPaymentApi, never()).queryPaymentResults(any());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("pending internal records are refreshed from IPE and updated when changed")
    void enquire_internalPending_refreshedFromIpe() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("PROCESSING"));
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(ipeResult(
                PaymentResultQueryResponse.StatusEnum.SUCCESS, null, new Date()));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, detail.getStatus());
        ArgumentCaptor<PaymentInternal> update = ArgumentCaptor.forClass(PaymentInternal.class);
        verify(paymentInternalMapper).updateById(update.capture());
        assertEquals("INTERNAL-ID-1", update.getValue().getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), update.getValue().getStatus());
        assertEquals("SUCCESS", update.getValue().getIpeStatus());
    }

    @Test
    @DisplayName("pending internal records rejected by IPE carry the reject message and status reason")
    void enquire_internalPendingFailed_rejectMessageUpdated() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("PROCESSING"));
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(ipeResult(
                PaymentResultQueryResponse.StatusEnum.FAILED, "insufficient funds", null));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.REJECTED, detail.getStatus());
        assertEquals("insufficient funds", detail.getRejectMessage());
        ArgumentCaptor<PaymentInternal> update = ArgumentCaptor.forClass(PaymentInternal.class);
        verify(paymentInternalMapper).updateById(update.capture());
        assertEquals(Payment.StatusEnum.REJECTED.value(), update.getValue().getStatus());
        assertEquals("insufficient funds", update.getValue().getRejectMessage());
    }

    @Test
    @DisplayName("inconclusive IPE enquiries keep the stored pending state")
    void enquire_internalPending_ipeInconclusive_keepsStored() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("PROCESSING"));
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(null);

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, detail.getStatus());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("payout records (FPS/CHATS/TT/DR) are returned after the internal lookup misses")
    void enquire_payoutFound() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(null);
        when(payoutMapper.selectOne(any())).thenReturn(payoutRecord("SUCCESS"));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-PAYOUT-1");

        assertEquals("PAY-PAYOUT-1", detail.getReferenceNumber());
        assertEquals(PaymentTransaction.PaymentTypeEnum.FPS, detail.getPaymentType());
        assertEquals("HKD", detail.getTransferCurrency());
        assertEquals("HKD", detail.getRemitterAccountCurrency());
        verify(payoutMapper, never()).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    @DisplayName("pending payout records are refreshed from IPE and updated when changed")
    void enquire_payoutPending_refreshedFromIpe() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(null);
        when(payoutMapper.selectOne(any())).thenReturn(payoutRecord("PROCESSING"));
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(ipeResult(
                PaymentResultQueryResponse.StatusEnum.SUCCESS, null, null));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-PAYOUT-1");

        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, detail.getStatus());
        ArgumentCaptor<PaymentPayoutRecord> update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(payoutMapper).updateById(update.capture());
        assertEquals("PAYOUT-ID-1", update.getValue().getId());
        assertEquals(Payment.StatusEnum.SUCCESS.value(), update.getValue().getStatus());
    }

    @Test
    @DisplayName("transactions missing from both tables raise PAYMENT_NOT_FOUND")
    void enquire_notFound_throws() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(null);
        when(payoutMapper.selectOne(any())).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> paymentService.enquireTransactionRecord("PAY-MISSING"));
        assertEquals(HTHSupportErrorMapping.PAYMENT_NOT_FOUND_EXCEPTION, exception.getErrCode());
    }

    @Test
    @DisplayName("unknown stored payment types map to null instead of failing the enquiry")
    void enquire_unknownPaymentType_mapsToNull() {
        PaymentInternal stored = internalRecord("SUCCESS");
        stored.setPaymentType("LEGACY");
        when(paymentInternalMapper.selectOne(any())).thenReturn(stored);

        assertNull(paymentService.enquireTransactionRecord("PAY-INTERNAL-1").getPaymentType());
    }

    @Test
    @DisplayName("unknown stored statuses map to null")
    void enquire_unknownStatus_mapsToNull() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("WEIRD"));

        assertNull(paymentService.enquireTransactionRecord("PAY-INTERNAL-1").getStatus());
    }

    @Test
    @DisplayName("missing product codes fall back to an empty remitter product name")
    void enquire_missingProductCode_emptyProductName() {
        PaymentInternal stored = internalRecord("SUCCESS");
        stored.setProductCode(null);
        when(paymentInternalMapper.selectOne(any())).thenReturn(stored);

        assertEquals("", paymentService.enquireTransactionRecord("PAY-INTERNAL-1").getRemitterProductName());
    }

    @Test
    @DisplayName("IPE enquiries returning the same pending state skip the DB update")
    void enquire_internalPendingSameStatus_noUpdate() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("Processing"));
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(ipeResult(
                PaymentResultQueryResponse.StatusEnum.PROCESSING, null, null));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, detail.getStatus());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("IPE enquiry failures keep the stored pending state")
    void enquire_internalPending_ipeThrows_keepsStored() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("PROCESSING"));
        when(localPaymentApi.queryPaymentResults(any())).thenThrow(new IllegalStateException("ipe down"));

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, detail.getStatus());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("non-success IPE enquiry codes keep the stored pending state")
    void enquire_internalPending_ipeErrorCode_keepsStored() {
        when(paymentInternalMapper.selectOne(any())).thenReturn(internalRecord("PROCESSING"));
        PaymentResultQueryApiResponse response = new PaymentResultQueryApiResponse();
        response.setCode("500");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, detail.getStatus());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
    }

    @Test
    @DisplayName("missing timestamps map to null enquiry dates")
    void enquire_missingDates_mapToNull() {
        PaymentInternal stored = internalRecord("SUCCESS");
        stored.setPaymentDate(null);
        stored.setLastUpdatedDate(null);
        when(paymentInternalMapper.selectOne(any())).thenReturn(stored);

        PaymentTransaction detail = paymentService.enquireTransactionRecord("PAY-INTERNAL-1");

        assertNull(detail.getExecutionDate());
        assertNull(detail.getAsOf());
    }

    // ===================== Routing dispatch =====================

    @Test
    void explicitChatsPassesCustomerIdentifierToRouteAndSelectedService() {
        for (String customerId : Arrays.asList("test123456end", "12345678901234567890123456789012345")) {
            reset(chatsPaymentService, payemtRoutApi);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType("CHATS");
            request.setPaymentIdentification(new PaymentIdentification().endToEndId(customerId));
            request.getCreditor().setCreditorName("N".repeat(35));
            Payment expected = new Payment().status(Payment.StatusEnum.PROCESSING);
            when(chatsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(payemtRoutApi.route(any())).thenAnswer(invocation -> {
                BatchRoutingCreditTransferRequest routing = invocation.getArgument(0);
                assertEquals(customerId, routing.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
                assertNotEquals(customerId, routing.getGroupHeader().getMessageId());
                assertTrue(routing.getGroupHeader().getMessageId().startsWith("HTH"));
                return PaymentRoutingTestSupport.routeResponseForType("CHATS");
            });

            assertSame(expected, paymentService.processLocalPayment(request));

            ArgumentCaptor<BatchRoutingCreditTransferRequest> routed = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
            verify(chatsPaymentService).processPayment(any(), routed.capture());
            verify(payemtRoutApi).route(same(routed.getValue()));
            assertEquals(customerId, routed.getValue().getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            assertEquals("N".repeat(35), routed.getValue().getTransactions().getFirst().getCreditor().getName());
        }
    }

    @Test
    void automaticRoutingToChatsReplacesInternalIdentifierWithCustomerValue() {
        PaymentInstructionRequest request = buildInstructionRequest();
        request.setPaymentType(LocalPayment.PaymentTypeEnum.OTHERS.value());
        request.setPaymentIdentification(new PaymentIdentification().endToEndId("CUSTOMER-ID"));
        Payment expected = new Payment().status(Payment.StatusEnum.PROCESSING);
        when(chatsPaymentService.processPayment(any(), any())).thenReturn(expected);
        when(payemtRoutApi.route(any())).thenAnswer(invocation -> {
            BatchRoutingCreditTransferRequest routing = invocation.getArgument(0);
            assertEquals(routing.getGroupHeader().getMessageId(),
                    routing.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            return PaymentRoutingTestSupport.routeResponseForType("CHATS");
        });

        assertSame(expected, paymentService.processLocalPayment(request));

        ArgumentCaptor<BatchRoutingCreditTransferRequest> routed = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(chatsPaymentService).processPayment(any(), routed.capture());
        assertEquals("CUSTOMER-ID", routed.getValue().getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
        assertNotEquals("CUSTOMER-ID", routed.getValue().getGroupHeader().getMessageId());
    }

    @Test
    void chatsReroutedToAnotherRailRestoresThatRailsInternalIdentifier() {
        for (String selectedType : Arrays.asList("FPS", "ONUS")) {
            reset(fpsPaymentService, onUsPaymentService, payemtRoutApi, chatsPaymentService);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType("CHATS");
            request.setPaymentIdentification(new PaymentIdentification().endToEndId("CUSTOMER-ID"));
            Payment expected = new Payment().status(Payment.StatusEnum.PROCESSING);
            when(fpsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(onUsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(payemtRoutApi.route(any())).thenAnswer(invocation -> {
                BatchRoutingCreditTransferRequest routing = invocation.getArgument(0);
                assertEquals("CUSTOMER-ID", routing.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
                return PaymentRoutingTestSupport.routeResponseForType(selectedType);
            });

            assertSame(expected, paymentService.processLocalPayment(request));

            ArgumentCaptor<BatchRoutingCreditTransferRequest> routed = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
            if ("FPS".equals(selectedType)) {
                verify(fpsPaymentService).processPayment(any(), routed.capture());
            } else {
                verify(onUsPaymentService).processPayment(any(), routed.capture());
            }
            assertEquals(routed.getValue().getGroupHeader().getMessageId(),
                    routed.getValue().getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            assertNotEquals("CUSTOMER-ID", routed.getValue().getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
            verify(chatsPaymentService, never()).processPayment(any(), any());
        }
    }

    @Test
    void explicitChatsOverlengthCreditorNameStopsBeforeRouting() {
        for (int length : new int[]{141}) {
            reset(payemtRoutApi, chatsPaymentService);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType("CHATS");
            request.getCreditor().setCreditorName("N".repeat(length));

            ParameterException exception = assertThrows(ParameterException.class,
                    () -> paymentService.processLocalPayment(request));

            assertEquals(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NAME_INVALID, exception.getErrCode());
            verifyNoInteractions(payemtRoutApi, chatsPaymentService);
        }
    }

    @Test
    void explicitChatsCreditorNamesUpTo140ReachIpeRouting() {
        for (int length : new int[]{36, 140}) {
            reset(payemtRoutApi, chatsPaymentService);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType("CHATS");
            request.getCreditor().setCreditorName("N".repeat(length));
            Payment expected = new Payment().status(Payment.StatusEnum.PROCESSING);
            when(chatsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(payemtRoutApi.route(any())).thenReturn(PaymentRoutingTestSupport.routeResponseForType("CHATS"));

            assertSame(expected, paymentService.processLocalPayment(request));

            ArgumentCaptor<BatchRoutingCreditTransferRequest> routed =
                    ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
            verify(chatsPaymentService).processPayment(any(), routed.capture());
            assertEquals("N".repeat(length), routed.getValue().getTransactions().getFirst().getCreditor().getName());
            assertEquals("N".repeat(length), routed.getValue().getTransactions().getFirst().getCreditorAccount().getAccountName());
        }
    }

    @Test
    void otherRequestedTypesKeepTheirExistingCreditorNameLimitAtEntry() {
        for (String requestedType : Arrays.asList("ONUS", "FPS", "TT", "DR", "OTHERS")) {
            reset(onUsPaymentService, fpsPaymentService, ttPaymentService, drPaymentService, chatsPaymentService, payemtRoutApi);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType(LocalPayment.PaymentTypeEnum.valueOf(requestedType).value());
            request.getCreditor().setCreditorName("N".repeat(140));
            Payment expected = new Payment().status(Payment.StatusEnum.PROCESSING);
            when(onUsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(fpsPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(ttPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(drPaymentService.processPayment(any(), any())).thenReturn(expected);
            when(chatsPaymentService.processPayment(any(), any())).thenReturn(expected);
            String selectedType = "OTHERS".equals(requestedType) ? "CHATS" : requestedType;
            when(payemtRoutApi.route(any())).thenReturn(PaymentRoutingTestSupport.routeResponseForType(selectedType));

            // The actual selected service retains ownership of its own name rule.
            assertSame(expected, paymentService.processLocalPayment(request));

            verify(payemtRoutApi).route(any());
        }
    }

    @Test
    void omittedChatsIdentifierUsesNotProvidedWithoutReplacingBatchReference() {
        for (PaymentIdentification identifier : Arrays.asList(
                null,
                new PaymentIdentification(),
                new PaymentIdentification().endToEndId(""),
                new PaymentIdentification().endToEndId("   "))) {
            reset(chatsPaymentService, payemtRoutApi);
            PaymentInstructionRequest request = buildInstructionRequest();
            request.setPaymentType("CHATS");
            request.setPaymentIdentification(identifier);
            when(chatsPaymentService.processPayment(any(), any())).thenReturn(new Payment().status(Payment.StatusEnum.PROCESSING));
            when(payemtRoutApi.route(any())).thenAnswer(invocation -> {
                BatchRoutingCreditTransferRequest routing = invocation.getArgument(0);
                assertEquals(routing.getGroupHeader().getMessageId(), routing.getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
                assertNotNull(routing.getGroupHeader().getMessageId());
                return PaymentRoutingTestSupport.routeResponseForType("CHATS");
            });

            paymentService.processLocalPayment(request);

            ArgumentCaptor<BatchRoutingCreditTransferRequest> routed = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
            verify(chatsPaymentService).processPayment(any(), routed.capture());
            assertEquals(routed.getValue().getGroupHeader().getMessageId(), routed.getValue().getTransactions().getFirst().getPaymentIdentification().getEndToEndId());
        }
    }

    @Test
    @DisplayName("routed payments dispatch to their corresponding payment services")
    void processLocalPayment_dispatchesToRoutedService() {
        for (String routeType : List.of("ONUS", "CHATS", "FPS", "DR")) {
            LocalPayment payment = buildValidPayment();
            BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
            Payment expected = new Payment().status(Payment.StatusEnum.SUCCESS);
            when(payemtRoutApi.route(any(BatchRoutingCreditTransferRequest.class)))
                    .thenReturn(PaymentRoutingTestSupport.routeResponseForType(routeType));
            switch (routeType) {
                case "ONUS" -> when(onUsPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);
                case "CHATS" -> when(chatsPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);
                case "FPS" -> when(fpsPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);
                case "DR" -> when(drPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);
                default -> throw new IllegalStateException(routeType);
            }

            Payment result = dispatchThroughMockedBuilder(payment, routingRequest);

            assertEquals(expected, result);
            assertEquals(LocalPayment.PaymentTypeEnum.valueOf(routeType), payment.getPaymentType());
            verifyUndispatched(routeType);

            reset(payemtRoutApi, onUsPaymentService, chatsPaymentService, fpsPaymentService, drPaymentService);
        }
    }

    @Test
    @DisplayName("unsupported routed types are rejected with IPE_ROUT_EXCEPTION")
    void processLocalPayment_unsupportedRoutedType_throws() {
        LocalPayment payment = buildValidPayment();
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();

        ParameterException exception;
        try (MockedStatic<PaymentCommonRoutingBuilder> builder = mockStatic(PaymentCommonRoutingBuilder.class);
             MockedStatic<PaymentRoutingSubmitter> submitter = mockStatic(PaymentRoutingSubmitter.class)) {
            builder.when(() -> PaymentCommonRoutingBuilder.prepareCommonRoutingRequest(
                            any(LocalPayment.class), any(PartyAccountMapper.class)))
                    .thenReturn(routingRequest);
            submitter.when(() -> PaymentRoutingSubmitter.route(
                            any(PayemtRoutApi.class), any(BatchRoutingCreditTransferRequest.class)))
                    .thenReturn(LocalPayment.PaymentTypeEnum.OTHERS);

            exception = assertThrows(ParameterException.class,
                    () -> paymentService.processLocalPayment(payment));
        }

        assertEquals(HTHSupportErrorMapping.IPE_ROUT_EXCEPTION, exception.getErrCode());
        verifyUndispatched("OTHERS");
    }

    @Test
    @DisplayName("the API entry converts the external request and dispatches the core flow")
    void processLocalPayment_requestOverload_convertsAndDispatches() {
        PaymentInstructionRequest request = buildInstructionRequest();
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        Payment expected = new Payment().status(Payment.StatusEnum.SUCCESS);
        when(ttPaymentService.processPayment(any(LocalPayment.class), eq(routingRequest))).thenReturn(expected);
        when(payemtRoutApi.route(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(PaymentRoutingTestSupport.routeResponseForType("TT"));

        Payment result;
        try (MockedStatic<PaymentCommonRoutingBuilder> builder = mockStatic(PaymentCommonRoutingBuilder.class)) {
            builder.when(() -> PaymentCommonRoutingBuilder.prepareCommonRoutingRequest(
                            any(LocalPayment.class), any(PartyAccountMapper.class)))
                    .thenReturn(routingRequest);
            result = paymentService.processLocalPayment(request);
        }

        assertEquals(expected, result);
        ArgumentCaptor<LocalPayment> converted = ArgumentCaptor.forClass(LocalPayment.class);
        verify(ttPaymentService).processPayment(converted.capture(), eq(routingRequest));
        assertEquals("UT-CUST-TXN-000001", converted.getValue().getTransactionID());
        assertEquals(PARTY_ID, converted.getValue().getPartyId());
        assertEquals(CLOSE_ID, converted.getValue().getCloseId());
        assertEquals(DEBIT_ACCOUNT, converted.getValue().getFromAccount().getAccountNumber());
        assertEquals("HKD", converted.getValue().getFromAccount().getCurrency());
        assertEquals("PAYER NAME", converted.getValue().getFromAccount().getName());
        assertEquals(LocalPayment.PaymentTypeEnum.TT, converted.getValue().getPaymentType());
    }

    @Test
    @DisplayName("party/close id resolution failures fall back to null without failing the payment")
    void partyResolutionFailure_fallsBackToNull() {
        when(partyResolver.resolvePartyId()).thenThrow(new IllegalStateException("no token"));
        when(partyResolver.resolveCloseId()).thenThrow(new IllegalStateException("no token"));
        LocalPayment payment = buildValidPayment();
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        Payment expected = new Payment().status(Payment.StatusEnum.SUCCESS);
        when(ttPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);
        when(payemtRoutApi.route(any(BatchRoutingCreditTransferRequest.class)))
                .thenReturn(PaymentRoutingTestSupport.routeResponseForType("TT"));

        Payment result = dispatchThroughMockedBuilder(payment, routingRequest);

        assertEquals(expected, result);
        assertNull(payment.getPartyId());
        assertNull(payment.getCloseId());
    }

    private Payment dispatchThroughMockedBuilder(LocalPayment payment,
                                                 BatchRoutingCreditTransferRequest routingRequest) {
        try (MockedStatic<PaymentCommonRoutingBuilder> builder = mockStatic(PaymentCommonRoutingBuilder.class)) {
            builder.when(() -> PaymentCommonRoutingBuilder.prepareCommonRoutingRequest(
                            any(LocalPayment.class), any(PartyAccountMapper.class)))
                    .thenReturn(routingRequest);
            return paymentService.processLocalPayment(payment);
        }
    }

    private void verifyUndispatched(String routedType) {
        if (!"ONUS".equals(routedType)) {
            verify(onUsPaymentService, never()).processPayment(any(), any());
        }
        if (!"CHATS".equals(routedType)) {
            verify(chatsPaymentService, never()).processPayment(any(), any());
        }
        if (!"FPS".equals(routedType)) {
            verify(fpsPaymentService, never()).processPayment(any(), any());
        }
        if (!"TT".equals(routedType)) {
            verify(ttPaymentService, never()).processPayment(any(), any());
        }
        if (!"DR".equals(routedType)) {
            verify(drPaymentService, never()).processPayment(any(), any());
        }
    }

    @Test
    @DisplayName("routed TT payments are dispatched to the TT service with the accepted routing request")
    void processLocalPayment_dispatchesToTtService() {
        LocalPayment payment = buildValidPayment();
        BatchRoutingCreditTransferRequest routingRequest = buildRoutingRequest();
        Payment expected = new Payment().status(Payment.StatusEnum.SUCCESS);
        when(ttPaymentService.processPayment(payment, routingRequest)).thenReturn(expected);

        Payment result;
        try (MockedStatic<PaymentCommonRoutingBuilder> builder = mockStatic(PaymentCommonRoutingBuilder.class)) {
            builder.when(() -> PaymentCommonRoutingBuilder.prepareCommonRoutingRequest(
                            any(LocalPayment.class), any(PartyAccountMapper.class)))
                    .thenReturn(routingRequest);
            when(payemtRoutApi.route(any(BatchRoutingCreditTransferRequest.class)))
                    .thenReturn(PaymentRoutingTestSupport.routeResponseForType("TT"));

            result = paymentService.processLocalPayment(payment);
        }

        assertEquals(expected, result);
        assertEquals(PARTY_ID, payment.getPartyId());
        assertEquals(CLOSE_ID, payment.getCloseId());
        assertEquals("PAYER NAME", payment.getFromAccount().getName());
        assertEquals(LocalPayment.PaymentTypeEnum.TT, payment.getPaymentType());
        verify(onUsPaymentService, never()).processPayment(any(), any());
        verify(chatsPaymentService, never()).processPayment(any(), any());
        verify(fpsPaymentService, never()).processPayment(any(), any());
        verify(drPaymentService, never()).processPayment(any(), any());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildValidPayment() {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setId(DEBIT_ACCOUNT);
        fromAccount.setType("CUR");

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setId(BENEFICIARY_ACCOUNT);
        toAccount.setType("SSA");

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(Currency.HKD);

        com.hkbea.hth.model.Creditor creditor = new com.hkbea.hth.model.Creditor();
        creditor.setCreditorName("BENEFICIARY");

        LocalPayment payment = new LocalPayment();
        payment.setTransactionID("UT-CUST-TXN-000001");
        payment.setPartyId(PARTY_ID);
        payment.setCloseId(CLOSE_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setSettlementDate(LocalDate.now().toString());
        return payment;
    }

    private HthUserAccessAccount accessAccount(String productCode) {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setPartyId(PARTY_ID);
        account.setCloseId(CLOSE_ID);
        account.setAccountNumber(DEBIT_ACCOUNT);
        account.setProductCode(productCode);
        account.setCurrency("HKD");
        account.setObjectStatus("A");
        return account;
    }

    private Bgech7e0Response accountInfoResponse() {
        Eh7e0Output output = new Eh7e0Output();
        output.setEh7e0CodReturnO(PaymentConstants.SUCC_CODE_00);
        output.setEh7e0FlgAccHoldO("N");
        Bgech7e0Res bgech7e0Res = new Bgech7e0Res();
        bgech7e0Res.setEh7e0Output(output);
        QaechccRes7e0 qaechccRes7e0 = new QaechccRes7e0();
        qaechccRes7e0.setBgech7e0(bgech7e0Res);
        DfhcommareaRes7e0 dfhcommareaRes7e0 = new DfhcommareaRes7e0();
        dfhcommareaRes7e0.setQaechcc(qaechccRes7e0);
        Bgech7e0Response response = new Bgech7e0Response();
        response.setDfhcommarea(dfhcommareaRes7e0);
        return response;
    }

    private void stubAccessAccounts(String productCode) {
        when(hthUserAccessAccountMapper.selectFromAccountInfo(any(), any(), any()))
                .thenReturn(List.of(accessAccount(productCode)));
    }

    private ProductDetails productDetails() {
        ProductDetails product = new ProductDetails();
        product.setProductCode("6802");
        product.setProductCurrency("HKD");
        product.setProductDescriptionShort("Current Account");
        return product;
    }

    private PaymentInternal internalRecord(String status) {
        PaymentInternal entity = new PaymentInternal();
        entity.setId("INTERNAL-ID-1");
        entity.setPaymentId("PAY-INTERNAL-1");
        entity.setPartyId(PARTY_ID);
        entity.setStatus(status);
        entity.setPaymentType("INTERNAL");
        entity.setInstdAmount(new BigDecimal("100.00"));
        entity.setDebitAccountCur("HKD");
        entity.setDebitAccountId(DEBIT_ACCOUNT);
        entity.setCreditAccountId(BENEFICIARY_ACCOUNT);
        entity.setCreditAccountName("BENEFICIARY");
        entity.setCreationDate(LocalDateTime.now());
        entity.setPaymentDate(new Date());
        entity.setLastUpdatedDate(new Date());
        entity.setEndToEndId("E2E-1");
        entity.setRemarks("payment remark");
        entity.setProductCode("6802");
        return entity;
    }

    private PaymentPayoutRecord payoutRecord(String status) {
        PaymentPayoutRecord entity = new PaymentPayoutRecord();
        entity.setId("PAYOUT-ID-1");
        entity.setPaymentId("PAY-PAYOUT-1");
        entity.setPartyId(PARTY_ID);
        entity.setStatus(status);
        entity.setPaymentType("FPS");
        entity.setCustomerTransactionId("UT-CUST-TXN-000001");
        entity.setInstdAmount(new BigDecimal("100.00"));
        entity.setInstdCurrency("HKD");
        entity.setDebitAccountId(DEBIT_ACCOUNT);
        entity.setCreditAccountId(BENEFICIARY_ACCOUNT);
        entity.setCreditAccountName("BENEFICIARY");
        entity.setCreationDate(LocalDateTime.now());
        entity.setPaymentDate(LocalDateTime.now());
        entity.setLastUpdatedDate(LocalDateTime.now());
        entity.setEndToEndId("E2E-1");
        entity.setRemarks("payout remark");
        entity.setProdCode("6802");
        return entity;
    }

    private PaymentResultQueryApiResponse ipeResult(PaymentResultQueryResponse.StatusEnum status,
                                                    String errorMessage, Date lastUpdatedAt) {
        PaymentResultQueryResponse result = new PaymentResultQueryResponse();
        result.setStatus(status);
        result.setErrorMessage(errorMessage);
        result.setLastUpdatedAt(lastUpdatedAt);

        PaymentResultQueryApiResponse response = new PaymentResultQueryApiResponse();
        response.setCode(PaymentConstants.SUCC_CODE_00);
        response.setData(List.of(result));
        return response;
    }

    private PaymentInstructionRequest buildInstructionRequest() {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setPaymentType("TT");
        request.setTransactionID("UT-CUST-TXN-000001");

        AccountId debtor = new AccountId();
        debtor.setId(DEBIT_ACCOUNT);
        debtor.setType("CUR");
        request.setDebtorAccount(debtor);

        CreditorAccount creditorAccount = new CreditorAccount();
        creditorAccount.setId(BENEFICIARY_ACCOUNT);
        request.setCreditorAccount(creditorAccount);

        InstructedAmount amount = new InstructedAmount();
        amount.setAmount(new BigDecimal("100.00"));
        amount.setCurrency("HKD");
        request.setInstructedAmount(amount);

        com.hkbea.hth.model.Creditor creditor = new com.hkbea.hth.model.Creditor();
        creditor.setCreditorName("BENEFICIARY");
        request.setCreditor(creditor);
        request.setSettlementDate(LocalDate.now());
        return request;
    }

    private BatchRoutingCreditTransferRequest buildRoutingRequest() {
        BatchRoutingCreditTransferRequest request = new BatchRoutingCreditTransferRequest();
        request.setPaymentInformation(new PaymentInformation());
        RoutingTransactionDetailRequest txn = new RoutingTransactionDetailRequest();
        com.hkbea.hth.ipe.model.PaymentIdentification identification =
                new com.hkbea.hth.ipe.model.PaymentIdentification();
        identification.setEndToEndId("E2E-1");
        txn.setPaymentIdentification(identification);
        request.setTransactions(List.of(txn));
        return request;
    }

    private Fpu01RespOArea createFpu01RespOArea() {
        ResponseHeader header = new ResponseHeader();
        header.setEbzwk00oHdrRespCode(PaymentLimitApiConstants.SUCCESS_CODE);
        Fpu01RespOArea area = new Fpu01RespOArea();
        area.setEbzwk00oHeader(header);
        return area;
    }
}
