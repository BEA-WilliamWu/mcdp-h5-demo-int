package com.hkbea.dsp.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("PasswordModificationRequest")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2026-11-11T14:33:41.773790700+08:00[Asia/Shanghai]", comments = "Generator version: 7.17.0")
public class PasswordModificationRequest   {
  public enum FunctionEnum {

    A(String.valueOf("A")), M(String.valueOf("M"));


    private String value;

    FunctionEnum (String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    /**
     * Convert a String into String, as specified in the
     * <a href="https://download.oracle.com/otndocs/jcp/jaxrs-2_0-fr-eval-spec/index.html">See JAX RS 2.0 Specification, section 3.2, p. 12</a>
     */
    public static FunctionEnum fromString(String s) {
        for (FunctionEnum b : FunctionEnum.values()) {
            // using Objects.toString() to be safe if value type non-object type
            // because types like 'int' etc. will be auto-boxed
            if (java.util.Objects.toString(b.value).equals(s)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected string value '" + s + "'");
    }

    @JsonCreator
    public static FunctionEnum fromValue(String value) {
        for (FunctionEnum b : FunctionEnum.values()) {
            if (b.value.equals(value)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
}

  private FunctionEnum function;
  private String oldEncryptedPassword;
  private String newEncryptedPassword;
  private String randomNumber;
  private String closeId;
  private String clientId;

  public PasswordModificationRequest() {
  }

  @JsonCreator
  public PasswordModificationRequest(
    @JsonProperty(required = true, value = "function") FunctionEnum function,
    @JsonProperty(required = true, value = "newEncryptedPassword") String newEncryptedPassword,
    @JsonProperty(required = true, value = "randomNumber") String randomNumber,
    @JsonProperty(required = true, value = "closeId") String closeId,
    @JsonProperty(required = true, value = "clientId") String clientId
  ) {
    this.function = function;
    this.newEncryptedPassword = newEncryptedPassword;
    this.randomNumber = randomNumber;
    this.closeId = closeId;
    this.clientId = clientId;
  }

  /**
   * Password operation type.  A - Add password  M - Modify password 
   **/
  public PasswordModificationRequest function(FunctionEnum function) {
    this.function = function;
    return this;
  }

  
  @JsonProperty(required = true, value = "function")
  public FunctionEnum getFunction() {
    return function;
  }

  @JsonProperty(required = true, value = "function")
  public void setFunction(FunctionEnum function) {
    this.function = function;
  }

  /**
   * Old password encrypted by:  RSA/ECB/OAEPWithSHA-256AndMGF1Padding  Base64 encoded encrypted bytes.  Deprecated compatibility field; ignored. CM must validate an approved HTH Code for function M.
   **/
  public PasswordModificationRequest oldEncryptedPassword(String oldEncryptedPassword) {
    this.oldEncryptedPassword = oldEncryptedPassword;
    return this;
  }

  
  @JsonProperty("oldEncryptedPassword")
  public String getOldEncryptedPassword() {
    return oldEncryptedPassword;
  }

  @JsonProperty("oldEncryptedPassword")
  public void setOldEncryptedPassword(String oldEncryptedPassword) {
    this.oldEncryptedPassword = oldEncryptedPassword;
  }

  /**
   * New password encrypted by:  RSA/ECB/OAEPWithSHA-256AndMGF1Padding  Base64(raw SHA-256(UTF-8(password))) must first be converted to ASCII bytes. OAEP and MGF1 both use SHA-256 with an empty label.  Base64 encoded encrypted bytes.
   **/
  public PasswordModificationRequest newEncryptedPassword(String newEncryptedPassword) {
    this.newEncryptedPassword = newEncryptedPassword;
    return this;
  }

  
  @JsonProperty(required = true, value = "newEncryptedPassword")
  public String getNewEncryptedPassword() {
    return newEncryptedPassword;
  }

  @JsonProperty(required = true, value = "newEncryptedPassword")
  public void setNewEncryptedPassword(String newEncryptedPassword) {
    this.newEncryptedPassword = newEncryptedPassword;
  }

  /**
   * 6-digit random number in UTF-8 format.
   **/
  public PasswordModificationRequest randomNumber(String randomNumber) {
    this.randomNumber = randomNumber;
    return this;
  }

  
  @JsonProperty(required = true, value = "randomNumber")
  public String getRandomNumber() {
    return randomNumber;
  }

  @JsonProperty(required = true, value = "randomNumber")
  public void setRandomNumber(String randomNumber) {
    this.randomNumber = randomNumber;
  }

  /**
   * Close ID in UTF-8 format.
   **/
  public PasswordModificationRequest closeId(String closeId) {
    this.closeId = closeId;
    return this;
  }

  
  @JsonProperty(required = true, value = "closeId")
  public String getCloseId() {
    return closeId;
  }

  @JsonProperty(required = true, value = "closeId")
  public void setCloseId(String closeId) {
    this.closeId = closeId;
  }

  /**
   * Client ID in UTF-8 format.
   **/
  public PasswordModificationRequest clientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  
  @JsonProperty(required = true, value = "clientId")
  public String getClientId() {
    return clientId;
  }

  @JsonProperty(required = true, value = "clientId")
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PasswordModificationRequest passwordModificationRequest = (PasswordModificationRequest) o;
    return Objects.equals(this.function, passwordModificationRequest.function) &&
        Objects.equals(this.oldEncryptedPassword, passwordModificationRequest.oldEncryptedPassword) &&
        Objects.equals(this.newEncryptedPassword, passwordModificationRequest.newEncryptedPassword) &&
        Objects.equals(this.randomNumber, passwordModificationRequest.randomNumber) &&
        Objects.equals(this.closeId, passwordModificationRequest.closeId) &&
        Objects.equals(this.clientId, passwordModificationRequest.clientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(function, oldEncryptedPassword, newEncryptedPassword, randomNumber, closeId, clientId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PasswordModificationRequest {\n");
    
    sb.append("    function: ").append(toIndentedString(function)).append("\n");
    sb.append("    oldEncryptedPassword: ").append("[REDACTED]").append("\n");
    sb.append("    newEncryptedPassword: ").append("[REDACTED]").append("\n");
    sb.append("    randomNumber: ").append("[REDACTED]").append("\n");
    sb.append("    closeId: ").append(toIndentedString(closeId)).append("\n");
    sb.append("    clientId: ").append(toIndentedString(clientId)).append("\n");
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

