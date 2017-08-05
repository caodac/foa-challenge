package controllers;

import au.com.bytecode.opencsv.CSVReader;
import com.fasterxml.jackson.databind.JsonNode;
import io.ebean.Transaction;
import models.Participant;
import org.apache.commons.math3.util.Precision;
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
import repository.C2ApiTester;
import repository.ChallengeResponse;
import repository.ParticipantRepository;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
    @Inject views.html.TTT ttt;
    
    @Inject ParticipantRepository repo;
    @Inject HttpExecutionContext httpExecutionContext;
    @Inject Configuration config;
    @Inject Environment env;
    @Inject WSClient ws;

    CompletionStage<Result> badRequestAsync (String mesg) {
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

    File download (int stage) {
        if (stage == 5) {
            String file = config.getConfig("challenge")
                .getConfig("c5").getString("graph-file");
            return env.getFile(file);
        }
        return null;
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

                        String action = request().getQueryString("action");
                        if (action != null
                            && "download".equalsIgnoreCase(action)) {
                            File file = download (stage);
                            if (file != null) {
                                Logger.debug(part.id+": download file "+file);
                                return ok (file, false);
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
        return ok (puzzle.render());
    }

    public Result ttt (String message) {
        return ok (ttt.render(message));
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
        /*
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        */
        
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
        /*
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        */
        
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
        /*
        if (env.isProd())
            return async (redirect
                          (routes.ChallengeController.welcome().url()));
        */
        
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
                return badRequest("This email address doesn't correspond"
                                  +" to a valid participant.\n");
            // exists, have they solved C3 before?
            if (part.stage > 3)
                return ok("Challenge 3 has been solved.\n");
            else {
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
        return repo.fetch(uuid).thenApplyAsync(part -> {
            Http.MultipartFormData<File> body = request().body().asMultipartFormData();
            Http.MultipartFormData.FilePart<File> filePart = body.getFile("c7assoc");
            if (filePart == null) return badRequest("No association file was provided");
            File csvFile = filePart.getFile();
            try {
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

                if (geneSet.size() != 2000)
                    return badRequest("Your calculation failed. Incorrect number of genes.\n");
                if (diseaseSet.size() != 500)
                    return badRequest("Your calculation failed. Incorrect number of diseases.\n");
                // check that we got the maximal probability
                if (Precision.round(probSum,2) != 0.95)
                    return badRequest("Your calculation failed. The sum of knowledge scores is not minimal.\n");

                repo.nextStage(part);
                return redirect(routes.ChallengeController.challenge(id));

            } catch (FileNotFoundException e) {
                return internalServerError("Error opening CSV file.\n" + e.getMessage() + "\n");
            } catch (IOException e) {
                return internalServerError("Error opening CSV file.\n" + e.getMessage() + "\n");
            }


        }, httpExecutionContext.current()).exceptionally(t -> {
            Logger.error("Failed to fetch participant: " + id, t);
            return badRequest("Something bad happened such as a malformed CSV.\n");
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

        return C2ApiTester.main(ws, apiurl[0]);
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

    @BodyParser.Of(value = BodyParser.FormUrlEncoded.class)
    public CompletionStage<Result> submit (final String id,
                                           final Integer stage) {
        try {
            UUID uuid = UUID.fromString(id);
            return repo.fetch(uuid).thenApplyAsync(part -> {
                    if (part != null) {
                        if (!stage.equals(part.stage))
                            return redirect(routes.ChallengeController
                                            .challenge(id));
                        
                        Map<String, String[]> data =
                            request().body().asFormUrlEncoded();

                        // check the answer
                        switch (stage) {
                        case 1:
                            // advance to next stage
                            repo.nextStage(part);
                            break;
                            
                        case 2:
                            ChallengeResponse resp = checkC2 (part, data);
                            Logger.debug(part.id+": "+resp.success+": "+resp.message);
                            if (resp.success > 0) {
                                repo.incrementStage(part); // advance to next stage
                                return ttt(resp.message);
                            }
                            return ok(resp.message);
                            
                        case 4:
                            if (checkC4 (part, data)) {
                                repo.nextStage(part); // advance to next stage
                            }
                            break;
                            
                        case 5:
                            if (checkC5 (part, data)) {
                                repo.nextStage(part); // advance to next stage
                            }
                            break;
                            
                        case 6:
                            if (checkC6 (part, data)){
                                repo.nextStage(part);
                            }
                        case 7:
                            break;
                        }

                        return redirect
                            (routes.ChallengeController.challenge(id));
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
