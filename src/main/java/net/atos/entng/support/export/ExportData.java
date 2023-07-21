package net.atos.entng.support.export;
import net.atos.entng.support.db.*;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;

public class ExportData extends DBService {

    private final Vertx vertx;

    public ExportData(Vertx vertx) {
        this.vertx = vertx;
    }

    public void export(String workerName, String action, JsonObject params) {

        JsonObject configWorker = new JsonObject()
                .put(Ticket.ACTION, action)
                .put(Ticket.PARAMS, params);

        vertx.eventBus().send(workerName, configWorker,
                new DeliveryOptions().setSendTimeout(1000 * 1000L));
    }

}
