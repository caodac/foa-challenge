package repository;


import com.fasterxml.jackson.databind.JsonNode;
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

    static JsonNode requestJson(WSClient ws, String uri, JsonNode json) {
        WSResponse resp = null;
        try {
            resp = request(ws, uri, json);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
        return resp.asJson();
    }

    static public String main(WSClient ws, String API) {
        if (!API.startsWith("http"))
            return ("You can not be serious! Hypertext Transfer Protocol was not on the line! "+API+"\n");

        // Test whether GET works on API
        String tttpath = "";
        try {
            JsonNode node = requestJson(ws, API + "/games", null);
            if (!node.isArray()) return "API response did not contain an ARRAY of games to play.";
            for (final JsonNode entry : node) {
                if (entry.has("id") && entry.has("idmap")) {
                    String game = entry.get("id").textValue().toUpperCase();
                    game = game.replace("-", "");
                    if (game.startsWith("TICTACTO")) {
                        tttpath = entry.get("idmap").textValue();
                        break;
                    }
                }
            }
            if (tttpath.length() == 0)
                return "Failed to find my game in API response.\n"+
                        "Different games should be named under an 'id' tag, as in the example yaml file.\n\n\n\n"+
                        "It's not on the list! It's got to be somewhere.\n\n"+API+"/games\n\n"+
                        node.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to successfully query "+API+"/games for a list of games to play. *sigh*";
        }

        // Test whether PUT works on API
        try {
            JsonNode json = Json.newObject()
                    .put("board", "OXXXOOX--")
                    .put("player", "O");
            JsonNode node = requestJson(ws, API + tttpath, json);
            if (node.has("makeMarkAt")) {
                return "A STRANGE GAME. THE ONLY WINNING MOVE IS NOT TO PLAY.\n";
            } else return "Something is amiss here ... Please specify where to move next with the tag 'makeMarkAt'\n\n"+node.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to successfully retrieve next move in TIC-TAC-TOE. *sigh*";
        }

        //StringBuffer sb = new StringBuffer();
        //sb.append("Looks like it worked.");

        //return sb.toString();
    }
}
