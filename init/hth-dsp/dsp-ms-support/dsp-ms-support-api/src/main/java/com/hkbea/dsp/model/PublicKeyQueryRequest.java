package com.hkbea.dsp.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Objects;



@JsonTypeName("PublicKeyQueryRequest")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-11-11T14:33:41.773790700+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public class PublicKeyQueryRequest   {
  private String hashValue;

  public PublicKeyQueryRequest() {
  }

  @JsonCreator
  public PublicKeyQueryRequest(
    @JsonProperty(required = true, value = "hashValue") String hashValue
  ) {
    this.hashValue = hashValue;
  }

  /**
   * Hash value in UTF-8 format.
   **/
  public PublicKeyQueryRequest hashValue(String hashValue) {
    this.hashValue = hashValue;
    return this;
  }

  
  @JsonProperty(required = true, value = "hashValue")
  public String getHashValue() {
    return hashValue;
  }

  @JsonProperty(required = true, value = "hashValue")
  public void setHashValue(String hashValue) {
    this.hashValue = hashValue;
  }


  private String protocol;
  @JsonProperty("protocol")
  public String getProtocol() { return protocol; }
  @JsonProperty("protocol")
  public void setProtocol(String value) { protocol = value; }

  private String clientId;
  @JsonProperty("clientId")
  public String getClientId() { return clientId; }
  @JsonProperty("clientId")
  public void setClientId(String value) { clientId = value; }

  private String closeId;
  @JsonProperty("closeId")
  public String getCloseId() { return closeId; }
  @JsonProperty("closeId")
  public void setCloseId(String value) { closeId = value; }

  private String requestId;
  @JsonProperty("requestId")
  public String getRequestId() { return requestId; }
  @JsonProperty("requestId")
  public void setRequestId(String value) { requestId = value; }

  private Boolean queryOnly;
  @JsonProperty("queryOnly")
  public Boolean getQueryOnly() { return queryOnly; }
  @JsonProperty("queryOnly")
  public void setQueryOnly(Boolean value) { queryOnly = value; }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PublicKeyQueryRequest publicKeyQueryRequest = (PublicKeyQueryRequest) o;
    return Objects.equals(this.hashValue, publicKeyQueryRequest.hashValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hashValue);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublicKeyQueryRequest {\n");
    
    sb.append("    hashValue: ").append(toIndentedString(hashValue)).append("\n");
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

