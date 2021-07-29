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

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import fr.wseduc.rs.Delete;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.AttachmentService;
import net.atos.entng.support.services.impl.AttachmentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.folders.FolderManager;
import org.entcore.common.http.filter.ResourceFilter;
import io.vertx.core.http.HttpServerRequest;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import org.entcore.common.user.UserUtils;

public class AttachmentController extends ControllerHelper {

	private final AttachmentService attachmentService;

	public AttachmentController(FolderManager folderManager) {
		attachmentService = new AttachmentServiceSqlImpl(folderManager);
		crudService = attachmentService;
	}

	@Get("/ticket/:id/attachments")
	@ApiDoc("Get all attachments of a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void listTicketAttachments(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		attachmentService.listTicketAttachments(ticketId, arrayResponseHandler(request));
	}

	@Delete("/ticket/:id/attachment/:attachmentId")
	@ApiDoc("delete attachment of a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void deleteTicketAttachment(final HttpServerRequest request) {
		String ticketId = request.params().get("id");
		String attachmentId = request.params().get("attachmentId");
		UserUtils.getUserInfos(eb, request, user ->
				attachmentService.deleteTicketAttachment(user, ticketId, attachmentId)
						.onSuccess(result -> renderJson(request, result))
						.onFailure(fail -> badRequest(request)));
	}

}
