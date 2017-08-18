package controllers;

import play.shaded.ahc.io.netty.handler.codec.base64.Base64Encoder;
import repository.C2ApiTester;
import repository.ChallengeResponse;
import repository.Repository;
import models.Participant;
import models.Submission;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.math3.util.Precision;

import com.fasterxml.jackson.databind.JsonNode;

import play.Configuration;
import play.Environment;
import play.Logger;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.libs.ws.WSClient;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Call;
import play.libs.mailer.Email;
import play.libs.mailer.MailerClient;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.annotation.processing.Completion;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
    @Inject protected views.html.Puzzle puzzle;

    @Inject protected Repository repo;
    @Inject protected HttpExecutionContext httpExecutionContext;
    @Inject protected Configuration config;
    @Inject protected Environment env;
    @Inject protected WSClient ws;
    @Inject protected MailerClient mailer;

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

    public Integer getMaxStage () {
        Configuration value = config.getConfig("challenge");
        if (value != null) {
            return value.getInt("max-stage", 0);
        }
        return 0;
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

    protected File download (int stage) {
        switch (stage) {
        case 5: return download (stage, "graph-file");
        case 3: return download (stage, "notebook-file");
        }
        return null;
    }
    
    protected File download (int stage, String name) {
        String file = config.getConfig("challenge.c"+stage).getString(name);
        if (file != null)
            return env.getFile(file);
        
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
                                            .challenge(id).url());

                        String action = request().getQueryString("action");
                        if (action != null) {
                            if ("download".equalsIgnoreCase(action)) {
                                File file = download(stage);
                                if (file != null) {
                                    Logger.debug(part.id + ": download file " + file);
                                    return ok(file, false);
                                }
                            } else if ("notebook".equalsIgnoreCase(action)) {
                                Configuration conf = config.getConfig("challenge").getConfig("c3");
                                String c3Url = conf.getString("handler-url");
                                String encodedUrl = Base64.getEncoder().encodeToString(c3Url.getBytes());
                                File file = download(stage);
                                if (file != null) {
                                    // read in file, replace string and send back
                                    try {
                                        BufferedReader reader = new BufferedReader(new FileReader(file));
                                        StringBuilder sb = new StringBuilder();
                                        String line;
                                        while ((line = reader.readLine()) != null) sb.append(line);
                                        reader.close();
                                        String ret = sb.toString().replace("aHR0cHM6Ly9uY2F0cy5pby9jaGFsbGVuZ2UvYzMvaGFuZGxlcg==", encodedUrl);
                                        response().setContentType("application/vnd.jupyter");
                                        response().setHeader("Content-disposition","attachment; filename=Challenge3.ipynb");
                                        return ok(ret.getBytes());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
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
            ("Looking-for-Clues","PLEASE FOCUS ON THE PROBLEM");
        return ok(views.txt.puzzle.render(url));
    }

    Result fatal () {
        return internalServerError
            ("We apologize; there seems to be a great disturbance in our "
             +"force field.\nPlease send an email to "
             +"translator-challenge@mail.nih.gov should\n"
             +"the problem persists.\n");
    }

    String getUrl (Call call) {
        String host = config.getString("play.http.host");
        return host != null ? (host+call.url()) : call.absoluteURL(request());
    }

    String sendmail (Participant part) {
        String url = getUrl (routes.ChallengeController.challenge
                             (part.id.toString()));
        Email email = new Email ()
            .setSubject("[Translator Challenge] Registration")
            .setFrom("\"NCATS Translator Team\" <translator-challenge@mail.nih.gov>")
            .addTo(part.name+" <"+part.email+">")
            .setBodyText(views.txt.registration.render(part, url).body());
        return mailer.send(email);
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
        final String key = config.getString("challenge.puzzle.key");
        final boolean correct = key.equalsIgnoreCase(answer);
        
        Logger.debug(part.email+": answer=\""+answer+"\" => "+correct);
        String response = "Thank you for your submission. If your submission "
            +"is correct, you will receive an email confirmation.\n"
            +Json.prettyPrint(json)+"\n";

        return repo.insertIfAbsent(part).thenApplyAsync(p -> {
                int stage = p.stage.intValue();
                if (stage == 0) {
                    try {                        
                        if (correct) {
                            repo.nextStage(p).toCompletableFuture().join();
                            
                            String mesgId = sendmail (p);
                            Logger.debug("Sending registration "+p.id+" to "
                                         +p.email+": "+mesgId);
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
        DynamicForm dynamicForm = formFactory.form().bindFromRequest();
        String email = dynamicForm.get("email");
        String magic = dynamicForm.get("magic");

        // magic is simply the base64 encoded email
        String myMagic = Base64.getEncoder().encodeToString(email.getBytes());
        boolean magicIsCorrect = magic.equals(myMagic);

        // does this email exist?
        return repo.fetchParticipant(email).thenApplyAsync(part -> {
            if (part == null)
                return badRequest("This email address doesn't correspond"
                                  +" to a valid participant.\n");
            // exists, have they solved C3 before?
            if (part.stage > 3)
                return ok("Challenge 3 has been solved.\n");
            else {
                repo.submission(part, magic)
                    .thenApplyAsync(sub -> {
                        Logger.debug(part.id+": submission "
                                     +sub.id+" => "+magic);
                        return sub;
                    }, httpExecutionContext.current()).exceptionally(t -> {
                            Logger.error("Failed to insert submission for "
                                         +part.id);
                            return null;
                        });
                
                if (!magicIsCorrect)
                    return badRequest("Sorry, invalid magic value "
                                      +"specified. Try again.\n");
                repo.nextStage(part);
                return ok("Success.\n");
            }
        }, httpExecutionContext.current()).exceptionally(t -> {
            return badRequest("This email address doesn't correspond "
                              +"to a valid participant.\n");
        });
    }

    @BodyParser.Of(value = BodyParser.MultipartFormData.class)
    public CompletionStage<Result> handleC7Request(String id) {
        UUID uuid = UUID.fromString(id);
        return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
            Http.MultipartFormData<File> body =
                request().body().asMultipartFormData();
            Http.MultipartFormData.FilePart<File> filePart = body.getFile("c7assoc");
            if (filePart == null) {
                flash ("error", "No association file was provided");
                return redirect(routes.ChallengeController.challenge(id));
            }
            
            File csvFile = filePart.getFile();
            try {
                repo.submission(part, csvFile).thenApplyAsync(sub -> {
                        Logger.debug(part.id+": submission "+sub.id
                                     +" => "+csvFile);
                        return sub;
                    }, httpExecutionContext.current()).exceptionally(t -> {
                            Logger.error("Failed to insert submission for "
                                         +part.id+" "+csvFile);
                            return null;
                        });
                
                List<String> genes = new ArrayList<String>();
                List<String> diseases = new ArrayList<String>();
                double probSum = 0.0;

                CSVReader csvReader = new CSVReader(new FileReader(csvFile), ',', '"', 1); // skip header line
                String[] toks;
                while ((toks = csvReader.readNext()) != null) {
                    if (toks.length != 3) return badRequest("CSV file was not formatted properly.\n");
                    genes.add(toks[0]);
                    diseases.add(toks[1]);
                    probSum += Double.parseDouble(toks[2]);
                }

                // check genes and diseases are unique and equal the proper number
                Set<String> geneSet = new HashSet<String>(genes);
                Set<String> diseaseSet = new HashSet<String>(diseases);

                if (geneSet.size() != 2000) {
                    flash ("error", "Your calculation failed. "
                           +"Incorrect number of genes.");
                    return redirect (routes.ChallengeController.challenge(id));
                }
                
                if (diseaseSet.size() != 500) {
                    flash ("error", "Your calculation failed. Incorrect "
                           +"number of diseases.");
                    return redirect (routes.ChallengeController.challenge(id));
                }
                
                // check that we got the maximal probability
                if (Precision.round(probSum,2) != 0.95) {
                    flash ("error", "Your calculation failed. The "
                           +"sum of knowledge scores is not minimal.");
                }
                else {
                    try {
                        repo.nextStage(part)
                            .toCompletableFuture().get();
                    }
                    catch (Exception ex) {
                        Logger.error
                            ("Can't advance to next stage for "+part.id, ex);
                        flash ("error", "Internal server error; unable "
                               +"to advance to next stage!");
                    }
                }
            } catch (FileNotFoundException e) {
                flash ("error", "Error opening CSV file. <code>"
                       + e.getMessage() + "</code>");
                Logger.error("Error opening CSV file", e);
            } catch (IOException e) {
                flash ("error", "Error opening CSV file. <code>"
                       + e.getMessage() + "</code>");
                Logger.error("Error opening CSV file", e);
            }
            
            return redirect(routes.ChallengeController.challenge(id));
        }, httpExecutionContext.current()).exceptionally(t -> {
            Logger.error("Failed to fetch participant: " + id, t);
            flash("error", "Something bad happened such as a malformed CSV.\n");
            return redirect(routes.ChallengeController.challenge(id));
        });
    }

    boolean checkC5 (Participant part, Map<String, String[]> data) {
        Configuration conf = config.getConfig("challenge").getConfig("c5");
        Configuration optional = conf.getConfig("optional");
        
        for (Map.Entry<String, String[]> me : data.entrySet()) {
            String[] val = me.getValue();
            if (val.length > 0 && !val[0].equals("")) {
                Logger.info(me.getKey()+": "+val[0]);
                try {
                    int iv = Integer.parseInt(val[0]);
                    Integer ans = conf.getInt(me.getKey());
                    boolean opt = false;
                    if (ans == null) {
                        ans = optional.getInt(me.getKey());
                        opt = true;
                    }
                    
                    if (ans != null && !ans.equals(iv)) {
                        Logger.debug
                            (part.id+": incorrect "
                             +me.getKey()+"="+iv+" optional="+opt);
                        
                        if (!opt)
                            return false;
                    }
                }
                catch (NumberFormatException ex) {
                    Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                    return false;
                }
            }
        }
        Logger.debug(part.id+": passes C5!");
        
        return true;
    }

    boolean checkC4 (Participant part, Map<String, String[]> data) {
        Configuration c4 = config.getConfig("challenge").getConfig("c4");
        for (Map.Entry<String, String[]> me : data.entrySet()) {
            String[] val = me.getValue();
            if (val.length > 0 && !val[0].equals("")) {
                Logger.info(me.getKey()+": "+val[0]);
                try {
                    int iv = Integer.parseInt(val[0]);
                    Integer ans = c4.getInt(me.getKey());
                    if (ans != null && !ans.equals(iv)) {
                        Logger.debug
                            (part.id+": incorrect "+me.getKey()+"="+iv);
                        return false;
                    }
                }
                catch (NumberFormatException ex) {
                    Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                    return false;
                }
            }
        }
        
        Logger.debug(part.id+": passes C4!");
        return true;
    }
    
    boolean checkC6 (Participant part, Map<String, String[]> data) {
        String[] target = data.get("target");
        String[] pathway = data.get("pathway");
        String[] symptom = data.get("symptom");
        String[] cell = data.get("cell");
        Logger.info("Checking c6");
        HashMap<Integer,List<String>> answerMap = createAnswerMap();
        boolean bTarget = false;
        boolean bPathway = false;
        boolean bSymptom = false;
        boolean bCell = false;

        if(target != null) {
            HashMap<String,String> mTarget = getTopics(target[0]);
            bTarget=!Collections.disjoint(mTarget.keySet(),answerMap.get(2));

        }
        if(pathway != null){
            HashMap<String,String> mPathway = getTopics(pathway[0]);
            bPathway = (!Collections.disjoint(mPathway.keySet(),answerMap.get(3)));
        }
        if(symptom!=null) {
            HashMap<String,String> mSymptom = getTopics(symptom[0]);
            bSymptom=(!Collections.disjoint(mSymptom.keySet(),answerMap.get(4)));
        }
        if(cell != null){
            HashMap<String,String> mCell = getTopics(cell[0]);
            bCell = (!Collections.disjoint(mCell.keySet(),answerMap.get(5)));
        }
//        if(bTarget)
//        {
//            Logger.info("target");
//        }
//        if(bPathway)
//        {
//            Logger.info("path");
//
//        }
//        if(bSymptom)
//        {
//            Logger.info("symptom");
//        }
//        if(bCell)
//        {
//            Logger.info("cell");
//        }
        return bTarget && bPathway && bSymptom && bCell;
    }
    ChallengeResponse checkC2 (Participant part, Map<String, String[]> data) {
        String[] apiurl = data.get("API-URI");
        if (apiurl == null || apiurl.length == 0 || apiurl[0].equals("")) {
            Logger.warn(part.id+": no API-URI parameter specified!");
            return new ChallengeResponse(0, "No API-URI parameter specified!");
        }

        return C2ApiTester.main(ws, part.id, apiurl[0]);
    }
    HashMap<Integer,List<String>> createAnswerMap() {
        HashMap<Integer, List<String>> answerMap = new HashMap();
        answerMap.put(1, Arrays.asList("D000068877"));
        answerMap.put(2,Arrays.asList("D019009"));
        answerMap.put(3,Arrays.asList("D015398","D020935"));
        answerMap.put(4,Arrays.asList("D000402","D012130","D004418","D016535"));
        answerMap.put(5,Arrays.asList("D008407"));
        answerMap.put(6,Arrays.asList("D001249"));

        return answerMap;
    }
    HashMap<String,String> getTopics(String pmid){
        HashMap<String,String> topics = new HashMap<String,String>();
        try{
            URL url = new URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id="
                    +pmid+
                    "&retmode=xml");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("charset", "utf-8");
            DataOutputStream wr = new DataOutputStream (
                    connection.getOutputStream());
            wr.close();

            BufferedReader br = new BufferedReader((new InputStreamReader(connection.getInputStream(),"UTF-8")));
            StringBuilder response = new StringBuilder();
            String line;
            while((line = br.readLine())!=null)
            {
                response.append(line);
            }
            DocumentBuilder db = DocumentBuilderFactory
                    .newInstance().newDocumentBuilder();
            Document doc = db.parse
                    (new InputSource(new StringReader(response.toString())));
            NodeList nodes = doc.getElementsByTagName("MeshHeading");

            for(int i=0;i<nodes.getLength();i++)
            {

                Element elem = (Element) nodes.item(i);
                NodeList headings = elem.getElementsByTagName("DescriptorName");
                for(int j = 0; j<headings.getLength(); j++) {
                    Element heading = (Element) headings.item(j);
                    String topic = heading.getTextContent();
                    String meshId = heading.getAttribute("UI");
                    topics.put(meshId,topic);
                }
            }
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return topics;
    }

    Result advance (Participant part, Map<String, String[]> data) {
        Submission sub = null;
        try {
            sub = repo.submission(part, data)
                .toCompletableFuture().join();
        }
        catch (Exception ex) {
            Logger.error("Can't create submission for "+part.id, ex);
        }
        
        // check the answer
        boolean passed = false;
        switch (part.stage) {
        case 1:
            // advance to next stage
            passed = true;
            break;
            
        case 2:
            ChallengeResponse resp = checkC2 (part, data);
            Logger.debug(part.id+": "+resp.success
                         +": "+resp.message);
            if (resp.success > 0) {
                passed = true;
                break;
            } else {
                return ok(resp.message);
            }
        case 4:
            passed = checkC4 (part, data);
            break;
            
        case 5:
            passed = checkC5 (part, data);
            break;
            
        case 6:
            passed = checkC6 (part, data);
            break;
        }
        
        if (passed) {
            try {
                int stage = part.stage;
                repo.nextStage(part).toCompletableFuture().join();
                if (stage > 1) {
                    flash ("success", "Congratulations on completing "
                           +"challenge "+stage+"!");
                }
                
                if (sub != null) {
                    Logger.debug(part.id+" advance to next stage "
                                 +"with submission "+sub.id);
                }
            }
            catch (Exception ex) {
                Logger.error("Can't advance to next stage for "+part.id, ex);
                flash ("error", "An internal server error has occurred; "
                       +"please try again!");
            }
        }
        else {
            flash ("error", "One or more of your answers "
                   +"are incorrect; please try again!");
        }
        
        return redirect
            (routes.ChallengeController.challenge(part.id.toString()));
    }

    @BodyParser.Of(value = BodyParser.FormUrlEncoded.class)
    public CompletionStage<Result> submit (final String id,
                                           final Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetchParticipant(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        if (!stage.equals(part.stage))
                            return redirect(routes.ChallengeController
                                            .challenge(id));
                        
                        Map<String, String[]> data =
                            request().body().asFormUrlEncoded();

                        
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
                        File foa = download (stage, "foa-file");
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
}
