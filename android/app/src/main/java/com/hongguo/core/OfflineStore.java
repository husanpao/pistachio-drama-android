package com.hongguo.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Metadata for episodes that were downloaded for offline playback. */
public final class OfflineStore {
    private static final String PREFS = "hongguo_offline";
    private static final String ITEMS = "items";

    private OfflineStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray items(Context context) {
        try {
            return new JSONArray(prefs(context).getString(ITEMS, "[]"));
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    public static void add(Context context, String seriesId, String title, String cover,
                           String vid, int index, String quality, long size) {
        try {
            JSONArray current = items(context);
            JSONArray next = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                if (item.optString("vid").equals(vid)
                        && item.optString("quality").equals(quality)) continue;
                next.put(item);
            }
            JSONObject item = new JSONObject();
            item.put("series_id", seriesId == null ? "" : seriesId);
            item.put("title", title == null ? "" : title);
            item.put("cover", cover == null ? "" : cover);
            item.put("vid", vid == null ? "" : vid);
            item.put("index", index);
            item.put("quality", quality == null ? "" : quality);
            item.put("size", size);
            item.put("updated", System.currentTimeMillis());
            next.put(item);
            prefs(context).edit().putString(ITEMS, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void remove(Context context, String vid, String quality) {
        try {
            JSONArray current = items(context);
            JSONArray next = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null) continue;
                if (item.optString("vid").equals(vid)
                        && item.optString("quality").equals(quality)) continue;
                next.put(item);
            }
            prefs(context).edit().putString(ITEMS, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(ITEMS).apply();
    }

    public static long totalSize(Context context) {
        long total = 0;
        JSONArray array = items(context);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) total += item.optLong("size", 0);
        }
        return total;
    }
}
