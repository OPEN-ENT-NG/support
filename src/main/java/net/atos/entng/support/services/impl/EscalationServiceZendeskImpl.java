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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.Attachment;
import net.atos.entng.support.Comment;
import net.atos.entng.support.GridFSAttachment;
import net.atos.entng.support.Issue;
import net.atos.entng.support.Ticket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.TicketHisto;
import net.atos.entng.support.model.Event;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.zendesk.ZendeskAttachment;
import net.atos.entng.support.zendesk.ZendeskComment;
import net.atos.entng.support.zendesk.ZendeskEscalationConf;
import net.atos.entng.support.zendesk.ZendeskIssue;
import net.atos.entng.support.zendesk.ZendeskIssue.ZendeskStatus;
import net.atos.entng.support.zendesk.ZendeskVia;
import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.json.JSONAble;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.remote.RemoteClient;
import org.entcore.common.storage.Storage;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.Id;
import org.entcore.common.utils.StringUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class EscalationServiceZendeskImpl implements EscalationService {

	private static final Logger log = LoggerFactory.getLogger(EscalationServiceZendeskImpl.class);

	private final RemoteClient zendeskClient;

	private final WorkspaceHelper wksHelper;
	private final TimelineHelper notification;
	private final TicketServiceSql ticketServiceSql;
	private final UserService userService;
	private final Storage storage;

	private AtomicLong lastPullEpoch = new AtomicLong(0);

	// Sometimes the pull from zendesk may be blocked due to an error or long processing time
	// In this case, we skip one pull to give zendesk time to process everything
	// then we set this flag to true in order to force the next one
	private final AtomicBoolean shouldForceSync = new AtomicBoolean(false);

	// This flag is used to prevent multiple pull from zendesk at the same time
	private final AtomicBoolean pullInProgess = new AtomicBoolean(false);

	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	@Override
	public BugTracker getBugTrackerType()
	{
		return BugTracker.ZENDESK;
	}

    private static class UploadResult {
        final boolean success;
        final Ticket ticket;
        final UserInfos user;

        UploadResult(boolean success, Ticket ticket, UserInfos user) {
            this.success = success;
            this.ticket = ticket;
            this.user = user;
        }
    }

	private long parseDateToEpoch(String date)
	{
		if(date == null)
			return 0;
		try
		{
			return (df.parse(date).getTime() / 1000);
		}
		catch(ParseException e)
		{
			log.error("[Support] Zendesk date parsing error : " + e.getMessage());
			return 0;
		}
	}

	public EscalationServiceZendeskImpl(final Vertx vertx, final JsonObject config, final TicketServiceSql ts, final UserService us, Storage storage)
	{
		EventBus eb = Server.getEventBus(vertx);
		this.wksHelper = new WorkspaceHelper(eb, storage);
		this.notification = new TimelineHelper(vertx, eb, config);
		this.ticketServiceSql = ts;
		this.userService = us;
		this.storage = storage;
		this.df.setTimeZone(TimeZone.getTimeZone("UTC"));

		this.zendeskClient = new RemoteClient(vertx, config.getJsonObject("zendesk-remote"));
		ZendeskIssue.zendeskIssueTemplate.fromJson(config.getJsonObject("zendesk-issue-template"));
		ZendeskEscalationConf.getInstance().fromJson(config.getJsonObject("zendesk-escalation-conf"));

		Long delayInMinutes = config.getLong("zendesk-refresh-period", config.getLong("refresh-period", 30L));
		log.info("[Support] Data will be pulled from Zendesk every " + delayInMinutes + " minutes");
		final Long delay = TimeUnit.MILLISECONDS.convert(delayInMinutes, TimeUnit.MINUTES);

		if(delay != 0)
		{
			ticketServiceSql.getLastSynchroEpoch().onFailure(new Handler<Throwable>()
			{
				@Override
				public void handle(Throwable t)
				{
					log.error("[Support] Last pull from Zendesk error : " + t.getCause());
				}
			}).onSuccess(new Handler<Long>()
				{
					public void handle(Long lastSynchroEpoch)
					{
						lastPullEpoch.set(lastSynchroEpoch == null ? 0 : lastSynchroEpoch);
						log.info("[Support] Last pull from Zendesk : " + lastPullEpoch);
						vertx.setPeriodic(delay, new Handler<Long>()
						{
							@Override
							public void handle(Long timerId)
							{
								if (!pullInProgess.get() || shouldForceSync.get()) {
									// If pull is not in progress or a forced sync is scheduled
									// start the pull from Zendesk
									shouldForceSync.set(false);
									pullInProgess.set(true);
									pullDataAndUpdateIssues();
								} else {
									// In case of error, a pull from zendesk may be skipped
									// the next one will be forced
									shouldForceSync.set(true);
								}
							}
						});
					}
				});
		}
	}

	/**
	 * @inheritDoc
	 */
    @Override
    public void escalateTicket(final HttpServerRequest request, Ticket ticket, final UserInfos user, Issue issue,
                               final Handler<Either<String, Issue>> handler) {
        if (issue != null && issue.id.get() != null) {
            log.error("[Support] Zendesk issue " + issue.id.get() + " already exists for ticket " + ticket.id.get());
            handler.handle(new Either.Left<>("support.escalation.zendesk.error.issue.exists"));

            return;
        }

        this.uploadAllAttachments(ticket.attachments)
                .map(compositeFuture -> new UploadResult(true, ticket, user))
                .recover(error -> {
                    log.warn("[Support] Attachment upload failed for ticket " + ticket.id.get(), error);

                    return Future.succeededFuture(new UploadResult(false, ticket, user));
                }).compose(uploadResult -> {
                    // If an upload failed, we add a note to the ticket description, but we continue the escalation process
                    // so the support team can continue the normal processing of the ticket
                    if (!uploadResult.success) {
                        String note = "\n\n--- \nL'utilisateur a tenté d'ajouter des pièces jointes, mais le téléversement a échoué.\n";
                        note += "Veuillez vous réferer au ticket dans l'application Assistance ENT\n";
                        ticket.description = (ticket.description == null ? "" : ticket.description) + note;
                    }

                    return ZendeskIssue.fromTicket(uploadResult.ticket, uploadResult.user);
                }).compose(zendeskIssue -> {
                    Promise<ZendeskIssue> promise = Promise.promise();
                    createIssue(zendeskIssue, result -> {
                        if (result.isLeft()) {
                            promise.fail(result.left().getValue());
                        } else {
                            promise.complete(result.right().getValue());
                        }
                    });

                    return promise.future();
                }).onSuccess(finalZendeskIssue -> {
                    finalZendeskIssue.attachments = ticket.attachments;
                    handler.handle(new Either.Right<>(finalZendeskIssue));
                }).onFailure(error -> {
                    log.error("[Support] Failed to escalate ticket " + ticket.id.get(), error);
                    handler.handle(new Either.Left<>(error.getMessage()));
                });
    }

    private CompositeFuture uploadAllAttachments(List<Attachment> attachments) {
        List<Future<Attachment>> uploads = attachments.stream()
                .map(this::uploadAttachment)
                .collect(Collectors.toList());

        return Future.all(uploads);
    }

    private Future<Attachment> uploadAttachment(Attachment attachment) {
        if (attachment == null || attachment.documentId == null) {
            return Future.failedFuture("support.escalation.zendesk.error.upload.invalid");
        }

        Promise<WorkspaceHelper.Document> fileReadPromise = Promise.promise();
        wksHelper.readDocument(attachment.documentId, file -> {
            if (file == null) {
                log.error("[Support] Error: Attachment read from wksHelper was null or empty.");
                fileReadPromise.fail("support.escalation.zendesk.error.upload.empty.file");
            } else {
                fileReadPromise.complete(file);
            }
        });

        return fileReadPromise.future()
                .compose(file -> {
                    final JsonObject documentObject = file.getDocument();
                    final String filename = documentObject.getString("name", "");
                    final Buffer data = file.getData();

                    if (data == null || data.length() == 0) {
                        return Future.failedFuture("support.escalation.zendesk.error.upload.empty");
                    }
                    if (filename.isEmpty()) {
                        return Future.failedFuture("support.escalation.zendesk.error.upload.empty.filename");
                    }
                    return Future.succeededFuture(file);

                }).compose(validatedFile -> {
                    JsonObject documentObject = validatedFile.getDocument();
                    JsonObject metadata = documentObject.getJsonObject("metadata", new JsonObject());
                    String contentType = metadata.getString("content-type", "");
                    Buffer data = validatedFile.getData();

                    String filename = StringUtils.stripAccents(documentObject.getString("name"));
                    if (!filename.contains(".") && contentType.contains("/")) {
                        filename += "." + contentType.substring(contentType.indexOf("/") + 1);
                    } else if (!filename.contains(".")) {
                        filename += ".txt";
                    }

                    attachment.contentType = contentType;
                    attachment.size = metadata.getLong("size", 0L).intValue();

                    return zendeskClient.request(new RequestOptions()
                                    .setMethod(HttpMethod.POST)
                                    .setURI("/api/v2/uploads.json?filename=" + filename)
                                    .addHeader(HttpHeaders.CONTENT_TYPE, contentType))
                            .compose(req -> req.send(data))
                            .compose(response -> {
                                if (response.statusCode() != 201) {
                                    return response.body().compose(body ->
                                            Future.failedFuture(
                                                    "Status " + response.statusCode() + ": " + body.toString()));
                                }
                                return response.body().map(body -> body.toJsonObject().getJsonObject("upload"));
                            })
                            .map(uploadJson -> {
                                attachment.bugTrackerToken = uploadJson.getString("token");
                                attachment.bugTrackerId = uploadJson.getJsonObject("attachment").getLong("id");
                                return attachment;
                            }).onFailure(t -> log.error("[Support] Zendesk upload request failed", t));
                });
    }

    private void createIssue(ZendeskIssue issue, Handler<Either<String, ZendeskIssue>> handler) {
        zendeskClient.request(new RequestOptions()
                        .setMethod(HttpMethod.POST)
                        .setURI("/api/v2/tickets.json")
                        .addHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
                .flatMap(request -> request.send(new JsonObject().put("ticket", issue.toJson()).encode()))
                .onSuccess(response -> {
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer data) {
                            if (response.statusCode() != 201) {
                                log.error("[Support] Error : Zendesk ticket escalation failed: " + data.toString());
                                handler.handle(new Either.Left<String, ZendeskIssue>(
                                        "support.escalation.zendesk.error.ticket.failure"));
                            } else {
                                JsonObject res = new JsonObject(data.toString());
                                ZendeskIssue zIssue = new ZendeskIssue(res.getJsonObject("ticket"));

                                if (issue.comments != null) {
                                    Handler<Integer> nextSender = new Handler<Integer>() {
                                        @Override
                                        public void handle(Integer ix) {
                                            Handler<Integer> nextSender = this;
                                            if (ix == issue.comments.size())
                                                handler.handle(new Either.Right<>(zIssue));
                                            else {
                                                commentIssue(zIssue.id.get(), zIssue.status, issue.comments.get(ix))
                                                        .onFailure(
                                                                t -> handler.handle(new Either.Left<>(t.getMessage())))
                                                        .onSuccess(v -> nextSender.handle(ix + 1));
                                            }
                                        }
                                    };

                                    nextSender.handle(0);
                                } else {
                                    handler.handle(new Either.Right<>(zIssue));
                                }
                            }
                        }
                    });
                })
                .onFailure(t -> {
                    log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
                    handler.handle(
                            new Either.Left<String, ZendeskIssue>("support.escalation.zendesk.error.ticket.request"));
                });
    }

	/**
	 * Not used in synchronous bugtrackers
	 */
	@Override
	public void updateTicketFromBugTracker(Message<JsonObject> message, Handler<Either<String, JsonObject>> handler)
	{
		handler.handle(new Either.Left<>("Not implemented in synchronous mode"));
	}

	@Override
	public void getIssue(final Number issueId, final Handler<Either<String, Issue>> handler) {
		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/api/v2/tickets/" + issueId.longValue())
			.addHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
		.flatMap(HttpClientRequest::send)
		.onSuccess(response -> response.bodyHandler(data -> {
			if(response.statusCode() != 200)
			{
				log.error("[Support] Error : Zendesk ticket find failed: " + data.toString());
				handler.handle(new Either.Left<>("support.escalation.zendesk.error.ticket.find.failure"));
			} else {
				JsonObject issueRes = new JsonObject(data.toString()).getJsonObject("ticket");
				ZendeskIssue zIssue = new ZendeskIssue(issueRes);
				loadComments(zIssue)
					.onFailure(t -> handler.handle(new Either.Left<>(t.getMessage())))
					.onSuccess(loaded -> handler.handle(new Either.Right<>(loaded)));
			}
		}))
		.onFailure(t -> {
			log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.ticket.find.request"));
		});
	}

	private class LoadCommentsResponse implements JSONAble {
		public List<ZendeskComment> comments;
	}

	public Future<ZendeskIssue> loadComments(ZendeskIssue issue) {
		Promise<ZendeskIssue> promise = Promise.promise();
		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/api/v2/tickets/" + issue.id.get() + "/comments")
			.addHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
		.flatMap(HttpClientRequest::send)
		.onSuccess(response -> response.bodyHandler(data -> {
      if(response.statusCode() != 200)
      {
        log.error("[Support] Error : Zendesk comments find failed: " + data.toString());
        promise.fail("support.escalation.zendesk.error.comments.failure");
      }
      else
      {
        LoadCommentsResponse res = new LoadCommentsResponse();
        res.fromJson(new JsonObject(data.toString()));
        List<ZendeskComment> publicComments = new ArrayList<>(res.comments.size());
        for(ZendeskComment c : res.comments)
          if(!c.isPrivate())
            publicComments.add(c);

        issue.comments = publicComments;
        promise.complete(issue);
      }
    }))
		.onFailure(t -> {
			log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			promise.fail("support.escalation.zendesk.error.comments.request");
		});

		return promise.future();
	}

	@Override
	public void commentIssue(Number issueId, Comment comment, final Handler<Either<String, Void>> handler)
	{
		if(issueId == null)
			handler.handle(new Either.Left<String, Void>("support.escalation.zendesk.error.comment.invalid"));
		else
		{
			this.ticketServiceSql.getTicketFromIssueId(issueId.toString(), getBugTrackerType().name(), new Handler<Either<String, Ticket>>()
			{
				@Override
				public void handle(Either<String, Ticket> res)
				{
					if(res.isLeft())
						handler.handle(new Either.Left<String, Void>(res.left().getValue()));
					else
					{
						Ticket t = res.right().getValue();
						ZendeskStatus updatedStatus = ZendeskStatus.from(t.status);
						commentIssue(issueId, updatedStatus, comment).onFailure(new Handler<Throwable>()
						{
							@Override
							public void handle(Throwable t)
							{
								handler.handle(new Either.Left<String, Void>(t.getMessage()));
							}
						}).onSuccess(new Handler<Void>()
						{
							@Override
							public void handle(Void v)
							{
								handler.handle(new Either.Right<String, Void>(v));
							}
						});
					}
				}
			});
		}
	}

	// This method attempts to comment on an issue and send it to Zendesk API
	// The request may fail if the comment's status transition is invalid or if the comment is not well-formed
	private Future<Void> sendCommentToZendesk(Number issueId, ZendeskStatus status, Comment comment) {
		Promise<Void> promise = Promise.promise();

		ZendeskComment zComment;
		if (comment instanceof ZendeskComment)
			zComment = (ZendeskComment) comment;
		else {
			zComment = new ZendeskComment(comment);
		}

		// Prepare the Zendesk issue object with the provided issue ID and status
		ZendeskIssue capsule = new ZendeskIssue(issueId.longValue());
		capsule.status = status;
		capsule.comment = zComment;

		zendeskClient.request(new RequestOptions()
						.setMethod(HttpMethod.PUT)
						.setURI("/api/v2/tickets/" + issueId)
						.addHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-16"))
				.flatMap(req -> req.send(new JsonObject().put("ticket", capsule.toJson()).encode()))
				.onSuccess(response -> {
					response.bodyHandler(data -> {
						if (response.statusCode() != 200) {
							try {
								JsonObject jo = new JsonObject(data.toString());
								String errorCode = jo.getString("error");

								// If the RecordInvalid error occurs, it indicates that the comment's status transition is invalid
								// (for example, trying to transition a ticket from open to new).
								if ("RecordInvalid".equals(errorCode)) {
									promise.fail(new RuntimeException("RecordInvalid"));
								} else {
									promise.fail("support.escalation.zendesk.error.comment.failure");
								}
							} catch (Exception e) {
								promise.fail("support.escalation.zendesk.error.comment.failure");
							}
						} else {
							promise.complete();
						}
					});
				})
				.onFailure(promise::fail);

		return promise.future();
	}

    // This method attempts to comment an issue, and if it encounters a 'RecordInvalid' error,
    // it tries to create a follow-up issue and then apply the comment to that follow-up.
    private Future<Void> commentIssue(Number issueId, ZendeskStatus updateStatus, Comment comment) {
        Promise<Void> promise = Promise.promise();

        sendCommentToZendesk(issueId, updateStatus, comment).onComplete(res -> {
            if (res.succeeded()) {
                promise.complete();
            } else if ("RecordInvalid".equals(res.cause().getMessage())) {
                // If the error is 'RecordInvalid', we create a follow-up issue
                ZendeskComment zComment = null;
                String ownerName = null;

                if (comment != null) {
                    zComment = (comment instanceof ZendeskComment)
                            ? (ZendeskComment) comment
                            : new ZendeskComment(comment);
                    ownerName = comment.ownerName;
                }

                createFollowUpIssue(issueId, ownerName, zComment, followUpRes -> {
                    if (followUpRes.isLeft()) {
                        promise.fail(followUpRes.left().getValue());
                    } else {
                        // Followup created successfully with the new comment as its first comment
                        // No need to send the comment again since it's already the first comment
                        promise.complete();
                    }
                });
            } else {
                promise.fail(res.cause());
            }
        });

        return promise.future();
    }

    private void createFollowUpIssue(
            Number issueId,
            String ownerName,
            ZendeskComment newComment,
            Handler<Either<String, ZendeskIssue>> handler
    ) {
        getIssue(issueId, res -> {
            if (res.isLeft()) {
                handler.handle(new Either.Left<>(res.left().getValue()));
            } else {
                ZendeskIssue oldIssue = (ZendeskIssue) res.right().getValue();
                ZendeskIssue newIssue = ZendeskIssue.followUp(oldIssue, newComment);

                createIssue(newIssue, res3 -> {
                    if (res3.isLeft()) {
                        handler.handle(res3.left());
                    } else {
                        ZendeskIssue createdIssue = res3.right().getValue();
                        ticketServiceSql.updateIssue(issueId, createdIssue, res2 -> {
                            if (res2.isLeft()) {
                                handler.handle(new Either.Left<>(res2.left().getValue()));
                            } else {
                                ticketServiceSql.getTicketIdAndSchoolId(createdIssue.id.get(), event -> {
                                    if (event.isLeft()) {
                                        handler.handle(new Either.Left<>(event.left().getValue()));
                                    } else {
                                        Ticket ticket = event.right().getValue();
                                        ticketServiceSql.createTicketHisto(
                                                ticket.id.get().toString(),
                                                I18n.getInstance().translate(
                                                        "support.ticket.histo.escalate.auto",
                                                        I18n.DEFAULT_DOMAIN,
                                                        ticket.locale
                                                ),
                                                createdIssue.status.correspondingStatus.status(),
                                                null,
                                                TicketHisto.ESCALATION,
                                                res1 -> {
                                                    if (res1.isLeft()) {
                                                        handler.handle(new Either.Left<>(res1.left().getValue()));
                                                    } else {
                                                        handler.handle(new Either.Right<>(createdIssue));
                                                    }
                                                }
                                        );
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

	// In Zendesk attachments are linked to comments, so we need to create a "fake" comment with the new attachments
	@Override
	public void syncAttachments(final String ticketId, final List<Attachment> attachments, final Handler<Either<String, Id<Issue, Long>>> handler)
	{
		this.ticketServiceSql.getIssue(ticketId, new Handler<Either<String, Issue>>()
		{
			@Override
			public void handle(Either<String, Issue> result)
			{
				if(result.isLeft())
					handler.handle(new Either.Left<String, Id<Issue, Long>>(result.left().getValue()));
				else
				{
					ZendeskIssue zIssue = new ZendeskIssue(result.right().getValue());

					List<Attachment> missingAttachments = new ArrayList<Attachment>();
					for(Attachment newAtt : attachments)
					{
						boolean found = false;
						for(Attachment oldAtt : zIssue.attachments)
						{
							if(newAtt.equals(oldAtt) == true)
							{
								found = true;
								break;
							}
						}
						if(found == false)
							missingAttachments.add(newAtt);
					}

					uploadAllAttachments(missingAttachments).onSuccess(new Handler<CompositeFuture>()
					{
						@Override
						public void handle(CompositeFuture allUploadsResult)
						{
							String uploadMessage = I18n.getInstance().translate("support.escalation.zendesk.comment.uploads", new Locale(ZendeskEscalationConf.getInstance().locale));
							ZendeskComment zComment = new ZendeskComment(uploadMessage);

							zComment.uploads = new ArrayList<String>();
							for(Attachment a : missingAttachments)
								zComment.uploads.add(a.bugTrackerToken);

							commentIssue(zIssue.id.get(), null, zComment).onFailure(new Handler<Throwable>()
							{
								@Override
								public void handle(Throwable t)
								{
									handler.handle(new Either.Left<String, Id<Issue, Long>>(t.getMessage()));
								}
							}).onSuccess(new Handler<Void>()
							{
								@Override
								public void handle(Void v)
								{
									handler.handle(new Either.Right<String, Id<Issue, Long>>(zIssue.id));
								}
							});
						}
					}).onFailure(new Handler<Throwable>()
					{
						@Override
						public void handle(Throwable t)
						{
							handler.handle(new Either.Left<String, Id<Issue, Long>>(t.getMessage()));
						}
					});
				}
			}
		});
	}

	// ======================================================= ZENDESK PULL =====================================================

	private static class IncrementalZendeskPull implements JSONAble
	{
		public Integer count;
		public Boolean end_of_stream;
		public Long end_time;
		public String next_page;
		public List<ZendeskIssue> tickets;

		public IncrementalZendeskPull(JsonObject o)
		{
			this.fromJson(o);
		}
	}

	private void pullDataAndUpdateIssues()
	{
		long currentEpochSeconds = new Date().getTime() / 1000;
		long pullStart = lastPullEpoch.get();

		// According to Zendesk doc:
		// "The start_time of the initial export is arbitrary. The time must be more than one minute in the past to avoid missing data.
		// To prevent race conditions, the ticket and ticket event export endpoints will not return data for the most recent minute."
		if(pullStart > currentEpochSeconds - 90)
			pullStart = currentEpochSeconds - 90;

		final long finalPullStart = pullStart;
		log.info("[Support] Info: Listing zendesk issues modified since " + pullStart);

		try {
			zendeskClient.request(new RequestOptions()
							.setMethod(HttpMethod.GET)
							.setURI("/api/v2/incremental/tickets?include=comment_events&start_time=" + pullStart)
							.addHeader(HttpHeaders.ACCEPT, "application/json"))
					.flatMap(HttpClientRequest::send)
					.onSuccess(response -> {
						response.bodyHandler(new Handler<Buffer>() {
							@Override
							public void handle(Buffer data) {
								if (response.statusCode() != 200) {
									log.error("[Support] Error : Zendesk pull failed: " + data.toString());
									pullInProgess.set(false);
								} else {
									IncrementalZendeskPull izp = new IncrementalZendeskPull(
											new JsonObject(data.toString()));

									if (izp.tickets.size() == 0) {
										pullInProgess.set(false);
										return;
									}

									Long[] issueIds = new Long[izp.count.intValue()];
									Map<Long, ZendeskIssue> issuesMap = new HashMap<Long, ZendeskIssue>();
									for (int i = izp.tickets.size(); i-- > 0; ) {
										issueIds[i] = izp.tickets.get(i).id.get();
										issuesMap.put(issueIds[i], izp.tickets.get(i));
									}

									ticketServiceSql.listExistingIssues(issueIds,
											new Handler<Either<String, List<Issue>>>() {
												@Override
												public void handle(Either<String, List<Issue>> listRes) {
													if (listRes.isLeft()) {
														log.error(
																"[Support] Error: Zendesk find existing issues failed: "
																		+ listRes.left().getValue());
														pullInProgess.set(false);
													} else {
														List<Issue> listResult = listRes.right().getValue();
														List<Future> issuesUpdates = new ArrayList<Future>(
																listResult.size());

														log.info("[Support] Info: Updating " + listResult.size()
																+ " zendesk issues");
														for (Issue existing : listResult)
															issuesUpdates
																	.add(updateDatabaseIssue(
																			issuesMap.get(existing.id.get()),
																			existing.attachments, finalPullStart));

														CompositeFuture.all(issuesUpdates)
																.onSuccess(new Handler<CompositeFuture>() {
																	@Override
																	public void handle(
																			CompositeFuture allUploadsResult) {
																		Handler<Void> next = new Handler<Void>() {
																			@Override
																			public void handle(Void v) {
																				lastPullEpoch
																						.set(izp.end_time.longValue());
																				if (!Boolean.TRUE
																						.equals(izp.end_of_stream))
																					pullDataAndUpdateIssues();
																				else {
																					log.info(
																							"[Support] Info: All zendesk issues are up to date");
																					pullInProgess.set(false);
																				}
																			}
																		};
																		ticketServiceSql
																				.setLastSynchroEpoch(izp.end_time)
																				.onFailure(new Handler<Throwable>() {
																					@Override
																					public void handle(Throwable t) {
																						log.error(
																								"[Support] Error: Failed to save the last Zendesk synchro time");
																						// Keep importing anyways
																						next.handle(null);
																					}
																				}).onSuccess(next);
																	}
																}).onFailure(new Handler<Throwable>() {
																	@Override
																	public void handle(Throwable t) {
																		log.error(
																				"[Support] Error: failed to update zendesk issues: ",
																				t);
																		pullInProgess.set(false);
																	}
																});
													}
												}
											});
								}
							}
						});
					})
					.onFailure(t -> {
						log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
						pullInProgess.set(false);
					});
		} catch (Exception e) {
			// In the past the synchronisation has been blocked due to unexpected errors
			// To prevent such case, we catch errors and set the pull in progress flag to
			// false
			pullInProgess.set(false);
			log.error("[Support] Error : an unexpected error occurred during zendesk synchronisation");
		}
	}

	private Future<Void> updateDatabaseIssue(ZendeskIssue issue, List<Attachment> existingAttachments, long lastUpdate)
	{
		Promise<Void> promise = Promise.promise();
		List<ZendeskAttachment> newAtts = new ArrayList<ZendeskAttachment>();

		// Load comments
		this.loadComments(issue).onFailure(new Handler<Throwable>()
		{
			@Override
			public void handle(Throwable t)
			{
				promise.fail(t);
			}
		}).onSuccess(new Handler<ZendeskIssue>()
		{
			@Override
			public void handle(ZendeskIssue issue)
			{
				if(issue.comments != null)
					for(ZendeskComment comm : issue.comments)
						for(ZendeskAttachment commAtt : comm.attachments)
							if(existingAttachments.contains(commAtt) == false)
								newAtts.add(commAtt);

				// Download all missing attachments
				downloadAllAttachments(newAtts, issue.id).onFailure(new Handler<Throwable>()
				{
					@Override
					public void handle(Throwable t)
					{
						promise.fail(t);
					}
				}).onSuccess(new Handler<CompositeFuture>()
				{
					@Override
					public void handle(CompositeFuture res)
					{
						// Update the issue json in postgres
						ticketServiceSql.updateIssue(issue.id.get(), issue, new Handler<Either<String, String>>()
						{
							@Override
							public void handle(Either<String, String> updateStatus)
							{
								if(updateStatus.isLeft())
									promise.fail(updateStatus.left().getValue());
								else
								{
									ZendeskStatus oldStatus = ZendeskStatus.fromString(updateStatus.right().getValue());
									// Process the new comment & notify users
									updateTicketHisto(issue, oldStatus, lastUpdate).onFailure(new Handler<Throwable>()
									{
										@Override
										public void handle(Throwable t)
										{
											promise.fail(t);
										}
									}).onSuccess(new Handler<Void>()
									{
										@Override
										public void handle(Void v)
										{
											promise.complete(null);
										}
									});
								}
							}
						});
					}
				});
			}
		});
		return promise.future();
	}

	private CompositeFuture downloadAllAttachments(List<ZendeskAttachment> attachments, Id<Issue, Long> issueId)
	{
		List<Future> downloads = new ArrayList<Future>();
		for(ZendeskAttachment a : attachments)
			downloads.add(this.downloadAttachment(a, issueId));

		return CompositeFuture.all(downloads);
	}

	private Future<Attachment> downloadAttachment(final ZendeskAttachment attachment, Id<Issue, Long> issueId)
	{
		Promise<Attachment> downloadPromise = Promise.promise();
		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI(attachment.content_url))
		.flatMap(HttpClientRequest::send)
		.onFailure(t -> {
			log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
		})
		.onSuccess(resp -> {
			resp.exceptionHandler(t -> {
        log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
        downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
      });
			resp.bodyHandler(new Handler<Buffer>()
			{
				@Override
				public void handle(Buffer data)
				{
					if(resp.statusCode() >= 400)
						downloadPromise.fail("support.escalation.zendesk.error.attachment.download");
					else if(resp.statusCode() >= 300)
					{
						zendeskClient.request(new RequestOptions()
							.setMethod(HttpMethod.GET)
							.setAbsoluteURI(resp.getHeader(HttpHeaders.LOCATION)))
						.flatMap(HttpClientRequest::send)
						.onFailure(t -> {
							log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
							downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
						})
						.onSuccess(resp -> {
							resp.exceptionHandler(t -> {
                log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
                downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
              });
							resp.bodyHandler(redirectData -> {
                if((resp.statusCode() >= 200 && resp.statusCode() < 300) == false)
                  downloadPromise.fail("support.escalation.zendesk.error.attachment.redirect");
                else
                {
                  storeAttachment(attachment, issueId, redirectData, downloadPromise);
                }
              });
						});
					}
					else if(resp.statusCode() >= 200)
						storeAttachment(attachment, issueId, data, downloadPromise);
					else
						downloadPromise.fail("support.escalation.zendesk.error.attachment.status");
				}
			});
		});
		return downloadPromise.future();
	}

	private void storeAttachment(final ZendeskAttachment attachment, Id<Issue, Long> issueId, Buffer data, Promise downloadPromise)
	{
		// store attachment
		storage.writeBuffer(data, attachment.contentType, attachment.name, new Handler<JsonObject>()
		{
			@Override
			public void handle(JsonObject attachmentMetaData)
			{
				/*
				 * Response example from gridfsWriteBuffer :
				 * {"_id":"f62f5dac-b32b-4cb8-b70a-1016885f37ec","status":"ok","metadata":{
				 * "content-type":"image/png","filename":"test_pj.png","size":118639}}
				 */
				if("ok".equals(attachmentMetaData.getString("status")) == false)
					downloadPromise.fail(attachmentMetaData.getString("message", attachmentMetaData.getString("error", "support.escalation.zendesk.error.attachment.storage")));
				else
				{
					JsonObject md = attachmentMetaData.getJsonObject("metadata");
					Attachment att = new GridFSAttachment(attachment.bugTrackerId, md.getString("filename"), md.getString("content-type"), md.getInteger("size"), attachmentMetaData.getString("_id"));

					// store attachment's metadata in postgresql
					ticketServiceSql.insertIssueAttachment(issueId, att, new Handler<Either<String, Void>>()
					{
						@Override
						public void handle(Either<String, Void> event)
						{
							if (event.isLeft())
								downloadPromise.fail("support.escalation.zendesk.error.attachment.metadata");
							else
								downloadPromise.complete(att);
						}
					});
				}
			}
		});
	}

    private Future<Void> updateTicketHisto(ZendeskIssue issue, ZendeskStatus oldStatus, long lastUpdate) {
        Promise<Void> promise = Promise.promise();

        // get school_id and ticket_id
        ticketServiceSql.getTicketIdAndSchoolId(issue.id.get(), event -> {
            if (event.isLeft()) {
                promise.fail(
                        "[Support] Error when calling service getTicketIdAndSchoolId : " + event.left().getValue()
                );
            } else {
                final Ticket ticket = event.right().getValue();
                if (ticket.id.get() == null || ticket.schoolId == null) {
                    promise.fail(
                            "[Support] Error : cannot get ticketId or schoolId. Unable to send timeline notification."
                    );
                } else {
                    Long tId = new Long(ticket.id.get());
                    Long tStatus = new Long(issue.status.correspondingStatus.status());
                    // Update the status
                    ticketServiceSql.updateTicketIssueUpdateDateAndStatus(tId, issue.updated_at, tStatus, res -> {
                        if (res.isLeft()) {
                            promise.fail(res.left().getValue());
                        } else {
                            // List used to store new comments to be added to ticket history
                            LinkedList<ZendeskComment> newComments = new LinkedList<>();
                            boolean newAttachments = false;
                            for (ZendeskComment comment : issue.comments) {
                                long postDate = parseDateToEpoch(comment.created);

                                // Comments with an api via may have been sent by the ENT (e.g. the first comment on each issue)
                                // But then could be sent by Zendesk too (e.g. when bulk updating tickets form Zendesk)
                                ZendeskVia via = comment.via; // Comments with an api via have been sent by the ENT (e.g. the first comment on each issue)

                                // To prevent skipping comments sent by Zendesk we check if the comment is from the ENT
                                // Comments containing the string "Auteur :" at the first line are sent by the ENT
                                boolean isFromENT =
                                        comment.content != null &&
                                                comment.content.contains("<br>") &&
                                                comment.content.split("<br>", 2)[0].contains("Auteur :");

                                // Skip comments from ENT
                                if (isFromENT) {
                                    continue;
                                }

                                if (via != null && via.isFromAPI()) {
                                    continue;
                                }

                                // We check if the comment is new, if so we add it to the linked list
                                if (postDate > lastUpdate || postDate == 0) {
                                    newComments.add(comment);
                                    if (comment.attachments != null && !comment.attachments.isEmpty()) {
                                        newAttachments = true;
                                    }
                                }
                            }

                            String additionnalInfoHisto = "";
                            if (!oldStatus.equals(issue.status)) {
                                additionnalInfoHisto += I18n.getInstance().translate(
                                        "support.ticket.histo.bug.tracker.attr",
                                        I18n.DEFAULT_DOMAIN,
                                        ticket.locale
                                );
                            }
                            if (!newComments.isEmpty()) {
                                additionnalInfoHisto += I18n.getInstance().translate(
                                        "support.ticket.histo.bug.tracker.notes",
                                        I18n.DEFAULT_DOMAIN,
                                        ticket.locale
                                );
                            }
                            if (newAttachments) {
                                additionnalInfoHisto += I18n.getInstance().translate(
                                        "support.ticket.histo.bug.tracker.attachment",
                                        I18n.DEFAULT_DOMAIN,
                                        ticket.locale
                                );
                            }

                            if ("".equals(additionnalInfoHisto)) {
                                promise.complete(null); // Nothing interesting happened
                            } else {
                                String update = I18n.getInstance().translate(
                                        "support.ticket.histo.bug.tracker.updated",
                                        I18n.DEFAULT_DOMAIN,
                                        ticket.locale
                                );
                                String updateEvent = update + additionnalInfoHisto;
                                String tId1 = ticket.id.get().toString();
                                ZendeskStatus newStatus = issue.status;
                                int ticketStatus = newStatus.correspondingStatus.status();
                                ticketServiceSql.createTicketHisto(
                                        tId1,
                                        updateEvent,
                                        ticketStatus,
                                        null,
                                        TicketHisto.REMOTE_UPDATED,
                                        commentRes -> {
                                            if (commentRes.isLeft()) {
                                                promise.fail(commentRes.left().getValue());
                                            } else {
                                                notifyIssueChanged(issue, oldStatus, ticket);
                                                addAllCommentsToTicket(newComments, ticket, newStatus, promise);

                                                ticketServiceSql.updateEventCount(tId1, countRes -> {
                                                    // Nothing to do
                                                });
                                            }
                                        }
                                );
                            }
                        }
                    });
                }
            }
        });

        return promise.future();
    }

	private void addAllCommentsToTicket(LinkedList<ZendeskComment> comments, Ticket ticket, ZendeskStatus newStatus, Promise promise)
	{
		if(comments.size() == 0)
			promise.complete(null);
		else
		{
			addCommentToTicket(comments.pop(), ticket, newStatus).onFailure(new Handler<Throwable>()
			{
				@Override
				public void handle(Throwable t)
				{
					promise.fail(t);
				}
			}).onSuccess(new Handler<Void>()
			{
				@Override
				public void handle(Void v)
				{
					addAllCommentsToTicket(comments, ticket, newStatus, promise);
				}
			});
		}
	}

	private Future<Void> addCommentToTicket(ZendeskComment comment, Ticket ticket, ZendeskStatus newStatus)
	{
		Promise<Void> promise = Promise.promise();
		if (comment == null)
			promise.complete(null);
		else
		{
			String tId = ticket.id.get().toString();
			ticketServiceSql.createTicketHistoZendesk(tId, comment, -1, null, TicketHisto.REMOTE_COMMENT, new Handler<Either<String, Void>>()
			{
				@Override
				public void handle(Either<String, Void> eventRes)
				{
					if (eventRes.isLeft())
						promise.fail(eventRes.left().getValue());
					else
						promise.complete(null);
				}
			});
		}
		return promise.future();
	}

	/*
	 * Notify local administrators (of the ticket's school_id) that the Zendesk
	 * issue's status has been changed to "resolved" or "closed"
	 */
	private void notifyIssueChanged(ZendeskIssue issue, ZendeskStatus oldStatus, Ticket ticket)
	{
		try
		{
			ZendeskStatus newStatus = issue.status;

			// get local administrators
			userService.getLocalAdministrators(ticket.schoolId, new Handler<JsonArray>()
			{
				@Override
				public void handle(JsonArray event)
				{
					if (event != null && event.size() > 0)
					{
						Set<String> recipientSet = new HashSet<>();
						for (Object o : event) // Du bon kode
						{
							if (!(o instanceof JsonObject))
								continue;
							JsonObject j = (JsonObject) o;
							String id = j.getString("id");
							recipientSet.add(id);
						}

						// the requier should be advised too
						if (!recipientSet.contains(ticket.ownerId))
							recipientSet.add(ticket.ownerId);

						List<String> recipients = new ArrayList<>(recipientSet);
						if (!recipients.isEmpty())
						{
							String notificationName;

							if (ZendeskStatus.solved.equals(newStatus) && !newStatus.equals(oldStatus)) {
								notificationName = "bugtracker-issue-resolved";
							} else if (ZendeskStatus.closed.equals(newStatus) && !newStatus.equals(oldStatus)) {
								notificationName = "bugtracker-issue-closed";
							} else {
								notificationName = "bugtracker-issue-updated";
							}

							JsonObject params = new JsonObject();
							params.put("issueId", issue.id.get()).put("ticketId", ticket.id.get());
							params.put("ticketUri", "/support#/ticket/" + ticket.id.get());
							params.put("resourceUri", params.getString("ticketUri"));

							String supportName = I18n.getInstance()
									.translate("support",
													I18n.DEFAULT_DOMAIN,
													ticket.locale);

							JsonObject pushNotif = new JsonObject()
									.put("title", "push-notif.support." + notificationName)
									.put("body", I18n.getInstance()
											.translate(
													"push-notif." + notificationName + ".body",
													I18n.DEFAULT_DOMAIN,
													ticket.locale,
													//Requière l'intégration d'un nouveau endpoint de zendesk pour récupérer les audits du ticket
													//afin d'extraire le nom de la dernière personne ayant modifiée
													String.valueOf(ticket.id.get()),
													issue.id.get().toString()
											));
							params.put("pushNotif", pushNotif);

							log.info("Sending notification to " + String.valueOf(ticket.id.get()));

							notification.notifyTimeline(null, "support." + notificationName, null, recipients, null, params);
						}
					}
				}
			});
		}
		catch (Exception e)
		{
			log.error("[Support] Error : unable to send timeline notification.", e);
		}
	}

	@Override
	public void refreshTicketFromBugTracker(Number issueId, final Handler<Either<String, Void>> handler) {
		getIssue(issueId, issue -> {
			if (issue.isLeft()) {
				log.error("[Support] Error: Unable to fetch issue from Zendesk: " + issue.left().getValue());
				handler.handle(new Either.Left<>("Failed to fetch issue from Zendesk"));
			} else {
				ZendeskIssue zIssue = (ZendeskIssue) issue.right().getValue();

				updateTicketHistoRefresh(zIssue)
						.onFailure(t -> {
							log.error("[Support] Error: Failed to update ticket history for ticket " + zIssue.getTicketId().get(), t);
							handler.handle(new Either.Left<>("Failed to update ticket history"));
						})
						.onSuccess(v -> {
							handler.handle(new Either.Right<String, Void>(null));
						});
			}
		});
	}

	private Future<Void> updateTicketHistoRefresh(ZendeskIssue zendeskIssue) {
		Promise<Void> promise = Promise.promise();

		ticketServiceSql.getTicketIdAndSchoolId(zendeskIssue.id.get(), res -> {
			if (res.isLeft()) {
				String errorMessage = "[Support] Error: Unable to fetch ticket ID and school ID for issue " + zendeskIssue.id.get() + ": " + res.left().getValue();
				log.error("[Support] Error: Unable to fetch ticket ID and school ID for issue " + zendeskIssue.id.get() + ": " + res.left().getValue());
				promise.fail(errorMessage);
				return;
			}

			Ticket ticket = res.right().getValue();

			ticketServiceSql.getlistEvents(ticket.id.get().toString())
					.onFailure(t -> {
						String errorMessage = "[Support] Error: Unable to fetch ticket events for issue " + zendeskIssue.id.get() + ": " + t.getMessage();
						log.error(errorMessage);
						promise.fail(errorMessage);
					})
					.onSuccess(events -> {
						LinkedList<ZendeskComment> newComments = new LinkedList<>();
						for (ZendeskComment comment : zendeskIssue.comments) {
							long zendeskCommentDate = parseDateToEpoch(comment.created);

							// To prevent skipping comments sent by Zendesk we check if the comment is from the ENT
							// Comments containing the string "Auteur :" at the first line are sent by the ENT
							boolean isFromENT = comment.content != null && comment.content.contains("<br>")
									&& comment.content.split("<br>", 2)[0].contains("Auteur :");

							if (isFromENT) {
								continue; // Skip comments from ENT
							}

							boolean found = false;
							for (Event event : events) {
								// Parse event date and convert to epoch seconds
								long eventTimestamp = LocalDateTime.parse(event.getEventDate())
										.atZone(ZoneId.systemDefault())
										.toInstant()
										.toEpochMilli() / 1000;

								// Check if the comment date matches any event date
								// If it's the case, it means the comment has already been saved in the database
								if (zendeskCommentDate == eventTimestamp) {
									found = true;
									break;
								}
							}

							if (!found) {
								newComments.add(comment);
							}
						}

						if (!newComments.isEmpty()) {
							// Add all new comments to the ticket
							addAllCommentsToTicket(newComments, ticket, zendeskIssue.status, promise);
						} else {
							promise.complete(); // Complete the promise if no new comments
						}
					});
		});

		return promise.future();
	}
}