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

package net.atos.entng.support;

import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import net.atos.entng.support.controllers.*;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.events.SupportSearchingEvents;
import net.atos.entng.support.export.TicketExportWorker;
import net.atos.entng.support.helpers.PromiseHelper;
import net.atos.entng.support.message.MessageResponseHandler;
import net.atos.entng.support.services.*;

import org.entcore.common.folders.FolderManager;
import org.entcore.common.http.BaseServer;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.share.impl.GenericShareService;
import org.entcore.common.share.impl.MongoDbShareService;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.entcore.common.storage.impl.PostgresqlApplicationStorage;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;


public class Support extends BaseServer {

	public final static String SUPPORT_NAME = "SUPPORT";
    private static boolean escalationActivated;
	private static boolean richEditorActivated;
    public static boolean bugTrackerCommDirect;

	@Override
	public void start() throws Exception {
		super.start();

		addController(new DisplayController());

		BugTracker bugTrackerType;

		// Default value to REDMINE for compatibility purpose
		bugTrackerType = BugTracker.valueOf(config.getString("bug-tracker-name", BugTracker.REDMINE.toString()).toUpperCase());
		final Storage storage = new StorageFactory(vertx, config,
				new PostgresqlApplicationStorage("support.attachments", Support.class.getSimpleName(),
						new JsonObject().put("id", "document_id"))).getStorage();


		String node = (String) vertx.sharedData().getLocalMap("server").get("node");
		if (node == null) {
			node = "";
		}
		GenericShareService shareService = new MongoDbShareService(vertx.eventBus(), MongoDb.getInstance(), "documents", securedActions, new HashMap<>());
		String imageResizerAddress = node + config.getString("image-resizer-address", "wse.image.resizer");
		final boolean useOldQueryChildren = config.getBoolean("old-query", false);
		FolderManager folderManager = FolderManager.mongoManager("documents", storage, vertx, shareService, imageResizerAddress, useOldQueryChildren);

		ServiceFactory serviceFactory = new ServiceFactory(vertx, storage, Neo4j.getInstance(), Sql.getInstance(), MongoDb.getInstance(), config, bugTrackerType);

        // Indicates if the user can have direct communication with redmine, or if the admin has to transfer the informations.
        bugTrackerCommDirect = config.getBoolean("bug-tracker-comm-direct", true);

		// Escalation to a remote bug tracker (e.g. Redmine) is desactivated by default
		escalationActivated = config.getBoolean("activate-escalation", false);
		if(!escalationActivated) {
			log.info("[Support] Escalation is desactivated");
		}

		// Rich Editor (for http link...) is desactivated by default
		richEditorActivated = config.getBoolean("activate-rich-editor", false);
		if(!richEditorActivated) {
			log.info("[Support] Rich Editor is desactivated");
		}

        TicketController ticketController = new TicketController(serviceFactory);
		addController(ticketController);

		SqlConf commentSqlConf = SqlConfs.createConf(CommentController.class.getName());
		commentSqlConf.setTable("comments");
		commentSqlConf.setSchema("support");
		CommentController commentController = new CommentController();
		addController(commentController);
		addController(new ConfigController());
		addController(new FakeRight());

		AttachmentController attachmentController = new AttachmentController(folderManager);
		addController(attachmentController);

		//suscribe to search engine
		if (config.getBoolean("searching-event", true)) {
			setSearchingEvents(new SupportSearchingEvents());
		}

		vertx.deployVerticle(TicketExportWorker.class, new DeploymentOptions().setConfig(config).setWorker(true));

	}

	public static boolean escalationIsActivated() {
		return escalationActivated;
	}

	public static boolean richEditorIsActivated() {
		return richEditorActivated;
	}

	public static Future<JsonObject> launchExportTicketsWorker(EventBus eb, JsonObject params) {
		Promise<JsonObject> promise = Promise.promise();
		eb.request(TicketExportWorker.class.getName(), params,
				new DeliveryOptions().setSendTimeout(1000 * 1000L),
				MessageResponseHandler.messageJsonObjectHandler(PromiseHelper.handler(promise)));

		return promise.future();
	}

}
