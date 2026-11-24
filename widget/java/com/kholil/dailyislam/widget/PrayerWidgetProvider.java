package com.kholil.dailyislam.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

import com.kholil.dailyislam.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Daily Islam home-screen widget.
 *
 * Shows a live clock (TextClock), the Gregorian + Bengali + Hijri dates,
 * the chosen district's prayer times for today, the next prayer with the
 * remaining time (calculated at update time), and a refresh button.
 * The district is chosen in {@link WidgetConfigActivity} and stored in
 * SharedPreferences; Dhaka is the default.
 */
public class PrayerWidgetProvider extends AppWidgetProvider {

    public static final String PREFS_NAME = "daily_islam_widget";
    public static final String KEY_DISTRICT = "district_name";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";

    private static final String ACTION_REFRESH = "com.kholil.dailyislam.widget.ACTION_REFRESH";

    private static final double DEFAULT_LATITUDE = 23.8103; // Dhaka
    private static final double DEFAULT_LONGITUDE = 90.4125;
    private static final String DEFAULT_DISTRICT = "ঢাকা";

    private static final String[] WEEKDAYS_BN = {
            "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
    };

    private static final String[] HIJRI_MONTHS = {
            "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
            "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ্জ"
    };

    private static final String[] PRAYER_NAMES = {"ফজর", "সূর্যোদয়", "যোহর", "আসর", "মাগরিব", "এশা"};

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidgetProvider.class));
            onUpdate(context, manager, ids);
        }
    }

    /** Refresh every placed widget (used after the district changes). */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidgetProvider.class));
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String district = prefs.getString(KEY_DISTRICT, DEFAULT_DISTRICT);
        double latitude = prefs.getFloat(KEY_LATITUDE, (float) DEFAULT_LATITUDE);
        double longitude = prefs.getFloat(KEY_LONGITUDE, (float) DEFAULT_LONGITUDE);

        Calendar now = Calendar.getInstance();
        double timeZone = TimeZone.getDefault().getOffset(now.getTimeInMillis()) / 3600000.0;

        PrayerTimes.Result times = PrayerTimes.compute(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
                latitude, longitude, timeZone);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prayer);

        // Dates: weekday + Gregorian / Bengali / Hijri
        String weekday = WEEKDAYS_BN[now.get(Calendar.DAY_OF_WEEK) - 1];
        String gregorian = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(now.getTime());
        String bengali = BengaliDate.formatBengaliDate(now);
        String hijri = hijriDate(now);
        StringBuilder dates = new StringBuilder();
        dates.append(weekday).append(" • ").append(gregorian).append('\n').append(bengali);
        if (!hijri.isEmpty()) {
            dates.append(" • ").append(hijri);
        }
        views.setTextViewText(R.id.tv_dates, dates.toString());
        views.setTextViewText(R.id.tv_district, "জেলা: " + district);

        // Prayer times
        views.setTextViewText(R.id.tv_fajr, BengaliDate.formatTime(times.fajr));
        views.setTextViewText(R.id.tv_sunrise, BengaliDate.formatTime(times.sunrise));
        views.setTextViewText(R.id.tv_dhuhr, BengaliDate.formatTime(times.dhuhr));
        views.setTextViewText(R.id.tv_asr, BengaliDate.formatTime(times.asr));
        views.setTextViewText(R.id.tv_maghrib, BengaliDate.formatTime(times.maghrib));
        views.setTextViewText(R.id.tv_isha, BengaliDate.formatTime(times.isha));

        // Next prayer + remaining time (at this update moment)
        views.setTextViewText(R.id.tv_next, nextPrayerText(now, times, latitude, longitude, timeZone));

        // Refresh button
        Intent refreshIntent = new Intent(context, PrayerWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending);

        // Tapping the widget body opens the app
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            PendingIntent launchPending = PendingIntent.getActivity(context, 1, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, launchPending);
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    private static String nextPrayerText(Calendar now, PrayerTimes.Result times,
                                         double latitude, double longitude, double timeZone) {
        double[] all = {times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha};
        double nowHours = now.get(Calendar.HOUR_OF_DAY)
                + now.get(Calendar.MINUTE) / 60.0
                + now.get(Calendar.SECOND) / 3600.0;

        for (int i = 0; i < all.length; i++) {
            if (all[i] > nowHours) {
                return "পরের ওয়াক্ত: " + PRAYER_NAMES[i] + " " + BengaliDate.formatTime(all[i])
                        + " • বাকি " + remainingText(all[i] - nowHours);
            }
        }
        // After Isha: next is tomorrow's Fajr.
        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        PrayerTimes.Result t = PrayerTimes.compute(
                tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH) + 1,
                tomorrow.get(Calendar.DAY_OF_MONTH), latitude, longitude, timeZone);
        return "পরের ওয়াক্ত: ফজর " + BengaliDate.formatTime(t.fajr)
                + " • বাকি " + remainingText((24.0 - nowHours) + t.fajr);
    }

    private static String remainingText(double hours) {
        int minutes = (int) Math.ceil(hours * 60.0);
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0) {
            return BengaliDate.bnNum(h) + " ঘণ্টা " + BengaliDate.bnNum(m) + " মিনিট";
        }
        return BengaliDate.bnNum(m) + " মিনিট";
    }

    /** Hijri date via the platform Islamic calendar (API 24+), e.g. "৫ জমাদিউস সানি ১৪৪৭ হিজরি". */
    private static String hijriDate(Calendar now) {
        if (Build.VERSION.SDK_INT < 24) {
            return "";
        }
        try {
            android.icu.util.IslamicCalendar islamic = new android.icu.util.IslamicCalendar();
            islamic.setTimeInMillis(now.getTimeInMillis());
            int day = islamic.get(android.icu.util.Calendar.DAY_OF_MONTH);
            int month = islamic.get(android.icu.util.Calendar.MONTH);
            int year = islamic.get(android.icu.util.Calendar.YEAR);
            if (month < 0 || month >= HIJRI_MONTHS.length) {
                return "";
            }
            return BengaliDate.bnNum(day) + " " + HIJRI_MONTHS[month] + " " + BengaliDate.bnNum(year) + " হিজরি";
        } catch (Exception e) {
            return "";
        }
    }
}
