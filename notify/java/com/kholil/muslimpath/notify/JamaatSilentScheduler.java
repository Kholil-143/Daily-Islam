package com.kholil.muslimpath.notify;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Stores the jamaat auto-silent windows and (re)schedules their alarms.
 *
 * Each window is {id, startMs, endMs, mode}. At startMs the phone is put
 * into the requested ringer mode (silent or vibrate); the mode the phone
 * had before is remembered and restored exactly at endMs. If Do Not
 * Disturb access has not been granted, everything silently no-ops —
 * Android forbids ringer-mode changes without it.
 */
public class JamaatSilentScheduler {
    private static final String PREFS = "jamaat_silent";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_SAVED_MODE = "saved_mode";
    private static final String KEY_SET_MODE = "set_mode";
    private static final int CODE_BASE = 710000;

    public static boolean hasDndAccess(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    public static void saveAndSchedule(Context ctx, String itemsJson) {
        cancelAlarms(ctx);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ITEMS, itemsJson).apply();
        scheduleStored(ctx);
        reconcile(ctx);
    }

    public static int countStored(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, null);
        if (json == null) return 0;
        try {
            return new JSONArray(json).length();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void scheduleStored(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, null);
        if (json == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            JSONArray arr = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                int id = o.optInt("id", i);
                long start = o.optLong("startMs", 0);
                long end = o.optLong("endMs", 0);
                int mode = o.optInt("mode", AudioManager.RINGER_MODE_SILENT);
                if (end <= now) continue;
                setAlarm(ctx, am, CODE_BASE + id * 2, end,
                        JamaatSilentReceiver.ACTION_END, mode);
                if (start > now) {
                    setAlarm(ctx, am, CODE_BASE + id * 2 + 1, start,
                            JamaatSilentReceiver.ACTION_START, mode);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void cancelAll(Context ctx) {
        cancelAlarms(ctx);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_ITEMS).apply();
        applyEnd(ctx);
    }

    private static void cancelAlarms(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, null);
        if (json == null) return;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                int id = arr.getJSONObject(i).optInt("id", i);
                cancelOne(ctx, am, CODE_BASE + id * 2, JamaatSilentReceiver.ACTION_END);
                cancelOne(ctx, am, CODE_BASE + id * 2 + 1, JamaatSilentReceiver.ACTION_START);
            }
        } catch (Exception ignored) {
        }
    }

    private static void setAlarm(Context ctx, AlarmManager am, int code, long t,
                                 String action, int mode) {
        Intent intent = new Intent(ctx, JamaatSilentReceiver.class);
        intent.setAction(action);
        intent.putExtra("mode", mode);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, code, intent,
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

    private static void cancelOne(Context ctx, AlarmManager am, int code, String action) {
        Intent intent = new Intent(ctx, JamaatSilentReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, code, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            if (am != null) am.cancel(pi);
            pi.cancel();
        }
    }

    /** Puts the phone into the given ringer mode, remembering the old one. */
    public static void applyStart(Context ctx, int mode) {
        if (!hasDndAccess(ctx)) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ACTIVE, false)) {
            // First window to start: remember the pre-silent mode. Later,
            // overlapping windows must NOT overwrite it, so the phone goes
            // back to the mode it had before the FIRST window began.
            sp.edit()
                    .putInt(KEY_SAVED_MODE, am.getRingerMode())
                    .putBoolean(KEY_ACTIVE, true)
                    .apply();
        }
        sp.edit().putInt(KEY_SET_MODE, mode).apply();
        try {
            am.setRingerMode(mode);
        } catch (SecurityException ignored) {
        }
    }

    /** Restores the ringer mode saved by applyStart, exactly as it was. */
    public static void applyEnd(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ACTIVE, false)) return;
        // Overlap rule: if another stored window (e.g. madrasa class time)
        // still covers this moment, stay silent — the later window's end
        // restores. The small tolerance keeps a window whose end alarm is
        // firing right now from counting as "still covering".
        if (coveringMode(ctx, System.currentTimeMillis() + 3000) >= 0) return;
        int saved = sp.getInt(KEY_SAVED_MODE, AudioManager.RINGER_MODE_NORMAL);
        int set = sp.getInt(KEY_SET_MODE, AudioManager.RINGER_MODE_SILENT);
        sp.edit().putBoolean(KEY_ACTIVE, false).apply();
        if (!hasDndAccess(ctx)) return;
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            // Only restore if the phone is still in the mode we set — if the
            // user changed it by hand meanwhile, leave their choice alone.
            if (am.getRingerMode() == set) {
                am.setRingerMode(saved);
            }
        } catch (SecurityException ignored) {
        }
    }

    /** Ringer mode of a stored window covering the given moment, or -1. */
    private static int coveringMode(Context ctx, long now) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, null);
        if (json == null) return -1;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("startMs", 0) <= now && now < o.optLong("endMs", 0)) {
                    return o.optInt("mode", AudioManager.RINGER_MODE_SILENT);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Brings the ringer state in line with the stored windows right now:
     * silences if a window currently covers this moment (e.g. after a
     * reboot mid-window), restores if a window ended while we were away.
     */
    public static void reconcile(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean active = sp.getBoolean(KEY_ACTIVE, false);
        int mode = coveringMode(ctx, System.currentTimeMillis());
        if (mode >= 0) {
            applyStart(ctx, mode);
        } else if (active) {
            applyEnd(ctx);
        }
    }
}
