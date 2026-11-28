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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recent significant earthquakes near Bangladesh, for the MAIN widget
 * only, shown in the free space right of the weather line.
 *
 * Data: the USGS FDSN event service (free, no API key) — earthquakes of
 * magnitude 4.5+ whose epicentre lies within 500 km of Bangladesh's
 * centre (23.7, 90.4), over the last 24 hours. This is a notice about a
 * quake that has ALREADY happened; it is not a prediction or warning.
 *
 * Like WeatherInfo, the result is cached in the widget SharedPreferences
 * and a background fetch runs at most once every three hours, so the
 * per-minute widget refresh stays offline and battery-friendly. Any
 * failure keeps the last cached state; when nothing qualifies, the
 * space stays empty.
 */
public final class QuakeInfo {

    private static final String KEY_LINE = "quake_line";
    private static final String KEY_QUAKE_MS = "quake_time_ms";
    private static final String KEY_ATTEMPT_MS = "quake_attempt_ms";

    private static final long REFRESH_INTERVAL_MS = 3L * 60L * 60L * 1000L;
    private static final long WINDOW_MS = 24L * 60L * 60L * 1000L;

    private static final double CENTRE_LAT = 23.7;
    private static final double CENTRE_LON = 90.4;
    private static final int RADIUS_KM = 500;
    private static final String MIN_MAGNITUDE = "4.5";

    private static final int MAX_PLACE_CHARS = 26;

    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    private QuakeInfo() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PrayerWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * The cached Bengali notice line, or null when no qualifying quake
     * occurred within the last 24 hours (or none was ever fetched).
     */
    public static String cachedLine(Context context) {
        SharedPreferences prefs = prefs(context);
        String line = prefs.getString(KEY_LINE, null);
        if (line == null || line.length() == 0) {
            return null;
        }
        long quakeMs = prefs.getLong(KEY_QUAKE_MS, 0L);
        if (System.currentTimeMillis() - quakeMs > WINDOW_MS) {
            return null;
        }
        return line;
    }

    /** Fetch in the background when the last attempt is stale (3h+). */
    public static void maybeFetch(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean stale = System.currentTimeMillis() - prefs.getLong(KEY_ATTEMPT_MS, 0L)
                > REFRESH_INTERVAL_MS;
        if (stale) {
            fetch(context);
        }
    }

    /** Force a fresh fetch (the widget's manual refresh button). */
    public static void forceFetch(Context context) {
        fetch(context);
    }

    private static void fetch(final Context context) {
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
                    String[] result = fetchLatestQuake();
                    if (result != null) {
                        prefs(appContext).edit()
                                .putString(KEY_LINE, result[0])
                                .putLong(KEY_QUAKE_MS, Long.parseLong(result[1]))
                                .apply();
                        PrayerWidgetProvider.refreshAll(appContext);
                    }
                } catch (Exception ignored) {
                    // Keep the cached line; the next stale window retries.
                } finally {
                    FETCHING.set(false);
                }
            }
        }, "widget-quake").start();
    }

    /**
     * @return {line, quakeTimeMs} for the most recent qualifying quake,
     *         {"", "0"} when the query succeeded but nothing qualifies,
     *         or null on any failure (keep the old cache).
     */
    private static String[] fetchLatestQuake() {
        HttpURLConnection conn = null;
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            String start = fmt.format(new Date(System.currentTimeMillis() - WINDOW_MS));
            String spec = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson"
                    + "&starttime=" + start
                    + "&minmagnitude=" + MIN_MAGNITUDE
                    + "&latitude=" + CENTRE_LAT
                    + "&longitude=" + CENTRE_LON
                    + "&maxradiuskm=" + RADIUS_KM
                    + "&orderby=time&limit=1";
            conn = (HttpURLConnection) new URL(spec).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "MuslimPathWidget");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();
            JSONArray features = new JSONObject(body.toString()).optJSONArray("features");
            if (features == null || features.length() == 0) {
                return new String[]{"", "0"};
            }
            JSONObject props = features.getJSONObject(0).optJSONObject("properties");
            if (props == null || props.isNull("mag")) {
                return new String[]{"", "0"};
            }
            double mag = props.optDouble("mag", 0);
            long timeMs = props.optLong("time", 0L);
            String place = props.optString("place", "");
            String magText = bnDigits(String.format(Locale.US, "%.1f", mag));
            String placeText = place.length() > MAX_PLACE_CHARS
                    ? place.substring(0, MAX_PLACE_CHARS - 1) + "…"
                    : place;
            String text = "⚠ ভূমিকম্প: " + magText + " মাত্রা"
                    + (placeText.length() > 0 ? ", " + placeText : "");
            return new String[]{text, String.valueOf(timeMs)};
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Latin digits (and any other characters) → Bengali digits where applicable. */
    private static String bnDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append((char) ('০' + (c - '0')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
