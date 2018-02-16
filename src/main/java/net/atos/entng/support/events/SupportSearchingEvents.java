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

package net.atos.entng.support.events;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import fr.wseduc.webutils.I18n;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.util.ArrayList;
import java.util.List;

import static org.entcore.common.sql.SqlResult.validResult;

public class SupportSearchingEvents extends SqlCrudService implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(SupportSearchingEvents.class);

	private static final I18n i18n = I18n.getInstance();

	public SupportSearchingEvents() {
		super("support", "tickets");
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, JsonArray searchWords, Integer page, Integer limit, final JsonArray columnsHeader,
							   final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(SupportSearchingEvents.class.getSimpleName())) {
			//fixme	for category, it's the uri of app so i need to add into core searching api the list of apps from userinfos ...
			//hardcode only today
			final List<String> searchFields = new ArrayList<String>();
			searchFields.add("t.subject");
			searchFields.add("t.description");

			final String iLikeTemplate = "ILIKE ALL " + Sql.arrayPrepared(searchWords.getList().toArray(), true);
			final String searchWhere = " AND (" + searchWherePrepared(searchFields, iLikeTemplate) + ")";

			//fixme only user tickets, perhaps add functions to searching api core for admin view
			final StringBuilder query = new StringBuilder();
			query.append(" SELECT t.id, t.owner, t.subject, t.description, t.modified, t.category, t.status, u.username AS owner_name")
					.append(" FROM support.tickets AS t")
					.append(" INNER JOIN support.users AS u ON t.owner = u.id")
					.append(" WHERE t.owner = ? ").append(searchWhere)
					.append(" ORDER BY t.modified DESC LIMIT ? OFFSET ?");
			final JsonArray values = new JsonArray();
			values.add(userId);
			final List<String> valuesWildcard = searchValuesWildcard(searchWords.getList());
			for (int i=0;i<searchFields.size();i++) {
				for (final String value : valuesWildcard) {
					values.add(value);
				}
			}

			final int offset = page * limit;
			values.add(limit).add(offset);

			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					final Either<String, JsonArray> ei = validResult(event);
					if (ei.isRight()) {
						final JsonArray res = formatSearchResult(ei.right().getValue(), columnsHeader, locale);
						handler.handle(new Right<String, JsonArray>(res));
						if (log.isDebugEnabled()) {
							log.debug("[SupportSearchingEvents][searchResource] The resources searched by user are finded");
						}
					} else {
						handler.handle(new Either.Left<String, JsonArray>(ei.left().getValue()));
					}
				}
			});
		} else {
			handler.handle(new Right<String, JsonArray>(new JsonArray()));
		}
	}

	private JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader, String locale) {
		final List<String> aHeader = columnsHeader.getList();
		final JsonArray traity = new JsonArray();

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.getJsonObject(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				jr.put(aHeader.get(0), formatTitle(j.getString("subject",""), j.getString("category",""),
						j.getInteger("status",0), locale));
				jr.put(aHeader.get(1), j.getString("description", ""));
				jr.put(aHeader.get(2), new JsonObject().put("$date",
						DatatypeConverter.parseDateTime(j.getString("modified")).getTime().getTime()));
				jr.put(aHeader.get(3), j.getString("owner_name"));
				jr.put(aHeader.get(4), j.getString("owner"));
				jr.put(aHeader.get(5), "/support#/ticket/"+ j.getLong("id", 0L));
				traity.add(jr);
			}
		}
		return traity;
	}

	private String formatStatus(int status, String locale) {
		final String key;
		switch (status) {
			case 1 : key="support.ticket.status.new";
				break;
			case 2 : key="support.ticket.status.opened";
				break;
			case 3 : key="support.ticket.status.resolved";
				break;
			default: key="support.ticket.status.closed";
				break;
		}

		return i18n.translate(key, I18n.DEFAULT_DOMAIN, locale);
	}

	private String formatCategory(String category, String locale) {
		final String categoryRes = i18n.translate(category.replace("/", ""), I18n.DEFAULT_DOMAIN, locale);
		return (categoryRes != null && !categoryRes.isEmpty()) ? categoryRes : i18n.translate("other", I18n.DEFAULT_DOMAIN, locale);
	}

	private String formatTitle(final String subject, final String category, final int status, final String locale) {
		return i18n.translate("support.search.title", I18n.DEFAULT_DOMAIN, locale,
				subject, formatCategory(category, locale), formatStatus(status, locale));
	}

	private String searchWherePrepared(List<String> list, final String templateLike) {
		StringBuilder sb = new StringBuilder();
		if (list != null && list.size() > 0) {
			for (String s : list) {
				sb.append("unaccent(").append(s).append(") ").append(templateLike).append(" OR ");
			}
			sb.delete(sb.length() - 3, sb.length());
		}
		return sb.toString();
	}

	private List<String> searchValuesWildcard(List<String> list) {
		final List<String> result = new ArrayList<String>();
		for (String s : list) {
			result.add("%" + s + "%");
		}
		return result;
	}
}
