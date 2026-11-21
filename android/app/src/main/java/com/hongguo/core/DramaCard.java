package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cover card used by the recommendation / browse / new grids. */
public final class DramaCard extends LinearLayout {
    public interface Listener {
        void onDramaClick(Drama drama);
    }

    private final CoverImageView cover;
    private final TextView title;
    private final TextView meta;
    private final TextView badge;
    private final TextView episode;
    private final TextView score;
    private final boolean compact;
    private Drama drama;
    private Listener listener;

    public DramaCard(Context context) {
        this(context, false);
    }

    public DramaCard(Context context, boolean compact) {
        super(context);
        this.compact = compact;
        setOrientation(VERTICAL);
        setClickable(true);
        setFocusable(true);

        FrameLayout coverWrap = new FrameLayout(context);
        cover = new CoverImageView(context);
        cover.setCornerRadius(compact ? 9 : 12);
        cover.setHeightRatio(4f / 3f);
        coverWrap.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        badge = Ui.badge(context, "", R.drawable.bg_badge_new, Color.WHITE);
        badge.setVisibility(GONE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = Gravity.TOP | Gravity.START;
        badgeParams.setMargins(Theme.dp(context, 7), Theme.dp(context, 7), 0, 0);
        coverWrap.addView(badge, badgeParams);

        episode = Ui.badge(context, "", R.drawable.bg_badge_ep, Color.WHITE);
        episode.setVisibility(GONE);
        FrameLayout.LayoutParams episodeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        episodeParams.gravity = Gravity.BOTTOM | Gravity.END;
        episodeParams.setMargins(0, 0, Theme.dp(context, 7), Theme.dp(context, 7));
        coverWrap.addView(episode, episodeParams);

        score = Ui.badge(context, "", R.drawable.bg_badge_score, Color.WHITE);
        score.setVisibility(GONE);
        FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.gravity = Gravity.BOTTOM | Gravity.START;
        scoreParams.setMargins(Theme.dp(context, 7), 0, 0, Theme.dp(context, 7));
        coverWrap.addView(score, scoreParams);

        addView(coverWrap, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        title = Ui.medium(context, "", compact ? 12.5f : 14, Theme.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(Theme.dp(context, 1), 1f);
        LayoutParams titleParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Theme.dp(context, compact ? 6 : 8);
        addView(title, titleParams);

        meta = Ui.text(context, "", compact ? 10 : 11, Theme.TEXT_TERTIARY);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams metaParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Theme.dp(context, 3);
        addView(meta, metaParams);

        setOnClickListener(v -> {
            if (listener != null && drama != null) listener.onDramaClick(drama);
        });
        Ui.pressable(this);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void bind(Drama value) {
        this.drama = value;
        title.setText(value.title);
        cover.load(value.coverUrl());
        if (value.badge != null && !value.badge.isEmpty()) {
            badge.setText(value.badge);
            badge.setVisibility(VISIBLE);
        } else {
            badge.setVisibility(GONE);
        }
        if (!value.episodeLabel().isEmpty()) {
            episode.setText(value.episodeLabel());
            episode.setVisibility(VISIBLE);
        } else {
            episode.setVisibility(GONE);
        }
        if (!value.score.isEmpty()) {
            score.setText(value.score + "分");
            score.setVisibility(VISIBLE);
        } else {
            score.setVisibility(GONE);
        }
        String tag = value.primaryTag();
        String heat = value.heatLabel();
        String metaText;
        if (!tag.isEmpty() && !heat.isEmpty()) metaText = tag + "  ·  " + heat;
        else metaText = tag.isEmpty() ? heat : tag;
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? GONE : VISIBLE);
    }
}
