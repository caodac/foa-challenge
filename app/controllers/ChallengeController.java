package controllers;

import javax.inject.Inject;
import play.Logger;
import play.mvc.*;
import java.util.concurrent.*;
import play.libs.concurrent.HttpExecutionContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import models.*;
import repository.*;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class ChallengeController extends Controller {

    @Inject views.html.Challenge challenge;
    @Inject views.html.Welcome welcome;
    @Inject ParticipantRepository repo;
    @Inject HttpExecutionContext httpExecutionContext;
    
    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        //return ok(views.txt.nothing.render());
        return ok (welcome.render());
    }

    public Result challenge (String id, Integer stage) {
        if (!"foobar".equals(id))
            return ok (welcome.render());
        
        return ok (challenge.render(id, stage));
    }

    public Result welcome () {
        return ok (welcome.render());
    }

    @BodyParser.Of(value = BodyParser.Json.class)
    public CompletionStage<Result> register () {
        JsonNode json = request().body().asJson();
        Logger.debug(">>> "+json);
        if (!json.has("email"))
            return CompletableFuture.supplyAsync(() -> {
                    return badRequest ("No \"email\" field set!");
                });

        Participant part = new Participant ();
        part.email = json.get("email").asText();
        if (json.has("firstname"))
            part.firstname = json.get("firstname").asText();
        if (json.has("lastname"))
            part.lastname = json.get("lastname").asText();
        
        return repo.insert(part).thenApplyAsync(id -> {
                return ok("Successful register participant: "+id);
            }, httpExecutionContext.current()).exceptionally(t -> {
                    Logger.error("Failed to insert participant: "+part.email, t);
                    return badRequest ("Email is already registered!");
                });
    }
}
