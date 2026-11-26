package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aPartI")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aPartI   {
  private String ehj7aKeyParticI;
  private String ehj7aPartseqI;

  public Peechj7aPartI() {
  }

  /**
   **/
  public Peechj7aPartI ehj7aKeyParticI(String ehj7aKeyParticI) {
    this.ehj7aKeyParticI = ehj7aKeyParticI;
    return this;
  }


  @JsonProperty("ehj7aKeyParticI")
  public String getEhj7aKeyParticI() {
    return ehj7aKeyParticI;
  }

  @JsonProperty("ehj7aKeyParticI")
  public void setEhj7aKeyParticI(String ehj7aKeyParticI) {
    this.ehj7aKeyParticI = ehj7aKeyParticI;
  }

  /**
   **/
  public Peechj7aPartI ehj7aPartseqI(String ehj7aPartseqI) {
    this.ehj7aPartseqI = ehj7aPartseqI;
    return this;
  }


  @JsonProperty("ehj7aPartseqI")
  public String getEhj7aPartseqI() {
    return ehj7aPartseqI;
  }

  @JsonProperty("ehj7aPartseqI")
  public void setEhj7aPartseqI(String ehj7aPartseqI) {
    this.ehj7aPartseqI = ehj7aPartseqI;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aPartI peechj7aPartI = (Peechj7aPartI) o;
    return Objects.equals(this.ehj7aKeyParticI, peechj7aPartI.ehj7aKeyParticI) &&
        Objects.equals(this.ehj7aPartseqI, peechj7aPartI.ehj7aPartseqI);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aKeyParticI, ehj7aPartseqI);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aPartI {\n");

    sb.append("    ehj7aKeyParticI: ").append(toIndentedString(ehj7aKeyParticI)).append("\n");
    sb.append("    ehj7aPartseqI: ").append(toIndentedString(ehj7aPartseqI)).append("\n");
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
