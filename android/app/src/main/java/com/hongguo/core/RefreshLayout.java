package com.hongguo.core;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Small pull-to-refresh container (works with ScrollView and ListView). */
public final class RefreshLayout extends FrameLayout {
    public interface Listener {
        void onRefresh(RefreshLayout layout);
    }

    private final LinearLayout header;
    private final ProgressBar spinner;
    private final TextView label;
    private final int headerHeight;
    private final int touchSlop;
    private final float maxOffset;
    private View child;
    private Listener listener;
    private boolean dragging;
    private boolean refreshing;
    private float downY;

    public RefreshLayout(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        headerHeight = Theme.dp(context, 58);
        maxOffset = Theme.dp(context, 96);
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER);
        header.setBackgroundColor(Theme.SURFACE);
        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 20), Theme.dp(context, 20));
        spinnerParams.rightMargin = Theme.dp(context, 8);
        header.addView(spinner, spinnerParams);
        label = Ui.medium(context, "下拉刷新", 12, Theme.TEXT_SECONDARY);
        header.addView(label);
        header.setTranslationY(-headerHeight);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void addView(View view, int index, ViewGroup.LayoutParams params) {
        if (view == header) {
            super.addView(view, index, params);
            return;
        }
        child = view;
        super.addView(view, 0, params);
        bringChildToFront(header);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (refreshing) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getY();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float delta = event.getY() - downY;
                if (delta > touchSlop && !canChildScrollUp()) {
                    dragging = true;
                    downY = event.getY() - touchSlop;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float offset = Math.min((event.getY() - downY) * 0.48f, maxOffset);
                if (offset < 0) offset = 0;
                applyOffset(offset);
                label.setText(offset >= headerHeight * 0.9f ? "松开刷新" : "下拉刷新");
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float offset = child == null ? 0 : child.getTranslationY();
                if (!refreshing && offset >= headerHeight * 0.9f) {
                    startRefresh();
                } else if (!refreshing) {
                    reset();
                }
                dragging = false;
                return true;
            }
            default:
                return true;
        }
    }

    private void applyOffset(float offset) {
        if (child == null) return;
        child.setTranslationY(offset);
        header.setTranslationY(-headerHeight + offset);
    }

    private boolean canChildScrollUp() {
        if (child == null) return false;
        if (child instanceof AbsListView) {
            AbsListView list = (AbsListView) child;
            return list.getChildCount() > 0
                    && (list.getFirstVisiblePosition() > 0
                    || list.getChildAt(0).getTop() < list.getPaddingTop());
        }
        return child.canScrollVertically(-1);
    }

    private void startRefresh() {
        if (listener == null) return;
        refreshing = true;
        spinner.setVisibility(VISIBLE);
        label.setText("正在刷新…");
        if (child != null) {
            child.animate().translationY(headerHeight).setDuration(160).start();
        }
        header.animate().translationY(0).setDuration(160).start();
        listener.onRefresh(this);
    }

    public void finishRefresh() {
        refreshing = false;
        spinner.setVisibility(VISIBLE);
        reset();
    }

    private void reset() {
        if (child != null) child.animate().translationY(0).setDuration(180).start();
        header.animate().translationY(-headerHeight).setDuration(180).start();
        label.setText("下拉刷新");
    }

    public boolean isRefreshing() {
        return refreshing;
    }
}
