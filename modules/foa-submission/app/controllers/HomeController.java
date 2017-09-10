package controllers;

import java.util.*;
import play.mvc.*;
import play.libs.Json;

import play.Logger;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.Configuration;
import javax.inject.Inject;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {
    @Inject protected WSClient ws;
    @Inject protected Configuration config;

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
    public Result submit () {
        Map<String, String[]> data =
            request().body().asFormUrlEncoded();

        String url = config.getString
            ("challenge.url", "https://ncats.io/challenge");
        Logger.debug("challenge url: "+url);
        Logger.debug("Input form:");
        for (Map.Entry<String, String[]> me : data.entrySet()) {
            Logger.debug(me.getKey()+": "+me.getValue()[0]);
        }

        try {
            WSRequest req = ws.url(url);
            Map<String, String> json = new TreeMap<>();
            json.put("name", data.get("name")[0]);
            json.put("email", data.get("email")[0]);
            json.put("answer", data.get("answer")[0]);

            WSResponse res = req.post(Json.toJson(json))
                .toCompletableFuture().get();
            flash ("message", res.getBody());
            return redirect (routes.HomeController.index());
        }
        catch (Exception ex) {
            Logger.error("Can't post to "+url, ex);
            return internalServerError (ex.getMessage());
        }
    }
}
