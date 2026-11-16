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

    @Test
    void missingReferenceUsesExistingCodeBeforeAnyLookup() {
        for (String ref : new String[]{null, "", " ", "\t"}) {
            ParameterException ex = assertThrows(ParameterException.class, () -> service.enquireTransactionRecord(ref));
            assertEquals(500101, ex.getErrCode());
            verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
        }
    }

    @Test
    void oversizedReferenceUsesFieldSpecificCommonError() {
        for (int length : new int[]{51, 1000}) {
            ParameterException ex = assertThrows(ParameterException.class,
                    () -> service.enquireTransactionRecord("R".repeat(length)));
            assertEquals(500000, ex.getErrCode());
            assertTrue(ex.getMessage().startsWith("txnRef"));
            verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
        }
    }

    @Test
    void fiftyCharacterReferenceReachesCompanyScopedLookupWithoutTruncation() {
        String ref = "R".repeat(50);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(ref));
        assertEquals(600131, ex.getErrCode());
        assertLookupScopes(ref);
    }

    @Test
    void allPaymentTypesReturnStoredDetailsAndFinalStatusesWithoutPolling() {
        String[][] cases = {
                {"INTERNAL", "Success"},
                {"DR", "Rejected"},
                {"FPS", "Success"},
                {"CHATS", "Rejected"},
                {"TT", "Success"}
        };
        for (String[] c : cases) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            String type = c[0];
            String status = c[1];
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
    }

    @Test
    void pendingAndProcessingPaymentsRefreshForEveryPaymentType() {
        String[][] cases = {
                {"INTERNAL", "Pending"}, {"INTERNAL", "Processing"},
                {"DR", "Pending"}, {"DR", "Processing"},
                {"FPS", "Pending"}, {"FPS", "Processing"},
                {"CHATS", "Pending"}, {"CHATS", "Processing"},
                {"TT", "Pending"}, {"TT", "Processing"},
                {"TT", "pEnDiNg"}, {"DR", "pRoCeSsInG"}
        };
        for (String[] c : cases) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            String type = c[0];
            String status = c[1];
            stored(type, status);
            Date updatedAt = new Date(1794387600000L);
            PaymentResultQueryResponse ipe = new PaymentResultQueryResponse()
                    .status(PaymentResultQueryResponse.StatusEnum.SUCCESS).lastUpdatedAt(updatedAt);
            when(localPaymentApi.queryPaymentResults(any())).thenReturn(response(ipe));

            PaymentTransaction result = service.enquireTransactionRecord(REF);

            assertEquals(PaymentTransaction.StatusEnum.SUCCESS, result.getStatus());
            assertEquals(updatedAt.toInstant(), result.getAsOf().toInstant());
            verify(localPaymentApi).queryPaymentResults(argThat(q -> "CHATS".equals(type)
                    ? REF.equals(q.getBatchId()) && q.getTransactionId() == null
                    : REF.equals(q.getTransactionId()) && q.getBatchId() == null));
            if (isInternal(type)) {
                verify(paymentInternalMapper).updateById(argThat((PaymentInternal p) ->
                        "ROW-1".equals(p.getId()) && "Success".equals(p.getStatus()) && "SUCCESS".equals(p.getIpeStatus())));
            } else {
                verify(payoutMapper).updateById(argThat((PaymentPayoutRecord p) ->
                        "ROW-1".equals(p.getId()) && "Success".equals(p.getStatus()) && "SUCCESS".equals(p.getIpeStatus())));
            }
        }
    }

    @Test
    void failedRefreshReturnsAndPersistsRejection() {
        for (String type : new String[]{"DR", "TT", "CHATS"}) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
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
    }

    @Test
    void inconclusiveQueryPreservesStoredPendingResult() {
        String[][] cases = {
                {"DR", "nullResponse"},
                {"TT", "errorCode"},
                {"TT", "emptyData"},
                {"DR", "nullStatus"},
                {"TT", "exception"},
                {"CHATS", "nullResponse"},
                {"CHATS", "errorCode"},
                {"CHATS", "emptyData"},
                {"CHATS", "nullStatus"},
                {"CHATS", "exception"}
        };
        for (String[] c : cases) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            String type = c[0];
            String scenario = c[1];
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
    }

    @Test
    void oldAndNewChatsRecordsRefreshByGeneratedBatchReference() {
        // Legacy CHATS endToEndId equalled the generated reference; new requests
        // carry a customer value. Neither changes the HTH lookup or response ID.
        for (String ipeTransactionId : new String[]{REF, "CUSTOMER-END-TO-END-ID"}) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            stored("CHATS", "Processing");
            when(localPaymentApi.queryPaymentResults(any())).thenReturn(response(new PaymentResultQueryResponse()
                    .batchId(REF).transactionId(ipeTransactionId).status(PaymentResultQueryResponse.StatusEnum.SUCCESS)));

            PaymentTransaction result = service.enquireTransactionRecord(REF);

            assertEquals(REF, result.getReferenceNumber());
            assertEquals(PaymentTransaction.StatusEnum.SUCCESS, result.getStatus());
            assertLookupScopes(REF);
            verify(localPaymentApi).queryPaymentResults(argThat(q ->
                    REF.equals(q.getBatchId()) && q.getTransactionId() == null));
            verify(payoutMapper).updateById(argThat((PaymentPayoutRecord p) ->
                    "ROW-1".equals(p.getId()) && "Success".equals(p.getStatus())));
        }
    }

    @Test
    void multipleChatsBatchResultsPreserveStoredStatusWithoutChoosingFirst() {
        stored("CHATS", "Processing");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(new PaymentResultQueryApiResponse().code("00")
                .data(List.of(
                        new PaymentResultQueryResponse().batchId(REF).transactionId("CUSTOMER-ID-1")
                                .status(PaymentResultQueryResponse.StatusEnum.SUCCESS),
                        new PaymentResultQueryResponse().batchId(REF).transactionId("CUSTOMER-ID-2")
                                .status(PaymentResultQueryResponse.StatusEnum.FAILED))));

        PaymentTransaction result = service.enquireTransactionRecord(REF);

        assertEquals(REF, result.getReferenceNumber());
        assertEquals(PaymentTransaction.StatusEnum.PROCESSING, result.getStatus());
        verify(localPaymentApi).queryPaymentResults(argThat(q ->
                REF.equals(q.getBatchId()) && q.getTransactionId() == null));
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

    @Test
    void unspecifiedStoredStatusesKeepExistingBehaviour() {
        for (String status : new String[]{null, "", "UNKNOWN"}) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            stored("TT", status);
            assertNull(service.enquireTransactionRecord(REF).getStatus());
            verifyNoInteractions(localPaymentApi);
        }
    }

    @Test
    void noRecordReturnsExistingNotFoundErrorAndSearchesOnlyCallersCompany() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF));
        assertEquals(600131, ex.getErrCode());
        assertEquals("Payment transaction not found for the given transactionId.", ex.getMessage());
        assertLookupScopes(REF);
        verifyNoInteractions(fineGrainedEntitlementUtil, localPaymentApi);
    }

    @Test
    void unauthorizedDebitAccountStopsBeforeDownstreamQuery() {
        for (String type : new String[]{"INTERNAL", "TT"}) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            caller();
            stored(type, "Pending");
            doThrow(new ServiceException(600027)).when(fineGrainedEntitlementUtil).isAuthorizedAccount(PATH, ACCOUNT);
            ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF));
            assertEquals(600027, ex.getErrCode());
            verifyNoInteractions(localPaymentApi);
        }
    }

    @Test
    void callerResolutionErrorsDoNotQueryAnotherCompany() {
        for (int code : new int[]{500070, 500071}) {
            reset(paymentInternalMapper, payoutMapper, localPaymentApi, fineGrainedEntitlementUtil, uriInfo, partyResolver);
            when(partyResolver.resolvePartyId()).thenThrow(new ServiceException(code));
            assertEquals(code, assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF)).getErrCode());
            verifyNoInteractions(paymentInternalMapper, payoutMapper, localPaymentApi);
        }
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
