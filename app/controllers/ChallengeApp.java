package controllers;

import java.util.*;
import java.net.*;
import java.io.*;

import play.Logger;
import play.Configuration;
import play.Environment;
import play.libs.ws.WSClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.annotation.processing.Completion;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import models.Participant;
import repository.C2ApiTester;
import repository.ChallengeResponse;

@Singleton
public class ChallengeApp {
    final protected int maxStage;
    final protected Environment env;
    final protected Configuration config;
    final protected WSClient ws;
    
    @Inject
    public ChallengeApp (Environment env, Configuration config, WSClient ws) {
        String host = config.getString("play.http.host", "");
        Logger.debug("########### Initializing Challenge App...");
        Logger.debug("Host: "+host);
        // in production you must define http.port
        Logger.debug("Port: "+config.getInt("http.port", 9000));
        Logger.debug("Context: "+config.getString("play.http.context"));
        Logger.debug("Mock mailer: "+config.getBoolean("play.mailer.mock"));
        Logger.debug("DB: "+config.getString("db.default.url"));
        Logger.debug("Secret: "+config.getString("play.http.secret.key"));
        
        if (env.isProd() && host.equals("")) {
            throw new RuntimeException
                ("***** You must define play.http.host in production!!! *****");
        }
        
        maxStage = config.getInt("challenge.max-stage", 8);
        this.env = env;
        this.config = config;
        this.ws = ws;
    }
    
    public int getMaxStage () { return maxStage; }

    public ChallengeResponse checkC5 (Participant part,
                                      Map<String, String[]> data) {
        ChallengeResponse resp = new ChallengeResponse ();
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
                            return resp;
                    }
                    else if (ans != null) {
                        resp.variables.put(me.getKey(), ans.toString());
                    }
                }
                catch (NumberFormatException ex) {
                    Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                    return resp;
                }
            }
        }
        
        Logger.debug(part.id+": passes C5!");
        resp.success = resp.variables.size();
        resp.message = null;
        
        return resp;
    }

    public ChallengeResponse checkC4 (Participant part,
                                      Map<String, String[]> data) {
        ChallengeResponse resp = new ChallengeResponse ();
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
                        return resp;
                    }
                    else if (ans != null)
                        resp.variables.put(me.getKey(), ans.toString());
                }
                catch (NumberFormatException ex) {
                    Logger.warn("Bogus value: "+me.getKey()+"="+val[0]);
                    return resp;
                }
            }
        }

        resp.success = resp.variables.size();
        resp.message = null;
        Logger.debug(part.id+": passes C4!");
        
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
                if (s.equalsIgnoreCase(id) || s.equalsIgnoreCase(topic))
                    return topic;
            }
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
                HashMap<String,String> mTarget = getTopics (target[0]);
                topic = matchTopic ("target",
                                    mTarget.keySet().toArray(new String[0]));
            }

            if (topic != null)
                response.variables.put("c6-target", topic);
        }
        
        if (pathway != null) {
            String topic = matchTopic ("pathway", pathway);
            if (topic == null) {
                HashMap<String, String> mPathway = getTopics(pathway[0]);
                topic = matchTopic ("pathway",
                                    mPathway.keySet().toArray(new String[0]));
            }

            if (topic != null)
                response.variables.put("c6-pathway", topic);
        }
        
        if (cell != null) {
            String topic = matchTopic ("cell", cell);
            if (topic == null) {
                HashMap<String,String> mCell = getTopics(cell[0]);
                topic = matchTopic ("cell",
                                    mCell.keySet().toArray(new String[0]));
            }

            if (topic != null)
                response.variables.put("c6-cell", topic);
        }
        
        if (symptom != null) {
            String topic = matchTopic ("symptom", symptom);
            if (topic == null) {
                HashMap<String,String> mSymptom = getTopics(symptom[0]);
                topic = matchTopic ("cell",
                                    mSymptom.keySet().toArray(new String[0]));
            }

            if (topic != null)
                response.variables.put("c6-symptom", topic);
        }

        response.success = response.variables.size();

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
    
    HashMap<Integer,List<String>> createAnswerMap() {
        HashMap<Integer, List<String>> answerMap = new HashMap();
        answerMap.put(1, Arrays.asList("D000068877"));
        answerMap.put(2,Arrays.asList("D019009"));
        answerMap.put(3,Arrays.asList("D015398","D020935"));
        answerMap.put(4,Arrays.asList("D008407"));
        answerMap.put(5,Arrays.asList("D000402","D012130","D004418","D016535"));
        answerMap.put(6,Arrays.asList("D001249"));

        return answerMap;
    }
    
    HashMap<String,String> getTopics (String pmid){
        HashMap<String,String> topics = new HashMap<String,String>();
        try{
            URL url = new URL
                ("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id="
                 +Long.parseLong(pmid.trim())+
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

    public Configuration config () { return config; }
    public Environment env () { return env; }
}
