package apimodels;

import java.util.Objects;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
/**
 * InlineResponse200
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaPlayFrameworkCodegen", date = "2017-08-19T20:56:55.877Z")

public class InlineResponse200   {
  @JsonProperty("id")
  private String id = null;

  @JsonProperty("desc")
  private String desc = null;

  @JsonProperty("idmap")
  private String idmap = null;

  public InlineResponse200 id(String id) {
    this.id = id;
    return this;
  }

   /**
   * the function name  
   * @return id
  **/
    public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public InlineResponse200 desc(String desc) {
    this.desc = desc;
    return this;
  }

   /**
   * a description of the function  
   * @return desc
  **/
    public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    this.desc = desc;
  }

  public InlineResponse200 idmap(String idmap) {
    this.idmap = idmap;
    return this;
  }

   /**
   * The URL to execute the API call on the id
   * @return idmap
  **/
    public String getIdmap() {
    return idmap;
  }

  public void setIdmap(String idmap) {
    this.idmap = idmap;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InlineResponse200 inlineResponse200 = (InlineResponse200) o;
    return Objects.equals(this.id, inlineResponse200.id) &&
        Objects.equals(this.desc, inlineResponse200.desc) &&
        Objects.equals(this.idmap, inlineResponse200.idmap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, desc, idmap);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InlineResponse200 {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    desc: ").append(toIndentedString(desc)).append("\n");
    sb.append("    idmap: ").append(toIndentedString(idmap)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

