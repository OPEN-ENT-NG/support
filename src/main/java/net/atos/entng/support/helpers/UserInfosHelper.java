package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.JiraTicket;
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
                .put(JiraTicket.HASAPP, userInfos.getHasApp())
                .put(JiraTicket.USERID, userInfos.getUserId())
                .put(JiraTicket.EXTERNALID, userInfos.getExternalId())
                .put(JiraTicket.FIRSTNAME, userInfos.getFirstName())
                .put(JiraTicket.LASTNAME, userInfos.getLastName())
                .put(JiraTicket.USERNAME, userInfos.getUsername())
                .put(JiraTicket.BIRTHDAY, userInfos.getBirthDate())
                .put(JiraTicket.CLASSNAMES, userInfos.getClasses())
                .put(JiraTicket.REALCLASSNAMES, userInfos.getRealClassNames())
                .put(JiraTicket.STRUCTURENAMES, userInfos.getStructureNames())
                .put(JiraTicket.UAI, userInfos.getUai())
                .put(JiraTicket.CHILDRENIDS, userInfos.getChildrenIds())
                .put(JiraTicket.LEVEL, userInfos.getLevel())
                .put(JiraTicket.TYPE, userInfos.getType())
                .put(JiraTicket.LOGIN, userInfos.getLogin())
                .put(JiraTicket.AUTHORIZEDACTIONS, getObjectJSONArray(userInfos.getAuthorizedActions(),UserInfosHelper::getUserActionJSON))
                .put(JiraTicket.GROUPSIDS, userInfos.getGroupsIds())
                .put(JiraTicket.CLASSES, userInfos.getClasses())
                .put(JiraTicket.STRUCTURES, userInfos.getStructures())
                .put(JiraTicket.APPS, getObjectJSONArray(userInfos.getApps(),UserInfosHelper::getUserAppsJSON));
    }

    @SuppressWarnings("unchecked")
    public static UserInfos getUserInfosFromJSON(JsonObject infos) {
        UserInfos user = new UserInfos();
        user.setHasApp(infos.getBoolean(JiraTicket.HASAPP));
        user.setUserId(infos.getString(JiraTicket.USERID));
        user.setExternalId(infos.getString(JiraTicket.EXTERNALID));
        user.setFirstName(infos.getString(JiraTicket.FIRSTNAME));
        user.setLastName(infos.getString(JiraTicket.LASTNAME));
        user.setUsername(infos.getString(JiraTicket.USERNAME));
        user.setBirthDate(infos.getString(JiraTicket.BIRTHDAY));
        if (infos.getJsonArray(JiraTicket.REALCLASSNAMES) != null) {
            user.setRealClassNames(infos.getJsonArray(JiraTicket.REALCLASSNAMES, new JsonArray()).getList());
        }
        user.setStructureNames(infos.getJsonArray(JiraTicket.STRUCTURENAMES, new JsonArray()).getList());
        user.setUai(infos.getJsonArray(JiraTicket.UAI, new JsonArray()).getList());
        user.setChildrenIds(infos.getJsonArray(JiraTicket.CHILDRENIDS, new JsonArray()).getList());
        user.setLevel(infos.getString(JiraTicket.LEVEL));
        user.setType(infos.getString(JiraTicket.TYPE));
        user.setLogin(infos.getString(JiraTicket.LOGIN));
        user.setAuthorizedActions(getObjectsFromJsonArray(infos.getJsonArray(JiraTicket.AUTHORIZEDACTIONS, new JsonArray()), UserInfosHelper::getUserActionFromJSON));
        user.setGroupsIds(infos.getJsonArray(JiraTicket.GROUPSIDS, new JsonArray()).getList());
        user.setClasses(infos.getJsonArray(JiraTicket.CLASSES, new JsonArray()).getList());
        user.setStructures(infos.getJsonArray(JiraTicket.STRUCTURES, new JsonArray()).getList());
        user.setApps(getObjectsFromJsonArray(infos.getJsonArray(JiraTicket.APPS, new JsonArray()), UserInfosHelper::getUserAppsFromJSON));
        return user;
    }

    public static JsonObject getUserActionJSON(UserInfos.Action action) {
        return new JsonObject()
                .put(JiraTicket.NAME, action.getName())
                .put(JiraTicket.DISPLAYNAME, action.getDisplayName())
                .put(JiraTicket.TYPE, action.getType());
    }

    public static JsonObject getUserAppsJSON(UserInfos.Application application) {
        return new JsonObject()
                .put(JiraTicket.ADDRESS, application.getAddress())
                .put(JiraTicket.DISPLAYNAME, application.getDisplayName());
    }

    public static <T> JsonArray getObjectJSONArray(List<T> list, Function<T, JsonObject> mapper) {
        return new JsonArray(list.stream().map(mapper).collect(Collectors.toList()));

    }

    public static UserInfos.Action getUserActionFromJSON(JsonObject oAction) {
        UserInfos.Action action = new UserInfos.Action();
        action.setName(oAction.getString(JiraTicket.NAME));
        action.setDisplayName(oAction.getString(JiraTicket.DISPLAYNAME));
        action.setType(oAction.getString(JiraTicket.TYPE));
        return action;
    }

    public static UserInfos.Application getUserAppsFromJSON(JsonObject oApplication) {
        UserInfos.Application application = new UserInfos.Application();
        application.setAddress(oApplication.getString(JiraTicket.ADDRESS));
        application.setDisplayName(oApplication.getString(JiraTicket.DISPLAYNAME));
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