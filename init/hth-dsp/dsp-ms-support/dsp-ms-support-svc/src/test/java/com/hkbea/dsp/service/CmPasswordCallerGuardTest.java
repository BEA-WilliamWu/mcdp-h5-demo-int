package com.hkbea.dsp.service;
import com.hkbea.common.exception.ParameterException;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CmPasswordCallerGuardTest {
    @Test void subscriptionAndChannelRequired() {
        CmPasswordCallerGuard guard = new CmPasswordCallerGuard();
        HttpHeaders headers=mock(HttpHeaders.class);
        when(headers.getHeaderString("Channel")).thenReturn("BCO");
        assertThrows(ParameterException.class, () -> guard.check(headers));
        ReflectionTestUtils.setField(guard,"clientId","cm-id");
        ReflectionTestUtils.setField(guard,"clientSecret","cm-secret");
        when(headers.getHeaderString("X-IBM-Client-Id")).thenReturn("cm-id");
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("wrong-secret");
        assertThrows(ParameterException.class, () -> guard.check(headers));
        when(headers.getHeaderString("X-IBM-Client-Secret")).thenReturn("cm-secret");
        assertDoesNotThrow(() -> guard.check(headers));
        when(headers.getHeaderString("Channel")).thenReturn("HTH");
        assertThrows(ParameterException.class, () -> guard.check(headers));
        assertThrows(ParameterException.class, () -> guard.check(null));
    }
}
