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
import io.vertx.core.json.JsonObject;
import org.entcore.common.storage.Storage;
import io.vertx.core.Vertx;


import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketServiceSql;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.impl.EscalationServiceRedmineImpl;
import net.atos.entng.support.services.impl.EscalationServicePivotImpl;
import net.atos.entng.support.services.impl.EscalationServiceZendeskImpl;


public class EscalationServiceFactory {

	public static EscalationService makeEscalationService(final BugTracker bugTracker,
														  final Vertx vertx, final JsonObject config, final TicketServiceSql ts, final UserService us,
														  Storage storage) {
		switch (bugTracker) {
			case REDMINE:
				return new EscalationServiceRedmineImpl(vertx, config, ts, us, storage);

			case PIVOT:
				return new EscalationServicePivotImpl(vertx, config, ts, us, storage);

			case ZENDESK:
				return new EscalationServiceZendeskImpl(vertx, config, ts, us, storage);

			default:
				throw new IllegalArgumentException("Invalid parameter bugTracker");
		}
	}
}
