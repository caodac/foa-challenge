package controllers;

import java.util.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.*;
import java.security.MessageDigest;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import play.Logger;
import play.Configuration;
import play.Environment;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.cache.*;
import play.libs.Json;
import play.libs.mailer.Email;
import play.libs.mailer.MailerClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.annotation.processing.Completion;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.math3.util.Precision;

import models.Participant;
import repository.C2ApiTester;
import repository.ChallengeResponse;

@Singleton
public class ChallengeApp {
    public enum PuzzleResult {
        Correct,
        NotOrder,
        Partial,
        Incorrect
    }
    
    final public int maxStage;
    final public String email;
    final public LocalDateTime enddate;
    final public LocalDateTime duedate;
    final public String puzzleKey;
    final public String faq;
    final public List<String> quotes;
    final byte[] mailersecret;
    final boolean mailforward;
    final String mailserver; 

    final protected Environment env;
    final protected Configuration config;
    final protected WSClient ws;
    final protected AsyncCacheApi cache;
    final protected MailerClient mailer;
    
    final Random rand = new Random ();
    final char[] SEED = {'a','b','c','d','e','f','1','2','3','4','5','6',
                         '7','8','9'};
    
    static LocalDateTime getDate (Configuration config, String key) {
        String date = config.getString(key, null);
        LocalDateTime d = null; 
        if (date != null) {
            try {
                d = LocalDateTime.parse(date);
            }
            catch (Exception ex) {
                Logger.error("Bad date format: "+date, ex);
                d = LocalDateTime.now().plusDays(7);
            }
        }
        return d;
    }
    
    @Inject
    public ChallengeApp (Environment env, Configuration config,
                         WSClient ws, AsyncCacheApi cache,
                         MailerClient mailer) {
        String host = config.getString("play.http.host", "");
        Logger.debug("########### Initializing Challenge App...");
        Logger.debug("Host: "+host);
        // in production you must define http.port
        Logger.debug("Port: "+config.getInt("http.port", 9000));
        Logger.debug("Context: "+config.getString("play.http.context"));
        Logger.debug("Mock mailer: "+config.getBoolean("play.mailer.mock"));
        Logger.debug("DB: "+config.getString("db.default.url"));
        Logger.debug("Secret: "+config.getString("play.http.secret.key"));
        Logger.debug("Root Path: "+env.rootPath());
        
        if (env.isProd() && host.equals("")) {
            throw new RuntimeException
                ("***** You must define play.http.host in production!!! *****");
        }

        maxStage = config.getInt("challenge.max-stage", 8);
        email = config.getString("challenge.support-email", "");
        enddate = getDate (config, "challenge.end-date");
        duedate = getDate (config, "challenge.due-date");
        faq = config.getString("challenge.faq", null);
        quotes = config.getStringList("challenge.quotes", new ArrayList<>());
        String secret = config.getString("challenge.mailer.secret", null);
        mailersecret = secret != null ? secret.getBytes() : null;
        mailforward = config.getBoolean("challenge.mailer.forward", false);
        mailserver = config.getString("challenge.mailer.server", null);
        puzzleKey = config.getString("challenge.puzzle.key", null);
        if (puzzleKey == null)
            throw new RuntimeException ("***** puzzle key is null! ******");
        
        Logger.debug("########### Challenge Parameters...");
        Logger.debug("Email: "+email);
        Logger.debug("End date: " + enddate);
        Logger.debug("Due date: " + duedate);
        Logger.debug("FAQ: "+faq);
        Logger.debug("Quotes: "+quotes.size());
        Logger.debug("Mail forward: "+mailforward);
        if (mailforward)
            Logger.debug("Mail server: "+mailserver);
        if (mailforward && mailserver == null)
            throw new RuntimeException
                ("Mail server can't be null when mailforward is true!");
        
        this.env = env;
        this.config = config;
        this.ws = ws;
        this.cache = cache;
        this.mailer = mailer;
    }

    static Map<Character, Integer> charmap (String s) {
        Map<Character, Integer> map = new TreeMap<>();
        for (int i = 0; i < s.length(); ++i) {
            char ch = s.charAt(i);
            Integer c = map.get(ch);
            map.put(ch, c==null ? 1 : c+1);
        }
        return map;
    }

    public String getRandomQuote () {
        return quotes.get(rand.nextInt(quotes.size()));
    }

    public boolean validateMailer (String seed, String hash) {
        if (mailersecret == null || hash == null || hash.length() < 10)
            return false;

        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(mailersecret);
            md.update(seed.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder ();
            for (int i = 0; i < digest.length; ++i)
                sb.append(String.format("%1$02x", digest[i] & 0xff));
            
            return sb.toString().startsWith(hash.toLowerCase());
        }
        catch (Exception ex) {
            Logger.error("Can't validate mailer!", ex);
        }
        return false;
    }

    void addSeed (Map<String, String> mesg) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(mailersecret);
            StringBuilder seed = new StringBuilder ();
            for (int i = 0; i < 10; ++i)
                seed.append(SEED[rand.nextInt(SEED.length)]);
            mesg.put("seed", seed.toString());
            md.update(seed.toString().getBytes());
            
            byte[] digest = md.digest();
            StringBuilder hash = new StringBuilder ();
            for (int i = 0; i < digest.length; ++i)
                hash.append(String.format("%1$02x", digest[i] & 0xff));
            mesg.put("hash", hash.toString());
        }
        catch (Exception ex) {
            Logger.error("Can't generate seed", ex);
        }
    }

    public String sendmail (Map<String, String> mesg) {
        if (mailforward) {
            try {
                addSeed (mesg);
                WSRequest req = ws.url(mailserver);
                WSResponse res = req.post(Json.toJson(mesg))
                    .toCompletableFuture().get();
                return res.getBody();
            }
            catch (Exception ex) {
                Logger.error("Can't forward message: "+mesg, ex);
                return null;
            }
        }
        else {
            addSeed (mesg);
            Logger.debug(">>>>\n"+mesg);
            
            Email email = new Email ()
                .setSubject(mesg.get("subject"))
                .setFrom(mesg.get("from"))
                .addTo(mesg.get("to"))
                .setBodyText(mesg.get("body"));
            return mailer.send(email);
        }
    }
    
    public PuzzleResult checkPuzzle (String answer) {
        String ans = answer.toUpperCase();
        if (puzzleKey.equals(ans))
            return PuzzleResult.Correct;

        // ok now see how is the answer wrong
        if (ans.length() == puzzleKey.length()) {
            Map<Character, Integer> m1 = charmap (puzzleKey);
            Map<Character, Integer> m2 = charmap (ans);
            if (m1.keySet().containsAll(m2.keySet()))
                return PuzzleResult.NotOrder;
        }

        int m = ans.length()+1, n = puzzleKey.length()+1;
        int[][] C = new int[m][n];
        // find length of longest common substring..
        for (int i = 1; i < m; ++i)
            for (int j = 1; j < n; ++j) {
                if (ans.charAt(i-1) == puzzleKey.charAt(j-1))
                    C[i][j] = C[i-1][j-1] + 1;
                else
                    C[i][j] = Math.max(C[i][j-1], C[i-1][j]);
            }
        
        if (C[m-1][n-1] > puzzleKey.length()/2)
            return PuzzleResult.Partial;

        return PuzzleResult.Incorrect;
    }
    
    public ChallengeResponse checkC5 (Participant part,
                                      Map<String, String[]> data) {
        ChallengeResponse resp = new ChallengeResponse ();
        Configuration conf = config.getConfig("challenge").getConfig("c5");
        Configuration optional = conf.getConfig("optional");

        int incorrect = 0;
        for (Map.Entry<String, String[]> me : data.entrySet()) {
            String[] val = me.getValue();
            if (val.length > 0 && !val[0].equals("")) {
                Logger.info(me.getKey()+": "+val[0]);
                
                if ("maxclique-size".equals(me.getKey())) {
                    try {
                        int iv = Integer.parseInt(val[0]);
                        Integer ans = optional.getInt("maxclique-size", null);
                        if (ans != null && ans.equals(iv)) {
                            resp.variables.put(me.getKey(), ans.toString());
                        }
                        else
                            resp.variables.remove(me.getKey());
                    }
                    catch (NumberFormatException ex) {
                        Logger.error("Bogus value specified for "
                                     +me.getKey(), ex);
                    }
                }
                else if ("maxclique-span".equals(me.getKey())) {
                    try {
                        String[] toks = val[0].split("[\\s,;]+");
                        List<String> span =
                            optional.getStringList(me.getKey(), null);
                        if (span != null) {
                            if (toks.length == span.size()) {
                                for (String t : toks)
                                    span.remove(t.toUpperCase());
                                
                                if (span.isEmpty()) {
                                    resp.variables.put(me.getKey(), val[0]);
                                }
                                else
                                    resp.variables.remove(me.getKey());
                            }
                            else
                                resp.variables.remove(me.getKey());
                        }
                        else {
                            resp.variables.remove(me.getKey());
                            Logger.error("Key "+me.getKey()+" is not "
                                         +"defined in configuration!");
                        }
                    }
                    catch (Exception ex) {
                        Logger.error("Can't validate solution for "
                                     +me.getKey(), ex);
                    }
                }
                else {
                    try {
                        int iv = Integer.parseInt(val[0]);
                        Integer ans = conf.getInt(me.getKey());
                        if (ans != null) {
                            if (!ans.equals(iv)) {
                                Logger.debug
                                    (part.id+": incorrect "+me.getKey()+"="+iv);
                                ++incorrect;
                                resp.variables.remove(me.getKey());
                            }
                            else {
                                resp.variables.put(me.getKey(), ans.toString());
                            }
                        }
                        else {
                            resp.variables.remove(me.getKey());
                            ++incorrect;
                        }
                    }
                    catch (NumberFormatException ex) {
                        Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                        resp.variables.remove(me.getKey());
                        ++incorrect;
                    }
                }
            }
        }

        if (incorrect == 0) {
            Logger.debug(part.id+": passes C5!");
            resp.success = resp.variables.size();
            resp.message = null;
        }
        
        return resp;
    }

    public ChallengeResponse checkC7 (Participant part, File csvFile)
        throws Exception {
        ChallengeResponse resp = new ChallengeResponse (0, null);
        if (true) {
            resp.success = 1;
            return resp;
        }
        
        List<String> genes = new ArrayList<String>();
        List<String> diseases = new ArrayList<String>();
        double probSum = 0.0;

        CSVReader csvReader = new CSVReader
            (new FileReader(csvFile), ',', '"', 1); // skip header line
        String[] toks;
        while ((toks = csvReader.readNext()) != null) {
            if (toks.length != 3) {
                resp.message = "CSV file was not formatted properly.";
                return resp;
            }
            genes.add(toks[0]);
            diseases.add(toks[1]);
            probSum += Double.parseDouble(toks[2]);
        }
        
        // check genes and diseases are unique and equal the proper number
        Set<String> geneSet = new HashSet<String>(genes);
        Set<String> diseaseSet = new HashSet<String>(diseases);
        
        if (geneSet.size() != 2000) {
            resp.message = "Your calculation failed. "
                +"Incorrect number of genes.";
            return resp;
        }
        
        if (diseaseSet.size() != 500) {
            resp.message = "Your calculation failed. Incorrect "
                +"number of diseases.";
            return resp;
        }

        // check that we got the maximal probability
        if (Precision.round(probSum, 2) > 0.95) {
            resp.message = "Your calculation failed. The "
                +"sum of knowledge scores is not minimal.";
        }
        else {
            resp.success = 1;
        }

        return resp;
    }
    
    public ChallengeResponse checkC4 (Participant part,
                                      Map<String, String[]> data) {
        ChallengeResponse resp = new ChallengeResponse ();
        Configuration c4 = config.getConfig("challenge").getConfig("c4");

        int incorrect = 0;
        for (Map.Entry<String, String[]> me : data.entrySet()) {
            String[] val = me.getValue();
            if (val.length > 0 && !val[0].equals("")) {
                Logger.info(me.getKey()+": "+val[0]);
                try {
                    int iv = Integer.parseInt(val[0]);
                    Integer ans = c4.getInt(me.getKey());
                    if (ans != null) {
                        if (!ans.equals(iv)) {
                            Logger.debug
                                (part.id+": incorrect "+me.getKey()+"="+iv);
                            ++incorrect;
                            
                        }
                        else {
                            resp.variables.put(me.getKey(), ans.toString());
                        }
                    }
                    else {
                        ++incorrect;
                    }
                }
                catch (NumberFormatException ex) {
                    Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                    ++incorrect;
                }
            }
        }

        if (incorrect == 0) {
            resp.success = resp.variables.size();
            resp.message = null;
            Logger.debug(part.id+": passes C4!");
        }
        
        return resp;
    }

    String matchTopic (String category, String... meshIds) {
        List<Configuration> c6 = config.getConfigList
            ("challenge.c6."+category, null);
        if (c6 == null)
            throw new IllegalArgumentException
                ("Unrecognized category: \""+category+"\"");
        
        for (Configuration conf : c6) {
            String id = conf.getString("id");
            String topic = conf.getString("topic");
            for (String s : meshIds) {
                if (s.equalsIgnoreCase(id) || s.equalsIgnoreCase(topic)) {
                    Logger.debug(category+": "+s +" => "+topic);
                    return topic;
                }
            }
        }
        
        return null;
    }

    Map<String, String> getCachedTopics (String pmid) {
        try {
            CompletionStage<Map<String, String>> topics = cache.getOrElseUpdate
                (pmid, new Callable<CompletionStage<Map<String, String>>> () {
                        public CompletionStage<Map<String, String>> call ()
                            throws Exception {
                            try {
                                return supplyAsync
                                (() -> getTopics (Long.parseLong(pmid.trim())));
                            }
                            catch (Exception ex) {
                                Logger.error("Can't getTopics", ex);
                            }
                            return null;
                        }
                    });
            return topics.toCompletableFuture().get();
        }
        catch (Exception ex) {
            Logger.error("Future fails", ex);
        }
        return null;
    }
    
    public ChallengeResponse checkC6 (Participant part,
                                      Map<String, String[]> data) {
        ChallengeResponse response = new ChallengeResponse ();

        String[] target = data.get("c6-target");
        String[] pathway = data.get("c6-pathway");
        String[] symptom = data.get("c6-symptom");
        String[] cell = data.get("c6-cell");
        Logger.info("Checking c6");

        if (target != null) {
            String topic = matchTopic ("target", target);
            if (topic == null) {
                Map<String, String> mTarget = getCachedTopics (target[0]);
                if (mTarget != null)
                    topic = matchTopic
                        ("target", mTarget.keySet().toArray(new String[0]));
            }

            response.variables.put("c6-target", topic);
        }
        
        if (pathway != null) {
            String topic = matchTopic ("pathway", pathway);
            if (topic == null) {
                Map<String, String> mPathway = getCachedTopics (pathway[0]);
                if (mPathway != null)
                    topic = matchTopic
                    ("pathway", mPathway.keySet().toArray(new String[0]));
            }

            response.variables.put("c6-pathway", topic);
        }
        
        if (cell != null) {
            String topic = matchTopic ("cell", cell);
            if (topic == null) {
                Map<String,String> mCell = getCachedTopics(cell[0]);
                if (mCell != null)
                    topic = matchTopic
                        ("cell", mCell.keySet().toArray(new String[0]));
            }

            response.variables.put("c6-cell", topic);
        }
        
        if (symptom != null) {
            String topic = matchTopic ("symptom", symptom);
            if (topic == null) {
                Map<String,String> mSymptom = getCachedTopics(symptom[0]);
                if (mSymptom != null)
                    topic = matchTopic
                        ("symptom", mSymptom.keySet().toArray(new String[0]));
            }

            response.variables.put("c6-symptom", topic);
        }

        response.success = 0;
        for (Map.Entry<String, String> me : response.variables.entrySet()) {
            if (me.getValue() != null) {
                Logger.debug("c6: "+me.getKey()+" <=> "+me.getValue());
                ++response.success;
            }
        }
        if (response.success != 4)
            response.success = 0;
        
        Logger.debug("C6: "+response.variables);

        return response;
    }
    
    public ChallengeResponse checkC2 (Participant part,
                                      Map<String, String[]> data) {
        String[] apiurl = data.get("API-URI");
        if (apiurl == null || apiurl.length == 0 || apiurl[0].equals("")) {
            Logger.warn(part.id+": no API-URI parameter specified!");
            return new ChallengeResponse(0, "No API-URI parameter specified!");
        }

        return C2ApiTester.main(ws, part.id, apiurl[0]);
    }
    
    Map<String,String> getTopics (long pmid) {
        HashMap<String,String> topics = new HashMap<String,String>();
        try{
            URL url = new URL
                ("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id="
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

        Logger.debug("TOPICS: "+pmid+" => "+topics);

        return topics;
    }

    public File download (int stage) {
        switch (stage) {
        case 5: return download (stage, "graph-file");
        case 3: return download (stage, "notebook-file");
        }
        return null;
    }

    public File download (int stage, String name) {
        String file = config.getConfig("challenge.c"+stage).getString(name);
        if (file != null)
            return env.getFile(file);

        return null;
    }

    public long endDateDuration () {
        return LocalDateTime.now().until(enddate, ChronoUnit.SECONDS);
    }
    
    public long dueDateDuration () {
        return LocalDateTime.now().until(duedate, ChronoUnit.SECONDS);
    }
    
    public Configuration config () { return config; }
    public Environment env () { return env; }
}
