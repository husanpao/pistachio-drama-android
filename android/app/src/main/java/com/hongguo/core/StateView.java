package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Centered loading / empty / error state for any page. */
public final class StateView extends LinearLayout {
    private final ProgressBar progress;
    private final FrameLayout artwork;
    private final TextView title;
    private final TextView hint;
    private final TextView action;

    public StateView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(Theme.dp(context, 40), Theme.dp(context, 48),
                Theme.dp(context, 40), Theme.dp(context, 48));
        setBackgroundColor(Theme.BG);

        artwork = new FrameLayout(context);
        artwork.setBackground(Theme.oval(Theme.BRAND_SOFT));
        ImageView icon = Ui.icon(context, R.drawable.ic_play, 34, Theme.BRAND);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                Theme.dp(context, 34), Theme.dp(context, 34), Gravity.CENTER);
        artwork.addView(icon, iconParams);
        addView(artwork, new LayoutParams(Theme.dp(context, 76), Theme.dp(context, 76)));

        progress = new ProgressBar(context);
        progress.setIndeterminate(true);
        addView(progress, new LayoutParams(Theme.dp(context, 28), Theme.dp(context, 28)));

        title = Ui.medium(context, "", 15, Theme.TEXT);
        title.setGravity(Gravity.CENTER);
        LayoutParams titleParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Theme.dp(context, 18);
        addView(title, titleParams);

        hint = Ui.text(context, "", 12, Theme.TEXT_TERTIARY);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(Theme.dp(context, 3), 1f);
        LayoutParams hintParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Theme.dp(context, 7);
        addView(hint, hintParams);

        action = Ui.medium(context, "重试", 13, Color.WHITE);
        action.setGravity(Gravity.CENTER);
        action.setBackground(Theme.round(Theme.BRAND, Theme.dp(context, 20)));
        action.setPadding(Theme.dp(context, 28), Theme.dp(context, 9),
                Theme.dp(context, 28), Theme.dp(context, 9));
        action.setClickable(true);
        action.setVisibility(GONE);
        LayoutParams actionParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Theme.dp(context, 18);
        addView(action, actionParams);
    }

    public void showLoading(String message) {
        setVisibility(VISIBLE);
        artwork.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
        title.setVisibility(GONE);
        hint.setVisibility(GONE);
        action.setVisibility(GONE);
    }

    public void showEmpty(String message, String detail) {
        setVisibility(VISIBLE);
        artwork.setVisibility(VISIBLE);
        progress.setVisibility(GONE);
        title.setText(message == null ? "这里还什么都没有" : message);
        title.setVisibility(VISIBLE);
        hint.setText(detail == null ? "" : detail);
        hint.setVisibility(detail == null || detail.isEmpty() ? GONE : VISIBLE);
        action.setVisibility(GONE);
    }

    public void showError(String message, String detail, Runnable retry) {
        setVisibility(VISIBLE);
        artwork.setVisibility(VISIBLE);
        progress.setVisibility(GONE);
        title.setText(message == null ? "网络开小差了" : message);
        title.setVisibility(VISIBLE);
        hint.setText(detail == null ? "" : detail);
        hint.setVisibility(detail == null || detail.isEmpty() ? GONE : VISIBLE);
        if (retry == null) {
            action.setVisibility(GONE);
        } else {
            action.setVisibility(VISIBLE);
            action.setOnClickListener(v -> retry.run());
        }
    }

    public void hide() {
        setVisibility(GONE);
    }
}
