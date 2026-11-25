package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aDocumentI")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aDocumentI   {
  private String ehj7aCodIdI;
  private String ehj7aKeyIdI;
  private String ehj7aCodIssCtryI;

  public Peechj7aDocumentI() {
  }

  /**
   **/
  public Peechj7aDocumentI ehj7aCodIdI(String ehj7aCodIdI) {
    this.ehj7aCodIdI = ehj7aCodIdI;
    return this;
  }


  @JsonProperty("ehj7aCodIdI")
  public String getEhj7aCodIdI() {
    return ehj7aCodIdI;
  }

  @JsonProperty("ehj7aCodIdI")
  public void setEhj7aCodIdI(String ehj7aCodIdI) {
    this.ehj7aCodIdI = ehj7aCodIdI;
  }

  /**
   **/
  public Peechj7aDocumentI ehj7aKeyIdI(String ehj7aKeyIdI) {
    this.ehj7aKeyIdI = ehj7aKeyIdI;
    return this;
  }


  @JsonProperty("ehj7aKeyIdI")
  public String getEhj7aKeyIdI() {
    return ehj7aKeyIdI;
  }

  @JsonProperty("ehj7aKeyIdI")
  public void setEhj7aKeyIdI(String ehj7aKeyIdI) {
    this.ehj7aKeyIdI = ehj7aKeyIdI;
  }

  /**
   **/
  public Peechj7aDocumentI ehj7aCodIssCtryI(String ehj7aCodIssCtryI) {
    this.ehj7aCodIssCtryI = ehj7aCodIssCtryI;
    return this;
  }


  @JsonProperty("ehj7aCodIssCtryI")
  public String getEhj7aCodIssCtryI() {
    return ehj7aCodIssCtryI;
  }

  @JsonProperty("ehj7aCodIssCtryI")
  public void setEhj7aCodIssCtryI(String ehj7aCodIssCtryI) {
    this.ehj7aCodIssCtryI = ehj7aCodIssCtryI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aDocumentI peechj7aDocumentI = (Peechj7aDocumentI) o;
    return Objects.equals(this.ehj7aCodIdI, peechj7aDocumentI.ehj7aCodIdI) &&
        Objects.equals(this.ehj7aKeyIdI, peechj7aDocumentI.ehj7aKeyIdI) &&
        Objects.equals(this.ehj7aCodIssCtryI, peechj7aDocumentI.ehj7aCodIssCtryI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aCodIdI, ehj7aKeyIdI, ehj7aCodIssCtryI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aDocumentI {\n");

    sb.append("    ehj7aCodIdI: ").append(toIndentedString(ehj7aCodIdI)).append("\n");
    sb.append("    ehj7aKeyIdI: ").append(toIndentedString(ehj7aKeyIdI)).append("\n");
    sb.append("    ehj7aCodIssCtryI: ").append(toIndentedString(ehj7aCodIssCtryI)).append("\n");
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
