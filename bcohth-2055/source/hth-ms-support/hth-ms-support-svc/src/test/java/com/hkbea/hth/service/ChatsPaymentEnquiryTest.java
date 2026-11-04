package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.PaymentPayoutRecord;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BCOH2H-2055: a successful CHATS posting may still receive a settlement outcome. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatsPaymentEnquiryTest {
    private static final String PAYMENT_ID = "HTH20261104100000123456";
    @InjectMocks private PaymentServiceImpl service;
    @Mock private LocalPaymentApi localPaymentApi;
    @Mock private PaymentInternalMapper paymentInternalMapper;
    @Mock private HthNwPaymentPayoutMapper payoutMapper;
    @Mock private PartyResolver partyResolver;
    @Mock private FineGrainedEntitlementUtil fineGrainedEntitlementUtil;
    @Mock private UriInfo uriInfo;
    private PaymentPayoutRecord stored;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "chats-enquiry-test"),
                PaymentPayoutRecord.class);
        stored = record("SUCCESS", false);
        when(partyResolver.resolvePartyId()).thenReturn("PARTY-1");
        when(uriInfo.getPath()).thenReturn("/payment/v1/transaction");
        when(payoutMapper.selectOne(any())).thenAnswer(invocation -> stored);
        when(payoutMapper.update(any(PaymentPayoutRecord.class), any())).thenReturn(1);
    }

    @Test
    void successfulPosting_laterHkiclRejection_isPersistedAndStopsFurtherQueries() {
        var response = result(PaymentResultQueryResponse.StatusEnum.FAILED, true);
        response.getData().getFirst().setStatusReason("INVALID_VALUE_DATE");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);

        PaymentTransaction detail = service.enquireTransactionRecord(PAYMENT_ID);

        assertEquals(PaymentTransaction.StatusEnum.REJECTED, detail.getStatus());
        assertEquals("INVALID_VALUE_DATE", detail.getRejectMessage());
        PaymentPayoutRecord update = capturedUpdate();
        assertEquals(com.hkbea.hth.model.Payment.StatusEnum.REJECTED.value(), update.getStatus());
        assertEquals("FAILED", update.getIpeStatus());
        assertEquals(true, update.getIpeTerminal());
        assertEquals("INVALID_VALUE_DATE", update.getIpeStatusReason());
        assertEquals(new BigDecimal("100.00"), detail.getTransferAmount());
        assertEquals(PaymentTransaction.StatusEnum.REJECTED,
                service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        verify(localPaymentApi, times(1)).queryPaymentResults(any());
        verify(payoutMapper, times(1)).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    void unchangedSuccess_withNewTerminalFlag_isPersisted() {
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true));
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        assertEquals(true, capturedUpdate().getIpeTerminal());
        service.enquireTransactionRecord(PAYMENT_ID);
        verify(localPaymentApi, times(1)).queryPaymentResults(any());
    }

    @Test
    void acceptedPayment_becomesSuccessfulButRemainsRefreshable() {
        stored = record("PROCESSING", false);
        var posting = result(PaymentResultQueryResponse.StatusEnum.SUCCESS, false);
        var settled = result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true);
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(posting, settled);
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        assertEquals(false, stored.getIpeTerminal());
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        assertEquals(true, stored.getIpeTerminal());
        verify(localPaymentApi, times(2)).queryPaymentResults(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "REJECTED", "PROCESSING"})
    void explicitTerminalFlag_stopsPollingRegardlessOfStatus(String status) {
        stored = record(status, true);
        service.enquireTransactionRecord(PAYMENT_ID);
        verifyNoInteractions(localPaymentApi);
        verify(payoutMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PROCESSING", ""})
    void unknownFinalityOnPendingLegacyRow_canBeRefreshed(String status) {
        stored = record(status, null);
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true));
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        assertEquals(true, capturedUpdate().getIpeTerminal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "REJECTED"})
    void unknownFinalityOnLegacyCompletedRow_preservesExistingBehaviour(String status) {
        stored = record(status, null);
        service.enquireTransactionRecord(PAYMENT_ID);
        verifyNoInteractions(localPaymentApi);
    }

    @Test
    void missingTerminalInLaterResponse_preservesKnownNonTerminalState() {
        var response = result(PaymentResultQueryResponse.StatusEnum.SUCCESS, null);
        response.getData().getFirst().setStatusReason("AWAITING_SETTLEMENT");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);
        service.enquireTransactionRecord(PAYMENT_ID);
        assertNull(capturedUpdate().getIpeTerminal()); // sparse update retains the stored false
        assertEquals(false, stored.getIpeTerminal());
        service.enquireTransactionRecord(PAYMENT_ID);
        verify(localPaymentApi, times(2)).queryPaymentResults(any());
        verify(payoutMapper, times(1)).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    void identicalResult_doesNotWriteAgain() {
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.SUCCESS, false));
        service.enquireTransactionRecord(PAYMENT_ID);
        service.enquireTransactionRecord(PAYMENT_ID);
        verify(localPaymentApi, times(2)).queryPaymentResults(any());
        verify(payoutMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    void updatedReasonAndReference_areSavedEvenWhenStatusIsUnchanged() {
        var response = result(PaymentResultQueryResponse.StatusEnum.SUCCESS, false);
        response.getData().getFirst().setStatusReason("CHATS_SUBMITTED");
        response.getData().getFirst().setIpeReferenceId("IPE-NEW-REF");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);
        service.enquireTransactionRecord(PAYMENT_ID);
        PaymentPayoutRecord update = capturedUpdate();
        assertEquals("CHATS_SUBMITTED", update.getIpeStatusReason());
        assertEquals("IPE-NEW-REF", update.getIpeRefNo());
        assertEquals("IPE-NEW-REF", stored.getIpeRefNo());
    }

    @Test
    void explicitErrorMessage_takesPrecedenceOverStatusReason() {
        var response = result(PaymentResultQueryResponse.StatusEnum.FAILED, true);
        response.getData().getFirst().setStatusReason("HKICL_REJECTED");
        response.getData().getFirst().setErrorMessage("Invalid value date supplied");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);
        assertEquals("Invalid value date supplied", service.enquireTransactionRecord(PAYMENT_ID).getRejectMessage());
    }

    @Test
    void rejectionWithoutDetails_usesGenericReason() {
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.FAILED, true));
        assertEquals("Rejected by payment engine (no detail returned)",
                service.enquireTransactionRecord(PAYMENT_ID).getRejectMessage());
    }

    @Test
    void queryTimeout_keepsSuccessfulPostingAndNonTerminalFlag() {
        when(localPaymentApi.queryPaymentResults(any())).thenThrow(new RuntimeException("IPE timeout"));
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        assertEquals(false, stored.getIpeTerminal());
        verify(payoutMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    void inconclusiveQuery_keepsStoredState() {
        var missingStatus = result(null, true);
        var failedEnvelope = result(PaymentResultQueryResponse.StatusEnum.FAILED, true);
        failedEnvelope.setCode("99");
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(null, missingStatus, failedEnvelope);
        for (int i = 0; i < 3; i++) {
            assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
            assertEquals(false, stored.getIpeTerminal());
        }
        verify(payoutMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"OTHER-PAYMENT"})
    void missingOrMismatchedResultId_isIgnored(String transactionId) {
        var response = result(PaymentResultQueryResponse.StatusEnum.FAILED, true);
        response.getData().getFirst().setTransactionId(transactionId);
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        verify(payoutMapper, never()).update(any(PaymentPayoutRecord.class), any());
    }

    @Test
    void queryRequestsTheUniqueIpePaymentId() {
        service.enquireTransactionRecord(PAYMENT_ID);
        verify(localPaymentApi).queryPaymentResults(argThat(request -> PAYMENT_ID.equals(request.getTransactionId())));
    }

    @Test
    void staleQuery_cannotOverwriteAConcurrentFinalResult() {
        var response = result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true);
        when(localPaymentApi.queryPaymentResults(any())).thenReturn(response);
        when(payoutMapper.update(any(PaymentPayoutRecord.class), any())).thenReturn(0);
        PaymentPayoutRecord latest = record("REJECTED", true);
        latest.setRejectMessage("HKICL rejected");
        when(payoutMapper.selectById("ROW-1")).thenReturn(latest);

        PaymentTransaction detail = service.enquireTransactionRecord(PAYMENT_ID);

        assertEquals(PaymentTransaction.StatusEnum.REJECTED, detail.getStatus());
        assertEquals("HKICL rejected", detail.getRejectMessage());
        @SuppressWarnings("rawtypes")
        var condition = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(payoutMapper).update(any(PaymentPayoutRecord.class), condition.capture());
        String sql = condition.getValue().getSqlSegment();
        assertTrue(sql.contains("STATUS ="));
        assertTrue(sql.contains("IPE_TERMINAL ="));
        assertTrue(sql.contains("LAST_UPDATED_DATE ="));
        assertTrue(condition.getValue().getParamNameValuePairs().containsValue(false));
        assertTrue(condition.getValue().getParamNameValuePairs().containsValue(stored.getLastUpdatedDate()));
    }

    @Test
    void nonFailedResult_clearsAnEarlierRejectionReason() {
        stored = record("REJECTED", false);
        stored.setRejectMessage("Earlier rejection");
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true));
        PaymentTransaction detail = service.enquireTransactionRecord(PAYMENT_ID);
        assertNull(detail.getRejectMessage());
        @SuppressWarnings("rawtypes")
        var condition = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(payoutMapper).update(any(PaymentPayoutRecord.class), condition.capture());
        assertTrue(condition.getValue().getSqlSet().contains("REJECT_MESSAGE="));
        assertTrue(condition.getValue().getParamNameValuePairs().containsValue(null));
    }

    @Test
    void fpsFinalityHandling_isUnchanged() {
        stored.setPaymentType("FPS");
        service.enquireTransactionRecord(PAYMENT_ID);
        verifyNoInteractions(localPaymentApi);
        stored.setStatus("PROCESSING");
        stored.setIpeTerminal(true);
        when(localPaymentApi.queryPaymentResults(any()))
                .thenReturn(result(PaymentResultQueryResponse.StatusEnum.SUCCESS, true));
        assertEquals(PaymentTransaction.StatusEnum.SUCCESS, service.enquireTransactionRecord(PAYMENT_ID).getStatus());
        verify(payoutMapper).updateById(any(PaymentPayoutRecord.class));
    }

    private PaymentPayoutRecord capturedUpdate() {
        var update = ArgumentCaptor.forClass(PaymentPayoutRecord.class);
        verify(payoutMapper).update(update.capture(), any());
        return update.getValue();
    }

    private PaymentPayoutRecord record(String status, Boolean terminal) {
        var record = new PaymentPayoutRecord();
        record.setId("ROW-1");
        record.setPaymentId(PAYMENT_ID);
        record.setPartyId("PARTY-1");
        record.setPaymentType("CHATS");
        record.setStatus(status);
        record.setIpeStatus("REJECTED".equals(status) ? "FAILED" : status);
        record.setIpeTerminal(terminal);
        record.setIpeRefNo("IPE-1");
        record.setDebitAccountId("01516868006864");
        record.setInstdAmount(new BigDecimal("100.00"));
        record.setInstdCurrency("HKD");
        record.setLastUpdatedDate(LocalDateTime.of(2026, 11, 4, 10, 0));
        return record;
    }

    private PaymentResultQueryApiResponse result(PaymentResultQueryResponse.StatusEnum status, Boolean terminal) {
        var result = new PaymentResultQueryResponse();
        result.setTransactionId(PAYMENT_ID);
        result.setIpeReferenceId("IPE-1");
        result.setStatus(status);
        result.setTerminal(terminal);
        var response = new PaymentResultQueryApiResponse();
        response.setCode(PaymentConstants.API_SUCCESS_CODE);
        response.setData(List.of(result));
        return response;
    }
}
