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

import io.vertx.core.*;
import net.atos.entng.support.filters.AdminOfTicketsStructure;
import net.atos.entng.support.helpers.CSVHelper;
import net.atos.entng.support.helpers.RequestHelper;
import net.atos.entng.support.helpers.UserInfosHelper;
import net.atos.entng.support.model.I18nConfig;
import net.atos.entng.support.services.*;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.Support;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.enums.BugTrackerSyncType;
import net.atos.entng.support.enums.EscalationStatus;
import net.atos.entng.support.export.TicketsCSVExport;
import net.atos.entng.support.filters.Admin;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.helpers.PromiseHelper;
import net.atos.entng.support.services.impl.TicketServiceNeo4jImpl;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.http.filter.AdminFilter;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.http.RouteMatcher;

import static net.atos.entng.support.enums.TicketStatus.NEW;


public class TicketController extends ControllerHelper {
    static final String RESOURCE_NAME = "support";

    private static final String TICKET_CREATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_CREATED";
    private static final String TICKET_UPDATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_UPDATED";
    private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;

    private final TicketServiceSql ticketServiceSql;
    private final TicketService ticketService;
    private final UserService userService;
    private final EscalationService escalationService;
    private final Storage storage;
    private final EventHelper eventHelper;

    public TicketController(ServiceFactory serviceFactory, EscalationService es, Storage storage) {
        ticketServiceSql = serviceFactory.ticketServiceSql();
        this.ticketService = serviceFactory.ticketService();
        userService = serviceFactory.userService();
        escalationService = es;
        this.storage = storage;
        final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(Support.class.getSimpleName());
        this.eventHelper = new EventHelper(eventStore);
    }

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
    }

    @Post("/ticket")
    @ApiDoc("Create a ticket")
    @SecuredAction("support.ticket.create")
    public void createTicket(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, pathPrefix + "createTicket", ticket -> {
                    ticket.put("status", NEW.status());
                    ticket.put("event_count", 1);
                    JsonArray attachments = ticket.getJsonArray("attachments", null);
                    ticket.remove("attachments");
                    final Handler<Either<String, JsonObject>> handler = getCreateOrUpdateTicketHandler(request, user, ticket, null);
                    ticketServiceSql.createTicket(ticket, attachments, user, I18n.acceptLanguage(request), eventHelper.onCreateResource(request, RESOURCE_NAME, handler));
                });
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
            }
        });
    }

    private Handler<Either<String, JsonObject>> getCreateOrUpdateTicketHandler(final HttpServerRequest request,
                                                                               final UserInfos user, final JsonObject ticket,
                                                                               final String ticketId) {

        return event -> {
            if (event.isRight()) {
                final JsonObject response = event.right().getValue();
                if (response != null && response.size() > 0) {
                    if (ticketId == null) {
                        notifyTicketCreated(request, user, response, ticket);
                        response.put("owner_name", user.getUsername());
                        response.put("owner", user.getUserId());
                        ticketServiceSql.createTicketHisto(response.getInteger("id").toString(),
                                I18n.getInstance().translate("support.ticket.histo.creation", getHost(request),
                                        I18n.acceptLanguage(request)),
                                ticket.getInteger("status"), user.getUserId(), 1, res -> {
                                    if (res.isLeft()) {
                                        log.error("Error creation historization : " + res.left().getValue());
                                    }
                                });
                        renderJson(request, response, 200);
                    } else {
                        ticketServiceSql.updateEventCount(ticketId, res -> {
                            if (res.isLeft()) {
                                log.error("Error updating ticket (event_count) : " + res.left().getValue());
                            }
                        });
                        JsonArray attachments = ticket.getJsonArray("attachments");
                        boolean commentSentToBugtracker = false;
                        // we only historize if no comment has been added. If there is a comment, it will appear in the history
                        if (ticket.getString("newComment") == null || "".equals(ticket.getString("newComment"))) {
                            ticketServiceSql.createTicketHisto(ticketId, I18n.getInstance().translate("support.ticket.histo.modification",
                                            getHost(request), I18n.acceptLanguage(request)),
                                    ticket.getInteger("status"), user.getUserId(), 2, res -> {
                                        if (res.isLeft()) {
                                            log.error("Error creation historization : " + res.left().getValue());
                                        } else {
                                            sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                        }
                                    });
                        } else {
                            // if option activated, we can send the comment directly to the bug-tracker
                            if (bugTrackerCommDirect && (attachments == null || attachments.isEmpty())) {
                                sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                commentSentToBugtracker = true;
                            }
                        }
                        notifyTicketUpdated(request, ticketId, user, response);
                        if (escalationService != null && attachments != null && attachments.size() > 0) {
                            if (escalationService.getBugTrackerType().getBugTrackerSyncType()
                                    == BugTrackerSyncType.ASYNC && !commentSentToBugtracker) {
                                sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                renderJson(request, response, 200);
                            } else {
                                final boolean commentSent = commentSentToBugtracker;
                                escalationService.syncAttachments(ticketId, attachments, res -> {
                                    if (!commentSent) {
                                        sendTicketUpdateToIssue(request, ticketId, ticket, user);
                                    }
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
        };
    }

    /**
     * Send ticket updates to bugtracker
     *
     * @param request  Original request
     * @param ticketId Id of the ticket updated
     * @param ticket   Content of ticket updated
     * @param user     User updating ticket
     */
    private void sendTicketUpdateToIssue(final HttpServerRequest request, final String ticketId, final JsonObject ticket,
                                         final UserInfos user) {
        ticketServiceSql.getIssue(ticketId, res -> {
            if (res.isRight() && res.right().getValue().size() > 0) {
                JsonObject issue = res.right().getValue().getJsonObject(0);
                Long id = issue.getLong("id");
                JsonObject comment = new JsonObject();
                if (ticket != null && ticket.containsKey("newComment")) {
                    comment.put("content", ticket.getString("newComment"));
                }
                if (escalationService.getBugTrackerType().getBugTrackerSyncType()
                        == BugTrackerSyncType.SYNC) {
                    sendIssueComment(user, comment, id.toString(), request);
                } else {
                    escalateTicket(request, ticketId, true);
                }
            } else if (res.isLeft()) {
                log.error("No associated issue found");
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

        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, pathPrefix + "updateTicket", ticket -> {
                    // TODO : do not authorize description update if there is a comment
                    ticketServiceSql.updateTicket(ticketId, ticket, user,
                            getCreateOrUpdateTicketHandler(request, user, ticket, ticketId));
                    //notifyTicketUpdated(request, user, ticket);
                });
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Post("/ticketstatus/:newStatus")
    @ApiDoc("Update multiple ticket status")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(OwnerOrLocalAdmin.class)
    public void updateTicketStatus(final HttpServerRequest request) {
        final Integer newStatus = Integer.valueOf(request.params().get("newStatus"));
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                // getting list of tickets ids to update, based on ticketUpdate.json file format.
                RequestUtils.bodyToJson(request, pathPrefix + "ticketUpdate", data -> {
                    final List<Integer> ids = data.getJsonArray("ids").getList();
                    ticketServiceSql.updateTicketStatus(newStatus, ids, event -> {
                        if (event.isRight()) {
                            createTicketHistoMultiple(ids, I18n.getInstance().translate("support.ticket.histo.mass.modification",
                                    getHost(request), I18n.acceptLanguage(request)), newStatus, user.getUserId());
                            request.response().setStatusCode(200).end();
                            if(escalationService != null && escalationService.getBugTrackerType().getBugTrackerSyncType()
                                    == BugTrackerSyncType.ASYNC) {
                                updateIssuesStatus(request, ids);
                            }
                            //renderJson(request, wholeIssue);
                        } else {
                            log.error("Error when updating ticket statuses.");
                            renderError(request, new JsonObject().put("error", event.left().getValue()));
                        }
                    });
                });
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
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
                                                request != null ? getHost(request) : I18n.DEFAULT_DOMAIN,
                                                request != null ? I18n.acceptLanguage(request) : "fr",
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

    @Get("/ticket/:id")
    @ApiDoc("If current user is local admin, get all tickets. Otherwise, get my tickets")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getTicket(final HttpServerRequest request) {
        final Integer id = Integer.valueOf(request.params().get(Ticket.ID));
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                Map<String, UserInfos.Function> functions = user.getFunctions();
                if (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN)) {
                    Promise<JsonArray> ticketsPromise = Promise.promise();
                    ticketServiceSql.getTicket(user, id, PromiseHelper.handler(ticketsPromise));
                    ticketsPromise.future()
                            .onSuccess(result -> renderJson(request, result))
                            .onFailure(err -> renderError(request, new JsonObject()));
                } else {
                    ticketServiceSql.getMyTicket(user, id, arrayResponseHandler(request));
                }
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
            }
        });

    }


    @Get("/tickets")
    @ApiDoc("If current user is local admin, get all tickets. Otherwise, get my tickets")
    @SecuredAction("support.ticket.list")
    public void listTickets(final HttpServerRequest request) {
        MultiMap params = request.params();
        Integer page = Integer.valueOf(params.get(Ticket.PAGE));
        List<String> statuses = params.getAll(Ticket.STATUS);
        List<String> applicants = params.getAll(Ticket.APPLICANT);
        String schoolId = params.get(Ticket.SCHOOL);
        String sortBy = params.get(Ticket.SORT_BY);
        String order = params.get(Ticket.ORDER);
        Integer nbTicketsPerPage = config.getInteger("nbTicketsPerPage", 25);

        I18nConfig i18nConfig = new I18nConfig(request);

        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                Map<String, UserInfos.Function> functions = user.getFunctions();
                if (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN)) {
                    Promise<JsonArray> ticketsPromise = Promise.promise();
                    ticketServiceSql.listTickets(user, page, statuses, applicants, schoolId, sortBy, order, nbTicketsPerPage, PromiseHelper.handler(ticketsPromise));
                    // getting the profile for users
                    ticketsPromise.future()
                            .compose(tickets -> ticketService.getProfileFromTickets(tickets, i18nConfig))
                            .onSuccess(result -> renderJson(request, result))
                            .onFailure(err -> renderError(request, new JsonObject()));
                } else {
                    ticketServiceSql.listMyTickets(user, page, statuses, schoolId, sortBy, order, nbTicketsPerPage, arrayResponseHandler(request));
                }
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
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

    @Get("/editor")
    @ApiDoc("Return true if rich editor is activated. False otherwise")
    @SecuredAction(value = "support.escalation.activation.status", type = ActionType.AUTHENTICATED)
    public void isRichEditorActivated(final HttpServerRequest request) {
        JsonObject result = new JsonObject().put("isRichEditorActivated", Support.richEditorIsActivated());
        renderJson(request, result);
    }

    @BusAddress("support.update.bugtracker")
    @ApiDoc("Update ticket with information from bugtracker")
    public void updateTicketFromBugTracker(final Message<JsonObject> message) {
        escalationService.updateTicketFromBugTracker(message, event -> {
            if (event.isLeft()) {
                log.error("Support : error when updating ticket from bugtracker " + event.left().getValue());
                message.reply(new JsonObject().put("status", "KO").put("message", event.left().getValue()));
            } else {
                JsonObject response = event.right().getValue();
                if (response.containsKey("user") && response.containsKey("ticket")) {
                    JsonObject userJson = response.getJsonObject("user");
                    UserInfos user = new UserInfos();
                    user.setUserId(userJson.getString("userid"));
                    user.setUsername(userJson.getString("username"));
                    user.setType(userJson.getString("type"));

                    JsonObject ticket = response.getJsonObject("ticket");
                    ticket.put("owner", ticket.getString("owner_id"));
                    if (message.body().getJsonObject(Ticket.ISSUE, new JsonObject()).getString(Ticket.ID_JIRA) != null) {
                        ticket.put(Ticket.ID, message.body().getJsonObject(Ticket.ISSUE, new JsonObject()).getString(Ticket.ID_JIRA));
                    }
                    String ticketId = ticket.getValue(Ticket.ID).toString();
                    notifyTicketUpdated(null, ticketId, user, ticket);
                }
                message.reply(new JsonObject().put("status", "OK"));
            }
        });
    }

    @Get("/profile/:userId")
    @ApiDoc("Returns the profile of a user")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getProfileString(final HttpServerRequest request) {
        final String userId = request.params().get("userId");
        TicketServiceNeo4jImpl ticketServiceNeo4j = new TicketServiceNeo4jImpl();
        JsonArray jsonUserId = new JsonArray();
        jsonUserId.add(userId);
        ticketServiceNeo4j.getUsersFromList(jsonUserId, event -> {
            if (event.isRight() && event.right().getValue().size() > 0) {
                JsonObject jUser = event.right().getValue().getJsonObject(0);
                // traduction profil
                String profil = jUser.getJsonArray("n.profiles").getString(0);
                profil = I18n.getInstance().translate(profil, getHost(request), I18n.acceptLanguage(request));
                JsonObject result = new JsonObject().put("profile", profil);
                renderJson(request, result);
            }
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
    @SecuredAction(value = "support.ticket.escalate")
    public void escalateTicket(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");
        escalateTicket(request, ticketId, false);
    }

    private void escalateTicket(final HttpServerRequest request, final String ticketId, final boolean updateEscalation) {

        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                if (updateEscalation) {
                    ticketServiceSql.getTicketForEscalationService(ticketId,
                            getTicketForEscalationHandler(request, ticketId, user, false));
                } else {
                    ticketServiceSql.getTicketWithEscalation(ticketId,
                            getTicketForEscalationHandler(request, ticketId, user, true));
                }
            } else {
                log.debug("User not found in session.");
                if (!updateEscalation) {
                    unauthorized(request);
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
                    if (doResponse) {
                        badRequest(request, "support.error.escalation.conflict");
                    }
                    return;
                }

                if (doResponse) {
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
                    if (getIssueResponse.isRight()) {
                        JsonArray issues = getIssueResponse.right().getValue();
                        if (issues != null && issues.size() > 0) {
                            if (issues.size() > 1) {
                                log.error("Support : more than one issue for ticket " + ticketId);
                            }
                            Object o = issues.getValue(0);
                            if (o instanceof JsonObject) {
                                issue = (JsonObject) o;
                            }
                        }
                    }
                    escalationService.escalateTicket(request, ticket, comments, attachments, attachmentMap, user,
                            issue, getEscalateTicketHandler(request, ticketId, user, attachmentMap, doResponse));
                });


            } else {
                log.error("Error when calling service getTicketWithEscalation. " + getTicketResponse.left().getValue());
                if (doResponse) {
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

        return escalationResponse -> {
            if (escalationResponse.isRight()) {
                final JsonObject issue = escalationResponse.right().getValue();
                final Number issueId = escalationService.getBugTrackerType().getIssueId(issue);

                if (issueId != null) {
                    escalationService.getIssue(issueId, getIssueHandler(request, issueId, ticketId, user, attachmentMap, doResponse, issue));
                } else {
                    badRequest(request);
                }
                // get the whole issue (i.e. with attachments' metadata and comments) to save it in database
            } else {
                ticketServiceSql.endFailedEscalation(ticketId, user, event -> {
                    if (event.isLeft()) {
                        log.error("Error when updating escalation status to failed");
                    }
                });
                if (doResponse) {
                    if(escalationResponse.left().getValue() == Ticket.PAYLOADTOOLARGE){
                        renderError(request, new JsonObject().put(Ticket.ERROR, escalationResponse.left().getValue()),413,Ticket.PAYLOADTOOLARGE);
                    }else {
                        renderError(request, new JsonObject().put(Ticket.ERROR, escalationResponse.left().getValue()));
                    }
                }
            }
        };
    }

    private Handler<Either<String, JsonObject>> getIssueHandler(final HttpServerRequest request, final Number issueId,
                                                                final String ticketId, final UserInfos user,
                                                                final ConcurrentMap<Long, String> attachmentMap,
                                                                final boolean doResponse, JsonObject issue) {
        return getWholeIssueResponse -> {
            if (getWholeIssueResponse.isRight()) {
                final JsonObject wholeIssue = getWholeIssueResponse.right().getValue();
                ticketServiceSql.endSuccessfulEscalation(ticketId, wholeIssue, issueId, attachmentMap, user,
                        event -> {
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
                        });
            } else {
                // Can't get issue from bug tracker in asynchronous mode
                if (escalationService.getBugTrackerType().getBugTrackerSyncType()
                        == BugTrackerSyncType.SYNC) {
                    log.error("Error when trying to get bug tracker issue");
                    // Update escalation status to successful
                    // (escalation succeeded, but data could not be saved in postgresql)
                    ticketServiceSql.endSuccessfulEscalation(ticketId, new JsonObject(), issueId, attachmentMap,
                            user, event -> {
                                if (event.isLeft()) {
                                    log.error("Error when trying to update escalation status to successful");
                                }
                            });
                    if (doResponse) {
                        renderError(request, new JsonObject().put("error", getWholeIssueResponse.left().getValue()));
                    }
                } else {
                    // Bug tracker is async, can't get information of tracker issue
                    // Send dummy info to front
                    log.info("Bug tracker issue not fetched in asynchronous mode");
                    ticketServiceSql.endInProgressEscalationAsync(ticketId, user, issue, event -> {
                        if (event.isLeft()) {
                            log.error("Error when trying to update escalation status to in_progress");
                        }
                    });
                    String status = I18n.getInstance().translate(

                            "support.ticket.escalation.successful",
                            getHost(request), I18n.acceptLanguage(request));
                    if (doResponse) {
                        renderJson(request, new JsonObject().put("issue",
                                new JsonObject().put(Ticket.ID, issue.getString(Ticket.ID_JIRA_FIELD))
                                        .put(Ticket.STATUS, new JsonObject().put(Ticket.NAME, EscalationStatus.SUCCESSFUL))));
                    }
                }
            }
        };
    }

    @Get("/ticket/:id/bugtrackerissue")
    @ApiDoc("Get bug tracker issue saved in postgresql")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(OwnerOrLocalAdmin.class)
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

        ticketServiceSql.getIssueAttachmentName(attachmentId, event -> {
            if (event.isRight() && event.right().getValue() != null) {
                String name = event.right().getValue().getString("name", null);
                final String filename = (name != null && name.trim().length() > 0) ? name : "filename";

                storage.sendFile(attachmentId, filename, request, false, null);
            } else {
                renderError(request, new JsonObject().put("error",
                        "support.get.attachment.metadata.error.data.cannot.be.retrieved.from.database"));
            }
        });
    }

    @Post("/issue/:id/comment")
    @ApiDoc("Add comment to bug tracker issue")
    @SecuredAction(value = "support.manager", type = ActionType.RESOURCE)
    @ResourceFilter(Admin.class)
    public void commentIssue(final HttpServerRequest request) {
        final String id = request.params().get("id");

        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                RequestUtils.bodyToJson(request, pathPrefix + "commentIssue", comment -> sendIssueComment(user, comment, id, request));
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
            }
        });

    }

    private void refreshIssue(final Integer issueId, final HttpServerRequest request) {
        escalationService.getIssue(issueId, response -> {
            if (response.isRight()) {
                final JsonObject issue = response.right().getValue();
                ticketServiceSql.updateIssue(issueId, issue.toString(), updateIssueResponse -> {
                    if (updateIssueResponse.isRight()) {
                        renderJson(request, issue);
                    } else {
                        renderError(request, new JsonObject().put("error",
                                "support.error.comment.added.to.escalated.ticket.but.synchronization.failed"));
                        log.error("Error when trying to update bug tracker issue: " + updateIssueResponse.toString());
                    }
                });
            } else {
                renderError(request, new JsonObject().put("error", response.left().getValue()));
            }
        });
    }

    public void createTicketHistoMultiple(List<Integer> idList, String event, Integer newStatus, String userid) {
        for (Integer id : idList) {
            ticketServiceSql.createTicketHisto(id.toString(), event, newStatus, userid, 2, res -> {
                if (res.isLeft()) {
                    log.error("Error creation historization : " + res.left().getValue());
                }
            });
        }
    }


    @Get("/events/:id")
    @ApiDoc("Get historization of a ticket")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(OwnerOrLocalAdmin.class)
    public void getHistorization(final HttpServerRequest request) {
        final String ticketId = request.params().get("id");
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                ticketServiceSql.getlistEvents(ticketId)
                        .onSuccess(result -> renderJson(request, RequestHelper.addAllValue(new JsonObject(), result).getJsonArray(Ticket.ALL)))
                        .onFailure(err -> renderError(request, new JsonObject().put(Ticket.MESSAGE, err.getMessage())));
            } else {
                log.debug("User not found in session");
                unauthorized(request);
            }
        });
    }

    public void sendIssueComment(final UserInfos user, JsonObject comment, final String id, final HttpServerRequest request/*, Handler<Either<String, JsonObject>> handler*/) {
        // add author name to comment
        StringBuilder content = new StringBuilder();
        final Integer issueId = Integer.parseInt(id);

        content.append(I18n.getInstance().translate("support.escalated.ticket.author", getHost(request), I18n.acceptLanguage(request)))
                .append(" : ")
                .append(user.getUsername())
                .append("\n")
                .append(comment.getString("content"));
        comment.put("content", content.toString());

        escalationService.commentIssue(issueId, comment, event -> {
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
        });
    }

    @Get("/tickets/export")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminOfTicketsStructure.class)
    @ApiDoc("Export tickets")
    public void exportTickets(HttpServerRequest request) {
        List<String> ids = request.params().getAll(Ticket.ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                I18nConfig i18nConfig = new I18nConfig(request);
                ticketServiceSql.getTicketsFromListId(ids)
                        .compose(tickets -> ticketService.getProfileFromTickets(tickets,i18nConfig))
                        .compose(ticketService::getSchoolFromTickets)
                        .onSuccess(result -> {
                            TicketsCSVExport pce = new TicketsCSVExport(result,i18nConfig);
                            CSVHelper.sendCSV(request, pce.filename(), pce.generate());
                        })
                        .onFailure(err -> renderError(request, new JsonObject()));
            } else {
                log.debug("User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("/check/user/:id/workflow/:workflow/structure/:structureId/auto/open")
    @ApiDoc("Returns if the user and the structure have a certain workflow")
    @SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getWorkflow(HttpServerRequest request) {
        final String userId = request.params().get(Ticket.ID);
        final String workflowWanted = request.params().get(Ticket.WORKFLOW);
        final String structureId = request.params().get(Ticket.STRUCTURE_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                ticketService.getSchoolWorkflowRightFromUserId(userId, workflowWanted, structureId)
                        .onSuccess(result -> renderJson(request, result))
                        .onFailure(err -> renderError(request, new JsonObject().put(Ticket.MESSAGE, err.getMessage())));
            } else {
                log.debug("[Support@%s::getWorkflow] User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("structures/:structureId/tickets/count")
    @ApiDoc("Return the number of tickets of a structure")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void countTickets(HttpServerRequest request) {
        final String structureId = request.params().get(Ticket.STRUCTURE_ID);
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                Promise<JsonObject> promise = Promise.promise();
                promise.future()
                        .compose(result -> ticketServiceSql.countTickets(user, result))
                        .onSuccess(result -> renderJson(request, result))
                        .onFailure(err -> renderError(request, new JsonObject().put(Ticket.MESSAGE, err.getMessage())));

                if (!Objects.equals(structureId, Ticket.ASTERISK))
                    ticketService.listStructureChildren(structureId).onComplete(promise);
                else promise.complete(new JsonObject());
            } else {
                log.debug("[Support@%s::countTickets] User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("/tickets/export/direct/:structureId")
    @ApiDoc("Generate export CSV and directly download it")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void directExport(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                final String structureId = request.params().get(Ticket.STRUCTURE_ID);
                I18nConfig i18nConfig = new I18nConfig(request);
                Promise<JsonArray> promise = Promise.promise();
                promise.future()
                        .compose(tickets -> ticketService.getProfileFromTickets(tickets, i18nConfig))
                        .compose(ticketService::getSchoolFromTickets)
                        .onSuccess(result -> {
                            TicketsCSVExport pce = new TicketsCSVExport(result, i18nConfig);
                            CSVHelper.sendCSV(request, pce.filename(), pce.generate());
                        })
                        .onFailure(err -> renderError(request, new JsonObject()));

                if (!Objects.equals(structureId, Ticket.ASTERISK)) ticketService.listStructureChildren(structureId)
                        .compose(ticketServiceSql::getTicketsFromArrayOfStructureId)
                        .onComplete(promise);
                else ticketServiceSql.getAllTicketsOfUser(user).onComplete(promise);
            } else {
                log.debug("[Support@%s::directExport] User not found in session.");
                unauthorized(request);
            }
        });
    }

    @Get("/tickets/export/worker/:structureId")
    @ApiDoc("Generate export CSV and download it in workspace")
    @SecuredAction(value = "", type = ActionType.RESOURCE)
    @ResourceFilter(AdminFilter.class)
    public void workerExport(HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, user -> {
            if (user != null) {
                final String structureId = request.params().get(Ticket.STRUCTURE_ID);
                I18nConfig i18nConfig = new I18nConfig(request);

                JsonObject params = new JsonObject()
                        .put(Ticket.STRUCTURE_ID, structureId)
                        .put(Ticket.USER, UserInfosHelper.toJSON(user))
                        .put(Ticket.LOCALE, i18nConfig.getLang())
                        .put(Ticket.DOMAIN, i18nConfig.getDomain());
                Support.launchExportTicketsWorker(eb, params)
                        .onSuccess(result -> renderJson(request, result))
                        .onFailure(err -> log.error(String.format("[Support@%s::workerExport] %s",
                                this.getClass().getSimpleName(), err)));
            } else {
                log.debug("[Support@%s::workerExport] User not found in session.");
                unauthorized(request);
            }
        });
    }
}
