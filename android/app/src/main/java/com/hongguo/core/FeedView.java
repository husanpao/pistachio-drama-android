package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 推荐 tab: full-screen vertical short-video feed. */
public final class FeedView extends FrameLayout {
    private static final String[] GENRES = {"short_play", "comic_series", "ai_series"};
    private static final String[] KINDS = {"真人剧", "漫剧", "AI漫剧"};
    private static final ExecutorService FETCH = Executors.newFixedThreadPool(3);

    public interface Host {
        void openDetail(Drama drama);
    }

    private final Random random = new Random();
    private final int[] genreOffsets = {0, 0, 0};
    private final boolean[] genreHasMore = {true, true, true};

    private final Context context;
    private final HongguoApi api;
    private final Host host;
    private final ViewPager2 pager;
    private final FeedAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Drama> items = new ArrayList<>();
    private final Set<String> prefetching = new HashSet<>();
    private final TextView status;
    private final ProgressBarBox loadingBox;
    private final ExoPlayer player;
    private int active = -1;
    private String activeVid = "";
    private int dataToken;
    private int mediaToken;
    private boolean loading;
    private boolean hasMore = true;
    private int offset;
    private FeedPageView activePage;
    private boolean activeReady;
    private long activeSince;
    private long preferenceSince;
    private long lastProgressSave;

    public FeedView(Context context, Host host) {
        super(context);
        this.context = context;
        this.host = host;
        this.api = new HongguoApi(context);
        setBackgroundColor(Color.BLACK);

        pager = new ViewPager2(context);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(1);
        pager.setBackgroundColor(Color.BLACK);
        adapter = new FeedAdapter();
        pager.setAdapter(adapter);
        addView(pager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        player = new ExoPlayer.Builder(context).build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    if (activePage != null) activePage.onReady();
                    prefetchNext();
                } else if (state == Player.STATE_ENDED) {
                    advance();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (activePage != null) activePage.setPaused(!isPlaying);
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (activePage != null) {
                    activePage.applyVideoSize(videoSize.width, videoSize.height);
                }
            }
        });

        status = Ui.medium(context, "正在加载推荐…", 13, 0xCCFFFFFF);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0x99000000);
        status.setPadding(Theme.dp(context, 24), Theme.dp(context, 16),
                Theme.dp(context, 24), Theme.dp(context, 16));
        addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        loadingBox = new ProgressBarBox(context);
        addView(loadingBox, new FrameLayout.LayoutParams(
                Theme.dp(context, 38), Theme.dp(context, 38), Gravity.CENTER));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                play(position);
                if (position >= items.size() - 2) loadMore();
            }
        });
        pager.setPageTransformer((page, position) -> {
            page.setAlpha(1f - Math.min(0.25f, Math.abs(position) * 0.25f));
        });
        loadFirstPage();
    }

    private void loadFirstPage() {
        loadMixed(true);
    }

    private void loadMore() {
        loadMixed(false);
    }

    private void loadMixed(boolean reset) {
        if (loading || (!reset && !hasMore)) return;
        loading = true;
        if (reset) {
            loadingBox.setVisibility(View.VISIBLE);
            status.setVisibility(View.GONE);
        }
        final int token = ++dataToken;
        App.io(() -> {
            Set<String> seen = new HashSet<>();
            org.json.JSONArray seenArray = AppPrefs.feedSeen(context);
            for (int i = 0; i < seenArray.length(); i++) seen.add(seenArray.optString(i));

            try {
                api.ensureReady();
            } catch (Throwable ignored) {
            }

            final int[] offsets = new int[GENRES.length];
            for (int g = 0; g < GENRES.length; g++) {
                offsets[g] = reset ? random.nextInt(4) * 6 : genreOffsets[g];
            }
            List<Future<ListPage>> futures = new ArrayList<>();
            for (int g = 0; g < GENRES.length; g++) {
                final int index = g;
                final int startOffset = offsets[g];
                futures.add(FETCH.submit(() -> {
                    int offset = startOffset;
                    for (int attempt = 0; attempt < 2; attempt++) {
                        try {
                            java.util.Map<String, String> filters =
                                    new HashMap<>(HongguoApi.defaultFilters());
                            filters.put(HongguoApi.FILTER_GENRE, GENRES[index]);
                            HongguoApi client = new HongguoApi(context);
                            ListPage page = client.category(offset, filters, false);
                            if (page.items.isEmpty() && reset && offset > 0) {
                                offset = 0;
                                continue;
                            }
                            return page;
                        } catch (Throwable ignored) {
                            if (attempt == 0) {
                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                    return null;
                }));
            }

            List<List<Drama>> groups = new ArrayList<>();
            boolean anyMore = false;
            for (int g = 0; g < GENRES.length; g++) {
                List<Drama> group = new ArrayList<>();
                ListPage page = null;
                try {
                    page = futures.get(g).get(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                    futures.get(g).cancel(true);
                }
                if (page != null) {
                    for (Drama drama : page.items) {
                        if (drama.playable()) {
                            drama.feedKind = KINDS[g];
                            group.add(drama);
                        }
                    }
                    genreOffsets[g] = page.nextOffset > offsets[g] ? page.nextOffset : offsets[g] + 6;
                    genreHasMore[g] = page.hasMore;
                    anyMore |= page.hasMore;
                }
                groups.add(group);
            }

            final List<Drama> mixed = interleave(groups, random);
            final boolean more = anyMore;
            final List<Drama> picked = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (Drama drama : mixed) {
                if (drama.seriesId == null || drama.seriesId.isEmpty()) continue;
                if (!ids.add(drama.seriesId)) continue;
                if (seen.contains(drama.seriesId)) continue;
                picked.add(drama);
            }
            if (reset && picked.isEmpty()) {
                // Entire pool has been shown; reset the local rotation.
                AppPrefs.clearFeedSeen(context);
                Set<String> resetIds = new HashSet<>();
                for (Drama drama : mixed) {
                    if (drama.seriesId != null && resetIds.add(drama.seriesId)) {
                        picked.add(drama);
                    }
                }
            }
            RecommendationStore.rank(context, picked);
            App.ui(() -> {
                if (token != dataToken) return;
                loading = false;
                loadingBox.setVisibility(View.GONE);
                if (reset) {
                    items.clear();
                    items.addAll(picked);
                    hasMore = more;
                    adapter.notifyDataSetChanged();
                    if (items.isEmpty()) {
                        status.setText("暂无推荐，点击重试");
                        status.setVisibility(View.VISIBLE);
                        status.setOnClickListener(v -> loadFirstPage());
                    } else {
                        pager.post(() -> {
                            pager.setCurrentItem(0, false);
                            play(0);
                        });
                    }
                } else if (!picked.isEmpty()) {
                    int start = items.size();
                    Set<String> existing = new HashSet<>();
                    for (Drama drama : items) existing.add(drama.seriesId);
                    for (Drama drama : picked) {
                        if (existing.add(drama.seriesId)) items.add(drama);
                    }
                    hasMore = more;
                    int added = items.size() - start;
                    if (added > 0) adapter.notifyItemRangeInserted(start, added);
                } else {
                    hasMore = false;
                }
            });
        });
    }

    private List<Drama> interleave(List<List<Drama>> groups, Random random) {
        List<Drama> out = new ArrayList<>();
        int max = 0;
        for (List<Drama> group : groups) max = Math.max(max, group.size());
        for (int round = 0; round < max; round++) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++) order.add(i);
            java.util.Collections.shuffle(order, random);
            for (int index : order) {
                if (round < groups.get(index).size()) out.add(groups.get(index).get(round));
            }
        }
        return out;
    }

    private void play(int position) {
        if (position < 0 || position >= items.size()) return;
        if (position == active && player.getMediaItemCount() > 0) return;
        persistActive(true);
        activeReady = false;
        active = position;
        Drama drama = items.get(position);
        AppPrefs.markFeedSeen(context, drama.seriesId);
        activeVid = drama.vid == null ? "" : drama.vid;

        if (activePage != null) {
            activePage.setPaused(true);
            activePage.playerView().setPlayer(null);
            activePage = null;
        }
        player.pause();

        if (pager.getChildCount() == 0) return;
        RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof FeedHolder)) {
            pager.post(() -> play(position));
            return;
        }
        FeedHolder feedHolder = (FeedHolder) holder;
        activePage = feedHolder.page;
        activePage.onLoading();
        activePage.playerView().setPlayer(player);

        final int token = ++mediaToken;
        final FeedPageView page = activePage;
        File cached = FeedMediaLoader.findCached(context, drama.vid);
        if (cached != null) {
            startLocal(cached);
            return;
        }
        FeedMediaLoader.load(context, drama, new FeedMediaLoader.Callback() {
            @Override
            public void onReady(File file, String quality) {
                if (token != mediaToken || page != activePage || active != position) return;
                startLocal(file);
            }

            @Override
            public void onFailed() {
                if (token != mediaToken || page != activePage || active != position) return;
                page.onFailed();
                handler.postDelayed(() -> {
                    if (active == position) {
                        pager.setCurrentItem(position + 1, true);
                    }
                }, 1800);
            }
        });
    }

    private void startLocal(File file) {
        if (activePage == null) return;
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
        Drama current = active >= 0 && active < items.size() ? items.get(active) : null;
        long resume = current == null || current.vid == null ? 0 : AppPrefs.position(context, current.vid);
        if (resume > 1500) player.seekTo(resume);
        player.play();
        VideoSize size = player.getVideoSize();
        activePage.applyVideoSize(size.width, size.height);
        activePage.onReady();
        activeReady = true;
        activeSince = System.currentTimeMillis();
        preferenceSince = activeSince;
        lastProgressSave = activeSince;
        handler.removeCallbacks(progressTick);
        handler.post(progressTick);
    }

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (activePage != null && player.getDuration() > 0) {
                activePage.setProgress((float) player.getCurrentPosition()
                        / (float) player.getDuration());
            }
            long now = System.currentTimeMillis();
            if (now - lastProgressSave > 5000) persistActive(false);
            handler.postDelayed(this, 500);
        }
    };

    private void persistActive(boolean preference) {
        if (active < 0 || active >= items.size()) return;
        Drama drama = items.get(active);
        if (drama == null || drama.seriesId == null || drama.seriesId.isEmpty()) return;
        long position = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        long duration = player == null ? 0
                : Math.max(0, player.getDuration());
        long now = System.currentTimeMillis();
        long activeMs = preferenceSince <= 0 ? 0 : Math.max(0, now - preferenceSince);
        if (drama.vid != null && !drama.vid.isEmpty() && position > 1500) {
            AppPrefs.savePosition(context, drama.vid, position);
        }
        try {
            JSONObject item = new JSONObject();
            item.put("series_id", drama.seriesId);
            item.put("title", drama.title);
            item.put("cover", drama.cover);
            item.put("vid", drama.vid == null ? "" : drama.vid);
            item.put("index", 0);
            item.put("episode_cnt", drama.episodeCount);
            LibraryStore.addHistory(context, item);
        } catch (Exception ignored) {
        }
        AppPrefs.saveLastEpisode(context, drama.seriesId,
                drama.vid == null ? "" : drama.vid, 0);
        if (preference && activeReady) {
            RecommendationStore.recordView(context, drama, position, duration, activeMs);
            preferenceSince = now;
        }
        activeSince = now;
        lastProgressSave = now;
    }

    private void prefetchNext() {
        if (active + 1 >= items.size()) {
            loadMore();
            return;
        }
        // Warm the next two pages so a quick swipe does not wait for a fresh
        // download/decrypt. Downloads remain serialized by MediaCache.LOCK.
        for (int delta = 1; delta <= 2; delta++) {
            prefetchAt(active + delta);
        }
    }

    private void prefetchAt(int position) {
        if (position < 0 || position >= items.size()) return;
        Drama drama = items.get(position);
        String key = drama.seriesId + ":" + drama.vid;
        if (!prefetching.add(key)) return;
        FeedMediaLoader.load(context, drama, new FeedMediaLoader.Callback() {
            @Override
            public void onReady(File file, String quality) {
                prefetching.remove(key);
            }

            @Override
            public void onFailed() {
                prefetching.remove(key);
            }
        });
    }

    private void advance() {
        if (active < 0) return;
        if (active + 1 < items.size()) {
            pager.setCurrentItem(active + 1, true);
        } else if (hasMore) {
            loadMore();
        }
    }

    private void notInterested(Drama drama) {
        if (drama == null) return;
        RecommendationStore.recordNegative(context, drama);
        AppPrefs.markFeedSeen(context, drama.seriesId);
        activeReady = false;
        Ui.toast(context, "已减少这类推荐");
        int next = active + 1;
        if (next < items.size()) {
            pager.setCurrentItem(next, true);
        }
    }

    public void onHidden() {
        persistActive(true);
        activeReady = false;
        handler.removeCallbacks(progressTick);
        if (player.isPlaying()) player.pause();
    }

    public void onShown() {
        if (active >= 0 && player.getMediaItemCount() > 0) {
            activeSince = System.currentTimeMillis();
            preferenceSince = activeSince;
            lastProgressSave = activeSince;
            player.play();
            handler.post(progressTick);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        persistActive(true);
        activeReady = false;
        handler.removeCallbacksAndMessages(null);
        player.release();
        super.onDetachedFromWindow();
    }

    private final class FeedAdapter extends RecyclerView.Adapter<FeedHolder> {
        @NonNull
        @Override
        public FeedHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FeedPageView page = new FeedPageView(context, new FeedPageView.Actions() {
                @Override
                public void onWatchAll(Drama drama) {
                    host.openDetail(drama);
                }

                @Override
                public void onToggleFavorite(Drama drama) {
                    try {
                        boolean found = LibraryStore.isFavorite(context, drama.seriesId);
                        JSONObject item = new JSONObject();
                        item.put("series_id", drama.seriesId);
                        item.put("title", drama.title);
                        item.put("cover", drama.cover);
                        item.put("score", drama.score);
                        item.put("episode_cnt", drama.episodeCount);
                        LibraryStore.toggleFavorite(context, item);
                        if (!found) RecommendationStore.recordFavorite(context, drama);
                        if (activePage != null) activePage.updateFavorite();
                        Ui.toast(context, found ? "已取消追剧" : "已追剧");
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onEpisodes(Drama drama) {
                    host.openDetail(drama);
                }

                @Override
                public void onNotInterested(Drama drama) {
                    notInterested(drama);
                }

                @Override
                public void onSearch() {
                    SearchActivity.open(context);
                }

                @Override
                public void onTogglePlay() {
                    if (player.isPlaying()) {
                        player.pause();
                        if (activePage != null) activePage.showPlayState(false);
                    } else {
                        player.play();
                        if (activePage != null) activePage.showPlayState(true);
                    }
                }
            });
            page.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new FeedHolder(page);
        }

        @Override
        public void onBindViewHolder(@NonNull FeedHolder holder, int position) {
            holder.page.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class FeedHolder extends RecyclerView.ViewHolder {
        final FeedPageView page;

        FeedHolder(FeedPageView page) {
            super(page);
            this.page = page;
        }
    }

    private static final class ProgressBarBox extends LinearLayout {
        ProgressBarBox(Context context) {
            super(context);
            setGravity(Gravity.CENTER);
            android.widget.ProgressBar bar = new android.widget.ProgressBar(context);
            bar.setIndeterminate(true);
            bar.getIndeterminateDrawable().setColorFilter(
                    Theme.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
            addView(bar, new LinearLayout.LayoutParams(
                    Theme.dp(context, 34), Theme.dp(context, 34)));
        }
    }
}
