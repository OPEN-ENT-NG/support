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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.helpers.PromiseHelper;
import net.atos.entng.support.services.UserService;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.neo4j.Neo4jResult;

import java.util.List;

public class UserServiceDirectoryImpl implements UserService {

	private final EventBus eb;
	private final Neo4j neo4j;
	private static final String DIRECTORY_ADDRESS = "directory";

	public UserServiceDirectoryImpl(EventBus eb, Neo4j neo4j) {
		this.neo4j = neo4j;
		this.eb = eb;
	}

	@Override
	public void getLocalAdministrators(String structure, final Handler<JsonArray> handler) {

		JsonObject action = new JsonObject()
			.put("action", "list-adml")
			.put("structureId", structure);
		eb.request(DIRECTORY_ADDRESS, action, (Handler<AsyncResult<Message<JsonArray>>>) res -> handler.handle(res.result().body()));
	}

	@Override
	public Future<JsonArray> getUserIdsProfileOrdered(List<String> ownerIds) {
		Promise<JsonArray> promise = Promise.promise();

		String query = " MATCH (u:User)-[:IN]->(pg:ProfileGroup)-[:HAS_PROFILE]->(profile:Profile) " +
				" WHERE u.id IN {ownerIds} " +
				" WITH profile, u " +
				" ORDER BY profile.name " +
				" RETURN distinct u.id as id ";
		JsonObject params = new JsonObject().put(JiraTicket.OWNERIDS, ownerIds);
		neo4j.execute(query, params, Neo4jResult.validResultHandler(PromiseHelper.handler(promise,
				String.format("[Minibadge@%s::getUsersRequest] Fail to create badge assigned",
						this.getClass().getSimpleName()))));

		return promise.future();
	}

}