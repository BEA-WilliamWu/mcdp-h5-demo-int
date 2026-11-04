package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.SystemException;
import com.hkbea.hth.entity.AuditLogRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.mapper.AuditLogMapper;
import com.hkbea.hth.model.AuditLogRequest;
import com.hkbea.hth.model.AuditLogResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceImplTest {

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private com.hkbea.hth.utils.PartyResolver partyResolver;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset HK_ZONE_OFFSET = ZoneOffset.ofHours(8);

    @BeforeAll
    static void setupAll() {
        // Ensure HTHSupportErrorMapping static block is executed to populate MESSAGE_MAP
        new HTHSupportErrorMapping();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(partyResolver.resolveCloseId()).thenReturn("mockCloseId");
        when(partyResolver.resolveClientId()).thenReturn("client2");
        when(partyResolver.resolvePartyId()).thenReturn("test-party");
        auditLogService.setMaxQueryPeriodDays(180); // Explicitly set for unit tests
    }

    private AuditLogRequest createAuditLogRequest(Integer pageNumber, Integer pageSize, String messageId, String closeId, String clientId, String fromDateTime, String toDateTime, Integer lastNDays) {
        AuditLogRequest request = new AuditLogRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setMessageId(messageId);
        request.setCloseId(closeId);
        request.setClientId(clientId);
        request.setFromDateTime(fromDateTime);
        request.setToDateTime(toDateTime);
        request.setLastNDays(lastNDays);
        return request;
    }

    private AuditLogRecord createAuditLogRecord(String messageId, String closeId, String clientId, OffsetDateTime createTime) {
        AuditLogRecord record = new AuditLogRecord();
        record.setMessageId(messageId);
        record.setCloseId(closeId);
        record.setClientId(clientId);
        record.setClientIp("127.0.0.1");
        record.setChannel("WEB");
        record.setHttpMethod("GET");
        record.setRequestUri("/api/audit");
        record.setQueryString("param=test");
        record.setStatusCode(200);
        record.setDurationMs(100L);
        record.setCreateTime(createTime);
        return record;
    }


    @Test
    void queryAuditLogs_noRecordsFound() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, null, null, null);
        Page<AuditLogRecord> page = new Page<>(1, 10);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);

        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // When
        AuditLogResponse result = auditLogService.queryAuditLogs(request);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTotalRecords());
        assertTrue(result.getAuditLog() == null || result.getAuditLog().isEmpty());
        verify(auditLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void queryAuditLogs_returnsCorrectOutputStructure() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(HK_ZONE_OFFSET);
        String fromDateTime = now.minusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String toDateTime = now.minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, fromDateTime, toDateTime, null);
        List<AuditLogRecord> records = new ArrayList<>();
        records.add(createAuditLogRecord("msg1", "close1", "client1", now.minusDays(3)));

        Page<AuditLogRecord> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(1);

        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // When
        AuditLogResponse result = auditLogService.queryAuditLogs(request);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getPageNumber());
        assertEquals(10, result.getPageSize());
        assertEquals(1, result.getAuditLog().size());
        assertEquals("msg1", result.getAuditLog().get(0).getMessageId());
        assertNotNull(result.getFromDateTime());
        assertNotNull(result.getToDateTime());
        verify(auditLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void queryAuditLogs_mapperThrowsException() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, null, null, null);
        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("DB error"));

        // When & Then
        SystemException exception = assertThrows(SystemException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.AUDIT_LOG_SYSTEM_ERROR, exception.getErrCode());
        verify(auditLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void queryAuditLogs_invalidPageNo() {
        // Given
        AuditLogRequest request = createAuditLogRequest(0, 10, null, null, null, null, null, null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_invalidPageSize_tooSmall() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 0, null, null, null, null, null, null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_invalidPageSize_tooLarge() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 51, null, null, null, null, null, null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_invalidStartTimeFormat() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, "invalid-date", null, null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_PARAMETER_FORMAT, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_dateTimeRangeAndLastNDaysMutuallyExclusive() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, "2023-01-01T00:00:00+08:00", "2023-01-01T23:59:59+08:00", 7);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.DATE_RANGE_MUTUALLY_EXCLUSIVE, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_invalidEndTimeFormat() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, "2023-01-01T00:00:00+08:00", "invalid-date", null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_PARAMETER_FORMAT, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_startTimeAfterEndTime() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, "2023-01-02T00:00:00+08:00", "2023-01-01T00:00:00+08:00", null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_DATE_RANGE, exception.getErrCode());
    }

    @Test
    void queryAuditLogs_queryPeriodExceedsMaxRange() {
        // Given
        LocalDate today = LocalDate.now();
        String fromDateTime = today.minusDays(181).atStartOfDay(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String toDateTime = today.minusDays(180).atStartOfDay(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, fromDateTime, toDateTime, null);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
        assertTrue(exception.getMessage().contains("fromDateTime"));
        assertTrue(exception.getMessage().contains("last 180 days"));
    }

    @Test
    void queryAuditLogs_invalidLastNDays_zero() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, null, null, 0);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
        assertTrue(exception.getMessage().contains("lastNDays"));
        assertTrue(exception.getMessage().contains("1-180"));
    }

    @Test
    void queryAuditLogs_invalidLastNDays_tooLarge() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, null, null, 181);

        // When & Then
        ParameterException exception = assertThrows(ParameterException.class, () -> auditLogService.queryAuditLogs(request));
        assertEquals(HTHSupportErrorMapping.INVALID_VALUE_RANGE, exception.getErrCode());
        assertTrue(exception.getMessage().contains("lastNDays"));
        assertTrue(exception.getMessage().contains("1-180"));
    }

    @Test
    void queryAuditLogs_validLastNDays() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, null, null, null, null, null, 7);
        List<AuditLogRecord> records = new ArrayList<>();
        records.add(createAuditLogRecord("msg1", "close1", "client1", OffsetDateTime.now().minusDays(3)));

        Page<AuditLogRecord> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(1);

        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // When
        AuditLogResponse result = auditLogService.queryAuditLogs(request);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getAuditLog().size());
        assertEquals("msg1", result.getAuditLog().get(0).getMessageId());
        assertNotNull(result.getFromDateTime());
        assertNotNull(result.getToDateTime());
        verify(auditLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void queryAuditLogs_withPartialSearchCriteria() {
        // Given
        AuditLogRequest request = createAuditLogRequest(1, 10, "msg2", null, "client2", null, null, null);
        List<AuditLogRecord> records = new ArrayList<>();
        records.add(createAuditLogRecord("msg2", "closeX", "client2", OffsetDateTime.now().minusDays(2)));

        Page<AuditLogRecord> page = new Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(1);

        when(auditLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // When
        AuditLogResponse result = auditLogService.queryAuditLogs(request);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getAuditLog().size());
        assertEquals("msg2", result.getAuditLog().get(0).getMessageId());
        verify(auditLogMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }
}
