package net.atos.entng.support.export;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.CSVHelper;
import net.atos.entng.support.model.ExportFile;
import net.atos.entng.support.model.I18nConfig;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.impl.TicketServiceImpl;
import net.atos.entng.support.services.impl.TicketServiceNeo4jImpl;
import net.atos.entng.support.services.impl.TicketServiceSqlImpl;
import java.util.Objects;


public class TicketExportWorker extends ExportWorker{
    private TicketServiceSql ticketServiceSql;
    private TicketService ticketService;
    @Override
    protected String getFolderName() {
        return Ticket.EXPORT;
    }

    protected Future<Void> init(JsonObject config) {
        Promise<Void> promise = Promise.promise();
        ticketServiceSql = new TicketServiceSqlImpl(null);
        TicketServiceNeo4jImpl ticketServiceNeo4jImpl = new TicketServiceNeo4jImpl();
        ticketService = new TicketServiceImpl(ticketServiceNeo4jImpl);

        promise.complete();
        return promise.future();
    }

    @Override
    protected Future<ExportFile> getData(String action, JsonObject params) {
        if (action.equals(Ticket.EXPORT_TICKETS)) {
            this.exportNotification = "support.export-events";
            return getTickets(params);
        }
        String message = String.format("[Support@%s::getData] invalid action %s", this.getClass().getName(), action);
        log.error(message);
        return Future.failedFuture(message);
    }

    private Future<ExportFile> getTickets(JsonObject params) {
        Promise<ExportFile> promise = Promise.promise();
        String structureId = params.getString(Ticket.STRUCTURE_ID);
        JsonObject structureIds = params.getJsonObject(Ticket.USER);
        String local = params.getString(Ticket.LOCALE);
        String domain = params.getString(Ticket.DOMAIN);
        I18nConfig i18nConfig = new I18nConfig(domain, local);

        Future<JsonArray> ticketsFuture;
        if (Objects.equals(structureId, Ticket.ASTERISK)) {
            ticketsFuture = ticketServiceSql.getTicketsFromArrayOfStructureId(
                    new JsonObject().put(Ticket.STRUCTURE_IDS, structureIds.getJsonArray(Ticket.STRUCTURES)));
        } else {
            ticketsFuture = ticketService.listChildren(structureId)
                    .compose(ticketServiceSql::getTicketsFromArrayOfStructureId);
        }

        ticketsFuture
                .compose(tickets -> ticketService.getProfileFromTickets(tickets, i18nConfig))
                .compose(ticketService::getSchoolFromTickets)
                .onSuccess(result -> {
                    TicketsCSVExport pce = new TicketsCSVExport(result, i18nConfig);
                    promise.complete(CSVHelper.getExportFile(pce.filename(), pce.generate()));
                })
                .onFailure(promise::fail);

        return promise.future();
    }
}
