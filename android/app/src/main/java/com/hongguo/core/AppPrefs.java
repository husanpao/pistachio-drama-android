package com.hongguo.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** App settings, search history and playback progress. */
public final class AppPrefs {
    private static final String PREFS = "hongguo_app";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_AUTO_NEXT = "auto_next";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final String KEY_MAX_CACHE = "max_cache_mb";
    private static final String KEY_FILL_SCREEN = "fill_screen";
    private static final String KEY_SPEED = "playback_speed";
    private static final String KEY_AUTO_PRELOAD = "auto_preload";
    private static final String KEY_FEED_SEEN = "feed_seen";
    private static final String KEY_UPDATE_CHECK = "update_check";

    private AppPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String quality(Context context) {
        return prefs(context).getString(KEY_QUALITY, "auto");
    }

    public static void setQuality(Context context, String quality) {
        prefs(context).edit().putString(KEY_QUALITY, quality).apply();
    }

    public static boolean autoNext(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_NEXT, true);
    }

    public static void setAutoNext(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_NEXT, enabled).apply();
    }

    public static boolean wifiOnly(Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, false);
    }

    public static void setWifiOnly(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, enabled).apply();
    }

    public static int maxCacheMb(Context context) {
        return prefs(context).getInt(KEY_MAX_CACHE, 1500);
    }

    public static void setMaxCacheMb(Context context, int value) {
        prefs(context).edit().putInt(KEY_MAX_CACHE, value).apply();
    }

    public static boolean fillScreen(Context context) {
        return prefs(context).getBoolean(KEY_FILL_SCREEN, true);
    }

    public static void setFillScreen(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FILL_SCREEN, enabled).apply();
    }

    public static float playbackSpeed(Context context) {
        return prefs(context).getFloat(KEY_SPEED, 1f);
    }

    public static void setPlaybackSpeed(Context context, float speed) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply();
    }

    public static boolean autoPreload(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_PRELOAD, true);
    }

    public static void setAutoPreload(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_PRELOAD, enabled).apply();
    }

    public static JSONArray feedSeen(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_FEED_SEEN, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    public static void markFeedSeen(Context context, String seriesId) {
        if (seriesId == null || seriesId.isEmpty()) return;
        try {
            JSONArray current = feedSeen(context);
            JSONArray next = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                String value = current.optString(i);
                if (!value.isEmpty() && !value.equals(seriesId)) next.put(value);
            }
            next.put(seriesId);
            while (next.length() > 80) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < next.length(); i++) trimmed.put(next.optString(i));
                next = trimmed;
            }
            prefs(context).edit().putString(KEY_FEED_SEEN, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void clearFeedSeen(Context context) {
        prefs(context).edit().remove(KEY_FEED_SEEN).apply();
    }

    public static long lastUpdateCheck(Context context) {
        return prefs(context).getLong(KEY_UPDATE_CHECK, 0L);
    }

    public static void setLastUpdateCheck(Context context, long time) {
        prefs(context).edit().putLong(KEY_UPDATE_CHECK, time).apply();
    }

    public static String qualityLabel(String quality) {
        if ("360p".equals(quality)) return "360P 流畅";
        if ("480p".equals(quality)) return "480P 标清";
        if ("720p".equals(quality)) return "720P 高清";
        if ("1080p".equals(quality)) return "1080P 超清";
        return "自动";
    }

    public static JSONArray searchHistory(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_SEARCH_HISTORY, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    public static void addSearchHistory(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return;
        String value = query.trim();
        JSONArray current = searchHistory(context);
        JSONArray next = new JSONArray();
        next.put(value);
        for (int i = 0; i < current.length() && next.length() < 10; i++) {
            String old = current.optString(i);
            if (!old.isEmpty() && !old.equals(value)) next.put(old);
        }
        prefs(context).edit().putString(KEY_SEARCH_HISTORY, next.toString()).apply();
    }

    public static void clearSearchHistory(Context context) {
        prefs(context).edit().remove(KEY_SEARCH_HISTORY).apply();
    }

    public static void savePosition(Context context, String vid, long positionMs) {
        if (vid == null || vid.isEmpty()) return;
        prefs(context).edit().putLong("pos_" + vid, Math.max(positionMs, 0)).apply();
    }

    public static long position(Context context, String vid) {
        return prefs(context).getLong("pos_" + vid, 0);
    }

    public static void saveLastEpisode(Context context, String seriesId, String vid, int index) {
        try {
            JSONObject value = new JSONObject();
            value.put("vid", vid == null ? "" : vid);
            value.put("index", index);
            value.put("updated", System.currentTimeMillis());
            prefs(context).edit().putString("last_" + seriesId, value.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static JSONObject lastEpisode(Context context, String seriesId) {
        try {
            return new JSONObject(prefs(context).getString("last_" + seriesId, "{}"));
        } catch (Exception error) {
            return new JSONObject();
        }
    }
}
