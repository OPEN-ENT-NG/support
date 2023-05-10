package net.atos.entng.support.export;

import fr.wseduc.webutils.I18n;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.DateHelper;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class TicketsCSVExport extends CSVExport {
    private final JsonArray tickets;
    private String acceptLanguage;
    public TicketsCSVExport(JsonArray tickets, String host, String acceptLanguage) {
        super(host, acceptLanguage);

        this.tickets = tickets;
        String date = DateHelper.getDateString(new Date(), DateHelper.MONGO_FORMAT);
        this.filename = this.translate(String.format("%s - %s.csv", this.translate("support.ticket.export.filename"), date));
        this.acceptLanguage = acceptLanguage;
    }

    @Override
    public ArrayList<String> header() {
        return new ArrayList<>(Arrays.asList(
                "support.ticket.table.id",
                "support.ticket.table.school",
                "support.ticket.status",
                "support.ticket.table.subject",
                "support.ticket.table.category",
                "support.label.profil",
                "support.ticket.creation.date",
                "support.ticket.modification.date"
        ));
    }

    @Override
    public String fillCSV() {
        this.tickets.stream()
                .map(ticket -> {
                    try {
                        return getLine((JsonObject) ticket);
                    } catch (ParseException e) {
                        LOGGER.error("[Incidents@TicketsCSVExport] Failed to parse line. Skipped", e);
                        return null;
                    }
                })
                .forEach(line -> this.value.append(line));
        return value.toString();
    }

    private String getLine(JsonObject ticket) throws ParseException {
        String line = ticket.getInteger(Ticket.ID) + SEPARATOR;
        line += ticket.getString(Ticket.SCHOOL_ID) + SEPARATOR;
        line += ticket.getInteger(Ticket.STATUS) + SEPARATOR;
        line += ticket.getString(Ticket.SUBJECT) + SEPARATOR;
        line += translateCategory(ticket.getString(Ticket.CATEGORY),acceptLanguage) + SEPARATOR;
        line += ticket.getString(Ticket.PROFILE) + SEPARATOR;
        line += ticket.getString(Ticket.CREATION_DATE) + SEPARATOR;
        line += ticket.getString(Ticket.MODIFICATION_DATE) + SEPARATOR;

        return line + EOL;
    }
}
