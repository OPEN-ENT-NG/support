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

package net.atos.entng.support.filters;

import static org.entcore.common.sql.Sql.parseId;

import java.util.Map;

import net.atos.entng.support.Support;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import fr.wseduc.webutils.http.Binding;
import fr.wseduc.webutils.http.HttpMethod;

public class Admin implements ResourcesProvider {

	@Override
	public void authorize(final HttpServerRequest request, final Binding binding,
			final UserInfos user, final Handler<Boolean> handler) {

		if((isCommentIssue(binding) || isEscalateTicket(binding))
				&& !Support.escalationIsActivated()) {
			// User cannot comment issue nor escalate ticket if escalation is desactivated
			handler.handle(false);
			return;
		}

		String id = request.params().get("id");
		if (id == null || id.trim().isEmpty() ||
				(!(parseId(id) instanceof Integer) && !isGetBugTrackerAttachment(binding))) {
			handler.handle(false);
			return;
		}

		// Check if current user is a local admin for the ticket's school_id
		Map<String, UserInfos.Function> functions = user.getFunctions();
		if (functions == null || !(functions.containsKey(DefaultFunctions.ADMIN_LOCAL) ||functions.containsKey(DefaultFunctions.SUPER_ADMIN) )) {
			handler.handle(false);
			return;
		}

		Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
        Function admin = functions.get(DefaultFunctions.SUPER_ADMIN);
		if ((adminLocal == null || adminLocal.getScope() == null || adminLocal.getScope().isEmpty()) && admin == null ) {
			handler.handle(false);
			return;
		}

		request.pause();

		StringBuilder query = new StringBuilder("SELECT count(*) FROM support.tickets AS t ");
		JsonArray values = new JsonArray();

		if(isCommentIssue(binding)) {
			// parameter "id" is an issueId
			query.append("INNER JOIN support.bug_tracker_issues AS i ON t.id = i.ticket_id ")
				.append("WHERE i.id = ? ");
		}
		else if(isGetBugTrackerAttachment(binding)) {
			// parameter "id" is a gridfsId
			query.append("INNER JOIN support.bug_tracker_issues AS i ON t.id = i.ticket_id ")
				.append("INNER JOIN support.bug_tracker_attachments AS a ON i.id = a.issue_id ")
				.append("WHERE a.gridfs_id = ? ");
		}
		else {
			// parameter "id" is a ticketId
			query.append("WHERE t.id = ? ");
		}
		values.add(Sql.parseId(id));

        if( adminLocal != null ) {
            query.append("AND t.school_id IN (");
            for (String scope : adminLocal.getScope()) {
                query.append("?,");
                values.add(scope);
            }
            query.deleteCharAt(query.length() - 1);
            query.append(")");
        }

		Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				request.resume();
				Long count = SqlResult.countResult(message);
				handler.handle(count != null && count > 0);
			}
		});
	}

	private boolean isCommentIssue(final Binding binding) {
		return (HttpMethod.POST.equals(binding.getMethod())
				&& "net.atos.entng.support.controllers.TicketController|commentIssue".equals(binding.getServiceMethod()));
	}

	private boolean isGetBugTrackerAttachment(final Binding binding) {
		return (HttpMethod.GET.equals(binding.getMethod())
				&& "net.atos.entng.support.controllers.TicketController|getBugTrackerAttachment".equals(binding.getServiceMethod()));
	}

	private boolean isEscalateTicket(final Binding binding) {
		return (HttpMethod.POST.equals(binding.getMethod())
				&& "net.atos.entng.support.controllers.TicketController|escalateTicket".equals(binding.getServiceMethod()));
	}

}
