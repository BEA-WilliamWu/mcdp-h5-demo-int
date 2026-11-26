package com.hkbea.hth.pe3.model;


import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonTypeName;



@JsonTypeName("Peechj7aEmailO")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaJAXRSSpecServerCodegen", comments = "Generator version: 7.17.0")@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)

public class Peechj7aEmailO   {
  private String ehj7aAdrEmailO;
  private Integer ehj7aFlgAccnumO;
  private String ehj7aDatModPriAdrO;
  private String ehj7aDatCreationO;
  private String ehj7aUsrCreationO;
  private String ehj7aDatLastmodO;
  private String ehj7aUsrLastmodO;

  public Peechj7aEmailO() {
  }

  /**
   **/
  public Peechj7aEmailO ehj7aAdrEmailO(String ehj7aAdrEmailO) {
    this.ehj7aAdrEmailO = ehj7aAdrEmailO;
    return this;
  }


  @JsonProperty("ehj7aAdrEmailO")
  public String getEhj7aAdrEmailO() {
    return ehj7aAdrEmailO;
  }

  @JsonProperty("ehj7aAdrEmailO")
  public void setEhj7aAdrEmailO(String ehj7aAdrEmailO) {
    this.ehj7aAdrEmailO = ehj7aAdrEmailO;
  }

  /**
   * minimum: 0
   * maximum: 999
   **/
  public Peechj7aEmailO ehj7aFlgAccnumO(Integer ehj7aFlgAccnumO) {
    this.ehj7aFlgAccnumO = ehj7aFlgAccnumO;
    return this;
  }


  @JsonProperty("ehj7aFlgAccnumO")
  public Integer getEhj7aFlgAccnumO() {
    return ehj7aFlgAccnumO;
  }

  @JsonProperty("ehj7aFlgAccnumO")
  public void setEhj7aFlgAccnumO(Integer ehj7aFlgAccnumO) {
    this.ehj7aFlgAccnumO = ehj7aFlgAccnumO;
  }

  /**
   **/
  public Peechj7aEmailO ehj7aDatModPriAdrO(String ehj7aDatModPriAdrO) {
    this.ehj7aDatModPriAdrO = ehj7aDatModPriAdrO;
    return this;
  }


  @JsonProperty("ehj7aDatModPriAdrO")
  public String getEhj7aDatModPriAdrO() {
    return ehj7aDatModPriAdrO;
  }

  @JsonProperty("ehj7aDatModPriAdrO")
  public void setEhj7aDatModPriAdrO(String ehj7aDatModPriAdrO) {
    this.ehj7aDatModPriAdrO = ehj7aDatModPriAdrO;
  }

  /**
   **/
  public Peechj7aEmailO ehj7aDatCreationO(String ehj7aDatCreationO) {
    this.ehj7aDatCreationO = ehj7aDatCreationO;
    return this;
  }


  @JsonProperty("ehj7aDatCreationO")
  public String getEhj7aDatCreationO() {
    return ehj7aDatCreationO;
  }

  @JsonProperty("ehj7aDatCreationO")
  public void setEhj7aDatCreationO(String ehj7aDatCreationO) {
    this.ehj7aDatCreationO = ehj7aDatCreationO;
  }

  /**
   **/
  public Peechj7aEmailO ehj7aUsrCreationO(String ehj7aUsrCreationO) {
    this.ehj7aUsrCreationO = ehj7aUsrCreationO;
    return this;
  }


  @JsonProperty("ehj7aUsrCreationO")
  public String getEhj7aUsrCreationO() {
    return ehj7aUsrCreationO;
  }

  @JsonProperty("ehj7aUsrCreationO")
  public void setEhj7aUsrCreationO(String ehj7aUsrCreationO) {
    this.ehj7aUsrCreationO = ehj7aUsrCreationO;
  }

  /**
   **/
  public Peechj7aEmailO ehj7aDatLastmodO(String ehj7aDatLastmodO) {
    this.ehj7aDatLastmodO = ehj7aDatLastmodO;
    return this;
  }


  @JsonProperty("ehj7aDatLastmodO")
  public String getEhj7aDatLastmodO() {
    return ehj7aDatLastmodO;
  }

  @JsonProperty("ehj7aDatLastmodO")
  public void setEhj7aDatLastmodO(String ehj7aDatLastmodO) {
    this.ehj7aDatLastmodO = ehj7aDatLastmodO;
  }

  /**
   **/
  public Peechj7aEmailO ehj7aUsrLastmodO(String ehj7aUsrLastmodO) {
    this.ehj7aUsrLastmodO = ehj7aUsrLastmodO;
    return this;
  }


  @JsonProperty("ehj7aUsrLastmodO")
  public String getEhj7aUsrLastmodO() {
    return ehj7aUsrLastmodO;
  }

  @JsonProperty("ehj7aUsrLastmodO")
  public void setEhj7aUsrLastmodO(String ehj7aUsrLastmodO) {
    this.ehj7aUsrLastmodO = ehj7aUsrLastmodO;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Peechj7aEmailO peechj7aEmailO = (Peechj7aEmailO) o;
    return Objects.equals(this.ehj7aAdrEmailO, peechj7aEmailO.ehj7aAdrEmailO) &&
        Objects.equals(this.ehj7aFlgAccnumO, peechj7aEmailO.ehj7aFlgAccnumO) &&
        Objects.equals(this.ehj7aDatModPriAdrO, peechj7aEmailO.ehj7aDatModPriAdrO) &&
        Objects.equals(this.ehj7aDatCreationO, peechj7aEmailO.ehj7aDatCreationO) &&
        Objects.equals(this.ehj7aUsrCreationO, peechj7aEmailO.ehj7aUsrCreationO) &&
        Objects.equals(this.ehj7aDatLastmodO, peechj7aEmailO.ehj7aDatLastmodO) &&
        Objects.equals(this.ehj7aUsrLastmodO, peechj7aEmailO.ehj7aUsrLastmodO);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ehj7aAdrEmailO, ehj7aFlgAccnumO, ehj7aDatModPriAdrO, ehj7aDatCreationO, ehj7aUsrCreationO, ehj7aDatLastmodO, ehj7aUsrLastmodO);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Peechj7aEmailO {\n");

    sb.append("    ehj7aAdrEmailO: ").append(toIndentedString(ehj7aAdrEmailO)).append("\n");
    sb.append("    ehj7aFlgAccnumO: ").append(toIndentedString(ehj7aFlgAccnumO)).append("\n");
    sb.append("    ehj7aDatModPriAdrO: ").append(toIndentedString(ehj7aDatModPriAdrO)).append("\n");
    sb.append("    ehj7aDatCreationO: ").append(toIndentedString(ehj7aDatCreationO)).append("\n");
    sb.append("    ehj7aUsrCreationO: ").append(toIndentedString(ehj7aUsrCreationO)).append("\n");
    sb.append("    ehj7aDatLastmodO: ").append(toIndentedString(ehj7aDatLastmodO)).append("\n");
    sb.append("    ehj7aUsrLastmodO: ").append(toIndentedString(ehj7aUsrLastmodO)).append("\n");
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
