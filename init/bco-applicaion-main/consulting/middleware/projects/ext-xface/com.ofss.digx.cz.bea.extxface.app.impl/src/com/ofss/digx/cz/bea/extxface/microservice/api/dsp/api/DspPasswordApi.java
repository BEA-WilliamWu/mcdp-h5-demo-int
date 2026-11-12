package com.ofss.digx.cz.bea.extxface.microservice.api.dsp.api;

import feign.Response;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.Map;

/**
 * Caller-side declarations of the provider's two password endpoints.
 * The installed DSP SDK predates PasswordApi. Keep its JAX-RS/Feign calling style
 * without replacing the shared SDK or changing the provider's request contract.
 * Responses stay raw so the adapter can enforce HTTP 200 and provider status 000000.
 */
public interface DspPasswordApi {
    @POST
    @Path("/password/v1/public-key")
    @Consumes("application/json")
    @Produces("application/json")
    Response queryPublicKey(Map<String, Object> request);

    @POST
    @Path("/password/v1/modification")
    @Consumes("application/json")
    @Produces("application/json")
    Response modifyPassword(Map<String, Object> request);
}
