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

package net.atos.entng.support.enums;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.constants.JiraTicket;

public enum BugTracker {

	REDMINE {
		@Override
		public Number getIssueId(JsonObject issue) {
			return this.extractIdFromIssue(issue);
		}

		@Override
		public String extractIdFromIssueString(JsonObject issue) {
			if (issue != null) {
				return issue.getString(JiraTicket.ID_ENT);
			}
			return "";
		}
		@Override
		public String getLastIssueUpdateFromPostgresqlJson() {
			return "->'issue'->>'updated_on'";
		}

		@Override
		public String getStatusIdFromPostgresqlJson() {
			return "#>'{issue,status,id}'";
		}

		@Override
		public Number extractIdFromIssue(JsonObject issue) {

			return issue.getJsonObject("issue").getLong("id");
		}

		@Override
		public JsonArray extractAttachmentsFromIssue(JsonObject issue) {
			return issue.getJsonObject("issue").getJsonArray("attachments", null);
		}

		@Override
		public BugTrackerSyncType getBugTrackerSyncType() {
			return BugTrackerSyncType.SYNC;
		}
	},
	PIVOT {
		@Override
		public Number getIssueId(JsonObject issue) {
			Number issueId;
			String issueIdString = this.extractIdFromIssueString(issue.getJsonObject(JiraTicket.ISSUE, new JsonObject()));
			try {
				issueId = Integer.parseInt(issueIdString);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				issueId = null;
			}
			return issueId;
		}

		@Override
		public String extractIdFromIssueString(JsonObject issue) {
			if (issue != null) {
				return issue.getString(JiraTicket.ID_ENT);
			}
			return "";
		}

		@Override
		public String getLastIssueUpdateFromPostgresqlJson() {
			return "->'issue'->>'date'";
		}

		@Override
		public String getStatusIdFromPostgresqlJson() {
			return "#>'{issue,status,id}'";
		}

		@Override
		public Number extractIdFromIssue(JsonObject issue) {
			if (issue != null) {
				return issue.getInteger("id_ent");
			}
			return 0;
		}

		@Override
		public JsonArray extractAttachmentsFromIssue(JsonObject issue) {
			//return issue.getObject("issue").getArray("attachments", null);
			return null;
		}

		@Override
		public BugTrackerSyncType getBugTrackerSyncType() {
			return BugTrackerSyncType.ASYNC;
		}
	};

	/**
	 * Extract "id" from JSON object sent by the bug tracker REST API
	 */
    public abstract String extractIdFromIssueString(JsonObject issue);

	/**
	 * @return SQL expression to extract last update time of bug tracker issue from JSON field stored in postgresql
	 */
	public abstract String getLastIssueUpdateFromPostgresqlJson();

	/**
	 * @return SQL expression to extract statusId of bug tracker issue from JSON field stored in postgresql
	 */
	public abstract String getStatusIdFromPostgresqlJson();

	/**
	 * Extract "id" from JSON object sent by the bug tracker REST API
	 */
	public abstract Number extractIdFromIssue(JsonObject issue);

	/**
	 * Extract "attachments" from JSON object sent by the bug tracker REST API
	 */
	public abstract JsonArray extractAttachmentsFromIssue(JsonObject issue);

	/**
	 * @return tracker sync type
	 */
	public abstract BugTrackerSyncType getBugTrackerSyncType();

    /***
     * @return issueid from the bugTracker
     */
	public abstract Number getIssueId(JsonObject issue);

}
