package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Participant;
import play.Configuration;
import play.Environment;
import play.Logger;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import repository.ParticipantRepository;

import javax.inject.Inject;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class ChallengeController extends Controller {

    @Inject FormFactory formFactory;

    /*
     * views
     */
    @Inject views.html.Challenge challenge;
    @Inject views.html.Welcome welcome;
    @Inject views.html.Puzzle puzzle;
    
    @Inject ParticipantRepository repo;
    @Inject HttpExecutionContext httpExecutionContext;
    @Inject Configuration config;
    @Inject Environment env;

    static CompletionStage<Result> badRequestAsync (String mesg) {
        return supplyAsync (() -> {
                return badRequest (mesg);
            }); 
    }

    static CompletionStage<Result> async (Result result) {
        return supplyAsync (() -> {
                return result;
            });
    }

    public Integer getMaxStage () {
        Configuration value = config.getConfig("challenge");
        if (value != null) {
            return value.getInt("maxStage");
        }
        return null;
    }
    
    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        //return ok(views.txt.nothing.render());
        return ok (puzzle.render());
    }

    public CompletionStage<Result> challenge (final String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetch(uuid).thenApplyAsync(part -> {
                    if (part != null)
                        return ok (challenge.render(part, part.stage));
                    return ok (welcome.render());
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+id, t);
                        return ok (welcome.render());
                    });
        }
        catch (Exception ex) {
            Logger.warn("Not a valid challenge id: "+id);
            return async (ok (welcome.render()));
        }
    }

    public CompletionStage<Result> stage (final String id,
                                          final Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetch(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        if (stage > part.stage || stage < 1)
                            return redirect(routes.ChallengeController
                                            .challenge(id).url());
                        return ok (challenge.render(part, stage));
                    }
                    return ok (welcome.render());
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+id, t);
                        return ok (welcome.render());
                    });
        }
        catch (Exception ex) {
            Logger.warn("Not a valid challenge id: "+id);
            return async (ok (welcome.render()));
        }       
    }

    public Result welcome () {
        return ok (welcome.render());
    }

    public Result puzzle () {
        return ok (puzzle.render());
    }

    @BodyParser.Of(value = BodyParser.Json.class)
    public CompletionStage<Result> register () {
        JsonNode json = request().body().asJson();
        Logger.debug(">>> "+json);
        if (!json.has("email"))
            return badRequestAsync ("No \"email\" field provided!");
        
        Participant part = new Participant ();
        part.stage = 1;
        part.email = json.get("email").asText();
        if (part.email == null || part.email.equals(""))
            return badRequestAsync ("Invalid email");
        
        if (!json.has("firstname"))
            return badRequestAsync ("No \"firstname\" field provided!");
        part.firstname = json.get("firstname").asText();
        
        if (!json.has("lastname"))
            return badRequestAsync ("No \"lastname\" field provided!");
        part.lastname = json.get("lastname").asText();

        part.c3Solved = false;

        return repo.insert(part).thenApplyAsync(id -> {
                return ok("Successfully registered participant: "+id+"\n");
            }, httpExecutionContext.current()).exceptionally(t -> {
                    Logger.error("Failed to register participant: "
                                 +part.email, t);
                    return badRequest ("Sorry, it appears the provided email "
                                       +"has already been registered!");
                });
    }

    public CompletionStage<Result> participant (String query) {
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        
        int pos = query.indexOf('@');
        if (pos > 0) { // email
            return repo.fetch(query).thenApplyAsync(part -> {
                    return ok (Json.toJson(part));
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+query, t);
                        return badRequest ("Unable to locate participatan.\n");
                    });
        }
        else {
            try {
                UUID id = UUID.fromString(query);
                return repo.fetch(id).thenApplyAsync(part -> {
                        return ok (Json.toJson(part));
                    }, httpExecutionContext.current()).exceptionally(t -> {
                            Logger.error("Failed to fetch participant: "
                                         +query, t);
                            return badRequest
                                ("Unable to locate participant.\n");
                        });
            }
            catch (Exception ex) {
                return badRequestAsync ("Not a valid id: "+query+"\n");
            }
        }
    }

    public CompletionStage<Result> next (String id) {
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetch(uuid).thenApplyAsync(part -> {
                    if (part.stage < getMaxStage ()) {
                        try {
                            Optional<Participant> ret = repo.nextStage(part)
                                .toCompletableFuture().get();
                            if (ret.isPresent())
                                return ok (Json.toJson(ret.get()));
                        }
                        catch (Exception ex) {
                            Logger.error("Failed to update next stage: "
                                         +part.id, ex);
                        }
                    }
                    return ok (Json.toJson(part));
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch and "
                                     +"update participant: "+id, t);
                        return badRequest ("Unable to locate participant.\n");
                    });
        }
        catch (Exception ex) {
            return badRequestAsync ("Not a valid id: "+id+"\n");
        }
    }

    public CompletionStage<Result> list () {
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        return repo.list().thenApplyAsync(list -> {
                return ok (Json.toJson(list));
            }, httpExecutionContext.current()).exceptionally(t -> {
                    Logger.error("Failed to fetch participant list!", t);
                    return internalServerError (t.getMessage());
                });
    }

    public CompletionStage<Result> handleC3Request() {
        DynamicForm dynamicForm = formFactory.form().bindFromRequest();
        String email = dynamicForm.get("email");
        String magic = dynamicForm.get("magic");

        // magic is simply the base64 encoded email
        String myMagic = Base64.getEncoder().encodeToString(email.getBytes());
        boolean magicIsCorrect = magic.equals(myMagic);

        // does this email exist?
        return repo.fetch(email).thenApplyAsync(part -> {
            if (part == null)
                return badRequest("This email address doesn't correspond to a valid participant.\n");
            // exists, have they solved C3 before?
            if (part.c3Solved)
                return ok("Challenge 3 has been solved.\n");
            else {
                if (!magicIsCorrect)
                    return badRequest("Sorry, invalid magic value specified. Try again.\n");
                part.c3Solved = true;
                repo.nextStage(part);
                return ok("Success.\n");
            }
        }, httpExecutionContext.current()).exceptionally(t -> {
            return badRequest("This email address doesn't correspond to a valid participant.\n");
        });

    }
}
