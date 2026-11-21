package com.hongguo.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses the app API payloads into the small UI-facing models. */
public final class DramaParser {
    private static final Map<String, String> FILTER_LABELS = new LinkedHashMap<>();

    static {
        FILTER_LABELS.put("genre", "类型");
        FILTER_LABELS.put("category_dim_theme", "主题");
        FILTER_LABELS.put("category_dim_role", "设定");
        FILTER_LABELS.put("category_dim_epoch", "背景");
        FILTER_LABELS.put("sort", "排序");
        FILTER_LABELS.put("gender", "受众");
        FILTER_LABELS.put("online_time", "时间");
        FILTER_LABELS.put("creation_status", "状态");
    }

    private DramaParser() {
    }

    public static ListPage parseCategory(JSONObject data, int offset) {
        ListPage page = new ListPage();
        if (data == null) return page;
        JSONArray rows = data.optJSONArray("video_data");
        appendRows(rows, page, 0);
        readCursor(data, page, offset);
        page.filters.addAll(parseFilters(data.optJSONArray("selector_rows")));
        return page;
    }

    public static ListPage parseNew(JSONObject data, int offset) {
        ListPage page = new ListPage();
        if (data == null) return page;
        JSONObject cellView = data.optJSONObject("cell_view");
        JSONArray rows = cellView == null ? null : cellView.optJSONArray("video_data");
        appendRows(rows, page, 0);
        readCursor(data, page, offset);
        return page;
    }

    public static ListPage parseRank(JSONObject data, int offset) {
        ListPage page = new ListPage();
        if (data == null) return page;
        JSONObject cellView = data.optJSONObject("cell_view");
        if (cellView == null) return page;
        JSONArray cells = cellView.optJSONArray("cell_data");
        if (cells != null) {
            for (int i = 0; i < cells.length(); i++) {
                JSONObject cell = cells.optJSONObject(i);
                if (cell == null) continue;
                JSONArray videos = cell.optJSONArray("video_data");
                if (videos != null) {
                    for (int j = 0; j < videos.length(); j++) {
                        Drama drama = parseDrama(videos.optJSONObject(j));
                        if (drama != null && drama.playable()) {
                            drama.rank = offset + page.items.size() + 1;
                            page.items.add(drama);
                        }
                    }
                } else {
                    Drama drama = parseDrama(cell.optJSONObject("video_data"));
                    if (drama != null && drama.playable()) {
                        drama.rank = offset + page.items.size() + 1;
                        page.items.add(drama);
                    }
                }
            }
        }
        readCursor(data, page, offset);
        if (!data.has("has_more") && cellView.has("has_more")) {
            page.hasMore = cellView.optBoolean("has_more");
            page.nextOffset = cellView.optInt("next_offset");
        }
        if (page.nextOffset <= offset && page.hasMore) {
            page.nextOffset = offset + Math.max(1, page.items.size());
        }
        page.rankOptions.addAll(parseRankOptions(cellView.optJSONObject("cell_selector")));
        return page;
    }

    public static ListPage parseSearch(JSONObject payload, int offset) {
        ListPage page = new ListPage();
        if (payload == null) return page;
        JSONObject data = payload.optJSONObject("data");
        JSONArray tabs = data == null ? null : data.optJSONArray("search_tabs");
        if (tabs == null) tabs = payload.optJSONArray("search_tabs");
        if (tabs == null) return page;
        JSONObject tab = null;
        for (int i = 0; i < tabs.length(); i++) {
            JSONObject candidate = tabs.optJSONObject(i);
            if (candidate != null && candidate.optInt("tab_type") == 11) {
                tab = candidate;
                break;
            }
        }
        if (tab == null) return page;
        page.hasMore = tab.optBoolean("has_more");
        page.nextOffset = tab.optInt("next_offset");
        page.searchId = tab.optString("search_id", "");
        page.passback = tab.optString("passback", "");
        if (page.nextOffset <= offset && page.hasMore) {
            page.nextOffset = offset + 20;
        }
        collectSearchItems(tab.opt("data"), page.items, 0);
        return page;
    }

    private static void collectSearchItems(Object value, List<Drama> out, int depth) {
        if (value == null || depth > 6 || out.size() >= 60) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && out.size() < 60; i++) {
                collectSearchItems(array.opt(i), out, depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        JSONArray videos = object.optJSONArray("video_data");
        if (videos != null) {
            for (int i = 0; i < videos.length() && out.size() < 60; i++) {
                addDrama(parseDrama(videos.optJSONObject(i)), out);
            }
        } else if (object.has("series_id") && object.optInt("episode_cnt", 0) > 0) {
            addDrama(parseDrama(object), out);
        }
        for (String key : new String[]{"cell_data", "data", "items"}) {
            if (object.has(key)) collectSearchItems(object.opt(key), out, depth + 1);
        }
        String lynx = object.optString("lynx_data", "");
        if (!lynx.isEmpty() && lynx.trim().startsWith("{")) {
            try {
                collectSearchItems(new JSONObject(lynx), out, depth + 1);
            } catch (Exception ignored) {
            }
        }
    }

    private static void addDrama(Drama drama, List<Drama> out) {
        if (drama == null || !drama.playable()) return;
        for (Drama existing : out) {
            if (drama.seriesId.equals(existing.seriesId)) return;
        }
        out.add(drama);
    }

    public static DetailInfo parseDetail(JSONObject videoData, String fallbackTitle,
                                         String fallbackCover, String fallbackScore) {
        DetailInfo info = new DetailInfo();
        if (videoData == null) {
            info.title = fallbackTitle == null ? "" : fallbackTitle;
            info.cover = fallbackCover == null ? "" : fallbackCover;
            info.score = fallbackScore == null ? "" : fallbackScore;
            return info;
        }
        info.seriesId = firstNonEmpty(videoData.optString("series_id_str"),
                videoData.optString("series_id"));
        info.title = firstNonEmpty(videoData.optString("series_title"), fallbackTitle);
        info.cover = firstNonEmpty(videoData.optString("series_cover"), fallbackCover);
        info.intro = firstNonEmpty(videoData.optString("series_intro"),
                videoData.optString("video_desc"), videoData.optString("description"));
        info.score = firstNonEmpty(videoData.optString("score"), fallbackScore);
        info.playCount = videoData.optLong("series_play_cnt", 0);
        info.diggCount = videoData.optLong("digg_cnt", 0);
        info.status = videoData.optString("series_status", "");
        JSONObject tagInfo = videoData.optJSONObject("video_tag_info");
        if (tagInfo != null) info.badge = tagInfo.optString("text", "");
        collectTags(videoData.optJSONArray("series_sub_title_list"), info.tags);
        collectTags(videoData.optJSONArray("abstract_tags"), info.tags);
        collectTags(videoData.optJSONArray("rec_tags"), info.tags);

        JSONArray list = videoData.optJSONArray("video_list");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject row = list.optJSONObject(i);
                if (row == null) continue;
                Episode episode = new Episode();
                episode.vid = row.optString("vid", "");
                if (episode.vid.isEmpty()) continue;
                episode.index = row.optInt("vid_index", i + 1);
                if (episode.index <= 0) episode.index = i + 1;
                episode.duration = row.optLong("duration", 0);
                episode.cover = row.optString("cover", "");
                episode.title = "第" + episode.index + "集";
                episode.locked = row.optBoolean("disable_play", false);
                info.episodes.add(episode);
            }
        }
        info.episodes.sort((a, b) -> Integer.compare(a.index, b.index));
        if (info.seriesId.isEmpty()) info.seriesId = "";
        return info;
    }

    public static Drama parseDrama(JSONObject value) {
        if (value == null) return null;
        JSONObject object = value;
        JSONObject detail = object.optJSONObject("video_detail");
        if (detail != null) object = detail;
        JSONObject nested = object.optJSONObject("video_data");
        if (nested != null) object = nested;

        String seriesId = firstNonEmpty(object.optString("series_id_str"),
                object.optString("series_id"), object.optString("seriesId"),
                object.optString("book_id"), object.optString("id"));
        String title = firstNonEmpty(object.optString("series_title"),
                object.optString("series_name"), object.optString("title"),
                object.optString("book_name"));
        if (seriesId.isEmpty() || title.isEmpty()) return null;

        Drama drama = new Drama();
        drama.seriesId = seriesId;
        drama.title = title;
        drama.cover = firstNonEmpty(object.optString("series_cover"),
                object.optString("cover"), object.optString("cover_url"),
                object.optString("episode_cover"));
        drama.coverFallback = browserCoverFallback(drama.cover);
        drama.intro = firstNonEmpty(object.optString("series_intro"),
                object.optString("video_desc"), object.optString("description"));
        drama.score = firstNonEmpty(object.optString("score"),
                object.optString("rating"), object.optString("scoreText"));
        if (drama.score.startsWith("评分")) drama.score = drama.score.substring(2).trim();
        drama.episodeCount = firstPositive(object.optInt("episode_cnt", 0),
                object.optInt("episode_total_cnt", 0));
        drama.vid = firstNonEmpty(object.optString("vid"), object.optString("video_id"));
        drama.playCount = object.optLong("play_cnt", 0);
        drama.duration = object.optLong("duration", 0);
        drama.colorHex = object.optString("color_dominate", "");
        drama.rank = object.optInt("rank", 0);

        JSONArray subTitles = object.optJSONArray("sub_title_list");
        collectTags(subTitles, drama.tags);
        collectTags(object.optJSONArray("tags"), drama.tags);
        drama.heat = heatFromSubTitles(subTitles);
        if (drama.heat.isEmpty()) {
            JSONObject recText = object.optJSONObject("rec_text_item");
            if (recText != null) drama.heat = recText.optString("RecommendText", "");
        }
        if (drama.heat.isEmpty()) drama.heat = object.optString("heatText", "");

        drama.badge = badgeFrom(object);
        if (drama.badge.isEmpty() && object.optBoolean("is_newly_update", false)) {
            drama.badge = "新剧";
        }
        return drama;
    }

    public static List<FilterGroup> parseFilters(JSONArray rows) {
        List<FilterGroup> out = new ArrayList<>();
        if (rows == null) return out;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String type = row.optString("type", "");
            String label = FILTER_LABELS.get(type);
            if (label == null) continue;
            FilterGroup group = new FilterGroup(type, label);
            JSONArray items = row.optJSONArray("items");
            if (items == null) continue;
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.optJSONObject(j);
                if (item == null) continue;
                String id = item.optString("selector_item_id", "");
                String name = item.optString("show_name", "");
                if (id.isEmpty() || name.isEmpty()) continue;
                group.options.add(new FilterOption(id, name));
            }
            if (!group.options.isEmpty()) out.add(group);
        }
        return out;
    }

    private static List<FilterOption> parseRankOptions(JSONObject selector) {
        List<FilterOption> out = new ArrayList<>();
        if (selector == null) return out;
        JSONObject outer = selector.optJSONObject("outer_row");
        JSONArray items = outer == null ? null : outer.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("selector_item_id", "");
            String label = item.optString("show_name", "");
            if (!id.isEmpty() && !label.isEmpty()) out.add(new FilterOption(id, label));
        }
        return out;
    }

    private static void appendRows(JSONArray rows, ListPage page, int rankStart) {
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            Drama drama = parseDrama(rows.optJSONObject(i));
            if (drama == null) continue;
            if (rankStart > 0) drama.rank = rankStart + page.items.size();
            page.items.add(drama);
        }
    }

    private static void readCursor(JSONObject data, ListPage page, int offset) {
        page.hasMore = data.optBoolean("has_more", false);
        page.nextOffset = data.optInt("next_offset", 0);
        page.sessionId = data.optString("session_id", "");
        if (page.nextOffset <= offset && page.hasMore) {
            page.nextOffset = offset + Math.max(1, page.items.size());
        }
    }

    private static void collectTags(JSONArray array, List<String> out) {
        if (array == null) return;
        for (int i = 0; i < array.length() && out.size() < 3; i++) {
            Object item = array.opt(i);
            String content = null;
            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                content = object.optString("content", "");
                if (content.isEmpty()) content = object.optString("name", "");
            } else if (item instanceof String) {
                content = (String) item;
            }
            if (content == null) continue;
            content = content.trim();
            if (content.isEmpty()) continue;
            if (looksLikeCount(content) || content.matches("第\\d+集")
                    || content.matches("第\\d+季") || content.matches("全\\d+集")
                    || content.contains("热度值") || content.contains("播放")
                    || content.contains("评分") || content.contains("推荐")) continue;
            if (!out.contains(content)) out.add(content);
        }
    }

    private static String heatFromSubTitles(JSONArray array) {
        if (array == null) return "";
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            if (item.optInt("data_type", -1) == 27) {
                String content = item.optString("content", "").trim();
                if (!content.isEmpty()) return content;
            }
        }
        return "";
    }

    private static String badgeFrom(JSONObject object) {
        Object reason = object.opt("recommend_reason_list");
        if (reason instanceof String) {
            String raw = (String) reason;
            if (raw.trim().startsWith("{")) {
                try {
                    reason = new JSONObject(raw);
                } catch (Exception ignored) {
                    reason = null;
                }
            }
        }
        if (reason instanceof JSONObject) {
            JSONObject json = (JSONObject) reason;
            JSONObject tagInfo = json.optJSONObject("tag_info");
            if (tagInfo != null) {
                String text = tagInfo.optString("text", "");
                if (!text.isEmpty()) return text;
            }
        }
        JSONObject tagInfo = object.optJSONObject("tag_info");
        if (tagInfo != null) return tagInfo.optString("text", "");
        return "";
    }

    private static boolean looksLikeCount(String text) {
        return text.matches("[0-9.]+(万|亿|w|W)?(播放|热度|人看过|次播放)?")
                || text.endsWith("播放") || text.endsWith("热度");
    }

    private static int firstPositive(int first, int second) {
        return first > 0 ? first : Math.max(second, 0);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    static String browserCoverFallback(String cover) {
        if (cover == null || cover.isEmpty()) return "";
        String lower = cover.toLowerCase(Locale.US);
        if (!lower.contains(".heic")) return "";
        try {
            URL url = new URL(cover);
            String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.US);
            if (!host.endsWith(".fqnovelpic.com") && !host.endsWith(".byteimg.com")) return "";
            String path = url.getPath();
            if (path == null || !path.startsWith("/novel-pic/") || !path.contains("~")) return "";
            return "https://p3-novel.byteimg.com"
                    + path.substring(0, path.indexOf('~'))
                    + "~tplv-shrink:640:0.image";
        } catch (Exception ignored) {
            return "";
        }
    }
}
