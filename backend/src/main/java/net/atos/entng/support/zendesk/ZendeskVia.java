package net.atos.entng.support.zendesk;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;

import org.entcore.common.json.JSONAble;
import org.entcore.common.json.JSONIgnore;
import org.entcore.common.json.JSONRename;
import org.entcore.common.json.JSONDefault;

// cf. https://developer.zendesk.com/documentation/ticketing/reference-guides/via-object-reference/
public class ZendeskVia implements JSONAble
{
    public static final class ViaSource implements JSONAble
    {
        // See https://developer.zendesk.com/documentation/ticketing/reference-guides/via-object-reference/ for JsonObject contents
        JsonObject from;
        JsonObject to;
        String rel;

        public ViaSource(JsonObject o)
        {
            this.from = o.getJsonObject("from");
            this.to = o.getJsonObject("to");
            this.rel = o.getString("rel");
        }
    }

    public String channel;
    public ViaSource source;

    public boolean isFromAPI()
    {
        if("api".equals(this.channel))
            return true;
        else if(this.source != null && this.source.from != null)
        {
            if("follow_up".equals(this.source.rel) && "api".equals(this.source.from.getString("channel")))
                return true;
        }

        return false;
    }
}