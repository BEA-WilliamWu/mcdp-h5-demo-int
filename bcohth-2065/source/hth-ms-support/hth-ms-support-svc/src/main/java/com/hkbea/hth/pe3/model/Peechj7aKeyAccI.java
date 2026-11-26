package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aKeyAccI")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aKeyAccI   {
  private String ehj7aKeyEntityI;
  private String ehj7aBrnOpenI;
  private String ehj7aNumAccountI;

  public Peechj7aKeyAccI() {
  }

  /**
   **/
  public Peechj7aKeyAccI ehj7aKeyEntityI(String ehj7aKeyEntityI) {
    this.ehj7aKeyEntityI = ehj7aKeyEntityI;
    return this;
  }


  @JsonProperty("ehj7aKeyEntityI")
  public String getEhj7aKeyEntityI() {
    return ehj7aKeyEntityI;
  }

  @JsonProperty("ehj7aKeyEntityI")
  public void setEhj7aKeyEntityI(String ehj7aKeyEntityI) {
    this.ehj7aKeyEntityI = ehj7aKeyEntityI;
  }

  /**
   **/
  public Peechj7aKeyAccI ehj7aBrnOpenI(String ehj7aBrnOpenI) {
    this.ehj7aBrnOpenI = ehj7aBrnOpenI;
    return this;
  }


  @JsonProperty("ehj7aBrnOpenI")
  public String getEhj7aBrnOpenI() {
    return ehj7aBrnOpenI;
  }

  @JsonProperty("ehj7aBrnOpenI")
  public void setEhj7aBrnOpenI(String ehj7aBrnOpenI) {
    this.ehj7aBrnOpenI = ehj7aBrnOpenI;
  }

  /**
   **/
  public Peechj7aKeyAccI ehj7aNumAccountI(String ehj7aNumAccountI) {
    this.ehj7aNumAccountI = ehj7aNumAccountI;
    return this;
  }


  @JsonProperty("ehj7aNumAccountI")
  public String getEhj7aNumAccountI() {
    return ehj7aNumAccountI;
  }

  @JsonProperty("ehj7aNumAccountI")
  public void setEhj7aNumAccountI(String ehj7aNumAccountI) {
    this.ehj7aNumAccountI = ehj7aNumAccountI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aKeyAccI peechj7aKeyAccI = (Peechj7aKeyAccI) o;
    return Objects.equals(this.ehj7aKeyEntityI, peechj7aKeyAccI.ehj7aKeyEntityI) &&
        Objects.equals(this.ehj7aBrnOpenI, peechj7aKeyAccI.ehj7aBrnOpenI) &&
        Objects.equals(this.ehj7aNumAccountI, peechj7aKeyAccI.ehj7aNumAccountI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aKeyEntityI, ehj7aBrnOpenI, ehj7aNumAccountI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aKeyAccI {\n");

    sb.append("    ehj7aKeyEntityI: ").append(toIndentedString(ehj7aKeyEntityI)).append("\n");
    sb.append("    ehj7aBrnOpenI: ").append(toIndentedString(ehj7aBrnOpenI)).append("\n");
    sb.append("    ehj7aNumAccountI: ").append(toIndentedString(ehj7aNumAccountI)).append("\n");
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
