package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aSqlerrm;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aAnswer")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aAnswer   {
  private String ehj7aCodReturn;
  private Integer ehj7aSqlcode;
  private Peechj7aSqlerrm ehj7aSqlerrm;
  private String ehj7aDesTable;
  private String ehj7aReference;

  public Peechj7aAnswer() {
  }

  /**
   **/
  public Peechj7aAnswer ehj7aCodReturn(String ehj7aCodReturn) {
    this.ehj7aCodReturn = ehj7aCodReturn;
    return this;
  }


  @JsonProperty("ehj7aCodReturn")
  public String getEhj7aCodReturn() {
    return ehj7aCodReturn;
  }

  @JsonProperty("ehj7aCodReturn")
  public void setEhj7aCodReturn(String ehj7aCodReturn) {
    this.ehj7aCodReturn = ehj7aCodReturn;
  }

  /**
   * minimum: -999999999
   * maximum: 999999999
   **/
  public Peechj7aAnswer ehj7aSqlcode(Integer ehj7aSqlcode) {
    this.ehj7aSqlcode = ehj7aSqlcode;
    return this;
  }


  @JsonProperty("ehj7aSqlcode")
  public Integer getEhj7aSqlcode() {
    return ehj7aSqlcode;
  }

  @JsonProperty("ehj7aSqlcode")
  public void setEhj7aSqlcode(Integer ehj7aSqlcode) {
    this.ehj7aSqlcode = ehj7aSqlcode;
  }

  /**
   **/
  public Peechj7aAnswer ehj7aSqlerrm(Peechj7aSqlerrm ehj7aSqlerrm) {
    this.ehj7aSqlerrm = ehj7aSqlerrm;
    return this;
  }


  @JsonProperty("ehj7aSqlerrm")
  public Peechj7aSqlerrm getEhj7aSqlerrm() {
    return ehj7aSqlerrm;
  }

  @JsonProperty("ehj7aSqlerrm")
  public void setEhj7aSqlerrm(Peechj7aSqlerrm ehj7aSqlerrm) {
    this.ehj7aSqlerrm = ehj7aSqlerrm;
  }

  /**
   **/
  public Peechj7aAnswer ehj7aDesTable(String ehj7aDesTable) {
    this.ehj7aDesTable = ehj7aDesTable;
    return this;
  }


  @JsonProperty("ehj7aDesTable")
  public String getEhj7aDesTable() {
    return ehj7aDesTable;
  }

  @JsonProperty("ehj7aDesTable")
  public void setEhj7aDesTable(String ehj7aDesTable) {
    this.ehj7aDesTable = ehj7aDesTable;
  }

  /**
   **/
  public Peechj7aAnswer ehj7aReference(String ehj7aReference) {
    this.ehj7aReference = ehj7aReference;
    return this;
  }


  @JsonProperty("ehj7aReference")
  public String getEhj7aReference() {
    return ehj7aReference;
  }

  @JsonProperty("ehj7aReference")
  public void setEhj7aReference(String ehj7aReference) {
    this.ehj7aReference = ehj7aReference;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aAnswer peechj7aAnswer = (Peechj7aAnswer) o;
    return Objects.equals(this.ehj7aCodReturn, peechj7aAnswer.ehj7aCodReturn) &&
        Objects.equals(this.ehj7aSqlcode, peechj7aAnswer.ehj7aSqlcode) &&
        Objects.equals(this.ehj7aSqlerrm, peechj7aAnswer.ehj7aSqlerrm) &&
        Objects.equals(this.ehj7aDesTable, peechj7aAnswer.ehj7aDesTable) &&
        Objects.equals(this.ehj7aReference, peechj7aAnswer.ehj7aReference);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aCodReturn, ehj7aSqlcode, ehj7aSqlerrm, ehj7aDesTable, ehj7aReference);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aAnswer {\n");

    sb.append("    ehj7aCodReturn: ").append(toIndentedString(ehj7aCodReturn)).append("\n");
    sb.append("    ehj7aSqlcode: ").append(toIndentedString(ehj7aSqlcode)).append("\n");
    sb.append("    ehj7aSqlerrm: ").append(toIndentedString(ehj7aSqlerrm)).append("\n");
    sb.append("    ehj7aDesTable: ").append(toIndentedString(ehj7aDesTable)).append("\n");
    sb.append("    ehj7aReference: ").append(toIndentedString(ehj7aReference)).append("\n");
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
