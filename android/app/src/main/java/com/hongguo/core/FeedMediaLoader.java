package com.hongguo.core;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Silent first-episode loader for the immersive feed. It reuses the same
 * download/decrypt/cache pipeline as the full player, but never touches UI.
 */
public final class FeedMediaLoader {
    public interface Callback {
        void onReady(File file, String quality);

        void onFailed();
    }

    private static final Object LOCK = MediaCache.LOCK;

    private FeedMediaLoader() {
    }

    public static void load(Context context, Drama drama, Callback callback) {
        final Context app = context.getApplicationContext();
        App.io(() -> {
            try {
                File file = ensure(app, drama);
                App.ui(() -> callback.onReady(file, qualityOf(file.getName())));
            } catch (Throwable error) {
                App.ui(callback::onFailed);
            }
        });
    }

    private static File ensure(Context context, Drama drama) throws Exception {
        String seriesId = drama.seriesId;
        String vid = drama.vid;
        if (vid == null || vid.isEmpty()) {
            DetailInfo info = new HongguoApi(context).detailInfo(seriesId,
                    drama.title, drama.cover, drama.score);
            if (info.episodes.isEmpty()) throw new IllegalStateException("empty episodes");
            vid = info.episodes.get(0).vid;
            if (drama.episodeCount <= 0) drama.episodeCount = info.episodeCount();
        }
        if (vid.isEmpty()) throw new IllegalStateException("empty vid");
        drama.vid = vid;

        File cached = findCached(context, vid);
        if (cached != null) return cached;

        synchronized (LOCK) {
            cached = findCached(context, vid);
            if (cached != null) return cached;

            List<HongguoApi.TrackInfo> tracks = null;
            for (int attempt = 0; attempt < 2 && tracks == null; attempt++) {
                try {
                    tracks = new HongguoApi(context).play(seriesId, vid);
                    if (tracks.isEmpty()) tracks = null;
                } catch (Throwable ignored) {
                    if (attempt == 0) Thread.sleep(350);
                }
            }
            if (tracks == null) throw new IllegalStateException("empty tracks");
            HongguoApi.TrackInfo track = chooseTrack(tracks);
            if (track == null) throw new IllegalStateException("no compatible track");

            File target = MediaCache.playable(context, vid, track.definition);
            if (isUsable(target)) return target;
            File encrypted = MediaCache.encrypted(context, vid, track.definition);
            download(track.url, encrypted);
            if (track.encrypted && track.spadeA != null && !track.spadeA.isEmpty()) {
                File plain = MediaCache.decrypted(context, vid, track.definition);
                CencMp4.Stats stats = CencMp4.decryptFile(encrypted, plain,
                        Spade.spadeToKey(track.spadeA));
                if (stats.videoSamples > 0 && stats.videoNalOk != stats.videoSamples) {
                    //noinspection ResultOfMethodCallIgnored
                    plain.delete();
                    //noinspection ResultOfMethodCallIgnored
                    encrypted.delete();
                    throw new IllegalStateException("decrypt check failed");
                }
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!plain.renameTo(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    plain.delete();
                    throw new IllegalStateException("save failed");
                }
            } else {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!encrypted.renameTo(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    encrypted.delete();
                    throw new IllegalStateException("save failed");
                }
            }
            //noinspection ResultOfMethodCallIgnored
            encrypted.delete();
            if (!isUsable(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw new IllegalStateException("not playable");
            }
            return target;
        }
    }

    public static File findCached(Context context, String vid) {
        if (vid == null || vid.isEmpty()) return null;
        for (String quality : new String[]{"720p", "1080p", "480p", "360p", "auto"}) {
            File file = MediaCache.playable(context, vid, quality);
            if (isUsable(file)) return file;
        }
        File legacy = MediaCache.legacyPlayable(context, vid);
        return isUsable(legacy) ? legacy : null;
    }

    private static boolean isUsable(File file) {
        return file.isFile() && file.length() > 100_000 && CencMp4.isPlayable(file);
    }

    private static HongguoApi.TrackInfo chooseTrack(List<HongguoApi.TrackInfo> tracks) {
        List<HongguoApi.TrackInfo> usable = new ArrayList<>();
        for (HongguoApi.TrackInfo track : tracks) {
            String codec = track.codec == null ? "" : track.codec.toLowerCase(Locale.US);
            if (!(codec.contains("bytevc2") || codec.contains("bvc2")
                    || codec.contains("vvc") || codec.contains("h266"))) {
                usable.add(track);
            }
        }
        if (usable.isEmpty()) return null;
        for (String wanted : new String[]{"720p", "1080p", "480p", "360p"}) {
            for (HongguoApi.TrackInfo track : usable) {
                if (wanted.equals(track.definition)) return track;
            }
        }
        return usable.get(0);
    }

    private static void download(String url, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", Http.UA);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String qualityOf(String name) {
        int start = name.lastIndexOf('_');
        int end = name.lastIndexOf('.');
        return start > 0 && end > start ? name.substring(start + 1, end) : "auto";
    }
}
