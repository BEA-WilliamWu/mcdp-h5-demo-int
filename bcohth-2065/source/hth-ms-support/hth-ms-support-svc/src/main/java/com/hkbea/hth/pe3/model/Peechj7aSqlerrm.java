package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aSqlerrm")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aSqlerrm   {
  private Integer ehj7aSqlerrmlth;
  private String ehj7aDatSqlerrm;

  public Peechj7aSqlerrm() {
  }

  /**
   * minimum: -9999
   * maximum: 9999
   **/
  public Peechj7aSqlerrm ehj7aSqlerrmlth(Integer ehj7aSqlerrmlth) {
    this.ehj7aSqlerrmlth = ehj7aSqlerrmlth;
    return this;
  }


  @JsonProperty("ehj7aSqlerrmlth")
  public Integer getEhj7aSqlerrmlth() {
    return ehj7aSqlerrmlth;
  }

  @JsonProperty("ehj7aSqlerrmlth")
  public void setEhj7aSqlerrmlth(Integer ehj7aSqlerrmlth) {
    this.ehj7aSqlerrmlth = ehj7aSqlerrmlth;
  }

  /**
   **/
  public Peechj7aSqlerrm ehj7aDatSqlerrm(String ehj7aDatSqlerrm) {
    this.ehj7aDatSqlerrm = ehj7aDatSqlerrm;
    return this;
  }


  @JsonProperty("ehj7aDatSqlerrm")
  public String getEhj7aDatSqlerrm() {
    return ehj7aDatSqlerrm;
  }

  @JsonProperty("ehj7aDatSqlerrm")
  public void setEhj7aDatSqlerrm(String ehj7aDatSqlerrm) {
    this.ehj7aDatSqlerrm = ehj7aDatSqlerrm;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aSqlerrm peechj7aSqlerrm = (Peechj7aSqlerrm) o;
    return Objects.equals(this.ehj7aSqlerrmlth, peechj7aSqlerrm.ehj7aSqlerrmlth) &&
        Objects.equals(this.ehj7aDatSqlerrm, peechj7aSqlerrm.ehj7aDatSqlerrm);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aSqlerrmlth, ehj7aDatSqlerrm);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aSqlerrm {\n");

    sb.append("    ehj7aSqlerrmlth: ").append(toIndentedString(ehj7aSqlerrmlth)).append("\n");
    sb.append("    ehj7aDatSqlerrm: ").append(toIndentedString(ehj7aDatSqlerrm)).append("\n");
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
