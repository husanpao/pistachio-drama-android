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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 推荐 tab: brand/search header, genre chips, hot carousel and a 2-column feed. */
public final class HomeView extends FrameLayout {
    public interface TabHost {
        void openTab(int index);
    }

    private final Context context;
    private final HongguoApi api;
    private final PagedDramaView paged;
    private final LinearLayout carousel;
    private final Map<String, String> filters = new HashMap<>(HongguoApi.defaultFilters());
    private final TextView[] genreChips = new TextView[3];
    private String genre = "short_play";
    private final TabHost host;

    public HomeView(Context context, TabHost host) {
        super(context);
        this.context = context;
        this.host = host;
        this.api = new HongguoApi(context);
        paged = new PagedDramaView(context, 2, false);
        paged.setFetcher(offset -> api.category(offset, filters, false));
        paged.setEmpty("推荐内容暂时为空", "下拉刷新试试看");
        paged.setFirstPageSkip(6);
        addView(paged, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = paged.header();
        header.setPadding(0, 0, 0, 0);
        header.addView(buildBrandRow());
        header.addView(buildSearchPill());
        header.addView(buildGenreRow());

        header.addView(buildCarouselTitle());
        carousel = new LinearLayout(context);
        carousel.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 16), 0);
        scroll.addView(carousel);
        header.addView(scroll);

        LinearLayout recommendHeader = Ui.sectionHeader(context, "为你推荐", "持续更新", null, null);
        recommendHeader.setPadding(Theme.dp(context, 4), Theme.dp(context, 22),
                Theme.dp(context, 4), Theme.dp(context, 10));
        header.addView(recommendHeader);

        paged.setPageListener((page, reset) -> {
            if (reset) bindCarousel(page.items);
        });
        paged.start();
    }

    private View buildBrandRow() {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 18), Theme.dp(context, 12),
                Theme.dp(context, 14), 0);
        TextView logo = Ui.black(context, "开心果", 25, Theme.BRAND);
        row.addView(logo);
        TextView suffix = Ui.medium(context, "短剧", 12, Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams suffixParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suffixParams.leftMargin = Theme.dp(context, 5);
        suffixParams.topMargin = Theme.dp(context, 7);
        row.addView(suffix, suffixParams);
        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout history = Ui.row(context);
        history.setPadding(Theme.dp(context, 10), Theme.dp(context, 8),
                Theme.dp(context, 10), Theme.dp(context, 8));
        ImageView historyIcon = Ui.icon(context, R.drawable.ic_history, 21, Theme.TEXT_SECONDARY);
        history.addView(historyIcon);
        history.setOnClickListener(v -> LibraryActivity.open(context, LibraryActivity.MODE_HISTORY));
        Ui.ripple(history);
        row.addView(history);

        LinearLayout searchShortcut = Ui.row(context);
        searchShortcut.setPadding(Theme.dp(context, 10), Theme.dp(context, 8),
                Theme.dp(context, 10), Theme.dp(context, 8));
        searchShortcut.addView(Ui.icon(context, R.drawable.ic_search, 21, Theme.TEXT_SECONDARY));
        searchShortcut.setOnClickListener(v -> SearchActivity.open(context));
        Ui.ripple(searchShortcut);
        row.addView(searchShortcut);
        return row;
    }

    private View buildSearchPill() {
        LinearLayout pill = Ui.row(context);
        pill.setPadding(Theme.dp(context, 14), 0, Theme.dp(context, 14), 0);
        pill.setBackgroundResource(R.drawable.bg_pill);
        ImageView icon = Ui.icon(context, R.drawable.ic_search, 17, Theme.TEXT_TERTIARY);
        pill.addView(icon);
        TextView hint = Ui.text(context, "搜你想看的短剧", 13, Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = Theme.dp(context, 8);
        pill.addView(hint, hintParams);
        View spacer = new View(context);
        pill.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView action = Ui.medium(context, "搜索", 12, Theme.BRAND);
        pill.addView(action);
        pill.setOnClickListener(v -> SearchActivity.open(context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 40));
        params.setMargins(Theme.dp(context, 16), Theme.dp(context, 12),
                Theme.dp(context, 16), 0);
        pill.setLayoutParams(params);
        Ui.ripple(pill);
        return pill;
    }

    private View buildGenreRow() {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                Theme.dp(context, 16), Theme.dp(context, 4));
        String[] labels = {"推荐", "漫剧", "AI剧"};
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
        TextView filterText = Ui.medium(context, "找剧", 12, Theme.TEXT_SECONDARY);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterParams.leftMargin = Theme.dp(context, 4);
        filter.addView(filterText, filterParams);
        filter.setOnClickListener(v -> host.openTab(1));
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

    private View buildCarouselTitle() {
        LinearLayout header = Ui.sectionHeader(context, "正在热播", "本周热度", "排行榜",
                () -> host.openTab(2));
        header.setPadding(Theme.dp(context, 16), Theme.dp(context, 18),
                Theme.dp(context, 12), Theme.dp(context, 10));
        ImageView fire = Ui.icon(context, R.drawable.ic_fire, 16, Theme.BRAND);
        LinearLayout.LayoutParams fireParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 16), Theme.dp(context, 16));
        fireParams.rightMargin = Theme.dp(context, 5);
        header.addView(fire, 0, fireParams);
        return header;
    }

    private void bindCarousel(List<Drama> items) {
        carousel.removeAllViews();
        int count = Math.min(items.size(), 6);
        for (int i = 0; i < count; i++) {
            DramaCard card = new DramaCard(context, true);
            card.bind(items.get(i));
            card.setListener(drama -> DetailActivity.open(context, drama));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Theme.dp(context, 124), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Theme.dp(context, 10);
            carousel.addView(card, params);
        }
        if (count == 0) {
            carousel.addView(Ui.text(context, "暂无热播内容", 13, Theme.TEXT_TERTIARY));
        }
    }
}
