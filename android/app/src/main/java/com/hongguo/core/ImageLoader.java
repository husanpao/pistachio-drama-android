package com.hongguo.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Memory + disk image cache with cover downsampling and the HEIC->JPEG CDN fallback. */
public final class ImageLoader {
    private static final int MAX_MEMORY = 24 * 1024 * 1024;
    private static final long MAX_DISK = 200L * 1024L * 1024L;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Set<String> LOADING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final java.util.Map<String, java.util.List<ImageView>> WAITERS =
            new ConcurrentHashMap<>();
    private static LruCache<String, Bitmap> memory;
    private static File diskDir;

    private ImageLoader() {
    }

    public static void init(Context context) {
        if (memory != null) return;
        Context app = context.getApplicationContext();
        memory = new LruCache<String, Bitmap>(MAX_MEMORY) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        diskDir = new File(app.getCacheDir(), "covers");
        //noinspection ResultOfMethodCallIgnored
        diskDir.mkdirs();
    }

    public static void load(String url, ImageView view) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(new ColorDrawable(Theme.SURFACE_ALT));
            return;
        }
        final String key = normalize(url);
        view.setTag(key);
        Bitmap cached = memory.get(key);
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(new ColorDrawable(Theme.SURFACE_ALT));
        java.util.List<ImageView> waiters = WAITERS.get(key);
        if (waiters == null) {
            waiters = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            WAITERS.put(key, waiters);
        }
        if (!waiters.contains(view)) waiters.add(view);
        if (!LOADING.add(key)) return;
        final int targetWidth = Math.max(view.getResources().getDisplayMetrics().widthPixels / 3, 320);
        POOL.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = loadBitmap(key, targetWidth);
            } catch (Throwable ignored) {
            } finally {
                LOADING.remove(key);
            }
            final Bitmap result = bitmap;
            if (result == null) {
                WAITERS.remove(key);
                return;
            }
            App.ui(() -> {
                java.util.List<ImageView> views = WAITERS.remove(key);
                if (views == null) return;
                for (ImageView target : new java.util.ArrayList<>(views)) {
                    if (key.equals(target.getTag())) {
                        target.setAlpha(0f);
                        target.setImageBitmap(result);
                        target.animate().alpha(1f).setDuration(180).start();
                    }
                }
            });
        });
    }

    public static void clearDisk() {
        File[] files = diskDir == null ? null : diskDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static long diskSize() {
        long total = 0;
        File[] files = diskDir == null ? null : diskDir.listFiles();
        if (files == null) return 0;
        for (File file : files) total += file.length();
        return total;
    }

    public static String normalize(String url) {
        if (url == null) return "";
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".heic")) {
            String fallback = DramaParser.browserCoverFallback(url);
            if (!fallback.isEmpty()) return fallback;
        }
        return url;
    }

    private static Bitmap loadBitmap(String url, int targetWidth) throws Exception {
        File file = diskFile(url);
        if (!file.isFile() || file.length() < 512) {
            download(url, file);
        }
        Bitmap bitmap = decode(file, targetWidth);
        if (bitmap != null) memory.put(url, bitmap);
        return bitmap;
    }

    private static void download(String url, File target) throws Exception {
        Http.Reply reply = Http.get(url, null);
        if (reply.status != 200 || reply.body.length == 0) {
            throw new IllegalStateException("cover HTTP " + reply.status);
        }
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(reply.body);
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IllegalStateException("cannot store cover");
        }
        trimDisk();
    }

    private static void trimDisk() {
        File[] files = diskDir == null ? null : diskDir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_DISK) return;
        java.util.Arrays.sort(files, (a, b) ->
                Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            if (total <= MAX_DISK * 9 / 10) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private static Bitmap decode(File file, int targetWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static File diskFile(String url) {
        return new File(diskDir, sha1(url) + ".img");
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte value : hash) sb.append(String.format(Locale.US, "%02x", value));
            return sb.toString();
        } catch (Exception error) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
