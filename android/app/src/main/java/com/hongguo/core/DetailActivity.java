package com.hongguo.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Series detail: hero, metadata, episode grid, recommendations and a play bar. */
public class DetailActivity extends Activity {
    private static final String EXTRA_SERIES_ID = "series_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_COVER = "cover";
    private static final String EXTRA_SCORE = "score";
    private static final String EXTRA_VID = "vid";

    private HongguoApi api;
    private String seriesId;
    private String initialTitle;
    private String initialCover;
    private String initialScore;
    private String fallbackVid;
    private DetailInfo info;
    private boolean favorite;
    private int lastIndex;

    private StateView state;
    private CoverImageView heroCover;
    private TextView heroBadge;
    private TextView heroEpisodes;
    private TextView heroScore;
    private TextView heroPlayCount;
    private TextView titleView;
    private TextView metaView;
    private TextView introView;
    private TextView introToggle;
    private FlowLayout tagRow;
    private GridLayout episodeGrid;
    private LinearLayout relatedRow;
    private TextView favoriteIconText;
    private TextView playButton;
    private TextView favoriteButton;
    private TextView episodeTitle;
    private TextView episodeToggle;
    private HorizontalScrollView episodeRangeScroll;
    private LinearLayout episodeRangeRow;
    private int episodeRangePage;
    private boolean episodeRangeExplicit;
    private boolean introExpanded;
    private boolean episodesExpanded;
    private java.util.Set<Integer> watched = new java.util.HashSet<>();

    public static void open(Context context, Drama drama) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(EXTRA_SERIES_ID, drama.seriesId);
        intent.putExtra(EXTRA_TITLE, drama.title);
        intent.putExtra(EXTRA_COVER, drama.cover);
        intent.putExtra(EXTRA_SCORE, drama.score);
        intent.putExtra(EXTRA_VID, drama.vid);
        context.startActivity(intent);
    }

    public static void open(Context context, String seriesId, String title, String cover, String score) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COVER, cover);
        intent.putExtra(EXTRA_SCORE, score);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.lightStatusBar(this, Theme.SURFACE);
        seriesId = getIntent().getStringExtra(EXTRA_SERIES_ID);
        initialTitle = getIntent().getStringExtra(EXTRA_TITLE);
        initialCover = getIntent().getStringExtra(EXTRA_COVER);
        initialScore = getIntent().getStringExtra(EXTRA_SCORE);
        fallbackVid = getIntent().getStringExtra(EXTRA_VID);
        if (seriesId == null || seriesId.isEmpty()) {
            Ui.toast(this, "缺少短剧 ID");
            finish();
            return;
        }
        api = new HongguoApi(this);
        favorite = LibraryStore.isFavorite(this, seriesId);
        JSONObject last = AppPrefs.lastEpisode(this, seriesId);
        lastIndex = last.optInt("index", 0);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Theme.BG);
        root.addView(buildTopBar());
        root.addView(Ui.divider(this));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Theme.dp(this, 80));
        LinearLayout content = Ui.column(this);
        content.setPadding(0, Theme.dp(this, 4), 0, Theme.dp(this, 20));
        scroll.addView(content);
        body.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content.addView(buildHero());
        content.addView(buildInfoCard());
        content.addView(buildEpisodeSection());
        content.addView(buildRelatedSection());

        root.addView(buildBottomBar());
        state = new StateView(this);
        state.showLoading("加载中…");
        body.addView(state, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        load();
    }

    private View buildTopBar() {
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 12), Theme.dp(this, 8));
        bar.setBackgroundColor(Theme.SURFACE);
        LinearLayout back = Ui.row(this);
        back.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 8), Theme.dp(this, 8));
        back.addView(Ui.icon(this, R.drawable.ic_back, 22, Theme.TEXT));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        TextView title = Ui.medium(this,
                initialTitle == null || initialTitle.isEmpty() ? "短剧详情" : initialTitle,
                16, Theme.TEXT);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = Theme.dp(this, 2);
        bar.addView(title, titleParams);
        ImageView search = Ui.icon(this, R.drawable.ic_search, 21, Theme.TEXT);
        search.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 8), Theme.dp(this, 8));
        search.setOnClickListener(v -> SearchActivity.open(this));
        bar.addView(search);
        return bar;
    }

    private View buildHero() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Theme.round(Theme.SURFACE, Theme.dp(this, 18)));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 230));
        params.setMargins(Theme.dp(this, 12), Theme.dp(this, 12), Theme.dp(this, 12), 0);
        card.setLayoutParams(params);

        heroCover = new CoverImageView(this);
        heroCover.setCornerRadius(0);
        heroCover.setHeightRatio(0);
        card.addView(heroCover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackgroundResource(R.drawable.bg_scrim_bottom);
        card.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 120), Gravity.BOTTOM));

        heroBadge = Ui.badge(this, "", R.drawable.bg_badge_new, Color.WHITE);
        heroBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.setMargins(Theme.dp(this, 12), Theme.dp(this, 12), 0, 0);
        card.addView(heroBadge, badgeParams);

        LinearLayout bottom = Ui.row(this);
        bottom.setPadding(Theme.dp(this, 14), 0, Theme.dp(this, 14), Theme.dp(this, 12));
        heroEpisodes = Ui.medium(this, "", 11, Color.WHITE);
        heroEpisodes.setBackgroundResource(R.drawable.bg_badge_ep);
        heroEpisodes.setPadding(Theme.dp(this, 8), Theme.dp(this, 4),
                Theme.dp(this, 8), Theme.dp(this, 4));
        bottom.addView(heroEpisodes);
        heroScore = Ui.medium(this, "", 11, Color.WHITE);
        heroScore.setBackgroundResource(R.drawable.bg_badge_ep);
        heroScore.setPadding(Theme.dp(this, 8), Theme.dp(this, 4),
                Theme.dp(this, 8), Theme.dp(this, 4));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.leftMargin = Theme.dp(this, 6);
        bottom.addView(heroScore, scoreParams);
        View spacer = new View(this);
        bottom.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        heroPlayCount = Ui.text(this, "", 11, 0xEEFFFFFF);
        bottom.addView(heroPlayCount);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM;
        card.addView(bottom, bottomParams);

        FrameLayout play = new FrameLayout(this);
        play.setBackground(Theme.round(0xCCFFFFFF, Theme.dp(this, 30)));
        ImageView playIcon = Ui.icon(this, R.drawable.ic_play, 26, Theme.BRAND);
        play.addView(playIcon, new FrameLayout.LayoutParams(
                Theme.dp(this, 26), Theme.dp(this, 26), Gravity.CENTER));
        play.setOnClickListener(v -> playEpisode(lastIndex));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                Theme.dp(this, 58), Theme.dp(this, 58), Gravity.CENTER);
        card.addView(play, playParams);
        Ui.ripple(play);
        return card;
    }

    private View buildInfoCard() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Theme.round(Theme.SURFACE, Theme.dp(this, 18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Theme.dp(this, 12), Theme.dp(this, 10), Theme.dp(this, 12), 0);
        card.setLayoutParams(params);
        card.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                Theme.dp(this, 16), Theme.dp(this, 16));

        titleView = Ui.medium(this, "", 21, Theme.TEXT);
        titleView.setLineSpacing(Theme.dp(this, 2), 1f);
        card.addView(titleView);

        metaView = Ui.text(this, "", 11, Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Theme.dp(this, 6);
        card.addView(metaView, metaParams);

        tagRow = new FlowLayout(this);
        tagRow.setPadding(0, Theme.dp(this, 10), 0, 0);
        card.addView(tagRow);

        introView = Ui.text(this, "", 12.5f, Theme.TEXT_SECONDARY);
        introView.setMaxLines(3);
        introView.setEllipsize(TextUtils.TruncateAt.END);
        introView.setLineSpacing(Theme.dp(this, 4), 1f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = Theme.dp(this, 12);
        card.addView(introView, introParams);

        introToggle = Ui.medium(this, "展开", 11, Theme.BRAND);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.topMargin = Theme.dp(this, 4);
        card.addView(introToggle, toggleParams);
        introToggle.setOnClickListener(v -> {
            introExpanded = !introExpanded;
            introView.setMaxLines(introExpanded ? Integer.MAX_VALUE : 3);
            introView.setEllipsize(introExpanded ? null : TextUtils.TruncateAt.END);
            introToggle.setText(introExpanded ? "收起" : "展开");
        });
        introToggle.setVisibility(View.GONE);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Theme.dp(this, 14);
        card.addView(actions, actionParams);

        favoriteButton = Ui.medium(this, "收藏", 12, Theme.TEXT_SECONDARY);
        favoriteButton.setGravity(Gravity.CENTER);
        favoriteButton.setBackgroundResource(R.drawable.bg_chip_normal);
        favoriteButton.setPadding(Theme.dp(this, 18), Theme.dp(this, 9),
                Theme.dp(this, 18), Theme.dp(this, 9));
        actions.addView(favoriteButton);
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        return card;
    }

    private View buildEpisodeSection() {
        LinearLayout section = Ui.column(this);
        section.setBackground(Theme.round(Theme.SURFACE, Theme.dp(this, 18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Theme.dp(this, 12), Theme.dp(this, 10), Theme.dp(this, 12), 0);
        section.setLayoutParams(params);
        section.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                Theme.dp(this, 16), Theme.dp(this, 16));

        LinearLayout header = Ui.row(this);
        header.addView(Ui.medium(this, "选集", 17, Theme.TEXT));
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        episodeTitle = Ui.text(this, "", 11, Theme.TEXT_TERTIARY);
        header.addView(episodeTitle);

        episodeToggle = Ui.medium(this, "展开", 11, Theme.BRAND);
        episodeToggle.setPadding(Theme.dp(this, 10), Theme.dp(this, 4),
                Theme.dp(this, 2), Theme.dp(this, 4));
        episodeToggle.setVisibility(View.GONE);
        episodeToggle.setOnClickListener(v -> {
            episodesExpanded = !episodesExpanded;
            episodeRangeExplicit = false;
            if (info != null) buildEpisodeGrid(info);
        });
        header.addView(episodeToggle);
        section.addView(header);

        episodeRangeScroll = new HorizontalScrollView(this);
        episodeRangeScroll.setHorizontalScrollBarEnabled(false);
        episodeRangeRow = Ui.row(this);
        episodeRangeScroll.addView(episodeRangeRow);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeParams.topMargin = Theme.dp(this, 10);
        section.addView(episodeRangeScroll, rangeParams);
        episodeRangeScroll.setVisibility(View.GONE);

        episodeGrid = new GridLayout(this);
        episodeGrid.setColumnCount(6);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = Theme.dp(this, 12);
        section.addView(episodeGrid, gridParams);
        return section;
    }

    private View buildRelatedSection() {
        LinearLayout section = Ui.column(this);
        section.setBackground(Theme.round(Theme.SURFACE, Theme.dp(this, 18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Theme.dp(this, 12), Theme.dp(this, 10), Theme.dp(this, 12), 0);
        section.setLayoutParams(params);
        section.setPadding(Theme.dp(this, 16), Theme.dp(this, 14),
                Theme.dp(this, 16), Theme.dp(this, 16));

        LinearLayout header = Ui.row(this);
        header.addView(Ui.medium(this, "相关推荐", 17, Theme.TEXT));
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(Ui.text(this, "换一部看看", 11, Theme.TEXT_TERTIARY));
        section.addView(header);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, Theme.dp(this, 12), 0, 0);
        relatedRow = Ui.row(this);
        scroll.addView(relatedRow);
        section.addView(scroll);
        return section;
    }

    private View buildBottomBar() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(Theme.SURFACE);
        bar.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                Theme.dp(this, 12), Theme.dp(this, 8));
        bar.setElevation(Theme.dp(this, 12));

        LinearLayout favoriteBar = Ui.column(this);
        favoriteBar.setGravity(Gravity.CENTER);
        favoriteBar.setPadding(Theme.dp(this, 8), Theme.dp(this, 2),
                Theme.dp(this, 8), Theme.dp(this, 2));
        favoriteIconText = Ui.medium(this, "♡", 20, Theme.TEXT_SECONDARY);
        favoriteIconText.setGravity(Gravity.CENTER);
        favoriteBar.addView(favoriteIconText);
        TextView favoriteLabel = Ui.text(this, "收藏", 9, Theme.TEXT_SECONDARY);
        favoriteLabel.setGravity(Gravity.CENTER);
        favoriteBar.addView(favoriteLabel);
        favoriteBar.setOnClickListener(v -> toggleFavorite());
        bar.addView(favoriteBar, new LinearLayout.LayoutParams(
                Theme.dp(this, 52), Theme.dp(this, 52)));

        playButton = Ui.medium(this, "立即播放", 14, Color.WHITE);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(Theme.round(Theme.BRAND, Theme.dp(this, 25)));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                0, Theme.dp(this, 48), 1f);
        playParams.leftMargin = Theme.dp(this, 6);
        bar.addView(playButton, playParams);
        playButton.setOnClickListener(v -> playEpisode(lastIndex));
        updateFavoriteUi();
        return bar;
    }

    private void load() {
        App.io(() -> {
            try {
                DetailInfo detail = null;
                Throwable last = null;
                for (int attempt = 0; attempt < 2 && detail == null; attempt++) {
                    try {
                        DetailInfo candidate = api.detailInfo(seriesId,
                                initialTitle, initialCover, initialScore);
                        if (!candidate.episodes.isEmpty()) {
                            detail = candidate;
                        } else {
                            last = new IllegalStateException("详情响应缺少 video_data");
                        }
                    } catch (Throwable error) {
                        last = error;
                    }
                    if (detail == null && attempt == 0) Thread.sleep(350);
                }
                if (detail == null) throw last == null
                        ? new IllegalStateException("详情响应缺少 video_data") : last;
                ListPage related = null;
                try {
                    related = api.category(0, HongguoApi.defaultFilters(), false);
                } catch (Throwable ignored) {
                }
                final DetailInfo resolved = detail;
                final ListPage relatedPage = related;
                App.ui(() -> {
                    applyInfo(resolved);
                    if (relatedPage != null) bindRelated(relatedPage);
                });
            } catch (Throwable error) {
                App.ui(() -> applyFallback(error));
            }
        });
    }

    private void applyFallback(Throwable error) {
        if (fallbackVid == null || fallbackVid.isEmpty()) {
            state.showError("详情加载失败",
                    error.getMessage() == null ? "请稍后再试" : error.getMessage(),
                    this::load);
            return;
        }
        DetailInfo fallback = new DetailInfo();
        fallback.seriesId = seriesId;
        fallback.title = initialTitle == null ? "未命名短剧" : initialTitle;
        fallback.cover = initialCover == null ? "" : initialCover;
        fallback.score = initialScore == null ? "" : initialScore;
        Episode episode = new Episode();
        episode.vid = fallbackVid;
        episode.index = 1;
        episode.title = "第1集";
        fallback.episodes.add(episode);
        applyInfo(fallback);
        Ui.toast(this, "详情不完整，可直接播放");
    }

    private void applyInfo(DetailInfo detail) {
        info = detail;
        episodesExpanded = false;
        episodeRangeExplicit = false;
        if (lastIndex > 0) LibraryStore.seedWatched(this, detail.seriesId, lastIndex);
        watched = LibraryStore.watched(this, detail.seriesId);
        state.hide();
        if (detail.title != null && !detail.title.isEmpty()) setTitle(detail.title);
        heroCover.load(detail.cover);
        if (!detail.badge.isEmpty()) {
            heroBadge.setText(detail.badge);
            heroBadge.setVisibility(View.VISIBLE);
        }
        heroEpisodes.setText(detail.episodeCount() + "集全");
        if (!detail.score.isEmpty()) {
            heroScore.setText(detail.score + "分");
            heroScore.setVisibility(View.VISIBLE);
        } else {
            heroScore.setVisibility(View.GONE);
        }
        if (detail.playCount > 0) {
            heroPlayCount.setText(Theme.formatCount(detail.playCount) + "播放");
        }
        titleView.setText(detail.title);
        StringBuilder meta = new StringBuilder();
        meta.append(detail.episodeCount()).append("集全");
        if (detail.playCount > 0) {
            meta.append("  ·  ").append(Theme.formatCount(detail.playCount)).append("播放");
        }
        if (detail.diggCount > 0) {
            meta.append("  ·  ").append(Theme.formatCount(detail.diggCount)).append("点赞");
        }
        if (!detail.score.isEmpty()) meta.append("  ·  ").append(detail.score).append("分");
        metaView.setText(meta.toString());

        tagRow.removeAllViews();
        List<String> tags = new ArrayList<>(detail.tags);
        if (tags.isEmpty()) tags.add("短剧");
        for (String tag : tags) {
            TextView chip = Ui.medium(this, tag, 10.5f, Theme.BRAND);
            chip.setBackgroundResource(R.drawable.bg_chip_selected);
            chip.setPadding(Theme.dp(this, 8), Theme.dp(this, 3),
                    Theme.dp(this, 8), Theme.dp(this, 3));
            tagRow.addView(chip);
        }

        introView.setText(detail.intro);
        introToggle.setVisibility(detail.intro != null && detail.intro.length() > 80
                ? View.VISIBLE : View.GONE);
        episodeTitle.setText("共 " + detail.episodeCount() + " 集");
        buildEpisodeGrid(detail);
        updateBottomBar();
    }

    private void buildEpisodeGrid(DetailInfo detail) {
        episodeGrid.removeAllViews();
        int count = detail.episodes.size();
        int start = 0;
        int end = count;
        int pageSize = 100;
        int pageCount = Math.max(1, (count + pageSize - 1) / pageSize);
        if (episodesExpanded && count > pageSize) {
            if (!episodeRangeExplicit) {
                episodeRangePage = Math.min(Math.max(lastIndex, 0) / pageSize, pageCount - 1);
            }
            start = episodeRangePage * pageSize;
            end = Math.min(count, start + pageSize);
            episodeRangeScroll.setVisibility(View.VISIBLE);
            episodeRangeRow.removeAllViews();
            for (int p = 0; p < pageCount; p++) {
                final int page = p;
                int from = p * pageSize + 1;
                int to = Math.min(count, (p + 1) * pageSize);
                TextView chip = Ui.chip(this, from + "-" + to, page == episodeRangePage);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                chipParams.rightMargin = Theme.dp(this, 7);
                episodeRangeRow.addView(chip, chipParams);
                chip.setOnClickListener(v -> {
                    episodeRangePage = page;
                    episodeRangeExplicit = true;
                    buildEpisodeGrid(detail);
                });
            }
        } else {
            episodeRangeScroll.setVisibility(View.GONE);
            if (!episodesExpanded && count > 30) {
                start = (Math.max(lastIndex, 0) / 30) * 30;
                if (start > count - 30) start = Math.max(0, count - 30);
                end = Math.min(count, start + 30);
            }
        }
        episodeToggle.setVisibility(count > 30 ? View.VISIBLE : View.GONE);
        episodeToggle.setText(episodesExpanded ? "收起" : "展开");
        for (int i = start; i < end; i++) {
            final int index = i;
            Episode episode = detail.episodes.get(i);
            boolean seen = watched.contains(i);
            String label = String.valueOf(episode.index) + (seen ? " ✓" : "");
            TextView button = Ui.medium(this, label, 12,
                    i == lastIndex ? Theme.BRAND : seen ? Theme.TEXT_TERTIARY : Theme.TEXT);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundResource(i == lastIndex
                    ? R.drawable.bg_chip_selected
                    : seen ? R.drawable.bg_chip_watched : R.drawable.bg_chip_normal);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = Theme.dp(this, 40);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(Theme.dp(this, 3), Theme.dp(this, 4),
                    Theme.dp(this, 3), Theme.dp(this, 4));
            episodeGrid.addView(button, params);
            button.setOnClickListener(v -> playEpisode(index));
        }
    }

    private void bindRelated(ListPage page) {
        relatedRow.removeAllViews();
        int added = 0;
        for (Drama drama : page.items) {
            if (seriesId.equals(drama.seriesId)) continue;
            DramaCard card = new DramaCard(this, true);
            card.bind(drama);
            card.setListener(this::openRelated);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Theme.dp(this, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Theme.dp(this, 10);
            relatedRow.addView(card, params);
            added++;
            if (added >= 10) break;
        }
        if (added == 0) {
            relatedRow.addView(Ui.text(this, "暂无相关推荐", 12, Theme.TEXT_TERTIARY));
        }
    }

    private void openRelated(Drama drama) {
        open(this, drama);
    }

    private void updateBottomBar() {
        if (info == null || info.episodes.isEmpty()) {
            playButton.setText("暂无可播放剧集");
            playButton.setEnabled(false);
            return;
        }
        int resumeIndex = Math.min(Math.max(lastIndex, 0), info.episodes.size() - 1);
        boolean hasHistory = AppPrefs.lastEpisode(this, seriesId).has("vid");
        playButton.setText(hasHistory ? "继续观看 第" + (resumeIndex + 1) + "集" : "立即播放");
        playButton.setOnClickListener(v -> playEpisode(resumeIndex));
    }

    private void updateFavoriteUi() {
        favoriteButton.setText(favorite ? "已收藏" : "收藏");
        favoriteButton.setTextColor(favorite ? Theme.BRAND : Theme.TEXT_SECONDARY);
        favoriteButton.setBackgroundResource(favorite
                ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
        favoriteIconText.setText(favorite ? "♥" : "♡");
        favoriteIconText.setTextColor(favorite ? Theme.BRAND : Theme.TEXT_SECONDARY);
    }

    private void toggleFavorite() {
        if (info == null) return;
        JSONObject item = new JSONObject();
        try {
            item.put("series_id", seriesId);
            item.put("title", info.title);
            item.put("cover", info.cover);
            item.put("intro", info.intro);
            item.put("episode_count", info.episodeCount());
        } catch (Exception ignored) {
        }
        favorite = LibraryStore.toggleFavorite(this, item);
        updateFavoriteUi();
        Ui.toast(this, favorite ? "已收藏" : "已取消收藏");
    }

    private void playEpisode(int index) {
        if (info == null || info.episodes.isEmpty()) {
            Ui.toast(this, "暂无可播放剧集");
            return;
        }
        if (index < 0 || index >= info.episodes.size()) index = 0;
        Episode episode = info.episodes.get(index);
        JSONArray vids = new JSONArray();
        for (Episode value : info.episodes) vids.put(value.vid);
        PlayerActivity.open(this, seriesId, info.title, info.cover, episode.vid,
                index, vids.toString());
        AppPrefs.saveLastEpisode(this, seriesId, episode.vid, index);
        lastIndex = index;
    }

    @Override
    protected void onResume() {
        super.onResume();
        JSONObject last = AppPrefs.lastEpisode(this, seriesId);
        lastIndex = last.optInt("index", 0);
        if (info != null) {
            if (lastIndex > 0) LibraryStore.seedWatched(this, seriesId, lastIndex);
            watched = LibraryStore.watched(this, seriesId);
            episodeRangeExplicit = false;
            buildEpisodeGrid(info);
            updateBottomBar();
        }
    }
}
