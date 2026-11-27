package com.kholil.muslimpath.widget;

/**
 * Prayer-time calculation using the standard solar-position algorithm
 * (the same method as PrayTimes). Fixed settings, matching the Daily Islam app:
 *   - Fajr angle 18 degrees, Isha angle 18 degrees
 *     (Islamic Foundation Bangladesh convention)
 *   - Asr: Hanafi school (shadow factor 2)
 *   - Sunrise / sunset depression angle 0.833 degrees
 * Returned times are fractional hours in local time (e.g. 5.5 = 05:30).
 */
public final class PrayerTimes {

    public static final class Result {
        public double fajr;
        public double sunrise;
        public double dhuhr;
        public double asr;
        public double maghrib;
        public double isha;
    }

    private static final double FAJR_ANGLE = 18.0;
    private static final double ISHA_ANGLE = 18.0;
    private static final double SUN_ANGLE = 0.833;
    private static final double ASR_FACTOR = 2.0; // Hanafi

    private PrayerTimes() {
    }

    /**
     * @param year     Gregorian year
     * @param month    Gregorian month (1-12)
     * @param day      Gregorian day of month
     * @param latitude degrees, north positive
     * @param longitude degrees, east positive
     * @param timeZone local time zone offset from UTC in hours (e.g. 6 for Dhaka)
     */
    public static Result compute(int year, int month, int day,
                                 double latitude, double longitude, double timeZone) {
        double jd = julian(year, month, day) - longitude / (15.0 * 24.0);

        // Two passes: the second pass uses the first pass's times as the
        // day-portions, which makes the sun-position sampling more accurate.
        double[] portion = {5.0 / 24, 6.0 / 24, 12.0 / 24, 13.0 / 24, 18.0 / 24, 18.0 / 24};
        Result r = computeOnce(jd, latitude, portion);
        double[] refined = {r.fajr / 24, r.sunrise / 24, r.dhuhr / 24,
                r.asr / 24, r.maghrib / 24, r.isha / 24};
        r = computeOnce(jd, latitude, refined);

        double adjust = timeZone - longitude / 15.0;
        r.fajr = fixHour(r.fajr + adjust);
        r.sunrise = fixHour(r.sunrise + adjust);
        r.dhuhr = fixHour(r.dhuhr + adjust);
        r.asr = fixHour(r.asr + adjust);
        r.maghrib = fixHour(r.maghrib + adjust);
        r.isha = fixHour(r.isha + adjust);
        // Islamic Foundation calendar precautionary (ihtiyat) adjustments,
        // matching the app: Fajr +1, Dhuhr +3, Maghrib +3, Isha +1 minutes.
        r.fajr = fixHour(r.fajr + 1.0 / 60.0);
        r.dhuhr = fixHour(r.dhuhr + 3.0 / 60.0);
        r.maghrib = fixHour(r.maghrib + 3.0 / 60.0);
        r.isha = fixHour(r.isha + 1.0 / 60.0);
        return r;
    }

    private static Result computeOnce(double jd, double latitude, double[] portion) {
        Result r = new Result();
        r.fajr = computeTime(180.0 - FAJR_ANGLE, jd, latitude, portion[0]);
        r.sunrise = computeTime(180.0 - SUN_ANGLE, jd, latitude, portion[1]);
        r.dhuhr = midDay(jd, portion[2]);
        r.asr = computeAsr(ASR_FACTOR, jd, latitude, portion[3]);
        r.maghrib = computeTime(SUN_ANGLE, jd, latitude, portion[4]);
        r.isha = computeTime(ISHA_ANGLE, jd, latitude, portion[5]);
        return r;
    }

    /** Time at which the sun reaches the given angle (degrees) below/above the horizon. */
    private static double computeTime(double angle, double jd, double latitude, double portion) {
        double decl = sunDeclination(jd + portion);
        double noon = midDay(jd, portion);
        double cosH = (-sinDeg(angle) - sinDeg(latitude) * sinDeg(decl))
                / (cosDeg(latitude) * cosDeg(decl));
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        double hourAngle = acosDeg(cosH) / 15.0;
        return angle > 90.0 ? noon - hourAngle : noon + hourAngle;
    }

    /** Asr time for the given shadow factor (1 = Shafi'i, 2 = Hanafi). */
    private static double computeAsr(double factor, double jd, double latitude, double portion) {
        double decl = sunDeclination(jd + portion);
        double angle = -atanDeg(1.0 / (factor + tanDeg(Math.abs(latitude - decl))));
        return computeTime(angle, jd, latitude, portion);
    }

    private static double midDay(double jd, double portion) {
        return fixHour(12.0 - equationOfTime(jd + portion));
    }

    // ---- Sun position ----------------------------------------------------

    private static double sunDeclination(double jd) {
        double[] pos = sunPosition(jd);
        return pos[0];
    }

    private static double equationOfTime(double jd) {
        double[] pos = sunPosition(jd);
        return pos[1];
    }

    /** @return {declination in degrees, equation of time in hours} */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = fixHour(atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0);
        double decl = asinDeg(sinDeg(e) * sinDeg(l));
        double eqt = q / 15.0 - ra;
        return new double[]{decl, eqt};
    }

    /** Julian day number for the Gregorian date (at 00:00). */
    private static double julian(int year, int month, int day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716))
                + Math.floor(30.6001 * (m + 1))
                + day + b - 1524.5;
    }

    // ---- Degree-based trig helpers ---------------------------------------

    private static double sinDeg(double d) {
        return Math.sin(Math.toRadians(d));
    }

    private static double cosDeg(double d) {
        return Math.cos(Math.toRadians(d));
    }

    private static double tanDeg(double d) {
        return Math.tan(Math.toRadians(d));
    }

    private static double asinDeg(double x) {
        return Math.toDegrees(Math.asin(x));
    }

    private static double acosDeg(double x) {
        return Math.toDegrees(Math.acos(x));
    }

    private static double atanDeg(double x) {
        return Math.toDegrees(Math.atan(x));
    }

    private static double atan2Deg(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    private static double fixAngle(double a) {
        double r = a % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static double fixHour(double h) {
        double r = h % 24.0;
        return r < 0 ? r + 24.0 : r;
    }
}
