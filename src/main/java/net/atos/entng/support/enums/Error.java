package net.atos.entng.support.enums;

import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;

public enum Error {
    SORT_BY_STRUCTURE("support.error.sort.tickets.by.school.name");

    private final String i18n;

    Error(String i18n) {
        this.i18n = i18n;
    }

    public String getI18n() {
        return i18n;
    }

    public JsonObject toJson(){
        return new JsonObject()
                .put(Ticket.SLUG, this.name())
                .put(Ticket.I18N, this.i18n);
    }

}
