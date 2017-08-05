package repository;

/**
 * Created by southalln on 8/4/17.
 */
public class ChallengeResponse {
    public int success;
    public String message;

    public ChallengeResponse(int success, String message) {
        this.success = success;
        this.message = message;
    }
}
