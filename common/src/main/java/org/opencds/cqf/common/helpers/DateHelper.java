package org.opencds.cqf.common.helpers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class DateHelper {

    // Helper class to resolve period dates
    public static Date resolveRequestDate(String date, boolean start) {
        // split it up - support dashes or slashes
        String[] dissect = date.contains("-") ? date.split("-") : date.split("/");
        List<Integer> dateVals = new ArrayList<>();
        for (String dateElement : dissect) {
            dateVals.add(Integer.parseInt(dateElement));
        }

        if (dateVals.isEmpty()) {
            throw new IllegalArgumentException("Invalid date");
        }

        // for now support dates up to day precision
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.setTimeZone(TimeZone.getDefault());
        calendar.set(Calendar.YEAR, dateVals.get(0));
        if (dateVals.size() > 1) {
            // java.util.Date months are zero based, hence the negative 1 -- 2014-01 == February 2014
            calendar.set(Calendar.MONTH, dateVals.get(1) - 1);
        }
        if (dateVals.size() > 2)
            calendar.set(Calendar.DAY_OF_MONTH, dateVals.get(2));
        else {
            if (start) {
                calendar.set(Calendar.DAY_OF_MONTH, 1);
            }
            else {
                // get last day of month for end period
                calendar.add(Calendar.MONTH, 1);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.add(Calendar.DATE, -1);
            }
        }
        return calendar.getTime();
    }

    public static Date increaseCurrentDate(String unit, BigDecimal value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        switch (unit) {
            //"s" is being used for testing
            case ("s") : calendar.add(Calendar.SECOND, value.intValue());
            case ("h") : calendar.add(Calendar.HOUR_OF_DAY, value.intValue()); break;
            case ("d") : calendar.add(Calendar.DAY_OF_MONTH, value.intValue()); break;
            case ("w") : calendar.add(Calendar.WEEK_OF_MONTH, value.intValue()); break;
            case ("m") : calendar.add(Calendar.MONTH, value.intValue()); break;
            default :
                throw new RuntimeException("Duration unit must be a DateTime unit. ");
        }
        return calendar.getTime();
    }

    public static Date increaseDate(String unit, BigDecimal value, Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        calendar.getTime();
        
        switch (unit) {
            //"s" is being used for testing
            case ("s") : calendar.add(Calendar.SECOND, value.intValue()); break;
            case ("h") : calendar.add(Calendar.HOUR_OF_DAY, value.intValue()); break;
            case ("d") : calendar.add(Calendar.DAY_OF_MONTH, value.intValue()); break;
            case ("w") : calendar.add(Calendar.WEEK_OF_MONTH, value.intValue()); break;
            case ("m") : calendar.add(Calendar.MONTH, value.intValue()); break;
            default :
                throw new RuntimeException("Duration unit must be a DateTime unit. ");
        }
        return calendar.getTime();
    }
}
