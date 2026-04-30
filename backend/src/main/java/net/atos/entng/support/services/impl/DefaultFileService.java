package net.atos.entng.support.services.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.services.FileService;
import org.entcore.common.storage.Storage;

public class DefaultFileService implements FileService {

    private final Storage storage;
    private final Logger log = LoggerFactory.getLogger(DefaultFileService.class);

    public DefaultFileService(Storage storage) {
        this.storage = storage;
    }

    @Override
    public Future<JsonObject> add(Buffer file, String contentType, String filename) {
        Promise<JsonObject> promise = Promise.promise();
        storage.writeBuffer(file, contentType, filename, result -> {
            if (!JiraTicket.OK.equals(result.getString(JiraTicket.STATUS))) {
                log.error(String.format("[Support@%s::add] Failed to upload file from buffer %s",
                        this.getClass().getSimpleName(), result.getString(JiraTicket.STATUS)));
                promise.fail("support.fail.upload.file");
            } else {
                result.remove(JiraTicket.STATUS);
                promise.complete(result);
            }
        });
        return promise.future();
    }

}