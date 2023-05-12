package net.atos.entng.support.services.impl;

import fr.wseduc.webutils.I18n;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.model.I18nConfig;
import net.atos.entng.support.services.TicketService;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public class TicketServiceImpl implements TicketService {

    private final TicketServiceNeo4jImpl ticketServiceNeo4j;

    public TicketServiceImpl(TicketServiceNeo4jImpl ticketServiceNeo4j) {
        this.ticketServiceNeo4j = ticketServiceNeo4j;
    }

    public Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, I18nConfig i18nConfig) {
        Promise<JsonArray> promise = Promise.promise();
        final JsonArray jsonListTickets = ticketsList;
        final Set<String> listUserIds = jsonListTickets.stream()
                .filter(JsonObject.class::isInstance)
                .map(ticket -> ((JsonObject) ticket).getString(Ticket.OWNER))
                .collect(Collectors.toSet());

        // get profiles from neo4j
        ticketServiceNeo4j.getUsersFromList(new JsonArray(new ArrayList<>(listUserIds)), event1 -> {
            if (event1.isRight()) {
                JsonArray listUsers = event1.right().getValue();
                // list of users got from neo4j
                listUsers.stream()
                        .filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast)
                        .forEach(jUser -> {
                            // traduction profil
                            String profil = jUser.getJsonArray("n.profiles").getString(0);
                            profil = I18n.getInstance().translate(profil, i18nConfig.getDomain(), i18nConfig.getLang());
                            // iterator on tickets, to see if the ids match
                            String finalProfil = profil;
                            jsonListTickets.stream()
                                    .filter(JsonObject.class::isInstance)
                                    .map(JsonObject.class::cast)
                                    .filter(jTicket -> jTicket.getString(Ticket.OWNER).equals(jUser.getString("n.id")))
                                    .forEach(jTicket -> jTicket.put(Ticket.PROFILE, finalProfil));
                        });
            }
            promise.complete(jsonListTickets);
        });

        return promise.future();
    }
}
