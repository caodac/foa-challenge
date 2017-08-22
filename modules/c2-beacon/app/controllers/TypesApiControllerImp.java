package controllers;

import apimodels.InlineResponse200;

import play.mvc.Http;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.FileInputStream;
import javax.validation.constraints.*;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaPlayFrameworkCodegen", date = "2017-08-19T20:56:55.877Z")

public class TypesApiControllerImp implements TypesApiControllerImpInterface {
    @Override
    public List<InlineResponse200> typesGet() throws Exception {
        InlineResponse200 type = new InlineResponse200();
        type.setId("collatzSiblings");
        type.setDesc("find collatz siblings");
        type.setIdmap("/functions/collatzSiblings");
        
        //Do your magic!!!
        return java.util.Arrays.asList(type);
    }

}
