package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;
import org.entcore.common.user.UserInfos;

import java.util.Map;

public interface TicketEventHelper {
    public boolean shouldRenderEvent(UserInfos user, Map<String, UserInfos.Function> functions, JsonArray eventResult);
}
