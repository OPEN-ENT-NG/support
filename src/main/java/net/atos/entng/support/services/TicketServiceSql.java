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

package net.atos.entng.support.services;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.utils.Id;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.Either;

import net.atos.entng.support.Ticket;
import net.atos.entng.support.Issue;
import net.atos.entng.support.Attachment;
import net.atos.entng.support.enums.TicketHisto;

public interface TicketServiceSql extends CrudService {

	public void createTicket(JsonObject ticket, JsonArray attachments, UserInfos user, String locale, Handler<Either<String, Ticket>> handler);

	public void updateTicket(String id, JsonObject data, UserInfos user, Handler<Either<String, Ticket>> handler);

	public void listTickets(UserInfos user, Integer page, List<String> statuses, List<String> applicants, String school_id, String sortBy, String order, Integer nbTicketsPerPage, Handler<Either<String, JsonArray>> handler);

	public void listMyTickets(UserInfos user, Integer page, List<String> statuses, String school_id, String sortBy, String order, Integer nbTicketsPerPage, Handler<Either<String, JsonArray>> handler);

	public void getMyTicket(UserInfos user, Integer id, Handler<Either<String, JsonArray>> handler);

	public void getTicket(UserInfos user, Integer id, Handler<Either<String, JsonArray>> handler);


	/**
	 * If escalation status is "not_done" or "failed", and ticket status is new or opened,
	 * update escalation status to "in_progress" and return the ticket with its attachments' ids and its comments.
	 *
	 * Else (escalation is not allowed) return null.
	 */
	public void getTicketWithEscalation(String ticketId, Handler<Either<String, Ticket>> handler);

    /**
     * Get ticket in format usable in escalation service
     * @param ticketId Id of the ticket to get
     * @param handler Handler that will process the response
     */
	public void getTicketForEscalationService( String ticketId, Handler<Either<String, Ticket>> handler);

	public void getTicketIdAndSchoolId(Number issueId, Handler<Either<String, Ticket>> handler);

	public void endInProgressEscalationAsync(String ticketId, UserInfos user, JsonObject issueJira, Handler<Either<String, JsonObject>> handler);

	public void endSuccessfulEscalation(String ticketId, Issue issue, Number issueId,
			UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * End escalation in "In progress" state. Used for asynchronous bug trackers
	 */
	public void endInProgressEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void endFailedEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void updateIssue(Number issueId, Issue content, Handler<Either<String, String>> handler);

    public void updateEventCount(String ticketId, Handler<Either<String, Void>> handler);

    public void createTicketHisto(String ticketId, String event, int status, String userid, TicketHisto histoType, Handler<Either<String, Void>> handler);

    public void getTicketFromIssueId(String issueId, Handler<Either<String, JsonObject>> handler);

	public void getLastIssuesUpdate(Handler<Either<String, String>> handler);

	/**
	 * Given a list of issue ids (parameter "issueIds"), return the issue ids that exist in database and their attachments' ids
	 */
	public void listExistingIssues(Number[] issueIds, Handler<Either<String, List<Issue>>> handler);

	public void getIssue(String ticketId, Handler<Either<String, Issue>> handler);

	public void getIssueAttachmentName(String gridfsId, Handler<Either<String, JsonObject>> handler);

	public void insertIssueAttachment(Id<Issue, ? extends Number> issueId, Attachment attachment, Handler<Either<String, Void>> handler);

	public void insertIssueAttachments(Id<Issue, ? extends Number> issueId, List<Attachment> attachments, Handler<Either<String, Void>> handler);

    public void updateTicketStatus(Integer newStatus, List<Integer> ids, Handler<Either<String, JsonObject>> handler);

    public void updateTicketIssueUpdateDateAndStatus(Long ticketId, String updateDate, Long status, Handler<Either<String, Void>> handler);

    public void listEvents(String ticketId, Handler<Either<String, JsonArray>> handler);
}
