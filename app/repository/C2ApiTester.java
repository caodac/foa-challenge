package repository;


import com.fasterxml.jackson.databind.JsonNode;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import java.util.ArrayList;
import java.util.Random;
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

    static char[] getBoardObj(String board) {
        return board.toCharArray();
    }

    static boolean isWinner(String board, char le) {
        return isWinner(getBoardObj(board), le);
    }

    static boolean isWinner(char[] bo, char le) {
        // Given a board and a player's letter, this function returns true if that player has won.
        return ((bo[6] == le && bo[7] == le && bo[8] == le) || //across the top
                (bo[3] == le && bo[4] == le && bo[5] == le) || //across the middle
                (bo[0] == le && bo[1] == le && bo[2] == le) || //across the bottom
                (bo[6] == le && bo[3] == le && bo[0] == le) || //down the left side
                (bo[7] == le && bo[4] == le && bo[1] == le) || //down the middle
                (bo[8] == le && bo[5] == le && bo[2] == le) || //down the right side
                (bo[6] == le && bo[4] == le && bo[2] == le) || //diagonal
                (bo[8] == le && bo[4] == le && bo[0] == le)); //diagonal
    }

    static boolean isBoardFull (String board) {
        // Return true if every space on the board has been taken, otherwise return false.
        if (board.indexOf('-') > -1) return false;
        return true;
    }

    static int makeMark(String board, char player) {
        // Here is our algorithm for our Tic Tac Toe AI:
        // First, check if we can win in the next move
        for (int i=0; i<9; i++) {
            char[] copy = getBoardObj(board);
            if (copy[i] == '-') {
                copy[i] = player;
                if (isWinner(copy, player)) {
                    // choose winning move, i
                    return i;
                }
            }
        }

        // Check if the player could win on his next move, and block them.
        char notPlayer = 'O';
        if (player == notPlayer) notPlayer = 'X';
        for (int i=0; i<9; i++) {
            char[] copy = getBoardObj(board);
            if (copy[i] == '-') {
                copy[i] = notPlayer;
                if (isWinner(copy, notPlayer)) {
                    // protect from winning move, i
                    return i;
                }
            }
        }

        // Protect from double jeopardy
        char[] copy = getBoardObj(board);
        int[] dj = {0,1,3,2,1,5,6,3,7,8,5,7};
        for (int i=0; i<3; i++) {
            if (copy[dj[i * 3]] == '-' && copy[dj[i * 3 + 1]] == copy[dj[i * 3 + 2]] && copy[dj[i * 3 + 1]] != '-') {
                // double jeopardy!", dj[i*3]
                return dj[i * 3];
            }
        }

        // Try to take the center, if it is free.
        if (copy[4]=='-')
            return 4;

        Random randomizer = new Random();
        // Move on one of the sides.
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1); list.add(3); list.add(5); list.add(7);
        while (list.size() > 0) {
            Integer random = list.get(randomizer.nextInt(list.size()));
            if (copy[random] == '-')
                return random;
            list.remove(random);
        }

        // Try to take one of the corners, if they are free.
        list = new ArrayList<>();
        list.add(0); list.add(2); list.add(6); list.add(8);
        while (list.size() > 0) {
            Integer random = list.get(randomizer.nextInt(list.size()));
            if (copy[random] == '-')
                return random;
            list.remove(random);
        }

        return 0;
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
            if (!node.has("makeMarkAt")) return "Something is amiss here ... Please specify where to move next with the tag 'makeMarkAt'\n\n"+node.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to successfully retrieve next move in TIC-TAC-TOE. *sigh*";
        }

        // Fake functioning tictactoe gameplay
        int i = 0;
        StringBuffer sb = new StringBuffer();
        char player = 'X';
        String board = "---------";
        while (!(isWinner(board, 'X') || isWinner(board, 'O')) && i<144) {
            if (isBoardFull(board))
                board = "---------";
            JsonNode gamePlay = Json.newObject()
                    .put("board", board)
                    .put("player", Character.toString(player));
            int mark = makeMark(board, player)+1;
            //It would be great! to be able to do this!
            //JsonNode node = requestJson(ws, API + tttpath, gamePlay);
            //if (node.has("makeMarkAt")) mark = node.get("makeMarkAt").intValue();
            board = board.substring(0, mark-1) + player + board.substring(mark);
            i = i + 1;
            sb.append(board); sb.append('\n');
            if (player == 'X')
                player = 'O';
            else
                player = 'X';

            if (isWinner(board, 'X'))
                return (sb.toString()+"\nX wins!!!");
            else if (isWinner(board, 'O'))
                return (sb.toString()+"\nO is the Winner!");
        }
        if (!isBoardFull(board))
            return (sb.toString()+"\nLet's try again.");

        return sb.toString()+"\nA STRANGE GAME. THE ONLY WINNING MOVE IS NOT TO PLAY.\n";
    }
}
