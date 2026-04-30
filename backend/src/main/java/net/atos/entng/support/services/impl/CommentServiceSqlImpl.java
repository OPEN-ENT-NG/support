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

import static org.entcore.common.sql.SqlResult.*;
import net.atos.entng.support.services.CommentService;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public class CommentServiceSqlImpl extends SqlCrudService implements CommentService {

	public CommentServiceSqlImpl() {
		super("support", "comments");
	}

	@Override
	public void listTicketComments(String ticketId, Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("SELECT c.*, u.username AS owner_name")
			.append(" FROM support.comments AS c")
			.append(" INNER JOIN support.users AS u ON c.owner = u.id")
			.append(" WHERE c.ticket_id = ?")
			.append(" ORDER BY c.modified");

		JsonArray values = new JsonArray().add(Sql.parseId(ticketId));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}



}
