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
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpHeaders;
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
		ZendeskIssue.escalationConf.fromJson(config.getJsonObject("zendesk-escalation-conf"));

		Long delayInMinutes = config.getLong("refresh-period", 30L);
		log.info("[Support] Data will be pulled from Zendesk every " + delayInMinutes + " minutes");
		final Long delay = TimeUnit.MILLISECONDS.convert(delayInMinutes, TimeUnit.MINUTES);

		ticketServiceSql.getLastIssuesUpdate(new Handler<Either<String, String>>()
		{
			@Override
			public void handle(Either<String, String> event)
			{
				if (event.isRight())
				{
					lastPullEpoch.set(parseDateToEpoch(event.right().getValue()));
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
				else
					log.error("[Support] Last pull from Zendesk error : " + event.left().getValue());
			}
		});

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
					createTicket(ticket, user, handler);
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

	private Future<Attachment> uploadAttachment(Attachment attachment)
	{
		Promise<Attachment> uploadPromise = Promise.promise();

		if(attachment == null || attachment.documentId == null)
			uploadPromise.fail("support.escalation.zendesk.error.upload.invalid");
		else
		{
			wksHelper.readDocument(attachment.documentId, new Handler<WorkspaceHelper.Document>()
			{
				@Override
				public void handle(final WorkspaceHelper.Document file)
				{
					try
					{
						final String filename = file.getDocument().getString("name");
						final String contentType = file.getDocument().getJsonObject("metadata").getString("content-type");
						final Long size = file.getDocument().getJsonObject("metadata").getLong("size");
						final Buffer data = file.getData();

						// This will be reused when inserting the issue attachment in postgres
						attachment.contentType = contentType;
						attachment.size = size.intValue();

						HttpClientRequest req = zendeskClient.post("/api/v2/uploads.json?filename=" + filename, new Handler<HttpClientResponse>()
						{
							@Override
							public void handle(HttpClientResponse response)
							{
								response.bodyHandler(new Handler<Buffer>()
								{
									@Override
									public void handle(Buffer data)
									{
										if(response.statusCode()  != 201)
										{
											log.error("[Support] Error : Attachment upload failed: " + data.toString());
											uploadPromise.fail("support.escalation.zendesk.error.upload.failure");
										}
										else
										{
											JsonObject upload = new JsonObject(data.toString()).getJsonObject("upload");
											attachment.bugTrackerToken = upload.getString("token");
											attachment.bugTrackerId = upload.getJsonObject("attachment").getLong("id");
											uploadPromise.complete(attachment);
										}
									}
								});
							}
						}).exceptionHandler(new Handler<Throwable>()
						{
							@Override
							public void handle(Throwable t)
							{
								log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
								uploadPromise.fail("support.escalation.zendesk.error.upload.request");
							}
						});

						req.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
						req.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length()));
						req.write(data).end();
					}
					catch (Exception e)
					{
						log.error("[Support] Error when processing response from readDocument", e);
						uploadPromise.fail("support.escalation.zendesk.error.upload.readerror");
					}

				}
			});
		}

		return uploadPromise.future();
	}

	private void createTicket(Ticket ticket, UserInfos user, Handler<Either<String, Issue>> handler)
	{
		Future<ZendeskIssue> future = ZendeskIssue.fromTicket(ticket, user);
		future.onSuccess(new Handler<ZendeskIssue>()
		{
			@Override
			public void handle(ZendeskIssue issue)
			{
				HttpClientRequest req = zendeskClient.post("/api/v2/tickets.json", new Handler<HttpClientResponse>()
				{
					@Override
					public void handle(HttpClientResponse response)
					{
						response.bodyHandler(new Handler<Buffer>()
						{
							@Override
							public void handle(Buffer data)
							{
								if(response.statusCode()  != 201)
								{
									log.error("[Support] Error : Zendesk ticket escalation failed: " + data.toString());
									handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.ticket.failure"));
								}
								else
								{
									JsonObject res = new JsonObject(data.toString());
									ZendeskIssue zIssue = new ZendeskIssue(res.getJsonObject("ticket"));

									zIssue.attachments = ticket.attachments;
									handler.handle(new Either.Right<String, Issue>(zIssue));
								}
							}
						});
					}
				}).exceptionHandler(new Handler<Throwable>()
				{
					@Override
					public void handle(Throwable t)
					{
						log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
						handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.ticket.request"));
					}
				});

				String data = new JsonObject().put("ticket", issue.toJson()).toString();
				req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				req.setChunked(true);
				req.write(data).end();
			}
		});
	}

	/**
	 * Not used in synchronous bugtrackers
	 */
	@Override
	public void updateTicketFromBugTracker(Message<JsonObject> message, Handler<Either<String, JsonObject>> handler)
	{
		handler.handle(new Either.Left<String, JsonObject>("Not implemented in synchronous mode"));
	}

	@Override
	public void getIssue(final Number issueId, final Handler<Either<String, Issue>> handler)
	{
		HttpClientRequest req = zendeskClient.get("/api/v2/tickets/" + issueId.longValue(), new Handler<HttpClientResponse>()
		{
			@Override
			public void handle(HttpClientResponse response)
			{
				response.bodyHandler(new Handler<Buffer>()
				{
					@Override
					public void handle(Buffer data)
					{
						if(response.statusCode() != 200)
						{
							log.error("[Support] Error : Zendesk ticket find failed: " + data.toString());
							handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.ticket.find.failure"));
						}
						else
						{
							JsonObject issueRes = new JsonObject(data.toString()).getJsonObject("ticket");
							ZendeskIssue zIssue = new ZendeskIssue(issueRes);
							loadComments(zIssue).onFailure(new Handler<Throwable>()
							{
								@Override
								public void handle(Throwable t)
								{
									handler.handle(new Either.Left<String, Issue>(t.getMessage()));
								}
							}).onSuccess(new Handler<ZendeskIssue>()
							{
								@Override
								public void handle(ZendeskIssue loaded)
								{
									handler.handle(new Either.Right<String, Issue>(loaded));
								}
							});
						}
					}
				});
			}
		}).exceptionHandler(new Handler<Throwable>()
		{
			@Override
			public void handle(Throwable t)
			{
				log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
				handler.handle(new Either.Left<String, Issue>("support.escalation.zendesk.error.ticket.find.request"));
			}
		});

		req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		req.end();
	}

	public Future<ZendeskIssue> loadComments(ZendeskIssue issue)
	{
		Promise<ZendeskIssue> promise = Promise.promise();
		HttpClientRequest reqComments = zendeskClient.get("/api/v2/tickets/" + issue.id.get() + "/comments", new Handler<HttpClientResponse>()
		{
			@Override
			public void handle(HttpClientResponse response)
			{
				response.bodyHandler(new Handler<Buffer>()
				{
					@Override
					public void handle(Buffer data)
					{
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
							issue.comments = res.comments;
							promise.complete(issue);
						}
					}
				});
			}
		}).exceptionHandler(new Handler<Throwable>()
		{
			@Override
			public void handle(Throwable t)
			{
				log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
				promise.fail("support.escalation.zendesk.error.comments.request");
			}
		});

		reqComments.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
		reqComments.end();
		return promise.future();
	}

	@Override
	public void commentIssue(Number issueId, Comment comment, final Handler<Either<String, Void>> handler)
	{
		ZendeskComment zComment;
		if(comment instanceof ZendeskComment)
			zComment = (ZendeskComment) comment;
		else
			zComment = new ZendeskComment(comment);

		HttpClientRequest req = zendeskClient.put("/api/v2/tickets/" + issueId, new Handler<HttpClientResponse>()
		{
			@Override
			public void handle(HttpClientResponse response)
			{
				response.bodyHandler(new Handler<Buffer>()
				{
					@Override
					public void handle(Buffer data)
					{
						if(response.statusCode()  != 200)
						{
							log.error("[Support] Error : Zendesk ticket comment failed: " + data.toString());
							handler.handle(new Either.Left<String, Void>("support.escalation.zendesk.error.comment.failure"));
						}
						else
							handler.handle(new Either.Right<String, Void>(null));
					}
				});
			}
		}).exceptionHandler(new Handler<Throwable>()
		{
			@Override
			public void handle(Throwable t)
			{
				log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
				handler.handle(new Either.Left<String, Void>("support.escalation.zendesk.error.comment.request"));
			}
		});

		ZendeskIssue capsule = new ZendeskIssue(issueId.longValue());
		capsule.comment = zComment;

		String data = new JsonObject().put("ticket", capsule.toJson()).toString();
		req.putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-16");
		req.setChunked(true); // Without this, comments with too many accents fail despite the 200
		req.write(data).end();
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
							String uploadMessage = I18n.getInstance().translate("support.escalation.zendesk.comment.uploads", new Locale(ZendeskIssue.escalationConf.locale));
							ZendeskComment zComment = new ZendeskComment(uploadMessage);

							zComment.uploads = new ArrayList<String>();
							for(Attachment a : missingAttachments)
								zComment.uploads.add(a.bugTrackerToken);

							commentIssue(zIssue.id.get(), zComment, new Handler<Either<String, Void>>()
							{
								@Override
								public void handle(Either<String, Void> result)
								{
									if(result.isLeft())
										handler.handle(new Either.Left<String, Id<Issue, Long>>(result.left().getValue()));
									else
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
		// Add one second to avoid duplicating the last comment pulled
		long pullStart = lastPullEpoch.get() + 1;
		log.info("[Support] Info: Listing zendesk issues modified since " + pullStart);
		HttpClientRequest req = zendeskClient.get("/api/v2/incremental/tickets?include=comment_events&start_time=" + pullStart, new Handler<HttpClientResponse>()
		{
			@Override
			public void handle(HttpClientResponse response)
			{
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
											issuesUpdates.add(updateDatabaseIssue(issuesMap.get(existing.id.get()), existing.attachments, pullStart));

										CompositeFuture.all(issuesUpdates).onSuccess(new Handler<CompositeFuture>()
										{
											@Override
											public void handle(CompositeFuture allUploadsResult)
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
			}
		}).exceptionHandler(new Handler<Throwable>()
		{
			@Override
			public void handle(Throwable t)
			{
				log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
			}
		});

		req.putHeader(HttpHeaders.ACCEPT, "application/json");
		req.end();
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
									ZendeskStatus newStatus = issue.status;
									Long tId = new Long(issue.getTicketId().get());
									Long tStatus = new Long(newStatus.correspondingStatus.status());

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

		HttpClientRequest req = zendeskClient.get(attachment.content_url, new Handler<HttpClientResponse>()
		{
			@Override
			public void handle(HttpClientResponse resp)
			{
				resp.exceptionHandler(new Handler<Throwable>()
				{
					@Override
					public void handle(Throwable t)
					{
						log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
						downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
					}
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
							HttpClientRequest redirectReq = zendeskClient.getAbs(resp.getHeader(HttpHeaders.LOCATION), new Handler<HttpClientResponse>()
							{
								@Override
								public void handle(HttpClientResponse resp)
								{
									resp.exceptionHandler(new Handler<Throwable>()
									{
										@Override
										public void handle(Throwable t)
										{
											log.error("[Support] Error : exception raised by zendesk escalation httpClient", t);
											downloadPromise.fail("support.escalation.zendesk.error.attachment.request");
										}
									});
									resp.bodyHandler(new Handler<Buffer>()
									{
										@Override
										public void handle(Buffer redirectData)
										{
											if((resp.statusCode() >= 200 && resp.statusCode() < 300) == false)
												downloadPromise.fail("support.escalation.zendesk.error.attachment.redirect");
											else
											{
												storeAttachment(attachment, issueId, redirectData, downloadPromise);
											}
										}
									});
								}

							});
							redirectReq.end();
						}
						else if(resp.statusCode() >= 200)
							storeAttachment(attachment, issueId, data, downloadPromise);
						else
							downloadPromise.fail("support.escalation.zendesk.error.attachment.status");
					}
				});
			}
		});

		req.end();
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
						LinkedList<ZendeskComment> newComments = new LinkedList<ZendeskComment>();
						boolean newAttachments = false;
						for(ZendeskComment comment : issue.comments)
						{
							long postDate = parseDateToEpoch(comment.created);
							ZendeskVia via = comment.via; // Comments with an api via have been sent by the ENT (e.g. the first comment on each issue)
							if((postDate > lastUpdate || postDate == 0) && (via == null || "api".equals(via.channel) == false))
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
