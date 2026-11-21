package com.hongguo.core;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Five-tab bottom bar (推荐 / 剧场 / 排行榜 / 新剧 / 我的). */
public final class BottomNav extends LinearLayout {
    public interface Listener {
        void onTabSelected(int index);
    }

    private static final int[] ICONS = {
            R.drawable.ic_home, R.drawable.ic_browse, R.drawable.ic_rank,
            R.drawable.ic_new, R.drawable.ic_mine
    };
    private static final String[] LABELS = {"推荐", "剧场", "排行榜", "新剧", "我的"};

    private final ImageView[] icons = new ImageView[5];
    private final TextView[] labels = new TextView[5];
    private int selected;
    private boolean dark;
    private Listener listener;

    public BottomNav(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundResource(R.drawable.bg_nav_bottom);
        setElevation(Theme.dp(context, 10));
        setPadding(0, Theme.dp(context, 5), 0, Theme.dp(context, 4));
        build();
    }

    private void build() {
        for (int i = 0; i < 5; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(false);
            item.setSoundEffectsEnabled(false);
            item.setContentDescription(LABELS[i]);
            ImageView icon = new ImageView(getContext());
            icon.setImageResource(ICONS[i]);
            icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    Theme.dp(getContext(), 23), Theme.dp(getContext(), 23));
            item.addView(icon, iconParams);
            TextView label = Ui.medium(getContext(), LABELS[i], 10.5f, Theme.TEXT_TERTIARY);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Theme.dp(getContext(), 3);
            item.addView(label, labelParams);
            item.setOnClickListener(v -> select(index, true));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            addView(item, params);
            icons[i] = icon;
            labels[i] = label;
        }
        select(0, false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void select(int index, boolean notify) {
        if (index < 0 || index >= 5) return;
        selected = index;
        int inactive = dark ? 0xFF9A9A9A : Theme.TEXT_TERTIARY;
        for (int i = 0; i < 5; i++) {
            boolean active = i == index;
            icons[i].setColorFilter(active ? Theme.BRAND : inactive);
            labels[i].setTextColor(active ? Theme.BRAND : inactive);
            labels[i].setTypeface(active ? Theme.medium() : Theme.regular());
            icons[i].animate().scaleX(active ? 1.06f : 1f).scaleY(active ? 1.06f : 1f)
                    .setDuration(160).start();
        }
        if (notify && listener != null) listener.onTabSelected(index);
    }

    public void setDark(boolean dark) {
        if (this.dark == dark) return;
        this.dark = dark;
        setBackgroundColor(dark ? 0xFA000000 : 0xFFFFFFFF);
        if (!dark) setBackgroundResource(R.drawable.bg_nav_bottom);
        select(selected, false);
    }

    public int selected() {
        return selected;
    }
}
