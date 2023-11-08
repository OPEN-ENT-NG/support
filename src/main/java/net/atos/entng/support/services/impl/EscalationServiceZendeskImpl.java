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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TimeZone;
import java.util.Locale;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.UnsupportedEncodingException;

import io.vertx.core.http.*;
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
import org.entcore.common.json.JSONAble;
import org.entcore.common.remote.RemoteClient;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Server;
import fr.wseduc.webutils.http.Renders;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.Promise;
import io.vertx.core.Future;
import io.vertx.core.CompositeFuture;

import net.atos.entng.support.Ticket;
import net.atos.entng.support.Issue;
import net.atos.entng.support.Comment;
import net.atos.entng.support.Attachment;
import net.atos.entng.support.GridFSAttachment;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.enums.TicketHisto;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.zendesk.*;
import net.atos.entng.support.zendesk.ZendeskIssue.ZendeskStatus;

public class EscalationServiceZendeskImpl implements EscalationService {

	private static final Logger log = LoggerFactory.getLogger(EscalationServiceZendeskImpl.class);

	private final RemoteClient zendeskClient;

	private final WorkspaceHelper wksHelper;
	private final TimelineHelper notification;
	private final TicketServiceSql ticketServiceSql;
	private final UserService userService;
	private final Storage storage;

	private AtomicLong lastPullEpoch = new AtomicLong(0);
	private AtomicBoolean pullInProgess = new AtomicBoolean(false);

	private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	@Override
	public BugTracker getBugTrackerType()
	{
		return BugTracker.ZENDESK;
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
								if(pullInProgess.get() == false)
								{
									pullInProgess.set(true);
									pullDataAndUpdateIssues();
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
	public void escalateTicket(final HttpServerRequest request, Ticket ticket, final UserInfos user, Issue issue, final Handler<Either<String, Issue>> handler)
	{
		if(issue != null && issue.id.get() != null)
		{
			log.error("[Support] Zendesk issue " + issue.id.get() + " already exists for ticket " + ticket.id.get());
			handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.issue.exists"));
		}
		else
		{
			this.uploadAllAttachments(ticket.attachments).onFailure(new Handler<Throwable>()
			{
				@Override
				public void handle(Throwable t)
				{
					handler.handle(new Either.Left<String, Issue>(t.getMessage()));
				}

			}).onSuccess(new Handler<CompositeFuture>()
			{
				@Override
				public void handle(CompositeFuture allUploadsResult)
				{
					Future<ZendeskIssue> future = ZendeskIssue.fromTicket(ticket, user);
					future.onSuccess(new Handler<ZendeskIssue>()
					{
						@Override
						public void handle(ZendeskIssue issue)
						{
							createIssue(issue, new Handler<Either<String, ZendeskIssue>>()
							{
								@Override
								public void handle(Either<String, ZendeskIssue> res)
								{
									if(res.isLeft())
										handler.handle(new Either.Left<String, Issue>(res.left().getValue()));
									else
									{
										ZendeskIssue zIssue = res.right().getValue();
										zIssue.attachments = ticket.attachments;
										handler.handle(new Either.Right<String, Issue>(zIssue));
									}
								}
							});
						}
					});
				}
			}).onFailure(new Handler<Throwable>()
			{
				@Override
				public void handle(Throwable t)
				{
					handler.handle(new Either.Left<String, Issue>(t.getMessage()));
				}
			});
		}
	}

	private CompositeFuture uploadAllAttachments(List<Attachment> attachments)
	{
		List<Future> uploads = new ArrayList<Future>();
		for(Attachment a : attachments)
			uploads.add(this.uploadAttachment(a));

		return CompositeFuture.all(uploads);
	}

	private Future<Attachment> uploadAttachment(Attachment attachment) {
		Promise<Attachment> uploadPromise = Promise.promise();

		if(attachment == null || attachment.documentId == null) {
			uploadPromise.fail("support.escalation.zendesk.error.upload.invalid");
		} else {
			wksHelper.readDocument(attachment.documentId, file -> {
					try
					{
						final String filename = file.getDocument().getString("name");
						final String contentType = file.getDocument().getJsonObject("metadata").getString("content-type");
						final Long size = file.getDocument().getJsonObject("metadata").getLong("size");
						final Buffer data = file.getData();

						// This will be reused when inserting the issue attachment in postgres
						attachment.contentType = contentType;
						attachment.size = size.intValue();
						zendeskClient.request(new RequestOptions()
							.setMethod(HttpMethod.POST)
							.setURI("/api/v2/uploads.json?filename=" + filename)
							.addHeader(HttpHeaders.CONTENT_TYPE, contentType)
							.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length()))
						).flatMap(req -> req.send(data))
						.onSuccess(response -> {
							response.bodyHandler(data1 -> {
                if(response.statusCode()  != 201) {
                  log.error("[Support] Error : Attachment upload failed: " + data1.toString());
                  uploadPromise.fail("support.escalation.zendesk.error.upload.failure");
                } else {
                  JsonObject upload = new JsonObject(data1.toString()).getJsonObject("upload");
                  attachment.bugTrackerToken = upload.getString("token");
                  attachment.bugTrackerId = upload.getJsonObject("attachment").getLong("id");
                  uploadPromise.complete(attachment);
                }
              });
						})
						.onFailure(t -> {
							log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
							uploadPromise.fail("support.escalation.zendesk.error.upload.request");
						});
					}
					catch (Exception e)
					{
						log.error("[Support] Error when processing response from readDocument", e);
						uploadPromise.fail("support.escalation.zendesk.error.upload.readerror");
					}

				}
			);
		}

		return uploadPromise.future();
	}

	private void createIssue(ZendeskIssue issue, Handler<Either<String, ZendeskIssue>> handler)
	{
		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.POST)
			.setURI("/api/v2/tickets.json")
			.addHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
		.flatMap(request -> request.send(new JsonObject().put("ticket", issue.toJson()).encode()))
		.onSuccess(response -> {
			response.bodyHandler(new Handler<Buffer>()
			{
				@Override
				public void handle(Buffer data)
				{
					if(response.statusCode()  != 201)
					{
						log.error("[Support] Error : Zendesk ticket escalation failed: " + data.toString());
						handler.handle(new Either.Left<String, ZendeskIssue>("support.escalation.zendesk.error.ticket.failure"));
					}
					else
					{
						JsonObject res = new JsonObject(data.toString());
						ZendeskIssue zIssue = new ZendeskIssue(res.getJsonObject("ticket"));

            if(issue.comments != null)
						{
							Handler<Integer> nextSender = new Handler<Integer>()
							{
								@Override
								public void handle(Integer ix)
								{
									Handler<Integer> nextSender = this;
									if(ix == issue.comments.size())
										handler.handle(new Either.Right<>(zIssue));
									else
									{
										commentIssue(zIssue.id.get(), zIssue.status, issue.comments.get(ix))
											.onFailure(t -> handler.handle(new Either.Left<>(t.getMessage())))
											.onSuccess(v -> nextSender.handle(ix + 1));
									}
								}
							};

							nextSender.handle(0);
						}
						else
							handler.handle(new Either.Right<>(zIssue));
					}
				}
			});
		})
		.onFailure(t -> {
			log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			handler.handle(new Either.Left<String, ZendeskIssue>("support.escalation.zendesk.error.ticket.request"));
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

        class loadCommentsResponse implements JSONAble
        {
          public List<ZendeskComment> comments;
        }
        loadCommentsResponse res = new loadCommentsResponse();
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

	private Future<Void> commentIssue(Number issueId, ZendeskStatus updateStatus, Comment comment)
	{
		Promise<Void> promise = Promise.promise();
		ZendeskComment zComment;
		if(comment instanceof ZendeskComment)
			zComment = (ZendeskComment) comment;
		else
			zComment = new ZendeskComment(comment);

		ZendeskIssue capsule = new ZendeskIssue(issueId.longValue());
		capsule.status = updateStatus;
		capsule.comment = zComment;

		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.PUT)
			.setURI("/api/v2/tickets/" + issueId)
			.addHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-16"))
		.flatMap(req -> req.send(new JsonObject().put("ticket", capsule.toJson()).encode()))
		.onSuccess(response -> {
			response.bodyHandler(new Handler<Buffer>()
			{
				@Override
				public void handle(Buffer data)
				{
					if(response.statusCode()  != 200)
					{
						// Not sure we get a json back 100% of the time
						try
						{
							JsonObject jo = new JsonObject(data.toString());
							String errorCode = jo.getString("error");
							if("RecordInvalid".equals(errorCode))
							{
								createFollowUpIssue(issueId, comment.ownerName, res -> {
                  if(res.isLeft())
                    promise.fail(res.left().getValue());
                  else
                  {
                    ZendeskIssue followup = res.right().getValue();
                    commentIssue(followup.id.get(), updateStatus, comment)
                      .onFailure(promise::fail)
                      .onSuccess(promise::complete);
                  }
                });
								return;
							}
						}
						catch(Exception e)
						{
						}
						log.error("[Support] Error : Zendesk ticket comment failed: " + data.toString());
						promise.fail("support.escalation.zendesk.error.comment.failure");
					}
					else
						promise.complete(null);
				}
			});
		})
		.onFailure(t -> {
			log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			promise.fail("support.escalation.zendesk.error.comment.request");
		});
		return promise.future();
	}

	private void createFollowUpIssue(Number issueId, String ownerName, Handler<Either<String, ZendeskIssue>> handler)
	{
		getIssue(issueId, new Handler<Either<String, Issue>>()
		{
			@Override
			public void handle(Either<String, Issue> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, ZendeskIssue>(res.left().getValue()));
				else
				{
					ZendeskIssue oldIssue = (ZendeskIssue) res.right().getValue();
					ZendeskIssue newIssue = ZendeskIssue.followUp(oldIssue);

					createIssue(newIssue, new Handler<Either<String, ZendeskIssue>>()
					{
						@Override
						public void handle(Either<String, ZendeskIssue> res)
						{
							if(res.isLeft())
								handler.handle(res.left());
							else
							{
								ZendeskIssue createdIssue = (ZendeskIssue) res.right().getValue();
								ticketServiceSql.updateIssue(issueId, createdIssue, new Handler<Either<String, String>>()
								{
									@Override
									public void handle(Either<String, String> res)
									{
										if(res.isLeft())
											handler.handle(new Either.Left<String, ZendeskIssue>(res.left().getValue()));
										else
										{
											ticketServiceSql.getTicketIdAndSchoolId(createdIssue.id.get(), new Handler<Either<String, Ticket>>()
											{
												@Override
												public void handle(Either<String, Ticket> event)
												{
													if(event.isLeft())
														handler.handle(new Either.Left<String, ZendeskIssue>(event.left().getValue()));
													else
													{
														Ticket ticket = event.right().getValue();
														ticketServiceSql.createTicketHisto(
															ticket.id.get().toString(),
															I18n.getInstance().translate("support.ticket.histo.escalate.auto", I18n.DEFAULT_DOMAIN, ticket.locale),
															createdIssue.status.correspondingStatus.status(),
															null,
															TicketHisto.ESCALATION,
															new Handler<Either<String, Void>>()
															{
																@Override
																public void handle(Either<String, Void> res)
																{
																	if(res.isLeft())
																		handler.handle(new Either.Left<String, ZendeskIssue>(res.left().getValue()));
																	else
																		handler.handle(new Either.Right<String, ZendeskIssue>(createdIssue));
																}
															});
													}
												}
											});
										}
									}
								});
							}
						}
					});
				}
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
		zendeskClient.request(new RequestOptions()
			.setMethod(HttpMethod.GET)
			.setURI("/api/v2/incremental/tickets?include=comment_events&start_time=" + pullStart)
			.addHeader(HttpHeaders.ACCEPT, "application/json"))
		.flatMap(HttpClientRequest::send)
		.onSuccess(response -> {
			response.bodyHandler(new Handler<Buffer>()
			{
				@Override
				public void handle(Buffer data)
				{
					if(response.statusCode()  != 200)
					{
						log.error("[Support] Error : Zendesk pull failed: " + data.toString());
						log.info("[Support] Info: No zendesk issues to update");
						pullInProgess.set(false);
					}
					else
					{
						IncrementalZendeskPull izp = new IncrementalZendeskPull(new JsonObject(data.toString()));

						if(izp.tickets.size() == 0)
						{
							pullInProgess.set(false);
							return;
						}

						Long[] issueIds = new Long[izp.count.intValue()];
						Map<Long, ZendeskIssue> issuesMap = new HashMap<Long, ZendeskIssue>();
						for(int i = izp.tickets.size(); i-- > 0;)
						{
							issueIds[i] = izp.tickets.get(i).id.get();
							issuesMap.put(issueIds[i], izp.tickets.get(i));
						}

						ticketServiceSql.listExistingIssues(issueIds, new Handler<Either<String, List<Issue>>>()
						{
							@Override
							public void handle(Either<String, List<Issue>> listRes)
							{
								if(listRes.isLeft())
								{
									log.error("[Support] Error: Zendesk find existing issues failed: " + listRes.left().getValue());
									pullInProgess.set(false);
								}
								else
								{
									List<Issue> listResult = listRes.right().getValue();
									List<Future> issuesUpdates = new ArrayList<Future>(listResult.size());

									log.info("[Support] Info: Updating " + listResult.size() + " zendesk issues");
									for(Issue existing : listResult)
										issuesUpdates.add(updateDatabaseIssue(issuesMap.get(existing.id.get()), existing.attachments, finalPullStart));

									CompositeFuture.all(issuesUpdates).onSuccess(new Handler<CompositeFuture>()
									{
										@Override
										public void handle(CompositeFuture allUploadsResult)
										{
											Handler<Void> next = new Handler<Void>()
											{
												@Override
												public void handle(Void v)
												{
													lastPullEpoch.set(izp.end_time.longValue());
													if(Boolean.TRUE.equals(izp.end_of_stream) == false)
														pullDataAndUpdateIssues();
													else
													{
														log.info("[Support] Info: All zendesk issues are up to date");
														pullInProgess.set(false);
													}
												}
											};
											ticketServiceSql.setLastSynchroEpoch(izp.end_time).onFailure(new Handler<Throwable>()
											{
												@Override
												public void handle(Throwable t)
												{
													log.error("[Support] Error: Failed to save the last Zendesk synchro time");
													// Keep importing anyways
													next.handle(null);
												}
											}).onSuccess(next);
										}
									}).onFailure(new Handler<Throwable>()
									{
										@Override
										public void handle(Throwable t)
										{
											log.error("[Support] Error: failed to update zendesk issues: ", t);
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

	private Future<Void> updateTicketHisto(ZendeskIssue issue, ZendeskStatus oldStatus, long lastUpdate)
	{
		Promise<Void> promise = Promise.promise();

		// get school_id and ticket_id
		ticketServiceSql.getTicketIdAndSchoolId(issue.id.get(), new Handler<Either<String, Ticket>>()
		{
			@Override
			public void handle(Either<String, Ticket> event)
			{
				if (event.isLeft())
					promise.fail("[Support] Error when calling service getTicketIdAndSchoolId : " + event.left().getValue());
				else
				{
					final Ticket ticket = event.right().getValue();
					if (ticket.id.get() == null || ticket.schoolId == null)
						promise.fail("[Support] Error : cannot get ticketId or schoolId. Unable to send timeline notification.");
					else
					{
						Long tId = new Long(ticket.id.get());
						Long tStatus = new Long(issue.status.correspondingStatus.status());
						// Update the status
						ticketServiceSql.updateTicketIssueUpdateDateAndStatus(tId, issue.updated_at, tStatus, new Handler<Either<String, Void>>()
						{
							@Override
							public void handle(Either<String, Void> res)
							{
								if(res.isLeft())
									promise.fail(res.left().getValue());
								else
								{
									LinkedList<ZendeskComment> newComments = new LinkedList<ZendeskComment>();
									boolean newAttachments = false;
									for(ZendeskComment comment : issue.comments)
									{
										long postDate = parseDateToEpoch(comment.created);
										ZendeskVia via = comment.via; // Comments with an api via have been sent by the ENT (e.g. the first comment on each issue)
										if((postDate > lastUpdate || postDate == 0) && (via == null || via.isFromAPI() == false))
										{
											newComments.add(comment);
											if(comment.attachments != null && comment.attachments.size() > 0)
												newAttachments = true;
										}
									}

									String additionnalInfoHisto = "";
									if(oldStatus.equals(issue.status) == false)
										additionnalInfoHisto += I18n.getInstance().translate("support.ticket.histo.bug.tracker.attr", I18n.DEFAULT_DOMAIN, ticket.locale);
									if(newComments.size() > 0)
										additionnalInfoHisto += I18n.getInstance().translate("support.ticket.histo.bug.tracker.notes", I18n.DEFAULT_DOMAIN, ticket.locale);
									if(newAttachments == true)
										additionnalInfoHisto += I18n.getInstance().translate("support.ticket.histo.bug.tracker.attachment", I18n.DEFAULT_DOMAIN, ticket.locale);

									if("".equals(additionnalInfoHisto))
										promise.complete(null);// Nothing interesting happened
									else
									{
										String update = I18n.getInstance().translate("support.ticket.histo.bug.tracker.updated", I18n.DEFAULT_DOMAIN, ticket.locale);
										String updateEvent = update + additionnalInfoHisto;
										String tId = ticket.id.get().toString();
										ZendeskStatus newStatus = issue.status;
										int ticketStatus = newStatus.correspondingStatus.status();
										ticketServiceSql.createTicketHisto(tId, updateEvent, ticketStatus, null, TicketHisto.REMOTE_UPDATED, new Handler<Either<String, Void>>()
										{
											@Override
											public void handle(Either<String, Void> commentRes)
											{
												if(commentRes.isLeft())
													promise.fail(commentRes.left().getValue());
												else
												{
													notifyIssueChanged(issue, oldStatus, ticket);
													addAllCommentsToTicket(newComments, ticket, newStatus, promise);

													ticketServiceSql.updateEventCount(tId, new Handler<Either<String, Void>>()
													{
														@Override
														public void handle(Either<String, Void> countRes)
														{
															// Nothing to do
														}
													});
												}
											}
										});
									}
								}
							}
						});
					}
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
			ticketServiceSql.createTicketHisto(tId, comment.content, -1, null, TicketHisto.REMOTE_COMMENT, new Handler<Either<String, Void>>()
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
	 * Notify local administrators (of the ticket's school_id) that the Redmine
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

							if (ZendeskStatus.solved.equals(newStatus) && newStatus.equals(oldStatus) == false)
								notificationName = "bugtracker-issue-resolved";
							else if (ZendeskStatus.closed.equals(newStatus) && newStatus.equals(oldStatus) == false)
								notificationName = "bugtracker-issue-closed";
							else
								notificationName = "bugtracker-issue-updated";

							JsonObject params = new JsonObject();
							params.put("issueId", issue.id.get()).put("ticketId", ticket.id.get());
							params.put("ticketUri", "/support#/ticket/" + ticket.id.get());
							params.put("resourceUri", params.getString("ticketUri"));

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
}