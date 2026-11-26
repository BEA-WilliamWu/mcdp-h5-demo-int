package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aQaechcc;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aDfhcommarea")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aDfhcommarea   {
  private Peechj7aQaechcc qaechcc;

  public Peechj7aDfhcommarea() {
  }

  /**
   **/
  public Peechj7aDfhcommarea qaechcc(Peechj7aQaechcc qaechcc) {
    this.qaechcc = qaechcc;
    return this;
  }


  @JsonProperty("qaechcc")
  public Peechj7aQaechcc getQaechcc() {
    return qaechcc;
  }

  @JsonProperty("qaechcc")
  public void setQaechcc(Peechj7aQaechcc qaechcc) {
    this.qaechcc = qaechcc;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aDfhcommarea peechj7aDfhcommarea = (Peechj7aDfhcommarea) o;
    return Objects.equals(this.qaechcc, peechj7aDfhcommarea.qaechcc);
  }

  @Override
  public int hashCode() {
    return Objects.hash(qaechcc);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aDfhcommarea {\n");

    sb.append("    qaechcc: ").append(toIndentedString(qaechcc)).append("\n");
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
