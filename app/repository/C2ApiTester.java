package repository;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Created by southalln on 8/2/17.
 *
 * Some notes ... right now all that is required to pass these tests is to Generate Server using the provided yaml
 * file and (in my case of using Python Flask) edit the last line of two files:
 * swagger_server/controllers/games_controller.py: return [InlineResponse200('TICTACTOE', '/games/tictactoe')]
 * swagger_server/controllers/tictactoe_controller.py: return InlineResponse2001(gameState._player, 9)
 *
 * one also needs to figure out how to make their application server publically accessible
 */
public class C2ApiTester {
    static WSResponse request(WSClient ws, String uri, JsonNode json) throws ExecutionException, InterruptedException {
        WSRequest req = ws.url(uri);
        req.addHeader("Accept","application/json");
        if (json == null)
            return req.get().toCompletableFuture().get();
        return req.post(json).toCompletableFuture().get();
    }

    static JsonNode requestJson(WSClient ws, String uri, JsonNode json) throws Exception {
        WSResponse resp = null;
        resp = request(ws, uri, json);
        return resp.asJson();
    }

    static int getCollatzPathLength(int n) {
        int i = 1;
        while (n > 1) {
            if (n % 2 == 0)
                n = n / 2;
            else
                n = 3 * n + 1;
            i = i + 1;
        }
        return i;
    }

    static ArrayList<Integer> getCollatzSiblings(int pLen) {
        pLen = pLen - 1;
        ArrayList<Integer> sibs = new ArrayList<>();
        sibs.add(1);
        while (pLen > 0) {
            pLen = pLen - 1;
            ArrayList<Integer> nSibs = new ArrayList<>();
            for (int n : sibs) {
                nSibs.add(n * 2);
                if (n % 2 == 0 && (n - 1) % 3 == 0 && (n - 1) / 3 > 1)
                    nSibs.add((n - 1) / 3);
            }
            sibs = nSibs;
            //System.out.println(sibs.toString());
        }
        return sibs;
    }

    static public ChallengeResponse main(WSClient ws, UUID id, String API) {
        if (!API.startsWith("http"))
            return new ChallengeResponse(0,
                    "You can not be serious! Hypertext Transfer Protocol was not on the line! "+API+"\n");

        // Test whether GET works on API
        String tttpath = "";
        try {
            JsonNode node = requestJson(ws, API + "/types", null);
            if (!node.isArray()) return new ChallengeResponse(0,
                    "API response did not contain an ARRAY of math functions.\n"+node.toString());
            for (final JsonNode entry : node) {
                if (entry.has("id") && entry.has("idmap")) {
                    String func = entry.get("id").textValue().toUpperCase();
                    func = func.replace("-", "");
                    func = func.replace("_", "");
                    if (func.startsWith("COLLATZSIBLINGS")) {
                        tttpath = entry.get("idmap").textValue();
                        break;
                    }
                }
            }
            if (tttpath.length() == 0)
                return new ChallengeResponse(0,
                        "Failed to find function collatzSiblings in API response.\n"+
                        "Different functions should be named under an 'id' tag, as in the example yaml file.\n\n\n\n"+
                        API+"\n\n"+
                        node.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new ChallengeResponse(0,
                    "Failed to successfully query " + API + "/types for a list of functions to use. *sigh*\n" + e.getCause().toString());
        }

        // Test whether POST works on API
        try {
            int[][] testList = {{144, 148, 149}, {112, 116, 117}, {49, 50, 51}, {28, 29, 30}};
            int testIndex = (int)(id.getLeastSignificantBits() & 0xFF)%4;
            int[] test = testList[testIndex];
            //System.out.println("random test: "+testIndex+":test:"+test);
            ObjectNode json = Json.newObject();
            ArrayNode ss = json.putArray("siblingSet");
            for (int n: test)
                ss.add(n);
            int pLen = getCollatzPathLength(test[0]);
            ArrayList<Integer> answer = getCollatzSiblings(pLen);
            for (int i=0; i<ss.size(); i++) {
                answer.remove(new Integer(ss.get(i).intValue()));
            }

            //System.err.print(ss.toString()+"\n"+answer.toString()+"\n");
            JsonNode node = requestJson(ws, API + tttpath, json);
            if (!node.isArray()) return new ChallengeResponse(0,
                    "Something is amiss here ... I was expecting to get an array of integers back in my response.\n\n"+API+tttpath+"   "+json.toString()+"\n\n"+node.toString());

            if (node.size() == answer.size() && answer.contains(node.get(0).asInt()) ) {
                ChallengeResponse resp = new ChallengeResponse(1, "Checks out!");
                resp.variables.put("API-URI", API);
                return resp;
            }
            else {
                StringBuilder sb = new StringBuilder
                    ("I don't think this is the correct response for that input:\n[");
                int size = Math.min(10,node.size());
                for (int i = 0; i < size; ++i) {
                    sb.append(node.get(i).asInt());
                    if (i+1 < size) sb.append(",");
                }
                
                if (size < node.size())
                    sb.append("...("+(node.size()-size)+" more)");
                sb.append("]");
                
                return new ChallengeResponse(0, sb.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ChallengeResponse(0,
                    "Failed to successfully execute collatzSiblings function. *sigh*\n" + e.getCause().toString());
        }
    }
}
