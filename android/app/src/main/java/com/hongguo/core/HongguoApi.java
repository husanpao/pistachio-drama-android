package com.hongguo.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Signed client for the anonymous hongguo content APIs.
 * The category/new/rank endpoints work with a one-shot identity; search needs the
 * anonymous guest registration implemented by {@link DeviceRegister}.
 */
public final class HongguoApi {
    private static final String API_BASE = "https://api5-normal-sinfonlineb.fqnovel.com";
    private static final String CATEGORY_PATH = "/reading/distribution/category/landpage/v/";
    private static final String PLAYBACK_V5_PRIMARY =
            "https://api5-normal-sinfonlineb.fqnovel.com/novel/player/multi_video_model/player/v1";
    private static final String PLAYBACK_V5_ALTERNATE =
            "https://api5-normal-sinfonlinea.fqnovel.com/novel/player/multi_video_model/player/v1";
    private static final String PLAYBACK_V5_UA =
            "com.phoenix.read/73932 (Linux; U; Android 12; zh_CN_#Hans; PGT-AN10; "
                    + "Build/V417IR;tt-ok/3.12.13.20)";
    private static final String PLAYBACK_VIDEO_TAB_UA =
            "com.phoenix.read/73932 (Linux; U; Android 16; zh_CN; 25053RT47C; "
                    + "Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 "
                    + "2026-01-23 QuicVersion:c67e9834 2025-09-08)";
    private static final String PLAYBACK_VIDEO_TAB =
            API_BASE + "/reading/bookapi/video_tab/multi_video_model/v1/";

    public static final String FILTER_GENRE = "genre";
    public static final String FILTER_THEME = "category_dim_theme";
    public static final String FILTER_ROLE = "category_dim_role";
    public static final String FILTER_EPOCH = "category_dim_epoch";
    public static final String FILTER_SORT = "sort";
    public static final String FILTER_GENDER = "gender";
    public static final String FILTER_TIME = "online_time";
    public static final String FILTER_STATUS = "creation_status";

    private final Context context;
    private String deviceId;
    private String installId;
    private String cdid;
    private String anonymousDeviceId;
    private String anonymousInstallId;
    private boolean registered;

    public HongguoApi() {
        this(null);
    }

    public HongguoApi(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
        DeviceSession saved = DeviceSessionStore.load(this.context);
        if (saved != null && saved.valid()) {
            this.deviceId = saved.deviceId;
            this.installId = saved.installId;
            this.cdid = saved.cdid;
            this.registered = true;
        } else {
            resetIdentity();
        }
        resetAnonymous();
    }

    /** Default selection for the recommendation feed. */
    public static Map<String, String> defaultFilters() {
        Map<String, String> filters = new HashMap<>();
        filters.put(FILTER_GENRE, "short_play");
        filters.put(FILTER_SORT, "hot_score");
        return filters;
    }

    private void resetIdentity() {
        this.deviceId = randomId();
        this.installId = randomId();
        this.cdid = UUID.randomUUID().toString();
        this.registered = false;
    }

    private void resetAnonymous() {
        this.anonymousDeviceId = randomId();
        this.anonymousInstallId = randomId();
    }

    private void ensureRegistered() throws Exception {
        if (registered) return;
        if (context == null) throw new IllegalStateException("缺少 Context，无法注册游客设备");
        DeviceSession session = DeviceRegister.register();
        this.deviceId = session.deviceId;
        this.installId = session.installId;
        this.cdid = session.cdid;
        this.registered = true;
        DeviceSessionStore.save(context, session);
    }

    public DeviceSession session() {
        return new DeviceSession(deviceId, installId, cdid, "");
    }

    /** Ensure the guest identity is registered before parallel list requests. */
    public void ensureReady() throws Exception {
        ensureRegistered();
    }

    // ---- lists ----

    public ListPage category(int offset, Map<String, String> filters, boolean selectors) throws Exception {
        JSONObject body = new JSONObject();
        body.put("filter_ids", "");
        body.put("req_scene", sceneFor(filters));
        body.put("offset", Math.max(offset, 0));
        body.put("need_selector_panel", selectors);
        body.put("limit", 15);
        body.put("select_items", selection(filters));
        body.put("session_id", "");
        body.put("req_type", selectors ? "default" : "only_content");
        body.put("client_req_type", 3);
        JSONObject payload = signedPostData(CATEGORY_PATH, body, false);
        JSONObject data = payload.optJSONObject("data");
        if (data == null) throw new IllegalStateException("分类响应缺少 data");
        return DramaParser.parseCategory(data, offset);
    }

    public ListPage newSeries(int offset) throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("change_type", "1");
        params.put("selected_items", "firstonlinetime_new");
        params.put("client_req_type", "1");
        params.put("cell_id", "7431550523368554558");
        params.put("limit", "20");
        params.put("offset", Integer.toString(Math.max(offset, 0)));
        params.put("cell_gender", "2");
        params.put("session_id", "");
        JSONObject payload = signedGet("/reading/bookapi/bookmall/cell/change/v1/", params, false);
        return DramaParser.parseNew(payload.optJSONObject("data"), offset);
    }

    public ListPage rank(int offset, String selected, String sessionId) throws Exception {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("tab_type", offset > 0 ? "18" : "26");
        params.put("client_req_type", "2");
        params.put("client_template", "2");
        params.put("background_selected_items", "");
        params.put("category_id", "0");
        params.put("limit", "20");
        params.put("cell_sub_id", "0");
        params.put("gender", "2");
        params.put("cell_id", "7470092475068071998");
        params.put("selected_items", selected == null ? "" : selected);
        params.put("sub_selected_items", "");
        params.put("panel_selected_items", "");
        params.put("offset", Integer.toString(Math.max(offset, 0)));
        params.put("session_id", sessionId == null ? "" : sessionId);
        params.put("filter_ids", "");
        params.put("unlimited_selector_change_type", "0");
        JSONObject payload = signedGet("/reading/bookapi/bookmall/cell/change/v", params, false);
        return DramaParser.parseRank(payload.optJSONObject("data"), offset);
    }

    public ListPage search(String text, int offset) throws Exception {
        return search(text, offset, "", "");
    }

    public ListPage search(String text, int offset, String searchId, String passback) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ensureRegistered();
                TreeMap<String, String> params = new TreeMap<>();
                params.put("bookshelf_search_plan", "4");
                params.put("use_correct", "true");
                params.put("user_is_login", "0");
                params.put("query", text);
                params.put("count", "20");
                params.put("offset", Integer.toString(Math.max(offset, 0)));
                params.put("tab_type", "11");
                params.put("search_id", searchId == null ? "" : searchId);
                params.put("passback", passback == null ? "" : passback);
                JSONObject payload = signedGet("/reading/bookapi/search/tab/v", params, true);
                return DramaParser.parseSearch(payload, offset);
            } catch (Exception error) {
                last = error;
                registered = false;
                Thread.sleep(150);
            }
        }
        throw last == null ? new IllegalStateException("搜索失败") : last;
    }

    // ---- detail / play ----

    public JSONObject detail(String seriesId) throws Exception {
        JSONObject body = new JSONObject();
        JSONObject biz = new JSONObject();
        biz.put("detail_page_version", 0);
        biz.put("disable_digg_stat", false);
        biz.put("disable_video_relate_book", false);
        biz.put("image_shrink_datas_str", "W3siaW1hZ2VfdHlwZSI6MywiaW1hZ2Vfd2lkdGgiOjkwMCwic2hyaW5rX3R5cGUiOjN9LHsiaW1hZ2VfdHlwZSI6NCwiaW1hZ2Vfd2lkdGgiOjU0LCJzaHJpbmtfdHlwZSI6NH1d");
        biz.put("need_all_video_definition", false);
        biz.put("need_mp4_align", false);
        biz.put("screen_width_px", "900");
        biz.put("source", 7);
        biz.put("use_os_player", false);
        biz.put("use_server_dns", false);
        body.put("biz_param", biz);
        body.put("series_id", seriesId);
        JSONObject payload = signedPostData("/novel/player/multi_video_detail/v1/", body, false);
        JSONObject data = payload.optJSONObject("data");
        JSONObject item = data == null ? null : data.optJSONObject(seriesId);
        JSONObject videoData = item == null ? null : item.optJSONObject("video_data");
        if (videoData == null) throw new IllegalStateException("详情响应缺少 video_data");
        return videoData;
    }

    public DetailInfo detailInfo(String seriesId, String title, String cover, String score) throws Exception {
        return DramaParser.parseDetail(detail(seriesId), title, cover, score);
    }

    public static final class TrackInfo {
        public String url;
        public String backupUrl;
        public String definition;
        public String codec;
        public long size;
        public String spadeA;
        public boolean encrypted;
    }

    public List<TrackInfo> play(String seriesId, String vid) throws Exception {
        Exception v5Error = null;
        try {
            List<TrackInfo> tracks = playVideoTab(vid);
            if (!tracks.isEmpty()) return tracks;
        } catch (Exception tabError) {
            v5Error = tabError;
        }
        for (String endpoint : new String[]{PLAYBACK_V5_PRIMARY, PLAYBACK_V5_ALTERNATE}) {
            try {
                List<TrackInfo> tracks = playV5(endpoint, vid);
                if (!tracks.isEmpty()) return tracks;
            } catch (Exception error) {
                if (v5Error == null) v5Error = error;
                else v5Error.addSuppressed(error);
            }
        }
        try {
            return playLegacy(seriesId, vid);
        } catch (Exception legacyError) {
            if (v5Error != null) v5Error.addSuppressed(legacyError);
            throw v5Error == null ? legacyError : v5Error;
        }
    }

    private List<TrackInfo> playV5(String endpoint, String vid) throws Exception {
        ensureRegistered();
        if (cdid == null || cdid.isEmpty()) {
            registered = false;
            ensureRegistered();
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("aid", "8662");
        values.put("app_name", "novelread");
        values.put("version_code", "74332");
        values.put("version_name", "7.4.3.32");
        values.put("channel", "vivo_8662_64");
        values.put("device_platform", "android");
        values.put("device_type", "PGT-AN10");
        values.put("os_version", "12");
        values.put("update_version_code", "74332");
        values.put("iid", installId);
        values.put("cdid", cdid);
        values.put("device_id", deviceId);

        JSONObject biz = new JSONObject();
        biz.put("detail_page_version", 0);
        biz.put("device_level", 1);
        biz.put("disable_digg_stat", false);
        biz.put("disable_video_relate_book", false);
        biz.put("need_all_video_definition", true);
        biz.put("need_mp4_align", false);
        biz.put("use_os_player", false);
        biz.put("use_server_dns", false);
        biz.put("video_platform", 1024);
        JSONObject idMap = new JSONObject();
        idMap.put("1004", new JSONArray().put(vid));
        JSONObject body = new JSONObject();
        body.put("biz_param", biz);
        body.put("dr_scene", "player");
        body.put("mixed_video_id_map", idMap);

        byte[] bodyBytes = gzip(body.toString().getBytes(StandardCharsets.UTF_8));
        long stamp = System.currentTimeMillis();
        PlaybackV5Signer.Result signed = PlaybackV5Signer.sign(values, bodyBytes, stamp);
        Map<String, String> headers = new HashMap<>();
        headers.putAll(signed.headers);
        headers.put("User-Agent", PLAYBACK_V5_UA);
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Content-Encoding", "gzip");
        headers.put("Accept", "application/json");
        Http.Reply reply = Http.post(endpoint + "?" + signed.query, bodyBytes, headers);
        if (reply.status != 200) {
            throw new IllegalStateException("v5 播放 HTTP " + reply.status + ": " + trim(reply.text()));
        }
        if (reply.body.length == 0) throw new EmptyResponseException();
        JSONObject payload = new JSONObject(reply.text());
        int code = payload.optInt("code", 0);
        if (code != 0) throw new IllegalStateException("v5 播放错误 " + code
                + ": " + payload.optString("message"));
        List<TrackInfo> tracks = parsePlayback(payload, vid);
        if (tracks.isEmpty()) throw new IllegalStateException("v5 播放响应没有可用清晰度");
        return tracks;
    }

    private List<TrackInfo> playVideoTab(String vid) throws Exception {
        ensureRegistered();
        if (cdid == null || cdid.isEmpty()) {
            registered = false;
            ensureRegistered();
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("aid", "8662");
        values.put("app_name", "novelread");
        values.put("version_code", "74332");
        values.put("version_name", "7.4.3.32");
        values.put("channel", "vivo_8662_64");
        values.put("device_platform", "android");
        values.put("device_type", "PGT-AN10");
        values.put("os_version", "12");
        values.put("update_version_code", "74332");
        values.put("iid", installId);
        values.put("cdid", cdid);
        values.put("device_id", deviceId);
        values.put("video_id", vid);
        values.put("video_id_type", "0");
        values.put("device_level", "1");
        values.put("need_all_video_definition", "1");
        values.put("need_mp4_align", "0");
        values.put("use_os_player", "0");
        values.put("video_platform", "1024");
        values.put("source", "0");
        values.put("from_video_id", "");

        long stamp = System.currentTimeMillis();
        PlaybackV5Signer.Result signed = PlaybackV5Signer.signKeys(
                values, new byte[0], stamp, PlaybackV5Signer.VIDEO_TAB_KEYS);
        Map<String, String> headers = new LinkedHashMap<>(signed.headers);
        headers.remove("X-SS-STUB");
        headers.put("User-Agent", PLAYBACK_VIDEO_TAB_UA);
        headers.put("Accept", "application/json; charset=utf-8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        Http.Reply reply = Http.get(PLAYBACK_VIDEO_TAB + "?" + signed.query, headers);
        if (reply.status != 200) {
            throw new IllegalStateException("video_tab HTTP " + reply.status + ": " + trim(reply.text()));
        }
        if (reply.body.length == 0) throw new EmptyResponseException();
        JSONObject payload = new JSONObject(reply.text());
        int code = payload.optInt("code", 0);
        if (code != 0) throw new IllegalStateException("video_tab 错误 " + code
                + ": " + payload.optString("message"));
        JSONObject data = payload.optJSONObject("data");
        if (data == null) throw new IllegalStateException("video_tab 缺少 data");
        JSONObject rows = data.optJSONObject("video_model_data");
        if (rows == null) rows = data;
        JSONObject entry = rows.optJSONObject(vid);
        Object modelValue = entry == null ? null : entry.opt("video_model");
        if (modelValue == null) modelValue = data.opt("video_model");
        JSONObject model = null;
        if (modelValue instanceof JSONObject) {
            model = (JSONObject) modelValue;
        } else if (modelValue instanceof String && ((String) modelValue).trim().startsWith("{")) {
            model = new JSONObject((String) modelValue);
        }
        if (model == null) throw new IllegalStateException("video_tab 缺少 video_model");
        List<TrackInfo> tracks = parseTracks(model);
        if (tracks.isEmpty()) throw new IllegalStateException("video_tab 没有可用清晰度");
        return tracks;
    }

    private List<TrackInfo> playLegacy(String seriesId, String vid) throws Exception {
        JSONObject body = new JSONObject();
        JSONObject map = new JSONObject();
        map.put("1", new JSONArray().put(vid));
        body.put("mixed_video_id_map", map);
        JSONObject biz = new JSONObject();
        biz.put("detail_page_version", 0);
        biz.put("device_level", 3);
        biz.put("disable_digg_stat", false);
        biz.put("disable_video_relate_book", false);
        biz.put("need_all_video_definition", true);
        biz.put("need_mp4_align", false);
        biz.put("use_os_player", false);
        biz.put("use_server_dns", false);
        biz.put("video_platform", 1024);
        body.put("biz_param", biz);
        JSONObject payload = signedPost("/novel/player/multi_video_model/v1/", body);
        List<TrackInfo> tracks = parsePlayback(payload, vid);
        if (tracks.isEmpty()) throw new IllegalStateException("播放响应没有可用清晰度");
        return tracks;
    }

    private List<TrackInfo> parsePlayback(JSONObject payload, String vid) throws Exception {
        JSONObject data = payload.optJSONObject("data");
        if (data == null) throw new IllegalStateException("播放响应缺少 data");
        JSONObject container = data.optJSONObject("video_model_data");
        if (container == null) container = data.optJSONObject("1004");
        if (container == null) container = data;
        JSONObject entry = container.optJSONObject(vid);
        Object modelValue = null;
        if (entry != null) {
            modelValue = entry.opt("video_model");
        } else if (data.optJSONObject(vid) != null) {
            modelValue = data.optJSONObject(vid).opt("video_model");
        } else {
            modelValue = data.opt("video_model");
        }
        JSONObject model = null;
        if (modelValue instanceof JSONObject) {
            model = (JSONObject) modelValue;
        } else if (modelValue instanceof String && ((String) modelValue).trim().startsWith("{")) {
            model = new JSONObject((String) modelValue);
        }
        if (model == null) throw new IllegalStateException("播放响应缺少 video_model");
        return parseTracks(model);
    }

    private List<TrackInfo> parseTracks(JSONObject model) {
        List<TrackInfo> tracks = new ArrayList<>();
        Object listValue = model.opt("video_list");
        List<JSONObject> entries = new ArrayList<>();
        if (listValue instanceof JSONArray) {
            JSONArray list = (JSONArray) listValue;
            for (int i = 0; i < list.length(); i++) {
                JSONObject entry = list.optJSONObject(i);
                if (entry != null) entries.add(entry);
            }
        } else if (listValue instanceof JSONObject) {
            JSONObject list = (JSONObject) listValue;
            java.util.Iterator<String> keys = list.keys();
            while (keys.hasNext()) {
                JSONObject entry = list.optJSONObject(keys.next());
                if (entry != null) entries.add(entry);
            }
        }
        for (JSONObject entry : entries) {
            JSONObject meta = entry.optJSONObject("video_meta");
            JSONObject encryption = entry.optJSONObject("encrypt_info");
            String url = decodeUrl(entry.optString("main_url", ""));
            if (url.isEmpty()) continue;
            TrackInfo track = new TrackInfo();
            track.url = url;
            track.backupUrl = decodeUrl(firstNonEmpty(entry.optString("backup_url"),
                    entry.optString("backup_url_1")));
            track.definition = meta == null
                    ? entry.optString("definition", "unknown")
                    : meta.optString("definition", "unknown");
            track.codec = meta == null
                    ? entry.optString("codec_type", "")
                    : meta.optString("codec_type", "");
            track.size = meta == null ? entry.optLong("size", 0) : entry.optLong("size", 0);
            track.encrypted = encryption != null
                    ? encryption.optBoolean("encrypt", false)
                    : entry.optBoolean("encrypt", false);
            track.spadeA = encryption != null
                    ? encryption.optString("spade_a", "")
                    : entry.optString("spade_a", "");
            tracks.add(track);
        }
        tracks.sort((a, b) -> qualityRank(a.definition) - qualityRank(b.definition));
        return tracks;
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static int qualityRank(String definition) {
        if ("360p".equals(definition)) return 0;
        if ("480p".equals(definition)) return 1;
        if ("540p".equals(definition)) return 2;
        if ("720p".equals(definition)) return 3;
        if ("1080p".equals(definition)) return 4;
        return 5;
    }

    private static String decodeUrl(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.isEmpty() || text.startsWith("http")) return text;
        try {
            byte[] decoded = android.util.Base64.decode(text, android.util.Base64.DEFAULT);
            String url = new String(decoded, StandardCharsets.UTF_8);
            if (url.startsWith("http")) return url;
        } catch (Throwable ignored) {
        }
        return text;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) return first;
        return second == null ? "" : second;
    }

    // ---- signed transport ----

    private JSONObject signedPostData(String path, JSONObject body, boolean guest) throws Exception {
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            String did = guest ? deviceId : anonymousDeviceId;
            String iid = guest ? installId : anonymousInstallId;
            try {
                TreeMap<String, String> query = baseQuery(did, iid);
                PassportSigner.Result signed = PassportSigner.sign(
                        query, bodyBytes, System.currentTimeMillis(), did);
                Map<String, String> headers = commonHeaders();
                headers.putAll(signed.headers);
                headers.put("Content-Type", "application/json; charset=utf-8");
                Http.Reply reply = Http.post(API_BASE + path + "?" + signed.query, bodyBytes, headers);
                if (reply.status != 200) {
                    throw new IllegalStateException("HTTP " + reply.status + ": " + trim(reply.text()));
                }
                if (reply.body.length == 0) {
                    throw new EmptyResponseException();
                }
                JSONObject json = new JSONObject(reply.text());
                JSONObject data = json.optJSONObject("data");
                JSONArray tabs = json.optJSONArray("search_tabs");
                boolean hasData = data != null && data.length() > 0;
                boolean hasTabs = tabs != null && tabs.length() > 0;
                if (!hasData && !hasTabs) {
                    throw new EmptyResponseException();
                }
                if (json.optInt("code", 0) != 0) {
                    throw new IllegalStateException("接口错误 " + json.optInt("code")
                            + ": " + json.optString("message"));
                }
                return json;
            } catch (Exception error) {
                last = error;
                if (attempt < 3) {
                    if (guest) registered = false;
                    else resetAnonymous();
                    Thread.sleep(160);
                }
            }
        }
        throw last == null ? new IllegalStateException("请求失败") : last;
    }

    private JSONObject signedPost(String path, JSONObject body) throws Exception {
        return signedPostData(path, body, false);
    }

    private JSONObject signedGet(String path, TreeMap<String, String> params, boolean guest) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            String did = guest ? deviceId : anonymousDeviceId;
            String iid = guest ? installId : anonymousInstallId;
            try {
                TreeMap<String, String> query = baseQuery(did, iid);
                query.putAll(params);
                PassportSigner.Result signed = PassportSigner.sign(
                        query, new byte[0], System.currentTimeMillis(), did);
                Map<String, String> headers = commonHeaders();
                headers.putAll(signed.headers);
                Http.Reply reply = Http.get(API_BASE + path + "?" + signed.query, headers);
                if (reply.status != 200) {
                    throw new IllegalStateException("HTTP " + reply.status + ": " + trim(reply.text()));
                }
                if (reply.body.length == 0) {
                    throw new EmptyResponseException();
                }
                JSONObject json = new JSONObject(reply.text());
                JSONObject data = json.optJSONObject("data");
                JSONArray tabs = json.optJSONArray("search_tabs");
                boolean hasData = data != null && data.length() > 0;
                boolean hasTabs = tabs != null && tabs.length() > 0;
                if (!hasData && !hasTabs) {
                    throw new EmptyResponseException();
                }
                if (json.optInt("code", 0) != 0) {
                    throw new IllegalStateException("接口错误 " + json.optInt("code")
                            + ": " + json.optString("message"));
                }
                return json;
            } catch (Exception error) {
                last = error;
                if (attempt < 3) {
                    if (guest) registered = false;
                    else resetAnonymous();
                    Thread.sleep(160);
                }
            }
        }
        throw last == null ? new IllegalStateException("请求失败") : last;
    }

    private static final class EmptyResponseException extends Exception {
        EmptyResponseException() {
            super("上游响应为空");
        }
    }

    private static String trim(String text) {
        if (text == null) return "";
        String value = text.trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private JSONObject selection(Map<String, String> filters) throws Exception {
        Map<String, String> values = filters == null ? new HashMap<>() : filters;
        JSONObject selection = new JSONObject();
        String[] keys = {FILTER_GENRE, FILTER_THEME, FILTER_ROLE, FILTER_EPOCH,
                FILTER_SORT, FILTER_GENDER, FILTER_TIME, FILTER_STATUS};
        for (String key : keys) {
            JSONArray array = new JSONArray();
            String value = values.get(key);
            if (value != null && !value.trim().isEmpty()) array.put(value.trim());
            selection.put(key, array);
        }
        if (selection.getJSONArray(FILTER_GENRE).length() == 0) {
            selection.put(FILTER_GENRE, new JSONArray().put("short_play"));
        }
        if (selection.getJSONArray(FILTER_SORT).length() == 0) {
            selection.put(FILTER_SORT, new JSONArray().put("hot_score"));
        }
        return selection;
    }

    private static String sceneFor(Map<String, String> filters) {
        String genre = filters == null ? null : filters.get(FILTER_GENRE);
        if ("comic_series".equals(genre)) return "comic_series";
        if ("ai_series".equals(genre)) return "ai_series";
        return "default";
    }

    private static Map<String, String> commonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-XS-From-Web", "0");
        headers.put("Sdk-Version", "2");
        headers.put("Passport-Sdk-Version", "6031190");
        headers.put("X-SS-DP", "8662");
        headers.put("X-Tt-Store-Region", "cn");
        headers.put("X-Tt-Store-Region-Src", "did");
        return headers;
    }

    private TreeMap<String, String> baseQuery(String did, String iid) {
        TreeMap<String, String> q = new TreeMap<>();
        q.put("ac", "wifi");
        q.put("aid", "8662");
        q.put("app_name", "novelread");
        q.put("channel", "update_64");
        q.put("device_brand", "Redmi");
        q.put("device_id", did);
        q.put("device_platform", "android");
        q.put("device_type", "25053RT47C");
        q.put("dpi", "520");
        q.put("iid", iid);
        q.put("language", "zh");
        q.put("manifest_version_code", "73532");
        q.put("os", "android");
        q.put("os_api", "36");
        q.put("os_version", "16");
        q.put("resolution", "1280*2772");
        q.put("ssmix", "a");
        q.put("update_version_code", "73532");
        q.put("version_code", "73532");
        q.put("version_name", "7.3.5.32");
        return q;
    }

    private static String randomId() {
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder(19);
        for (int i = 0; i < 19; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
