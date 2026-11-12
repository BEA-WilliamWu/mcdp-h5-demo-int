package com.hkbea.hth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.StatusEnum;
import com.hkbea.hth.ipe.model.TransferModelApiResponse;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.model.*;
import com.hkbea.hth.service.payment.ChatsPaymentService;
import com.hkbea.hth.service.payment.PaymentCommonService;
import com.hkbea.hth.service.payment.PaymentValidationSupport;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.FineGrainedEntitlementUtil;
import com.hkbea.hth.utils.PartyResolver;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Focused 2065 regression tests; downstream calls are mocked, routing/mapping are real. */
@ExtendWith(MockitoExtension.class)
class ChatsPaymentIpeContractTest {
    private static final String PARTY_ID = "01552168032777";
    private static final String DEBIT_ACCOUNT = "01516868006864";
    private static final String CREDIT_ACCOUNT = "01505216812024898";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static JsonNode schemas;

    @Mock private PayemtRoutApi payemtRoutApi;
    @Mock private AccountApi accountApi;
    @Mock private PartyAccountMapper partyAccountMapper;
    @Mock private InternalPayeeMapper internalPayeeMapper;
    @Mock private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Mock private PartyPreferenceMapper partyPreferenceMapper;
    @Mock private CommonHeader headers;
    @Mock private PaymentCommonService paymentCommonService;
    @Mock private CacheHelperUtils cacheHelperUtils;
    @Mock private ScameterValidatorUtil scameterValidatorUtil;
    @InjectMocks private ChatsPaymentService chatsPaymentService;
    private PaymentServiceImpl entry;

    @BeforeAll
    static void loadContract() throws Exception {
        new HTHSupportErrorMapping();
        try (var input = ChatsPaymentIpeContractTest.class.getResourceAsStream("/api/ipe/smartRouting.openapi.yaml")) {
            assertNotNull(input);
            schemas = JSON.readTree(input).path("components").path("schemas");
        }
    }

    @BeforeEach
    void setUp() {
        PartyAccount account = new PartyAccount();
        account.setStatus("A");
        account.setCompanyCode("00123");
        lenient().when(partyAccountMapper.selectProductCode(anyString(), anyString())).thenReturn("6802");
        lenient().when(partyAccountMapper.selectAccountStatus(anyString(), anyString(), anyString())).thenReturn(account);
        lenient().when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID)).thenReturn("PAYER COMPANY");
        BalanceDetail balance = new BalanceDetail().currency(Currency.HKD).availableBalance(new BigDecimal("10000"));
        BatchAccountResult accountResult = new BatchAccountResult()
                .queryStatus(BatchAccountResult.QueryStatusEnum.SUCCESS).balances(List.of(balance));
        lenient().when(accountApi.enquireGenericBalance(any()))
                .thenReturn(new BatchEnquiryResponse().results(List.of(accountResult)));
        TransferPaymentResultQueryResponse result = new TransferPaymentResultQueryResponse();
        result.setStatus(StatusEnum.SUCCESS);
        result.setIpeReferenceId("IPE-REF-CHATS");
        TransferModelApiResponse response = new TransferModelApiResponse();
        response.setCode(PaymentConstants.API_SUCCESS_CODE);
        response.setData(result);
        lenient().when(payemtRoutApi.submit(any())).thenReturn(response);
        lenient().when(payemtRoutApi.route(any())).thenReturn(PaymentRoutingTestSupport.routeResponseForType("CHATS"));

        entry = spy(new PaymentServiceImpl());
        // Isolate the CHATS contract from unrelated token, entitlement and account-status checks.
        // The entry's route selection, common payload builder and CHATS processing remain real.
        lenient().doNothing().when(entry).validateMandatoryFields(any());
        lenient().doNothing().when(entry).validateCommon(any());
        PartyResolver resolver = mock(PartyResolver.class);
        lenient().when(resolver.resolvePartyId()).thenReturn(PARTY_ID);
        ReflectionTestUtils.setField(entry, "partyResolver", resolver);
        ReflectionTestUtils.setField(entry, "uriInfo", mock(UriInfo.class));
        ReflectionTestUtils.setField(entry, "fineGrainedEntitlementUtil", mock(FineGrainedEntitlementUtil.class));
        ReflectionTestUtils.setField(entry, "partyPreferenceMapper", partyPreferenceMapper);
        ReflectionTestUtils.setField(entry, "partyAccountMapper", partyAccountMapper);
        ReflectionTestUtils.setField(entry, "payemtRoutApi", payemtRoutApi);
        ReflectionTestUtils.setField(entry, "chatsPaymentService", chatsPaymentService);
        ReflectionTestUtils.setField(entry, "cacheHelperUtils", cacheHelperUtils);
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("addressLine1", "A".repeat(71), 500141),
                Arguments.of("addressLine2", "B".repeat(71), 500144),
                Arguments.of("addressLine2", " ".repeat(71), 500144),
                Arguments.of("addressLine2", "\u4e2d", 500144),
                Arguments.of("townName", "T".repeat(71), 500142),
                Arguments.of("country", "H", 500143),
                Arguments.of("country", "HKG", 500143),
                Arguments.of("country", "hk", 500143),
                Arguments.of("country", " HK", 500143),
                Arguments.of("country", "", 500143),
                Arguments.of("country", "  ", 500143),
                Arguments.of("debtorName", "P".repeat(141), 500000),
                Arguments.of("debtorName", "\uD840\uDC00".repeat(141), 500000),
                Arguments.of("debtorName", null, 500000),
                Arguments.of("debtorName", "", 500000),
                Arguments.of("debtorName", "   ", 500000));
    }

    @ParameterizedTest(name = "explicit CHATS: {0} invalid case {index}")
    @MethodSource("invalidValues")
    void explicitChatsRejectsBeforeRouting(String field, String value, int errorCode) {
        LocalPayment payment = payment();
        setField(payment, field, value);

        assertInvalid(errorCode, () -> entry.processLocalPayment(payment));

        verifyNoInteractions(payemtRoutApi);
        verifyNoInteractions(paymentPayoutRecordMapper);
    }

    @ParameterizedTest(name = "auto-routed CHATS: {0} invalid case {index}")
    @MethodSource("invalidValues")
    void autoRoutedChatsRejectsBeforeSubmission(String field, String value, int errorCode) {
        LocalPayment payment = payment();
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.OTHERS);
        setField(payment, field, value);

        assertInvalid(errorCode, () -> entry.processLocalPayment(payment));

        verify(payemtRoutApi).route(any());
        verify(payemtRoutApi, never()).submit(any());
        verifyNoInteractions(paymentPayoutRecordMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHATS", "OTHERS"})
    void boundaryValuesReachSubmissionUnchanged(String requestedType) {
        LocalPayment payment = payment();
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.valueOf(requestedType));
        PostalAddress address = payment.getCreditor().getPostalAddress();
        address.setAddressLine1("A".repeat(70));
        address.setAddressLine2("B".repeat(70));
        address.setTownName("T".repeat(70));
        when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID)).thenReturn("P".repeat(140));

        assertEquals(Payment.StatusEnum.SUCCESS, entry.processLocalPayment(payment).getStatus());

        BatchRoutingCreditTransferRequest request = submittedRequest();
        verify(payemtRoutApi).route(same(request));
        JsonNode txn = JSON.valueToTree(request).path("transactions").get(0);
        assertEquals("P".repeat(140), txn.at("/debtor/name").asText());
        assertMaxLength(txn.at("/debtor/name"), "Debtor", "name");
        for (String field : List.of("addressLine1", "addressLine2", "townName", "country")) {
            JsonNode value = txn.at("/creditor/postalAddress/" + field);
            assertEquals(JSON.valueToTree(address).get(field), value);
            assertMaxLength(value, "PostalAddress", field);
        }
        assertEquals(2, txn.at("/creditor/postalAddress/country").asText().length());
        assertEquals("BEASHKHHXXX", txn.at("/debtorAgent/financialInstitution/bicfi").asText());
        assertEquals("/IFT01/", txn.at("/purpose/proprietary").asText());
        assertEquals("6304357992232185", txn.at("/supplementaryData/chatsRemittance/depositAccountName2").asText());
        PaymentPayoutRecord record = insertedRecord();
        assertEquals(address.getAddressLine1(), record.getCreditAddressLine1());
        assertEquals(address.getAddressLine2(), record.getCreditAddressLine2());
        assertEquals(address.getTownName(), record.getCreditTownName());
        assertEquals("HK", record.getCreditCountryCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void omittedOrEmptyAddressStillSubmitsAndPersists(boolean omitObject) {
        LocalPayment payment = payment();
        payment.getCreditor().setPostalAddress(omitObject ? null : new PostalAddress());

        assertEquals(Payment.StatusEnum.SUCCESS, entry.processLocalPayment(payment).getStatus());

        JsonNode txn = JSON.valueToTree(submittedRequest()).path("transactions").get(0);
        assertTrue(txn.at("/creditor/postalAddress").isMissingNode());
        assertTrue(txn.at("/creditorAccount/country").isMissingNode());
        PaymentPayoutRecord record = insertedRecord();
        assertNull(record.getCreditAddressLine1());
        assertNull(record.getCreditAddressLine2());
        assertNull(record.getCreditTownName());
        assertNull(record.getCreditCountryCode());
    }

    @Test
    void optionalEmptyLinesAndSeventySpacesRemainUnchanged() {
        LocalPayment payment = payment();
        payment.getCreditor().getPostalAddress().setAddressLine1("");
        payment.getCreditor().getPostalAddress().setAddressLine2(" ".repeat(70));
        payment.getCreditor().getPostalAddress().setTownName("");
        payment.getCreditor().getPostalAddress().setCountry(null);

        entry.processLocalPayment(payment);

        var address = submittedRequest().getTransactions().getFirst().getCreditor().getPostalAddress();
        assertEquals("", address.getAddressLine1());
        assertEquals(" ".repeat(70), address.getAddressLine2());
        assertEquals("", address.getTownName());
        assertNull(address.getCountry());
    }

    @Test
    void unicodeNamesAndAddressLengthsCountCharactersNotUtf16Units() {
        LocalPayment payment = payment();
        String character = "\uD840\uDC00";
        payment.getCreditor().getPostalAddress().setAddressLine1(character.repeat(70));
        payment.getCreditor().getPostalAddress().setTownName(character.repeat(70));
        when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID)).thenReturn(character.repeat(140));

        entry.processLocalPayment(payment);

        var txn = submittedRequest().getTransactions().getFirst();
        assertEquals(character.repeat(140), txn.getDebtor().getName());
        assertEquals(character.repeat(70), txn.getCreditor().getPostalAddress().getAddressLine1());
        assertEquals(character.repeat(70), txn.getCreditor().getPostalAddress().getTownName());
    }

    @Test
    void changedPayerNameIsRecheckedBeforeSubmission() {
        when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID))
                .thenReturn("VALID PAYER", "P".repeat(141));

        assertInvalid(500000, () -> entry.processLocalPayment(payment()));

        verify(payemtRoutApi).route(any());
        verify(payemtRoutApi, never()).submit(any());
        verifyNoInteractions(paymentPayoutRecordMapper);
    }

    @Test
    void legacyBeneficiaryNameMappingIsPreservedPendingIpeAgreement() {
        LocalPayment payment = payment();
        payment.getCreditor().setCreditorName("N".repeat(140));

        entry.processLocalPayment(payment);

        var txn = submittedRequest().getTransactions().getFirst();
        assertEquals("N".repeat(140), txn.getCreditor().getName());
        assertEquals("N".repeat(140), txn.getCreditorAccount().getAccountName());
        // This preserves the explicitly requested legacy behavior, NOT full IPE schema compliance.
        assertEquals("N".repeat(140), txn.getSupplementaryData().getChatsRemittance().getDepositAccountName1());
        assertEquals(35, schemas.at("/ChatsRemittance/properties/depositAccountName1/maxLength").asInt());
    }

    @Test
    void ttAddressRuleStillRequiresAddressAndLimitsTownToThirtyFive() {
        LocalPayment payment = payment();
        payment.getCreditor().getPostalAddress().setTownName("T".repeat(36));
        assertInvalid(500142, () -> PaymentValidationSupport.validateChatsAddress(payment));
        payment.getCreditor().setPostalAddress(null);
        assertInvalid(500141, () -> PaymentValidationSupport.validateChatsAddress(payment));
    }

    private void setField(LocalPayment payment, String field, String value) {
        if ("debtorName".equals(field)) {
            when(partyPreferenceMapper.selectCustomerNameByPartyId(PARTY_ID)).thenReturn(value);
        } else {
            ReflectionTestUtils.setField(payment.getCreditor().getPostalAddress(), field, value);
        }
    }

    private void assertInvalid(int errorCode, org.junit.jupiter.api.function.Executable action) {
        ParameterException error = assertThrows(ParameterException.class, action);
        assertEquals(errorCode, error.getErrCode());
        if (errorCode == 500000) {
            assertTrue(error.getMessage().startsWith("debtor.name"));
        }
    }

    private void assertMaxLength(JsonNode value, String schema, String property) {
        int max = schemas.path(schema).path("properties").path(property).path("maxLength").asInt();
        assertTrue(max > 0);
        assertTrue(value.asText().codePointCount(0, value.asText().length()) <= max);
    }

    private BatchRoutingCreditTransferRequest submittedRequest() {
        var captor = ArgumentCaptor.forClass(BatchRoutingCreditTransferRequest.class);
        verify(payemtRoutApi).submit(captor.capture());
        return captor.getValue();
    }

    private PaymentPayoutRecord insertedRecord() {
        var captor = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(paymentPayoutRecordMapper).insert(captor.capture());
        return captor.getValue();
    }

    private LocalPayment payment() {
        BeaAccount from = new BeaAccount();
        from.setId(DEBIT_ACCOUNT);
        from.setAccountNumber(DEBIT_ACCOUNT);
        from.setCurrency("HKD");
        from.setAccountType(BeaAccount.AccountTypeEnum.CUR);
        PaymentToAccount to = new PaymentToAccount();
        to.setId(CREDIT_ACCOUNT);
        to.setAccountNumber(CREDIT_ACCOUNT);
        to.setCurrency(Currency.HKD);
        to.setAccountType(PaymentToAccount.AccountTypeEnum.SSA);
        LocalPayment payment = new LocalPayment();
        payment.setTransactionID("UT-CHATS-2065");
        payment.setPaymentType(LocalPayment.PaymentTypeEnum.CHATS);
        payment.setPartyId(PARTY_ID);
        payment.setFromAccount(from);
        payment.setToAccount(to);
        payment.setTxnAmount(new TxnAmount().amount(new BigDecimal("100.00")).currency(Currency.HKD));
        payment.setCreditor(new Creditor().creditorName("CHATS BENEFICIARY").postalAddress(new PostalAddress()
                .addressLine1("12 Queens Road").addressLine2("Floor 2").townName("Hong Kong").country("HK")));
        payment.setCreditorAgent(new CreditorAgent().financialInstitutionId(new FinancialInstitutionId().clearingSystemMemberId("004")));
        payment.setPaymentIdentification(new PaymentIdentification().endToEndId("CUSTOMER-E2E"));
        payment.setSettlementDate(LocalDate.now().toString());
        payment.setChargesOption(LocalPayment.ChargesOptionEnum.SHA);
        payment.setPaymentDetails(List.of("Payment reference"));
        return payment;
    }
}
