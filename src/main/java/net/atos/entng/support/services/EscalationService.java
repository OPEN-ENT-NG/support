package net.atos.entng.support.services;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public interface EscalationService {

	public void escalateTicket(HttpServerRequest request, JsonObject ticket,
			JsonArray comments, JsonArray attachments, Handler<JsonObject> handler);

	public void listTickets(Handler<JsonObject> handler);

	public void getTicket(int issueId, Handler<JsonObject> handler);
}
