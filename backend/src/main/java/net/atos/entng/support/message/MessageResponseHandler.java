package net.atos.entng.support.message;

import net.atos.entng.support.constants.*;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class MessageResponseHandler {

    private MessageResponseHandler() {
    }

    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonObjectHandler(Handler<Either<String, JsonObject>> handler) {
        return event -> {
            JsonObject body = event.result().body();
            String status = body.getString(JiraTicket.STATUS);
            String message = body.getString(JiraTicket.MESSAGE);
            JsonObject result = body.getJsonObject(JiraTicket.RESULT);
            if (event.succeeded() && JiraTicket.OK.equals(status)) {
                handler.handle(new Either.Right<>(result != null ? result : body));
            } else {
                handler.handle(new Either.Left<>(event.failed() ? event.cause().getMessage() : message));
            }
        };
    }
}