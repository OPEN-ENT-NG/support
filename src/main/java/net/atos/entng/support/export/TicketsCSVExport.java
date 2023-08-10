package net.atos.entng.support.export;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import net.atos.entng.support.constants.Ticket;
import net.atos.entng.support.helpers.DateHelper;
import net.atos.entng.support.helpers.UserInfosHelper;
import net.atos.entng.support.model.I18nConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

public class TicketsCSVExport extends CSVExport {
    private final JsonArray tickets;
    private final String acceptLanguage;

    public TicketsCSVExport(JsonArray tickets, I18nConfig i18nConfig) {
        super(i18nConfig);

        this.tickets = tickets;
        SimpleDateFormat sdf = new SimpleDateFormat(DateHelper.MONGO_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(Ticket.PARIS_TIMEZONE));

        this.filename = this.translate(String.format("%s - %s.csv", this.translate("support.ticket.export.filename"), sdf.format(new Date())));
        this.acceptLanguage = i18nConfig.getLang();
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
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(this::getLine)
                .forEach(line -> this.value.append(line));
        return value.toString();
    }

    private String getLine(JsonObject ticket) {
        String line = ticket.getInteger(Ticket.ID) + separator;
        line += ticket.getString(Ticket.SCHOOL) + separator;
        line += formatStatus(ticket.getInteger(Ticket.STATUS), acceptLanguage) + separator;
        line += ticket.getString(Ticket.SUBJECT) + separator;
        line += ticket.getString(Ticket.CATEGORY) + separator;
        line += ticket.getString(Ticket.PROFILE) + separator;
        line += DateHelper.formatDateToString(ticket.getString(Ticket.CREATION_DATE), DateHelper.DAY_MONTH_YEAR_HOUR_MINUTES, Ticket.PARIS_TIMEZONE) + separator;
        line += DateHelper.formatDateToString(ticket.getString(Ticket.MODIFICATION_DATE), DateHelper.DAY_MONTH_YEAR_HOUR_MINUTES, Ticket.PARIS_TIMEZONE) + separator;

        return line + eol;
    }
}
