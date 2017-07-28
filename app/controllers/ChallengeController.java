package controllers;

import javax.inject.Inject;
import play.mvc.*;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class ChallengeController extends Controller {

    @Inject views.html.Challenge challenge;
    @Inject views.html.Welcome welcome;

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        //return ok(views.txt.nothing.render());
        return ok (welcome.render());
    }

    public Result challenge (String id, Integer stage) {
        if (!"foobar".equals(id))
            return ok (welcome.render());
        
        return ok (challenge.render(id, stage));
    }

    public Result welcome () {
        return ok (welcome.render());
    }
}
