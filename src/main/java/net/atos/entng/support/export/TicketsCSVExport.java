package net.atos.entng.support.export;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.DateHelper;

import java.text.ParseException;
import java.util.Date;

public class TicketsCSVExport extends CSVExport {
    private final JsonArray tickets;
    private final String locale;
    private final String domain;

    public TicketsCSVExport(JsonArray tickets, String locale, String domain) {
        super();
        this.tickets = tickets;
        this.locale = locale;
        this.domain = domain;
        String date = DateHelper.getDateString(new Date(), DateHelper.MONGO_FORMAT);
        this.filename = String.format("%s - %s.csv", "export-tickets", date);
    }

    @Override
    public void generate() {
        for (int i = 0; i < this.tickets.size(); i++) { //A changer pour un foreach
            try {
                JsonObject ticket = this.tickets.getJsonObject(i);
                this.value.append(getLine(ticket));
            } catch (ParseException e) {
                LOGGER.error("[Incidents@TicketsCSVExport] Failed to parse line. Skipped", e);
            }
        }
    }

    private String getLine(JsonObject ticket) throws ParseException {
        String line = ticket.getInteger(Ticket.ID) + SEPARATOR;
        line += ticket.getString(Ticket.SCHOOL_ID) + SEPARATOR;
        line += ticket.getInteger(Ticket.STATUS) + SEPARATOR;
        line += ticket.getString(Ticket.SUBJECT) + SEPARATOR;
        line += ticket.getString(Ticket.CATEGORY) + SEPARATOR;
        line += ticket.getString(Ticket.PROFILE) + SEPARATOR;
        line += ticket.getString(Ticket.CREATION_DATE) + SEPARATOR;
        line += ticket.getString(Ticket.MODIFICATION_DATE) + SEPARATOR;

        return line + EOL;
    }
}
