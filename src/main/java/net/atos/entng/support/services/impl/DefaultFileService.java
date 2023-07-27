package net.atos.entng.support.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferImpl;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.services.FileService;
import org.entcore.common.storage.Storage;

public class DefaultFileService implements FileService {

    private final Storage storage;

    public DefaultFileService(Storage storage) {
        this.storage = storage;
    }

    @Override
    public Future<JsonObject> add(Buffer file, String contentType, String filename) {
        Promise<JsonObject> promise = Promise.promise();
        storage.writeBuffer(file, contentType, filename, message -> {
            if (!Ticket.OK.equals(message.getString(Ticket.STATUS))) {
                promise.fail("[Common@DefaultFileService::add] Failed to upload file from buffer");
            } else {
                message.remove(Ticket.STATUS);
                promise.complete(message);
            }
        });
        return promise.future();
    }

}
