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

public class OwnerOrLocalAdmin implements ResourcesProvider {

	/**
	 * Authorize if user is the ticket's owner, or a local admin for the ticket's school_id
	 */
	@Override
	public void authorize(final HttpServerRequest request, final Binding binding,
			final UserInfos user, final Handler<Boolean> handler) {

		final String ticketId = request.params().get("id");
		if (ticketId == null || ticketId.trim().isEmpty() || !(parseId(ticketId) instanceof Integer)) {
			handler.handle(false);
			return;
		}

		request.pause();

		StringBuilder query = new StringBuilder("SELECT count(*) FROM support.tickets AS t");
		query.append(" WHERE t.id = ?")
			.append(" AND (t.owner = ?"); // Check if current user is the ticket's owner
		JsonArray values = new JsonArray();
		values.add(Sql.parseId(ticketId))
			.add(user.getUserId());

        Function admin = null;

        Map<String, UserInfos.Function> functions = user.getFunctions();
		if (functions != null  && (functions.containsKey(DefaultFunctions.ADMIN_LOCAL) || functions.containsKey(DefaultFunctions.SUPER_ADMIN))) {
			// If current user is a local admin, check that its scope contains the ticket's school_id
			Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
            // super_admin always are authorized
			if (adminLocal != null && adminLocal.getScope() != null && !adminLocal.getScope().isEmpty()) {
				query.append(" OR t.school_id IN (");
				for (String scope : adminLocal.getScope()) {
					query.append("?,");
					values.add(scope);
				}
				query.deleteCharAt(query.length() - 1);
				query.append(")");
			}
		}

		query.append(")");
        admin = functions.get(DefaultFunctions.SUPER_ADMIN);


        final Function finalAdmin = admin;
        Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				request.resume();
				Long count = SqlResult.countResult(message);

                if( finalAdmin != null  ) {
                    handler.handle(true);
                } else {
                    handler.handle(count != null && count > 0);
                }
			}
		});

	}

}
