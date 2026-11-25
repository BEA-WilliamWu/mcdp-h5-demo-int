package com.hkbea.hth.pe3.api;

import com.hkbea.hth.pe3.model.Peechi11Request;
import com.hkbea.hth.pe3.model.Peechi11Response;
import com.hkbea.hth.pe3.model.Peechj7aRequest;
import com.hkbea.hth.pe3.model.Peechj7aResponse;

import jakarta.ws.rs.*;


/**
* Represents a collection of functions to interact with the API endpoints.
*/

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-08-05T14:23:54.418687200+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public interface Pe3Api {

    /**
     * getCustomerBasicInfo
     *
     * @param peechi11Request request body
     * @return OK
     */
    @POST
    @Path("/v1z/peechi11.v1")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    Peechi11Response getCustomerBasicInfo(Peechi11Request peechi11Request);

    /** Customer contact information sorted by address, queried by customer or account. */
    @POST
    @Path("/v1z/peechj7a.v1")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    Peechj7aResponse getCustomerContactInfo(Peechj7aRequest peechj7aRequest);

}
