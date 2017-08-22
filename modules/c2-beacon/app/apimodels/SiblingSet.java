package apimodels;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
/**
 * SiblingSet
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaPlayFrameworkCodegen", date = "2017-08-19T20:56:55.877Z")

public class SiblingSet   {
  @JsonProperty("siblingSet")
  private List<Integer> siblingSet = null;

  public SiblingSet siblingSet(List<Integer> siblingSet) {
    this.siblingSet = siblingSet;
    return this;
  }

  public SiblingSet addSiblingSetItem(Integer siblingSetItem) {
    if (this.siblingSet == null) {
      this.siblingSet = new ArrayList<Integer>();
    }
    this.siblingSet.add(siblingSetItem);
    return this;
  }

   /**
   * Get siblingSet
   * @return siblingSet
  **/
    public List<Integer> getSiblingSet() {
    return siblingSet;
  }

  public void setSiblingSet(List<Integer> siblingSet) {
    this.siblingSet = siblingSet;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SiblingSet siblingSet = (SiblingSet) o;
    return Objects.equals(this.siblingSet, siblingSet.siblingSet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(siblingSet);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SiblingSet {\n");
    
    sb.append("    siblingSet: ").append(toIndentedString(siblingSet)).append("\n");
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

