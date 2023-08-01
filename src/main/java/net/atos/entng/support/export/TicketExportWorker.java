package net.atos.entng.support.export;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.CSVHelper;
import net.atos.entng.support.helpers.UserInfosHelper;
import net.atos.entng.support.model.ExportFile;
import net.atos.entng.support.model.I18nConfig;
import net.atos.entng.support.services.FileService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.impl.*;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.common.user.UserInfos;
import org.vertx.java.busmods.BusModBase;

import java.util.Collections;
import java.util.Objects;

import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

public class TicketExportWorker extends BusModBase implements Handler<Message<JsonObject>> {

    protected final Logger log = LoggerFactory.getLogger(TicketExportWorker.class);
    protected Context exportContext;

    private TicketServiceSql ticketServiceSql;
    private TicketService ticketService;

    private WorkspaceHelper workspaceHelper;
    private TimelineHelper timelineHelper;
    private FileService fileService;

    protected String exportNotification;

    @Override
    public void start() {
        super.start();
        ticketServiceSql = new TicketServiceSqlImpl(null);
        ticketService = new TicketServiceImpl();

        Storage storage = new StorageFactory(vertx, new JsonObject()).getStorage();
        fileService = new DefaultFileService(storage);
        workspaceHelper = new WorkspaceHelper(vertx.eventBus(), storage);
        timelineHelper = new TimelineHelper(this.vertx, this.vertx.eventBus(), config());

        exportContext = vertx.getOrCreateContext();
        String launchLog = String.format("[Support@%s::start] Launching worker %s, deploy verticle %s",
                this.getClass().getSimpleName(), this.getClass().getSimpleName(), exportContext.deploymentID());
        log.info(launchLog);

        eb.consumer(this.getClass().getName(), this);
    }

    @Override
    public void handle(Message<JsonObject> event) {
        event.reply(new JsonObject().put(Ticket.STATUS, Ticket.OK));
        JsonObject params = event.body();
        UserInfos user = UserInfosHelper.getUserInfosFromJSON(event.body().getJsonObject(Ticket.USER));

        String local = params.getString(Ticket.LOCALE);
        String domain = params.getString(Ticket.DOMAIN);
        I18nConfig i18nConfig = new I18nConfig(domain, local);

        this.exportNotification = "support.export-events";
        createCSVFile(params, i18nConfig)
                .compose(file -> exportData(file, user))
                .compose(fileInfos -> sendNotification(user, fileInfos))
                .onFailure(fail -> {
                    log.error(String.format("[Support@%s::handle] Error while exporting tickets  %s",
                            this.getClass().getSimpleName(), fail.getMessage()));
                    exportNotification = "support.export-events-error";
                    sendNotification(user, new JsonObject());
                });
    }

    private Future<ExportFile> createCSVFile(JsonObject params, I18nConfig i18nConfig) {
        Promise<ExportFile> promise = Promise.promise();
        String structureId = params.getString(Ticket.STRUCTURE_ID);

        getStructureIds(structureId, params)
                .compose(structureIds -> ticketServiceSql.getTicketsFromStructureIds(structureIds))
                .compose(tickets -> ticketService.getProfileFromTickets(tickets, i18nConfig))
                .compose(ticketService::getSchoolFromTickets)
                .onSuccess(result -> {
                    TicketsCSVExport pce = new TicketsCSVExport(result, i18nConfig);
                    promise.complete(CSVHelper.getExportFile(pce.filename(), pce.generate()));
                })
                .onFailure(fail -> {
                    log.error(String.format("[Support@%s::createCSVFile] Error while creating CSV file %s",
                            this.getClass().getSimpleName(), fail.getMessage()));
                    promise.fail(fail.getMessage());
                });

        return promise.future();
    }

    private Future<JsonObject> getStructureIds(String structureId, JsonObject params) {
        if (Objects.equals(structureId, Ticket.ASTERISK))
            return Future.succeededFuture(new JsonObject().put(Ticket.STRUCTUREIDS,
                    params.getJsonObject(Ticket.USER).getJsonArray(Ticket.STRUCTURES)));
        return ticketService.listStructureChildren(structureId);
    }

    protected Future<JsonObject> exportData(ExportFile exportFile, UserInfos user) {
        Promise<JsonObject> promise = Promise.promise();

        fileService.add(exportFile.getBuffer(), exportFile.getContentType(), exportFile.getFilename())
                .onSuccess(file -> this.workspaceHelper.addDocument(file, user, exportFile.getFilename(), "media-library",
                        false, new JsonArray(), handlerToAsyncHandler(message -> {
                            if (Ticket.OK.equals(message.body().getString(Ticket.STATUS))) {
                                promise.complete(message.body());
                            }
                        })))
                .onFailure(fail -> {
                    log.error(String.format("[Support@%s::exportData] Error adding folder/file in workspace for export %s",
                            this.getClass().getSimpleName(), fail.getMessage()));
                    promise.fail(fail.getMessage());
                });

        return promise.future();
    }

    protected Future<Void> sendNotification(UserInfos user, JsonObject fileInfos) {
        Promise<Void> promise = Promise.promise();
        JsonObject params = new JsonObject()
                .put(Ticket.FILENAME, fileInfos.getString(Ticket.NAME))
                .put(Ticket.PUSHNOTIF, new JsonObject()
                        .put(Ticket.TITLE, "support.push.export.finished")
                        .put(Ticket.BODY, ""));
        timelineHelper.notifyTimeline(null, this.exportNotification, user,
                Collections.singletonList(user.getUserId()), "", params);
        promise.complete();
        return promise.future();
    }
}
