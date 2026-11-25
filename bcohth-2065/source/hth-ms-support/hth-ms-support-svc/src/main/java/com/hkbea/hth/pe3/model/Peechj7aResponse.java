package com.hkbea.hth.pe3.model;

import com.hkbea.hth.pe3.model.Peechj7aDfhcommarea;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aResponse")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aResponse   {
  private Peechj7aDfhcommarea dfhcommarea;

  public Peechj7aResponse() {
  }

  /**
   **/
  public Peechj7aResponse dfhcommarea(Peechj7aDfhcommarea dfhcommarea) {
    this.dfhcommarea = dfhcommarea;
    return this;
  }


  @JsonProperty("dfhcommarea")
  public Peechj7aDfhcommarea getDfhcommarea() {
    return dfhcommarea;
  }

  @JsonProperty("dfhcommarea")
  public void setDfhcommarea(Peechj7aDfhcommarea dfhcommarea) {
    this.dfhcommarea = dfhcommarea;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aResponse peechj7aResponse = (Peechj7aResponse) o;
    return Objects.equals(this.dfhcommarea, peechj7aResponse.dfhcommarea);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dfhcommarea);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aResponse {\n");

    sb.append("    dfhcommarea: ").append(toIndentedString(dfhcommarea)).append("\n");
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
