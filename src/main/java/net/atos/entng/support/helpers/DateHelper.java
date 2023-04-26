package net.atos.entng.support.helpers;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String MONGO_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String SQL_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final Logger LOGGER = LoggerFactory.getLogger(DateHelper.class);

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

    public static Date parse(String date) throws ParseException {
        SimpleDateFormat ssdf = DateHelper.getPsqlSimpleDateFormat();
        SimpleDateFormat msdf = DateHelper.getMongoSimpleDateFormat();
        return date.contains("T") ? ssdf.parse(date) : msdf.parse(date);
    }

    /**
     * Get Simple format date as PostgreSQL timestamp without timezone format
     *
     * @return Simple date format
     */
    public static SimpleDateFormat getPsqlSimpleDateFormat() {
        return new SimpleDateFormat(SQL_FORMAT);
    }

    public static SimpleDateFormat getMongoSimpleDateFormat() {
        return new SimpleDateFormat(MONGO_FORMAT);
    }
}
