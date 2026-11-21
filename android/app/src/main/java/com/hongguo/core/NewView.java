package com.hongguo.core;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 新剧 tab: newest online drama grid. */
public final class NewView extends FrameLayout {
    public NewView(Context context) {
        super(context);
        HongguoApi api = new HongguoApi(context);
        PagedDramaView paged = new PagedDramaView(context, 2, false);
        paged.setFetcher(api::newSeries);
        paged.setEmpty("新剧还在路上", "稍后再来看看");
        addView(paged, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = paged.header();
        LinearLayout title = Ui.row(context);
        title.setPadding(Theme.dp(context, 18), Theme.dp(context, 16),
                Theme.dp(context, 14), Theme.dp(context, 2));
        View left = Ui.column(context);
        ((LinearLayout) left).addView(Ui.bold(context, "新剧", 24, Theme.TEXT));
        LinearLayout sub = Ui.row(context);
        View dot = new View(context);
        dot.setBackground(Theme.oval(Theme.BRAND));
        sub.addView(dot, new LinearLayout.LayoutParams(
                Theme.dp(context, 6), Theme.dp(context, 6)));
        TextView label = Ui.text(context, "每日上新 · 追新不迷路", 11, Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = Theme.dp(context, 5);
        sub.addView(label, labelParams);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Theme.dp(context, 3);
        ((LinearLayout) left).addView(sub, subParams);
        title.addView(left);
        View spacer = new View(context);
        title.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout search = Ui.row(context);
        search.setPadding(Theme.dp(context, 9), Theme.dp(context, 9),
                Theme.dp(context, 9), Theme.dp(context, 9));
        search.setBackground(Theme.oval(Theme.SURFACE));
        search.addView(Ui.icon(context, R.drawable.ic_search, 21, Theme.TEXT));
        search.setOnClickListener(v -> SearchActivity.open(context));
        title.addView(search);
        header.addView(title);

        LinearLayout section = Ui.sectionHeader(context, "最近上线", "按首播时间", null, null);
        section.setPadding(Theme.dp(context, 4), Theme.dp(context, 18),
                Theme.dp(context, 4), Theme.dp(context, 10));
        header.addView(section);

        header.setBackgroundColor(Theme.BG);
        paged.start();
    }
}
