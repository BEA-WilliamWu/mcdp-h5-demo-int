package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.model.PaymentResultQueryApiResponse;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthNwPaymentPayoutMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.PaymentTransaction;
import com.hkbea.hth.utils.FineGrainedEntitlementUtil;
import com.hkbea.hth.utils.PartyResolver;
import jakarta.ws.rs.core.UriInfo;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** BCOH2H-2126: exercises the existing common query through its public entry point. */
@ExtendWith(MockitoExtension.class)
class PaymentEnquiryServiceTest {
    private static final String REF = "HTH20261111093000000001";
    private static final String PARTY = "PARTY-1";
    private static final String ACCOUNT = "01516868006864";
    private static final String PATH = "payment/v1/transactions/enquiry";

    @InjectMocks private PaymentServiceImpl service;
    @Mock private PartyResolver partyResolver;
    @Mock private PaymentInternalMapper paymentInternalMapper;
    @Mock private HthNwPaymentPayoutMapper payoutMapper;
    @Mock private LocalPaymentApi localPaymentApi;
    @Mock private FineGrainedEntitlementUtil fineGrainedEntitlementUtil;
    @Mock private UriInfo uriInfo;

    @BeforeAll
    static void initialiseMetadata() {
        new HTHSupportErrorMapping();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "enquiry-test");
        assistant.setCurrentNamespace("enquiry-test");
        TableInfoHelper.initTableInfo(assistant, PaymentInternal.class);
        TableInfoHelper.initTableInfo(assistant, PaymentPayoutRecord.class);
    }

    @BeforeEach
    void caller() {
        when(partyResolver.resolvePartyId()).thenReturn(PARTY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingReferenceUsesExistingCodeBeforeAnyLookup(String ref) {
        ParameterException ex = assertThrows(ParameterException.class, () -> service.enquireTransactionRecord(ref));
        assertEquals(500101, ex.getErrCode());
        verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
    }

    @ParameterizedTest
    @ValueSource(ints = {51, 1000})
    void oversizedReferenceUsesFieldSpecificCommonError(int length) {
        ParameterException ex = assertThrows(ParameterException.class,
                () -> service.enquireTransactionRecord("R".repeat(length)));
        assertEquals(500000, ex.getErrCode());
        assertTrue(ex.getMessage().startsWith("txnRef"));
        verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
    }

    @Test
    void fiftyCharacterReferenceReachesCompanyScopedLookupWithoutTruncation() {
        String ref = "R".repeat(50);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(ref));
        assertEquals(600131, ex.getErrCode());
        assertLookupScopes(ref);
    }

    @ParameterizedTest
    @CsvSource({"INTERNAL,Success", "DR,Rejected", "FPS,Success", "CHATS,Rejected", "TT,Success"})
    void allPaymentTypesReturnStoredDetailsAndFinalStatusesWithoutPolling(String type, String status) {
        stored(type, status);
        PaymentTransaction result = service.enquireTransactionRecord(REF);
        assertEquals(REF, result.getReferenceNumber());
        assertEquals(type, result.getPaymentType().value());
        assertEquals(status, result.getStatus().value());
        assertEquals(new BigDecimal("123.45"), result.getTransferAmount());
        assertEquals("HKD", result.getTransferCurrency());
        assertEquals(ACCOUNT, result.getRemitterAccountNumber());
        assertEquals("BENEFICIARY-1", result.getBeneficiaryAccountNumber());
        assertEquals("BENEFICIARY", result.getBeneficiaryAccountName());
        assertEquals("stored reference", result.getExternalReferenceId());
        verify(fineGrainedEntitlementUtil).isAuthorizedAccount(PATH, ACCOUNT);
        verifyNoInteractions(localPaymentApi);
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
        verify(payoutMapper, never()).updateById(any(PaymentPayoutRecord.class));
        if (isInternal(type)) {
            verifyNoInteractions(payoutMapper);
        } else {
            assertLookupScopes(REF);
        }
    }

    @ParameterizedTest
    @CsvSource({"INTERNAL,Pending", "INTERNAL,Processing", "DR,Pending", "DR,Processing",
            "FPS,Pending", "FPS,Processing", "CHATS,Pending", "CHATS,Processing",
            "TT,Pending", "TT,Processing", "TT,pEnDiNg", "DR,pRoCeSsInG"})
    void pendingAndProcessingPaymentsRefreshForEveryPaymentType(String type, String status) {
        stored(type, status);
        Date updatedAt = new Date(1794387600000L);
        PaymentResultQueryResponse ipe = new PaymentResultQueryResponse()
                .status(PaymentResultQueryResponse.StatusEnum.SUCCESS).lastUpdatedAt(updatedAt);
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response(ipe));

        PaymentTransaction result = service.enquireTransactionRecord(REF);

        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, result.getStatus());
        assertEquals(updatedAt.toInstant(), result.getAsOf().toInstant());
        verify(localPaymentApi).queryPaymentResults(argThat(q -> REF.equals(q.getTransactionId())));
        if (isInternal(type)) {
            verify(paymentInternalMapper).updateById(argThat((PaymentInternal p) ->
                    "ROW-1".equals(p.getId()) && "Success".equals(p.getStatus()) && "SUCCESS".equals(p.getIpeStatus())));
        } else {
            verify(payoutMapper).updateById(argThat((PaymentPayoutRecord p) ->
                    "ROW-1".equals(p.getId()) && "Success".equals(p.getStatus()) && "SUCCESS".equals(p.getIpeStatus())));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DR", "TT"})
    void failedRefreshReturnsAndPersistsRejection(String type) {
        stored(type, "Pending");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response(new PaymentResultQueryResponse()
                .status(PaymentResultQueryResponse.StatusEnum.FAILED)
                .errorMessage("Payment rejected").statusReason("DOWNSTREAM_REJECTED")));
        PaymentTransaction result = service.enquireTransactionRecord(REF);
        assertEquals(PaymentTransaction.StatusEnum.REJECTED, result.getStatus());
        assertEquals("Payment rejected", result.getRejectMessage());
        if (isInternal(type)) {
            verify(paymentInternalMapper).updateById(argThat((PaymentInternal p) ->
                    "Payment rejected".equals(p.getRejectMessage()) && "DOWNSTREAM_REJECTED".equals(p.getIpeStatusReason())));
        } else {
            verify(payoutMapper).updateById(argThat((PaymentPayoutRecord p) ->
                    "Payment rejected".equals(p.getRejectMessage()) && "DOWNSTREAM_REJECTED".equals(p.getIpeStatusReason())));
        }
    }

    @ParameterizedTest
    @CsvSource({"DR,nullResponse", "TT,errorCode", "TT,emptyData", "DR,nullStatus", "TT,exception"})
    void inconclusiveQueryPreservesStoredPendingResult(String type, String scenario) {
        stored(type, "Pending");
        switch (scenario) {
            case "errorCode" -> when(localPaymentApi.queryPaymentResults(any()))
                    .thenReturn(new PaymentResultQueryApiResponse().code("ERROR"));
            case "emptyData" -> when(localPaymentApi.queryPaymentResults(any()))
                    .thenReturn(new PaymentResultQueryApiResponse().code("00").data(List.of()));
            case "nullStatus" -> when(localPaymentApi.queryPaymentResults(any()))
                    .thenReturn(response(new PaymentResultQueryResponse()));
            case "exception" -> when(localPaymentApi.queryPaymentResults(any()))
                    .thenThrow(new IllegalStateException("IPE unavailable"));
            default -> { } // An unstubbed response is null.
        }
        assertEquals(PaymentTransaction.StatusEnum.PENDING, service.enquireTransactionRecord(REF).getStatus());
        verify(localPaymentApi).queryPaymentResults(any());
        verify(paymentInternalMapper, never()).updateById(any(PaymentInternal.class));
        verify(payoutMapper, never()).updateById(any(PaymentPayoutRecord.class));
    }

    @Test
    void unchangedProcessingResultDoesNotWriteAgain() {
        stored("FPS", "Processing");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response(new PaymentResultQueryResponse()
                .status(PaymentResultQueryResponse.StatusEnum.PROCESSING)));
        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, service.enquireTransactionRecord(REF).getStatus());
        verify(payoutMapper, never()).updateById(any(PaymentPayoutRecord.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN"})
    void unspecifiedStoredStatusesKeepExistingBehaviour(String status) {
        stored("TT", status);
        assertNull(service.enquireTransactionRecord(REF).getStatus());
        verifyNoInteractions(localPaymentApi);
    }

    @Test
    void noRecordReturnsExistingNotFoundErrorAndSearchesOnlyCallersCompany() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF));
        assertEquals(600131, ex.getErrCode());
        assertEquals("Payment transaction not found for the given transactionId.", ex.getMessage());
        assertLookupScopes(REF);
        verifyNoInteractions(fineGrainedEntitlementUtil, localPaymentApi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL", "TT"})
    void unauthorizedDebitAccountStopsBeforeDownstreamQuery(String type) {
        stored(type, "Pending");
        doThrow(new ServiceException(600027)).when(fineGrainedEntitlementUtil).isAuthorizedAccount(PATH, ACCOUNT);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF));
        assertEquals(600027, ex.getErrCode());
        verifyNoInteractions(localPaymentApi);
    }

    @ParameterizedTest
    @ValueSource(ints = {500070, 500071})
    void callerResolutionErrorsDoNotQueryAnotherCompany(int code) {
        when(partyResolver.resolvePartyId()).thenThrow(new ServiceException(code));
        assertEquals(code, assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF)).getErrCode());
        verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
    }

    private boolean isInternal(String type) {
        return "INTERNAL".equals(type) || "DR".equals(type);
    }

    private void stored(String type, String status) {
        when(uriInfo.getPath()).thenReturn(PATH);
        if (isInternal(type)) {
            PaymentInternal entity = new PaymentInternal();
            entity.setId("ROW-1");
            entity.setPaymentId(REF);
            entity.setPartyId(PARTY);
            entity.setPaymentType(type);
            entity.setStatus(status);
            entity.setDebitAccountId(ACCOUNT);
            entity.setDebitAccountCur("HKD");
            entity.setCreditAccountId("BENEFICIARY-1");
            entity.setCreditAccountName("BENEFICIARY");
            entity.setInstdAmount(new BigDecimal("123.45"));
            entity.setEndToEndId("stored reference");
            when(paymentInternalMapper.selectOne(any())).thenReturn(entity);
        } else {
            PaymentPayoutRecord entity = new PaymentPayoutRecord();
            entity.setId("ROW-1");
            entity.setPaymentId(REF);
            entity.setPartyId(PARTY);
            entity.setPaymentType(type);
            entity.setStatus(status);
            entity.setDebitAccountId(ACCOUNT);
            entity.setInstdCurrency("HKD");
            entity.setCreditAccountId("BENEFICIARY-1");
            entity.setCreditAccountName("BENEFICIARY");
            entity.setInstdAmount(new BigDecimal("123.45"));
            entity.setEndToEndId("stored reference");
            when(payoutMapper.selectOne(any())).thenReturn(entity);
        }
    }

    private PaymentResultQueryApiResponse response(PaymentResultQueryResponse result) {
        return new PaymentResultQueryApiResponse().code("00").data(List.of(result));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertLookupScopes(String ref) {
        ArgumentCaptor<LambdaQueryWrapper> internal = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<LambdaQueryWrapper> payout = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paymentInternalMapper).selectOne(internal.capture());
        verify(payoutMapper).selectOne(payout.capture());
        for (LambdaQueryWrapper query : List.of(internal.getValue(), payout.getValue())) {
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("PAYMENT_ID"));
            assertTrue(sql.contains("PARTY_ID"));
            assertEquals(2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue(ref));
            assertTrue(query.getParamNameValuePairs().containsValue(PARTY));
        }
    }
}
