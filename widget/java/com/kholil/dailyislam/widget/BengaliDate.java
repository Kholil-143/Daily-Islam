package com.kholil.dailyislam.widget;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Bengali calendar (the revised calendar used in Bangladesh) conversion,
 * plus Bengali-digit number formatting for the widget.
 *
 * Rules used: Boishakh 1 = 14 April. Month lengths are fixed
 * (31,31,31,31,31,30,30,30,30,30,29,30); Falgun has 30 days when the
 * Gregorian February inside the same Bengali year is a leap February.
 */
public final class BengaliDate {

    public static final String[] MONTHS = {
            "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
            "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    };

    private static final int[] MONTH_LENGTHS = {31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29, 30};

    private static final char[] BN_DIGITS = {'০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'};

    private BengaliDate() {
    }

    /**
     * Convert a Gregorian date to Bengali.
     *
     * @return {dayOfMonth (1-based), monthIndex (0 = Boishakh), bengaliYear}
     */
    public static int[] fromGregorian(int gregYear, int gregMonth, int gregDay) {
        // The Bengali year containing this date starts on 14 April of startYear.
        int startYear = (gregMonth > 4 || (gregMonth == 4 && gregDay >= 14)) ? gregYear : gregYear - 1;
        int bengaliYear = startYear - 593;

        long days = dayDiff(startYear, 4, 14, gregYear, gregMonth, gregDay);

        int[] lengths = MONTH_LENGTHS.clone();
        // Falgun of this Bengali year falls in February of startYear + 1.
        if (isLeapYear(startYear + 1)) {
            lengths[10] = 30;
        }

        int month = 0;
        while (month < 11 && days >= lengths[month]) {
            days -= lengths[month];
            month++;
        }
        return new int[]{(int) days + 1, month, bengaliYear};
    }

    /** Format a fractional-hour time (e.g. 17.118) as "৫:০৭ PM" with Bengali digits. */
    public static String formatTime(double hours) {
        if (Double.isNaN(hours)) {
            return "--:--";
        }
        int totalMinutes = (int) Math.round(hours * 60.0);
        totalMinutes = ((totalMinutes % 1440) + 1440) % 1440;
        int hh = totalMinutes / 60;
        int mm = totalMinutes % 60;
        String ampm = hh < 12 ? "AM" : "PM";
        int h12 = hh % 12;
        if (h12 == 0) {
            h12 = 12;
        }
        return bnDigits(h12 + ":" + (mm < 10 ? "0" : "") + mm) + " " + ampm;
    }

    /** Format a Calendar's date as a Bengali date string, e.g. "১০ অগ্রহায়ণ ১৪৩২". */
    public static String formatBengaliDate(Calendar cal) {
        int[] bd = fromGregorian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        return bnNum(bd[0]) + " " + MONTHS[bd[1]] + " " + bnNum(bd[2]);
    }

    public static String bnNum(int n) {
        return bnDigits(Integer.toString(n));
    }

    public static String bnDigits(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c >= '0' && c <= '9' ? BN_DIGITS[c - '0'] : c);
        }
        return out.toString();
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    /** Whole days from date 1 to date 2 (both Gregorian), computed in UTC to avoid DST drift. */
    private static long dayDiff(int y1, int m1, int d1, int y2, int m2, int d2) {
        GregorianCalendar a = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        a.clear();
        a.set(y1, m1 - 1, d1);
        GregorianCalendar b = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        b.clear();
        b.set(y2, m2 - 1, d2);
        return Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }
}
