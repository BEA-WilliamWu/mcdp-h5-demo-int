package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aTeleFaxO")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aTeleFaxO   {
  private String ehj7aCodCountryPhoneO;
  private String ehj7aCodAreaPhoneO;
  private String ehj7aNumTphO;
  private String ehj7aContRmkO;

  public Peechj7aTeleFaxO() {
  }

  /**
   **/
  public Peechj7aTeleFaxO ehj7aCodCountryPhoneO(String ehj7aCodCountryPhoneO) {
    this.ehj7aCodCountryPhoneO = ehj7aCodCountryPhoneO;
    return this;
  }


  @JsonProperty("ehj7aCodCountryPhoneO")
  public String getEhj7aCodCountryPhoneO() {
    return ehj7aCodCountryPhoneO;
  }

  @JsonProperty("ehj7aCodCountryPhoneO")
  public void setEhj7aCodCountryPhoneO(String ehj7aCodCountryPhoneO) {
    this.ehj7aCodCountryPhoneO = ehj7aCodCountryPhoneO;
  }

  /**
   **/
  public Peechj7aTeleFaxO ehj7aCodAreaPhoneO(String ehj7aCodAreaPhoneO) {
    this.ehj7aCodAreaPhoneO = ehj7aCodAreaPhoneO;
    return this;
  }


  @JsonProperty("ehj7aCodAreaPhoneO")
  public String getEhj7aCodAreaPhoneO() {
    return ehj7aCodAreaPhoneO;
  }

  @JsonProperty("ehj7aCodAreaPhoneO")
  public void setEhj7aCodAreaPhoneO(String ehj7aCodAreaPhoneO) {
    this.ehj7aCodAreaPhoneO = ehj7aCodAreaPhoneO;
  }

  /**
   **/
  public Peechj7aTeleFaxO ehj7aNumTphO(String ehj7aNumTphO) {
    this.ehj7aNumTphO = ehj7aNumTphO;
    return this;
  }


  @JsonProperty("ehj7aNumTphO")
  public String getEhj7aNumTphO() {
    return ehj7aNumTphO;
  }

  @JsonProperty("ehj7aNumTphO")
  public void setEhj7aNumTphO(String ehj7aNumTphO) {
    this.ehj7aNumTphO = ehj7aNumTphO;
  }

  /**
   **/
  public Peechj7aTeleFaxO ehj7aContRmkO(String ehj7aContRmkO) {
    this.ehj7aContRmkO = ehj7aContRmkO;
    return this;
  }


  @JsonProperty("ehj7aContRmkO")
  public String getEhj7aContRmkO() {
    return ehj7aContRmkO;
  }

  @JsonProperty("ehj7aContRmkO")
  public void setEhj7aContRmkO(String ehj7aContRmkO) {
    this.ehj7aContRmkO = ehj7aContRmkO;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aTeleFaxO peechj7aTeleFaxO = (Peechj7aTeleFaxO) o;
    return Objects.equals(this.ehj7aCodCountryPhoneO, peechj7aTeleFaxO.ehj7aCodCountryPhoneO) &&
        Objects.equals(this.ehj7aCodAreaPhoneO, peechj7aTeleFaxO.ehj7aCodAreaPhoneO) &&
        Objects.equals(this.ehj7aNumTphO, peechj7aTeleFaxO.ehj7aNumTphO) &&
        Objects.equals(this.ehj7aContRmkO, peechj7aTeleFaxO.ehj7aContRmkO);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aCodCountryPhoneO, ehj7aCodAreaPhoneO, ehj7aNumTphO, ehj7aContRmkO);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aTeleFaxO {\n");

    sb.append("    ehj7aCodCountryPhoneO: ").append(toIndentedString(ehj7aCodCountryPhoneO)).append("\n");
    sb.append("    ehj7aCodAreaPhoneO: ").append(toIndentedString(ehj7aCodAreaPhoneO)).append("\n");
    sb.append("    ehj7aNumTphO: ").append(toIndentedString(ehj7aNumTphO)).append("\n");
    sb.append("    ehj7aContRmkO: ").append(toIndentedString(ehj7aContRmkO)).append("\n");
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
