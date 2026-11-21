package com.hongguo.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Checks for updates without relying on a reachable github.com.
 *
 * <p>A small update.json on the main branch is mirrored through jsDelivr and
 * several GitHub proxies. GitHub Releases / Tags remain a fallback.</p>
 */
public final class UpdateChecker {
    private static final String OWNER_REPO = "husanpao/pistachio-drama-android";
    private static final String RAW_MANIFEST =
            "https://raw.githubusercontent.com/" + OWNER_REPO + "/main/update.json";
    private static final String RELEASES_API =
            "https://api.github.com/repos/" + OWNER_REPO + "/releases/latest";
    private static final String TAGS_API =
            "https://api.github.com/repos/" + OWNER_REPO + "/tags?per_page=30";
    private static final String RELEASES_PAGE =
            "https://github.com/" + OWNER_REPO + "/releases";
    private static final long AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000;

    private static final String[] MANIFEST_MIRRORS = {
            "https://ghproxy.net/" + RAW_MANIFEST,
            "https://gh-proxy.com/" + RAW_MANIFEST,
            "https://ghfast.top/" + RAW_MANIFEST,
            "https://raw.gitmirror.com/" + OWNER_REPO + "/main/update.json",
            "https://cdn.jsdelivr.net/gh/" + OWNER_REPO + "@main/update.json",
            "https://fastly.jsdelivr.net/gh/" + OWNER_REPO + "@main/update.json",
            "https://gcore.jsdelivr.net/gh/" + OWNER_REPO + "@main/update.json",
            RAW_MANIFEST
    };

    private UpdateChecker() {
    }

    private static final class Latest {
        final String version;
        final String pageUrl;
        final String downloadUrl;

        Latest(String version, String pageUrl, String downloadUrl) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.downloadUrl = downloadUrl;
        }
    }

    public static void check(Context context, boolean manual) {
        if (context == null) return;
        long now = System.currentTimeMillis();
        if (!manual && now - AppPrefs.lastUpdateCheck(context) < AUTO_CHECK_INTERVAL_MS) {
            return;
        }
        final Context app = context.getApplicationContext();
        final Context uiContext = context;
        App.io(() -> {
            Latest latest = null;
            boolean successful = false;
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");
            headers.put("Cache-Control", "no-cache");
            headers.put("User-Agent", "pistachio-drama-android");

            // 1. Mirrored update.json: works when github.com is blocked.
            for (String url : MANIFEST_MIRRORS) {
                try {
                    Http.Reply reply = Http.get(url, headers, 6000, 8000);
                    if (reply.status != 200 || reply.body.length == 0) continue;
                    JSONObject json = new JSONObject(reply.text());
                    String version = json.optString("version", "").trim();
                    if (version.isEmpty()) continue;
                    String page = json.optString("release_url", RELEASES_PAGE);
                    String download = mirroredDownload(json);
                    successful = true;
                    latest = new Latest(version, page, download);
                    break;
                } catch (Exception ignored) {
                }
            }

            // 2. GitHub Release API, then Tags.
            if (latest == null) {
                try {
                    Http.Reply release = Http.get(RELEASES_API, headers);
                    if (release.status == 200 && release.body.length > 0) {
                        JSONObject json = new JSONObject(release.text());
                        if (!json.optBoolean("draft", false)
                                && !json.optBoolean("prerelease", false)) {
                            String tag = json.optString("tag_name", "");
                            if (!tag.isEmpty()) {
                                successful = true;
                                latest = new Latest(tag,
                                        json.optString("html_url", RELEASES_PAGE),
                                        releaseDownload(json));
                            }
                        }
                    }
                    if (latest == null) {
                        Http.Reply tags = Http.get(TAGS_API, headers);
                        if (tags.status == 200 && tags.body.length > 0) {
                            successful = true;
                            latest = newestTag(new JSONArray(tags.text()));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            final Latest result = latest;
            final boolean ok = successful;
            if (ok) AppPrefs.setLastUpdateCheck(app, now);
            App.ui(() -> handleResult(uiContext, result, ok, manual));
        });
    }

    private static String mirroredDownload(JSONObject json) {
        String tag = json.optString("tag", json.optString("tag_name", "")).trim();
        String apkName = json.optString("apk_name", "").trim();
        String apkPath = json.optString("apk_path", "").trim();

        // Prefer a proxy for the repository copy. jsDelivr is a secondary
        // mirror because it is not reachable on every mainland network.
        if (!apkPath.isEmpty()) {
            String raw = "https://raw.githubusercontent.com/" + OWNER_REPO
                    + "/main/" + apkPath;
            return "https://ghproxy.net/" + raw;
        }
        if (!tag.isEmpty() && !apkName.isEmpty()) {
            String github = "https://github.com/" + OWNER_REPO
                    + "/releases/download/" + tag + "/" + apkName;
            return "https://ghproxy.net/" + github;
        }
        return json.optString("release_url", RELEASES_PAGE);
    }

    private static String releaseDownload(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.toLowerCase(java.util.Locale.US).endsWith(".apk")) continue;
                String url = asset.optString("browser_download_url", "");
                if (!url.isEmpty()) return "https://ghproxy.net/" + url;
            }
        }
        return release.optString("html_url", RELEASES_PAGE);
    }

    private static Latest newestTag(JSONArray tags) {
        Latest best = null;
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.optJSONObject(i);
            if (tag == null) continue;
            String name = tag.optString("name", "");
            if (name.isEmpty()) continue;
            if (best == null || compareVersion(name, best.version) > 0) {
                best = new Latest(name, RELEASES_PAGE, RELEASES_PAGE);
            }
        }
        return best;
    }

    private static void handleResult(Context context, Latest latest, boolean successful,
                                     boolean manual) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing()
                    || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                return;
            }
        }
        if (!successful) {
            if (manual) toast(context, "检查更新失败，请稍后再试");
            return;
        }
        if (latest == null) {
            if (manual) toast(context, "暂未发布新版本");
            return;
        }
        String current = versionName(context);
        if (compareVersion(latest.version, current) <= 0) {
            if (manual) toast(context, "已是最新版本 v" + current);
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("发现新版本")
                .setMessage("最新版本：" + latest.version + "\n当前版本：v" + current)
                .setPositiveButton("去更新", (dialog, which) ->
                        open(context, latest.downloadUrl == null
                                || latest.downloadUrl.isEmpty()
                                ? latest.pageUrl : latest.downloadUrl))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void open(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            toast(context, "无法打开更新页面");
        }
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "0";
        }
    }

    private static int compareVersion(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i] ? 1 : -1;
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("v") || text.startsWith("V")) text = text.substring(1);
        String[] parts = text.split("[^0-9]+");
        int[] out = new int[3];
        int at = 0;
        for (String part : parts) {
            if (part.isEmpty() || at >= 3) continue;
            try {
                out[at++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
