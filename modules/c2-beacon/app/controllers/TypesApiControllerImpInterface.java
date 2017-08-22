package controllers;

import apimodels.InlineResponse200;

import play.mvc.Http;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import javax.validation.constraints.*;

public interface TypesApiControllerImpInterface {
    List<InlineResponse200> typesGet() throws Exception;

}
