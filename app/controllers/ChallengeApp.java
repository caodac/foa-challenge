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

    public boolean checkC5 (Participant part, Map<String, String[]> data) {
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

    public boolean checkC4 (Participant part, Map<String, String[]> data) {
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
    
    public Map<String, String> checkC6 (Participant part,
                                        Map<String, String[]> data) {
        Map results = new HashMap<String,String>();

        String[] target = data.get("c6-target");
        String[] pathway = data.get("c6-pathway");
        String[] symptom = data.get("c6-symptom");
        String[] cell = data.get("c6-cell");
        Logger.info("Checking c6");
        HashMap<Integer,List<String>> answerMap = createAnswerMap();
        boolean bTarget = false;
        boolean bPathway = false;
        boolean bSymptom = false;
        boolean bCell = false;

        if (target != null) {
            HashMap<String,String> mTarget = getTopics (target[0]);
            bTarget=!Collections.disjoint(mTarget.keySet(),answerMap.get(2));
            if(bTarget) {
                mTarget.keySet().retainAll(answerMap.get(2));
                results.put("c6-target",
                            mTarget.get(mTarget.keySet().iterator().next()));
                Logger.info("target");
            }
        }
        
        if (pathway != null) {
            HashMap<String,String> mPathway = getTopics(pathway[0]);
            bPathway = (!Collections.disjoint(mPathway.keySet(),answerMap.get(3)));
            if(bPathway) {
                mPathway.keySet().retainAll(answerMap.get(3));
                results.put("c6-pathway",
                            mPathway.get(mPathway.keySet().iterator().next()));
            }
        }
        
        if (cell != null) {
            HashMap<String,String> mCell = getTopics(cell[0]);
            bCell = (!Collections.disjoint(mCell.keySet(),answerMap.get(4)));
            if(bCell) {
                mCell.keySet().retainAll(answerMap.get(4));
                results.put("c6-cell",
                            mCell.get(mCell.keySet().iterator().next()));
            }
        }
        
        if (symptom != null) {
            HashMap<String,String> mSymptom = getTopics(symptom[0]);
            bSymptom=(!Collections.disjoint(mSymptom.keySet(),answerMap.get(5)));
            if (bSymptom) {
                mSymptom.keySet().retainAll(answerMap.get(5));
                results.put("c6-symptom",
                            mSymptom.get(mSymptom.keySet().iterator().next()));
            }
        }

        return results;
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
