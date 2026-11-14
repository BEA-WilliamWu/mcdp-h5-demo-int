package com.hkbea.dsp.api;

import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PasswordModificationResponse;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.model.PublicKeyQueryResponse;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;


import java.io.InputStream;
import java.util.Map;
import java.util.List;


/**
* Represents a collection of functions to interact with the API endpoints.
*/

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-11-13T17:02:37.540287100+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public interface PasswordApi {

    /**
     * Add or modify user password.  Encryption Information:  Algorithm: `RSA`  Algorithm Transformation: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`  Password Rules:  - Base64(raw SHA-256(UTF-8(password))) must first be converted into ASCII bytes. OAEP and MGF1 both use SHA-256 with an empty label. - RSA encryption must be performed on the UTF-8 bytes. - Encrypted bytes must be encoded using Base64. - No old password is required. CM must first validate the approved HTH reset Code. M creates the first credential only when the exact clientId + closeId pair has no records; inactive records are not reactivated. - newEncryptedPassword is always mandatory.
     *
     * @param passwordModificationRequest 
     * @return Password modification completed.
     * @return Parameter Exception.  Possible Error Codes: - **500050**: Request body is missing or empty. - **500051**: Mandatory parameter is missing or empty. 
     * @return Service Exception.  Possible Error Codes: - **600052**: Failed to verify the password_signature. 
     * @return System Exception. 
     * @return Other / General Exception. 
     */
    @POST
    @Path("/password/v1/modification")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    PasswordModificationResponse modifyPassword(PasswordModificationRequest passwordModificationRequest);


    /**
     * Query the RSA public key used for password encryption. 
     *
     * @param publicKeyQueryRequest 
     * @return Public key query completed.
     * @return Parameter Exception.  Possible Error Codes: - **500050**: Request body is missing or empty. - **500051**: The mandatory parameter 'hashValue' is missing or empty. 
     * @return Service Exception.  Possible Error Codes: - **600052**: Failed to verify the password_signature. 
     * @return System Exception. 
     * @return Other / General Exception. 
     */
    @POST
    @Path("/password/v1/public-key")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    PublicKeyQueryResponse queryPublicKey(PublicKeyQueryRequest publicKeyQueryRequest);

}
