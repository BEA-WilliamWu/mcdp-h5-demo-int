package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aCiIdI;
import com.hkbea.hth.pe3.model.Peechj7aContractI;
import com.hkbea.hth.pe3.model.Peechj7aDocumentI;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aInput")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aInput   {
  private String ehj7aCodEntityI;
  private Peechj7aCiIdI ehj7aCiIdI;
  private Peechj7aDocumentI ehj7aDocumentI;
  private Peechj7aContractI ehj7aContractI;

  public Peechj7aInput() {
  }

  /**
   **/
  public Peechj7aInput ehj7aCodEntityI(String ehj7aCodEntityI) {
    this.ehj7aCodEntityI = ehj7aCodEntityI;
    return this;
  }


  @JsonProperty("ehj7aCodEntityI")
  public String getEhj7aCodEntityI() {
    return ehj7aCodEntityI;
  }

  @JsonProperty("ehj7aCodEntityI")
  public void setEhj7aCodEntityI(String ehj7aCodEntityI) {
    this.ehj7aCodEntityI = ehj7aCodEntityI;
  }

  /**
   **/
  public Peechj7aInput ehj7aCiIdI(Peechj7aCiIdI ehj7aCiIdI) {
    this.ehj7aCiIdI = ehj7aCiIdI;
    return this;
  }


  @JsonProperty("ehj7aCiIdI")
  public Peechj7aCiIdI getEhj7aCiIdI() {
    return ehj7aCiIdI;
  }

  @JsonProperty("ehj7aCiIdI")
  public void setEhj7aCiIdI(Peechj7aCiIdI ehj7aCiIdI) {
    this.ehj7aCiIdI = ehj7aCiIdI;
  }

  /**
   **/
  public Peechj7aInput ehj7aDocumentI(Peechj7aDocumentI ehj7aDocumentI) {
    this.ehj7aDocumentI = ehj7aDocumentI;
    return this;
  }


  @JsonProperty("ehj7aDocumentI")
  public Peechj7aDocumentI getEhj7aDocumentI() {
    return ehj7aDocumentI;
  }

  @JsonProperty("ehj7aDocumentI")
  public void setEhj7aDocumentI(Peechj7aDocumentI ehj7aDocumentI) {
    this.ehj7aDocumentI = ehj7aDocumentI;
  }

  /**
   **/
  public Peechj7aInput ehj7aContractI(Peechj7aContractI ehj7aContractI) {
    this.ehj7aContractI = ehj7aContractI;
    return this;
  }


  @JsonProperty("ehj7aContractI")
  public Peechj7aContractI getEhj7aContractI() {
    return ehj7aContractI;
  }

  @JsonProperty("ehj7aContractI")
  public void setEhj7aContractI(Peechj7aContractI ehj7aContractI) {
    this.ehj7aContractI = ehj7aContractI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aInput peechj7aInput = (Peechj7aInput) o;
    return Objects.equals(this.ehj7aCodEntityI, peechj7aInput.ehj7aCodEntityI) &&
        Objects.equals(this.ehj7aCiIdI, peechj7aInput.ehj7aCiIdI) &&
        Objects.equals(this.ehj7aDocumentI, peechj7aInput.ehj7aDocumentI) &&
        Objects.equals(this.ehj7aContractI, peechj7aInput.ehj7aContractI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aCodEntityI, ehj7aCiIdI, ehj7aDocumentI, ehj7aContractI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aInput {\n");

    sb.append("    ehj7aCodEntityI: ").append(toIndentedString(ehj7aCodEntityI)).append("\n");
    sb.append("    ehj7aCiIdI: ").append(toIndentedString(ehj7aCiIdI)).append("\n");
    sb.append("    ehj7aDocumentI: ").append(toIndentedString(ehj7aDocumentI)).append("\n");
    sb.append("    ehj7aContractI: ").append(toIndentedString(ehj7aContractI)).append("\n");
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
