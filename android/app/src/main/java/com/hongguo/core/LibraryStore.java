package com.hongguo.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Local history and favorites, no account required. */
public final class LibraryStore {
    private static final String PREFS = "hongguo_library";
    private static final String HISTORY = "history";
    private static final String FAVORITES = "favorites";
    private static final String WATCHED_PREFIX = "watched_";

    private LibraryStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addHistory(Context context, JSONObject item) {
        try {
            JSONArray current = new JSONArray(prefs(context).getString(HISTORY, "[]"));
            JSONArray next = new JSONArray();
            String seriesId = item.optString("series_id");
            item.put("updated", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < current.length() && next.length() < 50; i++) {
                JSONObject old = current.optJSONObject(i);
                if (old != null && !seriesId.equals(old.optString("series_id"))) next.put(old);
            }
            prefs(context).edit().putString(HISTORY, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static JSONArray history(Context context) {
        try {
            return new JSONArray(prefs(context).getString(HISTORY, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    public static boolean isFavorite(Context context, String seriesId) {
        try {
            JSONArray array = new JSONArray(prefs(context).getString(FAVORITES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null && seriesId.equals(item.optString("series_id"))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean toggleFavorite(Context context, JSONObject item) {
        try {
            JSONArray current = new JSONArray(prefs(context).getString(FAVORITES, "[]"));
            JSONArray next = new JSONArray();
            String seriesId = item.optString("series_id");
            boolean found = false;
            for (int i = 0; i < current.length(); i++) {
                JSONObject old = current.optJSONObject(i);
                if (old == null) continue;
                if (seriesId.equals(old.optString("series_id"))) {
                    found = true;
                } else {
                    next.put(old);
                }
            }
            if (!found) next.put(item);
            prefs(context).edit().putString(FAVORITES, next.toString()).apply();
            return !found;
        } catch (Exception error) {
            return false;
        }
    }

    public static JSONArray favorites(Context context) {
        try {
            return new JSONArray(prefs(context).getString(FAVORITES, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    public static Set<Integer> watched(Context context, String seriesId) {
        Set<Integer> result = new HashSet<>();
        if (seriesId == null || seriesId.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(prefs(context).getString(
                    WATCHED_PREFIX + seriesId, "[]"));
            for (int i = 0; i < array.length(); i++) result.add(array.optInt(i, -1));
        } catch (Exception ignored) {
        }
        return result;
    }

    public static void markWatched(Context context, String seriesId, int index) {
        if (seriesId == null || seriesId.isEmpty() || index < 0) return;
        Set<Integer> set = watched(context, seriesId);
        // First playback of a series: treat prior episodes as seen too.
        if (set.isEmpty()) {
            for (int i = 0; i <= index; i++) set.add(i);
        } else {
            set.add(index);
        }
        saveWatched(context, seriesId, set);
    }

    public static void seedWatched(Context context, String seriesId, int upTo) {
        if (seriesId == null || seriesId.isEmpty() || upTo < 0) return;
        if (!watched(context, seriesId).isEmpty()) return;
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i <= upTo; i++) set.add(i);
        saveWatched(context, seriesId, set);
    }

    private static void saveWatched(Context context, String seriesId, Set<Integer> set) {
        JSONArray array = new JSONArray();
        for (Integer value : set) array.put(value);
        prefs(context).edit().putString(WATCHED_PREFIX + seriesId, array.toString()).apply();
    }

    public static void removeHistory(Context context, String seriesId) {
        remove(context, HISTORY, seriesId);
        if (seriesId != null && !seriesId.isEmpty()) {
            prefs(context).edit().remove(WATCHED_PREFIX + seriesId).apply();
        }
    }

    public static void removeFavorite(Context context, String seriesId) {
        remove(context, FAVORITES, seriesId);
    }

    public static void clearHistory(Context context) {
        SharedPreferences store = prefs(context);
        SharedPreferences.Editor editor = store.edit().remove(HISTORY);
        for (String key : store.getAll().keySet()) {
            if (key.startsWith(WATCHED_PREFIX)) editor.remove(key);
        }
        editor.apply();
    }

    public static void clearFavorites(Context context) {
        prefs(context).edit().remove(FAVORITES).apply();
    }

    private static void remove(Context context, String key, String seriesId) {
        try {
            JSONArray current = new JSONArray(prefs(context).getString(key, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                if (!seriesId.equals(item.optString("series_id"))) next.put(item);
            }
            prefs(context).edit().putString(key, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
