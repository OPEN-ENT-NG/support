package net.atos.entng.support.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;

public interface WorkspaceService {

    /**
     * Add a new folder to the user workspace
     * @param name              folder name
     * @param owner             user identifier
     * @param ownerName         user name
     * @param parentFolderId    parent folder identifier
     * @return                  created folder data
     */
    Future<JsonObject> addFolder(String name, String owner, String ownerName, String parentFolderId);

    /**
     * Get the user workspace root files/folders
     * @param user              user infos
     * @return                  list of files/folders
     */
    Future<JsonArray> listRootDocuments(UserInfos user);
}
