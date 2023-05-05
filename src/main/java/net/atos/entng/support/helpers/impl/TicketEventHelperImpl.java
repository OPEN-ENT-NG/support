package net.atos.entng.support.helpers.impl;

import io.vertx.core.json.JsonArray;
import net.atos.entng.support.helpers.TicketEventHelper;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;

import java.util.Map;

public class TicketEventHelperImpl implements TicketEventHelper {
    /**
     *
     * @param user : user
     * @param functions : aim to check if user is an admin
     * @param eventResult : the result of handler passed in listEvents method
     */
    @Override
    public boolean shouldRenderEvent(UserInfos user, Map<String, UserInfos.Function> functions, JsonArray eventResult){
        String owner = eventResult.getJsonObject(0).getString("user_id");
        String schoolId = eventResult.getJsonObject(0).getString("school_id");
        return owner.equals(user.getUserId()) || functions.containsKey(DefaultFunctions.SUPER_ADMIN) || (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) && user.getStructures().contains(schoolId));
    }
}
