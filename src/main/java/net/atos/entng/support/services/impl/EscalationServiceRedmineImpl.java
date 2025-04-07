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

package net.atos.entng.support.services.impl;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.http.Renders;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.*;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.TicketHisto;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.bus.WorkspaceHelper.Document;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.StatementsBuilder;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.Id;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class EscalationServiceRedmineImpl implements EscalationService {

	private final Logger log;

	private final HttpClient httpClient;
	private final String redmineHost;
	private final int redminePort;
	private boolean proxyIsDefined;
	private final int redmineProjectId;
	private final Number redmineResolvedStatusId;
	private final Number redmineClosedStatusId;

	private final WorkspaceHelper wksHelper;
	private final TimelineHelper notification;
	private final TicketServiceSql ticketServiceSql;
	private final UserService userService;
	private final Storage storage;
	private final Sql sql = Sql.getInstance();

	/*
	 * According to
	 * http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication : API
	 * key is a handy way to avoid putting a password in a script. You can find your
	 * API key on your account page ( /my/account ) when logged in, on the
	 * right-hand pane of the default layout.
	 */
	private static final String HEADER_REDMINE_API_KEY = "X-Redmine-API-Key";
	private String redmineApiKey;

	private static final String REDMINE_ISSUES_PATH = "/issues.json";
	private static final String REDMINE_UPLOAD_ATTACHMENT_PATH = "/uploads.json";

	private static final String REDMINE_SUPPORT_GROUP_ID = "4";
	private static final String REDMINE_COMMENTAIRE_STATUS_ID = "4";

	@Override
	public BugTracker getBugTrackerType() {
		return BugTracker.REDMINE;
	}

	public EscalationServiceRedmineImpl(final Vertx vertx, final JsonObject config, final TicketServiceSql ts,
			final UserService us, Storage storage) {

		log = LoggerFactory.getLogger(EscalationServiceRedmineImpl.class);
		EventBus eb = Server.getEventBus(vertx);
		HttpClientOptions options = new HttpClientOptions().setConnectTimeout(10000);
		wksHelper = new WorkspaceHelper(eb, storage);
		notification = new TimelineHelper(vertx, eb, config);
		ticketServiceSql = ts;
		userService = us;
		this.storage = storage;

		String proxyHost = System.getProperty("http.proxyHost", null);
		int proxyPort = 80;
		try {
			proxyPort = Integer.valueOf(System.getProperty("http.proxyPort", "80"));
		} catch (NumberFormatException e) {
			log.error("[Support] Error : JVM property 'http.proxyPort' must be an integer", e);
		}

		redmineHost = config.getString("bug-tracker-host", null);
		if (redmineHost == null || redmineHost.trim().isEmpty()) {
			log.error("[Support] Error : Module property 'bug-tracker-host' must be defined");
		}
		redminePort = config.getInteger("bug-tracker-port", 80);

		redmineResolvedStatusId = config.getInteger("bug-tracker-resolved-statusid", -1);
		redmineClosedStatusId = config.getInteger("bug-tracker-closed-statusid", -1);
		if (redmineResolvedStatusId.intValue() == -1) {
			log.error("[Support] Error : Module property 'bug-tracker-resolved-statusid' must be defined");
		}
		if (redmineClosedStatusId.intValue() == -1) {
			log.error("[Support] Error : Module property 'bug-tracker-closed-statusid' must be defined");
		}

		if (proxyHost != null && !proxyHost.trim().isEmpty()) {
			proxyIsDefined = true;
			options.setDefaultHost(proxyHost).setDefaultPort(proxyPort);
		} else {
			options.setDefaultHost(redmineHost).setDefaultPort(redminePort)
					.setSsl(config.getBoolean("bug-tracker-ssl", (redminePort == 443)));
		}
		log.info("[Support] proxyHost: " + proxyHost);
		log.info("[Support] proxyPort: " + proxyPort);

		redmineApiKey = config.getString("bug-tracker-api-key", null);
		if (redmineApiKey == null || redmineApiKey.trim().isEmpty()) {
			log.error("[Support] Error : Module property 'bug-tracker-api-key' must be defined");
		}

		redmineProjectId = config.getInteger("bug-tracker-projectid", -1);
		if (redmineProjectId == -1) {
			log.error("[Support] Error : Module property 'bug-tracker-projectid' must be defined");
		}

		options.setMaxPoolSize(config.getInteger("escalation-httpclient-maxpoolsize", 16))
				.setKeepAlive(config.getBoolean("escalation-httpclient-keepalive", false))
				.setTryUseCompression(config.getBoolean("escalation-httpclient-tryusecompression", true));

		httpClient = vertx.createHttpClient(options);

		Long delayInMinutes = config.getLong("refresh-period", 30L);
		log.info("[Support] Data will be pulled from Redmine every " + delayInMinutes + " minutes");
		final Long delay = TimeUnit.MILLISECONDS.convert(delayInMinutes, TimeUnit.MINUTES);

		ticketServiceSql.getLastIssuesUpdate(new Handler<Either<String, String>>() {
			@Override
			public void handle(Either<String, String> event) {
				final String lastUpdate;
				final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				df.setTimeZone(TimeZone.getTimeZone("GMT"));
				if (event.isRight() && event.right().getValue() != null) {
					String date = event.right().getValue();

					if (date != null) {
						try {
							Date d = df.parse(date);
							date = df.format(new Date(d.getTime() + 2000l));
						} catch (ParseException e) {
							log.error(e.getMessage(), e);
						}
					}

					log.info("[Support] Last pull from Redmine : " + date);
					lastUpdate = date;
				} else {
					lastUpdate = null;
				}

				vertx.setPeriodic(delay, new Handler<Long>() {
					// initialize last update with value from database
					String lastUpdateTime = lastUpdate;

					@Override
					public void handle(Long timerId) {
						Date currentDate = new Date();
						log.debug("[Support] Current date : " + currentDate.toString());

						EscalationServiceRedmineImpl.this.pullDataAndUpdateIssues(lastUpdateTime);
						lastUpdateTime = df.format(new Date(currentDate.getTime() + 2000l));
						log.debug("[Support] New value of lastUpdateTime : " + lastUpdateTime);
					}
				});
			}
		});

	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void escalateTicket(final HttpServerRequest request, final Ticket ticket,
			final UserInfos user, final Issue issue, final Handler<Either<String, Issue>> handler) {

		JsonObject ticketJson = ticket.toJsonObject();
		JsonArray comments = ticketJson.getJsonArray("comments");
		JsonArray attachmentsIds = ticketJson.getJsonArray("attachments");
		/*
		 * Escalation steps 1) if there are attachments, upload each attachement.
		 * Redmine returns a token for each attachement 2) create the issue with all its
		 * attachments 3) update the issue with all comments
		 */
		final JsonArray attachments = new JsonArray();
		final List<Attachment> issueAttachments = new ArrayList<Attachment>();

		if (attachmentsIds != null && attachmentsIds.size() > 0) {
			final AtomicInteger successfulUploads = new AtomicInteger(0);
			final AtomicInteger failedUploads = new AtomicInteger(0);

			for (Object o : attachmentsIds) {
				if (!(o instanceof String))
					continue;
				final String attachmentId = (String) o;

				// 1) read attachment from workspace, and upload it to redmine
				wksHelper.readDocument(attachmentId, new Handler<WorkspaceHelper.Document>() {
					@Override
					public void handle(final Document file) {
						try {
							final String filename = file.getDocument().getString("name");
							final String contentType = file.getDocument().getJsonObject("metadata")
									.getString("content-type");

							EscalationServiceRedmineImpl.this.uploadAttachment(file.getData(),
									new Handler<HttpClientResponse>() {
										@Override
										public void handle(final HttpClientResponse resp) {
											resp.exceptionHandler(excep -> log.error("client error", excep));

											resp.bodyHandler(new Handler<Buffer>() {
												@Override
												public void handle(final Buffer event) {
													if (resp.statusCode() == 201) {
														// Example of token returned by Redmine :
														// {"upload":{"token":"781.687411f12da55bbd5a3d991675ac2135"}}
														JsonObject response = new JsonObject(event.toString());
														String token = response.getJsonObject("upload")
																.getString("token");
														String attachmentIdInRedmine = token.substring(0,
																token.indexOf('.'));
														issueAttachments.add(new WorkspaceAttachment(Long.valueOf(attachmentIdInRedmine), filename, file.getDocument().getString("_id")));

														JsonObject attachment = new JsonObject().put("token", token)
																.put("filename", filename)
																.put("content_type", contentType);
														attachments.add(attachment);

														// 2) Create redmine issue only if all attachments have been
														// uploaded successfully
														if (successfulUploads.incrementAndGet() == attachmentsIds
																.size()) {
															EscalationServiceRedmineImpl.this.createIssue(request,
																	ticketJson, attachments, user,
																	getCreateIssueHandler(request, comments, issueAttachments, handler));
														}
													} else {
														log.error(
																"Error during escalation. Could not upload attachment to Redmine. Response status is "
																		+ resp.statusCode() + " instead of 201.");
														log.error(event.toString());

														// Return error message as soon as one upload failed
														if (failedUploads.incrementAndGet() == 1) {
															handler.handle(new Either.Left<String, Issue>(
																	"support.escalation.error.upload.attachment.failed"));
														}
													}
												}
											});
										}
									});
						} catch (Exception e) {
							log.error("Error when processing response from readDocument", e);
						}

					}
				});

			}
		} else {
			this.createIssue(request, ticketJson, attachments, user, getCreateIssueHandler(request, comments, null, handler));
		}

	}

	private Handler<HttpClientResponse> getCreateIssueHandler(final HttpServerRequest request, final JsonArray comments, final List<Attachment> attachments,
			final Handler<Either<String, Issue>> handler) {

		return new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.exceptionHandler(excep -> log.error("client error", excep));
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(final Buffer data) {
						if (resp.statusCode() == 201) { // Issue creation was successful
							try {
								final JsonObject response = new JsonObject(data.toString());
								Issue issue = new Issue(response.getJsonObject("issue").getLong("id"), response);
								if (comments == null || comments.size() == 0) {
									handler.handle(new Either.Right<String, Issue>(issue));
									return;
								}
								if(attachments != null)
									issue.attachments.addAll(attachments);

								EscalationServiceRedmineImpl.this.updateIssue(issue.id.get(),
										new Comment(aggregateComments(request, comments).getString("content")), getUpdateIssueHandler(issue, handler));

							} catch (Exception e) {
								log.error(
										"Redmine issue was created. Error when trying to update it, i.e. when adding comment",
										e);
								handler.handle(new Either.Left<String, Issue>("support.escalation.error"));
							}
						} else {
							log.error("Error during escalation. Could not create redmine issue. Response status is "
									+ resp.statusCode() + " instead of 201.");
							log.error(data.toString());
							handler.handle(new Either.Left<String, Issue>("support.escalation.error"));
						}
					}
				});
			}
		};

	}

	private Handler<HttpClientResponse> getUpdateIssueHandler(final Issue issue, final Handler<Either<String, Issue>> handler) {

		return new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse event) {
				event.exceptionHandler(excep -> log.error("client error", excep));
				event.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						if (event.statusCode() == 200) {
							handler.handle(new Either.Right<String, Issue>(issue));
						} else {
							log.error(
									"Error during escalation. Could not update redmine issue to add comment. Response status is "
											+ event.statusCode() + " instead of 200.");
							log.error(buffer.toString());
							handler.handle(new Either.Left<String, Issue>("support.error.escalation.incomplete"));
						}
					}
				});
			}
		};
	}

	/*
	 * Return a JsonObject containing all comments
	 */
	private JsonObject aggregateComments(final HttpServerRequest request, JsonArray comments) {
		JsonObject result = new JsonObject();
		if (comments != null && comments.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (Object o : comments) {
				if (!(o instanceof JsonObject))
					continue;
				JsonObject c = (JsonObject) o;

				sb.append(c.getString("owner_name")).append(", ");

				String onDate = I18n.getInstance().translate("support.on", Renders.getHost(request),
						I18n.acceptLanguage(request));
				sb.append(onDate).append(" ").append(c.getString("created"));

				sb.append("\n\n").append(c.getString("content")).append("\n\n");
			}

			result.put("content", sb.toString());
		}

		return result;
	}

	private void uploadAttachment(final Buffer data, final Handler<HttpClientResponse> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_UPLOAD_ATTACHMENT_PATH)
				: REDMINE_UPLOAD_ATTACHMENT_PATH;

		httpClient.request(new RequestOptions()
					.setMethod(HttpMethod.POST)
					.setURI(url)
					.setHeaders(new HeadersMultiMap()
							.add(HttpHeaders.HOST, redmineHost)
							.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
							.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length()))
							.add(HEADER_REDMINE_API_KEY, redmineApiKey)))
				.flatMap(request -> request.send(data))
				.onFailure(t -> log.error("[Support] Error : exception raised by redmine escalation httpClient", t));

	}

	private void createIssue(final HttpServerRequest request, final JsonObject ticket, final JsonArray attachments,
			final UserInfos user, final Handler<HttpClientResponse> handler) {

		final String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH)
				: REDMINE_ISSUES_PATH;

		final JsonObject data = new JsonObject().put("project_id", redmineProjectId).put("subject",
				ticket.getString("subject"));

		// add fields (such as ticket id, application name ...) to description
		final String locale = I18n.acceptLanguage(request);
		final String categoryLabel = I18n.getInstance().translate("support.escalated.ticket.category",
				Renders.getHost(request), locale);
		final String ticketOwnerLabel = I18n.getInstance().translate("support.escalated.ticket.ticket.owner",
				Renders.getHost(request), locale);
		final String ticketIdLabel = I18n.getInstance().translate("support.escalated.ticket.ticket.id",
				Renders.getHost(request), locale);
		final String schoolNameLabel = I18n.getInstance().translate("support.escalated.ticket.school.name",
				Renders.getHost(request), locale);
		final String ManagerLabel = I18n.getInstance().translate("support.escalated.ticket.manager",
				Renders.getHost(request), locale);

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

				final StringBuilder description = new StringBuilder();
				appendDataToDescription(description, categoryLabel, category);
				appendDataToDescription(description, ticketOwnerLabel, ticket.getString("owner_name"));
				appendDataToDescription(description, ticketIdLabel, ticket.getLong("id").toString());

				appendDataToDescription(description, schoolNameLabel, schoolName);
				appendDataToDescription(description, ManagerLabel, user.getUsername());
				description.append("\n").append(ticket.getString("description"));

				data.put("description", description.toString());

				if (attachments != null && attachments.size() > 0) {
					data.put("uploads", attachments);
				}

				JsonObject issue = new JsonObject().put("issue", data);

				Buffer buffer = Buffer.buffer();
				buffer.appendString(issue.toString());

				httpClient.request(new RequestOptions()
								.setMethod(HttpMethod.POST)
								.setURI(url)
								.setHeaders(new HeadersMultiMap()
										.add(HttpHeaders.HOST, redmineHost)
										.add(HttpHeaders.CONTENT_TYPE, "application/json")
										.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
										.add(HEADER_REDMINE_API_KEY, redmineApiKey)))
						.flatMap(request -> request.send(buffer))
						.onFailure(t -> log.error("[Support] Error : exception raised by redmine escalation httpClient", t));
			}
		});

	}

	private void appendDataToDescription(final StringBuilder description, final String label, final String value) {
		description.append(label).append(": ").append(value).append("\n");
	}

	private void updateIssue(final Number issueId, final Comment comment,
			final Handler<HttpClientResponse> handler) {
		updateIssue(issueId, comment, null, handler);
	}

	private void updateIssue(final Number issueId, final Comment comment, JsonArray attachments,
			final Handler<HttpClientResponse> handler) {
		String path = "/issues/" + issueId + ".json";
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + path) : path;

		JsonObject data = new JsonObject();
		if (comment != null) {
			data.put("notes", comment.content);
			data.put("status_id", REDMINE_COMMENTAIRE_STATUS_ID);
			data.put("assigned_to_id", REDMINE_SUPPORT_GROUP_ID);
		}
		if (attachments != null) {
			data.put("uploads", attachments);
		}

		JsonObject ticket = new JsonObject().put("issue", data);

		Buffer buffer = Buffer.buffer().appendString(ticket.toString());

		httpClient.request(new RequestOptions()
						.setMethod(HttpMethod.PUT)
						.setURI(url)
						.setHeaders(new HeadersMultiMap()
								.add(HttpHeaders.HOST, redmineHost)
								.add(HttpHeaders.CONTENT_TYPE, "application/json")
								.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
								.add(HEADER_REDMINE_API_KEY, redmineApiKey)))
				.flatMap(request -> request.send(buffer))
				.onFailure(t -> log.error("[Support] Error : exception raised by redmine escalation httpClient", t));
	}

	private void pullDataAndUpdateIssues(final String since) {
		this.pullDataAndUpdateIssues(since, -1, -1, true);
	}

	private void pullDataAndUpdateIssues(final String lastUpdateTime, final int offset, final int limit,
			final boolean allowRecursiveCall) {
		/*
		 * Steps : 1) list Redmine issues that have been created/updated since last time
		 *
		 * 2) get issue ids that exist in current ENT and their attachments' ids
		 *
		 * 3) for each issue existing in current ENT, a/ get the "whole" issue (i.e.
		 * with its attachments' metadata and with its comments) from Redmine b/ update
		 * the issue in Postgresql, so that local administrators can see the last
		 * changes c/ If there are "new" attachments in Redmine, download them, store
		 * them in gridfs and store their metadata in postgresql
		 *
		 */
		log.debug("Value of since : " + lastUpdateTime);

		// Step 1)
		this.listIssues(lastUpdateTime, offset, limit, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(final Either<String, JsonObject> listIssuesEvent) {

				if (listIssuesEvent.isLeft()) {
					log.error("Error when listing issues. " + listIssuesEvent.left().getValue());
				} else {
					try {
						final JsonArray issues = listIssuesEvent.right().getValue().getJsonArray("issues", null);
						if (issues == null || issues.size() == 0) {
							log.debug("Result of listIssues is null or empty");
							return;
						}

						if (allowRecursiveCall) {
							int aTotalCount = listIssuesEvent.right().getValue().getInteger("total_count", -1);
							int aOffset = listIssuesEvent.right().getValue().getInteger("offset", -1);
							int aLimit = listIssuesEvent.right().getValue().getInteger("limit", -1);
							if (aTotalCount != -1 && aOffset != -1 && aLimit != -1 && (aTotalCount > aLimit)) {
								// Second call to get remaining issues
								EscalationServiceRedmineImpl.this.pullDataAndUpdateIssues(lastUpdateTime, aLimit,
										aTotalCount - aLimit, false);
							}
						}

						final Number[] issueIds = new Number[issues.size()];
						for (int i = 0; i < issues.size(); i++) {
							JsonObject issue = issues.getJsonObject(i);
							issueIds[i] = issue.getLong("id");
						}

						// Step 2) : given a list of issue ids in Redmine, get issue ids that exist in
						// current ENT and their attachments' ids
						ticketServiceSql.listExistingIssues(issueIds, new Handler<Either<String, List<Issue>>>() {
							@Override
							public void handle(Either<String, List<Issue>> event) {
								if (event.isLeft()) {
									log.error("Error when calling service listExistingIssueIds : " + event.left());
								} else {
									List<Issue> existingIssues = event.right().getValue();
									if (existingIssues == null || existingIssues.size() == 0) {
										log.info("No issue ids found in database");
										return;
									}
									log.debug("Result of service listExistingIssues : " + existingIssues.toString());

									final AtomicInteger remaining = new AtomicInteger(existingIssues.size());

									for (Issue i : existingIssues) {
										final Number issueId = i.id.get();

										final JsonArray existingAttachmentsIds = new JsonArray();
										for(Attachment a : i.attachments)
											existingAttachmentsIds.add(a.bugTrackerId);

										// Step 3a)
										EscalationServiceRedmineImpl.this.getIssue(issueId,
												new Handler<Either<String, Issue>>() {
													@Override
													public void handle(final Either<String, Issue> getIssueEvent) {
														if (getIssueEvent.isLeft()) {
															log.error(getIssueEvent.left().getValue());
														} else {
															final Issue issue = getIssueEvent.right().getValue();
															final JsonObject issueJson = issue.getContent();
															// check if this issue had already been received.
															ticketServiceSql.getTicketFromIssueId(issueId.toString(), getBugTrackerType().name(),
																	new Handler<Either<String, Ticket>>() {
																		public void handle(
																				final Either<String, Ticket> res) {
																			if (res.isRight()) {
																				final JsonObject ticket = res.right()
																						.getValue().toJsonObject();
																				final DateFormat dfTicket = new SimpleDateFormat(
																						"yyyy-MM-dd'T'HH:mm:ss");
																				final DateFormat dfIssue = new SimpleDateFormat(
																						"yyyy-MM-dd'T'HH:mm:ss'Z'");
																				dfIssue.setTimeZone(
																						TimeZone.getTimeZone("UTC"));
																				Date ticketDate = null;
																				Date issueDate = null;
																				if (ticket != null && ticket.getString(
																						"issue_update_date") != null
																						&& !"".equals(ticket.getString(
																								"issue_update_date"))) {
																					try {
																						ticketDate = dfTicket
																								.parse(ticket.getString(
																										"issue_update_date"));
																						issueDate = dfIssue.parse(issueJson
																								.getJsonObject("issue")
																								.getString(
																										"updated_on"));
																					} catch (ParseException e) {
																						e.printStackTrace();
																					}
																				}
																				if (ticketDate == null || !ticketDate
																						.equals(issueDate)) {
																					// if the issue_update_date ==
																					// updated_on, it means the update
																					// had already been treated.
																					// Step 3b) : update issue in
																					// postgresql
																					ticketServiceSql.updateIssue(
																							issueId, issue,
																							new Handler<Either<String, String>>() {
																								@Override
																								public void handle(
																										final Either<String, String> updateIssueEvent) {
																									if (updateIssueEvent
																											.isRight()) {
																										log.debug(
																												"pullDataAndUpdateIssue OK for issue n°"
																														+ issueId);
																										if (remaining
																												.decrementAndGet() < 1) {
																											log.info(
																													"pullDataAndUpdateIssue OK for all issues");
																										}

																										EscalationServiceRedmineImpl.this
																												.notifyIssueChanged(
																														issueId,
																														updateIssueEvent
																																.right()
																																.getValue(),
																														issueJson);
																										Long newStatus = issueJson.getJsonObject("issue").getJsonObject("status").getLong("id");
																										if (newStatus == 5l) {
                                                                                                            newStatus = 4l;
                                                                                                        } else if (newStatus >= 4l) {
																											newStatus = 2l;
																										}
																										ticketServiceSql
																												.updateTicketIssueUpdateDateAndStatus(
																														ticket.getLong(
																																"id"),
																														issueJson.getJsonObject(
																																"issue")
																																.getString(
																																		"updated_on"),
																														newStatus,
																														new Handler<Either<String, Void>>() {
																															@Override
																															public void handle(
																																	final Either<String, Void> res) {
																																if (res.isLeft()) {
																																	log.error(
																																			"Updating ticket "
																																					+ ticket.getLong(
																																							"id")
																																							.toString()
																																					+ " failed");
																																}
																															}
																														});
																									} else {
																										log.error(
																												"pullDataAndUpdateIssue FAILED. Error when updating issue n°"
																														+ issueId);
																									}
																								}
																							});

																					// Step 3c) : If "new" attachments
																					// have been added in Redmine,
																					// download them
																					final JsonArray redmineAttachments = issueJson
																							.getJsonObject("issue")
																							.getJsonArray("attachments",
																									null);
																					if (redmineAttachments != null
																							&& redmineAttachments
																									.size() > 0) {
																						boolean existingAttachmentIdsEmpty = existingAttachmentsIds == null
																								|| existingAttachmentsIds
																										.size() == 0;

																						for (Object o : redmineAttachments) {
																							if (!(o instanceof JsonObject))
																								continue;
																							final JsonObject attachment = (JsonObject) o;
																							final Number redmineAttachmentId = attachment
																									.getLong("id");

																							if (existingAttachmentIdsEmpty
																									|| !existingAttachmentsIds
																											.contains(
																													redmineAttachmentId)) {
																								final String attachmentUrl = attachment
																										.getString(
																												"content_url");
																								EscalationServiceRedmineImpl.this
																										.doDownloadAttachment(
																												attachmentUrl,
																												attachment,
																												issueId);
																							}
																						}
																					}
																				}
																			}
																		}

																	});

														}
													}
												});
									}
								}
							}
						});

					} catch (Exception e) {
						log.error("Service pullDataAndUpdateIssues : error after listing issues", e);
					}
				}

			}

		});
	}

	private void listIssues(final String since, final int offset, final int limit,
			final Handler<Either<String, JsonObject>> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH)
				: REDMINE_ISSUES_PATH;

		StringBuilder query = new StringBuilder("?status_id=*"); // return open and closed issues
		if (since != null) {
			// updated_on : fetch issues updated after a certain date
			query.append("&updated_on=%3E%3D").append(since);
			/*
			 * "%3E%3D" is ">=" with hex-encoding. According to
			 * http://www.redmine.org/projects/redmine/wiki/Rest_Issues : operators
			 * containing ">", "<" or "=" should be hex-encoded
			 */
		}
		if (offset > -1) {
			// offset: skip this number of issues in response
			query.append("&offset=").append(offset);
		}
		if (limit > 0) {
			// limit: number of issues per page
			query.append("&limit=").append(limit);
		}
		url += query.toString();
		log.info("Url used to list redmine issues : " + url);

		httpClient.request(new RequestOptions()
						.setMethod(HttpMethod.GET)
						.setHeaders(new HeadersMultiMap()
								.add(HttpHeaders.HOST, redmineHost)
								.add(HEADER_REDMINE_API_KEY, redmineApiKey)
								.add(HttpHeaders.CONTENT_TYPE, "application/json")))
				.flatMap(HttpClientRequest::send)
				.onSuccess(resp -> resp.bodyHandler(data -> {
					JsonObject response = new JsonObject(data.toString());
					if (resp.statusCode() == 200) {
						handler.handle(new Either.Right<String, JsonObject>(response));
					} else {
						log.error("Error when listing redmine tickets. Response status is " + resp.statusCode()
								+ " instead of 200.");
						handler.handle(new Either.Left<String, JsonObject>(response.toString()));
					}
				}))
				.onFailure(excep -> log.error("client error", excep));
	}

	private void doDownloadAttachment(final String attachmentUrl, final JsonObject attachment, final Number issueId) {
		final Long attachmentIdInRedmine = attachment.getLong("id");

		EscalationServiceRedmineImpl.this.downloadAttachment(attachmentUrl, new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {
				// store attachment
				storage.writeBuffer(data, attachment.getString("content_type", ""), attachment.getString("filename"),
						new Handler<JsonObject>() {
							@Override
							public void handle(JsonObject attachmentMetaData) {
								/*
								 * Response example from gridfsWriteBuffer :
								 * {"_id":"f62f5dac-b32b-4cb8-b70a-1016885f37ec","status":"ok","metadata":{
								 * "content-type":"image/png","filename":"test_pj.png","size":118639}}
								 */
								log.info("Metadata of attachment written in gridfs: "
										+ attachmentMetaData.encodePrettily());
								JsonObject md = attachmentMetaData.getJsonObject("metadata");

								Attachment att = new GridFSAttachment(attachmentIdInRedmine, md.getString("filename"), md.getString("content-type"), md.getInteger("size"), attachmentMetaData.getString("_id"));

								// store attachment's metadata in postgresql
								ticketServiceSql.insertIssueAttachment(new Id<Issue, Number>(issueId), att,
										new Handler<Either<String, Void>>() {
											@Override
											public void handle(Either<String, Void> event) {
												if (event.isRight()) {
													log.info("download attachment " + attachmentIdInRedmine
															+ " OK for issue n°" + issueId);
												} else {
													log.error("download attachment " + attachmentIdInRedmine
															+ " FAILED for issue n°" + issueId
															+ ". Error when trying to insert metadata in postgresql");
												}
											}
										});
							}
						});
			}
		});
	}

	/*
	 * Notify local administrators (of the ticket's school_id) that the Redmine
	 * issue's status has been changed to "resolved" or "closed"
	 */
	private void notifyIssueChanged(final Number issueId, final String updateIssueStatus,
			final JsonObject issue) {
		try {
			final int oldStatusId = Integer.parseInt(updateIssueStatus);
			final Long newStatusId = issue.getJsonObject("issue").getJsonObject("status").getLong("id");
			log.debug("Old status_id: " + oldStatusId);
			log.debug("New status_id:" + newStatusId);
//			if(newStatusId.intValue() != oldStatusId &&
//					(newStatusId.intValue() == redmineResolvedStatusId.intValue() ||
//					newStatusId.intValue() == redmineClosedStatusId.intValue())) {

			JsonObject lastEvent = null;
			if (issue.getJsonObject("issue") != null && issue.getJsonObject("issue").getJsonArray("journals") != null
					&& issue.getJsonObject("issue").getJsonArray("journals").size() >= 1) {
				// getting the last event from the bug tracker for historization
				lastEvent = issue.getJsonObject("issue").getJsonArray("journals")
						.getJsonObject(issue.getJsonObject("issue").getJsonArray("journals").size() - 1);
			}
			final JsonObject fLastEvent = lastEvent;

			// get school_id and ticket_id
			ticketServiceSql.getTicketIdAndSchoolId(issueId, new Handler<Either<String, Ticket>>() {
				@Override
				public void handle(Either<String, Ticket> event) {
					if (event.isLeft()) {
						log.error("[Support] Error when calling service getTicketIdAndSchoolId : "
								+ event.left().getValue() + ". Unable to send timeline notification.");
					} else {
						final JsonObject ticket = event.right().getValue().toJsonObject();
						if (ticket == null || ticket.size() == 0) {
							log.error(
									"[Support] Error : ticket is null or empty. Unable to send timeline notification.");
							return;
						}

						final Number ticketId = ticket.getLong("id", -1L);
						String schooldId = ticket.getString("school_id", null);
						if (ticketId.longValue() == -1 || schooldId == null) {
							log.error(
									"[Support] Error : cannot get ticketId or schoolId. Unable to send timeline notification.");
							return;
						}

						// get local administrators
						userService.getLocalAdministrators(schooldId, new Handler<JsonArray>() {
							@Override
							public void handle(JsonArray event) {
								if (event != null && event.size() > 0) {
									Set<String> recipientSet = new HashSet<>();
									for (Object o : event) {
										if (!(o instanceof JsonObject))
											continue;
										JsonObject j = (JsonObject) o;
										String id = j.getString("id");
										recipientSet.add(id);
									}

									// the requier should be advised too
									if (!recipientSet.contains(ticket.getString("owner"))) {
										recipientSet.add(ticket.getString("owner"));
									}

									List<String> recipients = new ArrayList<>(recipientSet);
									if (!recipients.isEmpty()) {
										String notificationName;

										if (newStatusId.intValue() != oldStatusId
												&& newStatusId.intValue() == redmineResolvedStatusId.intValue()) {
											notificationName = "bugtracker-issue-resolved";
										} else if (newStatusId.intValue() != oldStatusId
												&& newStatusId.intValue() == redmineClosedStatusId.intValue()) {
											notificationName = "bugtracker-issue-closed";
										} else {
											notificationName = "bugtracker-issue-updated";
										}

										JsonObject params = new JsonObject();
										params.put("issueId", issueId).put("ticketId", ticketId);
										params.put("ticketUri", "/support#/ticket/" + ticketId);
										params.put("resourceUri", params.getString("ticketUri"));

										notification.notifyTimeline(null, "support." + notificationName, null,
												recipients, null, params);
										// Historization
										String additionnalInfoHisto = "";
										String locale = ticket.getString("locale");
										if (fLastEvent != null && fLastEvent.getJsonArray("details") != null) {
											if (fLastEvent.getJsonArray("details").size() > 0) {
												JsonArray details = fLastEvent.getJsonArray("details");
												// do not duplicate identical informations
												boolean attrFound = false;
												boolean attachmentFound = false;
												boolean otherFound = false;
												for (Object obj : details) {
													if (!(obj instanceof JsonObject))
														continue;
													JsonObject detail = (JsonObject) obj;
													switch (detail.getString("property")) {
													case "attr":
														if (!attrFound) {
															additionnalInfoHisto += I18n.getInstance().translate(
																	"support.ticket.histo.bug.tracker.attr",
																	I18n.DEFAULT_DOMAIN, locale);
															attrFound = true;
														}
														break;
													case "attachment":
														if (!attachmentFound) {
															additionnalInfoHisto += I18n.getInstance().translate(
																	"support.ticket.histo.bug.tracker.attachment",
																	I18n.DEFAULT_DOMAIN, locale);
															attachmentFound = true;
														}
														break;
													default:
														if (!otherFound) {
															additionnalInfoHisto += I18n.getInstance().translate(
																	"support.ticket.histo.bug.tracker.other",
																	I18n.DEFAULT_DOMAIN, locale);
															otherFound = true;
														}
														break;
													}
												}
												ticketServiceSql.createTicketHisto(ticket.getInteger("id").toString(),
														I18n.getInstance().translate(
																"support.ticket.histo.bug.tracker.updated",
																I18n.DEFAULT_DOMAIN, locale) + additionnalInfoHisto,
														newStatusId == 5l ? 4 : newStatusId >= 4l ? 2 : newStatusId.intValue(), null, TicketHisto.REMOTE_UPDATED,
														new Handler<Either<String, Void>>() {
															@Override
															public void handle(Either<String, Void> res) {
																if (res.isRight()) {
																	ticketServiceSql.updateEventCount(
																			ticket.getInteger("id").toString(),
																			new Handler<Either<String, Void>>() {
																				@Override
																				public void handle(
																						Either<String, Void> res) {
																					if (res.isLeft()) {
																						log.error(
																								"Error updating ticket (event_count) : "
																										+ res.left()
																												.getValue());
																					}
																				}
																			});
																} else {
																	log.error("Error creation historization : "
																			+ res.left().getValue());
																}
															}
														});
											}
										}

									}
								}
							}
						});
					}
				}
			});

//			}
		} catch (Exception e) {
			log.error("[Support] Error : unable to send timeline notification.", e);
		}
	}

	@Override
	public void getIssue(final Number issueId, final Handler<Either<String, Issue>> handler) {
		String path = "/issues/" + issueId + ".json?include=journals,attachments";
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + path) : path;

		httpClient.request(new RequestOptions()
						.setMethod(HttpMethod.GET)
						.setHeaders(new HeadersMultiMap()
								.add(HttpHeaders.HOST, redmineHost)
								.add(HEADER_REDMINE_API_KEY, redmineApiKey)
								.add(HttpHeaders.CONTENT_TYPE, "application/json")))
				.flatMap(HttpClientRequest::send)
				.onSuccess(resp -> resp.bodyHandler(data -> {
					JsonObject response = new JsonObject(data.toString());
						if (resp.statusCode() == 200)
						{
							Issue issue = new Issue(issueId.longValue(), response);
							JsonArray attachments = response.getJsonObject("issue", new JsonObject()).getJsonArray("attachments", new JsonArray());
							for(Object o : attachments)
							{
								if(!(o instanceof JsonObject)) continue;
								JsonObject att = (JsonObject) o;
								issue.attachments.add(new GridFSAttachment(att.getLong("id"), att.getString("filename"), null, att.getInteger("filesize")));
							}
							handler.handle(new Either.Right<String, Issue>(issue));
					} else {
						log.error("Error when getting a redmine ticket. Response status is " + resp.statusCode()
								+ " instead of 200.");
						log.error(response.toString());
							handler.handle(new Either.Left<String, Issue>(
								"support.error.comment.added.to.escalated.ticket.but.synchronization.failed"));
					}
				}))
				.onFailure(excep -> log.error("client error", excep));
	}

	/**
	 * @param attachmentUrl : attachment URL given by Redmine, e.g.
	 *                      "http://support.web-education.net/attachments/download/784/test_pj.png"
	 */
	private void downloadAttachment(final String attachmentUrl, final Handler<Buffer> handler) {
		String url = proxyIsDefined ? attachmentUrl
				: attachmentUrl.substring(attachmentUrl.indexOf(redmineHost) + redmineHost.length());

		httpClient.request(new RequestOptions()
						.setMethod(HttpMethod.GET)
						.setHeaders(new HeadersMultiMap()
								.add(HttpHeaders.HOST, redmineHost)
								.add(HEADER_REDMINE_API_KEY, redmineApiKey)
								.add(HttpHeaders.CONTENT_TYPE, "application/json")))
				.flatMap(HttpClientRequest::send)
				.onSuccess(resp -> resp.bodyHandler(data -> handler.handle(data)))
				.onFailure(excep -> log.error("client error", excep));
	}

	@Override
	public void commentIssue(Number issueId, Comment comment, final Handler<Either<String, Void>> handler) {

		this.updateIssue(issueId, comment, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse event) {
				event.exceptionHandler(excep -> log.error("client error", excep));
				event.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						if (event.statusCode() >= 200 && event.statusCode() < 300) {
							handler.handle(new Either.Right<String, Void>(null));
						} else {
							log.error("Error : could not update redmine issue to add comment. Response status is "
									+ event.statusCode() + " instead of 200.");
							log.error(buffer.toString());
							handler.handle(new Either.Left<String, Void>(
									"support.error.comment.has.not.been.added.to.escalated.ticket"));
						}
					}
				});
			}
		});

	}

	/**
	 * Not used in synchronous bugtrackers
	 */
	@Override
	public void updateTicketFromBugTracker(Message<JsonObject> message, Handler<Either<String, JsonObject>> handler) {
		handler.handle(new Either.Left<String, JsonObject>("Not implemented in synchronous mode"));
	}

	private void uploadDocuments(final Long issueId, Set<String> exists, List<Attachment> documents,
			final Handler<Either<String, Id<Issue, Long>>> handler) {
		Set<String> d = new HashSet<>();
		for (Attachment a : documents) {
			if (a.documentId != null && !exists.contains(a.documentId)) {
				d.add(a.documentId);
			}
		}
		final AtomicInteger count = new AtomicInteger(d.size());
		final AtomicBoolean uploadError = new AtomicBoolean(false);
		final JsonArray uploads = new JsonArray();
		final JsonArray bugTrackerAttachments = new JsonArray();
		for (final String documentId : d) {
			wksHelper.readDocument(documentId, new Handler<Document>() {
				@Override
				public void handle(final Document file) {
					final String filename = file.getDocument().getString("name");
					final String contentType = file.getDocument().getJsonObject("metadata").getString("content-type");
					final Long size = file.getDocument().getJsonObject("metadata").getLong("size");
					EscalationServiceRedmineImpl.this.uploadAttachment(file.getData(),
							new Handler<HttpClientResponse>() {
								@Override
								public void handle(final HttpClientResponse resp) {
									resp.exceptionHandler(excep -> log.error("client error", excep));
									resp.bodyHandler(new Handler<Buffer>() {
										@Override
										public void handle(final Buffer event) {
											if (resp.statusCode() != 201) {
												uploadError.set(true);
											} else {
												JsonObject response = new JsonObject(event.toString());
												String token = response.getJsonObject("upload").getString("token");
												String attachmentIdInRedmine = token.substring(0, token.indexOf('.'));

												JsonObject attachment = new JsonObject().put("token", token)
														.put("filename", filename).put("content_type", contentType);
												uploads.add(attachment);
												bugTrackerAttachments.add(Sql.parseId(attachmentIdInRedmine))
														.add(issueId).add("REDMINE").add(documentId).add(filename).add(size);
//												insertBugTrackerAttachment(attachmentIdInRedmine, issueId, documentId, filename, size);
											}
											if (count.decrementAndGet() <= 0) {
												if (uploads.size() > 0) {
													updateIssue(issueId, null, uploads,
															new Handler<HttpClientResponse>() {
																@Override
																public void handle(HttpClientResponse resp) {
																	resp.exceptionHandler(
																			excep -> log.error("client error", excep));
																	if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
																		insertBugTrackerAttachment(
																				bugTrackerAttachments,
																				new Handler<Either<String, JsonObject>>() {
																					@Override
																					public void handle(
																							Either<String, JsonObject> r) {
																						handler.handle(
																								new Either.Right<String, Id<Issue, Long>>(
																										new Id<Issue, Long>(issueId)));
																					}
																				});
																	} else {
																		handler.handle(
																				new Either.Left<String, Id<Issue, Long>>(
																						"upload.attachments.error : "
																								+ resp.statusMessage()));
																	}
																}
															});
												} else {
													if (uploadError.get()) {
														handler.handle(new Either.Left<String, Id<Issue, Long>>(
																"upload.attachments.error"));
													} else {
														handler.handle(
																new Either.Right<String, Id<Issue, Long>>(new Id<Issue, Long>(null)));
													}
												}
											}
										}
									});
								}
							});
				}
			});
		}
	}

	private void insertBugTrackerAttachment(JsonArray values, Handler<Either<String, JsonObject>> handler) {
		if (values == null || values.size() == 0 || values.size() % 6 != 0) {
			handler.handle(new Either.Left<String, JsonObject>("invalid.values"));
			return;
		}
		StringBuilder query = new StringBuilder("SELECT ");
		for (int i = 0; i < values.size(); i += 6) {
			query.append(" support.merge_attachment_bydoc(?,?,?,?,?,?), ");
		}
		sql.prepared(query.deleteCharAt(query.length() - 2).toString(), values,
				SqlResult.validRowsResultHandler(handler));
	}

	@Override
	public void syncAttachments(final String ticketId, final List<Attachment> attachments,
			final Handler<Either<String, Id<Issue, Long>>> handler) {
		getIssueId(ticketId, new Handler<Long>() {
			@Override
			public void handle(final Long issueId) {
				if (issueId != null) {
					String query = "SELECT a.document_id as attachmentId "
							+ "FROM support.bug_tracker_attachments AS a " + "WHERE a.issue_id = ? AND a.bugtracker = 'REDMINE'";
					sql.prepared(query, new JsonArray().add(issueId),
							SqlResult.validResultHandler(new Handler<Either<String, JsonArray>>() {
								@Override
								public void handle(Either<String, JsonArray> r) {
									if (r.isRight()) {
										Set<String> exists = new HashSet<>();
										for (Object o : r.right().getValue()) {
											if (!(o instanceof JsonObject))
												continue;
											exists.add(((JsonObject) o).getString("attachmentId"));
										}
										uploadDocuments(issueId, exists, attachments, handler);
									} else {
										handler.handle(new Either.Left<String, Id<Issue, Long>>(r.left().getValue()));
									}
								}
							}));
				} else {
					handler.handle(new Either.Right<String, Id<Issue, Long>>(null));
				}
			}
		});
	}

	private void getIssueId(String ticketId, final Handler<Long> handler) {
		String query = "SELECT id FROM support.bug_tracker_issues WHERE ticket_id = ? ";
		sql.prepared(query, new JsonArray().add(Sql.parseId(ticketId)),
				SqlResult.validUniqueResultHandler(new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> r) {
						if (r.isRight()) {
							handler.handle(r.right().getValue().getLong("id"));
						} else {
							handler.handle(null);
						}
					}
				}));
	}

	@Override
	public void refreshTicketFromBugTracker(Number issueId, final Handler<Either<String, Void>> handler) {
		// Not implemented
		handler.handle(new Either.Left<>("Method not implemented"));
	}
}
