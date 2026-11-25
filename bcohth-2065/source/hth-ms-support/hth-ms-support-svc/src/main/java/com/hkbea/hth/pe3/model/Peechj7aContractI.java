package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aKeyAccI;
import com.hkbea.hth.pe3.model.Peechj7aPartI;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aContractI")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aContractI   {
  private String ehj7aTypAppI;
  private String ehj7aNumAccExtI;
  private Peechj7aKeyAccI ehj7aKeyAccI;
  private String ehj7aCodOperI;
  private Peechj7aPartI ehj7aPartI;

  public Peechj7aContractI() {
  }

  /**
   **/
  public Peechj7aContractI ehj7aTypAppI(String ehj7aTypAppI) {
    this.ehj7aTypAppI = ehj7aTypAppI;
    return this;
  }


  @JsonProperty("ehj7aTypAppI")
  public String getEhj7aTypAppI() {
    return ehj7aTypAppI;
  }

  @JsonProperty("ehj7aTypAppI")
  public void setEhj7aTypAppI(String ehj7aTypAppI) {
    this.ehj7aTypAppI = ehj7aTypAppI;
  }

  /**
   **/
  public Peechj7aContractI ehj7aNumAccExtI(String ehj7aNumAccExtI) {
    this.ehj7aNumAccExtI = ehj7aNumAccExtI;
    return this;
  }


  @JsonProperty("ehj7aNumAccExtI")
  public String getEhj7aNumAccExtI() {
    return ehj7aNumAccExtI;
  }

  @JsonProperty("ehj7aNumAccExtI")
  public void setEhj7aNumAccExtI(String ehj7aNumAccExtI) {
    this.ehj7aNumAccExtI = ehj7aNumAccExtI;
  }

  /**
   **/
  public Peechj7aContractI ehj7aKeyAccI(Peechj7aKeyAccI ehj7aKeyAccI) {
    this.ehj7aKeyAccI = ehj7aKeyAccI;
    return this;
  }


  @JsonProperty("ehj7aKeyAccI")
  public Peechj7aKeyAccI getEhj7aKeyAccI() {
    return ehj7aKeyAccI;
  }

  @JsonProperty("ehj7aKeyAccI")
  public void setEhj7aKeyAccI(Peechj7aKeyAccI ehj7aKeyAccI) {
    this.ehj7aKeyAccI = ehj7aKeyAccI;
  }

  /**
   **/
  public Peechj7aContractI ehj7aCodOperI(String ehj7aCodOperI) {
    this.ehj7aCodOperI = ehj7aCodOperI;
    return this;
  }


  @JsonProperty("ehj7aCodOperI")
  public String getEhj7aCodOperI() {
    return ehj7aCodOperI;
  }

  @JsonProperty("ehj7aCodOperI")
  public void setEhj7aCodOperI(String ehj7aCodOperI) {
    this.ehj7aCodOperI = ehj7aCodOperI;
  }

  /**
   **/
  public Peechj7aContractI ehj7aPartI(Peechj7aPartI ehj7aPartI) {
    this.ehj7aPartI = ehj7aPartI;
    return this;
  }


  @JsonProperty("ehj7aPartI")
  public Peechj7aPartI getEhj7aPartI() {
    return ehj7aPartI;
  }

  @JsonProperty("ehj7aPartI")
  public void setEhj7aPartI(Peechj7aPartI ehj7aPartI) {
    this.ehj7aPartI = ehj7aPartI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aContractI peechj7aContractI = (Peechj7aContractI) o;
    return Objects.equals(this.ehj7aTypAppI, peechj7aContractI.ehj7aTypAppI) &&
        Objects.equals(this.ehj7aNumAccExtI, peechj7aContractI.ehj7aNumAccExtI) &&
        Objects.equals(this.ehj7aKeyAccI, peechj7aContractI.ehj7aKeyAccI) &&
        Objects.equals(this.ehj7aCodOperI, peechj7aContractI.ehj7aCodOperI) &&
        Objects.equals(this.ehj7aPartI, peechj7aContractI.ehj7aPartI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aTypAppI, ehj7aNumAccExtI, ehj7aKeyAccI, ehj7aCodOperI, ehj7aPartI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aContractI {\n");

    sb.append("    ehj7aTypAppI: ").append(toIndentedString(ehj7aTypAppI)).append("\n");
    sb.append("    ehj7aNumAccExtI: ").append(toIndentedString(ehj7aNumAccExtI)).append("\n");
    sb.append("    ehj7aKeyAccI: ").append(toIndentedString(ehj7aKeyAccI)).append("\n");
    sb.append("    ehj7aCodOperI: ").append(toIndentedString(ehj7aCodOperI)).append("\n");
    sb.append("    ehj7aPartI: ").append(toIndentedString(ehj7aPartI)).append("\n");
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
