package net.atos.entng.support.services.impl;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.enums.EventBusActions;
import net.atos.entng.support.helpers.EventBusHelper;
import net.atos.entng.support.services.WorkspaceService;
import org.entcore.common.folders.ElementQuery;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.share.impl.GenericShareService;
import org.entcore.common.share.impl.MongoDbShareService;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;

import java.util.HashMap;
import java.util.HashSet;

public class DefaultWorkspaceService implements WorkspaceService {

    private final EventBus eb;
    private final FolderManager folderManager;

    public DefaultWorkspaceService(Vertx vertx, Storage storage, JsonObject config) {
        this.eb = vertx.eventBus();

        String node = (String) vertx.sharedData().getLocalMap("server").get("node");
        if (node == null) {
            node = "";
        }
        String imageResizerAddress = node + config.getString("image-resizer-address", "wse.image.resizer");
        final boolean useOldQueryChildren = config.getBoolean("old-query", false);
        GenericShareService shareService = new MongoDbShareService(vertx.eventBus(), MongoDb.getInstance(), "documents", null, new HashMap<>());

        folderManager = FolderManager.mongoManager("documents", storage, vertx, shareService, imageResizerAddress, useOldQueryChildren);
    }

    @Override
    public Future<JsonObject> addFolder(String name, String owner, String ownerName, String parentFolderId) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject action = new JsonObject()
                .put(Ticket.ACTION, EventBusActions.ADDFOLDER.action())
                .put(Ticket.NAME, name)
                .put(Ticket.OWNER, owner)
                .put(Ticket.OWNERNAME, ownerName)
                .put(Ticket.PARENTFOLDERID, parentFolderId);


        EventBusHelper.requestJsonObject(EventBusActions.EventBusAddresses.WORKSPACE_BUS_ADDRESS.address(), eb, action)
                .onFailure(fail -> {
                    String message = String.format("[Support@%s::addFolder]Error while adding folder %s",
                            this.getClass().getSimpleName(), name);
                    promise.fail(message);
                })
                .onSuccess(promise::complete);

        return promise.future();
    }

    @Override
    public Future<JsonArray> listRootDocuments(UserInfos user) {
        Promise<JsonArray> promise = Promise.promise();

        if (user != null && user.getUserId() != null) {
            ElementQuery query = new ElementQuery(false);
            query.setHierarchical(false);
            query.setTrash(false);
            query.setNoParent(true);
            query.setHasBeenShared(false);
            query.setVisibilitiesNotIn(new HashSet<>());
            query.getVisibilitiesNotIn().add("protected");
            query.getVisibilitiesNotIn().add("public");
            query.setType(FolderManager.FILE_TYPE);
            query.setProjection(ElementQuery.defaultProjection());
            query.getProjection().add("comments");
            query.getProjection().add("application");
            query.getProjection().add("trasher");
            query.getProjection().add("protected");
            query.getProjection().add("ancestors");
            query.getProjection().add("externalId");
            query.getProjection().add("isShared");

            query.setType(null);

            folderManager.findByQuery(query, user, res -> {
                if (res.failed()) {
                    promise.fail(res.cause().getMessage());
                } else {
                    promise.complete(res.result());
                }
            });

        } else {
            String message = String.format("[Support@%s::listRootDocuments] Error fetching user id", this.getClass().getName());
            promise.fail(message);
        }

        return promise.future();

    }

}
