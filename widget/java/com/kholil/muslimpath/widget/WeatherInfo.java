package com.kholil.muslimpath.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tomorrow's rain forecast for the widgets, from the free Open-Meteo API
 * (no API key needed). The result is cached in the widget
 * SharedPreferences, so the per-minute widget refresh does NOT hit the
 * network: a new fetch is attempted at most once every three hours, or
 * when the district (coordinates) changes. Any failure keeps the last
 * cached line — or hides the line when nothing was ever cached — so the
 * weather can never break the prayer times.
 */
public final class WeatherInfo {

    private static final String KEY_PROB = "weather_prob";
    private static final String KEY_ATTEMPT_MS = "weather_attempt_ms";
    private static final String KEY_LAT = "weather_lat";
    private static final String KEY_LON = "weather_lon";

    private static final long REFRESH_INTERVAL_MS = 3L * 60L * 60L * 1000L;
    private static final double COORD_EPSILON = 0.01;

    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    private WeatherInfo() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PrayerWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * The cached Bengali line for these coordinates, or null when no
     * forecast has been cached for this district yet.
     */
    public static String cachedLine(Context context, double latitude, double longitude) {
        SharedPreferences prefs = prefs(context);
        int prob = prefs.getInt(KEY_PROB, -1);
        if (prob < 0) {
            return null;
        }
        if (Math.abs(prefs.getFloat(KEY_LAT, 0f) - latitude) > COORD_EPSILON
                || Math.abs(prefs.getFloat(KEY_LON, 0f) - longitude) > COORD_EPSILON) {
            return null;
        }
        if (prob >= 30) {
            return "আগামীকাল: বৃষ্টির সম্ভাবনা আছে (" + BengaliDate.bnNum(prob) + "%)";
        }
        return "আগামীকাল: বৃষ্টির সম্ভাবনা নেই";
    }

    /**
     * Fetch in the background when the cache is stale (3h+) or the
     * district changed. Safe to call on every widget update.
     */
    public static void maybeFetch(Context context, double latitude, double longitude) {
        SharedPreferences prefs = prefs(context);
        boolean coordsChanged =
                Math.abs(prefs.getFloat(KEY_LAT, 0f) - latitude) > COORD_EPSILON
                        || Math.abs(prefs.getFloat(KEY_LON, 0f) - longitude) > COORD_EPSILON;
        boolean stale = System.currentTimeMillis() - prefs.getLong(KEY_ATTEMPT_MS, 0L)
                > REFRESH_INTERVAL_MS;
        if (coordsChanged || stale) {
            fetch(context, latitude, longitude);
        }
    }

    /** Force a fresh fetch (the widget's manual refresh button). */
    public static void forceFetch(Context context) {
        SharedPreferences prefs = prefs(context);
        double latitude = prefs.getFloat(PrayerWidgetProvider.KEY_LATITUDE,
                (float) PrayerWidgetProvider.DEFAULT_LATITUDE);
        double longitude = prefs.getFloat(PrayerWidgetProvider.KEY_LONGITUDE,
                (float) PrayerWidgetProvider.DEFAULT_LONGITUDE);
        fetch(context, latitude, longitude);
    }

    private static void fetch(final Context context, final double latitude, final double longitude) {
        if (!FETCHING.compareAndSet(false, true)) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        prefs(appContext).edit()
                .putLong(KEY_ATTEMPT_MS, System.currentTimeMillis())
                .apply();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int prob = fetchTomorrowRainProbability(latitude, longitude);
                    if (prob >= 0) {
                        prefs(appContext).edit()
                                .putInt(KEY_PROB, prob)
                                .putFloat(KEY_LAT, (float) latitude)
                                .putFloat(KEY_LON, (float) longitude)
                                .apply();
                        PrayerWidgetProvider.refreshAll(appContext);
                        CompactPrayerWidgetProvider.refreshAll(appContext);
                    }
                } catch (Exception ignored) {
                    // Keep the cached line; the next stale window retries.
                } finally {
                    FETCHING.set(false);
                }
            }
        }, "widget-weather").start();
    }

    /** Tomorrow's max precipitation probability (0-100), or -1 on any failure. */
    private static int fetchTomorrowRainProbability(double latitude, double longitude) {
        HttpURLConnection conn = null;
        try {
            String spec = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&daily=precipitation_probability_max"
                            + "&timezone=Asia%%2FDhaka&forecast_days=2",
                    latitude, longitude);
            conn = (HttpURLConnection) new URL(spec).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "MuslimPathWidget");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return -1;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            JSONObject daily = new JSONObject(body.toString()).getJSONObject("daily");
            JSONArray probs = daily.getJSONArray("precipitation_probability_max");
            // With timezone=Asia/Dhaka, index 0 is today and index 1 tomorrow.
            if (probs.length() < 2 || probs.isNull(1)) {
                return -1;
            }
            return probs.optInt(1, -1);
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
