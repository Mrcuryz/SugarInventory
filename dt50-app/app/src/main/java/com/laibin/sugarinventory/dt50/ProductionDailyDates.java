package com.laibin.sugarinventory.dt50;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

final class ProductionDailyDates {
    private static final Locale LOCALE = Locale.CHINA;

    private ProductionDailyDates() {
    }

    static String today() {
        return format(Calendar.getInstance());
    }

    static String shiftDay(String date, int offset) {
        Calendar calendar = parse(date);
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        return format(calendar);
    }

    static String shiftMonth(String month, int offset) {
        Calendar calendar = parse(month + "-01");
        calendar.add(Calendar.MONTH, offset);
        return format(calendar).substring(0, 7);
    }

    static int daysInMonth(String month) {
        return parse(month + "-01").getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    static int mondayOffset(String month) {
        return (parse(month + "-01").get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    static String weekday(String date) {
        int day = parse(date).get(Calendar.DAY_OF_WEEK);
        return "日一二三四五六".substring(day - 1, day);
    }

    static String display(String date) {
        Calendar calendar = parse(date);
        return calendar.get(Calendar.YEAR) + "年" + (calendar.get(Calendar.MONTH) + 1) + "月"
                + calendar.get(Calendar.DAY_OF_MONTH) + "日";
    }

    static Calendar parse(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("日期格式无效");
        }
        int year = Integer.parseInt(date.substring(0, 4));
        int month = Integer.parseInt(date.substring(5, 7));
        int day = Integer.parseInt(date.substring(8, 10));
        Calendar calendar = new GregorianCalendar(LOCALE);
        calendar.clear();
        calendar.setLenient(false);
        calendar.set(year, month - 1, day, 12, 0, 0);
        calendar.getTime();
        return calendar;
    }

    private static String format(Calendar calendar) {
        return new SimpleDateFormat("yyyy-MM-dd", LOCALE).format(calendar.getTime());
    }
}
