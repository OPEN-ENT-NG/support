package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
                .put(Ticket.AUTHORIZEDACTIONS, getObjectJSONArray(userInfos.getAuthorizedActions(),UserInfosHelper::getUserActionJSON))
                .put(Ticket.GROUPSIDS, userInfos.getGroupsIds())
                .put(Ticket.CLASSES, userInfos.getClasses())
                .put(Ticket.STRUCTURES, userInfos.getStructures())
                .put(Ticket.APPS, getObjectJSONArray(userInfos.getApps(),UserInfosHelper::getUserAppsJSON));
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
        user.setAuthorizedActions(getObjectsFromJsonArray(infos.getJsonArray(Ticket.AUTHORIZEDACTIONS, new JsonArray()), UserInfosHelper::getUserActionFromJSON));
        user.setGroupsIds(infos.getJsonArray(Ticket.GROUPSIDS, new JsonArray()).getList());
        user.setClasses(infos.getJsonArray(Ticket.CLASSES, new JsonArray()).getList());
        user.setStructures(infos.getJsonArray(Ticket.STRUCTURES, new JsonArray()).getList());
        user.setApps(getObjectsFromJsonArray(infos.getJsonArray(Ticket.APPS, new JsonArray()), UserInfosHelper::getUserAppsFromJSON));
        return user;
    }

    public static JsonObject getUserActionJSON(UserInfos.Action action) {
        return new JsonObject()
                .put(Ticket.NAME, action.getName())
                .put(Ticket.DISPLAYNAME, action.getDisplayName())
                .put(Ticket.TYPE, action.getType());
    }

    public static JsonObject getUserAppsJSON(UserInfos.Application application) {
        return new JsonObject()
                .put(Ticket.ADDRESS, application.getAddress())
                .put(Ticket.DISPLAYNAME, application.getDisplayName());
    }

    public static <T> JsonArray getObjectJSONArray(List<T> list, Function<T, JsonObject> mapper) {
        return new JsonArray(list.stream().map(mapper).collect(Collectors.toList()));

    }

    public static UserInfos.Action getUserActionFromJSON(JsonObject oAction) {
        UserInfos.Action action = new UserInfos.Action();
        action.setName(oAction.getString(Ticket.NAME));
        action.setDisplayName(oAction.getString(Ticket.DISPLAYNAME));
        action.setType(oAction.getString(Ticket.TYPE));
        return action;
    }

    public static UserInfos.Application getUserAppsFromJSON(JsonObject oApplication) {
        UserInfos.Application application = new UserInfos.Application();
        application.setAddress(oApplication.getString(Ticket.ADDRESS));
        application.setDisplayName(oApplication.getString(Ticket.DISPLAYNAME));
        return application;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getObjectsFromJsonArray(JsonArray jsonArray, Function<JsonObject, T> mapper) {
        return ((List<JsonObject>) jsonArray.getList()).stream().map(mapper).collect(Collectors.toList());
    }

    public static String getAppName(UserInfos user, String appAddress) {
        return user.getApps().stream()
                .filter(app -> Objects.equals(app.getAddress(), appAddress))
                .map(UserInfos.Application::getDisplayName)
                .findFirst()
                .orElse("");
    }

}
