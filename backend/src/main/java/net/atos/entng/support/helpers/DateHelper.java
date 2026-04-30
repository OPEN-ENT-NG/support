package net.atos.entng.support.helpers;


import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateHelper {
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String MONGO_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DAY_MONTH_YEAR_HOUR_MINUTES = "dd/MM/yyyy - HH:mm";
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

    public static SimpleDateFormat getPsqlSimpleDateFormat() {
        return new SimpleDateFormat(SQL_FORMAT);
    }

    public static SimpleDateFormat getMongoSimpleDateFormat() {
        return new SimpleDateFormat(MONGO_FORMAT);
    }

    /**
     * Get Simple date as string
     *
     * @param date   date to format
     * @param format the format wished
     * @return Simple date format as string
     */
    public static String getDateString(String date, String format) {
        try {
            Date parsedDate = parse(date);
            return new SimpleDateFormat(format).format(parsedDate);
        } catch (ParseException err) {
            LOGGER.error("[Common@DateHelper::getDateString] Failed to parse date " + date, err);
            return date;
        }
    }

    /**
     * Utility function to convert java Date to TimeZone format
     *
     * @param date
     * @param format
     * @param timeZone
     * @return
     */
    public static String formatDateToString(String date, String format, String timeZone) {
        // create SimpleDateFormat object with input format
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        // default system timezone if passed null or empty
        if (timeZone == null || "".equalsIgnoreCase(timeZone.trim())) {
            timeZone = Calendar.getInstance().getTimeZone().getID();
        }
        // set timezone to SimpleDateFormat
        sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
        try {
            Date parsedDate = parse(date);
            return sdf.format(parsedDate);
        } catch (ParseException err) {
            LOGGER.error("[Common@DateHelper::getDateString] Failed to parse date " + date, err);
            return date;
        }


    }
}
