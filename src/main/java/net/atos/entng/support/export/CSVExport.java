package net.atos.entng.support.export;

import fr.wseduc.webutils.I18n;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class CSVExport {
    public static final Logger LOGGER = LoggerFactory.getLogger(CSVExport.class);
    private I18n i18n;
    protected StringBuilder value;
    protected String SEPARATOR;
    protected String EOL;

    protected String filename;

    private String host;
    private String acceptLanguage;

    public CSVExport(String host, String acceptLanguage) {
        this.i18n = I18n.getInstance();
        this.value = new StringBuilder("\uFEFF");
        this.SEPARATOR = ";";
        this.EOL = "\n";
        this.filename = "";
        this.host = host;
        this.acceptLanguage = acceptLanguage;
    }


    public String generate() {
        this.setHeader(header());
        return this.fillCSV();
    }

    public void setHeader(String header) {
        this.value.append(header).append(this.EOL);
    }

    public void setHeader(List<String> headers) {
        StringBuilder line = new StringBuilder();
        for (String head : headers) {
            line.append(this.translate(head))
                    .append(this.SEPARATOR);
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

    public abstract ArrayList<String> header();
}
