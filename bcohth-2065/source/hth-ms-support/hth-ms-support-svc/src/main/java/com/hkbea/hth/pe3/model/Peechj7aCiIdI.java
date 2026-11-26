package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aCiIdI")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aCiIdI   {
  private String ehj7aNumCusI;

  public Peechj7aCiIdI() {
  }

  /**
   **/
  public Peechj7aCiIdI ehj7aNumCusI(String ehj7aNumCusI) {
    this.ehj7aNumCusI = ehj7aNumCusI;
    return this;
  }


  @JsonProperty("ehj7aNumCusI")
  public String getEhj7aNumCusI() {
    return ehj7aNumCusI;
  }

  @JsonProperty("ehj7aNumCusI")
  public void setEhj7aNumCusI(String ehj7aNumCusI) {
    this.ehj7aNumCusI = ehj7aNumCusI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aCiIdI peechj7aCiIdI = (Peechj7aCiIdI) o;
    return Objects.equals(this.ehj7aNumCusI, peechj7aCiIdI.ehj7aNumCusI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aNumCusI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aCiIdI {\n");

    sb.append("    ehj7aNumCusI: ").append(toIndentedString(ehj7aNumCusI)).append("\n");
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
