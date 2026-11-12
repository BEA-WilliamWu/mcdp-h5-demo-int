package com.hkbea.dsp.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Objects;



@JsonTypeName("PublicKeyQueryResponse")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-11-11T14:33:41.773790700+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public class PublicKeyQueryResponse   {
  private String status;
  private String publicKey;

  public PublicKeyQueryResponse() {
  }

  @JsonCreator
  public PublicKeyQueryResponse(
    @JsonProperty(required = true, value = "status") String status,
    @JsonProperty("publicKey") String publicKey
  ) {
    this.status = status;
    this.publicKey = publicKey;
  }

  /**
   * 000000 indicates success.
   **/
  public PublicKeyQueryResponse status(String status) {
    this.status = status;
    return this;
  }

  
  @JsonProperty(required = true, value = "status")
  public String getStatus() {
    return status;
  }

  @JsonProperty(required = true, value = "status")
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * RSA public key encoded in Base64 format.
   **/
  public PublicKeyQueryResponse publicKey(String publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  
  @JsonProperty("publicKey")
  public String getPublicKey() {
    return publicKey;
  }

  @JsonProperty("publicKey")
  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }


  private String credentialStatus;
  @JsonProperty("credentialStatus")
  public String getCredentialStatus() { return credentialStatus; }
  @JsonProperty("credentialStatus")
  public void setCredentialStatus(String value) { credentialStatus = value; }

  private String protocol;
  @JsonProperty("protocol")
  public String getProtocol() { return protocol; }
  @JsonProperty("protocol")
  public void setProtocol(String value) { protocol = value; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PublicKeyQueryResponse publicKeyQueryResponse = (PublicKeyQueryResponse) o;
    return Objects.equals(this.status, publicKeyQueryResponse.status) &&
        Objects.equals(this.publicKey, publicKeyQueryResponse.publicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, publicKey);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicKeyQueryResponse {\n");
    
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    publicKey: ").append(toIndentedString(publicKey)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }


}

