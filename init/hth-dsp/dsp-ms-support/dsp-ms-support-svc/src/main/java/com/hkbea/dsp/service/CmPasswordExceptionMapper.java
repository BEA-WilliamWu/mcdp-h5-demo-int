package com.hkbea.dsp.service;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.springframework.stereotype.Component;

/** Preserve explicit pre-write rejections; do not route them through the general 490 mapper. */
@Component
@Provider
public class CmPasswordExceptionMapper implements ExceptionMapper<CmPasswordService.Rejected> {
    @Override
    public Response toResponse(CmPasswordService.Rejected failure) {
        return failure.getResponse();
    }
}
