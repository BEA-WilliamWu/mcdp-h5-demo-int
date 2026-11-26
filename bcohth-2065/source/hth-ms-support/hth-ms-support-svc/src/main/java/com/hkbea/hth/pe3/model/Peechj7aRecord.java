package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aInput;
import com.hkbea.hth.pe3.model.Peechj7aOutput;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aRecord")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aRecord   {
  private Peechj7aInput ehj7aInput;
  private Peechj7aOutput ehj7aOutput;

  public Peechj7aRecord() {
  }

  /**
   **/
  public Peechj7aRecord ehj7aInput(Peechj7aInput ehj7aInput) {
    this.ehj7aInput = ehj7aInput;
    return this;
  }


  @JsonProperty("ehj7aInput")
  public Peechj7aInput getEhj7aInput() {
    return ehj7aInput;
  }

  @JsonProperty("ehj7aInput")
  public void setEhj7aInput(Peechj7aInput ehj7aInput) {
    this.ehj7aInput = ehj7aInput;
  }

  /**
   **/
  public Peechj7aRecord ehj7aOutput(Peechj7aOutput ehj7aOutput) {
    this.ehj7aOutput = ehj7aOutput;
    return this;
  }


  @JsonProperty("ehj7aOutput")
  public Peechj7aOutput getEhj7aOutput() {
    return ehj7aOutput;
  }

  @JsonProperty("ehj7aOutput")
  public void setEhj7aOutput(Peechj7aOutput ehj7aOutput) {
    this.ehj7aOutput = ehj7aOutput;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aRecord peechj7aRecord = (Peechj7aRecord) o;
    return Objects.equals(this.ehj7aInput, peechj7aRecord.ehj7aInput) &&
        Objects.equals(this.ehj7aOutput, peechj7aRecord.ehj7aOutput);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aInput, ehj7aOutput);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aRecord {\n");

    sb.append("    ehj7aInput: ").append(toIndentedString(ehj7aInput)).append("\n");
    sb.append("    ehj7aOutput: ").append(toIndentedString(ehj7aOutput)).append("\n");
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
