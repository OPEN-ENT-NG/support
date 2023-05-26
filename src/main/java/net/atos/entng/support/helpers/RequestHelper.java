package net.atos.entng.support.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.model.IModel;

import java.util.List;
import java.util.stream.Collectors;

public class RequestHelper {
    private RequestHelper() {
        throw new IllegalStateException("Utility class");
    }
    public static JsonObject addAllValue(JsonObject jsonObject, List<? extends IModel<?>> dataList) {
        return jsonObject
                .put(Ticket.ALL, new JsonArray(dataList.stream().map(IModel::toJson).collect(Collectors.toList())));
    }
}
