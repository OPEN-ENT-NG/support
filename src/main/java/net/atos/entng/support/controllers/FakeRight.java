package net.atos.entng.support.controllers;

import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import io.vertx.core.http.HttpServerRequest;
import net.atos.entng.support.constants.Ticket;
import org.entcore.common.controller.ControllerHelper;

public class FakeRight extends ControllerHelper {
    public FakeRight() {
        super();
    }

    private void notImplemented(HttpServerRequest request) {
        request.response().setStatusCode(501).end();
    }

    @Get("/rights/support/auto/open")
    @SecuredAction(Ticket.AUTO_OPEN_TICKET)
    public void autoOpenTicket(HttpServerRequest request) {
        notImplemented(request);
    }

    @Get("/rights/support/reopen/on/comment")
    @SecuredAction(Ticket.REOPEN_TICKET_ON_COMMENT)
    public void reopenTicketOnComment(HttpServerRequest request) {
        notImplemented(request);
    }
}
