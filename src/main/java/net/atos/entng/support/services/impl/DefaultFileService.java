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
    public void get(String fileId, Handler<Buffer> handler) {
        storage.readFile(fileId, handler);
    }

    @Override
    public Future<JsonObject> add(HttpServerRequest request, String contentType, String filename) {
        Promise<JsonObject> promise = Promise.promise();
        Buffer responseBuffer = new BufferImpl();
        request.handler(responseBuffer::appendBuffer);
        request.endHandler(aVoid -> storage.writeBuffer(responseBuffer, contentType, filename, entries -> {
            if (Ticket.OK.equals(entries.getString(Ticket.STATUS))) {
                promise.complete(entries);
            } else {
                promise.fail("[Common@DefaultFileService::add] An error occurred while writing file in the storage");
            }
        }));
        request.exceptionHandler(throwable -> promise.fail("[Common@DefaultFileService::add] An error occurred when uploading file"));
        return promise.future();
    }

    @Override
    public Future<JsonObject> add(HttpServerRequest request) {
        Promise<JsonObject> promise = Promise.promise();
        storage.writeUploadFile(request, message -> {
            if (!Ticket.OK.equals(message.getString(Ticket.STATUS))) {
                promise.fail("[Common@DefaultFileService::add] Failed to upload file from http request");
            } else {
                message.remove(Ticket.STATUS);
                promise.complete(message);
            }
        });
        return promise.future();
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
