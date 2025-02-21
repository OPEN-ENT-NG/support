package net.atos.entng.support.helpers;

import net.atos.entng.support.enums.I18nKeys;
import fr.wseduc.webutils.I18n;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class I18nHelper {
    private static final Logger log = LoggerFactory.getLogger(I18nHelper.class);

    private I18nHelper() {}

    public static String getI18nValue(I18nKeys i18nKey, HttpServerRequest request) {
        return I18n.getInstance().translate(i18nKey.getValue(), I18n.DEFAULT_DOMAIN, I18n.acceptLanguage(request));
    }

    public static String getI18nValue(I18nKeys i18nKey, String locale) {
        return I18n.getInstance().translate(i18nKey.getValue(), I18n.DEFAULT_DOMAIN, locale);
    }

    public static <T> String getWithParams(I18nKeys key, List<T> params, HttpServerRequest request) {
        String i18n = getI18nValue(key, request);
        for(int i = 0; i < params.size(); i ++){
           i18n = i18n.replace("{" + i + "}", params.get(i).toString());
        }
        return i18n;
    }

    public static <T> String getWithParam(I18nKeys key, T param, HttpServerRequest request){
        List<T> finalParam = Collections.singletonList(param);
        return getWithParams(key, finalParam, request);
    }

}
