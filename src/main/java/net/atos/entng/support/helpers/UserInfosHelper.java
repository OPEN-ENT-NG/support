package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.stream.Collectors;

public class UserInfosHelper {

    private UserInfosHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static JsonObject toJSON(UserInfos userInfos) {
        return new JsonObject()
                .put(Ticket.HASAPP, userInfos.getHasApp())
                .put(Ticket.USERID, userInfos.getUserId())
                .put(Ticket.EXTERNALID, userInfos.getExternalId())
                .put(Ticket.FIRSTNAME, userInfos.getFirstName())
                .put(Ticket.LASTNAME, userInfos.getLastName())
                .put(Ticket.USERNAME, userInfos.getUsername())
                .put(Ticket.BIRTHDAY, userInfos.getBirthDate())
                .put(Ticket.CLASSNAMES, userInfos.getClasses())
                .put(Ticket.REALCLASSNAMES, userInfos.getRealClassNames())
                .put(Ticket.STRUCTURENAMES, userInfos.getStructureNames())
                .put(Ticket.UAI, userInfos.getUai())
                .put(Ticket.CHILDRENIDS, userInfos.getChildrenIds())
                .put(Ticket.LEVEL, userInfos.getLevel())
                .put(Ticket.TYPE, userInfos.getType())
                .put(Ticket.LOGIN, userInfos.getLogin())
                .put(Ticket.AUTHORIZEDACTIONS, getUserActionJSONArray(userInfos.getAuthorizedActions()))
                .put(Ticket.GROUPSIDS, userInfos.getGroupsIds())
                .put(Ticket.CLASSES, userInfos.getClasses())
                .put(Ticket.STRUCTURES, userInfos.getStructures());
    }

    @SuppressWarnings("unchecked")
    public static UserInfos getUserInfosFromJSON(JsonObject infos) {
        UserInfos user = new UserInfos();
        user.setHasApp(infos.getBoolean(Ticket.HASAPP));
        user.setUserId(infos.getString(Ticket.USERID));
        user.setExternalId(infos.getString(Ticket.EXTERNALID));
        user.setFirstName(infos.getString(Ticket.FIRSTNAME));
        user.setLastName(infos.getString(Ticket.LASTNAME));
        user.setUsername(infos.getString(Ticket.USERNAME));
        user.setBirthDate(infos.getString(Ticket.BIRTHDAY));
        if (infos.getJsonArray(Ticket.REALCLASSNAMES) != null) {
            user.setRealClassNames(infos.getJsonArray(Ticket.REALCLASSNAMES, new JsonArray()).getList());
        }
        user.setStructureNames(infos.getJsonArray(Ticket.STRUCTURENAMES, new JsonArray()).getList());
        user.setUai(infos.getJsonArray(Ticket.UAI, new JsonArray()).getList());
        user.setChildrenIds(infos.getJsonArray(Ticket.CHILDRENIDS, new JsonArray()).getList());
        user.setLevel(infos.getString(Ticket.LEVEL));
        user.setType(infos.getString(Ticket.TYPE));
        user.setLogin(infos.getString(Ticket.LOGIN));
        user.setAuthorizedActions(getUserActionsFromJSONArray(infos.getJsonArray(Ticket.AUTHORIZEDACTIONS, new JsonArray())));
        user.setGroupsIds(infos.getJsonArray(Ticket.GROUPSIDS, new JsonArray()).getList());
        user.setClasses(infos.getJsonArray(Ticket.CLASSES, new JsonArray()).getList());
        user.setStructures(infos.getJsonArray(Ticket.STRUCTURES, new JsonArray()).getList());
        return user;
    }

    public static JsonObject getUserActionJSON(UserInfos.Action action) {
        return new JsonObject()
                .put(Ticket.NAME, action.getName())
                .put(Ticket.DISPLAYNAME, action.getDisplayName())
                .put(Ticket.TYPE, action.getType());
    }

    public static JsonArray getUserActionJSONArray(List<UserInfos.Action> actions) {
        return new JsonArray(actions.stream().map(UserInfosHelper::getUserActionJSON).collect(Collectors.toList()));
    }

    public static UserInfos.Action getUserActionFromJSON(JsonObject oAction) {
        UserInfos.Action action = new UserInfos.Action();
        action.setName(oAction.getString(Ticket.NAME));
        action.setDisplayName(oAction.getString(Ticket.DISPLAYNAME));
        action.setType(oAction.getString(Ticket.TYPE));
        return action;
    }

     @SuppressWarnings("unchecked")
     public static List<UserInfos.Action> getUserActionsFromJSONArray(JsonArray actions) {
        return ((List<JsonObject>) actions.getList()).stream().map(UserInfosHelper::getUserActionFromJSON).collect(Collectors.toList());
     }

}
