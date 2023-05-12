package net.atos.entng.support.helpers;

import io.vertx.core.http.HttpServerRequest;

public class CSVHelper {

    private CSVHelper() {
        throw new IllegalStateException("Utility class");
    }

    public static void sendCSV(HttpServerRequest request, String filename, String csv) {
        request.response()
                .putHeader("Content-Type", "text/csv; charset=utf-8")
                .putHeader("Content-Disposition", "attachment; filename=" + filename)
                .end(csv);
    }

}