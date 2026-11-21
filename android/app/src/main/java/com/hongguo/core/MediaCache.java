package com.hongguo.core;

import android.content.Context;

import java.io.File;

/** Location and accounting for downloaded / decrypted episode files. */
public final class MediaCache {
    /** Shared by every download/decrypt path, so feed/player never write the same file. */
    public static final Object LOCK = new Object();

    private MediaCache() {
    }

    public static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "play");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File playable(Context context, String vid, String quality) {
        String suffix = quality == null || quality.isEmpty() ? "auto" : quality;
        return new File(dir(context), vid + "_" + suffix + ".mp4");
    }

    public static File legacyPlayable(Context context, String vid) {
        return new File(dir(context), vid + ".mp4");
    }

    public static File encrypted(Context context, String vid, String quality) {
        String suffix = quality == null || quality.isEmpty() ? "auto" : quality;
        return new File(dir(context), vid + "_" + suffix + ".enc");
    }

    public static File decrypted(Context context, String vid, String quality) {
        String suffix = quality == null || quality.isEmpty() ? "auto" : quality;
        return new File(dir(context), vid + "_" + suffix + ".dec");
    }

    public static long size(Context context) {
        long total = 0;
        File[] files = dir(context).listFiles();
        if (files == null) return 0;
        for (File file : files) total += file.length();
        return total;
    }

    public static int count(Context context) {
        int count = 0;
        File[] files = dir(context).listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.getName().endsWith(".mp4") && file.length() > 100_000) count++;
        }
        return count;
    }

    public static void clear(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /** Remove cached files that are not playable (e.g. ByteVC2 or unpatched CENC). */
    public static void cleanInvalid(Context context) {
        File[] files = dir(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".mp4") || file.length() <= 100_000) continue;
            String codec = CencMp4.videoCodec(file);
            if (codec.isEmpty() || CencMp4.isPlayable(file)) continue;
            int start = name.lastIndexOf('_');
            int end = name.lastIndexOf('.');
            String vid = start > 0 ? name.substring(0, start) : "";
            String quality = start > 0 && end > start
                    ? name.substring(start + 1, end) : "";
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            if (!vid.isEmpty()) OfflineStore.remove(context, vid, quality);
        }
    }
}
