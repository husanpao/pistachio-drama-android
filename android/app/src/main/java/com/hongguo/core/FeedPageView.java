package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/** One full-screen page in the short-video style recommendation feed. */
public final class FeedPageView extends FrameLayout {
    public interface Actions {
        void onWatchAll(Drama drama);

        void onToggleFavorite(Drama drama);

        void onEpisodes(Drama drama);

        void onNotInterested(Drama drama);

        void onSearch();

        void onTogglePlay();
    }

    private final Actions actions;
    private final FrameLayout videoHost;
    private final PlayerView playerView;
    private final CoverImageView cover;
    private final ProgressBar spinner;
    private final TextView statusText;
    private final ImageView playState;
    private final ImageView likeBurst;
    private final TextView title;
    private final TextView meta;
    private final TextView intro;
    private final TextView tag;
    private final TextView watchAll;
    private final ImageView favoriteIcon;
    private final TextView favoriteLabel;
    private final View progressFill;
    private final View progressTrack;
    private Drama drama;

    public FeedPageView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        final GestureDetector gestures = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        actions.onTogglePlay();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        actions.onToggleFavorite(drama);
                        showLikeBurst();
                        return true;
                    }
                });
        setOnTouchListener((v, event) -> {
            gestures.onTouchEvent(event);
            return false;
        });

        videoHost = new FrameLayout(context);
        addView(videoHost, match());
        playerView = (PlayerView) inflate(context, R.layout.feed_player, null);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        videoHost.addView(playerView, match());

        cover = new CoverImageView(context);
        cover.setCornerRadius(0);
        addView(cover, match());

        addView(scrim(R.drawable.bg_scrim_top), new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 138), Gravity.TOP));
        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.BOTTOM);
        bottom.setBackgroundResource(R.drawable.bg_scrim_bottom);
        LayoutParams bottomParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 330), Gravity.BOTTOM);
        addView(bottom, bottomParams);

        LinearLayout topBar = Ui.row(context);
        topBar.setBackgroundColor(0x66000000);
        topBar.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                Theme.dp(context, 10), 0);
        TextView brand = Ui.black(context, "开心果", 18, Color.WHITE);
        topBar.addView(brand);
        TextView suffix = Ui.medium(context, "短剧", 9, 0xCCFFFFFF);
        LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suffixParams.leftMargin = Theme.dp(context, 3);
        suffixParams.topMargin = Theme.dp(context, 5);
        topBar.addView(suffix, suffixParams);
        View spacer = new View(context);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        ImageView search = Ui.icon(context, R.drawable.ic_search, 22, Color.WHITE);
        search.setLayoutParams(new LinearLayout.LayoutParams(
                Theme.dp(context, 48), Theme.dp(context, 48)));
        search.setBackground(Theme.round(0x55000000, Theme.dp(context, 24)));
        search.setPadding(Theme.dp(context, 13), Theme.dp(context, 13),
                Theme.dp(context, 13), Theme.dp(context, 13));
        search.setContentDescription("搜索");
        search.setOnClickListener(v -> actions.onSearch());
        topBar.addView(search);
        addView(topBar, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 58), Gravity.TOP));

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.getIndeterminateDrawable().setColorFilter(
                Theme.BRAND, android.graphics.PorterDuff.Mode.SRC_IN);
        LayoutParams spinnerParams = new LayoutParams(
                Theme.dp(context, 34), Theme.dp(context, 34), Gravity.CENTER);
        addView(spinner, spinnerParams);

        statusText = Ui.medium(context, "正在准备…", 12, 0xCCFFFFFF);
        statusText.setGravity(Gravity.CENTER);
        statusText.setVisibility(View.GONE);
        LayoutParams statusParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        statusParams.topMargin = Theme.dp(context, 56);
        addView(statusText, statusParams);

        playState = Ui.icon(context, R.drawable.ic_pause, 34, Color.WHITE);
        playState.setBackground(Theme.round(0x77000000, Theme.dp(context, 30)));
        playState.setPadding(Theme.dp(context, 16), Theme.dp(context, 16),
                Theme.dp(context, 16), Theme.dp(context, 16));
        playState.setVisibility(View.GONE);
        addView(playState, new LayoutParams(
                Theme.dp(context, 66), Theme.dp(context, 66), Gravity.CENTER));

        likeBurst = Ui.icon(context, R.drawable.ic_heart, 74, Theme.BRAND);
        likeBurst.setBackground(Theme.round(0xCC000000, Theme.dp(context, 37)));
        likeBurst.setPadding(Theme.dp(context, 17), Theme.dp(context, 17),
                Theme.dp(context, 17), Theme.dp(context, 17));
        likeBurst.setVisibility(View.GONE);
        addView(likeBurst, new LayoutParams(
                Theme.dp(context, 108), Theme.dp(context, 108), Gravity.CENTER));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams rightParams = new LayoutParams(
                Theme.dp(context, 58), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        rightParams.rightMargin = Theme.dp(context, 9);
        rightParams.bottomMargin = Theme.dp(context, 150);
        addView(right, rightParams);

        favoriteIcon = Ui.icon(context, R.drawable.ic_heart, 27, Color.WHITE);
        favoriteIcon.setContentDescription("追剧");
        favoriteIcon.setPadding(Theme.dp(context, 10), Theme.dp(context, 10),
                Theme.dp(context, 10), Theme.dp(context, 10));
        favoriteIcon.setOnClickListener(v -> actions.onToggleFavorite(drama));
        right.addView(favoriteIcon, new LinearLayout.LayoutParams(
                Theme.dp(context, 48), Theme.dp(context, 48)));
        favoriteLabel = Ui.medium(context, "追剧", 10, Color.WHITE);
        favoriteLabel.setGravity(Gravity.CENTER);
        favoriteLabel.setOnClickListener(v -> actions.onToggleFavorite(drama));
        right.addView(favoriteLabel);

        ImageView episode = Ui.icon(context, R.drawable.ic_list, 25, Color.WHITE);
        episode.setContentDescription("选集");
        episode.setPadding(Theme.dp(context, 10), Theme.dp(context, 10),
                Theme.dp(context, 10), Theme.dp(context, 10));
        episode.setOnClickListener(v -> actions.onEpisodes(drama));
        LinearLayout.LayoutParams episodeParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 48), Theme.dp(context, 48));
        episodeParams.topMargin = Theme.dp(context, 13);
        right.addView(episode, episodeParams);
        TextView episodeLabel = Ui.medium(context, "选集", 10, Color.WHITE);
        episodeLabel.setGravity(Gravity.CENTER);
        episodeLabel.setOnClickListener(v -> actions.onEpisodes(drama));
        right.addView(episodeLabel);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LayoutParams infoParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        infoParams.leftMargin = Theme.dp(context, 16);
        infoParams.rightMargin = Theme.dp(context, 76);
        infoParams.bottomMargin = Theme.dp(context, 18);
        addView(info, infoParams);

        title = Ui.bold(context, "", 21, Color.WHITE);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);

        meta = Ui.text(context, "", 11, 0xD9FFFFFF);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Theme.dp(context, 5);
        info.addView(meta, metaParams);

        tag = Ui.medium(context, "", 9, 0xFFFFD7DD);
        tag.setBackground(Theme.round(0x33FF2C46, Theme.dp(context, 5)));
        tag.setPadding(Theme.dp(context, 6), Theme.dp(context, 3),
                Theme.dp(context, 6), Theme.dp(context, 3));
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagParams.topMargin = Theme.dp(context, 7);
        info.addView(tag, tagParams);

        intro = Ui.text(context, "", 12, 0xCCFFFFFF);
        intro.setMaxLines(2);
        intro.setEllipsize(android.text.TextUtils.TruncateAt.END);
        intro.setLineSpacing(Theme.dp(context, 2), 1f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = Theme.dp(context, 7);
        info.addView(intro, introParams);

        watchAll = Ui.medium(context, "看全集", 12, Color.WHITE);
        watchAll.setContentDescription("看全集");
        watchAll.setGravity(Gravity.CENTER);
        watchAll.setMinHeight(Theme.dp(context, 44));
        watchAll.setBackground(Theme.round(Theme.BRAND, Theme.dp(context, 18)));
        watchAll.setPadding(Theme.dp(context, 18), Theme.dp(context, 8),
                Theme.dp(context, 18), Theme.dp(context, 8));
        watchAll.setOnClickListener(v -> actions.onWatchAll(drama));
        LinearLayout ctaRow = Ui.row(context);
        ctaRow.setGravity(Gravity.CENTER_VERTICAL);
        ctaRow.addView(watchAll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Theme.dp(context, 36)));
        View ctaSpacer = new View(context);
        ctaRow.addView(ctaSpacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView notInterested = Ui.text(context, "不感兴趣", 10, 0xCCFFFFFF);
        notInterested.setContentDescription("不感兴趣，减少此类推荐");
        notInterested.setMinHeight(Theme.dp(context, 40));
        notInterested.setGravity(Gravity.CENTER);
        notInterested.setPadding(Theme.dp(context, 8), Theme.dp(context, 8),
                Theme.dp(context, 4), Theme.dp(context, 8));
        notInterested.setOnClickListener(v -> actions.onNotInterested(drama));
        ctaRow.addView(notInterested);
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctaParams.topMargin = Theme.dp(context, 11);
        info.addView(ctaRow, ctaParams);

        progressTrack = new View(context);
        progressTrack.setBackgroundColor(0x33FFFFFF);
        LayoutParams trackParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 2), Gravity.BOTTOM);
        trackParams.bottomMargin = Theme.dp(context, 5);
        addView(progressTrack, trackParams);

        progressFill = new View(context);
        progressFill.setBackgroundColor(Theme.BRAND);
        LayoutParams fillParams = new LayoutParams(0, Theme.dp(context, 2), Gravity.BOTTOM);
        fillParams.bottomMargin = Theme.dp(context, 5);
        addView(progressFill, fillParams);
    }

    private View scrim(int drawable) {
        View view = new View(getContext());
        view.setBackgroundResource(drawable);
        return view;
    }

    private static LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public FrameLayout videoHost() {
        return videoHost;
    }

    public PlayerView playerView() {
        return playerView;
    }

    /** Horizontal sources stay whole in portrait; vertical sources fill the screen. */
    public void applyVideoSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        playerView.setResizeMode(width > height
                ? AspectRatioFrameLayout.RESIZE_MODE_FIT
                : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
    }

    public Drama drama() {
        return drama;
    }

    public void bind(Drama item) {
        drama = item;
        title.setText(item.title);
        String count = item.episodeCount > 0 ? " · 共" + item.episodeCount + "集" : "";
        meta.setText("第1集" + count + (item.score.isEmpty() ? "" : " · 评分 " + item.score));
        intro.setText(item.intro == null || item.intro.isEmpty()
                ? "上滑看下一部，点击暂停或继续播放" : item.intro);
        tag.setText(!item.feedKind.isEmpty() ? item.feedKind
                : item.primaryTag().isEmpty() ? "短剧" : item.primaryTag());
        tag.setVisibility(item.feedKind.isEmpty() && item.primaryTag().isEmpty()
                ? View.GONE : View.VISIBLE);
        watchAll.setText(item.episodeCount > 0 ? "看全集 · 共" + item.episodeCount + "集" : "看全集");
        watchAll.setContentDescription(item.episodeCount > 0
                ? "看全集，共" + item.episodeCount + "集" : "看全集");
        setContentDescription(item.title + "，第1集" + count);
        cover.load(item.coverUrl());
        updateFavorite();
        onLoading();
    }

    public void updateFavorite() {
        boolean active = drama != null && LibraryStore.isFavorite(getContext(), drama.seriesId);
        favoriteIcon.setColorFilter(active ? Theme.BRAND : Color.WHITE);
        favoriteLabel.setText(active ? "已追剧" : "追剧");
        favoriteIcon.setContentDescription(active ? "已追剧，点击取消" : "追剧，点击加入");
    }

    public void onLoading() {
        spinner.setVisibility(View.VISIBLE);
        statusText.setText("正在准备…");
        statusText.setVisibility(View.VISIBLE);
        cover.setVisibility(View.VISIBLE);
        playState.setVisibility(View.GONE);
    }

    public void onReady() {
        spinner.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        cover.setVisibility(View.GONE);
        playState.setVisibility(View.GONE);
    }

    public void onFailed() {
        spinner.setVisibility(View.GONE);
        statusText.setText("网络波动，正在重试…");
        statusText.setVisibility(View.VISIBLE);
        cover.setVisibility(View.VISIBLE);
    }

    public void showPlayState(boolean playing) {
        playState.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        playState.setVisibility(View.VISIBLE);
        playState.animate().alpha(1f).setDuration(120).withEndAction(() ->
                playState.postDelayed(() -> playState.animate().alpha(0f)
                        .setDuration(220).withEndAction(() ->
                                playState.setVisibility(View.GONE)).start(), 550)).start();
    }

    private void showLikeBurst() {
        likeBurst.setVisibility(View.VISIBLE);
        likeBurst.setAlpha(1f);
        likeBurst.setScaleX(0.55f);
        likeBurst.setScaleY(0.55f);
        likeBurst.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160)
                .withEndAction(() -> likeBurst.animate().alpha(0f).setDuration(260)
                        .withEndAction(() -> likeBurst.setVisibility(View.GONE)).start())
                .start();
    }

    public void setProgress(float fraction) {
        float value = Math.max(0f, Math.min(1f, fraction));
        int width = getWidth();
        ViewGroup.LayoutParams params = progressFill.getLayoutParams();
        params.width = (int) (width * value);
        progressFill.setLayoutParams(params);
    }

    public void setPaused(boolean paused) {
        playState.setImageResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
    }
}
