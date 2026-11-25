package com.hongguo.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks for updates through mirrored update.json, then GitHub Releases/Tags.
 * New versions are downloaded in-app and handed to the system package installer.
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
        final String sha256;
        final List<String> downloadUrls;

        Latest(String version, String pageUrl, String sha256, List<String> downloadUrls) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.sha256 = sha256;
            this.downloadUrls = downloadUrls;
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

            for (String url : MANIFEST_MIRRORS) {
                try {
                    Http.Reply reply = Http.get(url, headers, 6000, 8000);
                    if (reply.status != 200 || reply.body.length == 0) continue;
                    JSONObject json = new JSONObject(reply.text());
                    String version = json.optString("version", "").trim();
                    if (version.isEmpty()) continue;
                    String page = json.optString("release_url", RELEASES_PAGE);
                    successful = true;
                    latest = new Latest(version, page,
                            json.optString("sha256", "").trim(),
                            mirroredDownloads(json));
                    break;
                } catch (Exception ignored) {
                }
            }

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
                                        releaseSha(json), releaseDownloads(json));
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

    private static List<String> mirroredDownloads(JSONObject json) {
        List<String> urls = new ArrayList<>();
        String tag = json.optString("tag", json.optString("tag_name", "")).trim();
        String apkName = json.optString("apk_name", "").trim();
        String apkPath = json.optString("apk_path", "").trim();

        if (!apkPath.isEmpty()) {
            String raw = "https://raw.githubusercontent.com/" + OWNER_REPO + "/main/" + apkPath;
            urls.add("https://ghproxy.net/" + raw);
            urls.add("https://gh-proxy.com/" + raw);
            urls.add("https://ghfast.top/" + raw);
            urls.add("https://cdn.jsdelivr.net/gh/" + OWNER_REPO + "@main/" + apkPath);
        }
        if (!tag.isEmpty() && !apkName.isEmpty()) {
            String github = "https://github.com/" + OWNER_REPO
                    + "/releases/download/" + tag + "/" + apkName;
            urls.add("https://ghproxy.net/" + github);
            urls.add("https://gh-proxy.com/" + github);
            urls.add(github);
        }
        if (urls.isEmpty()) urls.add(json.optString("release_url", RELEASES_PAGE));
        return urls;
    }

    private static String releaseSha(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                String digest = asset.optString("digest", "");
                if (digest.startsWith("sha256:")) return digest.substring(7);
            }
        }
        return "";
    }

    private static List<String> releaseDownloads(JSONObject release) {
        List<String> urls = new ArrayList<>();
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) continue;
                String url = asset.optString("browser_download_url", "");
                if (!url.isEmpty()) {
                    urls.add("https://ghproxy.net/" + url);
                    urls.add("https://gh-proxy.com/" + url);
                    urls.add(url);
                }
            }
        }
        if (urls.isEmpty()) urls.add(release.optString("html_url", RELEASES_PAGE));
        return urls;
    }

    private static Latest newestTag(JSONArray tags) {
        Latest best = null;
        for (int i = 0; i < tags.length(); i++) {
            JSONObject tag = tags.optJSONObject(i);
            if (tag == null) continue;
            String name = tag.optString("name", "");
            if (name.isEmpty()) continue;
            if (best == null || compareVersion(name, best.version) > 0) {
                List<String> urls = new ArrayList<>();
                urls.add(RELEASES_PAGE);
                best = new Latest(name, RELEASES_PAGE, "", urls);
            }
        }
        return best;
    }

    private static void handleResult(Context context, Latest latest, boolean successful,
                                     boolean manual) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
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
                .setPositiveButton("立即更新", (dialog, which) -> startUpdate(context, latest))
                .setNegativeButton("稍后", null)
                .show();
    }

    private static void startUpdate(Context context, Latest latest) {
        if (latest.downloadUrls.isEmpty()
                || !latest.downloadUrls.get(0).toLowerCase(Locale.US).contains(".apk")) {
            open(context, latest.pageUrl);
            return;
        }
        if (Build.VERSION.SDK_INT >= 26
                && !context.getPackageManager().canRequestPackageInstalls()) {
            toast(context, "请允许安装未知应用后，再点击检查更新");
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settings);
            } catch (Exception ignored) {
            }
            return;
        }
        if (!(context instanceof Activity)) {
            open(context, latest.downloadUrls.get(0));
            return;
        }

        final AtomicBoolean canceled = new AtomicBoolean(false);
        ProgressDialog progress = new ProgressDialog(context);
        progress.setTitle("正在下载更新");
        progress.setMessage("准备下载 " + latest.version);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCanceledOnTouchOutside(false);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE, "取消", (dialog, which) -> {
            canceled.set(true);
            dialog.dismiss();
        });
        progress.show();

        final Context app = context.getApplicationContext();
        App.io(() -> {
            File dir = new File(app.getCacheDir(), "updates");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File apk = new File(dir, "update-" + safeName(latest.version) + ".apk");
            Exception lastError = null;
            boolean ok = false;
            for (String url : latest.downloadUrls) {
                if (canceled.get()) break;
                try {
                    download(url, apk, latest.sha256, progress, canceled);
                    ok = true;
                    break;
                } catch (Exception error) {
                    lastError = error;
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                }
            }
            final boolean success = ok;
            final Exception failure = lastError;
            App.ui(() -> {
                if (!canceled.get()) progress.dismiss();
                if (success) {
                    install(context, apk);
                } else if (!canceled.get()) {
                    toast(context, "更新下载失败"
                            + (failure == null ? "" : "：" + failure.getMessage()));
                }
            });
        });
    }

    private static void download(String url, File target, String expectedSha,
                                 ProgressDialog progress, AtomicBoolean canceled) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "pistachio-drama-android");
        connection.setRequestProperty("Accept", "application/octet-stream");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException("HTTP " + status);
            long total = connection.getContentLengthLong();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536];
                long received = 0;
                int count;
                int lastPercent = -1;
                while ((count = input.read(buffer)) >= 0) {
                    if (canceled.get()) throw new IllegalStateException("已取消");
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    received += count;
                    if (total > 0) {
                        int percent = (int) Math.min(100, received * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final int value = percent;
                            App.ui(() -> {
                                if (!canceled.get()) progress.setProgress(value);
                            });
                        }
                    }
                }
            }
            if (target.length() < 1_000_000) throw new IllegalStateException("文件过小");
            if (expectedSha != null && !expectedSha.isEmpty()) {
                String actual = hex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha)) {
                    throw new IllegalStateException("校验失败");
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void install(Context context, File apk) {
        try {
            Uri uri = ApkProvider.uriFor(context.getPackageName(), apk.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception error) {
            toast(context, "无法启动安装程序，请在设置中允许安装未知应用");
        }
    }

    private static String safeName(String version) {
        return version.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    private static String hex(byte[] data) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            out[i * 2] = digits[(data[i] >>> 4) & 15];
            out[i * 2 + 1] = digits[data[i] & 15];
        }
        return new String(out);
    }

    private static void open(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
