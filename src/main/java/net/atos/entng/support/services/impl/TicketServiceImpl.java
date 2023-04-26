package net.atos.entng.support.services.impl;

import fr.wseduc.webutils.I18n;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.services.TicketService;

import static fr.wseduc.webutils.http.Renders.getHost;

public class TicketServiceImpl implements TicketService {

    private final TicketServiceNeo4jImpl ticketServiceNeo4j;

    public TicketServiceImpl(TicketServiceNeo4jImpl ticketServiceNeo4j) {
        this.ticketServiceNeo4j = ticketServiceNeo4j;
    }

    public Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, HttpServerRequest request) {
        Promise promise = Promise.promise();
        final JsonArray jsonListTickets = ticketsList;
        final JsonArray listUserIds = new JsonArray();
        for (Object ticket : jsonListTickets) {
            if (!(ticket instanceof JsonObject)) continue;
            String userId = ((JsonObject) ticket).getString("owner");
            if (!listUserIds.contains(userId)) {
                listUserIds.add(userId);
            }
        }
        // get profiles from neo4j
        ticketServiceNeo4j.getUsersFromList(listUserIds, event1 -> {
            if (event1.isRight()) {
                JsonArray listUsers = event1.right().getValue();
                // list of users got from neo4j
                for (Object user1 : listUsers) {
                    if (!(user1 instanceof JsonObject)) continue;
                    JsonObject jUser = (JsonObject) user1;
                    // traduction profil
                    String profil = jUser.getJsonArray("n.profiles").getString(0);
                    profil = I18n.getInstance().translate(profil, getHost(request), I18n.acceptLanguage(request));
                    // iterator on tickets, to see if the ids match
                    for (Object ticket : jsonListTickets) {
                        if (!(ticket instanceof JsonObject)) continue;
                        JsonObject jTicket = (JsonObject) ticket;
                        if (jTicket.getString("owner").equals(jUser.getString("n.id"))) {
                            jTicket.put("profile", profil);
                        }
                    }
                }

            }
            promise.complete(ticketsList);
        }
        );

        return promise.future();
    }
}
