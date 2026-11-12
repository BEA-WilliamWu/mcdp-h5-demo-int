package com.hkbea.dsp.service;

import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ErrorMapping;
import jakarta.ws.rs.core.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Restricted to the internal CM subscription; Channel alone never authorizes a password reset. */
@Component
public class CmPasswordCallerGuard {
    @Value("${dsp.password.cm-client-id:}")
    private String clientId;
    @Value("${dsp.password.cm-client-secret:}")
    private String clientSecret;

    public void check(HttpHeaders headers) {
        if (headers == null || !"BCO".equalsIgnoreCase(headers.getHeaderString("Channel"))
                || !matches(clientId, headers.getHeaderString("X-IBM-Client-Id"))
                || !matches(clientSecret, headers.getHeaderString("X-IBM-Client-Secret"))) {
            throw new ParameterException("CM subscription", ErrorMapping.HEADER_INVALID_EXCEPTION);
        }
    }

    private boolean matches(String configured, String supplied) {
        return configured != null && !configured.isBlank() && supplied != null
                && MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
    }
}
