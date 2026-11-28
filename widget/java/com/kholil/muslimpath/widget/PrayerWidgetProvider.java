package com.kholil.muslimpath.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;

import com.kholil.muslimpath.R;

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
 * SharedPreferences; Bhanga, Faridpur (the owner's home area) is the
 * default. Tapping the district line on the widget reopens the picker,
 * so the district can be changed at any time.
 */
public class PrayerWidgetProvider extends AppWidgetProvider {

    public static final String PREFS_NAME = "daily_islam_widget";
    public static final String KEY_DISTRICT = "district_name";
    public static final String KEY_LATITUDE = "latitude";
    public static final String KEY_LONGITUDE = "longitude";

    private static final String ACTION_REFRESH = "com.kholil.muslimpath.widget.ACTION_REFRESH";
    private static final String ACTION_TICK = "com.kholil.muslimpath.widget.ACTION_MINUTE_TICK";
    private static final int TICK_REQUEST_CODE = 4242;

    static final double DEFAULT_LATITUDE = 23.3736; // Bhanga, Faridpur
    static final double DEFAULT_LONGITUDE = 90.0272;
    static final String DEFAULT_DISTRICT = "ভাঙ্গা, ফরিদপুর";

    private static final String[] WEEKDAYS_BN = {
            "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
    };

    static final String[] HIJRI_MONTHS = {
            "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
            "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    };

    static final String[] PRAYER_NAMES = {"ফজর", "সূর্যোদয়", "যোহর", "আসর", "মাগরিব", "এশা"};

    /** Display name for a waqt: on Fridays the Dhuhr slot (index 2) reads জুমা. */
    static String prayerName(int i, Calendar now) {
        if (i == 2 && now != null && now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            return "জুমা";
        }
        return PRAYER_NAMES[i];
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleNextMinuteTick(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelMinuteTick(context);
        super.onDisabled(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        // Keep the "next prayer in X minutes" countdown live: the system's
        // updatePeriodMillis can only refresh every 30 minutes, so chain a
        // one-shot alarm to the next minute boundary after every update.
        scheduleNextMinuteTick(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && (ACTION_REFRESH.equals(intent.getAction())
                || ACTION_TICK.equals(intent.getAction()))) {
            if (ACTION_REFRESH.equals(intent.getAction())) {
                // A manual refresh also asks for a fresh rain forecast.
                WeatherInfo.forceFetch(context);
            }
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidgetProvider.class));
            onUpdate(context, manager, ids);
        }
    }

    private static PendingIntent minuteTickIntent(Context context) {
        Intent intent = new Intent(context, PrayerWidgetProvider.class);
        intent.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, TICK_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Schedule a refresh just after the next minute boundary. */
    static void scheduleNextMinuteTick(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long nextMinuteMs = nowMs - (nowMs % 60000L) + 60000L + 250L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, nextMinuteMs, minuteTickIntent(context));
            } else {
                alarmManager.set(AlarmManager.RTC, nextMinuteMs, minuteTickIntent(context));
            }
        } catch (Exception e) {
            // If the alarm cannot be set, the 30-minute system updates and
            // the manual refresh button still keep the widget current.
        }
    }

    private static void cancelMinuteTick(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(minuteTickIntent(context));
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
        CharSequence hijri = hijriDisplay(now, times.maghrib);
        SpannableStringBuilder dates = new SpannableStringBuilder();
        dates.append(weekday).append(" • ").append(gregorian).append('\n').append(bengali);
        if (hijri.length() > 0) {
            dates.append(" • ").append(hijri);
        }
        views.setTextViewText(R.id.tv_dates, dates);
        views.setTextViewText(R.id.tv_district, "জেলা: " + district + " — বদলাতে এখানে ট্যাপ করুন");

        // Prayer times
        views.setTextViewText(R.id.tv_fajr, BengaliDate.formatTime(times.fajr));
        views.setTextViewText(R.id.tv_sunrise, BengaliDate.formatTime(times.sunrise));
        views.setTextViewText(R.id.tv_dhuhr, BengaliDate.formatTime(times.dhuhr));
        views.setTextViewText(R.id.tv_dhuhr_lbl, prayerName(2, now));
        views.setTextViewText(R.id.tv_asr, BengaliDate.formatTime(times.asr));
        views.setTextViewText(R.id.tv_sunset, BengaliDate.formatTime(times.maghrib - 3.0 / 60.0));
        views.setTextViewText(R.id.tv_maghrib, BengaliDate.formatTime(times.maghrib));
        views.setTextViewText(R.id.tv_isha, BengaliDate.formatTime(times.isha));

        // Ramadan only: extra সাহরি / ইফতার rows, using the app's own
        // convention (sehri ends 10 minutes before Fajr; iftar is Maghrib).
        // Outside Ramadan the row is GONE. Ramadan is Islamic month 9,
        // index 8 in the 0-based hijriParts month.
        int[] todayHijri = hijriParts(now);
        boolean ramadan = todayHijri != null && todayHijri[1] == 8;
        views.setViewVisibility(R.id.row_ramadan, ramadan ? View.VISIBLE : View.GONE);
        if (ramadan) {
            views.setTextViewText(R.id.tv_sehri, BengaliDate.formatTime(times.fajr - 10.0 / 60.0));
            views.setTextViewText(R.id.tv_iftar, BengaliDate.formatTime(times.maghrib));
        }

        // Next prayer + remaining time (at this update moment)
        views.setTextViewText(R.id.tv_next, nextPrayerText(now, times, latitude, longitude, timeZone));

        // Tomorrow's rain line (cached; a background fetch runs at most
        // every three hours, so the per-minute refresh stays offline).
        String weather = WeatherInfo.cachedLine(context, latitude, longitude);
        if (weather != null) {
            views.setViewVisibility(R.id.tv_weather, View.VISIBLE);
            views.setTextViewText(R.id.tv_weather, weather);
        } else {
            views.setViewVisibility(R.id.tv_weather, View.GONE);
        }
        WeatherInfo.maybeFetch(context, latitude, longitude);

        // Refresh button
        Intent refreshIntent = new Intent(context, PrayerWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending);

        // Tapping the district line reopens the district picker, so the
        // location can be changed at any time (not only when the widget
        // is first placed).
        Intent configIntent = new Intent(context, WidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent configPending = PendingIntent.getActivity(context, appWidgetId + 1000,
                configIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_district, configPending);

        // Tapping the widget body opens the app
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            PendingIntent launchPending = PendingIntent.getActivity(context, 1, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, launchPending);
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    static String nextPrayerText(Calendar now, PrayerTimes.Result times,
                                         double latitude, double longitude, double timeZone) {
        double[] all = {times.fajr, times.sunrise, times.dhuhr, times.asr, times.maghrib, times.isha};
        double nowHours = now.get(Calendar.HOUR_OF_DAY)
                + now.get(Calendar.MINUTE) / 60.0
                + now.get(Calendar.SECOND) / 3600.0;

        for (int i = 0; i < all.length; i++) {
            if (all[i] > nowHours) {
                return "পরের ওয়াক্ত: " + prayerName(i, now) + " " + BengaliDate.formatTime(all[i])
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

    static String remainingText(double hours) {
        int minutes = (int) Math.ceil(hours * 60.0);
        int h = minutes / 60;
        int m = minutes % 60;
        if (h > 0) {
            return BengaliDate.bnNum(h) + " ঘণ্টা " + BengaliDate.bnNum(m) + " মিনিট";
        }
        return BengaliDate.bnNum(m) + " মিনিট";
    }

    /** BD Hijri {day, monthIndex, year} for a civil day, via the Umm al-Qura
     *  calendar (API 24+), minus one day for Bangladesh local moon sighting —
     *  the same reckoning as the in-app date. Null when unavailable. */
    static int[] hijriParts(Calendar day) {
        if (Build.VERSION.SDK_INT < 24) {
            return null;
        }
        try {
            android.icu.util.Calendar islamic = android.icu.util.Calendar.getInstance(
                    new android.icu.util.ULocale("en@calendar=islamic-umalqura"));
            islamic.setTimeInMillis(day.getTimeInMillis());
            islamic.add(android.icu.util.Calendar.DAY_OF_MONTH, -1); // Bangladesh: one day behind Umm al-Qura
            int d = islamic.get(android.icu.util.Calendar.DAY_OF_MONTH);
            int month = islamic.get(android.icu.util.Calendar.MONTH);
            int year = islamic.get(android.icu.util.Calendar.YEAR);
            if (month < 0 || month >= HIJRI_MONTHS.length) {
                return null;
            }
            return new int[]{d, month, year};
        } catch (Exception e) {
            return null;
        }
    }

    static String hijriPlain(int[] h) {
        return BengaliDate.bnNum(h[0]) + " " + HIJRI_MONTHS[h[1]] + " " + BengaliDate.bnNum(h[2]) + " হিজরি";
    }

    /** Hijri date display with Khalilur's Maghrib rule: the Hijri day begins
     *  at Maghrib, so from Maghrib until midnight the OLD day number is shown
     *  struck through in red with the NEW day number beside it (month/year
     *  are the new date's), e.g. "১৬ ১৭ জমাদিউস সানি ১৪৪৮ হিজরি" with ১৬ in
     *  red struck through. From midnight to the next Maghrib, plain. */
    static CharSequence hijriDisplay(Calendar now, double maghribHours) {
        int[] oldH = hijriParts(now);
        if (oldH == null) {
            return "";
        }
        double nowHours = now.get(Calendar.HOUR_OF_DAY)
                + now.get(Calendar.MINUTE) / 60.0
                + now.get(Calendar.SECOND) / 3600.0;
        if (nowHours >= maghribHours) {
            Calendar tomorrow = (Calendar) now.clone();
            tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            int[] newH = hijriParts(tomorrow);
            if (newH != null) {
                String oldDay = BengaliDate.bnNum(oldH[0]);
                String text = oldDay + " " + BengaliDate.bnNum(newH[0]) + " "
                        + HIJRI_MONTHS[newH[1]] + " " + BengaliDate.bnNum(newH[2]) + " হিজরি";
                SpannableString ss = new SpannableString(text);
                ss.setSpan(new StrikethroughSpan(), 0, oldDay.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(Color.rgb(0xD3, 0x2F, 0x2F)), 0, oldDay.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return ss;
            }
        }
        return hijriPlain(oldH);
    }
}
