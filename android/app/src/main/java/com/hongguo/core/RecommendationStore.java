package com.hongguo.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

/**
 * Tiny on-device recommendation profile. No account, no network, no telemetry:
 * it only remembers the kinds/tags the user actually watches and skips.
 */
public final class RecommendationStore {
    private static final String PREFS = "happyguo_reco";
    private static final String KEY_PROFILE = "profile";
    private static final Random RANDOM = new Random();

    private RecommendationStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject profile(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_PROFILE, "{}"));
        } catch (Exception error) {
            return new JSONObject();
        }
    }

    public static double score(Context context, Drama drama) {
        if (drama == null) return 0;
        JSONObject profile = profile(context);
        double score = value(profile.optJSONObject("kinds"), drama.feedKind) * 1.6;
        JSONObject tags = profile.optJSONObject("tags");
        for (String tag : drama.tags) {
            score += value(tags, tag) * 0.6;
        }
        JSONObject series = profile.optJSONObject("series");
        if (series != null) {
            JSONObject row = series.optJSONObject(drama.seriesId);
            if (row != null) {
                if (row.optBoolean("negative", false)) score -= 2;
                score += row.optDouble("favorite", 0) * 1.5;
            }
        }
        return score;
    }

    public static void recordView(Context context, Drama drama, long watchedMs,
                                  long durationMs, long activeMs) {
        if (drama == null || drama.seriesId == null || drama.seriesId.isEmpty()) return;
        if (durationMs <= 0 || watchedMs < 0) return;
        double fraction = durationMs > 0 ? (double) watchedMs / durationMs : 0;
        boolean liked = fraction >= 0.6 || watchedMs >= 25_000;
        boolean skipped = activeMs >= 3_000 && watchedMs < 2_500 && fraction < 0.15;

        JSONObject root = profile(context);
        JSONObject kinds = object(root, "kinds");
        JSONObject tags = object(root, "tags");
        JSONObject series = object(root, "series");
        if (liked) {
            bump(kinds, drama.feedKind, 0.8);
            for (String tag : drama.tags) bump(tags, tag, 0.3);
        } else if (!skipped && fraction >= 0.25) {
            bump(kinds, drama.feedKind, 0.15);
            for (String tag : drama.tags) bump(tags, tag, 0.08);
        } else if (skipped) {
            bump(kinds, drama.feedKind, -0.25);
            for (String tag : drama.tags) bump(tags, tag, -0.08);
        }

        try {
            JSONObject row = series.optJSONObject(drama.seriesId);
            if (row == null) row = new JSONObject();
            row.put("kind", drama.feedKind == null ? "" : drama.feedKind);
            row.put("last_watched", Math.max(row.optLong("last_watched", 0), watchedMs));
            row.put("duration", durationMs);
            row.put("updated", System.currentTimeMillis());
            series.put(drama.seriesId, row);
            root.put("kinds", kinds);
            root.put("tags", tags);
            root.put("series", series);
            prefs(context).edit().putString(KEY_PROFILE, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void recordFavorite(Context context, Drama drama) {
        if (drama == null || drama.seriesId == null || drama.seriesId.isEmpty()) return;
        JSONObject root = profile(context);
        JSONObject kinds = object(root, "kinds");
        JSONObject tags = object(root, "tags");
        JSONObject series = object(root, "series");
        bump(kinds, drama.feedKind, 1.8);
        for (String tag : drama.tags) bump(tags, tag, 0.7);
        try {
            JSONObject row = series.optJSONObject(drama.seriesId);
            if (row == null) row = new JSONObject();
            row.put("favorite", 1);
            row.put("negative", false);
            row.put("kind", drama.feedKind == null ? "" : drama.feedKind);
            series.put(drama.seriesId, row);
            root.put("kinds", kinds);
            root.put("tags", tags);
            root.put("series", series);
            prefs(context).edit().putString(KEY_PROFILE, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void recordNegative(Context context, Drama drama) {
        if (drama == null || drama.seriesId == null || drama.seriesId.isEmpty()) return;
        JSONObject root = profile(context);
        JSONObject kinds = object(root, "kinds");
        JSONObject tags = object(root, "tags");
        JSONObject series = object(root, "series");
        bump(kinds, drama.feedKind, -0.8);
        for (String tag : drama.tags) bump(tags, tag, -0.3);
        try {
            JSONObject row = series.optJSONObject(drama.seriesId);
            if (row == null) row = new JSONObject();
            row.put("negative", true);
            row.put("favorite", 0);
            row.put("kind", drama.feedKind == null ? "" : drama.feedKind);
            series.put(drama.seriesId, row);
            root.put("kinds", kinds);
            root.put("tags", tags);
            root.put("series", series);
            prefs(context).edit().putString(KEY_PROFILE, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Preference order with a little exploration, while keeping the first three cards diverse. */
    public static void rank(Context context, List<Drama> items) {
        if (items == null || items.size() < 2) return;
        final JSONObject profile = profile(context);
        final List<Drama> copy = new ArrayList<>(items);
        final java.util.Map<String, Double> scores = new java.util.HashMap<>();
        for (Drama drama : copy) {
            double score = value(profile.optJSONObject("kinds"), drama.feedKind) * 1.6;
            JSONObject tags = profile.optJSONObject("tags");
            for (String tag : drama.tags) score += value(tags, tag) * 0.6;
            if (drama.seriesId != null) {
                JSONObject row = profile.optJSONObject("series") == null ? null
                        : profile.optJSONObject("series").optJSONObject(drama.seriesId);
                if (row != null) {
                    if (row.optBoolean("negative", false)) score -= 2;
                    score += row.optDouble("favorite", 0) * 1.5;
                }
            }
            scores.put(drama.seriesId + "@" + System.identityHashCode(drama),
                    score + RANDOM.nextDouble() * 0.8);
        }
        Collections.sort(copy, (a, b) -> Double.compare(
                scores.get(b.seriesId + "@" + System.identityHashCode(b)),
                scores.get(a.seriesId + "@" + System.identityHashCode(a))));

        List<Drama> out = new ArrayList<>();
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (Drama drama : copy) {
            if (out.size() >= 3) break;
            String kind = drama.feedKind == null ? "" : drama.feedKind;
            if (kind.isEmpty() || kinds.add(kind)) out.add(drama);
        }
        for (Drama drama : copy) if (!out.contains(drama)) out.add(drama);
        items.clear();
        items.addAll(out);
    }

    private static JSONObject object(JSONObject root, String key) {
        JSONObject value = root.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static double value(JSONObject object, String key) {
        if (object == null || key == null || key.isEmpty()) return 0;
        double value = object.optDouble(key, 0);
        if (value < -4) value = -4;
        if (value > 8) value = 8;
        return value;
    }

    private static void bump(JSONObject object, String key, double delta) {
        if (object == null || key == null || key.isEmpty()) return;
        double value = object.optDouble(key, 0) + delta;
        value = Math.max(-4, Math.min(8, value));
        try {
            object.put(key, Math.round(value * 100) / 100.0);
        } catch (Exception ignored) {
        }
    }
}
