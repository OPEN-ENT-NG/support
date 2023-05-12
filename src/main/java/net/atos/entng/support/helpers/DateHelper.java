package net.atos.entng.support.helpers;


import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String MONGO_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private DateHelper() {
    }

    public static String convertDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT).format(new Date());
    }

    /**
     * Get Simple date as string
     *
     * @param date   date to format
     * @param format the format wished
     * @return Simple date format as string
     */
    public static String getDateString(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }
}
