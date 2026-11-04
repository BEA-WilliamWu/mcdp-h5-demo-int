package com.hkbea.hth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hkbea.common.client.CommonClient;
import com.hkbea.common.exception.ExceptionConfig;
import com.hkbea.common.exception.ExceptionEntity;
import com.hkbea.common.exception.ExceptionResponseBuilder;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import feign.Request;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogErrorResponseTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesAcMessageThroughExistingSupportResponseAndClientDecoder(boolean serializeType) throws Exception {
        new HTHSupportErrorMapping();
        ParameterException denied = new ParameterException(HTHSupportErrorMapping.AUDIT_LOG_ACCESS_DENIED_EXCEPTION);
        ExceptionConfig config = new ExceptionConfig();
        config.setSerializationType(serializeType);
        ExceptionResponseBuilder builder = new ExceptionResponseBuilder();
        ReflectionTestUtils.setField(builder, "exceptionConfig", config);
        try (jakarta.ws.rs.core.Response response = builder.buildExceptionResponse(denied)) {
            assertEquals(417, response.getStatus(), "Keep the existing DSP error transport contract");
            ExceptionEntity entity = (ExceptionEntity) response.getEntity();
            String body = new ObjectMapper().writeValueAsString(entity);
            try (feign.Response wireResponse = feign.Response.builder()
                    .status(response.getStatus()).reason("Application error")
                    .request(Request.create(Request.HttpMethod.POST, "http://localhost/audit-log/v1",
                            Map.of(), null, StandardCharsets.UTF_8, null))
                    .body(body, StandardCharsets.UTF_8).build()) {
                Exception decoded = new DecoderClient().decode(wireResponse);
                ParameterException result = assertInstanceOf(ParameterException.class, decoded);
                assertEquals(500074, result.getErrCode());
                assertEquals("Access denied. You are authorized to view only your own audit logs.", result.getMessage());
                assertEquals(denied.getReferenceCode(), result.getReferenceCode());
            }
        }
    }

    private static class DecoderClient extends CommonClient {
        Exception decode(feign.Response response) {
            return new MyErrorDecoder() { }.decode("AuditLogApi#queryAuditLogs", response);
        }
        @Override public void setServiceUrl(String url) { }
        @Override public String getServiceUrl() { return "http://localhost"; }
        @Override public void setCommonHeader(CommonHeader header) { }
        @Override public CommonHeader getCommonHeader() { return null; }
    }
}
