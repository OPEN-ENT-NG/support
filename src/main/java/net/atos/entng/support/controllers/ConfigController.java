package net.atos.entng.support.controllers;

import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.JiraTicket;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.AdminFilter;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

public class ConfigController extends ControllerHelper {
    @Get("/config")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getConfig(final HttpServerRequest request) {
        renderJson(request, config);
    }

    @Get("/config/thresholdDirectExportTickets")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void getConfigMaxTickets(final HttpServerRequest request) {
        renderJson(request, new JsonObject().put(JiraTicket.THRESHOLD, config.getString(JiraTicket.THRESHOLD_DIRECT_EXPORT_TICKETS)));
    }

    @Get("/config/numberTicketsPerPage")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getNumberTicketsPerPage(final HttpServerRequest request) {
        renderJson(request, new JsonObject().put(JiraTicket.NBTICKETSPERPAGE, config.getInteger(JiraTicket.NBTICKETSPERPAGE, 25)));
    }
}