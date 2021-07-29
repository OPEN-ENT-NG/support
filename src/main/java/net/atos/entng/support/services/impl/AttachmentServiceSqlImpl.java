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

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.services.AttachmentService;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;

import fr.wseduc.webutils.Either;
import org.entcore.common.user.UserInfos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.entcore.common.sql.SqlResult.*;

public class AttachmentServiceSqlImpl extends SqlCrudService implements AttachmentService  {
	Logger log = LoggerFactory.getLogger(AttachmentServiceSqlImpl.class);

	private final FolderManager folderManager;


	public AttachmentServiceSqlImpl(FolderManager folderManager) {
		super("support", "comments");
		this.folderManager = folderManager;
	}

	@Override
	public void listTicketAttachments(String ticketId,
			Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("SELECT a.*, u.username AS owner_name")
			.append(" FROM support.attachments AS a")
			.append(" INNER JOIN support.users AS u ON a.owner = u.id")
			.append(" WHERE a.ticket_id = ?")
			.append(" ORDER BY a.created");

		JsonArray values = new JsonArray().add(Sql.parseId(ticketId));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	/** Proceed on delete ticket attachment
	 *
	 * @param user			user info {@link UserInfos}
	 * @param ticketId		ticket identifier {@link String}
	 * @param attachmentId	attachment identifier {@link String}
	 * @return {@link Future<JsonObject>}
	 */
	@Override
	public Future<JsonObject> deleteTicketAttachment(UserInfos user, String ticketId, String attachmentId) {
		Promise<JsonObject> promise = Promise.promise();

		checkAttachmentExist(ticketId, attachmentId)
				.onSuccess(exist -> {
					if (Boolean.TRUE.equals(exist)) {
						deleteAttachmentFromWorkspace(user, attachmentId)
								.compose(ar -> deleteAttachmentFromTicket(ticketId, attachmentId))
								.onSuccess(promise::complete)
								.onFailure(promise::fail);
					} else {
						promise.fail(String.format("[%s::deleteTicketAttachment] no attachment", this.getClass().getSimpleName()));
					}
				})
				.onFailure(promise::fail);

		return promise.future();
	}

	/**
	 * check if attachment identifier exists among the ticket from support
	 *
	 * @param ticketId		ticket identifier {@link String}
	 * @param attachmentId	attachment identifier {@link String}
	 * @return {@link Future<Boolean>}
	 */
	private Future<Boolean> checkAttachmentExist(String ticketId, String attachmentId) {
		Promise<Boolean> promise = Promise.promise();

		String queryAttachment = "SELECT a.* FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ? ORDER BY a.created";

		JsonArray values = new JsonArray()
				.add(ticketId)
				.add(attachmentId);

		sql.prepared(queryAttachment, values, validResultHandler(event -> {
			if (event.isLeft()) {
				log.error(String.format("[%s::checkAttachmentExist]  an error has occured during checking attachment file: %s",
						this.getClass().getSimpleName(), event.left().getValue()));
				promise.fail(event.left().getValue());
			} else {
				promise.complete(!event.right().getValue().isEmpty());
			}
		}));

		return promise.future();
	}

	/**
	 * delete attachment from workspace
	 *
	 * @param user			user data {@link UserInfos}
	 * @param attachmentId	attachment identifier {@link String}
	 * @return {@link Future<Boolean>}
	 */
	private Future<Void> deleteAttachmentFromWorkspace(UserInfos user, String attachmentId) {
		Promise<Void> promise = Promise.promise();
		Set<String> id = new HashSet<>(Collections.singleton(attachmentId));
		folderManager.deleteAll(id, user, event -> {
			if (event.succeeded()) {
				promise.complete();
			} else {
				log.error(String.format("[%s::deleteAttachmentFromWorkspace] An error has occured during document deletion: %s",
						this.getClass().getSimpleName(), event.cause().getMessage()));
				promise.fail(event.cause().getMessage());
			}
		});
		return promise.future();
	}

	/**
	 * delete attachment data from table support attachment
	 *
	 * @param ticketId		ticket identifier {@link String}
	 * @param attachmentId	attachment identifier {@link String}
	 * @return {@link Future<Boolean>}
	 */
	private Future<JsonObject> deleteAttachmentFromTicket(String ticketId, String attachmentId) {
		Promise<JsonObject> promise = Promise.promise();

		String queryAttachment = "DELETE FROM support.attachments AS a WHERE a.ticket_id = ? AND a.document_id = ?";

		JsonArray values = new JsonArray()
				.add(ticketId)
				.add(attachmentId);

		sql.prepared(queryAttachment, values, validUniqueResultHandler(event -> {
			if (event.isLeft()) {
				log.error(String.format("[%s::deleteAttachmentFromTicket] an error has occured during deleting attachment from ticket: %s",
						this.getClass().getSimpleName(), event.left().getValue()));
				promise.fail(event.left().getValue());
			} else {
				promise.complete(event.right().getValue());
			}
		}));

		return promise.future();
	}

}
