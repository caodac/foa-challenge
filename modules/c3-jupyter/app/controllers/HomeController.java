package controllers;

import java.util.*;
import play.mvc.*;
import play.libs.Json;

import play.Logger;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

import javax.inject.Inject;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    @Inject protected WSClient ws;
    
    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(views.html.index.render());
    }

    @BodyParser.Of(value = BodyParser.FormUrlEncoded.class)    
    public Result solve () {
        Map<String, String[]> data =
            request().body().asFormUrlEncoded();
        String url = data.get("url")[0] + "/c3/handler";
        String email = data.get("email")[0];

        try {
            WSRequest req = ws.url(url);
            Map<String, String> json = new TreeMap<>();
            json.put("email", email);
            json.put("magic", Base64.getEncoder()
                     .encodeToString(email.getBytes("utf8")));
            WSResponse res = req.post(Json.toJson(json))
                .toCompletableFuture().get();
        
            return ok (res.getBody());
        }
        catch (Exception ex) {
            Logger.error("Can't post to "+url, ex);
            return internalServerError (ex.getMessage());
        }
    }
}
