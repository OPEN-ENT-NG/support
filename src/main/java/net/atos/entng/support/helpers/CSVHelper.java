package net.atos.entng.support.helpers;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.model.ExportFile;
import org.entcore.common.user.UserInfos;

import java.util.stream.Collectors;

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

    public static ExportFile getExportFile(String filename, String csv) {
        Buffer buffer = Buffer.buffer(csv);
        return new ExportFile(buffer, "text/csv; charset=utf-8", filename);
    }

    public static JsonArray translateTicketCategory(UserInfos user, JsonArray ticketsResults) {
        return new JsonArray(ticketsResults.stream().filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast).map(ticket -> {
                    ticket.put(Ticket.CATEGORY, UserInfosHelper.getAppName(user, ticket.getString(Ticket.CATEGORY)));
                    return ticket;
                })
                .collect(Collectors.toList()));
    }

}