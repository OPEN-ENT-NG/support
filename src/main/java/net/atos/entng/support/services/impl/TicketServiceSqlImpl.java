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

import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;
import static org.entcore.common.sql.SqlResult.validResultHandler;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import fr.wseduc.webutils.http.Renders;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.EscalationStatus;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.enums.TicketHisto;
import net.atos.entng.support.helpers.DateHelper;
import net.atos.entng.support.services.TicketServiceSql;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import org.entcore.common.utils.Id;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.eventbus.Message;

import net.atos.entng.support.Ticket;
import net.atos.entng.support.Issue;
import net.atos.entng.support.Attachment;
import net.atos.entng.support.WorkspaceAttachment;
import net.atos.entng.support.Comment;

public class TicketServiceSqlImpl extends SqlCrudService implements TicketServiceSql {

	private final static String UPSERT_USER_QUERY = "SELECT support.merge_users(?,?)";
    protected static final Logger log = LoggerFactory.getLogger(Renders.class);
	private final List<String> ALLOWED_SORT_BY_COLUMN = new ArrayList<>(Arrays.asList("id","modified","status","category","owner","event_count","subject"));
	private final BugTracker bugTrackerType;
	private final Logger LOGGER = LoggerFactory.getLogger(TicketServiceSqlImpl.class);
	public TicketServiceSqlImpl(BugTracker bugTracker) {
		super("support", "tickets");
		bugTrackerType = bugTracker;
	}

	@Override
	public void createTicket(JsonObject ticket, JsonArray attachments, UserInfos user, String locale,
			Handler<Either<String, Ticket>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		// 1. Upsert user
		s.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Create ticket
		ticket.put("owner", user.getUserId());
        ticket.put("locale", locale);
		String returnedFields = "id, subject, school_id, status, created, modified, escalation_status, escalation_date, substring(description, 0, 101)  as short_desc";
		s.insert(resourceTable, ticket, returnedFields);

		this.insertAttachments(attachments, user, s, null);

		sql.transaction(s.build(), validUniqueResultHandler(1, toTicketHandler(handler)));
	}

	@Override
	public void updateTicket(String ticketId, JsonObject data, UserInfos user, Handler<Either<String, Ticket>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		// 1. Upsert user
		s.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Update ticket
		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.fieldNames()) {
			if( !"newComment".equals(attr) && !"newComments".equals(attr) && !"attachments".equals(attr) ) {
				sb.append(attr).append(" = ?, ");
				values.add(data.getValue(attr));
			}
		}
		values.add(parseId(ticketId));

		String updateTicketQuery = "UPDATE support.tickets" +
				" SET " + sb.toString() + "modified = timezone('UTC', NOW()) " +
				"WHERE id = ? RETURNING id, modified, subject, owner, school_id";
		s.prepared(updateTicketQuery, values);

		// 3. Insert comment(s)
		String comment = data.getString("newComment", null);
		JsonArray comments = data.getJsonArray("newComments", new JsonArray());
		String insertCommentQuery = "INSERT INTO support.comments (ticket_id, owner, content) VALUES(?, ?, ?)";
		if(comment != null && !comment.trim().isEmpty()) {
			JsonArray commentValues = new JsonArray();
			commentValues.add(parseId(ticketId))
				.add(user.getUserId())
				.add(comment);
			s.prepared(insertCommentQuery, commentValues);
		} else if ( comments.size() > 0 ) {
			for(Object o : comments) {
				String newComment = (String)o;
				String contentOfComment = newComment;
				String[] elem = newComment.split(Pattern.quote("|"));
				if (elem.length == JiraTicket.COMMENT_LENGTH) {
					contentOfComment = " " + elem[0] + "|" + "\n" + elem[1] + "|" + "\n" + elem[2] + "|" + "\n" + "\n" + elem[3];
				}
				JsonArray commentValues = new JsonArray();
				commentValues.add(parseId(ticketId))
						.add(user.getUserId())
						.add(contentOfComment);
				s.prepared(insertCommentQuery, commentValues);
			}
		}

		// 4. Insert attachments
		JsonArray attachments = data.getJsonArray("attachments", null);
		this.insertAttachments(attachments, user, s, ticketId);

		// Send queries to event bus
		sql.transaction(s.build(), validUniqueResultHandler(1, toTicketHandler(handler, attachments)));
	}


	/**
	 * Append query "insert attachments" to SqlStatementsBuilder s
	 *
	 * @param ticketId : must be null when creating a ticket, and supplied when updating a ticket
	 */
	private void insertAttachments(final JsonArray attachments,
			final UserInfos user, final SqlStatementsBuilder s, final String ticketId) {

		if(attachments != null && attachments.size() > 0) {
			StringBuilder query = new StringBuilder();
			query.append("INSERT INTO support.attachments(document_id, name, size, owner, ticket_id)")
				.append(" VALUES");

			JsonArray values = new JsonArray();
			for (Object a : attachments) {
				if(!(a instanceof JsonObject)) continue;
				JsonObject jo = (JsonObject) a;
				query.append("(?, ?, ?, ?, ");
				values.add(jo.getString("id"))
					.add(jo.getString("name"))
					.add(jo.getInteger("size"))
					.add(user.getUserId());

				if(ticketId == null){
					query.append("(SELECT currval('support.tickets_id_seq'))),");
				}
				else {
					query.append("?),");
					values.add(parseId(ticketId));
				}
			}
			// remove trailing comma
			query.deleteCharAt(query.length() - 1);

			s.prepared(query.toString(), values);
		}

	}


	@Override
	public void listTickets(UserInfos user, Integer page, List<String> statuses, List<String> applicants, String school_id,
							String sortBy, String order, Integer nbTicketsPerPage, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name, ")
			.append("i.content").append(bugTrackerType.getLastIssueUpdateFromPostgresqlJson()).append(" AS last_issue_update, ")
            .append(" substring(t.description, 0, 101)  as short_desc,")
			.append(" COUNT(*) OVER() AS total_results")
			.append(" FROM support.tickets AS t")
			.append(" INNER JOIN support.users AS u ON t.owner = u.id")
			.append(" LEFT JOIN support.bug_tracker_issues AS i ON t.id=i.ticket_id");

		boolean oneApplicant = false;
		String applicant;
		boolean applicantIsMe = true;
		if (applicants.size() == 1) {
			applicant = applicants.get(0);
			applicantIsMe = applicant.equals("ME");
			oneApplicant = true;
		}

		JsonArray values = new JsonArray();
		Function adminLocal = user.getFunctions().get(DefaultFunctions.ADMIN_LOCAL);
		if (adminLocal != null) {
			List<String> scopesList = adminLocal.getScope();
			if(scopesList != null && !scopesList.isEmpty()) {
				query.append(" WHERE ((t.school_id IN (");
				for (String scope : scopesList) {
					query.append("?,");
					values.add(scope);
				}
				query.deleteCharAt(query.length() - 1);
				query.append(")");

				if (oneApplicant) {
					query.append(" AND t.owner").append(applicantIsMe?"=":"!=").append("?");
					values.add(user.getUserId());
				}
				query.append(")");

				// Include tickets created by current user, and linked to a school where he is not local administrator
				if (!oneApplicant || applicantIsMe) {
					query.append(" OR t.owner = ?");
					values.add(user.getUserId());
				}
				query.append(")");
			}
		}
		else {
            query.append(" WHERE t.school_id IN ").append(Sql.listPrepared(user.getStructures())).append(" ");
            values.addAll(new JsonArray(user.getStructures()));
			if (oneApplicant) {
				query.append(" AND t.owner").append(applicantIsMe?"=":"!=").append("?");
				values.add(user.getUserId());
			}
        }

		if (statuses.size() > 0 && statuses.size() < 4) {
			query.append(" AND (t.status IN (");
			for (String status : statuses) {
				query.append("?,");
				values.add(status);
			}
			query.deleteCharAt(query.length() - 1);
			query.append("))");
		}

		if (!school_id.equals("*")) {
			query.append(" AND t.school_id = ?");
			values.add(school_id);
		}


		if(ALLOWED_SORT_BY_COLUMN.contains(sortBy)){
			query.append(" ORDER BY t.");
			query.append(sortBy);
		}

		if(order != null && (order.equals("ASC") || order.equals("DESC"))){
			query.append(" " + order);
		}else {
			String message = String.format("[Support@%s::listTickets] this order is not valid"
					, this.getClass().getSimpleName());
			LOGGER.error(String.format(message));
		}

		if (page > 0) {
			query.append(" LIMIT ?").append(" OFFSET ").append((page - 1) * nbTicketsPerPage);
			values.add(nbTicketsPerPage);
		}

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getTicket(UserInfos user, Integer id, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name, ")
				.append("i.content").append(bugTrackerType.getLastIssueUpdateFromPostgresqlJson()).append(" AS last_issue_update, ")
				.append(" substring(t.description, 0, 101)  as short_desc,")
				.append(" COUNT(*) OVER() AS total_results")
				.append(" FROM support.tickets AS t")
				.append(" INNER JOIN support.users AS u ON t.owner = u.id")
				.append(" LEFT JOIN support.bug_tracker_issues AS i ON t.id=i.ticket_id");

		JsonArray values = new JsonArray();
		Function adminLocal = user.getFunctions().get(DefaultFunctions.ADMIN_LOCAL);
		if (adminLocal != null) {
			List<String> scopesList = adminLocal.getScope();
			if(scopesList != null && !scopesList.isEmpty()) {
				query.append(" WHERE ((t.school_id IN (");
				for (String scope : scopesList) {
					query.append("?,");
					values.add(scope);
				}
				query.deleteCharAt(query.length() - 1);
				query.append("))");

				query.append(" OR t.owner = ?");
				values.add(user.getUserId());
				query.append(")");
			}
		}
		else {
			query.append(" WHERE t.school_id IN (?)");
			values.add(user.getStructures().get(0)); // SUPER_ADMIN, has only 1 structure.
		}

		query.append(" AND t.id = ?");
		values.add(id);

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listMyTickets(UserInfos user, Integer page, List<String> statuses, String school_id, String sortBy,
							  String order, Integer nbTicketsPerPage, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name, substring(t.description, 0, 100) AS short_desc")
				.append(", COUNT(*) OVER() AS total_results")
				.append(" FROM support.tickets AS t")
				.append(" INNER JOIN support.users AS u ON t.owner = u.id")
				.append(" WHERE t.owner = ?");
		JsonArray values = new JsonArray().add(user.getUserId());

		if (statuses.size() > 0 && statuses.size() < 4) {
			query.append(" AND t.status IN (");
			for (String status : statuses) {
				query.append("?,");
				values.add(status);
			}
			query.deleteCharAt(query.length() - 1);
			query.append(")");
		}

		if (!school_id.equals("*")) {
			query.append(" AND t.school_id = ?");
			values.add(school_id);
		}

		if(ALLOWED_SORT_BY_COLUMN.contains(sortBy)){
			query.append(" ORDER BY t.");
			query.append(sortBy);
		}

		if(order != null && (order.equals("ASC") || order.equals("DESC"))){
			query.append(" " + order);
		}else {
			String message = String.format("[Support@%s::listMyTickets] this order is not valid"
					, this.getClass().getSimpleName());
			LOGGER.error(String.format(message));
		}


		query.append(" LIMIT ?").append(" OFFSET ").append((page - 1) * nbTicketsPerPage);

		values.add(nbTicketsPerPage);

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getMyTicket(UserInfos user, Integer id, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name, substring(t.description, 0, 100) AS short_desc")
				.append(", COUNT(*) OVER() AS total_results")
				.append(" FROM support.tickets AS t")
				.append(" INNER JOIN support.users AS u ON t.owner = u.id")
				.append(" WHERE t.owner = ?");
		JsonArray values = new JsonArray().add(user.getUserId());

		query.append(" AND t.id = ?");
		values.add(id);

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getTicketIdAndSchoolId(final Number issueId, final Handler<Either<String, Ticket>> handler) {
		String query = "SELECT t.id, t.school_id, t.owner, t.locale, t.status FROM support.tickets AS t"
				+ " INNER JOIN support.bug_tracker_issues AS i ON t.id = i.ticket_id"
				+ " WHERE i.id = ? AND i.bugtracker = ?";
		JsonArray values = new JsonArray().add(issueId).add(bugTrackerType.name());

		sql.prepared(query.toString(), values, validUniqueResultHandler(toTicketHandler(handler)));
	}

	/**
	 * If escalation status is "not_done" or "failed", and ticket status is new or opened,
	 * update escalation status to "in_progress" and return the ticket with its attachments' ids and its comments.
	 *
	 * Else (escalation is not allowed) return null.
	 */
	@Override
	public void getTicketWithEscalation(String ticketId, Handler<Either<String, Ticket>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		// 1) WITH query to update status
		query.append("WITH updated_ticket AS (")
				.append(" UPDATE support.tickets")
				.append(" SET escalation_status = ?, escalation_date = timezone('UTC', NOW()), status = 2 ")
				.append(" WHERE id = ?");
		values.add(EscalationStatus.IN_PROGRESS.status())
				.add(parseId(ticketId));

		query.append(" AND escalation_status NOT IN (?, ?)");
		values.add(EscalationStatus.IN_PROGRESS.status())
				.add(EscalationStatus.SUCCESSFUL.status());

		query.append(" AND status NOT IN (?, ?)")
				.append(" RETURNING * )");
		values.add(TicketStatus.RESOLVED.status())
				.add(TicketStatus.CLOSED.status());

		query.append( escalateTicketInfoQuery("updated_ticket AS t", "") );

		sql.prepared(query.toString(), values, validUniqueResultHandler(toTicketHandler(handler)));
	}

	/**
	 * When no rows are selected, json_agg returns a JSON array whose objects' fields have null values.
	 * We use CASE to return an empty array instead.
	 * @return query to select ticket, attachments' ids and comments
	 */
	private String escalateTicketInfoQuery( String fromTable, String whereClause )  {
		return "SELECT t.id, t.status, t.subject, t.description, t.category, t.school_id,"
		+ " u.username AS owner_name, t.owner as owner_id, t.created,"
				+ " CASE WHEN COUNT(a.document_id) = 0 THEN '[]' ELSE json_agg(DISTINCT a.document_id) END AS attachments,"
				+ " CASE WHEN COUNT(a.name) = 0 THEN '[]' ELSE json_agg(DISTINCT a.name) END AS attachmentsNames,"
		+ " CASE WHEN COUNT(c.id) = 0 THEN '[]' "
		+ " ELSE json_agg(DISTINCT(date_trunc('second', c.created), c.id, c.content, v.username)::support.comment_tuple"
		+ " ORDER BY (date_trunc('second', c.created), c.id, c.content, v.username)::support.comment_tuple)"
		+ " END AS comments"
		+ " FROM " + fromTable
		+ " INNER JOIN support.users AS u ON t.owner = u.id"
		+ " LEFT JOIN support.attachments AS a ON t.id = a.ticket_id"
		+ " LEFT JOIN support.comments AS c ON t.id = c.ticket_id"
		+ " LEFT JOIN support.users AS v ON c.owner = v.id"
		+ " " + whereClause
		+ " GROUP BY t.id, t.status, t.subject, t.description, t.category, t.school_id, u.username,"
		+ " t.owner, t.created";
	}

	/**
	 * Get ticket in format usable in escalation service
	 * @param ticketId Id of the ticket to get
	 * @param handler Handler that will process the response
	 */
	public void getTicketForEscalationService( String ticketId, Handler<Either<String, Ticket>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append( escalateTicketInfoQuery("support.tickets AS t", "WHERE t.id = ?") );
		values.add(Long.valueOf(ticketId));

		sql.prepared(query.toString(), values, validUniqueResultHandler(toTicketHandler(handler)));

	}

	private Handler<Either<String, JsonObject>> toTicketHandler(Handler<Either<String, Ticket>> handler)
	{
		return this.toTicketHandler(handler, null);
	}

	private Handler<Either<String, JsonObject>> toTicketHandler(Handler<Either<String, Ticket>> handler, JsonArray additionalAttachments)
	{
		return new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> result)
			{
				if(result.isLeft() == true)
					handler.handle(new Either.Left<String, Ticket>(result.left().getValue()));
				else
				{
					JsonObject sqlTicket = result.right().getValue();

					Ticket ticket = new Ticket(sqlTicket.getInteger("id"));
					ticket.status = TicketStatus.fromStatus(sqlTicket.getInteger("status"));
					ticket.subject = sqlTicket.getString("subject");
					ticket.description = sqlTicket.getString("description");
					ticket.category = sqlTicket.getString("category");
					ticket.schoolId = sqlTicket.getString("school_id");
					ticket.ownerId = sqlTicket.getString("owner_id", sqlTicket.getString("owner"));
					ticket.ownerName = sqlTicket.getString("owner_name");
					ticket.created = sqlTicket.getString("created");
					ticket.modified = sqlTicket.getString("modified");
					ticket.escalationStatus = sqlTicket.getInteger("escalation_status");
					ticket.escalationDate = sqlTicket.getString("escalation_date");
					ticket.issueUpdateDate = sqlTicket.getString("issue_update_date");
					ticket.locale = sqlTicket.getString("locale");

					JsonArray attachments = new JsonArray(sqlTicket.getString("attachments", "[]"));
					JsonArray attachmentsNames = new JsonArray(sqlTicket.getString("attachmentsNames", sqlTicket.getString("attachmentsnames", "[]")));
					for(int i = 0; i < attachments.size(); ++i)
						ticket.attachments.add(new WorkspaceAttachment(null, attachmentsNames.getString(i), attachments.getString(i)));

					if(additionalAttachments != null)
						for(int i = 0; i < additionalAttachments.size(); ++i)
						{
							JsonObject att = additionalAttachments.getJsonObject(i);
							ticket.attachments.add(new WorkspaceAttachment(null, att.getString("name"), null, att.getInteger("size"), att.getString("id")));
						}

					JsonArray comments = new JsonArray(sqlTicket.getString("comments", "[]"));
					for(int i = 0; i < comments.size(); ++i)
					{
						JsonObject c = comments.getJsonObject(i);
						ticket.comments.add(new Comment(c.getLong("id"), c.getString("content"), c.getString("owner_name"), c.getString("created")));
					}

					handler.handle(new Either.Right<String, Ticket>(ticket));
				}
			}
		};
	}


	private void updateTicketAfterEscalation(String ticketId, EscalationStatus targetStatus,
			Issue issue, Number issueId, UserInfos user, Handler<Either<String, JsonObject>> handler) {

		// 1. Update escalation status
		String query = "UPDATE support.tickets"
				+ " SET escalation_status = ?, escalation_date = timezone('UTC', NOW())"
				+ " WHERE id = ?";

		JsonArray values = new JsonArray()
			.add(targetStatus.status())
			.add(parseId(ticketId));

		if(EscalationStatus.FAILED.equals(targetStatus) || EscalationStatus.NOT_DONE.equals(targetStatus)) {
			sql.prepared(query, values, validUniqueResultHandler(handler));
		}
		else {
			SqlStatementsBuilder statements = new SqlStatementsBuilder();
			statements.prepared(query, values);

			// 2. Upsert user
			statements.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

			// 3. Insert bug tracker issue in ENT, so that local administrators can see it
			String insertQuery = "INSERT INTO support.bug_tracker_issues(id, ticket_id, content, bugtracker, owner)"
					+ " VALUES(?, ?, ?::JSON, ?, ?)"
					+ " ON CONFLICT ON CONSTRAINT bug_tracker_issues_pkey"
					+ " DO UPDATE"
					+ " SET content = excluded.content";

			JsonArray insertValues = new JsonArray().add(issueId)
					.add(parseId(ticketId))
					.add(issue.getContent())
					.add(bugTrackerType.name())
					.add(user.getUserId());

			statements.prepared(insertQuery, insertValues);

			// 4. Insert attachment (document from workspace) metadata
			if(issue != null) {
				this.insertIssueAttachments(new Id<Issue, Number>(issueId), issue.attachments, statements);
			}

			sql.transaction(statements.build(), validUniqueResultHandler(1, handler));
		}

	}

	/**
	 * 	@inheritDoc
	 */
	@Override
	public void endSuccessfulEscalation(String ticketId, Issue issue, Number issueId,
			UserInfos user, Handler<Either<String, JsonObject>> handler) {

		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, issue, issueId, user, handler);
	}

	@Override
	public void endFailedEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.FAILED, null, null, user, handler);
	}

	/**
	 * End escalation in "In progress" state. Used for asynchronous bug trackers
	 */
	@Override
	public void endInProgressEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		JsonObject issue = new JsonObject()
			.put("issue",new JsonObject()
				.put("date",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new Date()))
				.put("id_ent",ticketId));

		Number issueId = 0;
		try {
			// use ticket id as issue id in database
			issueId = Integer.parseInt(ticketId);
		} catch (NumberFormatException e) {
			log.error("Invalid id_ent, saving issue with id 0");
		}
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, new Issue(issueId.longValue(), issue), issueId, user, handler);
	}

    @Override
    public void endInProgressEscalationAsync(String ticketId, UserInfos user, JsonObject issueJira, Handler<Either<String, JsonObject>> handler) {
		JsonObject issue = new JsonObject()
				.put(JiraTicket.ISSUE, new JsonObject()
						.put(JiraTicket.ID, issueJira.getString(JiraTicket.ID_JIRA_FIELD))
						.put(JiraTicket.STATUS, issueJira.getString(JiraTicket.STATUS_JIRA_FIELD))
						.put(JiraTicket.DATE, DateHelper.convertDateFormat())
						.put(JiraTicket.ID_ENT, ticketId));

		Number issueId = 0;
		try {
			// use ticket id as issue id in database
			issueId = Integer.parseInt(ticketId);
		} catch (NumberFormatException e) {
			String message = String.format("[Support@%s::endInProgressEscalationAsync] Support : Invalid id_ent, saving issue with id 0 : %s",
					this.getClass().getSimpleName(), e.getMessage());
			log.error(message);
		}
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, new Issue(issueId.longValue(), issue), issueId, user, handler);
    }

	@Override
	public void updateIssue(Number issueId, Issue content, Handler<Either<String, String>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		// WITH clause to RETURN previous status_id
		query.append("WITH old_issue AS (")
			.append(" SELECT content").append(bugTrackerType.getStatusIdFromPostgresqlJson()).append(" AS status_id")
			.append(" FROM support.bug_tracker_issues")
			.append(" WHERE id = ? AND bugtracker = ?)");
		values.add(issueId).add(bugTrackerType.name());

		query.append(" UPDATE support.bug_tracker_issues")
			.append(" SET id = ?, content = ?::JSON, modified = timezone('UTC', NOW())")
			.append(" WHERE id = ? AND bugtracker = ?")
			.append(" RETURNING (SELECT status_id FROM old_issue)");

		values.add(content.id.get()).add(content.getContent().toString())
			.add(issueId).add(bugTrackerType.name());

		sql.prepared(query.toString(), values, validUniqueResultHandler(new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, String>(res.left().getValue()));
				else
					handler.handle(new Either.Right<String, String>(res.right().getValue().getString("status_id", "-1")));
			}
		}));
	}

	@Override
	public void getLastIssuesUpdate(Handler<Either<String, String>> handler)
	{
		String updatedExtract = bugTrackerType.getLastIssueUpdateFromPostgresqlJson();
		String createdExtract = bugTrackerType.getIssueCreationFromPostgresqlJson();

		String query = "SELECT max(content"
				+ updatedExtract
				+ ") AS last_update FROM support.bug_tracker_issues"
				+ " WHERE bugtracker = '" + bugTrackerType.name() + "'";

		if(createdExtract != null)
		{
			// Dans le cas où la routine de mise à jour plante, mais qu'une issue a été créée entre le plantage et le redémarrage du serveur,
			// ceci évite de se baser sur la date de l'issue récemment créée et de perdre les mises à jour des autres tickets que la toutine
			// n'a pas pu répliquer.
			// TODO: Stocker explicitement une date de màj côté ENT pour aussi supporter les tickets créés avec plusieurs commentaires
			// dont la date d'enregistrement ferait que la date updated soit différente de created
			query += " AND content" + updatedExtract + " != content" + createdExtract;
		}

		sql.raw(query, validResultHandler(new Handler<Either<String, JsonArray>>()
		{
			@Override
			public void handle(Either<String, JsonArray> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, String>(res.left().getValue()));
				else
				{
					JsonObject r = res.right().getValue().getJsonObject(0);
					handler.handle(new Either.Right<String, String>(r != null ? r.getString("last_update") : null));
				}
			}
		}));
	}

	/**
	 * 	@inheritDoc
	 */
	@Override
	public void listExistingIssues(Number[] issueIds, Handler<Either<String, List<Issue>>> handler) {
		/* Return for instance :
			[ { "attachment_ids": "[]", "id": 2836 },
			  { "attachment_ids": "[931, 932, 933, 934, 935, 937, 936]", "id": 2876 } ]
		 */
		StringBuilder query = new StringBuilder("SELECT i.id,")
			.append(" i.content").append(bugTrackerType.getLastIssueUpdateFromPostgresqlJson()).append(" AS last_update, ")
			.append(" CASE WHEN COUNT(a.id) = 0 THEN '[]'")
			.append(" ELSE json_agg(a.id)")
			.append(" END AS attachment_ids");

		query.append(" FROM support.bug_tracker_issues AS i")
			.append(" LEFT JOIN support.bug_tracker_attachments AS a")
			.append(" ON a.issue_id = i.id AND a.bugtracker = i.bugtracker");

		query.append(" WHERE i.bugtracker = ? ");

		JsonArray values = new JsonArray();
		values.add(bugTrackerType.name());

		if(issueIds != null && issueIds.length>0) {
			query.append(" AND i.id IN (");
			for (Number id : issueIds) {
				query.append("?,");
				values.add(id);
			}
			query.deleteCharAt(query.length() - 1);
			query.append(")");
		}

		query.append(" GROUP BY i.id, last_update");
		sql.prepared(query.toString(), values, validResultHandler(new Handler<Either<String, JsonArray>>()
		{
			@Override
			public void handle(Either<String, JsonArray> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, List<Issue>>(res.left().getValue()));
				else
				{
					JsonArray resArr = res.right().getValue();
					List<Issue> existing = new ArrayList<Issue>(resArr.size());
					for(int i = 0; i < resArr.size(); ++i)
					{
						Issue e = new Issue(new Long(resArr.getJsonObject(i).getNumber("id").longValue()));
						e.lastUpdate = resArr.getJsonObject(i).getString("last_update");

						JsonArray attIds = new JsonArray(resArr.getJsonObject(i).getString("attachment_ids"));
						for(int j = 0; j < attIds.size(); ++j)
							e.attachments.add(new Attachment(new Long(attIds.getNumber(j).longValue()), null));
						existing.add(e);
					}
					handler.handle(new Either.Right<String, List<Issue>>(existing));
				}
			}	
		}));
	}

	@Override
	public void getIssue(String ticketId, Handler<Either<String, Issue>> handler) {
		/* Field "attachments" will contain for instance :
		 *  [{"id":931,"document_id":null,"gridfs_id":"13237cd7-9567-4810-a85e-39414093e3b5"},
			 {"id":932,"document_id":null,"gridfs_id":"17223f70-d9a8-4983-92b1-d867fc881d44"},
			 {"id":933,"document_id":"c7b27108-8715-40e1-a32f-e90828857c35","gridfs_id":null}]
		 */
		StringBuilder query = new StringBuilder("SELECT i.id, i.content, i.bugtracker,")
			.append(" CASE WHEN COUNT(a.id) = 0 THEN '[]'")
			.append(" ELSE json_agg((a.*))")
			.append(" END AS attachments")
			.append(" FROM support.bug_tracker_issues AS i")
			.append(" LEFT JOIN support.bug_tracker_attachments AS a ON i.id = a.issue_id AND i.bugtracker = a.bugtracker")
			.append(" WHERE i.ticket_id = ?")
			.append(" GROUP BY i.id, i.bugtracker");
		JsonArray values = new JsonArray().add(parseId(ticketId));

		sql.prepared(query.toString(), values, validUniqueResultHandler(this.toIssueHandler(handler)));
	}

	private Handler<Either<String, JsonObject>> toIssueHandler(Handler<Either<String, Issue>> handler)
	{
		return new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> result)
			{
				if(result.isLeft() == true)
					handler.handle(new Either.Left<String, Issue>(result.left().getValue()));
				else
				{
					JsonObject sqlIssue = result.right().getValue();
					Long id = sqlIssue.getLong("id");
					if(id == null)
					{
						handler.handle(new Either.Left<String, Issue>("No issue found"));
						return;
					}

					String content = sqlIssue.getString("content");
					Issue issue = new Issue(id, content == null ? null : new JsonObject(content));

					JsonArray attachments = new JsonArray(sqlIssue.getString("attachments", "[]"));
					for(int i = 0; i < attachments.size(); ++i)
					{
						JsonObject a = attachments.getJsonObject(i);
						issue.attachments.add(new Attachment(a.getLong("id"), a.getString("name"), null, a.getInteger("size"), a.getString("created"), a.getString("document_id"), a.getString("gridfs_id")));
					}

					handler.handle(new Either.Right<String, Issue>(issue));
				}
			}
		};
	}

	@Override
	public void getIssueAttachmentName(String gridfsId, Handler<Either<String, JsonObject>> handler) {
		String query = "SELECT name FROM support.bug_tracker_attachments WHERE gridfs_id = ?";
		JsonArray values = new JsonArray().add(gridfsId);
		sql.prepared(query, values, validUniqueResultHandler(handler));
	};

	@Override
	public void insertIssueAttachment(Id<Issue, ? extends Number> issueId, Attachment attachment, Handler<Either<String, Void>> handler)
	{
		List<Attachment> atts = new ArrayList<Attachment>();
		atts.add(attachment);
		this.insertIssueAttachments(issueId, atts, handler);
	}

	@Override
	public void insertIssueAttachments(Id<Issue, ? extends Number> issueId, List<Attachment> attachments, Handler<Either<String, Void>> handler)
	{
		SqlStatementsBuilder statements = new SqlStatementsBuilder();
		this.insertIssueAttachments(issueId, attachments, statements);
		sql.transaction(statements.build(), new Handler<Message<JsonObject>>()
		{
			@Override
			public void handle(Message<JsonObject> result)
			{
				if("ok".equals(result.body().getString("status")))
					handler.handle(new Either.Right<String, Void>(null));
				else
					handler.handle(new Either.Left<String, Void>(result.body().getString("error", result.body().getString("message"))));
			}
		});
	}

	private void insertIssueAttachments(Id<Issue, ? extends Number> issueId, List<Attachment> attachments, SqlStatementsBuilder statements)
	{
		if((issueId != null && issueId.get() != null) && attachments != null)
		{
			// Old "doc" I'm too scared to remove (:
			/*
			 * Example of "attachments" array with one attachment :
			 *
				"attachments":[
					{
						"id": 784,
						"filename": "test_pj.png",
						"filesize": 118639,
						"content_type": "image/png",
						"description": "descriptionpj",
						"content_url": "http: //support.web-education.net/attachments/download/784/test_pj.png"
					}
				]
			 */
			StringBuilder attachmentsQuery = new StringBuilder();
			attachmentsQuery.append("SELECT ");

			JsonArray attachmentsValues = new fr.wseduc.webutils.collections.JsonArray();

			for (Attachment attachment : attachments)
			{
				if(attachment.bugTrackerId == null)
					continue;
				Long attachmentIdInBugTracker = new Long(attachment.bugTrackerId);

				String docOrFsId = null;
				if(attachment.documentId != null)
				{
					attachmentsQuery.append("support.merge_attachment_bydoc(?, ?, ?, ?, ?, ?),");
					docOrFsId = attachment.documentId;
				}
				else if(attachment.fileSystemId != null)
				{
					attachmentsQuery.append("support.merge_attachment_bygridfs(?, ?, ?, ?, ?, ?),");
					docOrFsId = attachment.fileSystemId;
				}
				else
					continue;

				attachmentsValues.add(attachmentIdInBugTracker)
					.add(issueId.get())
					.add(bugTrackerType.name())
					.add(docOrFsId)
					.add(attachment.name)
					.add(attachment.size);
			}
			// remove trailing comma
			attachmentsQuery.deleteCharAt(attachmentsQuery.length() - 1);

			if(attachmentsValues.size() > 0)
				statements.prepared(attachmentsQuery.toString(), attachmentsValues);
		}
	}

    /**
     * Increase the event_count field of ticket table. It means an update has been done.
     * @param ticketId
     * @param handler
     */
    public void updateEventCount(String ticketId, Handler<Either<String, Void>> handler) {
        String query = "UPDATE support.tickets"
                + " SET event_count = event_count + 1 "
                + " WHERE id = ?";

        JsonArray values = new JsonArray()
                .add(parseId(ticketId));

        sql.prepared(query, values, validUniqueResultHandler(new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, Void>(res.left().getValue()));
				else
					handler.handle(new Either.Right<String ,Void>(null));
			}
		}));
    }

    /**
     * Updating mass modification of tickets status
     * @param newStatus : new status of tickets
     * @param idList : list of the ids that will be modified
     * @param handler
     */
    public void updateTicketStatus(Integer newStatus, List<Integer> idList, Handler<Either<String, JsonObject>> handler) {
        StringBuilder query = new StringBuilder();
        query.append("UPDATE support.tickets");
        query.append(" SET status = ?, event_count = event_count + 1 ");
        query.append(" WHERE id in ( ");

        JsonArray values = new JsonArray();
        values.add(newStatus);

        for (Integer id : idList) {
            query.append("?,");
            values.add(id);
        }
        query.deleteCharAt(query.length() - 1);
        query.append(")");

        sql.prepared(query.toString(), values, validUniqueResultHandler(handler));
    }

    /**
     *
     * @param ticketId : ticket id from which we want to list the history
     */
    public void listEvents(String ticketId, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT username, event, status, event_date, user_id, event_type FROM support.tickets_histo th " +
                    " left outer join support.users u on u.id = th.user_id " +
                    " WHERE ticket_id = ? ";
        JsonArray values = new JsonArray().add(parseId(ticketId));
        sql.prepared(query, values, validResultHandler(handler));
    }

    /**
     *
     * @param issueId : bug tracker number from which we want the linked ticket
     * @param handler
     */
    public void getTicketFromIssueId(String issueId, String bugtracker, Handler<Either<String, Ticket>> handler) {
        String query = "SELECT t.* " +
                " from support.tickets t" +
                " inner join support.bug_tracker_issues bti on bti.ticket_id = t.id" +
                " WHERE bti.id = ? AND bti.bugtracker = ?";
        JsonArray values = new JsonArray().add(parseId(issueId)).add(bugtracker);
        sql.prepared(query, values, validUniqueResultHandler(toTicketHandler(handler)));
    }

    /**
     * Updates the ticket table, sets the issueUpdateDate field to the last update date managed
     * @param ticketId
     * @param updateDate
     */
    public void updateTicketIssueUpdateDateAndStatus(Long ticketId, String updateDate, Long status, Handler<Either<String, Void>> handler){
        String query = "update support.tickets set issue_update_date = to_timestamp(?,?)::timestamp, status = ?, "+
			" modified = timezone('UTC', NOW()), event_count = event_count + 1 "+
			" where id = ? ";
        JsonArray values = new JsonArray();
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d = null;
        try {
            d = df.parse(updateDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        values.add(formatter.format(d));
        values.add("YYYY-MM-dd HH24:MI:SS");
        values.add(status);
        values.add(ticketId);

        sql.prepared(query, values, validUniqueResultHandler(new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, Void>(res.left().getValue()));
				else
					handler.handle(new Either.Right<String, Void>(null));
			}
		}));
    }

    /**
     *
     * @param ticketId : id of the ticket historized
     * @param event : description of the event
     * @param status : status after the event
     * @param userid : user that made de creation / modification
     * @param eventType : 1 : new ticket /
     *                    2 : ticket updated /
     *                    3 : new comment /
     *                    4 : ticket escalated to bug-tracker /
     *                    5 : new comment from bug-tracker /
     *                    6 : bug-tracker updated.
     * @param handler
     */
    public void createTicketHisto(String ticketId, String event, int status, String userid, TicketHisto histoType, Handler<Either<String, Void>> handler) {
        String query = "INSERT INTO support.tickets_histo( ticket_id, event, status, user_id, event_type) "
                + " values( ?, ?, ?, ?, ? )";

        JsonArray values = new fr.wseduc.webutils.collections.JsonArray()
                .add(parseId(ticketId))
                .add(event)
                .add(status)
                .add(userid)
                .add(histoType.eventType());

        sql.prepared(query, values, validUniqueResultHandler(new Handler<Either<String, JsonObject>>()
		{
			@Override
			public void handle(Either<String, JsonObject> res)
			{
				if(res.isLeft())
					handler.handle(new Either.Left<String, Void>(res.left().getValue()));
				else
					handler.handle(new Either.Right<String ,Void>(null));
			}
		}));
    }

}
