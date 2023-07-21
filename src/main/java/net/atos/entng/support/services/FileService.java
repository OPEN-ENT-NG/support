package net.atos.entng.support.services;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

public interface FileService {

    /**
     * Get file based on provided Id
     *
     * @param fileId  File id
     * @param handler Function handler returning data
     */
    void get(String fileId, Handler<Buffer> handler);

    /**
     * Add file in file system
     *
     * @param request     Server request uploading file
     * @param contentType Content type file
     * @param filename    Filename
     */
    Future<JsonObject> add(HttpServerRequest request, String contentType, String filename);

    /**
     * Add file in file system from http request
     *
     * @param request Http request uploading file
     */
    Future<JsonObject> add(HttpServerRequest request);

    /**
     * Add file in file systeme based on given buffer
     *
     * @param file        File buffer
     * @param contentType File content type
     * @param filename    Filename
     */
    Future<JsonObject> add(Buffer file, String contentType, String filename);
}
