package net.atos.entng.support.helpers;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {

    private DateHelper() {
    }

    public static String convertDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new Date());
    }

}
