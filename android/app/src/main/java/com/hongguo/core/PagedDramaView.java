package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Reusable grid page with pull-to-refresh, skeleton state and infinite scroll. */
public final class PagedDramaView extends FrameLayout {
    public interface Fetcher {
        ListPage fetch(int offset) throws Exception;
    }

    public interface PageListener {
        void onPage(ListPage page, boolean reset);
    }

    private final Context context;
    private final RefreshLayout refresh;
    private final LinearLayout header;
    private final LinearLayout footer;
    private final android.widget.ListView list;
    private final DramaGridAdapter adapter;
    private final StateView state;
    private final SkeletonView skeleton;
    private final int columns;
    private Fetcher fetcher;
    private PageListener pageListener;
    private boolean started;
    private boolean loading;
    private boolean hasMore = true;
    private int nextOffset;
    private int generation;
    private int firstPageSkip;
    private String emptyTitle = "这里还什么都没有";
    private String emptyHint = "换个条件试试看";

    public PagedDramaView(Context context, int columns, boolean compact) {
        super(context);
        this.context = context;
        this.columns = columns;
        setBackgroundColor(Theme.BG);
        refresh = new RefreshLayout(context);
        refresh.setListener(layout -> load(true));
        addView(refresh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new android.widget.ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setClipToPadding(false);
        list.setPadding(Theme.dp(context, 12), 0, Theme.dp(context, 12), Theme.dp(context, 18));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);

        header = Ui.column(context);
        list.addHeaderView(header, null, false);

        footer = new LinearLayout(context);
        footer.setGravity(android.view.Gravity.CENTER);
        list.addFooterView(footer, null, false);

        adapter = new DramaGridAdapter(context, columns, compact);
        adapter.setListener(drama -> DetailActivity.open(context, drama));
        list.setAdapter(adapter);

        refresh.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int state) {
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
                if (total <= visible) return;
                if (hasMore && !loading && total > 0 && first + visible >= total - 3) {
                    loadMore();
                }
            }
        });

        skeleton = new SkeletonView(context, columns, false);
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

    public void setEmpty(String title, String hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
    }

    public void setFirstPageSkip(int count) {
        this.firstPageSkip = Math.max(count, 0);
    }

    public void start() {
        if (started) return;
        started = true;
        load(true);
    }

    public void reload() {
        load(true);
    }

    /** Drops the old result set and starts a new query (used by search). */
    public void restart(Fetcher fetcher) {
        generation++;
        this.fetcher = fetcher;
        loading = false;
        started = true;
        hasMore = true;
        nextOffset = 0;
        adapter.setItems(new ArrayList<>());
        skeleton.show();
        state.hide();
        updateFooter();
        load(true);
    }

    public boolean isLoading() {
        return loading;
    }

    public List<Drama> items() {
        return adapter.items();
    }

    private void loadMore() {
        if (loading) return;
        load(false);
    }

    private void load(boolean reset) {
        if (loading || fetcher == null) return;
        loading = true;
        final int offset = reset ? 0 : nextOffset;
        if (reset && adapter.items().isEmpty()) {
            skeleton.show();
            state.hide();
        } else {
            skeleton.hide();
            state.hide();
        }
        updateFooter();
        final int gen = generation;
        App.io(() -> {
            try {
                ListPage page = fetcher.fetch(offset);
                App.ui(() -> {
                    if (gen != generation) return;
                    onPage(page, reset);
                });
            } catch (Throwable error) {
                App.ui(() -> {
                    if (gen != generation) return;
                    onFailure(error, reset);
                });
            }
        });
    }

    private void onPage(ListPage page, boolean reset) {
        loading = false;
        skeleton.hide();
        hasMore = page.hasMore;
        nextOffset = page.nextOffset;
        if (reset) {
            if (firstPageSkip > 0 && page.items.size() > firstPageSkip) {
                adapter.setItems(page.items.subList(firstPageSkip, page.items.size()));
            } else {
                adapter.setItems(page.items);
            }
        } else {
            adapter.append(page.items);
        }
        if (pageListener != null) pageListener.onPage(page, reset);
        if (adapter.items().isEmpty() && !hasMore) {
            state.showEmpty(emptyTitle, emptyHint);
        } else {
            state.hide();
        }
        refresh.finishRefresh();
        updateFooter();
    }

    private void onFailure(Throwable error, boolean reset) {
        loading = false;
        skeleton.hide();
        refresh.finishRefresh();
        updateFooter();
        String message = error.getMessage() == null ? "请稍后再试" : error.getMessage();
        if (reset && adapter.items().isEmpty()) {
            state.showError("加载失败", message, () -> load(true));
        } else {
            Toast.makeText(context, "加载更多失败：" + message, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateFooter() {
        footer.removeAllViews();
        if (adapter.items().isEmpty()) {
            footer.setVisibility(GONE);
            return;
        }
        footer.setVisibility(VISIBLE);
        if (loading) {
            android.widget.ProgressBar progress = new android.widget.ProgressBar(context);
            progress.setIndeterminate(true);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    Theme.dp(context, 16), Theme.dp(context, 16));
            progressParams.rightMargin = Theme.dp(context, 6);
            footer.addView(progress, progressParams);
            footer.addView(Ui.text(context, "正在加载…", 12, Theme.TEXT_TERTIARY));
        } else if (hasMore) {
            TextView more = Ui.medium(context, "加载更多", 12, Theme.BRAND);
            more.setPadding(0, Theme.dp(context, 4), 0, Theme.dp(context, 4));
            more.setOnClickListener(v -> loadMore());
            footer.addView(more);
        } else {
            footer.addView(Ui.text(context, "已经到底啦", 12, Theme.TEXT_TERTIARY));
        }
        footer.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 16));
    }

    private static final class DramaGridAdapter extends BaseAdapter {
        interface Listener {
            void onDramaClick(Drama drama);
        }

        private final Context context;
        private final int columns;
        private final boolean compact;
        private final List<Drama> items = new ArrayList<>();
        private Listener listener;

        DramaGridAdapter(Context context, int columns, boolean compact) {
            this.context = context;
            this.columns = columns;
            this.compact = compact;
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        List<Drama> items() {
            return items;
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
            return (items.size() + columns - 1) / columns;
        }

        @Override
        public Object getItem(int position) {
            int index = position * columns;
            return index < items.size() ? items.get(index) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, Theme.dp(context, 14));
                for (int i = 0; i < columns; i++) {
                    if (i > 0) {
                        View spacer = new View(context);
                        row.addView(spacer, new LinearLayout.LayoutParams(
                                Theme.dp(context, 10), 1));
                    }
                    DramaCard card = new DramaCard(context, compact);
                    card.setListener(drama -> {
                        if (listener != null) listener.onDramaClick(drama);
                    });
                    row.addView(card, new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
            }
            for (int i = 0; i < columns; i++) {
                DramaCard card = (DramaCard) row.getChildAt(i * 2);
                int index = position * columns + i;
                if (index < items.size()) {
                    card.setVisibility(View.VISIBLE);
                    card.bind(items.get(index));
                } else {
                    card.setVisibility(View.INVISIBLE);
                }
            }
            return row;
        }
    }
}
