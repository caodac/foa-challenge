package controllers;

import apimodels.SiblingSet;

import play.mvc.Http;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import javax.validation.constraints.*;

public interface FunctionsApiControllerImpInterface {
    List<Integer> functionsCollatzSiblingsPost(SiblingSet siblingSet) throws Exception;

}
