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

    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonArrayHandler(Handler<Either<String, JsonArray>> handler) {
        return event -> {
            if (event.succeeded() && Ticket.OK.equals(event.result().body().getString(Ticket.STATUS))) {
                handler.handle(new Either.Right<>(event.result().body().getJsonArray(Ticket.RESULT, event.result().body().getJsonArray(Ticket.RESULTS))));
            } else {
                handler.handle(new Either.Left<>(event.failed() ? event.cause().getMessage() : event.result().body().getString(Ticket.MESSAGE)));
            }
        };
    }

    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonObjectHandler(Handler<Either<String, JsonObject>> handler) {
        return event -> {
            JsonObject body = event.result().body();
            String status = body.getString(Ticket.STATUS);
            String message = body.getString(Ticket.MESSAGE);
            JsonObject result = body.getJsonObject(Ticket.RESULT);
            if (event.succeeded() && Ticket.OK.equals(status)) {
                handler.handle(new Either.Right<>(result != null ? result : body));
            } else {
                handler.handle(new Either.Left<>(event.failed() ? event.cause().getMessage() : message));
            }
        };
    }
}
