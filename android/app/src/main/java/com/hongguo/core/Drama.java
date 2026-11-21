package com.hongguo.core;

import java.util.ArrayList;
import java.util.List;

/** Normalized short-drama card shared by every list screen. */
public final class Drama {
    public String seriesId = "";
    public String title = "";
    public String cover = "";
    public String coverFallback = "";
    public String intro = "";
    public String score = "";
    public String heat = "";
    public String badge = "";
    public String vid = "";
    public String colorHex = "";
    public String feedKind = "";
    public int episodeCount;
    public int rank;
    public long playCount;
    public long duration;
    public List<String> tags = new ArrayList<>();

    public String coverUrl() {
        return coverFallback != null && !coverFallback.isEmpty() ? coverFallback : cover;
    }

    public String episodeLabel() {
        return episodeCount > 0 ? episodeCount + "集" : "";
    }

    public String heatLabel() {
        if (heat != null && !heat.isEmpty()) return heat;
        if (playCount > 0) return Theme.formatCount(playCount) + "播放";
        return "";
    }

    public String primaryTag() {
        for (String tag : tags) {
            if (tag != null && !tag.trim().isEmpty()) return tag.trim();
        }
        return "";
    }

    public boolean playable() {
        return seriesId != null && !seriesId.isEmpty() && !seriesId.startsWith("actor-");
    }
}
