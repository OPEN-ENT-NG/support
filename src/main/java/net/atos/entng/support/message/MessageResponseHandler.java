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
                if (event.failed()) {
                    handler.handle(new Either.Left<>(event.cause().getMessage()));
                    return;
                }
                handler.handle(new Either.Left<>(event.result().body().getString(Ticket.MESSAGE)));
            }
        };
    }

    public static Handler<AsyncResult<Message<JsonObject>>> messageJsonObjectHandler(Handler<Either<String, JsonObject>> handler) {
        return event -> {
            if (event.succeeded() && Ticket.OK.equals(event.result().body().getString(Ticket.STATUS))) {
                if (!event.result().body().containsKey(Ticket.RESULT))
                    handler.handle(new Either.Right<>(event.result().body()));
                else
                    handler.handle(new Either.Right<>(event.result().body().getJsonObject(Ticket.RESULT)));
            } else {
                if (event.failed()) {
                    handler.handle(new Either.Left<>(event.cause().getMessage()));
                    return;
                }
                handler.handle(new Either.Left<>(event.result().body().getString(Ticket.MESSAGE)));
            }
        };
    }
}
