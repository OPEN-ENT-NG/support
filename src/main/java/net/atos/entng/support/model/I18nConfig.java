package net.atos.entng.support.model;

import fr.wseduc.webutils.I18n;
import io.vertx.core.http.HttpServerRequest;

import static fr.wseduc.webutils.http.Renders.getHost;

public class I18nConfig {

    private final String domain;
    private final String lang;

    public I18nConfig(HttpServerRequest request) {
        this.domain = getHost(request);
        this.lang = I18n.acceptLanguage(request);
    }

    public I18nConfig(String domain, String lang) {
        this.domain = domain;
        this.lang = lang;
    }

    public String getDomain() {
        return domain;
    }

    public String getLang() {
        return lang;
    }
}
