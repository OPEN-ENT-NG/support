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

import java.util.*;

import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;

import fr.wseduc.webutils.http.Binding;

public class OwnerOrLocalAdmin implements ResourcesProvider {

	/**
	 * Authorize if user is the tickets' owner, or a local admin for the tickets' school_id
	 */
	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		Set<Integer> ticketIds = new HashSet<>();
		if (request.params().contains("id")) {
			try {
				ticketIds.add(Integer.parseInt(request.params().get("id")));
			} catch (NumberFormatException e) {
				handler.handle(false);
			}
		}
		request.pause();
		getTicketIdsFromBody(request).onComplete(ids -> {
			ticketIds.addAll(ids.result());

			if (ticketIds.isEmpty()) {
				handler.handle(false);
				return;
			}
			JsonArray values = new JsonArray();
			StringBuilder query = new StringBuilder("SELECT count(*) FROM support.tickets AS t");
			query.append(" WHERE t.id IN (");
			bindInSqlKeyword(query, values, new HashSet<>(ticketIds));
			query.append(")");
			query.append(" AND (t.owner = ?"); // Check if current user is the ticket's owner
			values.add(user.getUserId());

			Map<String, UserInfos.Function> userFunctions = user.getFunctions();
			if (userFunctions != null  && (userFunctions.containsKey(DefaultFunctions.ADMIN_LOCAL) || userFunctions.containsKey(DefaultFunctions.SUPER_ADMIN))) {
				// If current user is a local admin, check that its scope contains the ticket's school_id
				Function adminLocal = userFunctions.get(DefaultFunctions.ADMIN_LOCAL);
				// super_admin always are authorized
				if (adminLocal != null && adminLocal.getScope() != null && !adminLocal.getScope().isEmpty()) {
					query.append(" OR t.school_id IN (");
					bindInSqlKeyword(query, values, new HashSet<>(adminLocal.getScope()));
					query.append(")");
				}
			}
			query.append(")");
			Sql.getInstance().prepared(query.toString(), values, result -> {
				request.resume();
				Long count = SqlResult.countResult(result);
				if(userFunctions.get(DefaultFunctions.SUPER_ADMIN) != null) {
					// super admin is authorized
					handler.handle(true);
				} else {
					handler.handle(count != null && count == ticketIds.size());
				}
			});
		});
	}

	private Future<Set<Integer>> getTicketIdsFromBody(HttpServerRequest request) {
		Promise<Set<Integer>> promise = Promise.promise();
		final Set<Integer> idsFromBody = new HashSet<>();
		if (request.headers().contains("Content-Type") && request.headers().get("Content-Type").contains("application/json")){
			RequestUtils.bodyToJson(request, body -> {
				if (body != null && body.containsKey("ids")) {
					idsFromBody.addAll(body.getJsonArray("ids").getList());
				}
				promise.complete(idsFromBody);
			});
		} else {
			promise.complete(idsFromBody);
		}
		return promise.future();
	}

	private void bindInSqlKeyword(StringBuilder query, JsonArray values, Set<Object> bindings) {
		bindings.forEach(binding -> {
			query.append("?,");
			values.add(binding);
		});
		query.deleteCharAt(query.length() - 1);
	}
}
