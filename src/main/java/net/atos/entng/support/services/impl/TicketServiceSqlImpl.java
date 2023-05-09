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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.enums.EscalationStatus;
import net.atos.entng.support.enums.TicketStatus;
import net.atos.entng.support.helpers.DateHelper;
import net.atos.entng.support.helpers.PromiseHelper;
import net.atos.entng.support.helpers.FutureHelper;
import net.atos.entng.support.services.TicketServiceSql;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
			Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		// 1. Upsert user
		s.prepared(UPSERT_USER_QUERY, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Create ticket
		ticket.put("owner", user.getUserId());
        ticket.put("locale", locale);
		String returnedFields = "id, school_id, status, created, modified, escalation_status, escalation_date, substring(description, 0, 101)  as short_desc";
		s.insert(resourceTable, ticket, returnedFields);

		this.insertAttachments(attachments, user, s, null);

		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
	}

	@Override
	public void updateTicket(String ticketId, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {

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
				"WHERE id = ? RETURNING modified, subject, owner, school_id";
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
				if (elem.length == Ticket.COMMENT_LENGTH) {
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
		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
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
			query.append(" WHERE t.school_id IN ").append(Sql.listPrepared(user.getStructures())).append(" ");
			values.addAll(new JsonArray(user.getStructures()));
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
	public void getTicketIdAndSchoolId(final Number issueId, final Handler<Either<String, JsonObject>> handler) {
		String query = "SELECT t.id, t.school_id, t.owner, t.locale, t.status FROM support.tickets AS t"
				+ " INNER JOIN support.bug_tracker_issues AS i ON t.id = i.ticket_id"
				+ " WHERE i.id = ?";
		JsonArray values = new JsonArray().add(issueId);

		sql.prepared(query.toString(), values, validUniqueResultHandler(handler));
	}

	/**
	 * If escalation status is "not_done" or "failed", and ticket status is new or opened,
	 * update escalation status to "in_progress" and return the ticket with its attachments' ids and its comments.
	 *
	 * Else (escalation is not allowed) return null.
	 */
	@Override
	public void getTicketWithEscalation(String ticketId, Handler<Either<String, JsonObject>> handler) {
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

		sql.prepared(query.toString(), values, validUniqueResultHandler(handler));
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
	public void getTicketForEscalationService( String ticketId, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append( escalateTicketInfoQuery("support.tickets AS t", "WHERE t.id = ?") );
		values.add(Long.valueOf(ticketId));

		sql.prepared(query.toString(), values, validUniqueResultHandler(handler));

	}


	private void updateTicketAfterEscalation(String ticketId, EscalationStatus targetStatus,
			JsonObject issue, Number issueId, ConcurrentMap<Long, String> attachmentMap,
			UserInfos user, Handler<Either<String, JsonObject>> handler) {

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
			String insertQuery = "INSERT INTO support.bug_tracker_issues(id, ticket_id, content, owner)"
					+ " VALUES(?, ?, ?::JSON, ?)"
					+ " ON CONFLICT (id)"
					+ " DO UPDATE"
					+ " SET content = excluded.content";

			JsonArray insertValues = new JsonArray().add(issueId)
					.add(parseId(ticketId))
					.add(issue)
					.add(user.getUserId());

			statements.prepared(insertQuery, insertValues);

			// 4. Insert attachment (document from workspace) metadata
			if(issue.size() > 0) {
				JsonArray attachments = bugTrackerType.extractAttachmentsFromIssue(issue);
				if(attachments != null && attachments.size() > 0
						&& attachmentMap != null && !attachmentMap.isEmpty()) {
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

					for (Object o : attachments) {
						if(!(o instanceof JsonObject)) continue;
						JsonObject attachment = (JsonObject) o;
						attachmentsQuery.append("support.merge_attachment_bydoc(?, ?, ?, ?, ?),");

						Number attachmentIdInBugTracker = attachment.getLong("id");
						attachmentsValues.add(attachmentIdInBugTracker)
							.add(issueId)
							.add(attachmentMap.get(attachmentIdInBugTracker))
							.add(attachment.getString("filename"))
							.add(attachment.getInteger("filesize"));
					}
					// remove trailing comma
					attachmentsQuery.deleteCharAt(attachmentsQuery.length() - 1);

					statements.prepared(attachmentsQuery.toString(), attachmentsValues);
				}
			}

			sql.transaction(statements.build(), validUniqueResultHandler(1, handler));
		}

	}

	/**
	 * 	@inheritDoc
	 */
	@Override
	public void endSuccessfulEscalation(String ticketId, JsonObject issue, Number issueId,
			ConcurrentMap<Long, String> attachmentMap,
			UserInfos user, Handler<Either<String, JsonObject>> handler) {

		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, issue, issueId, attachmentMap, user, handler);
	}

	@Override
	public void endFailedEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.FAILED, null, null, null, user, handler);
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
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, issue, issueId, null, user, handler);
	}

    @Override
    public void endInProgressEscalationAsync(String ticketId, UserInfos user, JsonObject issueJira, Handler<Either<String, JsonObject>> handler) {
		JsonObject issue = new JsonObject()
				.put(Ticket.ISSUE, new JsonObject()
						.put(Ticket.ID, issueJira.getString(Ticket.ID_JIRA_FIELD))
						.put(Ticket.STATUS, issueJira.getString(Ticket.STATUS_JIRA_FIELD))
						.put(Ticket.DATE, DateHelper.convertDateFormat())
						.put(Ticket.ID_ENT, ticketId));

		Number issueId = 0;
		try {
			// use ticket id as issue id in database
			issueId = Integer.parseInt(ticketId);
		} catch (NumberFormatException e) {
			String message = String.format("[Support@%s::endInProgressEscalationAsync] Support : Invalid id_ent, saving issue with id 0 : %s",
					this.getClass().getSimpleName(), e.getMessage());
			log.error(message);
		}
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, issue, issueId, null, user, handler);
    }

	@Override
	public void updateIssue(Number issueId, String content, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		// WITH clause to RETURN previous status_id
		query.append("WITH old_issue AS (")
			.append(" SELECT content").append(bugTrackerType.getStatusIdFromPostgresqlJson()).append(" AS status_id")
			.append(" FROM support.bug_tracker_issues")
			.append(" WHERE id = ?)");
		values.add(issueId);

		query.append(" UPDATE support.bug_tracker_issues")
			.append(" SET content = ?::JSON, modified = timezone('UTC', NOW())")
			.append(" WHERE id = ?")
			.append(" RETURNING (SELECT status_id FROM old_issue)");

		values.add(content)
			.add(issueId);

		sql.prepared(query.toString(), values, validUniqueResultHandler(handler));
	}

	@Override
	public void getLastIssuesUpdate(Handler<Either<String, JsonArray>> handler) {
		String query = "SELECT max(content"
				+ bugTrackerType.getLastIssueUpdateFromPostgresqlJson()
				+ ") AS last_update FROM support.bug_tracker_issues";

		sql.raw(query, validResultHandler(handler));
	}

	/**
	 * 	@inheritDoc
	 */
	@Override
	public void listExistingIssues(Number[] issueIds, Handler<Either<String, JsonArray>> handler) {
		/* Return for instance :
			[ { "attachment_ids": "[]", "id": 2836 },
			  { "attachment_ids": "[931, 932, 933, 934, 935, 937, 936]", "id": 2876 } ]
		 */
		StringBuilder query = new StringBuilder("SELECT i.id,")
			.append(" CASE WHEN COUNT(a.id) = 0 THEN '[]'")
			.append(" ELSE json_agg(a.id)")
			.append(" END AS attachment_ids");

		query.append(" FROM support.bug_tracker_issues AS i")
			.append(" LEFT JOIN support.bug_tracker_attachments AS a")
			.append(" ON a.issue_id = i.id");

		JsonArray values = new JsonArray();

		if(issueIds != null && issueIds.length>0) {
			query.append(" WHERE i.id IN (");
			for (Number id : issueIds) {
				query.append("?,");
				values.add(id);
			}
			query.deleteCharAt(query.length() - 1);
			query.append(")");
		}

		query.append(" GROUP BY i.id");

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getIssue(String ticketId, Handler<Either<String, JsonArray>> handler) {
		/* Field "attachments" will contain for instance :
		 *  [{"id":931,"document_id":null,"gridfs_id":"13237cd7-9567-4810-a85e-39414093e3b5"},
			 {"id":932,"document_id":null,"gridfs_id":"17223f70-d9a8-4983-92b1-d867fc881d44"},
			 {"id":933,"document_id":"c7b27108-8715-40e1-a32f-e90828857c35","gridfs_id":null}]
		 */
		StringBuilder query = new StringBuilder("SELECT i.id, i.content,")
			.append(" CASE WHEN COUNT(a.id) = 0 THEN '[]'")
			.append(" ELSE json_agg((a.id, a.document_id, a.gridfs_id)::support.bug_tracker_attachment_tuple)")
			.append(" END AS attachments")
			.append(" FROM support.bug_tracker_issues AS i")
			.append(" LEFT JOIN support.bug_tracker_attachments AS a ON i.id = a.issue_id")
			.append(" WHERE i.ticket_id = ?")
			.append(" GROUP BY i.id");
		JsonArray values = new JsonArray().add(parseId(ticketId));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void getIssueAttachmentName(String gridfsId, Handler<Either<String, JsonObject>> handler) {
		String query = "SELECT name FROM support.bug_tracker_attachments WHERE gridfs_id = ?";
		JsonArray values = new JsonArray().add(gridfsId);
		sql.prepared(query, values, validUniqueResultHandler(handler));
	};

	@Override
	public void insertIssueAttachment(Number issueId, JsonObject attachment, Handler<Either<String, JsonArray>> handler) {
		/* NB : Attachments downloaded from bug tracker are saved in gridfs, but not in application "workspace".
		 * => we save a gridfs_id, not a document_id
		 */
		String query = "SELECT support.merge_attachment_bygridfs(?, ?, ?, ?, ?)";

		JsonArray values = new JsonArray();
		JsonObject metadata = attachment.getJsonObject("metadata");

		values.add(attachment.getInteger("id_in_bugtracker"))
			.add(issueId)
			.add(attachment.getString("_id"))
			.add(metadata.getString("filename"))
			.add(metadata.getLong("size"));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

    /**
     * Increase the event_count field of ticket table. It means an update has been done.
     * @param ticketId
     * @param handler
     */
    public void updateEventCount(String ticketId, Handler<Either<String, JsonObject>> handler) {
        String query = "UPDATE support.tickets"
                + " SET event_count = event_count + 1 "
                + " WHERE id = ?";

        JsonArray values = new JsonArray()
                .add(parseId(ticketId));

        sql.prepared(query, values, validUniqueResultHandler(handler));
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
	 * Aim to convert listEvents in Future behaviour
	 * @param ticketId : ticket id from which we want to list the history
	 */
	@Override
	public Future<JsonArray> getlistEvents(String ticketId) {
		Promise<JsonArray> promise = Promise.promise();
		String query = "SELECT username, event, th.status, event_date, user_id, event_type, t.school_id FROM support.tickets_histo th " +
				" left outer join support.users u on u.id = th.user_id " +
				" left outer join support.tickets t on th.ticket_id = t.id" +
				" WHERE ticket_id = ? ";
		JsonArray values = new JsonArray().add(parseId(ticketId));
		sql.prepared(query, values, validResultHandler(FutureHelper.handlerEitherPromise(promise)));
		return promise.future();
	}

	/**
     *
     * @param issueId : bug tracker number from which we want the linked ticket
     * @param handler
     */
    public void getTicketFromIssueId(String issueId, Handler<Either<String, JsonObject>> handler) {
        String query = "SELECT t.* " +
                " from support.tickets t" +
                " inner join support.bug_tracker_issues bti on bti.ticket_id = t.id" +
                " WHERE bti.id = ? ";
        JsonArray values = new JsonArray().add(parseId(issueId));
        sql.prepared(query, values, validUniqueResultHandler(handler));
    }

    /**
     * Updates the ticket table, sets the issueUpdateDate field to the last update date managed
     * @param ticketId
     * @param updateDate
     */
    public void updateTicketIssueUpdateDateAndStatus(Long ticketId, String updateDate, Long status, Handler<Either<String, JsonObject>> handler){
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

        sql.prepared(query, values, validUniqueResultHandler(handler));
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
    public void createTicketHisto(String ticketId, String event, int status, String userid, int eventType, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO support.tickets_histo( ticket_id, event, status, user_id, event_type) "
                + " values( ?, ?, ?, ?, ? )";

        JsonArray values = new fr.wseduc.webutils.collections.JsonArray()
                .add(parseId(ticketId))
                .add(event)
                .add(status)
                .add(userid)
                .add(eventType);

        sql.prepared(query, values, validUniqueResultHandler(handler));
    }

	/**
	 * @param idList : list of ticket ids I want to retrieve
	 * @return {@link Future} of {@link JsonArray}
	 **/
	@Override
	public Future<JsonArray> getTicketsFromListId(List<String> idList) {
		Promise<JsonArray> promise = Promise.promise();
		String query = "SELECT * FROM support.tickets" +
				" WHERE id IN " + Sql.listPrepared(idList);
		JsonArray values = new JsonArray(idList);
		sql.prepared(query, values, validResultHandler(PromiseHelper.handler(promise)));
		return promise.future();
	}
}
