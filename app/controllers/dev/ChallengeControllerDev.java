package controllers.dev;

import repository.Repository;
import models.Participant;
import models.Submission;

import com.fasterxml.jackson.databind.JsonNode;
import io.ebean.Transaction;

import play.Logger;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Result;
import play.mvc.Call;
import play.Configuration;
import play.mvc.Controller;

import java.io.*;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletionStage;

import controllers.ChallengeController;
import static java.util.concurrent.CompletableFuture.supplyAsync;

public class ChallengeControllerDev extends ChallengeController {
    
    public CompletionStage<Result> participant (String query) {
        int pos = query.indexOf('@');
        if (pos > 0) { // email
            return repo.fetchParticipant(query).thenApplyAsync(part -> {
                    return ok (Json.toJson(part));
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+query, t);
                        return badRequest ("Unable to locate participatan.\n");
                    });
        }
        else {
            try {
                UUID id = UUID.fromString(query);
                return repo.fetchParticipant(id).thenApplyAsync(part -> {
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

    public CompletionStage<Result> reset(String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                part.stage = 1;
                part.save();
                return ok(Json.toJson(part));
            }, httpExecutionContext.current()).exceptionally(t -> {
                Logger.error("Failed to fetch and "
                        + "update participant: " + id, t);
                return badRequest("Unable to locate participant.\n");
            });
        } catch (Exception ex) {
            return badRequestAsync("Not a valid id: " + id + "\n");
        }
    }

    public CompletionStage<Result> next (String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part.stage < getMaxStage ()) {
                        try {
                            Optional<Participant> ret = repo.nextStage(part)
                                .toCompletableFuture().get();
                        }
                        catch (Exception ex) {
                            Logger.error("Failed to update next stage: "
                                         +part.id, ex);
                        }
                    }
                    
                    return redirect (controllers.routes
                                     .ChallengeController.challenge(id));
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
        return repo.participants().thenApplyAsync(list -> {
                return ok (Json.toJson(list));
            }, httpExecutionContext.current()).exceptionally(t -> {
                    Logger.error("Failed to fetch participant list!", t);
                    return internalServerError (t.getMessage());
                });
    }

    public CompletionStage<Result> submissions (String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    try {
                        List<Submission> subs = repo.submissions(part)
                            .toCompletableFuture().get();
                        return ok (Json.toJson(subs));
                    }
                    catch (Exception ex) {
                        return internalServerError (ex.getMessage());
                    }
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch submissions for "+id, t);
                        return internalServerError (t.getMessage());
                    });
        }
        catch (Exception ex) {
            Logger.error("Not a valid uuid: "+id, ex);
            return async (internalServerError (ex.getMessage()));
        }
    }

    /* advance to next stage but don't have to submit the answer */
    public CompletionStage<Result> advance (String id, Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        if (!stage.equals(part.stage))
                            return redirect(controllers.routes
                                            .ChallengeController.challenge(id));
                        try {
                            Optional<Participant> opt = repo.nextStage(part)
                                .toCompletableFuture().get();
                            if (!opt.isPresent())
                                return redirect
                                    (controllers.routes
                                     .ChallengeController.welcome());
                        }
                        catch (Exception ex) {
                            Logger.error("Can't increment next stage for "
                                         +part.id, ex);
                            return redirect
                                (controllers.routes.ChallengeController.welcome());
                        }
                        
                        return redirect
                            (controllers.routes.ChallengeController.challenge(id));
                    }
                    
                    return redirect
                        (controllers.routes.ChallengeController.welcome());
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+id, t);
                        return redirect (controllers.routes
                                         .ChallengeController.welcome());
                    });
        }
        catch (Exception ex) {
            Logger.warn("Not a valid challenge id: "+id);
            return async (redirect (controllers.routes
                                    .ChallengeController.welcome()));
        }
    }
}
