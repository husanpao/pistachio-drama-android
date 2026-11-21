package com.hongguo.core;

import java.util.ArrayList;
import java.util.List;

/** Detail page data: series metadata plus the full episode list. */
public final class DetailInfo {
    public String seriesId = "";
    public String title = "";
    public String cover = "";
    public String intro = "";
    public String score = "";
    public String badge = "";
    public String status = "";
    public long playCount;
    public long diggCount;
    public final List<String> tags = new ArrayList<>();
    public final List<Episode> episodes = new ArrayList<>();

    public int episodeCount() {
        return episodes.size();
    }
}
