package repository;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

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

    static public ChallengeResponse main(WSClient ws, String API) {
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
            ObjectNode json = Json.newObject();
            ArrayNode ss = json.putArray("siblingSet");
            ss.add(5);
            System.err.print(ss.toString()+"\n");
            JsonNode node = requestJson(ws, API + tttpath, json);
            if (!node.isArray()) return new ChallengeResponse(0,
                    "Something is amiss here ... I was expecting to get an array of integers back in my response.\n\n"+API+tttpath+"   "+json.toString()+"\n\n"+node.toString());

            if (node.size() == 1 && node.get(0).asInt() == 32)
                return new ChallengeResponse(1, "Checks out!");
            else return new ChallengeResponse(0,
                    "I don't think this is the correct response for that input.\n\n"+node.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new ChallengeResponse(0,
                    "Failed to successfully execute collatzSiblings function. *sigh*\n" + e.getCause().toString());
        }
    }
}
