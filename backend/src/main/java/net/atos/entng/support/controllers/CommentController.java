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

package net.atos.entng.support.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.CommentService;
import net.atos.entng.support.services.impl.CommentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.sql.OwnerOnly;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;


public class CommentController extends ControllerHelper {

	private final CommentService commentService;

	public CommentController() {
		commentService = new CommentServiceSqlImpl();
		crudService = commentService;
	}

	@Put("comment/:id")
	@ApiDoc("Update a comment")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOnly.class)
	public void updateComment(final HttpServerRequest request) {
		final String commentId = request.params().get("id");

		RequestUtils.bodyToJson(request, pathPrefix + "updateComment", new Handler<JsonObject>(){
			@Override
			public void handle(JsonObject comment) {
				commentService.update(commentId, comment, notEmptyResponseHandler(request));
			}
		});
	}

	@Get("/ticket/:id/comments")
	@ApiDoc("Get all comments of a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void listTicketComments(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		commentService.listTicketComments(ticketId, arrayResponseHandler(request));
	}


}
