/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.support.controllers;

import static net.atos.entng.support.Support.SUPPORT_NAME;
import static net.atos.entng.support.Support.bugTrackerCommDirect;
import static net.atos.entng.support.enums.TicketStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fr.wseduc.bus.BusAddress;
import net.atos.entng.support.Support;
import net.atos.entng.support.enums.BugTrackerSyncType;
import net.atos.entng.support.filters.Admin;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;

import net.atos.entng.support.services.impl.TicketServiceNeo4jImpl;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;


public class TicketController extends ControllerHelper {

    private static final String TICKET_CREATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_CREATED";
    private static final String TICKET_UPDATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_UPDATED";
    private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;

    private final TicketServiceSql ticketServiceSql;
    private final UserService userService;
    private final EscalationService escalationService;
    private final Storage storage;

    public TicketController(TicketServiceSql ts, EscalationService es, UserService us, Storage storage) {
        ticketServiceSql = ts;
        userService = us;
        escalationService = es;
        this.storage = storage;
    }

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    @Post("/ticket")
    @ApiDoc("Create a ticket")
    @SecuredAction("support.ticket.create")
    public void createTicket(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "createTicket", new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject ticket) {
                            ticket.put("status", NEW.status());
                            ticket.put("event_count", 1);
                            JsonArray attachments = ticket.getJsonArray("attachments", null);
                            ticket.remove("attachments");
                            ticketServiceSql.createTicket(ticket, attachments, user, I18n.acceptLanguage(request), getCreateOrUpdateTicketHandler(request, user, ticket, null));
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    private Handler<Either<String, JsonObject>> getCreateOrUpdateTicketHandler(final HttpServerRequest request,
                                                                               final UserInfos user, final JsonObject ticket, final String ticketId) {

        return new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if (event.isRight()) {
                    final JsonObject response = event.right().getValue();
                    if (response != null && response.size() > 0) {
                        if (ticketId == null) {
                            notifyTicketCreated(request, user, response, ticket);
                            response.put("owner_name", user.getUsername());
                            ticketServiceSql.createTicketHisto(response.getInteger("id").toString(), I18n.getInstance().translate("support.ticket.histo.creation", getHost(request), I18n.acceptLanguage(request)),
                                    ticket.getInteger("status"), user.getUserId(), 1, new Handler<Either<String, JsonObject>>() {
                                        @Override
                                        public void handle(Either<String, JsonObject> res) {
                                            if (res.isLeft()) {
                                                log.error("Error creation historization : " + res.left().getValue());
                                            }
                                        }
                                    });
                            renderJson(request, response, 200);
                        } else {
                            ticketServiceSql.updateEventCount(ticketId, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> res) {
                                    if (res.isLeft()) {
                                        log.error("Error updating ticket (event_count) : " + res.left().getValue());
                                    }
                                }
                            });
                            boolean commentSentToBugtracker = false;
                            // we only historize if no comment has been added. If there is a comment, it will appear in the history
                            if( ticket.getString("newComment") == null || "".equals(ticket.getString("newComment")) ) {
                                ticketServiceSql.createTicketHisto(ticketId, I18n.getInstance().translate("support.ticket.histo.modification", getHost(request), I18n.acceptLanguage(request)),
                                        ticket.getInteger("status"), user.getUserId(), 2,  new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> res) {
                                                if (res.isLeft()) {
                                                    log.error("Error creation historization : " + res.left().getValue());
                                                }
                                            }
                                        });
                            } else {
                                // if option activated, we can send the comment directly to the bug-tracker
                                if( bugTrackerCommDirect ) {
                                    sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                    commentSentToBugtracker = true;
                                }
                            }
                            notifyTicketUpdated(request, ticketId, user, response);
                            JsonArray attachments = ticket.getJsonArray("attachments");
                            if (escalationService != null && attachments != null && attachments.size() > 0) {
                                if(escalationService.getBugTrackerType().getBugTrackerSyncType()
                                        == BugTrackerSyncType.ASYNC && !commentSentToBugtracker) {
                                    sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                    renderJson(request, response, 200);
                                } else {
                                    escalationService.syncAttachments(ticketId, attachments, new Handler<Either<String, JsonObject>>() {
                                        @Override
                                        public void handle(Either<String, JsonObject> res) {
                                            if (res.isRight()) {
                                                Integer issueId = res.right().getValue().getInteger("issue_id");
                                                if (issueId != null) {
                                                    refreshIssue(issueId, request);
                                                } else {
                                                    renderJson(request, response, 200);
                                                }
                                            } else {
                                                log.error("Error syncing attachments : " + res.left().getValue());
                                                renderJson(request, response, 200);
                                            }
                                        }
                                    });
                                }
                            } else {
                                renderJson(request, response, 200);
                            }
                        }
                    } else {
                        notFound(request);
                    }
                } else {
                    badRequest(request, event.left().getValue());
                }
            }
        };
    }

    /**
     * Send ticket updates to bugtracker
     * @param request Original request
     * @param ticketId Id of the ticket updated
     * @param ticket Content of ticket updated
     * @param user User updating ticket
     */
    private void sendTicketUpdateToIssue(final HttpServerRequest request, final String ticketId, final JsonObject ticket,
                                         final UserInfos user) {
        ticketServiceSql.getIssue(ticketId, new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> res) {
                if (res.isRight() && res.right().getValue().size() > 0) {
                    JsonObject issue = res.right().getValue().getJsonObject(0);
                    Long id = issue.getLong("id");
                    JsonObject comment = new JsonObject();
                    if(ticket != null && ticket.containsKey("newComment")) {
                        comment.put("content", ticket.getString("newComment"));
                    }
                    if(escalationService.getBugTrackerType().getBugTrackerSyncType()
                            == BugTrackerSyncType.SYNC) {
                        sendIssueComment(user, comment, id.toString(), request);
                    } else {
                        escalateTicket(request, ticketId, true);
                    }
                } else if (res.isLeft()) {
                    log.error("No associated issue found");
                }
            }
        });
    }

    private void updateIssuesStatus(HttpServerRequest request, List<Integer> ids) {
        for (Integer id : ids) {
            sendTicketUpdateToIssue(request, id.toString(), null, null);
        }
    }

    /**
     * Notify local administrators that a ticket has been created
     */
    private void notifyTicketCreated(final HttpServerRequest request, final UserInfos user,
                                     final JsonObject response, final JsonObject ticket) {

        final String eventType = TICKET_CREATED_EVENT_TYPE;
        final String notificationName = "ticket-created";

        try {
            final long id = response.getLong("id", 0L);
            final String ticketSubject = ticket.getString("subject", null);
            final String structure = ticket.getString("school_id", null);

            if (id == 0L || ticketSubject == null || structure == null) {
                log.error("Could not get parameters id, subject or school_id. Unable to send timeline " + eventType
                        + " notification.");
                return;
            }
            final String ticketId = Long.toString(id);

            userService.getLocalAdministrators(structure, event -> {
                if (event != null) {
                    Set<String> recipientSet = new HashSet<>();
                    for (Object o : event) {
                        if (!(o instanceof JsonObject)) continue;
                        JsonObject j = (JsonObject) o;
                        String id1 = j.getString("id");
                        if (!user.getUserId().equals(id1)) {
                            recipientSet.add(id1);
                        }
                    }

                    List<String> recipients = new ArrayList<>(recipientSet);
                    if (!recipients.isEmpty()) {
                        JsonObject params = new JsonObject();
                        params.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
                        params.put("ticketUri", "/support#/ticket/" + ticketId)
                                .put("username", user.getUsername())
                                .put("ticketid", ticketId)
                                .put("ticketsubject", shortenSubject(ticketSubject));
                        params.put("resourceUri", params.getString("ticketUri"));

                        JsonObject pushNotif = new JsonObject()
                                .put("title", "push-notif.support." + notificationName)
                                .put("body", I18n.getInstance()
                                    .translate(
                                            "push-notif." + notificationName + ".body",
                                            getHost(request),
                                            I18n.acceptLanguage(request),
                                            user.getUsername(),
                                            ticketId
                                    ));
                        params.put("pushNotif", pushNotif);

                        notification.notifyTimeline(request, "support." + notificationName, user, recipients, ticketId, params);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Unable to send timeline " + eventType + " notification.", e);
        }
    }

    private String shortenSubject(String subject) {
        if (subject.length() > SUBJECT_LENGTH_IN_NOTIFICATION) {
            return subject.substring(0, SUBJECT_LENGTH_IN_NOTIFICATION)
                    .concat(" [...]");
        }
        return subject;
    }

    @Put("/ticket/:id")
    @ApiDoc("Update a ticket")
    @SecuredAction(value = "support.manager", type = ActionType.RESOURCE)
    @ResourceFilter(OwnerOrLocalAdmin.class)
    public void updateTicket(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "updateTicket", new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject ticket) {
                            // TODO : do not authorize description update if there is a comment
                            ticketServiceSql.updateTicket(ticketId, ticket, user,
                                    getCreateOrUpdateTicketHandler(request, user, ticket, ticketId));
                            //notifyTicketUpdated(request, user, ticket);
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    @Post("/ticketstatus/:newStatus")
    @ApiDoc("Update multiple ticket status")
    @ResourceFilter(OwnerOrLocalAdmin.class)
    public void updateTicketStatus(final HttpServerRequest request) {
        final Integer newStatus = Integer.valueOf(request.params().get("newStatus"));
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    // getting list of tickets ids to update, based on ticketUpdate.json file format.
                    RequestUtils.bodyToJson(request, pathPrefix + "ticketUpdate", new Handler<JsonObject>() {
                        public void handle(JsonObject data) {
                            final List<Integer> ids = data.getJsonArray("ids").getList();
                            ticketServiceSql.updateTicketStatus(newStatus, ids, new Handler<Either<String, JsonObject>>() {
                                @Override
                                public void handle(Either<String, JsonObject> event) {
                                    if (event.isRight()) {
                                        createTicketHistoMultiple(ids, I18n.getInstance().translate("support.ticket.histo.mass.modification", getHost(request), I18n.acceptLanguage(request)), newStatus, user.getUserId());
                                        request.response().setStatusCode(200).end();
                                        if(escalationService.getBugTrackerType().getBugTrackerSyncType()
                                                == BugTrackerSyncType.ASYNC) {
                                            updateIssuesStatus(request, ids);
                                        }
                                        //renderJson(request, wholeIssue);
                                    } else {
                                        log.error("Error when updating ticket statuses.");
                                        renderError(request, new JsonObject().put("error", event.left().getValue()));
                                    }
                                }
                            });
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });
    }

    /**
     * Notify owner and local administrators that the ticket has been updated
     */
    private void notifyTicketUpdated(final HttpServerRequest request, final String ticketId,
                                     final UserInfos user, final JsonObject response) {

        final String eventType = TICKET_UPDATED_EVENT_TYPE;
        final String notificationName = "ticket-updated";

        try {
            final String ticketSubject = response.getString("subject", null);
            final String ticketOwner = response.getString("owner", null);
            final String structure = response.getString("school_id", null);

            if (ticketSubject == null || ticketOwner == null || structure == null) {
                log.error("Could not get parameters subject, owner or school_id. Unable to send timeline " + eventType
                        + " notification.");
                return;
            }

            final Set<String> recipientSet = new HashSet<>();
            if (!ticketOwner.equals(user.getUserId())) {
                recipientSet.add(ticketOwner);
            }

            userService.getLocalAdministrators(structure, event -> {
                if (event != null) {
                    for (Object o : event) {
                        if (!(o instanceof JsonObject)) continue;
                        JsonObject j = (JsonObject) o;
                        String id = j.getString("id");
                        if (!user.getUserId().equals(id)) {
                            recipientSet.add(id);
                        }
                    }

                    List<String> recipients = new ArrayList<>(recipientSet);

                    if (!recipients.isEmpty()) {
                        JsonObject params = new JsonObject();
                        params.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
                        params.put("ticketUri", "/support#/ticket/" + ticketId)
                                .put("username", user.getUsername())
                                .put("ticketid", ticketId)
                                .put("ticketsubject", shortenSubject(ticketSubject));
                        params.put("resourceUri", params.getString("ticketUri"));

                        JsonObject pushNotif = new JsonObject()
                                .put("title", "push-notif.support." + notificationName)
                                .put("body", I18n.getInstance()
                                        .translate(
                                                "push-notif." + notificationName + ".body",
                                                request!= null ? getHost(request) : I18n.DEFAULT_DOMAIN,
                                                request!= null ? I18n.acceptLanguage(request) : "fr",
                                                user.getUsername(),
                                                ticketId
                                        ));
                        params.put("pushNotif", pushNotif);

                        notification.notifyTimeline(request, "support." + notificationName, user, recipients, params);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Unable to send timeline " + eventType + " notification.", e);
        }
    }


    @Get("/tickets")
    @ApiDoc("If current user is local admin, get all tickets. Otherwise, get my tickets")
    @SecuredAction("support.ticket.list")
    public void listTickets(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    Map<String, UserInfos.Function> functions = user.getFunctions();
                    if (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN)) {
                        ticketServiceSql.listTickets(user, new Handler<Either<String, JsonArray>>() {
                            @Override
                            public void handle(Either<String, JsonArray> event) {
                                // getting the profile for users
                                if( event.isRight()){
                                    final JsonArray jsonListTickets = event.right().getValue();
                                    final JsonArray listUserIds = new JsonArray();
                                    // get list of unique user ids
                                    for (Object ticket : jsonListTickets) {
                                        if (!(ticket instanceof JsonObject)) continue;
                                        String userId = ((JsonObject) ticket).getString("owner");
                                        if( !listUserIds.contains(userId)){
                                            listUserIds.add(userId);
                                        }
                                    }
                                    // get profiles from neo4j
                                    TicketServiceNeo4jImpl ticketServiceNeo4j = new TicketServiceNeo4jImpl();
                                    ticketServiceNeo4j.getUsersFromList(listUserIds, new Handler<Either<String, JsonArray>>() {
                                        @Override
                                        public void handle(Either<String, JsonArray> event) {
                                            if( event.isRight()){
                                                JsonArray listUsers = event.right().getValue();
                                                // list of users got from neo4j
                                                for( Object user : listUsers ) {
                                                    if (!(user instanceof JsonObject)) continue;
                                                    JsonObject jUser = (JsonObject)user;
                                                    // traduction porfil
                                                    String profil = jUser.getJsonArray("n.profiles").getString(0);
                                                    profil = I18n.getInstance().translate(profil, getHost(request), I18n.acceptLanguage(request));
                                                    // iterator on tickets, to see if the ids match
                                                    for( Object ticket : jsonListTickets) {
                                                        if (!(ticket instanceof JsonObject)) continue;
                                                        JsonObject jTicket = (JsonObject)ticket;
                                                        if( jTicket.getString("owner").equals(jUser.getString("n.id"))) {
                                                            jTicket.put("profile", profil);
                                                        }
                                                    }
                                                }
                                                renderJson(request, jsonListTickets);
                                            }
                                        }
                                    });
                                }
                            }
                        });
                    } else {
                        ticketServiceSql.listMyTickets(user, arrayResponseHandler(request));
                    }
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }

    @Get("/escalation")
    @ApiDoc("Return true if escalation is activated. False otherwise")
    @SecuredAction(value = "support.escalation.activation.status", type = ActionType.AUTHENTICATED)
    public void isEscalationActivated(final HttpServerRequest request) {
        JsonObject result = new JsonObject().put("isEscalationActivated", Support.escalationIsActivated());
        renderJson(request, result);
    }

    @BusAddress("support.update.bugtracker")
    @ApiDoc("Update ticket with information from bugtracker")
    public void updateTicketFromBugTracker(final Message<JsonObject> message) {
        escalationService.updateTicketFromBugTracker(message, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if(event.isLeft()) {
                    log.error("Support : error when updating ticket from bugtracker " + event.left().getValue());
                    message.reply(new JsonObject().put("status", "KO").put("message",event.left().getValue()));
                } else {
                    JsonObject response = event.right().getValue();
                    if(response.containsKey("user") && response.containsKey("ticket")) {
                        JsonObject userJson = response.getJsonObject("user");
                        UserInfos user = new UserInfos();
                        user.setUserId(userJson.getString("userid"));
                        user.setUsername(userJson.getString("username"));
                        user.setType(userJson.getString("type"));

                        JsonObject ticket = response.getJsonObject("ticket");
                        ticket.put("owner", ticket.getString("owner_id"));
                        String ticketId = ticket.getInteger("id").toString();
                        notifyTicketUpdated(null, ticketId, user, ticket);
                    }
                    message.reply(new JsonObject().put("status", "OK"));
                }
            }
        });
    }

    @Get("/profile/:userId")
    @ApiDoc("Returns the profile of a user")
    public void getProfileString(final HttpServerRequest request) {
        final String userId = request.params().get("userId");
        TicketServiceNeo4jImpl ticketServiceNeo4j = new TicketServiceNeo4jImpl();
        JsonArray jsonUserId = new JsonArray();
        jsonUserId.add(userId);
        ticketServiceNeo4j.getUsersFromList(jsonUserId, new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> event) {
                if( event.isRight() && event.right().getValue().size() > 0){
                    JsonObject jUser = event.right().getValue().getJsonObject(0);
                        // traduction profil
                        String profil = jUser.getJsonArray("n.profiles").getString(0);
                        profil = I18n.getInstance().translate(profil, getHost(request), I18n.acceptLanguage(request));
                        JsonObject result = new JsonObject().put("profile", profil);
                        renderJson(request, result);
                }
            };
        });
    }

    @Get("/userStructures/:userId")
    @ApiDoc("Returns the profile of a user")
    public void getUserStructures(final HttpServerRequest request) {
        final String userId = request.params().get("userId");
        TicketServiceNeo4jImpl ticketServiceNeo4j = new TicketServiceNeo4jImpl();
        ticketServiceNeo4j.getUserStructures(userId, new Handler<Either<String, JsonArray>>() {
            @Override
            public void handle(Either<String, JsonArray> event) {
                if( event.isRight() && event.right().getValue().size() > 0){
                    JsonArray structures = event.right().getValue();
                    JsonObject result = new JsonObject().put("structures", structures);
                    renderJson(request, result);
                }
            };
        });
    }

    @Get("/isBugTrackerCommDirect")
    @ApiDoc("Return true if communication with bug tracker is direct. False otherwise")
    @SecuredAction(value = "support.escalation.activation.status", type = ActionType.AUTHENTICATED)
    public void isBugTrackerCommDirect(final HttpServerRequest request) {
        JsonObject result = new JsonObject().put("isBugTrackerCommDirect", bugTrackerCommDirect);
        renderJson(request, result);
    }


    @Post("/ticket/:id/escalate")
    @ApiDoc("Escalate ticket : the ticket is forwarded to an external bug tracker, a copy of the ticket is saved and will be regularly synchronized")
    @SecuredAction(value = "support.manager", type = ActionType.RESOURCE)
    @ResourceFilter(Admin.class)
    public void escalateTicket(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");
        escalateTicket(request, ticketId, false);
    }

    private void escalateTicket(final HttpServerRequest request, final String ticketId, final boolean updateEscalation) {

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    if(updateEscalation) {
                        ticketServiceSql.getTicketForEscalationService(ticketId,
                                getTicketForEscalationHandler(request, ticketId, user, false));
                    } else {
                        ticketServiceSql.getTicketWithEscalation(ticketId,
                                getTicketForEscalationHandler(request, ticketId, user, true));
                    }
                } else {
                    log.debug("User not found in session.");
                    if(!updateEscalation) {
                        unauthorized(request);
                    }
                }
            }
        });
    }

    private Handler<Either<String, JsonObject>> getTicketForEscalationHandler(final HttpServerRequest request,
                                                                              final String ticketId, final UserInfos user,
                                                                              final boolean doResponse) {
        return getTicketResponse -> {
            if (getTicketResponse.isRight()) {
                final JsonObject ticket = getTicketResponse.right().getValue();
                if (ticket == null || ticket.size() == 0) {
                    log.error("Ticket " + ticketId + " cannot be escalated : its status should be new or opened"
                            + ", and its escalation status should be not_done or in_progress");
                    if(doResponse) {
                        badRequest(request, "support.error.escalation.conflict");
                    }
                    return;
                }

                if(doResponse) {
                    ticketServiceSql.createTicketHisto(
                            ticket.getInteger("id").toString(),
                            I18n.getInstance().translate(
                                    "support.ticket.histo.escalate", getHost(request),
                                    I18n.acceptLanguage(request)) + user.getUsername(),
                            ticket.getInteger("status"), user.getUserId(), 4,
                            res -> {
                                if (res.isLeft()) {
                                    log.error("Error creation historization : " + res.left().getValue());
                                }
                            });
                }

                final JsonArray comments = new JsonArray(ticket.getString("comments"));
                final JsonArray attachments = new JsonArray(ticket.getString("attachments"));
                final ConcurrentMap<Long, String> attachmentMap = new ConcurrentHashMap<>();

                ticketServiceSql.getIssue(ticketId, getIssueResponse -> {
                    JsonObject issue = null;
                    if(getIssueResponse.isRight()) {
                        JsonArray issues = getIssueResponse.right().getValue();
                        if(issues != null && issues.size() > 0) {
                            if(issues.size() > 1 ) {
                                log.error("Support : more than one issue for ticket " + ticketId);
                            }
                            Object o = issues.getValue(0);
                            if(o instanceof JsonObject) {
                                issue = (JsonObject) o;
                            }
                        }
                    }
                    escalationService.escalateTicket(request, ticket, comments, attachments, attachmentMap, user,
                            issue, getEscalateTicketHandler(request, ticketId, user, attachmentMap, doResponse));
                });


            } else {
                log.error("Error when calling service getTicketWithEscalation. " + getTicketResponse.left().getValue());
                if(doResponse) {
                    renderError(request, new JsonObject().put("error",
                            "support.escalation.error.data.cannot.be.retrieved.from.database"));
                }
            }
        };
    }

    private Handler<Either<String, JsonObject>> getEscalateTicketHandler(final HttpServerRequest request,
                                                                         final String ticketId, final UserInfos user,
                                                                         final ConcurrentMap<Long, String> attachmentMap,
                                                                         final boolean doResponse) {

        return new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(final Either<String, JsonObject> escalationResponse) {
                if (escalationResponse.isRight()) {
                    final JsonObject issue = escalationResponse.right().getValue();
                    final Number issueId = escalationService.getBugTrackerType().extractIdFromIssue(issue);

                    // get the whole issue (i.e. with attachments' metadata and comments) to save it in database
                    escalationService.getIssue(issueId, getIssueHandler(request, issueId, ticketId, user, attachmentMap, doResponse));
                } else {
                    ticketServiceSql.endFailedEscalation(ticketId, user, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isLeft()) {
                                log.error("Error when updating escalation status to failed");
                            }
                        }
                    });
                    if(doResponse) {
                        renderError(request, new JsonObject().put("error", escalationResponse.left().getValue()));
                    }
                }
            }
        };
    }

    private Handler<Either<String, JsonObject>> getIssueHandler(final HttpServerRequest request, final Number issueId,
                                                                final String ticketId, final UserInfos user,
                                                                final ConcurrentMap<Long, String> attachmentMap,
                                                                final boolean doResponse) {
        return new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> getWholeIssueResponse) {
                if (getWholeIssueResponse.isRight()) {
                    final JsonObject wholeIssue = getWholeIssueResponse.right().getValue();
                    ticketServiceSql.endSuccessfulEscalation(ticketId, wholeIssue, issueId, attachmentMap, user,
                            new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                if (doResponse) {
                                    renderJson(request, wholeIssue);
                                }
                            } else {
                                log.error("Error when trying to update escalation status to successful and to save bug tracker issue");
                                if (doResponse) {
                                    renderError(request, new JsonObject().put("error", event.left().getValue()));
                                }
                            }
                        }

                    });
                } else {
                    // Can't get issue from bug tracker in asynchronous mode
                    if(escalationService.getBugTrackerType().getBugTrackerSyncType()
                            == BugTrackerSyncType.SYNC) {
                        log.error("Error when trying to get bug tracker issue");
                        // Update escalation status to successful
                        // (escalation succeeded, but data could not be saved in postgresql)
                        ticketServiceSql.endSuccessfulEscalation(ticketId, new JsonObject(), issueId, attachmentMap,
                                user, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isLeft()) {
                                    log.error("Error when trying to update escalation status to successful");
                                }
                            }
                        });
                        if(doResponse) {
                            renderError(request, new JsonObject().put("error", getWholeIssueResponse.left().getValue()));
                        }
                    } else {
                        // Bug tracker is async, can't get information of tracker issue
                        // Send dummy info to front
                        log.info("Bug tracker issue not fetched in asynchronous mode");
                       ticketServiceSql.endInProgressEscalation(ticketId, user, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> event) {
                                if (event.isLeft()) {
                                    log.error("Error when trying to update escalation status to in_progress");
                                }
                            }
                        });
                        String status = I18n.getInstance().translate(
                                "support.escalation.in.progress",
                                getHost(request), I18n.acceptLanguage(request));
                        if(doResponse) {
                            renderJson(request, new JsonObject().put("issue",
                                    new JsonObject().put("id", "#")
                                    .put("status", new JsonObject().put("name", status))));
                        }
                    }
                }
            }
        };
    }

    @Get("/ticket/:id/bugtrackerissue")
    @ApiDoc("Get bug tracker issue saved in postgresql")
    @SecuredAction(value = "support.escalation.activation.status", type = ActionType.AUTHENTICATED)
    public void getBugTrackerIssue(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");
        ticketServiceSql.getIssue(ticketId, arrayResponseHandler(request));
    }

    @Get("/gridfs/:id")
    @ApiDoc("Get bug tracker attachment saved in gridfs")
    @SecuredAction(value = "support.manager", type = ActionType.RESOURCE)
    @ResourceFilter(Admin.class)
    public void getBugTrackerAttachment(final HttpServerRequest request) {
        final String attachmentId = request.params().get("id");

        ticketServiceSql.getIssueAttachmentName(attachmentId, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if (event.isRight() && event.right().getValue() != null) {
                    String name = event.right().getValue().getString("name", null);
                    final String filename = (name != null && name.trim().length() > 0) ? name : "filename";

                    storage.sendFile(attachmentId, filename, request, false, null);
                } else {
                    renderError(request, new JsonObject().put("error",
                            "support.get.attachment.metadata.error.data.cannot.be.retrieved.from.database"));
                }
            }
        });
    }

    @Post("/issue/:id/comment")
    @ApiDoc("Add comment to bug tracker issue")
    @SecuredAction(value = "support.manager", type = ActionType.RESOURCE)
    @ResourceFilter(Admin.class)
    public void commentIssue(final HttpServerRequest request) {
        final String id = request.params().get("id");
        final Integer issueId = Integer.parseInt(id);

        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    RequestUtils.bodyToJson(request, pathPrefix + "commentIssue", new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject comment) {
                            sendIssueComment(user, comment, id, request);
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }

    private void refreshIssue(final Integer issueId, final HttpServerRequest request) {
        escalationService.getIssue(issueId, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> response) {
                if (response.isRight()) {
                    final JsonObject issue = response.right().getValue();
                    ticketServiceSql.updateIssue(issueId, issue.toString(), new Handler<Either<String, JsonObject>>() {

                        @Override
                        public void handle(Either<String, JsonObject> updateIssueResponse) {
                            if (updateIssueResponse.isRight()) {
                                renderJson(request, issue);
                            } else {
                                renderError(request, new JsonObject().put("error",
                                        "support.error.comment.added.to.escalated.ticket.but.synchronization.failed"));
                                log.error("Error when trying to update bug tracker issue: " + updateIssueResponse.toString());
                            }
                        }

                    });
                } else {
                    renderError(request, new JsonObject().put("error", response.left().getValue()));
                }
            }
        });
    }

    public void createTicketHistoMultiple(List<Integer> idList, String event, Integer newStatus, String userid) {
        for (Integer id : idList) {
            ticketServiceSql.createTicketHisto(id.toString(), event, newStatus, userid, 2, new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> res) {
                    if (res.isLeft()) {
                        log.error("Error creation historization : " + res.left().getValue());
                    }
                }
            });
        }
    }

    @Get("/events/:id")
    @ApiDoc("Get historization of a ticket")
    public void getHistorization(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    ticketServiceSql.listEvents(ticketId, arrayResponseHandler(request));
                } else {
                    log.debug("User not found in session.");
                    unauthorized(request);
                }
            }
        });

    }


    public void sendIssueComment(final UserInfos user, JsonObject comment, final String id, final HttpServerRequest request/*, Handler<Either<String, JsonObject>> handler*/){
        // add author name to comment
        StringBuilder content = new StringBuilder();
        final Integer issueId = Integer.parseInt(id);

        content.append(I18n.getInstance().translate("support.escalated.ticket.author", getHost(request), I18n.acceptLanguage(request)))
                .append(" : ")
                .append(user.getUsername())
                .append("\n")
                .append(comment.getString("content"));
        comment.put("content", content.toString());

        escalationService.commentIssue(issueId, comment, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if (event.isRight()) {
                    // get the whole issue (i.e. with attachments' metadata and comments) and save it in postgresql
                    refreshIssue(issueId, request);
                    /*
                    // Historization
                    ticketServiceSql.getTicketFromIssueId(id, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> res) {
                            if (res.isRight()) {
                                final JsonObject ticket = res.right().getValue();
                                ticketServiceSql.createTicketHisto(ticket.getInteger("id").toString(), I18n.getInstance().translate("support.ticket.histo.add.bug.tracker.comment", I18n.acceptLanguage(request)),
                                        ticket.getInteger("status"), user.getUserId(), 4, new Handler<Either<String, JsonObject>>() {
                                            @Override
                                            public void handle(Either<String, JsonObject> res) {
                                                if (res.isLeft()) {
                                                    log.error("Error creation historization : " + res.left().getValue());
                                                }
                                            }
                                        });
                            } else if (res.isLeft()) {
                                log.error("Error creation historization : " + res.left().getValue());
                            }
                        }
                    });*/
                } else {
                    renderError(request, new JsonObject().put("error", event.left().getValue()));
                }
            }
        });
    }
}
