package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard page with pull-to-refresh and automatic pagination. */
public final class PagedRankView extends FrameLayout {
    public interface Fetcher {
        ListPage fetch(int offset, String sessionId) throws Exception;
    }

    public interface PageListener {
        void onPage(ListPage page, boolean reset);
    }

    private final Context context;
    private final RefreshLayout refresh;
    private final LinearLayout header;
    private final LinearLayout footer;
    private final ListView list;
    private final RankAdapter adapter;
    private final StateView state;
    private final SkeletonView skeleton;
    private Fetcher fetcher;
    private PageListener pageListener;
    private boolean started;
    private boolean loading;
    private boolean hasMore = true;
    private int nextOffset;
    private String sessionId = "";

    public PagedRankView(Context context) {
        super(context);
        this.context = context;
        setBackgroundColor(Theme.SURFACE);
        refresh = new RefreshLayout(context);
        refresh.setListener(layout -> load(true));
        addView(refresh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new ListView(context);
        list.setDivider(new ColorDrawable(Theme.DIVIDER));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setBackgroundColor(Theme.SURFACE);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        header = Ui.column(context);
        list.addHeaderView(header, null, false);
        footer = new LinearLayout(context);
        footer.setGravity(android.view.Gravity.CENTER);
        list.addFooterView(footer, null, false);
        adapter = new RankAdapter(context);
        list.setAdapter(adapter);
        refresh.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total <= visible) return;
                if (hasMore && !loading && total > 0 && first + visible >= total - 3) {
                    load(false);
                }
            }
        });
        skeleton = new SkeletonView(context, 1, true);
        skeleton.hide();
        addView(skeleton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        state = new StateView(context);
        addView(state, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        state.hide();
        updateFooter();
    }

    public LinearLayout header() {
        return header;
    }

    public void setFetcher(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public void setPageListener(PageListener listener) {
        this.pageListener = listener;
    }

    public void start() {
        if (started) return;
        started = true;
        load(true);
    }

    public void reload() {
        load(true);
    }

    public void reloadFresh() {
        sessionId = "";
        load(true);
    }

    private void load(boolean reset) {
        if (loading || fetcher == null) return;
        loading = true;
        final int offset = reset ? 0 : nextOffset;
        if (reset && adapter.items.isEmpty()) {
            skeleton.show();
            state.hide();
        } else {
            skeleton.hide();
            state.hide();
        }
        updateFooter();
        App.io(() -> {
            try {
                ListPage page = fetcher.fetch(offset, sessionId);
                App.ui(() -> onPage(page, reset));
            } catch (Throwable error) {
                App.ui(() -> onFailure(error, reset));
            }
        });
    }

    private void onPage(ListPage page, boolean reset) {
        loading = false;
        skeleton.hide();
        hasMore = page.hasMore;
        nextOffset = page.nextOffset;
        if (!page.sessionId.isEmpty()) sessionId = page.sessionId;
        if (reset) adapter.setItems(page.items);
        else adapter.append(page.items);
        if (pageListener != null) pageListener.onPage(page, reset);
        if (adapter.items.isEmpty() && !hasMore) state.showEmpty("榜单暂时为空", "稍后再来看看");
        else state.hide();
        refresh.finishRefresh();
        updateFooter();
    }

    private void onFailure(Throwable error, boolean reset) {
        loading = false;
        skeleton.hide();
        refresh.finishRefresh();
        updateFooter();
        String message = error.getMessage() == null ? "请稍后再试" : error.getMessage();
        if (reset && adapter.items.isEmpty()) {
            state.showError("加载失败", message, () -> load(true));
        } else {
            Toast.makeText(context, "加载失败：" + message, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFooter() {
        footer.removeAllViews();
        if (adapter.items.isEmpty()) {
            footer.setVisibility(GONE);
            return;
        }
        footer.setVisibility(VISIBLE);
        if (loading) {
            ProgressBar progress = new ProgressBar(context);
            progress.setIndeterminate(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Theme.dp(context, 16), Theme.dp(context, 16));
            params.rightMargin = Theme.dp(context, 6);
            footer.addView(progress, params);
            footer.addView(Ui.text(context, "正在加载…", 12, Theme.TEXT_TERTIARY));
        } else if (hasMore) {
            TextView more = Ui.medium(context, "加载更多", 12, Theme.BRAND);
            more.setPadding(0, Theme.dp(context, 4), 0, Theme.dp(context, 4));
            more.setOnClickListener(v -> load(false));
            footer.addView(more);
        } else {
            footer.addView(Ui.text(context, "已经到底啦", 12, Theme.TEXT_TERTIARY));
        }
        footer.setPadding(0, Theme.dp(context, 14), 0, Theme.dp(context, 18));
        footer.setBackgroundColor(Theme.SURFACE);
    }

    private static final class RankAdapter extends BaseAdapter {
        private final Context context;
        private final List<Drama> items = new ArrayList<>();

        RankAdapter(Context context) {
            this.context = context;
        }

        void setItems(List<Drama> values) {
            items.clear();
            if (values != null) items.addAll(values);
            notifyDataSetChanged();
        }

        void append(List<Drama> values) {
            if (values == null || values.isEmpty()) return;
            items.addAll(values);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RankRow row;
            if (convertView instanceof RankRow) {
                row = (RankRow) convertView;
            } else {
                row = new RankRow(context);
                row.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.bind(items.get(position), position);
            return row;
        }
    }
}
