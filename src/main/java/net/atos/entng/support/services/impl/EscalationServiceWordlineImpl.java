package net.atos.entng.support.services.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.bus.WorkspaceHelper.Document;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.StatementsBuilder;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.Server;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.eventbus.EventBus;

import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;


/**
 * This class is an implementation of the EscalationService interface for the Wordline bug tracker.
 * It is used to escalate tickets to the bug tracker.
 */
public class EscalationServiceWordlineImpl implements EscalationService {


    private static final String HEADER_WORDLINE_API_KEY = "Authorization";
    private final Logger log;
    private final HttpClient httpClient;
    private final String wordlineHost;
    private final int wordlinePort;
    private final int wordlineProjectId;
    private final WorkspaceHelper wksHelper;
    private String wordlineApiKey;
    private String url;


    public EscalationServiceWordlineImpl(final Vertx vertx, final JsonObject config, final TicketServiceSql ts, final UserService us, Storage storage) {

        log = LoggerFactory.getLogger(EscalationServiceWordlineImpl.class);
        EventBus eb = Server.getEventBus(vertx);
        wksHelper = new WorkspaceHelper(eb, storage);

        HttpClientOptions options = new HttpClientOptions().setConnectTimeout(10000);

        // wordline host to connect to
        wordlineHost = config.getString("bug-tracker-host", null);
        if (wordlineHost == null || wordlineHost.trim().isEmpty()) {
            log.error("[Support] Error : Module property 'bug-tracker-host' must be defined");
        }

        // wordline port to connect to
        wordlinePort = config.getInteger("bug-tracker-port", 80);


        options.setDefaultHost(wordlineHost).setDefaultPort(wordlinePort)
                .setSsl(config.getBoolean("bug-tracker-ssl", (wordlinePort == 443)));


        // wordline API key to authenticate
        wordlineApiKey = config.getString("bug-tracker-api-key", null);
        if (wordlineApiKey == null || wordlineApiKey.trim().isEmpty()) {
            log.error("[Support] Error : Module property 'bug-tracker-api-key' must be defined");
        }

        wordlineProjectId = config.getInteger("bug-tracker-projectid", -1);
        if (wordlineProjectId == -1) {
            log.error("[Support] Error : Module property 'bug-tracker-projectid' must be defined");
        }

        options.setMaxPoolSize(config.getInteger("escalation-httpclient-maxpoolsize", 16))
                .setKeepAlive(config.getBoolean("escalation-httpclient-keepalive", false))
                .setTryUseCompression(config.getBoolean("escalation-httpclient-tryusecompression", true));

        httpClient = vertx.createHttpClient(options);

        // URL to request to create an issue
        url = "http://" + wordlineHost + ":" + wordlinePort;

    }


    @Override
    public BugTracker getBugTrackerType() {
        return BugTracker.WORDLINE;
    }

    @Override
    public void escalateTicket(final HttpServerRequest request, final JsonObject ticket, final JsonArray comments,
                               final JsonArray attachmentsIds, final ConcurrentMap<Long, String> attachmentMap, final UserInfos user,
                               final JsonObject issue, final Handler<Either<String, JsonObject>> handler) {

        // Here escalates the ticket to the bug tracker
        this.createIssue(request, ticket, attachmentsIds, user, getCreateIssueHandler(ticket, request, comments, handler));

    }

    private Handler<HttpClientResponse> getCreateIssueHandler(final JsonObject ticket, final HttpServerRequest request, final JsonArray comments,
                                                              final Handler<Either<String, JsonObject>> handler) {

        /**
         * Handler called when the issue creation request is sent to the bug tracker.
         */
        return new Handler<HttpClientResponse>() {
            @Override
            public void handle(final HttpClientResponse resp) {
                resp.exceptionHandler(excep -> log.error("client error", excep));
                resp.bodyHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(final Buffer data) {
                        if (resp.statusCode() == 200) { // Issue escalation was successful

                            // Review response from wordline API
                            log.info("Issue escalation to wordline successfully.");
                            final JsonObject response = new JsonObject(data.toString());
                            EscalationServiceWordlineImpl.this.getBugTrackerType().extractIdFromIssue(ticket);
							handler.handle(new Either.Right<String, JsonObject>(response));
                        } else {
                            log.error("Error during escalation. Could not create wordline issue. Response status is "
                                    + resp.statusCode() + " instead of 200.");
                            log.error(data.toString());
                            handler.handle(new Either.Left<String, JsonObject>("support.escalation.error"));
                        }
                    }
                });
            }
        };
    }


    private void createIssue(final HttpServerRequest request, final JsonObject ticket, final JsonArray attachmentsIds,
                             final UserInfos user, final Handler<HttpClientResponse> handler) {


        // add fields (such as ticket id, application name ...) to data
        // TO CHANGE AND UPDATE THIS PART WITH WORDLINE DOCUMENTATION
        final String locale = I18n.acceptLanguage(request);

        // get school name and add it to description
        final String schoolId = ticket.getString("school_id");
        final StatementsBuilder s = new StatementsBuilder();
        s.add("MATCH (s:Structure {id : {schoolId}}) return s.name as name ",
                new JsonObject().put("schoolId", schoolId));
        s.add("MATCH (a:Application {address : {category}}) return a.displayName as name",
                new JsonObject().put("category", ticket.getString("category")));
        Neo4j.getInstance().executeTransaction(s.build(), null, true, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> message) {
                String schoolName, category;
                JsonArray res = message.body().getJsonArray("results");
                if ("ok".equals(message.body().getString("status")) && res != null && res.size() == 2
                        && res.<JsonArray>getJsonArray(0) != null && res.<JsonArray>getJsonArray(1) != null) {
                    JsonArray sa = res.getJsonArray(0);
                    JsonObject s;
                    if (sa != null && sa.size() == 1 && (s = sa.getJsonObject(0)) != null
                            && s.getString("name") != null) {
                        schoolName = s.getString("name");
                    } else {
                        schoolName = schoolId;
                    }
                    JsonArray aa = res.getJsonArray(1);
                    JsonObject a;
                    if (aa != null && aa.size() == 1 && (a = aa.getJsonObject(0)) != null
                            && a.getString("name") != null) {
                        category = a.getString("name");
                    } else {
                        // Category "Other" is saved as i18n, whereas remaining categories are addresses
                        // (e.g. "/support")
                        category = I18n.getInstance().translate(ticket.getString("category"), Renders.getHost(request),
                                locale);
                        if (category.equals(ticket.getString("category"))) {
                            category = category.substring(1);
                        }
                    }
                } else {
                    schoolName = schoolId;
                    // Category "Other" is saved as i18n, whereas remaining categories are addresses
                    // (e.g. "/support")
                    category = I18n.getInstance().translate(ticket.getString("category"), Renders.getHost(request),
                            locale);
                    if (category.equals(ticket.getString("category"))) {
                        category = category.substring(1);
                    }
                }

                log.info("Ticket: " + ticket.toString());

                // Data to send to wordline API
                final JsonObject data = new JsonObject();
                data.put("projectId", wordlineProjectId);
                data.put("subject", ticket.getString("subject"));
                data.put("description", ticket.getString("description"));
                data.put("category", category);
                data.put("ownerName", ticket.getString("owner_name"));
                data.put("ticketId", ticket.getLong("id"));
                data.put("schoolName", schoolName);
                data.put("manger", user.getUsername());

                
                // Create buffer to send data and files
                Buffer buffer = Buffer.buffer().appendString("--boundary\r\n");
                // List of futures to read attachments
                List<Future<Buffer>> futures = new ArrayList<Future<Buffer>>();

                if (attachmentsIds != null && attachmentsIds.size() > 0) {
                    for (Object o : attachmentsIds) {
                        if (!(o instanceof String))
                            continue;
                        final String attachmentId = (String) o;

                        // Read document from workspace and add it to buffer
                        Future<Buffer> future = Future.future(promise -> {
                            readDocumentWksHelper(attachmentId, promise);
                        });
                        // Add future to list
                        futures.add(future);
                    }
                }

                // TODO FIX TYPE ERROR WITH VERTX 4
                // Wait for all futures to complete before sending data to wordline API
                CompositeFuture.all((List) futures).onComplete(ar -> {
                    if (ar.succeeded()) {
                        // All succeeded
                        for (Future<Buffer> future : futures) {
                            buffer.appendBuffer(future.result());
                        }

                        buffer.appendString("Content-Disposition: form-data; name=\"data\"\r\n\r\n")
                            .appendString(data.toString() + "\r\n")
                            .appendString("--boundary--\r\n");

                        // Post data and files to wordline API after reading all attachments and handle response
                        httpClient.post(url, handler).exceptionHandler(new Handler<Throwable>() {
                                    @Override
                                    public void handle(Throwable t) {
                                        log.error("[Support] Error : exception raised by wordline escalation httpClient", t);
                                    }
                                }).putHeader(HttpHeaders.HOST, wordlineHost).putHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=boundary")
                                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
                                .putHeader(HEADER_WORDLINE_API_KEY, "Bearer " + wordlineApiKey).write(buffer).end();
                    } else {
                        // At least one failed
                        log.error("Error when reading attachments from workspace", ar.cause());
                    }

                });
            }

        });

    }

    // TODO VERTX 4
    /**
     * Read document from workspace
     *
     * @param attachmentId
     * @param promise      Buffer to complete with the document data
     */
    private void readDocumentWksHelper(String attachmentId, Promise<Buffer> promise) {
        wksHelper.readDocument(attachmentId, new Handler<WorkspaceHelper.Document>() {
            @Override
            public void handle(final Document file) {
                try {
                    final String filename = file.getDocument().getString("name");
                    final String contentType = file.getDocument().getJsonObject("metadata").getString("content-type");

                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("Content-Disposition: form-data; name=\"file\"; filename=" + filename + "\r\n");
                    buffer.appendString("Content-Type:" + contentType + "\r\n\r\n");
                    buffer.appendBuffer(file.getData());
                    buffer.appendString("\r\n--boundary\r\n");

                    promise.complete(buffer);

                } catch (Exception e) {
                    log.error("Error when processing response from readDocument", e);
                }

            }
        });
    }


    @Override
    public void getIssue(Number issueId, Handler<Either<String, JsonObject>> handler) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getIssue'");
    }


    @Override
    public void commentIssue(Number issueId, JsonObject comment, Handler<Either<String, JsonObject>> handler) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'commentIssue'");
    }


    @Override
    public void updateTicketFromBugTracker(Message<JsonObject> message, Handler<Either<String, JsonObject>> handler) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTicketFromBugTracker'");
    }


    @Override
    public void syncAttachments(String ticketId, JsonArray attachments, Handler<Either<String, JsonObject>> handler) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'syncAttachments'");
    }

}