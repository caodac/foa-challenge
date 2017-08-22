package repository;

import java.util.Map;
import java.util.TreeMap;

/**
 * Created by southalln on 8/4/17.
 */
public class ChallengeResponse {
    public int success;
    public String message;
    public Map<String, String> variables = new TreeMap<>();

    public ChallengeResponse () {
        this (0, "Sorry, you have one or more incorrect answers. "
              +"Please try again!");
    }
    
    public ChallengeResponse(int success, String message) {
        this.success = success;
        this.message = message;
    }
}
