package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aAddressO;
import com.hkbea.hth.pe3.model.Peechj7aEmailO;
import com.hkbea.hth.pe3.model.Peechj7aTeleFaxO;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aContact")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aContact   {
  private String ehj7aNumCusO;
  private Integer ehj7aNumLadr1O;
  private String ehj7aCodIdO;
  private String ehj7aKeyIdO;
  private String ehj7aCodIssCtryO;
  private String ehj7aTypCntaadrO;
  private String ehj7aCodAttnO;
  private String ehj7aDesPersonO;
  private String ehj7aLngSpokenO;
  private Peechj7aAddressO ehj7aAddressO;
  private Peechj7aTeleFaxO ehj7aTeleFaxO;
  private Peechj7aEmailO ehj7aEmailO;
  private String ehj7aTimeLinkO;
  private String ehj7aTimeLastmodO;

  public Peechj7aContact() {
  }

  /**
   **/
  public Peechj7aContact ehj7aNumCusO(String ehj7aNumCusO) {
    this.ehj7aNumCusO = ehj7aNumCusO;
    return this;
  }


  @JsonProperty("ehj7aNumCusO")
  public String getEhj7aNumCusO() {
    return ehj7aNumCusO;
  }

  @JsonProperty("ehj7aNumCusO")
  public void setEhj7aNumCusO(String ehj7aNumCusO) {
    this.ehj7aNumCusO = ehj7aNumCusO;
  }

  /**
   * minimum: 0
   * maximum: 999
   **/
  public Peechj7aContact ehj7aNumLadr1O(Integer ehj7aNumLadr1O) {
    this.ehj7aNumLadr1O = ehj7aNumLadr1O;
    return this;
  }


  @JsonProperty("ehj7aNumLadr1O")
  public Integer getEhj7aNumLadr1O() {
    return ehj7aNumLadr1O;
  }

  @JsonProperty("ehj7aNumLadr1O")
  public void setEhj7aNumLadr1O(Integer ehj7aNumLadr1O) {
    this.ehj7aNumLadr1O = ehj7aNumLadr1O;
  }

  /**
   **/
  public Peechj7aContact ehj7aCodIdO(String ehj7aCodIdO) {
    this.ehj7aCodIdO = ehj7aCodIdO;
    return this;
  }


  @JsonProperty("ehj7aCodIdO")
  public String getEhj7aCodIdO() {
    return ehj7aCodIdO;
  }

  @JsonProperty("ehj7aCodIdO")
  public void setEhj7aCodIdO(String ehj7aCodIdO) {
    this.ehj7aCodIdO = ehj7aCodIdO;
  }

  /**
   **/
  public Peechj7aContact ehj7aKeyIdO(String ehj7aKeyIdO) {
    this.ehj7aKeyIdO = ehj7aKeyIdO;
    return this;
  }


  @JsonProperty("ehj7aKeyIdO")
  public String getEhj7aKeyIdO() {
    return ehj7aKeyIdO;
  }

  @JsonProperty("ehj7aKeyIdO")
  public void setEhj7aKeyIdO(String ehj7aKeyIdO) {
    this.ehj7aKeyIdO = ehj7aKeyIdO;
  }

  /**
   **/
  public Peechj7aContact ehj7aCodIssCtryO(String ehj7aCodIssCtryO) {
    this.ehj7aCodIssCtryO = ehj7aCodIssCtryO;
    return this;
  }


  @JsonProperty("ehj7aCodIssCtryO")
  public String getEhj7aCodIssCtryO() {
    return ehj7aCodIssCtryO;
  }

  @JsonProperty("ehj7aCodIssCtryO")
  public void setEhj7aCodIssCtryO(String ehj7aCodIssCtryO) {
    this.ehj7aCodIssCtryO = ehj7aCodIssCtryO;
  }

  /**
   **/
  public Peechj7aContact ehj7aTypCntaadrO(String ehj7aTypCntaadrO) {
    this.ehj7aTypCntaadrO = ehj7aTypCntaadrO;
    return this;
  }


  @JsonProperty("ehj7aTypCntaadrO")
  public String getEhj7aTypCntaadrO() {
    return ehj7aTypCntaadrO;
  }

  @JsonProperty("ehj7aTypCntaadrO")
  public void setEhj7aTypCntaadrO(String ehj7aTypCntaadrO) {
    this.ehj7aTypCntaadrO = ehj7aTypCntaadrO;
  }

  /**
   **/
  public Peechj7aContact ehj7aCodAttnO(String ehj7aCodAttnO) {
    this.ehj7aCodAttnO = ehj7aCodAttnO;
    return this;
  }


  @JsonProperty("ehj7aCodAttnO")
  public String getEhj7aCodAttnO() {
    return ehj7aCodAttnO;
  }

  @JsonProperty("ehj7aCodAttnO")
  public void setEhj7aCodAttnO(String ehj7aCodAttnO) {
    this.ehj7aCodAttnO = ehj7aCodAttnO;
  }

  /**
   **/
  public Peechj7aContact ehj7aDesPersonO(String ehj7aDesPersonO) {
    this.ehj7aDesPersonO = ehj7aDesPersonO;
    return this;
  }


  @JsonProperty("ehj7aDesPersonO")
  public String getEhj7aDesPersonO() {
    return ehj7aDesPersonO;
  }

  @JsonProperty("ehj7aDesPersonO")
  public void setEhj7aDesPersonO(String ehj7aDesPersonO) {
    this.ehj7aDesPersonO = ehj7aDesPersonO;
  }

  /**
   **/
  public Peechj7aContact ehj7aLngSpokenO(String ehj7aLngSpokenO) {
    this.ehj7aLngSpokenO = ehj7aLngSpokenO;
    return this;
  }


  @JsonProperty("ehj7aLngSpokenO")
  public String getEhj7aLngSpokenO() {
    return ehj7aLngSpokenO;
  }

  @JsonProperty("ehj7aLngSpokenO")
  public void setEhj7aLngSpokenO(String ehj7aLngSpokenO) {
    this.ehj7aLngSpokenO = ehj7aLngSpokenO;
  }

  /**
   **/
  public Peechj7aContact ehj7aAddressO(Peechj7aAddressO ehj7aAddressO) {
    this.ehj7aAddressO = ehj7aAddressO;
    return this;
  }


  @JsonProperty("ehj7aAddressO")
  public Peechj7aAddressO getEhj7aAddressO() {
    return ehj7aAddressO;
  }

  @JsonProperty("ehj7aAddressO")
  public void setEhj7aAddressO(Peechj7aAddressO ehj7aAddressO) {
    this.ehj7aAddressO = ehj7aAddressO;
  }

  /**
   **/
  public Peechj7aContact ehj7aTeleFaxO(Peechj7aTeleFaxO ehj7aTeleFaxO) {
    this.ehj7aTeleFaxO = ehj7aTeleFaxO;
    return this;
  }


  @JsonProperty("ehj7aTeleFaxO")
  public Peechj7aTeleFaxO getEhj7aTeleFaxO() {
    return ehj7aTeleFaxO;
  }

  @JsonProperty("ehj7aTeleFaxO")
  public void setEhj7aTeleFaxO(Peechj7aTeleFaxO ehj7aTeleFaxO) {
    this.ehj7aTeleFaxO = ehj7aTeleFaxO;
  }

  /**
   **/
  public Peechj7aContact ehj7aEmailO(Peechj7aEmailO ehj7aEmailO) {
    this.ehj7aEmailO = ehj7aEmailO;
    return this;
  }


  @JsonProperty("ehj7aEmailO")
  public Peechj7aEmailO getEhj7aEmailO() {
    return ehj7aEmailO;
  }

  @JsonProperty("ehj7aEmailO")
  public void setEhj7aEmailO(Peechj7aEmailO ehj7aEmailO) {
    this.ehj7aEmailO = ehj7aEmailO;
  }

  /**
   **/
  public Peechj7aContact ehj7aTimeLinkO(String ehj7aTimeLinkO) {
    this.ehj7aTimeLinkO = ehj7aTimeLinkO;
    return this;
  }


  @JsonProperty("ehj7aTimeLinkO")
  public String getEhj7aTimeLinkO() {
    return ehj7aTimeLinkO;
  }

  @JsonProperty("ehj7aTimeLinkO")
  public void setEhj7aTimeLinkO(String ehj7aTimeLinkO) {
    this.ehj7aTimeLinkO = ehj7aTimeLinkO;
  }

  /**
   **/
  public Peechj7aContact ehj7aTimeLastmodO(String ehj7aTimeLastmodO) {
    this.ehj7aTimeLastmodO = ehj7aTimeLastmodO;
    return this;
  }


  @JsonProperty("ehj7aTimeLastmodO")
  public String getEhj7aTimeLastmodO() {
    return ehj7aTimeLastmodO;
  }

  @JsonProperty("ehj7aTimeLastmodO")
  public void setEhj7aTimeLastmodO(String ehj7aTimeLastmodO) {
    this.ehj7aTimeLastmodO = ehj7aTimeLastmodO;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aContact peechj7aContact = (Peechj7aContact) o;
    return Objects.equals(this.ehj7aNumCusO, peechj7aContact.ehj7aNumCusO) &&
        Objects.equals(this.ehj7aNumLadr1O, peechj7aContact.ehj7aNumLadr1O) &&
        Objects.equals(this.ehj7aCodIdO, peechj7aContact.ehj7aCodIdO) &&
        Objects.equals(this.ehj7aKeyIdO, peechj7aContact.ehj7aKeyIdO) &&
        Objects.equals(this.ehj7aCodIssCtryO, peechj7aContact.ehj7aCodIssCtryO) &&
        Objects.equals(this.ehj7aTypCntaadrO, peechj7aContact.ehj7aTypCntaadrO) &&
        Objects.equals(this.ehj7aCodAttnO, peechj7aContact.ehj7aCodAttnO) &&
        Objects.equals(this.ehj7aDesPersonO, peechj7aContact.ehj7aDesPersonO) &&
        Objects.equals(this.ehj7aLngSpokenO, peechj7aContact.ehj7aLngSpokenO) &&
        Objects.equals(this.ehj7aAddressO, peechj7aContact.ehj7aAddressO) &&
        Objects.equals(this.ehj7aTeleFaxO, peechj7aContact.ehj7aTeleFaxO) &&
        Objects.equals(this.ehj7aEmailO, peechj7aContact.ehj7aEmailO) &&
        Objects.equals(this.ehj7aTimeLinkO, peechj7aContact.ehj7aTimeLinkO) &&
        Objects.equals(this.ehj7aTimeLastmodO, peechj7aContact.ehj7aTimeLastmodO);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aNumCusO, ehj7aNumLadr1O, ehj7aCodIdO, ehj7aKeyIdO, ehj7aCodIssCtryO, ehj7aTypCntaadrO, ehj7aCodAttnO, ehj7aDesPersonO, ehj7aLngSpokenO, ehj7aAddressO, ehj7aTeleFaxO, ehj7aEmailO, ehj7aTimeLinkO, ehj7aTimeLastmodO);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aContact {\n");

    sb.append("    ehj7aNumCusO: ").append(toIndentedString(ehj7aNumCusO)).append("\n");
    sb.append("    ehj7aNumLadr1O: ").append(toIndentedString(ehj7aNumLadr1O)).append("\n");
    sb.append("    ehj7aCodIdO: ").append(toIndentedString(ehj7aCodIdO)).append("\n");
    sb.append("    ehj7aKeyIdO: ").append(toIndentedString(ehj7aKeyIdO)).append("\n");
    sb.append("    ehj7aCodIssCtryO: ").append(toIndentedString(ehj7aCodIssCtryO)).append("\n");
    sb.append("    ehj7aTypCntaadrO: ").append(toIndentedString(ehj7aTypCntaadrO)).append("\n");
    sb.append("    ehj7aCodAttnO: ").append(toIndentedString(ehj7aCodAttnO)).append("\n");
    sb.append("    ehj7aDesPersonO: ").append(toIndentedString(ehj7aDesPersonO)).append("\n");
    sb.append("    ehj7aLngSpokenO: ").append(toIndentedString(ehj7aLngSpokenO)).append("\n");
    sb.append("    ehj7aAddressO: ").append(toIndentedString(ehj7aAddressO)).append("\n");
    sb.append("    ehj7aTeleFaxO: ").append(toIndentedString(ehj7aTeleFaxO)).append("\n");
    sb.append("    ehj7aEmailO: ").append(toIndentedString(ehj7aEmailO)).append("\n");
    sb.append("    ehj7aTimeLinkO: ").append(toIndentedString(ehj7aTimeLinkO)).append("\n");
    sb.append("    ehj7aTimeLastmodO: ").append(toIndentedString(ehj7aTimeLastmodO)).append("\n");
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
