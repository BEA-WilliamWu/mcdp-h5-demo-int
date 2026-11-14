package com.hkbea.dsp.api;

import com.hkbea.dsp.model.PasswordModificationRequest;
import com.hkbea.dsp.model.PasswordModificationResponse;
import com.hkbea.dsp.model.PublicKeyQueryRequest;
import com.hkbea.dsp.model.PublicKeyQueryResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;


/**
* Represents a collection of functions to interact with the API endpoints.
*/

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-11-11T14:33:41.773790700+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public interface PasswordApi {

    /**
     * Add or modify user password.  Encryption Information:  Algorithm: `RSA`  Algorithm Transformation: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`  Password Rules:  - Base64(raw SHA-256(UTF-8(password))) must be converted into ASCII bytes; OAEP and MGF1 both use SHA-256, empty label. - RSA encryption must be performed on the UTF-8 bytes. - Encrypted bytes must be encoded using Base64. - This endpoint accepts only the internal CM subscription. M resets with an approved Code validated by CM; no old password is required. For legacy CM users, M creates the first credential if no record exists for the exact clientId + closeId pair; inactive records are not reactivated. - newEncryptedPassword is always mandatory.
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
