package controllers;

import apimodels.SiblingSet;

import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Http;
import play.Logger;
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

public class FunctionsApiController extends Controller {

    private final FunctionsApiControllerImp imp;
    private final ObjectMapper mapper;

    @Inject
    private FunctionsApiController(FunctionsApiControllerImp imp) {
        this.imp = imp;
        mapper = new ObjectMapper();
    }


    @ApiAction
    public Result functionsCollatzSiblingsPost() throws Exception {
        JsonNode nodesiblingSet = request().body().asJson();
        SiblingSet siblingSet;
        if (nodesiblingSet != null) {
            siblingSet = mapper.readValue(nodesiblingSet.toString(), SiblingSet.class);
        
        } else {
            siblingSet = null;
        }
        String addr = request().remoteAddress();
        if (request().hasHeader("X-Real-IP-AWS"))
            addr = request().getHeader("X-Real-IP-AWS");
        else if (request().hasHeader("X-Real-IP"))
            addr = request().getHeader("X-Real-IP");
        Logger.debug(addr+": functionsCollatzSiblingsPost: "
                     +Json.toJson(siblingSet));
        
        List<Integer> obj = imp.functionsCollatzSiblingsPost(siblingSet);
        JsonNode result = mapper.valueToTree(obj);
        return ok(result);
        
    }
}
