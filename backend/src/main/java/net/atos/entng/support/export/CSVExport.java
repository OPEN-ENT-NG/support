package net.atos.entng.support.export;

import fr.wseduc.webutils.I18n;
import net.atos.entng.support.constants.JiraTicket;
import net.atos.entng.support.model.I18nConfig;

import java.util.List;

public abstract class CSVExport {

    private final I18n i18n;
    protected StringBuilder value;
    protected String separator;
    protected String eol;

    protected String filename;

    private final String host;
    private final String acceptLanguage;

    protected CSVExport(I18nConfig i18nConfig) {
        this.i18n = I18n.getInstance();
        this.value = new StringBuilder("\uFEFF");
        this.separator = ";";
        this.eol = "\n";
        this.filename = "";
        this.host = i18nConfig.getDomain();
        this.acceptLanguage = i18nConfig.getLang();
    }


    public String generate() {
        this.setHeader(header());
        return this.fillCSV();
    }

    public void setHeader(String header) {
        this.value.append(header).append(this.eol);
    }

    public void setHeader(List<String> headers) {
        StringBuilder line = new StringBuilder();
        for (String head : headers) {
            line.append(this.translate(head))
                    .append(this.separator);
        }
        this.setHeader(line.toString());
    }

    public String translate(String key) {
        return i18n.translate(key, this.host, this.acceptLanguage);
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String filename() {
        return this.filename;
    }

    public abstract String fillCSV();

    public abstract List<String> header();

    public String translateCategory(String category, String locale) {
        if (category.startsWith("/")){
            final String categoryRes = i18n.translate(category.replace("/", ""), I18n.DEFAULT_DOMAIN, locale);
            return (categoryRes != null && !categoryRes.isEmpty()) ? categoryRes : i18n.translate(JiraTicket.OTHER, I18n.DEFAULT_DOMAIN, locale);
        }else {
            return JiraTicket.BLANK;
        }

    }

    public String formatStatus(int status, String locale) {
        final String key;
        switch (status) {
            case 1 : key="support.ticket.status.new";
                break;
            case 2 : key="support.ticket.status.opened";
                break;
            case 3 : key="support.ticket.status.resolved";
                break;
            case 5 : key="support.ticket.status.waiting";
                break;
            default: key="support.ticket.status.closed";
                break;
        }

        return i18n.translate(key, I18n.DEFAULT_DOMAIN, locale);
    }
}