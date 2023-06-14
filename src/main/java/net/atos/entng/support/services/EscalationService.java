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

import net.atos.entng.support.Ticket;
import net.atos.entng.support.Issue;
import net.atos.entng.support.Comment;
import net.atos.entng.support.Attachment;
import net.atos.entng.support.enums.BugTracker;

import org.entcore.common.utils.Id;
import org.entcore.common.user.UserInfos;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.eventbus.Message;


import fr.wseduc.webutils.Either;

/**
 * Terminology used : "ticket" for tickets in ENT, "issue" for tickets in bug tracker
 */
public interface EscalationService {

	public BugTracker getBugTrackerType();

	/**
	 * Parameters "ticket", "comments" and "attachments" are used to create a ticket in bug tracker
	 *
	 */
	public void escalateTicket(HttpServerRequest request, Ticket ticket,
							   UserInfos user, Issue issue, Handler<Either<String, Issue>> handler);

	public void getIssue(Number issueId, Handler<Either<String, Issue>> handler);

	public void commentIssue(Number issueId, Comment comment, Handler<Either<String,Void>> handler);

	public void updateTicketFromBugTracker(Message<JsonObject> message, Handler<Either<String, JsonObject>> handler);

	void syncAttachments(String ticketId, List<Attachment> attachments, Handler<Either<String, Id<Issue, Long>>> handler);

}
