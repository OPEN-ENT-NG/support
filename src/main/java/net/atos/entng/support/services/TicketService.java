package net.atos.entng.support.services;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;

public interface TicketService {
     Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, HttpServerRequest request);
}
