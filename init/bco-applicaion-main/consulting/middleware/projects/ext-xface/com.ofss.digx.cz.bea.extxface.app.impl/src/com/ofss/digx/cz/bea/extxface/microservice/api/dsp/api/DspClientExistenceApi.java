package com.ofss.digx.cz.bea.extxface.microservice.api.dsp.api;

import com.hkbea.dsp.model.ClientValidationParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.Map;

/** Caller-side contract until the installed DSP SDK includes /client/v1/exists. */
public interface DspClientExistenceApi {
    @POST
    @Path("/client/v1/exists")
    @Consumes("application/json")
    @Produces("application/json")
    Map<String, Object> checkClientIdExists(ClientValidationParam request);
}
