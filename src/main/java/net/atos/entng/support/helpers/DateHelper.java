package net.atos.entng.support.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private DateHelper() {
    }

    public static String convertDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT).format(new Date());
    }

}
