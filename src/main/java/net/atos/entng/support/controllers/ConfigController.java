package net.atos.entng.support.controllers;

import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;

public class ConfigController extends ControllerHelper {
    @Get("/config")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(SuperAdminFilter.class)
    public void getConfig(final HttpServerRequest request) {
        renderJson(request, config);
    }
}
