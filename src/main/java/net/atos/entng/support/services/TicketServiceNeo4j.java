package net.atos.entng.support.services;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import java.util.Set;

public interface TicketServiceNeo4j {
    public Future<JsonArray> getUsersFromList(JsonArray listUserIds);
}
