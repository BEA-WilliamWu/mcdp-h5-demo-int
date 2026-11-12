package com.hkbea.dsp;

import com.hkbea.common.exception.DefaultExceptionMapper;
import com.hkbea.common.filter.RequestHeaderFilter;
import com.hkbea.common.filter.ResponseHeaderFilter;
import com.hkbea.dsp.service.CorporateTokenServiceImpl;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        // Password traffic must not expose ciphertext, keys or APIC credentials in HTTP debug logs.
        property(LoggingFeature.LOGGING_FEATURE_VERBOSITY_SERVER, LoggingFeature.Verbosity.HEADERS_ONLY);
        property(LoggingFeature.LOGGING_FEATURE_REDACT_HEADERS_SERVER,
                "Authorization;X-IBM-Client-Id;X-IBM-Client-Secret;Cookie;Set-Cookie");
        register(LoggingFeature.class);
        register(RequestHeaderFilter.class);
        register(ResponseHeaderFilter.class);

        packages(DefaultExceptionMapper.class.getPackageName());
        packages(CorporateTokenServiceImpl.class.getPackageName());
    }
}
