package net.atos.entng.support.services.impl;

import fr.wseduc.webutils.I18n;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.enums.Error;
import net.atos.entng.support.model.I18nConfig;
import net.atos.entng.support.services.TicketService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TicketServiceImpl implements TicketService {

    public Future<JsonArray> getProfileFromTickets(JsonArray ticketsList, I18nConfig i18nConfig) {
        Promise<JsonArray> promise = Promise.promise();
        final JsonArray jsonListTickets = ticketsList;
        final Set<String> listUserIds = jsonListTickets.stream()
                .filter(JsonObject.class::isInstance)
                .map(ticket -> ((JsonObject) ticket).getString(JiraTicket.OWNER))
                .collect(Collectors.toSet());

        // get profiles from neo4j
        TicketServiceNeo4jImpl.getUsersFromList(new JsonArray(new ArrayList<>(listUserIds)), event1 -> {
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
                                    .filter(jTicket -> jTicket.getString(JiraTicket.OWNER).equals(jUser.getString("n.id")))
                                    .forEach(jTicket -> jTicket.put(JiraTicket.PROFILE, finalProfil));
                        });
            }
            promise.complete(jsonListTickets);
        });

        return promise.future();
    }

    public Future<JsonArray> getSchoolFromTickets(JsonArray ticketsList) {
        Promise<JsonArray> promise = Promise.promise();
        final JsonArray jsonListTickets = ticketsList;
        final List<String> listSchoolIds = jsonListTickets.stream()
                .filter(JsonObject.class::isInstance)
                .map(ticket -> ((JsonObject) ticket).getString(JiraTicket.SCHOOL_ID))
                .collect(Collectors.toList());

        // get school name from neo4j
        TicketServiceNeo4jImpl.getSchoolFromList(new JsonArray(new ArrayList<>(listSchoolIds)))
                .onSuccess(listSchools -> {
                    listSchools.stream()
                            .filter(JsonObject.class::isInstance)
                            .map(JsonObject.class::cast)
                            .forEach(jSchool -> {
                                String schoolName = jSchool.getString("s.name");
                                // iterator on tickets, to see if the ids match
                                jsonListTickets.stream()
                                        .filter(JsonObject.class::isInstance)
                                        .map(JsonObject.class::cast)
                                        .filter(jTicket -> jTicket.getString(JiraTicket.SCHOOL_ID).equals(jSchool.getString("s.id")))
                                        .forEach(jTicket -> jTicket.put(JiraTicket.SCHOOL, schoolName));
                            });
                    promise.complete(jsonListTickets);
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    public Future<JsonObject> getSchoolWorkflowRightFromUserId(String userId, String workflowWanted, String structureId) {
        Promise<JsonObject> promise = Promise.promise();
        TicketServiceNeo4jImpl.getSchoolWorkflowRights(userId, workflowWanted, structureId)
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<JsonObject> listStructureChildren(List<String> structureIds) {
        Promise<JsonObject> promise = Promise.promise();
        TicketServiceNeo4jImpl.listStructureChildren(structureIds)
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        return promise.future();
    }

    public Future<JsonObject> sortSchoolByName(List<String> structureIds) {
        Promise<JsonObject> promise = Promise.promise();
        if (structureIds != null && !structureIds.isEmpty()){
            TicketServiceNeo4jImpl.sortSchoolByName(structureIds)
                    .onSuccess(promise::complete)
                    .onFailure(promise::fail);
        } else promise.fail(Error.SORT_BY_STRUCTURE.getI18n());
        return promise.future();
    }


    public CompositeFuture getSchoolAndProfileFromTicket(JsonArray tickets, I18nConfig i18nConfig) {
        return CompositeFuture.all(getProfileFromTickets(tickets, i18nConfig), getSchoolFromTickets(tickets));
    }
}