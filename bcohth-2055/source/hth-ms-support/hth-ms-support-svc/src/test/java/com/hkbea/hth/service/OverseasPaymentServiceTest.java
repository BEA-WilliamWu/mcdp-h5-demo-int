package com.hkbea.hth.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.Bgech7e0Request;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Res;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.DfhcommareaRes7e0;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Eh7e0Output;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.QaechccRes7e0;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.HthUserAccessAccountMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.BeaAccount;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.CreditorAgent;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.FinancialInstitutionId;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.service.payment.OverseasPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.PaymentServiceSupport;
import com.hkbea.hth.service.payment.PaymentValidationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Shared overseas (TT/DR) validation rules, driven through a concrete no-op
 * subclass so only the base-class behaviour is under test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentServiceTest {

    protected static final String PARTY_ID = "01552168032777";
    protected static final String CLOSE_ID = "CLOSE001";
    protected static final String DEBIT_ACCOUNT = "01516868006864";
    protected static final String BENEFICIARY_ACCOUNT = "01505216812024898";

    @InjectMocks
    private TestOverseasPaymentService service;

    @Mock
    private PayemtRoutApi payemtRoutApi;
    @Mock
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock
    private PaymentInternalMapper paymentInternalMapper;
    @Mock
    private PartyAccountMapper partyAccountMapper;
    @Mock
    private PartyPreferenceMapper partyPreferenceMapper;
    @Mock
    private InternalPayeeMapper internalPayeeMapper;
    @Mock
    private com.hkbea.hth.bg3.api.AccountApi bgAccountApi;
    @Mock
    private CommonHeader headers;
    @Mock
    private PaymentCommonService paymentCommonService;
    @Mock
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;

    @BeforeEach
    void setUp() {
        new HTHSupportErrorMapping();
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString()))
                .thenReturn(activePartyAccount());
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(anyString(),anyString())).thenReturn("PAYER NAME");
        lenient().when(hthUserAccessAccountMapper.selectFromAccountInfo(
                        nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(activeAccessAccount()));
        lenient().when(bgAccountApi.queryAccountInfo(any(Bgech7e0Request.class)))
                .thenReturn(buildAccountInfoResponse());
    }

    static class TestOverseasPaymentService extends OverseasPaymentService {
        @Override
        protected String insertPendingRecord(LocalPayment localPayment,
                                             com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest routingRequest,
                                             Payment payment) {
            return null;
        }

        @Override
        protected void persistPaymentRecord(String recordId,
                                            LocalPayment localPayment,
                                            com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest routingRequest,
                                            Payment payment,
                                            com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse result) {
            // no-op
        }

        @Override
        protected String routingPaymentMethod() {
            return "TT";
        }

        @Override
        protected void validateSpecific(LocalPayment localPayment) {
            // no-op
        }

        @Override
        protected void enrichRoutingDetail(com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest txn,
                                           LocalPayment localPayment) {
            // no-op
        }
    }

    @Test
    @DisplayName("null payment is rejected as missing mandatory parameter")
    void nullPayment_throws() {
        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(null));
        assertEquals(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account is rejected with DEBIT_ACCOUNT_MISSING")
    void missingDebitAccount_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.setFromAccount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("blank debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_MISSING")
    void blankDebitAccountNumber_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getFromAccount().setAccountNumber(" ");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("malformed debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID")
    void malformedDebitAccountNumber_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getFromAccount().setAccountNumber("015123");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("non-BEA debtor account number is rejected with DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID")
    void nonBeaDebitAccountNumber_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getFromAccount().setAccountNumber("12345678901234");

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debit currency is rejected with DEBIT_CURRENCY_MISSING_OR_INVALID")
    void missingDebitCurrency_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getFromAccount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("missing debtor account type is rejected with DEBIT_ACCOUNT_TYPE_MISSING")
    void missingDebitAccountType_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getFromAccount().setAccountType(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing instructedAmount is rejected with TXN_AMOUNT_MISSING")
    void missingTxnAmount_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.setTxnAmount(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.TXN_AMOUNT_MISSING, exception.getErrCode());
    }

    @Test
    @DisplayName("missing payment currency is rejected with PAYMENT_CURRENCY_MISSING_OR_INVALID")
    void missingPaymentCurrency_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getTxnAmount().setCurrency(null);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.PAYMENT_CURRENCY_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("zero amount is rejected with INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID")
    void zeroAmount_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getTxnAmount().setAmount(BigDecimal.ZERO);

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("amount with more than 15 significant digits is rejected")
    void excessiveAmountPrecision_throws() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getTxnAmount().setAmount(new BigDecimal("99999999999999.98"));

        ParameterException exception = assertThrows(ParameterException.class,
                () -> service.validateOverseas(payment));
        assertEquals(HTHSupportErrorMapping.INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, exception.getErrCode());
    }

    @Test
    @DisplayName("creditor account currency defaults to the debit currency when absent")
    void creditorCurrencyDefaultsToDebitCurrency() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.getToAccount().setCurrency(null);

        PaymentValidationSupport.useDebitCurrencyForCreditorAccount(payment);

        assertEquals(Currency.HKD, payment.getToAccount().getCurrency());
    }

    @Test
    @DisplayName("creditor name fallback keeps downstream builders non-null")
    void accountNameOrFallback_fallsBackToNA() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);
        payment.setCreditor(null);

        assertEquals("N/A", service.accountNameOrFallback(payment));
    }

    @Test
    @DisplayName("limit account carries the partyId as account number")
    void limitAccount_usesPartyId() {
        LocalPayment payment = buildOverseasPayment(Currency.HKD);

        BeaAccount limitAccount = PaymentServiceSupport.limitAccount(payment.getFromAccount(), PARTY_ID);

        assertEquals(PARTY_ID, limitAccount.getAccountNumber());
        assertEquals(BeaAccount.AccountTypeEnum.CUR, limitAccount.getAccountType());
        assertNull(limitAccount.getId());
    }

    // ===================== Fixtures =====================

    private LocalPayment buildOverseasPayment(Currency currency) {
        BeaAccount fromAccount = new BeaAccount();
        fromAccount.setAccountNumber(DEBIT_ACCOUNT);
        fromAccount.setCurrency(currency.toString());
        fromAccount.setAccountType(BeaAccount.AccountTypeEnum.CUR);

        PaymentToAccount toAccount = new PaymentToAccount();
        toAccount.setAccountNumber(BENEFICIARY_ACCOUNT);
        toAccount.setCurrency(currency);

        TxnAmount txnAmount = new TxnAmount();
        txnAmount.setAmount(new BigDecimal("100.00"));
        txnAmount.setCurrency(currency);

        Creditor creditor = new Creditor();
        creditor.setCreditorName("TT BENEFICIARY");

        CreditorAgent creditorAgent = new CreditorAgent();
        FinancialInstitutionId financialInstitutionId = new FinancialInstitutionId();
        financialInstitutionId.setBicfi("BKCHHKHHXXX");
        financialInstitutionId.setClearingSystemMemberId("");
        creditorAgent.setFinancialInstitutionId(financialInstitutionId);

        LocalPayment payment = new LocalPayment();
        payment.setPartyId(PARTY_ID);
        payment.setCloseId(CLOSE_ID);
        payment.setFromAccount(fromAccount);
        payment.setToAccount(toAccount);
        payment.setTxnAmount(txnAmount);
        payment.setCreditor(creditor);
        payment.setCreditorAgent(creditorAgent);
        return payment;
    }

    /** Valid BG9CH7E0 account-info response: active, non-dormant account. */
    static Bgech7e0Response buildAccountInfoResponse() {
        Eh7e0Output output = new Eh7e0Output();
        output.setEh7e0CodReturnO(PaymentConstants.SUCC_CODE_00);
        output.setEh7e0FlgStatusO("A");
        output.setEh7e0FlgDormantO("N");

        Bgech7e0Res bgech7e0 = new Bgech7e0Res();
        bgech7e0.setEh7e0Output(output);

        QaechccRes7e0 qaechcc = new QaechccRes7e0();
        qaechcc.setQaechccErrData(PaymentConstants.BG_RETURN_CODE_0000);
        qaechcc.setBgech7e0(bgech7e0);

        DfhcommareaRes7e0 dfhcommarea = new DfhcommareaRes7e0();
        dfhcommarea.setQaechcc(qaechcc);

        Bgech7e0Response response = new Bgech7e0Response();
        response.setDfhcommarea(dfhcommarea);
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

    private HthUserAccessAccount activeAccessAccount() {
        HthUserAccessAccount account = new HthUserAccessAccount();
        account.setObjectStatus("A");
        return account;
    }

}
