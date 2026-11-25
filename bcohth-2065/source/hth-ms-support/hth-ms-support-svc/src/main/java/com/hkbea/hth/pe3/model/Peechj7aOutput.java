package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aAnswer;
import com.hkbea.hth.pe3.model.Peechj7aContact;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aOutput")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aOutput   {
  private Peechj7aAnswer ehj7aAnswer;
  private List<Peechj7aContact> ehj7aContactO = new ArrayList<>();

  public Peechj7aOutput() {
  }

  /**
   **/
  public Peechj7aOutput ehj7aAnswer(Peechj7aAnswer ehj7aAnswer) {
    this.ehj7aAnswer = ehj7aAnswer;
    return this;
  }


  @JsonProperty("ehj7aAnswer")
  public Peechj7aAnswer getEhj7aAnswer() {
    return ehj7aAnswer;
  }

  @JsonProperty("ehj7aAnswer")
  public void setEhj7aAnswer(Peechj7aAnswer ehj7aAnswer) {
    this.ehj7aAnswer = ehj7aAnswer;
  }

  /**
   **/
  public Peechj7aOutput ehj7aContactO(List<Peechj7aContact> ehj7aContactO) {
    this.ehj7aContactO = ehj7aContactO;
    return this;
  }


  @JsonProperty("ehj7aContactO")
  public List<Peechj7aContact> getEhj7aContactO() {
    return ehj7aContactO;
  }

  @JsonProperty("ehj7aContactO")
  public void setEhj7aContactO(List<Peechj7aContact> ehj7aContactO) {
    this.ehj7aContactO = ehj7aContactO;
  }

  public Peechj7aOutput addEhj7aContactOItem(Peechj7aContact ehj7aContactOItem) {
    if (this.ehj7aContactO == null) {
      this.ehj7aContactO = new ArrayList<>();
    }

    this.ehj7aContactO.add(ehj7aContactOItem);
    return this;
  }

  public Peechj7aOutput removeEhj7aContactOItem(Peechj7aContact ehj7aContactOItem) {
    if (ehj7aContactOItem != null && this.ehj7aContactO != null) {
      this.ehj7aContactO.remove(ehj7aContactOItem);
    }

    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aOutput peechj7aOutput = (Peechj7aOutput) o;
    return Objects.equals(this.ehj7aAnswer, peechj7aOutput.ehj7aAnswer) &&
        Objects.equals(this.ehj7aContactO, peechj7aOutput.ehj7aContactO);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aAnswer, ehj7aContactO);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aOutput {\n");

    sb.append("    ehj7aAnswer: ").append(toIndentedString(ehj7aAnswer)).append("\n");
    sb.append("    ehj7aContactO: ").append(toIndentedString(ehj7aContactO)).append("\n");
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
