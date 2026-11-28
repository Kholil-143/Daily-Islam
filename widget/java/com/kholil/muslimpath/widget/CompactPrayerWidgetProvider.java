package com.kholil.muslimpath.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.RemoteViews;

import com.kholil.muslimpath.R;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Second, smaller and plainer prayer widget — Khalilur's own layout:
 * at the very top left the Hijri date followed by the Bengali date,
 * then the next waqt with its countdown and tomorrow's rain line; just
 * above the bottom line সূর্যোদয় on the left and সূর্যাস্ত on the
 * right; and the five waqt times in a single line at the very bottom.
 *
 * It shares the district preference and the per-minute refresh chain
 * pattern with the main widget, and — like the main widget since
 * v2.13 — has NO configure activity, so it places immediately with the
 * default district (ভাঙ্গা, ফরিদপুর). Tapping the dates opens the
 * district picker, so the district can still be changed afterwards.
 */
public class CompactPrayerWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_TICK =
            "com.kholil.muslimpath.widget.ACTION_MINUTE_TICK_COMPACT";
    private static final int TICK_REQUEST_CODE = 4243;

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
        scheduleNextMinuteTick(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, CompactPrayerWidgetProvider.class));
            onUpdate(context, manager, ids);
        }
    }

    private static PendingIntent minuteTickIntent(Context context) {
        Intent intent = new Intent(context, CompactPrayerWidgetProvider.class);
        intent.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, TICK_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleNextMinuteTick(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long nextMinuteMs = nowMs - (nowMs % 60000L) + 60000L + 250L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC, nextMinuteMs, minuteTickIntent(context));
            } else {
                alarmManager.set(AlarmManager.RTC, nextMinuteMs, minuteTickIntent(context));
            }
        } catch (Exception e) {
            // The 30-minute system updates still keep the widget current.
        }
    }

    private static void cancelMinuteTick(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(minuteTickIntent(context));
        }
    }

    /** Refresh every placed compact widget (district change / new weather). */
    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, CompactPrayerWidgetProvider.class));
        for (int id : ids) {
            updateWidget(context, manager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(
                PrayerWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE);
        double latitude = prefs.getFloat(PrayerWidgetProvider.KEY_LATITUDE,
                (float) PrayerWidgetProvider.DEFAULT_LATITUDE);
        double longitude = prefs.getFloat(PrayerWidgetProvider.KEY_LONGITUDE,
                (float) PrayerWidgetProvider.DEFAULT_LONGITUDE);

        Calendar now = Calendar.getInstance();
        double timeZone = TimeZone.getDefault().getOffset(now.getTimeInMillis()) / 3600000.0;

        PrayerTimes.Result times = PrayerTimes.compute(
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH), latitude, longitude, timeZone);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_compact);

        // Top left: Hijri date (with the Maghrib rollover rule), then Bengali date.
        CharSequence hijri = PrayerWidgetProvider.hijriDisplay(now, times.maghrib);
        SpannableStringBuilder dates = new SpannableStringBuilder();
        if (hijri.length() > 0) {
            dates.append(hijri).append('\n');
        }
        dates.append(BengaliDate.formatBengaliDate(now));
        views.setTextViewText(R.id.tv_dates_c, dates);

        // Next waqt + countdown, same wording as the main widget.
        views.setTextViewText(R.id.tv_next_c,
                PrayerWidgetProvider.nextPrayerText(now, times, latitude, longitude, timeZone));

        // Tomorrow's rain line (cached; background fetch at most every 3h).
        String weather = WeatherInfo.cachedLine(context, latitude, longitude);
        if (weather != null) {
            views.setViewVisibility(R.id.tv_weather_c, View.VISIBLE);
            views.setTextViewText(R.id.tv_weather_c, weather);
        } else {
            views.setViewVisibility(R.id.tv_weather_c, View.GONE);
        }
        WeatherInfo.maybeFetch(context, latitude, longitude);

        // Just above the bottom line: sunrise left, sunset right
        // (sunset = Maghrib - 3 minutes, the main widget's convention).
        views.setTextViewText(R.id.tv_sunrise_c,
                "সূর্যোদয় " + BengaliDate.formatTime(times.sunrise));
        views.setTextViewText(R.id.tv_sunset_c,
                "সূর্যাস্ত " + BengaliDate.formatTime(times.maghrib - 3.0 / 60.0));

        // The five waqt times in a single line at the very bottom.
        views.setTextViewText(R.id.tv_fajr_c, BengaliDate.formatTime(times.fajr));
        views.setTextViewText(R.id.tv_dhuhr_c, BengaliDate.formatTime(times.dhuhr));
        views.setTextViewText(R.id.tv_dhuhr_lbl_c, PrayerWidgetProvider.prayerName(2, now));
        views.setTextViewText(R.id.tv_asr_c, BengaliDate.formatTime(times.asr));
        views.setTextViewText(R.id.tv_maghrib_c, BengaliDate.formatTime(times.maghrib));
        views.setTextViewText(R.id.tv_isha_c, BengaliDate.formatTime(times.isha));

        // Tapping the dates reopens the district picker (as on the main widget).
        Intent configIntent = new Intent(context, WidgetConfigActivity.class);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent configPending = PendingIntent.getActivity(context, appWidgetId + 2000,
                configIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_dates_c, configPending);

        // Tapping the widget body opens the app.
        Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        if (launch != null) {
            PendingIntent launchPending = PendingIntent.getActivity(context, 2, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root_c, launchPending);
        }

        manager.updateAppWidget(appWidgetId, views);
    }
}
