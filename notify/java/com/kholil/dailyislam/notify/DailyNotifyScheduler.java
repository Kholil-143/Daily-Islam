package com.kholil.dailyislam.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stores the daily-notification items and (re)schedules their alarms. */
public class DailyNotifyScheduler {
    private static final String PREFS = "daily_notify";
    private static final String KEY_ITEMS = "items";

    public static void saveAndSchedule(Context ctx, String itemsJson) {
        cancelAll(ctx);
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ITEMS, itemsJson).apply();
        scheduleStored(ctx);
    }

    public static int countStored(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null);
        if (json == null) return 0;
        try {
            return new JSONArray(json).length();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void scheduleStored(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null);
        if (json == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            JSONArray arr = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long t = o.optLong("epochMs", 0);
                if (t <= now) continue;
                int id = o.optInt("id", i);
                Intent intent = new Intent(ctx, DailyNotifyReceiver.class);
                intent.putExtra("id", id);
                intent.putExtra("title", o.optString("title", ""));
                intent.putExtra("body", o.optString("body", ""));
                intent.putExtra("bigText", o.optString("bigText", ""));
                PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                    }
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void cancelAll(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                for (int i = 0; i < arr.length(); i++) {
                    int id = arr.getJSONObject(i).optInt("id", i);
                    Intent intent = new Intent(ctx, DailyNotifyReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent,
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (pi != null) {
                        if (am != null) am.cancel(pi);
                        pi.cancel();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ITEMS).apply();
    }
}
