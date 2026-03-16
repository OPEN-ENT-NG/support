package net.atos.entng.support.services;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public interface FileService {

    /**
     * Add file in file systeme based on given buffer
     *
     * @param file        File buffer
     * @param contentType File content type
     * @param filename    Filename
     */
    Future<JsonObject> add(Buffer file, String contentType, String filename);
}
