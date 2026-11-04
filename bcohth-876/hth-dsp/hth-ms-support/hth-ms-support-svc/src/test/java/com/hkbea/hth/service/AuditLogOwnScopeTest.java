package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.entity.AuditLogRecord;
import com.hkbea.hth.entity.Management;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.mapper.AuditLogMapper;
import com.hkbea.hth.mapper.HthManagementMapper;
import com.hkbea.hth.model.AuditLogRequest;
import com.hkbea.hth.utils.PartyResolver;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogOwnScopeTest {
    private static final String CLOSE_ID = "API01@TEST-PARTY";
    private static final String CLIENT_ID = "test-client";
    private static final String DENIED = "Access denied. You are authorized to view only your own audit logs.";
    @InjectMocks private AuditLogServiceImpl service;
    @Mock private PartyResolver resolver;
    @Mock private AuditLogMapper auditMapper;
    @Mock private HthManagementMapper managementMapper;

    @BeforeAll
    static void initializeMetadata() {
        new HTHSupportErrorMapping();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "audit-test");
        TableInfoHelper.initTableInfo(assistant, AuditLogRecord.class);
        TableInfoHelper.initTableInfo(assistant, Management.class);
    }

    @BeforeEach
    void setUp() {
        service.setMaxQueryPeriodDays(180);
    }

    private AuditLogRequest request() {
        AuditLogRequest request = new AuditLogRequest();
        request.setPageNumber(1);
        request.setPageSize(10);
        request.setLastNDays(7);
        return request;
    }

    private void identity() {
        when(resolver.resolveCloseId()).thenReturn(CLOSE_ID);
        when(resolver.resolveClientId()).thenReturn(CLIENT_ID);
    }

    private void successfulQuery() {
        Page<AuditLogRecord> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        when(auditMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(empty);
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "blank", "own"})
    void bindsEverySuccessfulQueryToTokenCompanyAndUser(String suppliedScope) {
        identity();
        successfulQuery();
        AuditLogRequest request = request();
        request.setMessageId("test-message");
        if ("blank".equals(suppliedScope)) {
            request.setCloseId("  ");
            request.setClientId("  ");
        } else if ("own".equals(suppliedScope)) {
            request.setCloseId(" " + CLOSE_ID + " ");
            request.setClientId(" " + CLIENT_ID + " ");
        }
        assertEquals(0, service.queryAuditLogs(request).getTotalRecords());
        assertEquals(CLOSE_ID, request.getCloseId());
        assertEquals(CLIENT_ID, request.getClientId());
        verifyNoInteractions(managementMapper);
        ArgumentCaptor<LambdaQueryWrapper<AuditLogRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(auditMapper).selectPage(any(Page.class), query.capture());
        String sql = query.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("CLOSE_ID ="));
        assertTrue(sql.contains("CLIENT_ID ="));
        assertTrue(sql.contains("MESSAGE_ID ="));
        assertTrue(sql.contains("ORDER BY CREATE_TIME DESC"));
        assertTrue(query.getValue().getParamNameValuePairs().values().containsAll(List.of(CLOSE_ID, CLIENT_ID, "test-message")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"API02@TEST-PARTY", "API01@OTHER-PARTY", "api01@TEST-PARTY"})
    void explicitlyRequestingAnotherUserIsRejectedBeforeDatabaseAccess(String otherUser) {
        identity();
        AuditLogRequest request = request();
        request.setCloseId(otherUser);
        ParameterException error = assertThrows(ParameterException.class, () -> service.queryAuditLogs(request));
        assertEquals(500074, error.getErrCode());
        assertEquals(DENIED, error.getMessage());
        assertEquals(otherUser, request.getCloseId(), "Do not silently rewrite an unauthorized request");
        verifyNoInteractions(auditMapper, managementMapper);
    }

    @Test
    void explicitlyRequestingAnotherClientIsRejected() {
        identity();
        AuditLogRequest request = request();
        request.setClientId("other-client");
        ParameterException error = assertThrows(ParameterException.class, () -> service.queryAuditLogs(request));
        assertEquals(500074, error.getErrCode());
        assertEquals(DENIED, error.getMessage());
        verifyNoInteractions(auditMapper, managementMapper);
    }

    @Test
    void missingTokenUserCannotBecomeAnUnscopedQuery() {
        when(resolver.resolveCloseId()).thenThrow(new ServiceException(HTHSupportErrorMapping.INVALID_ACCESS_TOKEN_EXCEPTION));
        assertThrows(ServiceException.class, () -> service.queryAuditLogs(request()));
        verifyNoInteractions(auditMapper, managementMapper);
    }

    @Test
    void missingTokenClientCannotBecomeAnUnscopedQuery() {
        when(resolver.resolveCloseId()).thenReturn(CLOSE_ID);
        when(resolver.resolveClientId()).thenThrow(new ServiceException(HTHSupportErrorMapping.INVALID_ACCESS_TOKEN_EXCEPTION));
        assertThrows(ServiceException.class, () -> service.queryAuditLogs(request()));
        verifyNoInteractions(auditMapper, managementMapper);
    }

    @Test
    void operationsQueryRetainsItsExistingPartyScope() {
        Management management = new Management();
        management.setUamClientId("operations-client");
        when(managementMapper.selectOne(any())).thenReturn(management);
        successfulQuery();
        AuditLogRequest request = request();
        assertDoesNotThrow(() -> service.queryAuditLogsByPartyId("operations-party", request));
        assertEquals("operations-client", request.getClientId());
        verifyNoInteractions(resolver);
    }
}
