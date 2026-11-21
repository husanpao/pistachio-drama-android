package com.hongguo.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Lightweight shimmer skeleton shown before the first list page arrives. */
public final class SkeletonView extends FrameLayout {
    private final ValueAnimator pulse;
    private final int columns;
    private final boolean rankMode;

    public SkeletonView(Context context, int columns, boolean rankMode) {
        super(context);
        this.columns = Math.max(columns, 1);
        this.rankMode = rankMode;
        setBackgroundColor(Theme.BG);
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.column(context);
        content.setPadding(Theme.dp(context, 12), Theme.dp(context, 8),
                Theme.dp(context, 12), Theme.dp(context, 18));
        for (int row = 0; row < 4; row++) {
            content.addView(rankMode ? rankRow(context) : cardRow(context));
        }
        scroll.addView(content);
        addView(scroll, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pulse = ValueAnimator.ofFloat(0.35f, 0.9f);
        pulse.setDuration(900);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            for (int i = 0; i < content.getChildCount(); i++) {
                content.getChildAt(i).setAlpha(alpha);
            }
        });
    }

    public void show() {
        setVisibility(VISIBLE);
        if (pulse != null) {
            pulse.cancel();
            pulse.start();
        }
    }

    public void hide() {
        setVisibility(GONE);
        if (pulse != null && pulse.isRunning()) pulse.cancel();
    }

    private View cardRow(Context context) {
        LinearLayout row = Ui.row(context);
        row.setPadding(0, 0, 0, Theme.dp(context, 14));
        int coverHeight = columns <= 2 ? 200 : 132;
        for (int i = 0; i < columns; i++) {
            if (i > 0) {
                View gap = new View(context);
                row.addView(gap, new LinearLayout.LayoutParams(Theme.dp(context, 10), 1));
            }
            LinearLayout card = Ui.column(context);
            View cover = block(context, coverHeight);
            card.addView(cover, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, coverHeight)));
            View title = block(context, 13);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 13));
            titleParams.topMargin = Theme.dp(context, 9);
            card.addView(title, titleParams);
            View meta = block(context, 10);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    Theme.dp(context, 70), Theme.dp(context, 10));
            metaParams.topMargin = Theme.dp(context, 6);
            card.addView(meta, metaParams);
            row.addView(card, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View rankRow(Context context) {
        LinearLayout row = Ui.row(context);
        row.setBackground(Theme.round(Theme.SURFACE, Theme.dp(context, 12)));
        row.setPadding(Theme.dp(context, 12), Theme.dp(context, 10),
                Theme.dp(context, 12), Theme.dp(context, 10));
        View rank = block(context, 24);
        row.addView(rank, new LinearLayout.LayoutParams(
                Theme.dp(context, 24), Theme.dp(context, 24)));
        View cover = block(context, 72);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 72), Theme.dp(context, 96));
        coverParams.leftMargin = Theme.dp(context, 12);
        row.addView(cover, coverParams);
        LinearLayout info = Ui.column(context);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = Theme.dp(context, 12);
        row.addView(info, infoParams);
        info.addView(block(context, 15), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 15)));
        View meta = block(context, 10);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 130), Theme.dp(context, 10));
        metaParams.topMargin = Theme.dp(context, 10);
        info.addView(meta, metaParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = Theme.dp(context, 10);
        row.setLayoutParams(rowParams);
        return row;
    }

    private View block(Context context, int heightDp) {
        View view = new View(context);
        view.setBackground(Theme.round(0xFFE6E8EC, Theme.dp(context, 8)));
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, heightDp)));
        return view;
    }
}
