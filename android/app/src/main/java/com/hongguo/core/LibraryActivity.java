package com.hongguo.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Full-page library for history, favorites and offline cache. */
public class LibraryActivity extends Activity {
    public static final String MODE_HISTORY = "history";
    public static final String MODE_FAVORITES = "favorites";
    public static final String MODE_DOWNLOADS = "downloads";
    private static final String EXTRA_MODE = "mode";

    private final List<JSONObject> items = new ArrayList<>();
    private LibraryAdapter adapter;
    private StateView state;
    private String mode;
    private TextView countLabel;

    public static void open(Context context, String mode) {
        Intent intent = new Intent(context, LibraryActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_HISTORY;
        Theme.lightStatusBar(this, Theme.SURFACE);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Theme.SURFACE);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 12), Theme.dp(this, 8));
        LinearLayout back = Ui.row(this);
        back.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 8), Theme.dp(this, 8));
        back.addView(Ui.icon(this, R.drawable.ic_back, 22, Theme.TEXT));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(Ui.medium(this, title(), 18, Theme.TEXT));
        countLabel = Ui.text(this, "", 11, Theme.TEXT_TERTIARY);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.leftMargin = Theme.dp(this, 8);
        bar.addView(countLabel, countParams);
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView clear = Ui.medium(this, "清空", 12, Theme.BRAND);
        clear.setPadding(Theme.dp(this, 10), Theme.dp(this, 8),
                Theme.dp(this, 4), Theme.dp(this, 8));
        clear.setPadding(Theme.dp(this, 10), Theme.dp(this, 6),
                Theme.dp(this, 6), Theme.dp(this, 6));
        clear.setOnClickListener(v -> confirmClear());
        bar.addView(clear);
        root.addView(bar);
        root.addView(Ui.divider(this));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Theme.DIVIDER));
        list.setDividerHeight(1);
        list.setBackgroundColor(Theme.SURFACE);
        adapter = new LibraryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openItem(items.get(position)));
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        state = new StateView(this);
        state.setBackgroundColor(Theme.SURFACE);
        body.addView(state, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        reload();
    }

    private String title() {
        if (MODE_FAVORITES.equals(mode)) return "我的收藏";
        if (MODE_DOWNLOADS.equals(mode)) return "离线缓存";
        return "观看历史";
    }

    private void reload() {
        items.clear();
        if (MODE_DOWNLOADS.equals(mode)) {
            JSONArray offline = OfflineStore.items(this);
            for (int i = 0; i < offline.length(); i++) {
                JSONObject item = offline.optJSONObject(i);
                if (item == null || item.optString("vid").isEmpty()) continue;
                String vid = item.optString("vid");
                String quality = item.optString("quality");
                File file = MediaCache.playable(this, vid, quality);
                if (!file.isFile() || file.length() <= 100_000) {
                    OfflineStore.remove(this, vid, quality);
                    continue;
                }
                items.add(item);
            }
        } else {
            JSONArray source = MODE_FAVORITES.equals(mode)
                    ? LibraryStore.favorites(this) : LibraryStore.history(this);
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item != null && !item.optString("series_id").isEmpty()) items.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        countLabel.setText(items.isEmpty() ? "" : items.size() + " 部");
        if (items.isEmpty()) {
            if (MODE_DOWNLOADS.equals(mode)) {
                state.showEmpty("还没有离线缓存", "在详情页或播放页即可缓存整部短剧");
            } else if (MODE_FAVORITES.equals(mode)) {
                state.showEmpty("还没有收藏", "在详情页点一下收藏，就能在这里找到");
            } else {
                state.showEmpty("还没有观看记录", "开始看一部短剧吧");
            }
        } else {
            state.hide();
        }
    }

    private void openItem(JSONObject item) {
        if (MODE_DOWNLOADS.equals(mode)) {
            PlayerActivity.open(this, item.optString("series_id"),
                    item.optString("title"), item.optString("cover"),
                    item.optString("vid"), item.optInt("index", 0), "[]");
            return;
        }
        Drama drama = new Drama();
        drama.seriesId = item.optString("series_id");
        drama.title = item.optString("title", "未命名短剧");
        drama.cover = item.optString("cover");
        drama.intro = item.optString("intro");
        DetailActivity.open(this, drama);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(MODE_DOWNLOADS.equals(mode) ? "清空离线缓存"
                        : "清空" + (MODE_FAVORITES.equals(mode) ? "收藏" : "历史"))
                .setMessage("此操作不可恢复，确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    if (MODE_DOWNLOADS.equals(mode)) {
                        OfflineStore.clear(this);
                        MediaCache.clear(this);
                    } else if (MODE_FAVORITES.equals(mode)) {
                        LibraryStore.clearFavorites(this);
                    } else {
                        LibraryStore.clearHistory(this);
                    }
                    reload();
                })
                .show();
    }

    private final class LibraryAdapter extends BaseAdapter {
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
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = Ui.row(getApplicationContext());
                row.setPadding(Theme.dp(LibraryActivity.this, 16), Theme.dp(LibraryActivity.this, 10),
                        Theme.dp(LibraryActivity.this, 12), Theme.dp(LibraryActivity.this, 10));
                CoverImageView cover = new CoverImageView(LibraryActivity.this);
                cover.setCornerRadius(8);
                row.addView(cover, new LinearLayout.LayoutParams(
                        Theme.dp(LibraryActivity.this, 62), Theme.dp(LibraryActivity.this, 82)));
                LinearLayout info = Ui.column(LibraryActivity.this);
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                infoParams.leftMargin = Theme.dp(LibraryActivity.this, 12);
                row.addView(info, infoParams);
                info.addView(Ui.medium(LibraryActivity.this, "", 15, Theme.TEXT));
                info.addView(Ui.text(LibraryActivity.this, "", 11, Theme.TEXT_TERTIARY));
                TextView delete = Ui.medium(LibraryActivity.this, "删除", 12, 0xFFE5484D);
                delete.setPadding(Theme.dp(LibraryActivity.this, 12), Theme.dp(LibraryActivity.this, 8),
                        Theme.dp(LibraryActivity.this, 4), Theme.dp(LibraryActivity.this, 8));
                delete.setPadding(Theme.dp(LibraryActivity.this, 10), Theme.dp(LibraryActivity.this, 10),
                        Theme.dp(LibraryActivity.this, 6), Theme.dp(LibraryActivity.this, 10));
                row.addView(delete);
                delete.setOnClickListener(v -> {
                    int index = (int) ((View) v.getParent()).getTag();
                    removeAt(index);
                });
            }
            JSONObject item = items.get(position);
            row.setTag(position);
            CoverImageView cover = (CoverImageView) row.getChildAt(0);
            cover.load(item.optString("cover"));
            LinearLayout info = (LinearLayout) row.getChildAt(1);
            ((TextView) info.getChildAt(0)).setText(item.optString("title", "未命名短剧"));
            int index = item.optInt("index", 0);
            TextView subtitle = (TextView) info.getChildAt(1);
            if (MODE_DOWNLOADS.equals(mode)) {
                String quality = item.optString("quality", "").toUpperCase();
                long size = item.optLong("size", 0);
                subtitle.setText("第 " + (index + 1) + " 集 · "
                        + (quality.isEmpty() ? "缓存" : quality) + " · " + formatSize(size));
            } else if (MODE_FAVORITES.equals(mode)) {
                int count = item.optInt("episode_count", 0);
                subtitle.setText(count > 0 ? "共 " + count + " 集" : "已收藏");
            } else {
                subtitle.setText(index > 0 ? "上次看到第 " + (index + 1) + " 集" : "已加入观看历史");
            }
            return row;
        }
    }

    private void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        JSONObject item = items.get(position);
        if (MODE_DOWNLOADS.equals(mode)) {
            String vid = item.optString("vid");
            String quality = item.optString("quality");
            java.io.File file = MediaCache.playable(this, vid, quality);
            if (!file.exists()) file = MediaCache.legacyPlayable(this, vid);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            OfflineStore.remove(this, vid, quality);
        } else if (MODE_FAVORITES.equals(mode)) {
            LibraryStore.removeFavorite(this, item.optString("series_id"));
        } else {
            LibraryStore.removeHistory(this, item.optString("series_id"));
        }
        reload();
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1fMB", bytes / 1024.0 / 1024);
        }
        return Math.max(bytes / 1024, 0) + "KB";
    }
}
