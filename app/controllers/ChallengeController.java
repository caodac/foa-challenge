package controllers;

import play.shaded.ahc.io.netty.handler.codec.base64.Base64Encoder;
import repository.ChallengeResponse;
import repository.Repository;
import models.Participant;
import models.Submission;

import com.fasterxml.jackson.databind.JsonNode;

import play.Configuration;
import play.Environment;
import play.Logger;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;

import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Call;
import play.libs.mailer.Email;
import play.libs.mailer.MailerClient;
import org.webjars.play.WebJarsUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class ChallengeController extends Controller {
    static final String JSON_FORMAT =
        "{\n\"email\":\"EMAIL\",\n"+
        "\"name\":\"TEAM NAME\",\n"+
        "\"answer\":\"YOUR ANSWER TO THE PUZZLE\"\n}\n";

    @Inject protected FormFactory formFactory;

    /*
     * views
     */
    @Inject protected views.html.Challenge challenge;
    @Inject protected views.html.Welcome welcome;

    @Inject protected Repository repo;
    @Inject protected HttpExecutionContext httpExecutionContext;
    @Inject protected Environment env;

    @Inject protected MailerClient mailer;
    @Inject protected Configuration config;
    
    @Inject public ChallengeApp app;
    @Inject public WebJarsUtil webjars;

    public ChallengeController () {
    }

    protected CompletionStage<Result> badRequestAsync (String mesg) {
        return supplyAsync (() -> {
                return badRequest (mesg);
            }); 
    }

    static protected CompletionStage<Result> async (Result result) {
        return supplyAsync (() -> {
                return result;
            });
    }

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok (views.html.index.render(this, app.getRandomQuote()));
    }

    public CompletionStage<Result> rank (final String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        try {
                            Double pct = repo.percentile(part)
                                .toCompletableFuture().join();
                            if (pct != null)
                                return ok (String.format
                                           ("%1$.1f", pct*100.0));
                        }
                        catch (Exception ex) {
                            Logger.error("Can't calculate percentile!", ex);
                        }
                        return badRequest ("");
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
    
    public CompletionStage<Result> challenge (final String id) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null && part.stage > 0)
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

    Result doAction (Participant part, int stage, String action) {
        if ("download".equalsIgnoreCase(action)) {
            File file = app.download(stage);
            if (file != null) {
                Logger.debug(part.id + ": download file " + file);
                return ok(file, false);
            }
        }
        else if ("notebook".equalsIgnoreCase(action)) {
            Configuration conf = config.getConfig("challenge").getConfig("c3");
            String c3Url = conf.getString("handler-url");
            String encodedUrl = Base64.getEncoder()
                .encodeToString(c3Url.getBytes());
            File file = app.download(stage);
            if (file != null) {
                // read in file, replace string and send back
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String ret = sb.toString()
                        .replace("aHR0cHM6Ly9uY2F0cy5pby9jaGFsbGVuZ2UvYzMvaGFuZGxlcg==", encodedUrl);
                    response().setContentType("application/vnd.jupyter");
                    response().setHeader("Content-disposition","attachment; filename=Challenge3.ipynb");
                    return ok(ret.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return null;
    }

    public CompletionStage<Result> stage (final String id,
                                          final Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        if (stage > part.stage || stage < 1)
                            return redirect(routes.ChallengeController
                                            .challenge(id));

                        String action = request().getQueryString("action");
                        if (action != null) {
                            Result r = doAction (part, stage, action);
                            if (r != null)
                                return r;
                        }
                        
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
        //return ok (puzzle.render());
        String url = getUrl (routes.ChallengeController.register());
        response().setHeader
            ("Looking-For-Clues","PLEASE FOCUS ON THE PROBLEM");
        return ok(views.txt.puzzle.render(url));
    }

    Result fatal () {
        return internalServerError
            ("We apologize; there seems to be a great disturbance in our "
             +"force field.\nPlease send an email to "
             +"ncats.io-support@mail.nih.gov should\n"
             +"the problem persists.\n");
    }

    String getUrl (Call call) {
        String host = config.getString("play.http.host");
        return host != null ? (host+call.url()) : call.absoluteURL(request());
    }

    String sendmail (Participant part) {
        String url = getUrl (routes.ChallengeController.challenge
                             (part.id.toString()));
        Map<String, String> mesg = new TreeMap<>();
        mesg.put("subject", "[Translator Challenge] Registration");
        mesg.put("from",
                 "\"NCATS Translator Team\" <ncats.io-support@mail.nih.gov>");
        mesg.put("to", part.name+" <"+part.email+">");
        mesg.put("body", views.txt.registration.render(part, url).body());
        return app.sendmail(mesg);
    }

    String sendmail (ChallengeApp.PuzzleResult result,
                     JsonNode json, Participant part) {
        Map<String, String> mesg = new TreeMap<>();
        mesg.put("subject", "[Translator Challenge] You're almost there");
        mesg.put("from",
                 "\"NCATS Translator Team\" <ncats.io-support@mail.nih.gov>");
        mesg.put("to", part.name+" <"+part.email+">");
        mesg.put("body", "Hi "+part.name+",\n\nThank you for your submission! We just want to let you know your answer is almost correct. Please keep trying!\n\nSincerely,\nNCATS Translator Team\n\n--\n"+Json.prettyPrint(json));
        return app.sendmail(mesg);
    }

    @BodyParser.Of(value = BodyParser.Json.class)
    public CompletionStage<Result> mailer () {
        JsonNode mesg = request().body().asJson();
        String seed = null, hash = null;
        if (mesg.has("seed"))
            seed = mesg.get("seed").asText();
        if (mesg.has("hash"))
            hash = mesg.get("hash").asText();
        
        if (seed == null || hash == null || !app.validateMailer(seed, hash))
            return supplyAsync (() -> {
                    return forbidden ("Not authorized!");
                });

        return supplyAsync (() -> {
                Logger.debug("<<< Mailer >>>\n"+mesg);
                Email email = new Email ()
                    .setSubject(mesg.get("subject").asText())
                    .setFrom(mesg.get("from").asText())
                    .addTo(mesg.get("to").asText())
                    .setBodyText(mesg.get("body").asText());
                return ok (mailer.send(email));
            }, httpExecutionContext.current());
    }
    
    @BodyParser.Of(value = BodyParser.AnyContent.class)
    public CompletionStage<Result> register () {        
        JsonNode json = request().body().asJson();
        Logger.debug(">>> "+json);
        if (json == null)
            return badRequestAsync ("Please make sure your Content-Type "
                                    +"header is set to application/json!\n");

        if (!json.has("email"))
            return badRequestAsync ("Bad JSON message; "
                                    +"please use the format:\n"+JSON_FORMAT);
        
        Participant part = new Participant ();
        part.stage = 0;
        part.email = json.get("email").asText();
        if (part.email == null || part.email.equals(""))
            return badRequestAsync ("Bad JSON message; "
                                    +"please use the format:\n"+JSON_FORMAT);
        
        if (!json.has("name"))
            return badRequestAsync ("Bad JSON message; "
                                    +"please use the format:\n"+JSON_FORMAT);
        part.name = json.get("name").asText();
        if (part.name == null || part.name.equals(""))
            return badRequestAsync ("Bad JSON message; "
                                    +"please use the format:\n"+JSON_FORMAT);

        if (!json.has("answer"))
            return badRequestAsync ("Bad JSON message; "
                                    +"please use the format:\n"+JSON_FORMAT);

        final String answer = json.get("answer").asText().trim();
        final ChallengeApp.PuzzleResult result = app.checkPuzzle(answer);

        if (app.endDateDuration() < 0) {
            return async (ok ("Sorry, the challenge is now closed; "
                              +"your answer is "
                              +(result==ChallengeApp.PuzzleResult.Correct
                                ? "CORRECT!":"INCORRECT.")));
        }

        Logger.debug(part.email+": answer=\""+answer+"\" => "+result);
        String response = "Thank you for your submission. If your submission "
            +"is correct, you will receive an email confirmation.\n"
            +Json.prettyPrint(json)+"\n";

        return repo.insertIfAbsent(part).thenApplyAsync(p -> {
                int stage = p.stage.intValue();
                if (stage == 0) {
                    try {                       
                        switch (result) {
                        case Correct:
                            repo.nextStage(p).toCompletableFuture().join();

                            String mesgId = sendmail (p);
                            Logger.debug("Sending registration "+p.id+" to "
                                         +p.email+": "+mesgId);
                            break;

                        case NotOrder:
                        case Partial:
                            { String id = sendmail (result, json, p);
                              Logger.debug("Sending ("+result
                                           +") encouragement "
                                           +" to "+p.email+": "+id);
                            }
                            break;
                        }
                        
                        Submission sub = repo.submission
                            (p, json).toCompletableFuture().join();

                        return ok (response+"submission-id: "+sub.id+"\n");
                    }
                    catch (Exception ex) {
                        Logger.error("Can't create submission", ex);
                        return fatal ();
                    }
                }
                else {
                    /*
                    return ok ("Please visit "
                               +getUrl(routes.ChallengeController.challenge
                                       (p.id.toString()))
                               +"\nto start your challenge!\n");
                    */

                    // let's make the response the same so that people don't
                    // just figure out whether they've correctly answered
                    // the puzzle by keep doing post
                    String fake = UUID.randomUUID().toString();
                    Logger.debug("Participant "+p.id+" already passed stage 0;"
                                 +" return a fake uuid for this submission: "
                                 +fake);
                    return ok (response+"submission-id: "+fake+"\n");
                }
            }, httpExecutionContext.current()).exceptionally(t -> {
                    Logger.error("Failed to register participant: "
                                 +part.email, t);
                    return fatal ();
                });
    }

    public CompletionStage<Result> handleC3Request() {
        if (app.endDateDuration() < 0) {
            return badRequestAsync ("Sorry, the challenge is now closed!");
        }
        
        DynamicForm dynamicForm = formFactory.form().bindFromRequest();
        String email = dynamicForm.get("email");
        String magic = dynamicForm.get("magic");

        // does this email exist?
        return repo.fetchParticipant(email).thenApplyAsync(part -> {
            if (part == null)
                return badRequest ("This email address doesn't correspond"
                                  +" to a valid participant.\n");
            else if (part.stage < 3)
                return badRequest
                    ("Are you trying to subvert the challenge?\n");
            
            // magic is simply the base64 encoded email
            String myMagic = "";
            try {
                myMagic = Base64.getEncoder().encodeToString(email.getBytes());

                // try deleting padding, which is actually optional under Bas64 spec and Java is too strict
                if (!myMagic.equals(magic))
                    myMagic = Base64.getEncoder().withoutPadding().encodeToString(email.getBytes());
            } catch (Exception e) {;}

            // multiple base64 encoded strings decode to same string, so we need to test reverse process as well
            String decodedEmail = "";
            try {
                String padmagic = magic.substring(0,
                        magic.indexOf('=') > 0 ? magic.indexOf('=') : magic.length());
                for (int i=0; i<4; i++)
                    if (!email.equals(decodedEmail)) {
                        try {
                            decodedEmail = new String(Base64.getDecoder().decode(padmagic));
                        } catch (Exception e) {;}
                        padmagic = padmagic + "=";
                    }
            } catch (Exception e) {;}
            boolean magicIsCorrect = decodedEmail.trim().equals(email.trim()) || myMagic.equals(magic);
            //System.err.println(magicIsCorrect+":"+magic+":"+myMagic+":"+email+":"+decodedEmail);
            
            // exists, have they solved C3 before?
            if (part.stage > 3)
                return ok("Challenge 3 has been solved.\n");
            else {
                Submission sub = repo.submission(part, magic)
                    .toCompletableFuture().join();
                Logger.debug(part.id+": c3 submission "+sub.id);

                if (!magicIsCorrect)
                    return badRequest("Sorry, invalid magic value "
                                      +"specified. Try again.\n");
                repo.nextStage(part);
                return ok("Success! Please visit "+getUrl
                          (routes.ChallengeController.challenge
                           (part.id.toString()))
                          + " to continue with the challenge.\n");
            }
        }, httpExecutionContext.current()).exceptionally(t -> {
            if (email == null) {
                return badRequest("Email address not specified.\n");
            }
            return badRequest("This email address doesn't correspond "
                              +"to a valid participant.\n");
        });
    }

    @BodyParser.Of(value = BodyParser.MultipartFormData.class)
    public CompletionStage<Result> handleC7Request(String id) {
        if (app.endDateDuration() < 0) {
            flash ("error", "Sorry, the challenge is now closed!");
            return async (redirect(routes.ChallengeController.challenge(id)));
        }
        
        UUID uuid = UUID.fromString(id);
        return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
            Http.MultipartFormData<File> body =
                request().body().asMultipartFormData();
            Http.MultipartFormData.FilePart<File> filePart =
                body.getFile("c7assoc");
            
            if (filePart == null) {
                /*
                flash ("error", "No association file was provided");
                return redirect(routes.ChallengeController.challenge(id));
                */
                try {
                    repo.nextStage(part)
                        .toCompletableFuture().join();
                    flash ("success",
                           "Congratulations on completing task 7!");
                }
                catch (Exception ex) {
                    Logger.error
                        ("Can't advance to next stage for "+part.id, ex);
                    flash ("error", "Internal server error; unable "
                           +"to advance to next stage!");
                }
            }
            else {
                File csvFile = filePart.getFile();
                try {
                    ChallengeResponse resp = app.checkC7(part, csvFile);
                    if (resp.success > 0) {
                        repo.nextStage(part)
                            .toCompletableFuture().join();
                        flash ("success",
                               "Congratulations on completing task 7!");
                    }
                    else {
                        flash ("error", resp.message);
                    }
                }
                catch (Exception ex) {
                    Logger.error
                        ("Can't advance to next stage for "+part.id, ex);
                    flash ("error", "Internal server error; unable "
                           +"to advance to next stage!");
                }
            
                // save the file submission regardless
                repo.submission(part, csvFile).thenApplyAsync(sub -> {
                        Logger.debug(part.id+": submission "+sub.id
                                     +" => "+csvFile);
                        return sub;
                    }, httpExecutionContext.current()).exceptionally(t -> {
                            Logger.error("Failed to insert submission for "
                                         +part.id+" "+csvFile);
                            return null;
                        });
            }
            
            return redirect(routes.ChallengeController.challenge(id));
        }, httpExecutionContext.current()).exceptionally(t -> {
            Logger.error("Failed to fetch participant: " + id, t);
            flash("error", "Something bad happened such as a malformed CSV.\n");
            return redirect(routes.ChallengeController.challenge(id));
        });
    }

    ChallengeResponse submit (Participant part,
                              int stage, Map<String, String[]> data) {        
        Submission sub = null;
        try {
            sub = repo.submission(part, data)
                .toCompletableFuture().join();
            if (sub != null) {
                Logger.debug(part.id+" advance to next stage "
                             +"with submission "+sub.id);
            }
        }
        catch (Exception ex) {
            Logger.error("Can't create submission for "+part.id, ex);
        }

        // check the answer
        ChallengeResponse response = null;      
        switch (stage) {
        case 1:
            // advance to next stage
            response = new ChallengeResponse (1, null);
            session().clear();
            break;

        case 2:
            response = app.checkC2(part, data);
            break;
            
        case 4:
            response = app.checkC4(part, data);
            break;

        case 5:
            response = app.checkC5(part, data);
            break;

        case 6:
            response = app.checkC6(part, data);
            break;
        }
        
        return response;
    }
    

    Result advance (Participant part, Map<String, String[]> data) {
        ChallengeResponse response = submit (part, part.stage, data);
        if (response == null) {
        }
        else {
            Logger.debug(part.id+": "+response.success+": "+response.message);
            for (Map.Entry<String, String> me : response.variables.entrySet()) {
                String value = me.getValue();
                if (value != null)
                    session (me.getKey(), value);
                else
                    session().remove(me.getKey());
            }

            if (response.success > 0) {
                try {
                    int stage = part.stage;
                    repo.nextStage(part).toCompletableFuture().join();
                    if (stage > 1) {
                        flash ("success", "Congratulations on completing "
                               +"task "+(stage-1)+"!");
                    }                    
                }
                catch (Exception ex) {
                    Logger.error
                        ("Can't advance to next stage for "+part.id, ex);
                    flash ("error", "An internal server error has occurred; "
                           +"please send an email to " +app.email +" if the "
                           +"the problem persists.");
                }
            }
            else {
                flash ("error", response.message);
            }
        }

        return redirect
            (routes.ChallengeController.challenge(part.id.toString()));
    }

    @BodyParser.Of(value = BodyParser.FormUrlEncoded.class)
    public CompletionStage<Result> submit (final String id,
                                           final Integer stage) {
        if (app.endDateDuration() < 0) {
            flash ("error", "Sorry, the challenge is now closed!");
            return async (redirect(routes.ChallengeController.challenge(id)));
        }
        
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        Map<String, String[]> data =
                            request().body().asFormUrlEncoded();
                        
                        if (!stage.equals(part.stage)) {
                            ChallengeResponse resp = submit (part, stage, data);
                            if (resp != null) {
                                for (Map.Entry<String, String> me
                                         : resp.variables.entrySet()) {
                                    String value = me.getValue();
                                    if (value != null)
                                        session (me.getKey(), value);
                                    else
                                        session().remove(me.getKey());
                                }
                            }
                            
                            return redirect(routes.ChallengeController
                                            .challenge(id));
                        }

                        return advance (part, data);
                    }
                    
                    return redirect (routes.ChallengeController.welcome());
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+id, t);
                        return redirect (routes.ChallengeController.welcome());
                    });
        }
        catch (Exception ex) {
            Logger.warn("Not a valid challenge id: "+id);
            return async (redirect (routes.ChallengeController.welcome()));
        }
    }

    public CompletionStage<Result> foa (final String id,
                                        final Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null && stage <= part.stage) {
                        File foa = app.download(stage, "foa-file");
                        if (foa != null) {
                            return ok (foa);
                        }
                        else {
                            Logger.warn
                                ("Can't download FOA file for stage "+stage);
                        }
                    }
                    return redirect (routes.ChallengeController.welcome());
                }, httpExecutionContext.current()).exceptionally(t -> {
                        Logger.error("Failed to fetch participant: "+id, t);
                        return redirect (routes.ChallengeController.welcome());
                    });
        }
        catch (Exception ex) {
            Logger.warn("Not a valid challenge id: "+id);
            return async (redirect (routes.ChallengeController.welcome()));
        }
    }

    Result getFile (String name) {
        try {
            File file = env.getFile("conf/"+name);
            if (!file.exists())
                Logger.warn("File "+file+" doesn't exist!");
            return ok (file);
        }
        catch (Exception ex) {
            Logger.error("Can't locate file for Q1!", ex);
            return fatal ();
        }       
    }

    public Result q1 () {
        return getFile ("q1-disease-list.txt");
    }

    public Result q2 () {
        return getFile ("q2-drugandcondition-list.txt");
    }
}
