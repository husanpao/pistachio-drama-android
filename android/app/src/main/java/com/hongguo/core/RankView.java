package com.hongguo.core;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 排行榜 tab: real leaderboard rows with rank category chips. */
public final class RankView extends FrameLayout {
    private final Context context;
    private final PagedRankView paged;
    private final LinearLayout chips;
    private String selected = "";
    private boolean chipsBuilt;

    public RankView(Context context) {
        super(context);
        this.context = context;
        HongguoApi api = new HongguoApi(context);
        paged = new PagedRankView(context);
        paged.setFetcher((offset, sessionId) -> api.rank(offset, selected, sessionId));
        addView(paged, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = paged.header();
        header.setBackgroundColor(Theme.SURFACE);
        LinearLayout title = Ui.row(context);
        title.setPadding(Theme.dp(context, 18), Theme.dp(context, 16),
                Theme.dp(context, 14), Theme.dp(context, 2));
        title.addView(Ui.bold(context, "排行榜", 24, Theme.TEXT));
        View spacer = new View(context);
        title.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView updated = Ui.text(context, "实时热度", 11, Theme.TEXT_TERTIARY);
        updated.setBackground(Theme.round(Theme.SURFACE_ALT, Theme.dp(context, 12)));
        updated.setPadding(Theme.dp(context, 10), Theme.dp(context, 5),
                Theme.dp(context, 10), Theme.dp(context, 5));
        title.addView(updated);
        header.addView(title);

        TextView subtitle = Ui.text(context, "短剧热度榜 · 数据来自开心果实时榜单", 11, Theme.TEXT_TERTIARY);
        subtitle.setPadding(Theme.dp(context, 18), Theme.dp(context, 4),
                Theme.dp(context, 14), Theme.dp(context, 12));
        header.addView(subtitle);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 16), 0);
        chips = Ui.row(context);
        chips.setPadding(0, 0, 0, Theme.dp(context, 12));
        scroll.addView(chips);
        header.addView(scroll);

        buildChips(defaultOptions());
        paged.setPageListener((page, reset) -> {
            if (reset && !page.rankOptions.isEmpty()) {
                List<FilterOption> options = new ArrayList<>();
                for (FilterOption option : page.rankOptions) {
                    if (option.label.contains("演员")) continue;
                    options.add(option);
                }
                if (!options.isEmpty()) {
                    buildChips(options);
                    chipsBuilt = true;
                }
            }
        });
        paged.start();
    }

    private List<FilterOption> defaultOptions() {
        List<FilterOption> options = new ArrayList<>();
        options.add(new FilterOption("", "全部"));
        options.add(new FilterOption("human", "真人剧"));
        options.add(new FilterOption("comic_series_rank", "漫剧"));
        options.add(new FilterOption("ai_playlet", "AI剧"));
        return options;
    }

    private void buildChips(List<FilterOption> options) {
        if (chipsBuilt) return;
        chips.removeAllViews();
        List<FilterOption> ordered = new ArrayList<>();
        String[] priority = {"全部", "真人剧", "漫剧", "AI剧", "系列剧"};
        for (String label : priority) {
            for (FilterOption option : options) {
                if (label.equals(option.label)) {
                    ordered.add(option);
                    break;
                }
            }
        }
        for (FilterOption option : options) {
            if (!ordered.contains(option)) ordered.add(option);
        }
        for (int i = 0; i < ordered.size(); i++) {
            FilterOption option = ordered.get(i);
            TextView chip = Ui.chip(context, option.label, option.id.equals(selected));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Theme.dp(context, 8);
            chips.addView(chip, params);
            chip.setOnClickListener(v -> {
                selected = option.id;
                for (int j = 0; j < chips.getChildCount(); j++) {
                    Ui.selectChip((TextView) chips.getChildAt(j), j == optionIndex(option));
                }
                paged.reloadFresh();
            });
        }
    }

    private int optionIndex(FilterOption option) {
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            if (option.label.contentEquals(chip.getText())) return i;
        }
        return -1;
    }
}
