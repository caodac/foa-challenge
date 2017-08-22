package controllers;

import apimodels.InlineResponse200;

import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Http;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import swagger.SwaggerUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.validation.constraints.*;

import swagger.SwaggerUtils.ApiAction;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaPlayFrameworkCodegen", date = "2017-08-19T20:56:55.877Z")

public class TypesApiController extends Controller {

    private final TypesApiControllerImp imp;
    private final ObjectMapper mapper;

    @Inject
    private TypesApiController(TypesApiControllerImp imp) {
        this.imp = imp;
        mapper = new ObjectMapper();
    }


    @ApiAction
    public Result typesGet() throws Exception {
        List<InlineResponse200> obj = imp.typesGet();
        JsonNode result = mapper.valueToTree(obj);
        return ok(result);
        
    }
}
