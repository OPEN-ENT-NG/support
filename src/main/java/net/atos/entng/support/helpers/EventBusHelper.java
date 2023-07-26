package net.atos.entng.support.helpers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.message.MessageResponseHandler;

public class EventBusHelper {

    private EventBusHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Call event bus with action
     * @param address   EventBus address
     * @param eb        EventBus
     * @param action    The action to perform
     * @return          Future with the body of the response from the eb
     */
    public static Future<JsonObject> requestJsonObject(String address, EventBus eb, JsonObject action) {
        Promise<JsonObject> promise = Promise.promise();
        eb.request(address, action, MessageResponseHandler.messageJsonObjectHandler(PromiseHelper.handler(promise)));
        return promise.future();
    }

    public static Future<JsonArray> requestJsonArray(String address, EventBus eb, JsonObject action) {
        Promise<JsonArray> promise = Promise.promise();
        eb.request(address, action, MessageResponseHandler.messageJsonArrayHandler(PromiseHelper.handler(promise)));
        return promise.future();
    }


}
