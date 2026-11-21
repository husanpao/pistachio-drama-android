package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 剧场 tab: genre shortcuts, theme chips and the full filter sheet. */
public final class BrowseView extends FrameLayout {
    private final Context context;
    private final HongguoApi api;
    private final PagedDramaView paged;
    private final Map<String, String> filters = new HashMap<>(HongguoApi.defaultFilters());
    private final TextView[] genreChips = new TextView[3];
    private final LinearLayout quickRow;
    private final List<FilterGroup> groups = new ArrayList<>();
    private String genre = "short_play";
    private Map<String, String> themeOptions = new HashMap<>();

    public BrowseView(Context context) {
        super(context);
        this.context = context;
        api = new HongguoApi(context);
        paged = new PagedDramaView(context, 3, true);
        paged.setFetcher(offset -> api.category(offset, filters, offset == 0));
        paged.setEmpty("没有找到符合条件的好剧", "试试减少筛选条件");
        addView(paged, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = paged.header();
        header.addView(buildTitleRow());
        header.addView(buildGenreRow());

        LinearLayout quickLabelRow = Ui.row(context);
        quickLabelRow.setPadding(Theme.dp(context, 4), Theme.dp(context, 14),
                Theme.dp(context, 4), Theme.dp(context, 4));
        quickLabelRow.addView(Ui.medium(context, "热门题材", 13, Theme.TEXT));
        View spacer = new View(context);
        quickLabelRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        quickLabelRow.addView(Ui.text(context, "点选快速筛选", 11, Theme.TEXT_TERTIARY));
        header.addView(quickLabelRow);

        HorizontalScrollView quickScroll = new HorizontalScrollView(context);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickScroll.setClipToPadding(false);
        quickScroll.setPadding(Theme.dp(context, 4), 0, Theme.dp(context, 4), 0);
        quickRow = Ui.row(context);
        quickRow.setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 8));
        quickScroll.addView(quickRow);
        header.addView(quickScroll);

        LinearLayout listHeader = Ui.sectionHeader(context, "全部好剧", "按热度排序", null, null);
        listHeader.setPadding(Theme.dp(context, 4), Theme.dp(context, 12),
                Theme.dp(context, 4), Theme.dp(context, 10));
        header.addView(listHeader);

        paged.setPageListener((page, reset) -> {
            if (!reset) return;
            groups.clear();
            groups.addAll(page.filters);
            updateQuickThemes(page.filters);
        });
        paged.start();
    }

    private View buildTitleRow() {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 18), Theme.dp(context, 16),
                Theme.dp(context, 14), Theme.dp(context, 2));
        LinearLayout titles = Ui.column(context);
        titles.addView(Ui.bold(context, "剧场", 24, Theme.TEXT));
        TextView subtitle = Ui.text(context, "按题材、设定和热度筛选", 11, Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Theme.dp(context, 3);
        titles.addView(subtitle, subtitleParams);
        row.addView(titles);
        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout search = Ui.row(context);
        search.setPadding(Theme.dp(context, 9), Theme.dp(context, 9),
                Theme.dp(context, 9), Theme.dp(context, 9));
        search.setBackground(Theme.oval(Theme.SURFACE));
        search.addView(Ui.icon(context, R.drawable.ic_search, 21, Theme.TEXT));
        search.setOnClickListener(v -> SearchActivity.open(context));
        row.addView(search);
        return row;
    }

    private View buildGenreRow() {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                Theme.dp(context, 16), Theme.dp(context, 4));
        String[] labels = {"真人剧", "漫剧", "AI剧"};
        String[] values = {"short_play", "comic_series", "ai_series"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(context, labels[i], i == 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Theme.dp(context, 8);
            row.addView(chip, params);
            chip.setOnClickListener(v -> selectGenre(index, values[index]));
            genreChips[i] = chip;
        }
        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout filter = Ui.row(context);
        filter.setPadding(Theme.dp(context, 12), Theme.dp(context, 7),
                Theme.dp(context, 12), Theme.dp(context, 7));
        filter.setBackgroundResource(R.drawable.bg_chip_normal);
        filter.addView(Ui.icon(context, R.drawable.ic_filter, 15, Theme.TEXT_SECONDARY));
        TextView filterText = Ui.medium(context, "筛选", 12, Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterParams.leftMargin = Theme.dp(context, 4);
        filter.addView(filterText, filterParams);
        filter.setOnClickListener(v -> openFilters());
        row.addView(filter);
        return row;
    }

    private void selectGenre(int index, String value) {
        if (value.equals(genre)) return;
        genre = value;
        filters.put(HongguoApi.FILTER_GENRE, value);
        for (int i = 0; i < genreChips.length; i++) Ui.selectChip(genreChips[i], i == index);
        paged.reload();
    }

    private void updateQuickThemes(List<FilterGroup> filterGroups) {
        quickRow.removeAllViews();
        themeOptions.clear();
        for (FilterGroup group : filterGroups) {
            if (!HongguoApi.FILTER_THEME.equals(group.type)) continue;
            int count = Math.min(group.options.size(), 10);
            for (int i = 0; i < count; i++) {
                FilterOption option = group.options.get(i);
                themeOptions.put(option.id, option.label);
                boolean selected = option.id.equals(filters.get(HongguoApi.FILTER_THEME));
                TextView chip = Ui.chip(context, option.label, selected);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.rightMargin = Theme.dp(context, 8);
                quickRow.addView(chip, params);
                chip.setOnClickListener(v -> {
                    String current = filters.get(HongguoApi.FILTER_THEME);
                    if (option.id.equals(current)) {
                        filters.remove(HongguoApi.FILTER_THEME);
                        Ui.selectChip(chip, false);
                    } else {
                        filters.put(HongguoApi.FILTER_THEME, option.id);
                        for (int j = 0; j < quickRow.getChildCount(); j++) {
                            TextView sibling = (TextView) quickRow.getChildAt(j);
                            Ui.selectChip(sibling, sibling == chip);
                        }
                    }
                    paged.reload();
                });
            }
            break;
        }
        if (quickRow.getChildCount() == 0) {
            quickRow.addView(Ui.text(context, "暂无题材数据", 12, Theme.TEXT_TERTIARY));
        }
    }

    private void openFilters() {
        if (groups.isEmpty()) {
            Ui.toast(context, "筛选条件加载中，请稍后再试");
            return;
        }
        List<FilterGroup> usable = new ArrayList<>();
        for (FilterGroup group : groups) {
            if (HongguoApi.FILTER_GENRE.equals(group.type)) continue;
            usable.add(group);
        }
        Map<String, String> current = new HashMap<>(filters);
        FilterSheet.show(context, current, usable, selection -> {
            String selectedGenre = filters.get(HongguoApi.FILTER_GENRE);
            String selectedSort = selection.get(HongguoApi.FILTER_SORT);
            filters.clear();
            filters.put(HongguoApi.FILTER_GENRE,
                    selectedGenre == null ? "short_play" : selectedGenre);
            filters.put(HongguoApi.FILTER_SORT,
                    selectedSort == null ? "hot_score" : selectedSort);
            for (Map.Entry<String, String> entry : selection.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()
                        && !HongguoApi.FILTER_GENRE.equals(entry.getKey())
                        && !HongguoApi.FILTER_SORT.equals(entry.getKey())) {
                    filters.put(entry.getKey(), entry.getValue());
                }
            }
            paged.reload();
        });
    }
}
