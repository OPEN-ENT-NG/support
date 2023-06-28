package net.atos.entng.support.services.impl;

import fr.wseduc.webutils.Either;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.enums.TicketStatusJira;
import net.atos.entng.support.helpers.EscalationPivotHelper;
import net.atos.entng.support.helpers.impl.EscalationPivotHelperImpl;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;
import org.entcore.common.bus.ErrorMessage;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

/**
 * Implementation of EscalationService for Pivot module (asynchronous)
 * Created by colenot on 14/12/2017.
 */
public class EscalationServicePivotImpl implements EscalationService
{
    private final Logger log = LoggerFactory.getLogger(EscalationServicePivotImpl.class);
    private final TimelineHelper notification;
    private final TicketServiceSql ticketServiceSql;
    private final EventBus eb;
    private final UserService userService;
    private final WorkspaceHelper wksHelper;
    private final Storage storage;
    private final JsonObject STATUS ;
    private static final String TRACKER_ADDRESS = "supportpivot.demande";
    /**
     * Declaration of JSON fields for pivot format
     */
    private final String IDIWS_FIELD;
    private final static String IDENT_FIELD = "id_ent";
    private final static String IDJIRA_FIELD = "id_jira";
    private final static String COLLECTIVITY_FIELD = "collectivite";
    private final static String ACADEMY_FIELD = "academie";
    private final static String CREATOR_FIELD = "demandeur";
    private final static String TICKETTYPE_FIELD = "type_demande";
    private final static String TITLE_FIELD = "titre";
    private final static String DESCRIPTION_FIELD = "description";
    private final static String PRIORITY_FIELD = "priorite";
    private final static String MODULES_FIELD = "modules";
    private final static String COMM_FIELD = "commentaires";
    private final static String CLIENT_RESPONSE = "reponse_client";
    private final static String ATTACHMENT_FIELD = "pj";
    private final static String ATTACHMENT_NAME_FIELD = "nom";
    private final static String ATTACHMENT_CONTENT_FIELD = "contenu";
    private final String STATUSIWS_FIELD;
    private final static String STATUSENT_FIELD = "statut_ent";
    private final static String STATUSJIRA_FIELD = "statut_jira";
    private final static String DATE_CREA_FIELD = "date_creation";
    private final String DATE_RESOIWS_FIELD;
    private final static String DATE_RESOENT_FIELD = "date_resolution_ent";
    private final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";
    private final static String ATTRIBUTION_FIELD = "attribution";
    private final static String ATTRIBUTION_ENT_VALUE = "RECTORAT";

    private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;

    private final UserInfos userIws;

    private final EscalationPivotHelper helper;

    public EscalationServicePivotImpl(final Vertx vertx, final JsonObject config,
                                        final TicketServiceSql ts, final UserService us, Storage storage) {

        eb = getEventBus(vertx);
        wksHelper = new WorkspaceHelper(eb, storage);
        ticketServiceSql = ts;
        userService = us;
        userIws = new UserInfos();
        userIws.setUserId(config.getString("user-iws-id"));
        userIws.setUsername(config.getString("user-iws-name"));

        String externalServiceName = config.getString("external-service-name", "iws");
        IDIWS_FIELD = "id_" + externalServiceName;
        STATUSIWS_FIELD = "statut_" + externalServiceName;
        DATE_RESOIWS_FIELD = "date_resolution_" + externalServiceName;

        STATUS = config.getJsonObject("status_iws");
        userIws.setType("");
        helper = new EscalationPivotHelperImpl();
        notification = new TimelineHelper(vertx, eb, config);
        this.storage = storage;
    }

    @Override
    public BugTracker getBugTrackerType() {
        return BugTracker.PIVOT;
    }

    /**
     * Escalate a ticket to Pivot service
     * @param request unused
     * @param ticket Json Object containing local ticket info
     * @param comments Json Array with all comments, comments info are serialized
     * @param attachmentsIds Ids of attachments in workspace
     * @param attachmentMap unused in async mode
     * @param user User infos
     */
    @Override
    public void escalateTicket(final HttpServerRequest request, final JsonObject ticket,
                               final JsonArray comments, final JsonArray attachmentsIds,
                               final ConcurrentMap<Long, String> attachmentMap, final UserInfos user,
                               final JsonObject issue,
                               final Handler<Either<String, JsonObject>> handler) {

        doTicketEscalation(ticket, comments, attachmentsIds, issue, user, handler);
    }

    /**
     * Escalate ticket
     * Step 1 : Serialize comments
     * Step 2 : Transform attachment in base64
     * Step 3 : Create issue with attachments and comments
     * @param ticket Json Object containing local ticket info
     * @param comments Json Array with all comments, comments info are serialized
     * @param attachmentsIds Ids of attachments in workspaces
     */
    private void doTicketEscalation(final JsonObject ticket, final JsonArray comments,
                                    final JsonArray attachmentsIds, final JsonObject issue,
                                    final UserInfos escalationUser,
                                    final Handler<Either<String, JsonObject>> handler) {

        final JsonArray finalComments = helper.serializeComments(comments);
        final JsonArray attachments = new JsonArray();
        if( null !=  attachmentsIds  && 0 < attachmentsIds.size() ) {
            final AtomicInteger successfulDocs = new AtomicInteger(0);

            for (Object o : attachmentsIds) {
                if (!(o instanceof String)) continue;
                final String attachmentId = (String) o;

                wksHelper.readDocument(attachmentId, file -> {
                    try {
                        if (file.getDocument().getJsonObject(Ticket.METADATE).getInteger(Ticket.SIZE) < 10000000) {
                            final String filename = file.getDocument().getString(Ticket.NAME);
                            final String encodedData = Base64.getMimeEncoder().encodeToString( file.getData().getBytes() );

                            JsonObject attachment = new JsonObject().put(ATTACHMENT_NAME_FIELD, filename)
                                    .put(ATTACHMENT_CONTENT_FIELD, encodedData);
                            attachments.add(attachment);

                            // Create issue only if all attachments have been retrieved successfully
                            if (successfulDocs.incrementAndGet() == attachmentsIds.size()) {
                                EscalationServicePivotImpl.this.getDataAndCreateIssue( ticket, attachments,
                                        finalComments, issue, escalationUser, handler);
                            }
                        } else {
                            handler.handle(new Either.Left<>(Ticket.PAYLOADTOOLARGE));
                        }

                    } catch (Exception e) {
                        log.error("Error when processing response from readDocument", e);
                    }
                });
            }
        } else { // No attachments
            this.getDataAndCreateIssue( ticket, attachments,  finalComments, issue,escalationUser, handler);
        }
    }

    /**
     * Get an issue from bugtracker.
     * Not possible in async mode, placeholder behavior.
     */
    @Override
    public void getIssue(Number issueId, Handler<Either<String, JsonObject>> handler) {
        handler.handle(new Either.Left<>("OK"));
    }

    /**
     * Escalate ticket with all comments, including new
     */
    @Override
    public void commentIssue(Number issueId, JsonObject comment, Handler<Either<String, JsonObject>> handler) {
        ticketServiceSql.getTicketForEscalationService( issueId.toString(), getTicketForEscalationHandler(handler) );
    }

    /**
     * Handler after get ticket from base, and do escalation
     * @param handler Final handler
     * @return handler to process SQL response
     */
    private Handler<Either<String, JsonObject>> getTicketForEscalationHandler (
            final Handler<Either<String,JsonObject>> handler) {
        return getTicketResponse -> {
            if (getTicketResponse.isLeft()) {
                handler.handle(getTicketResponse);
            } else {
                JsonObject ticket = getTicketResponse.right().getValue();
                if (ticket == null || ticket.size() == 0) {
                    handler.handle(new Either.Left<>("Ticket not escalated"));
                } else {
                    JsonArray comments = new JsonArray(ticket.getString("comments"));
                    JsonArray attachments = new JsonArray(ticket.getString("attachments"));
                    doTicketEscalation(ticket, comments, attachments, null, null, handler);
                }
            }
        };
    }

    /**
     * Update a ticket from bugtracker infos
     */
    @Override
    public void updateTicketFromBugTracker(Message<JsonObject> message, final Handler<Either<String, JsonObject>> handler) {

        final JsonObject issue;

        try {
            issue = message.body().getJsonObject("issue");
            if( !issue.containsKey(IDENT_FIELD) || issue.getString(IDENT_FIELD).isEmpty() ) {
                handler.handle(new Either.Left<>("Support : No id_ent field, can't update ticket from bugtracker"));
                return;
            }
        } catch (NullPointerException e) {
            handler.handle(new Either.Left<>("Support : No issue, can't update ticket from bugtracker"));
            return;
        }

        final String idEnt = issue.getString( IDENT_FIELD );
        ticketServiceSql.getTicketForEscalationService(idEnt, getTicketResponse -> {
            if( getTicketResponse.isLeft() )  {
                log.error("Error when calling service getTicketWithEscalation : " + getTicketResponse.left().getValue());
                handler.handle(new Either.Left<>("Error when calling service getTicketWithEscalation."));
            } else {
                updateTicketWithBugtrackerData(idEnt, getTicketResponse.right().getValue(), issue, handler);
            }
        });
    }
    private String shortenSubject(String subject) {
        if (subject.length() > SUBJECT_LENGTH_IN_NOTIFICATION) {
            return subject.substring(0, SUBJECT_LENGTH_IN_NOTIFICATION)
                    .concat(" [...]");
        }
        return subject;
    }
    private String getNotificationName( int status ){

        if ( status == TicketStatus.RESOLVED.status()) {
            return "bugtracker-issue-resolved";
        } else if (status == TicketStatus.CLOSED.status()) {
            return "bugtracker-issue-closed";
        } else {
            return "bugtracker-issue-updated";
        }

}
    private void notifyUsersOfChangeStatus ( final JsonObject issue, String ticket_id_iws,
                                         final int issueStatus, JsonObject user){
        String ticketId = issue.getLong("id").toString();
        log.debug("Ticket Updated: " + ticketId);
        log.debug("New status_id:" + issueStatus);

        String ticketOwner = issue.getString("owner_id");
        String structure = issue.getString("school_id");
        final Set<String> recipientSet = new HashSet<>();
        String notificationName = getNotificationName( issueStatus );

        recipientSet.add(ticketOwner);
        userService.getLocalAdministrators(structure, admins -> {
                    if (admins != null) {

                        for (Object o : admins) {
                            if (!(o instanceof JsonObject)) continue;
                            String id1 = ((JsonObject) o).getString("id");
                            recipientSet.add(id1);
                        }
                    }
            List<String> recipients = new ArrayList<>(recipientSet);
            if (!recipients.isEmpty()) {
                JsonObject params = new JsonObject();
                params.put("issueId",ticket_id_iws)
                        .put("username", user.getString("username"))
                        .put("ticketId", ticketId);
                params.put("ticketUri", "/support#/ticket/" + ticketId);
                params.put("resourceUri", params.getString("ticketUri"))
                        .put("ticketsubject", shortenSubject(issue.getString("description") ));
                params.put("resourceUri", params.getString("ticketUri"));

                notification.notifyTimeline(null, "support." + notificationName, null, recipients, params);
            }

        } );

    }

    /**
     * Update ticket with bugtracker info
     * If status of IWS ticket is "resolved" or "closed", ent status is updated
     * @param ticketId ticket to update
     * @param ticket bugtracker issue
     */
    private void updateTicketWithBugtrackerData(final String ticketId, final JsonObject ticket, final JsonObject issue,
                                                final Handler<Either<String,JsonObject>> handler) {

        //Check ENT ticket presence
        if(ticket == null || ticket.size() == 0) {
            handler.handle(new Either.Left<>("201: no ENT sticket found" + ticketId));
            return;
        }

        //Check forbidden changing id_iws
        final String entering_id_iws = issue.getString(IDIWS_FIELD);
        String enttiket_id_iws = ticket.getString(IDIWS_FIELD);
        if(enttiket_id_iws != null && !enttiket_id_iws.equals(entering_id_iws)){
            handler.handle(new Either.Left<>("202;ENT ticket " + ticketId + " is already link with IWS ticket" + entering_id_iws));
            return;
        }


        Long ticketStatus = ticket.getLong("status");

        final int issueStatus = helper.getStatusCorrespondence(STATUS, issue.getString(STATUSIWS_FIELD));
        final boolean updateStatus = ( ticketStatus.intValue() != issueStatus
                && (issueStatus == TicketStatus.RESOLVED.status()
                    || issueStatus == TicketStatus.CLOSED.status()) );

        JsonArray ticketComments = new JsonArray(ticket.getString("comments", "[]"));
        JsonArray issueComments = issue.getJsonArray(COMM_FIELD, new JsonArray());

        if(issue.containsKey(CLIENT_RESPONSE)) {
            String clientResponse = issue.getString(CLIENT_RESPONSE);
            if(!clientResponse.isEmpty()) {
                String crComment = "REPONSECLIENT | " + clientResponse;
                issueComments.add(crComment);
            }
        }
        if (Ticket.SOURCE_CGI.equals(issue.getString(Ticket.SOURCE))) {
            ticket.put(IDJIRA_FIELD, issue.getString(IDJIRA_FIELD));
            ticket.put(Ticket.ID, issue.getString(IDJIRA_FIELD));
            ticket.put(STATUSJIRA_FIELD, issue.getString(STATUSJIRA_FIELD));
        }


        JsonArray commentsToAdd = helper.compareComments(ticketComments, issueComments);
        JsonObject data = new JsonObject();
        data.put("newComments", commentsToAdd);
        if(updateStatus) {
            data.put("status", issueStatus);
        }

        JsonArray ticketAttachments = new JsonArray(ticket.getString("attachmentsnames", "[]"));
        List<String> attsName = new LinkedList<>();
        for(Object o : ticketAttachments) {
            if(!(o instanceof String)) continue;
            attsName.add((String)o);
        }

        Handler<Either<String,JsonObject>> preparedHandler = response -> {
            if(response.isLeft()) {
                handler.handle(response);
            } else {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.put("ticket", ticket);
                JsonObject jsonUser = new JsonObject();
                jsonUser.put("userid", userIws.getUserId())
                        .put("username", userIws.getUsername())
                        .put("type", userIws.getType());
                jsonResponse.put("user", jsonUser);
                final Either<String,JsonObject> finalResponse = new Either.Right<>(jsonResponse);
                if(updateStatus) {
                    notifyUsersOfChangeStatus(ticket,entering_id_iws,issueStatus, jsonUser );
                    ticketServiceSql.updateEventCount(ticketId, result ->{
                        if(result.isRight()){
                            handler.handle(finalResponse);
                        }else{
                            handler.handle(new Either.Left<>("couldn't update ticket: " + ticketId));
                        }
                    });
                }else {
                    handler.handle(finalResponse);
                }

            }
        };
        addAttachmentAndUpdate(ticketId, data, issue, issueStatus, updateStatus, attsName, preparedHandler);
    }

    private void addAttachmentAndUpdate(final String ticketId, final JsonObject data, final JsonObject issue,
                                        final int issueStatus, final boolean updateStatus,
                                        final List<String> attsName,
                                        final Handler<Either<String,JsonObject>> handler ) {
        if(issue.containsKey(ATTACHMENT_FIELD) && issue.getJsonArray(ATTACHMENT_FIELD).size() > 0) {
            final JsonArray dataAtts = new JsonArray();
            data.put("attachments", dataAtts);
            final AtomicInteger uploadedDocs = new AtomicInteger(issue.getJsonArray(ATTACHMENT_FIELD).size());
            for(Object o : issue.getJsonArray(ATTACHMENT_FIELD)) {
                if(!(o instanceof JsonObject)) {
                    uploadedDocs.decrementAndGet();
                    continue;
                }
                JsonObject att = (JsonObject)o;
                if(!att.containsKey(ATTACHMENT_NAME_FIELD)
                        || !att.containsKey(ATTACHMENT_CONTENT_FIELD)) {
                    uploadedDocs.decrementAndGet();
                    continue;
                }
                final String pjName = att.getString(ATTACHMENT_NAME_FIELD);
                if(attsName.contains(pjName)){
                    uploadedDocs.decrementAndGet();
                    continue;
                }
                Buffer pjContent;
                try {
                    pjContent = Buffer.buffer(Base64.getMimeDecoder().decode(att.getString(ATTACHMENT_CONTENT_FIELD)));
                } catch (IllegalArgumentException e){
                    log.error("Bad attachment file " + pjName);
                    break;
                }

                storage.writeBuffer(pjContent, "", pjName, attachmentMetaData -> {
                    if("error".equals(attachmentMetaData.getString("status"))) {
                        log.error("Error when saving attachment " + pjName);
                    } else {
                        wksHelper.addDocument(attachmentMetaData, userIws, pjName, "media-library",
                                true, new JsonArray (), handlerToAsyncHandler(wksResponse -> {

                                    dataAtts.add(new JsonObject()
                                            .put("name", pjName)
                                            .put("id",wksResponse.body().getString("_id"))
                                            .put("size",
                                                    attachmentMetaData.getJsonObject("metadata").getInteger("size",0)));
                                    data.put("attachments", dataAtts);
                                    if(uploadedDocs.decrementAndGet() <= 0) {
                                        ticketServiceSql.updateTicket(ticketId, data, userIws,
                                                getAfterTicketUpdateHandler(ticketId, issueStatus,
                                                        issue, updateStatus, handler) );
                                    }
                                }));
                    }
                });
            }
            if(uploadedDocs.get() <= 0) {
                ticketServiceSql.updateTicket(ticketId, data, userIws,
                        getAfterTicketUpdateHandler(ticketId, issueStatus, issue, updateStatus, handler) );
            }
        } else {
            ticketServiceSql.updateTicket(ticketId, data, userIws,
                    getAfterTicketUpdateHandler(ticketId, issueStatus, issue, updateStatus, handler) );
        }
        if (Ticket.SOURCE_CGI.equals(issue.getString(Ticket.SOURCE))) {
            int statusForHisto = TicketStatusJira.getTicketStatusJira(issue.getString(STATUSJIRA_FIELD, "")).getStatus();
            ticketServiceSql.createTicketHisto(ticketId, Ticket.JIRA_COMMENT_HEADER,
                    statusForHisto, Ticket.ID, 2, res -> {
                        if (res.isLeft()) {
                            String message = String.format("[Support@%s::addAttachmentAndUpdate] Support : Error creation historization : %s",
                                    this.getClass().getSimpleName(), res.left().getValue());
                            log.error(message);
                        }
                    });
        }
    }

    /**
     * Used after updating a ticket with bugtracker info
     * Save ticket histo and issue in database
     * @param ticketId Id of the ticket updated
     * @param issueStatus Status of the issue
     * @param issue JsonObject of the issue
     * @param handler final handler to answer to
     * @return handler to send back to calling function
     */
    private Handler<Either<String, JsonObject>> getAfterTicketUpdateHandler(final String ticketId,
                                                                            final int issueStatus,
                                                                            final JsonObject issue,
                                                                            final boolean statusUpdated,
                                                                            final Handler<Either<String,JsonObject>> handler) {
        return updateTicketResponse -> {
            if(updateTicketResponse.isLeft()) {
                handler.handle(new Either.Left<>("Error when updating ticket"));
            } else {
                if (statusUpdated) {
                    ticketServiceSql.createTicketHisto(
                            ticketId, "", issueStatus, userIws.getUserId(), 2,
                            event -> {
                                if (event.isLeft()) {
                                    log.error("Support : error when saving ticket histo " + event.left().getValue());
                                }
                            }
                    );
                }


                String issueIdStr = issue.getString(IDIWS_FIELD);
                issue.put("id", issueIdStr);
                issue.put("status",
                        new JsonObject().put("name", issue.getString(STATUSIWS_FIELD)
                        +  (issue.containsKey(DATE_RESOIWS_FIELD) ? " " +issue.getString(DATE_RESOIWS_FIELD): "")));
                Number issueId = 0;
                try {
                    // use ticket id as issue id in database
                    issueId = Integer.parseInt(ticketId);
                } catch (NumberFormatException e) {
                    log.error("Invalid id_ent, saving issue with id 0");
                }
                JsonObject dataIssue = new JsonObject().put("issue", issue);
                ticketServiceSql.endSuccessfulEscalation(
                        ticketId, dataIssue, issueId, null, userIws, handler
                );
            }
        };
    }

    /**
     * Attachments can't be synced in asynchronous mode, ignore the step
     */
    @Override
    public void syncAttachments(String ticketId, JsonArray attachments, Handler<Either<String, JsonObject>> handler) {
        handler.handle(new Either.Right<>(new JsonObject()));
    }

    /**
     * Get user information from Neo4j, then create issue
     */
    private void getDataAndCreateIssue(final JsonObject ticket, final JsonArray attachments,
                                       final JsonArray comments, final JsonObject issue,
                                       final UserInfos escalationUser,
                                       final Handler<Either<String,JsonObject>> handler) {
        TicketServiceNeo4jImpl.getUserEscalateInfo(
                ticket.getString("owner_id"),
                ticket.getString("school_id"),
                event -> {
                        if(event.isLeft()) {
                            log.error(event.left().getValue());

                            handler.handle(new Either.Left<>(event.left().getValue()));
                        } else if (event.right().getValue().size() == 0){
                            log.error("No data associated to ticket : " + Long.toString(ticket.getLong("id")));

                            handler.handle(new Either.Right<>(new JsonObject().put("status", "ko")));
                        } else {
                            JsonObject neoData = event.right().getValue().getJsonObject(0);
                            EscalationServicePivotImpl.this.createIssue(ticket,
                                    attachments, comments, neoData, issue, escalationUser, getCreateIssueHandler(handler));
                        }
                });
    }

    /**
     * Create issue in bugtracker
     * Format every data to Pivot format
     * Send one the bus for Pivot module
     * @param ticket ticket to send to bugtracker
     * @param attachments attachments of ticket
     * @param comments comments of ticket
     * @param userInfos Information on ticket author and structure
     */
    private void createIssue(final JsonObject ticket, final JsonArray attachments,
                             final JsonArray comments, final JsonObject userInfos,
                             final JsonObject issue, final UserInfos escalationUser,
                             final Handler<Message<JsonObject>> handler) {


        buildUserDataField(ticket, userInfos, escalationUser,
                userDataFieldValue -> {
                    if(userDataFieldValue.isRight()) {
                        String Creator =  userDataFieldValue.right().getValue() ;
                        JsonObject data = new JsonObject()
                                .put(ATTRIBUTION_FIELD, ATTRIBUTION_ENT_VALUE)
                                .put(IDENT_FIELD, Long.toString(ticket.getLong("id")))
                                .put(TITLE_FIELD, ticket.getString("subject"))
                                .put(DESCRIPTION_FIELD, ticket.getString(DESCRIPTION_FIELD))
                                .put(STATUSENT_FIELD, Long.toString(ticket.getLong("status")))
                                .put(CREATOR_FIELD, Creator)
                                .put(DATE_CREA_FIELD, ticket.getString("created"))
                                .put(ACADEMY_FIELD, userInfos.getString("structacademy"))
                                .put(MODULES_FIELD, new JsonArray().add(ticket.getString("category")));

                        if (comments != null && comments.size() > 0) {
                            data.put(COMM_FIELD, comments);
                        }
                        if (attachments != null && attachments.size() > 0) {
                            data.put(ATTACHMENT_FIELD, attachments);
                        }
                        if (issue != null && issue.containsKey("content") ) {
                            JsonObject issueContent = new JsonObject(issue.getString("content"));
                            if (issueContent.containsKey("issue")) {
                                JsonObject issueData = issueContent.getJsonObject("issue");
                                if (issueData.containsKey(IDIWS_FIELD) && !issueData.getString(IDIWS_FIELD).isEmpty()) {
                                    data.put(IDIWS_FIELD, issueData.getString(IDIWS_FIELD));
                                }
                            }
                        }

                        eb.send(TRACKER_ADDRESS, new JsonObject().put("action", "create").put("issue", data), handlerToAsyncHandler(handler));
                    }
                    else{
                        log.error("couldn't get information of any responsible of the ticket");
                        handler.handle(new ErrorMessage("couldn't get information of any responsible of the ticket"));
                    }
                });
    }

    /**
     * Build userData field Value. If ticket owner dosen't have academic mail address, use escalating user instead of him
     * @param ticket the ticket info
     * @param userInfos The creator of the ticket info
     * @param escalationUser the connected user info
     * @param handler
     */
    private void buildUserDataField(final JsonObject ticket, JsonObject userInfos, final UserInfos escalationUser, Handler<Either<String, String>> handler) {
        final String UserEmail_FAILD = "useremail" ;
        if (userInfos.getString(UserEmail_FAILD) != null) {
            handler.handle(new Either.Right<>(
                    BuildStringEscalationUser(
                            ticket.getString("owner_name"),
                            userInfos.getString(UserEmail_FAILD),
                            userInfos.getString("userphone"),
                            userInfos.getString("structname"),
                            userInfos.getString("structuai"))
            ));
        } else {
            TicketServiceNeo4jImpl.getUserEscalateInfo( escalationUser.getUserId(),  escalationUser.getStructures().get(0),
                    userEscalateInfo -> {
                        if (userEscalateInfo.isRight() && userEscalateInfo.right().getValue().size() > 0) {
                            JsonObject userInfo = userEscalateInfo.right().getValue().getJsonObject(0);
                            String userEmail = (null != userInfo.getString(UserEmail_FAILD)) ?
                                    userInfo.getString(UserEmail_FAILD)
                                    : userInfo.getString("userpersoemail");
                            handler.handle(new Either.Right<>(BuildStringEscalationUser(
                                    escalationUser.getUsername(),
                                    userEmail,
                                    userInfo.getString("userphone"),
                                    userInfo.getString("structname"),
                                    userInfo.getString("structuai")
                            )));
                        }else {
                            log.error("Something is wrong! can't find the connected user on data base");
                            handler.handle(new Either.Left<>("Something is wrong! can't find you on data base"));
                        }
                    });
        }
    }

    String BuildStringEscalationUser(String uName ,String uEmail, String uPhone, String sName, String sUAI){
        return uName + " | " + uEmail + " | " + uPhone + " | " + sName + " | " + sUAI;
    }

    /**
     * Create handler to process create issue response
     * @return handler
     */
    private Handler<Message<JsonObject>> getCreateIssueHandler(final Handler<Either<String, JsonObject>> handler) {
        return event -> {
            if (!"ok".equals(event.body().getString("status"))) {
                handler.handle(new Either.Left<>("support.escalation.error"));
            } else {
                handler.handle(new Either.Right<>(event.body()));
            }
        };
    }
}
