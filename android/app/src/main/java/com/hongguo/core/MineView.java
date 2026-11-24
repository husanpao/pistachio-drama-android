package com.hongguo.core;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** 我的 tab: profile, history, favorites, cache management and settings. */
public final class MineView extends FrameLayout {
    private final Context context;
    private final LinearLayout continueRow;
    private final TextView historyCount;
    private final TextView favoriteCount;
    private final TextView cacheTrailing;
    private final TextView deviceLabel;

    public MineView(Context context) {
        super(context);
        this.context = context;

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = Ui.column(context);
        content.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 28));
        scroll.addView(content);

        LinearLayout profile = Ui.column(context);
        profile.setBackground(Theme.gradient(
                new int[]{0xFFFF3D5A, 0xFFFF7A45}, Theme.dp(context, 20),
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR));
        profile.setPadding(Theme.dp(context, 18), Theme.dp(context, 18),
                Theme.dp(context, 14), Theme.dp(context, 16));
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        profileParams.setMargins(Theme.dp(context, 12), 0, Theme.dp(context, 12), 0);
        content.addView(profile, profileParams);

        LinearLayout top = Ui.row(context);
        FrameLayout avatar = new FrameLayout(context);
        avatar.setBackgroundResource(R.drawable.bg_avatar);
        TextView avatarText = Ui.black(context, "果", 23, Color.WHITE);
        avatarText.setGravity(Gravity.CENTER);
        avatar.addView(avatarText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(avatar, new LinearLayout.LayoutParams(
                Theme.dp(context, 54), Theme.dp(context, 54)));

        LinearLayout names = Ui.column(context);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = Theme.dp(context, 12);
        top.addView(names, nameParams);
        names.addView(Ui.medium(context, "开心果游客", 18, Color.WHITE));
        deviceLabel = Ui.text(context, "游客身份 · 本机保存", 10.5f, 0xCCFFFFFF);
        LinearLayout.LayoutParams deviceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deviceParams.topMargin = Theme.dp(context, 4);
        names.addView(deviceLabel, deviceParams);

        LinearLayout settings = Ui.row(context);
        settings.setPadding(Theme.dp(context, 8), Theme.dp(context, 8),
                Theme.dp(context, 8), Theme.dp(context, 8));
        settings.addView(Ui.icon(context, R.drawable.ic_settings, 21, Color.WHITE));
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);
        profile.addView(top);

        LinearLayout stats = Ui.row(context);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = Theme.dp(context, 18);
        profile.addView(stats, statsParams);
        historyCount = addStat(context, stats, "观看历史");
        addVerticalLine(context, stats);
        favoriteCount = addStat(context, stats, "我的收藏");

        TextView tip = Ui.text(context, "搜索与收藏数据只保存在本机", 10, 0xCCFFFFFF);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipParams.topMargin = Theme.dp(context, 14);
        profile.addView(tip, tipParams);

        LinearLayout continueHeader = Ui.sectionHeader(context, "继续观看", null, "全部",
                () -> LibraryActivity.open(context, LibraryActivity.MODE_HISTORY));
        continueHeader.setPadding(Theme.dp(context, 16), Theme.dp(context, 18),
                Theme.dp(context, 14), Theme.dp(context, 4));
        content.addView(continueHeader);

        HorizontalScrollView historyScroll = new HorizontalScrollView(context);
        historyScroll.setHorizontalScrollBarEnabled(false);
        historyScroll.setClipToPadding(false);
        historyScroll.setPadding(Theme.dp(context, 14), 0, Theme.dp(context, 14), 0);
        continueRow = Ui.row(context);
        continueRow.setPadding(0, Theme.dp(context, 4), 0, Theme.dp(context, 6));
        historyScroll.addView(continueRow);
        content.addView(historyScroll);

        LinearLayout menuCard = Ui.column(context);
        menuCard.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menuParams.setMargins(Theme.dp(context, 12), Theme.dp(context, 12),
                Theme.dp(context, 12), 0);
        content.addView(menuCard, menuParams);

        menuCard.addView(menuRow(R.drawable.ic_history, "观看历史", "",
                () -> LibraryActivity.open(context, LibraryActivity.MODE_HISTORY)));
        menuCard.addView(menuDivider());
        menuCard.addView(menuRow(R.drawable.ic_heart, "我的收藏", "",
                () -> LibraryActivity.open(context, LibraryActivity.MODE_FAVORITES)));
        menuCard.addView(menuDivider());
        cacheTrailing = Ui.text(context, "", 11, Theme.TEXT_TERTIARY);
        menuCard.addView(menuRow(R.drawable.ic_trash, "清理播放缓存", cacheTrailing,
                this::confirmClearCache));
        menuCard.addView(menuDivider());
        menuCard.addView(menuRow(R.drawable.ic_settings, "设置", "",
                this::showSettings));
        menuCard.addView(menuDivider());
        menuCard.addView(menuRow(R.drawable.ic_download, "检查更新", "",
                () -> UpdateChecker.check(context, true)));
        menuCard.addView(menuDivider());
        menuCard.addView(menuRow(R.drawable.ic_star, "关于开心果短剧", "v0.5.8",
                this::showAbout));

        TextView footer = Ui.text(context, "仅供个人协议研究与学习使用", 10, Theme.TEXT_TERTIARY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Theme.dp(context, 20), 0, 0);
        content.addView(footer);

        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        onShown();
    }

    private TextView addStat(Context context, LinearLayout parent, String label) {
        LinearLayout column = Ui.column(context);
        column.setGravity(Gravity.CENTER);
        TextView value = Ui.bold(context, "0", 20, Color.WHITE);
        value.setGravity(Gravity.CENTER);
        column.addView(value);
        TextView caption = Ui.text(context, label, 10.5f, 0xCCFFFFFF);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = Theme.dp(context, 2);
        column.addView(caption, captionParams);
        parent.addView(column, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return value;
    }

    private void addVerticalLine(Context context, LinearLayout parent) {
        View line = new View(context);
        line.setBackgroundColor(0x33FFFFFF);
        parent.addView(line, new LinearLayout.LayoutParams(
                Math.max(1, Theme.dp(context, 0.6f)), Theme.dp(context, 26)));
    }

    private View menuRow(int icon, String label, String trailingText, Runnable action) {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                Theme.dp(context, 14), Theme.dp(context, 14));
        row.addView(Ui.icon(context, icon, 19, Theme.TEXT_SECONDARY));
        TextView text = Ui.medium(context, label, 14, Theme.TEXT);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Theme.dp(context, 12);
        row.addView(text, textParams);
        if (trailingText != null && !trailingText.isEmpty()) {
            TextView trailing = Ui.text(context, trailingText, 11, Theme.TEXT_TERTIARY);
            row.addView(trailing);
        }
        row.addView(Ui.icon(context, R.drawable.ic_arrow_right, 14, Theme.TEXT_TERTIARY));
        row.setOnClickListener(v -> action.run());
        Ui.ripple(row);
        return row;
    }

    private View menuRow(int icon, String label, TextView trailing, Runnable action) {
        LinearLayout row = Ui.row(context);
        row.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                Theme.dp(context, 14), Theme.dp(context, 14));
        row.addView(Ui.icon(context, icon, 19, Theme.TEXT_SECONDARY));
        TextView text = Ui.medium(context, label, 14, Theme.TEXT);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Theme.dp(context, 12);
        row.addView(text, textParams);
        if (trailing.getParent() != null) {
            ((ViewGroup) trailing.getParent()).removeView(trailing);
        }
        row.addView(trailing);
        row.addView(Ui.icon(context, R.drawable.ic_arrow_right, 14, Theme.TEXT_TERTIARY));
        row.setOnClickListener(v -> action.run());
        Ui.ripple(row);
        return row;
    }

    private View menuDivider() {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(context, 0.6f)));
        params.leftMargin = Theme.dp(context, 47);
        divider.setLayoutParams(params);
        return divider;
    }

    public void onShown() {
        JSONArray history = LibraryStore.history(context);
        JSONArray favorites = LibraryStore.favorites(context);
        historyCount.setText(String.valueOf(history.length()));
        favoriteCount.setText(String.valueOf(favorites.length()));
        long bytes = MediaCache.size(context);
        String size = formatSize(bytes);
        if (cacheTrailing != null) cacheTrailing.setText(size);
        deviceLabel.setText(deviceLine());
        rebuildContinue(history);
    }

    private void refreshStats() {
        historyCount.setText(String.valueOf(LibraryStore.history(context).length()));
        favoriteCount.setText(String.valueOf(LibraryStore.favorites(context).length()));
        if (cacheTrailing != null) cacheTrailing.setText(formatSize(MediaCache.size(context)));
    }

    private String deviceLine() {
        DeviceSession session = DeviceSessionStore.load(context);
        if (session == null) return "游客身份 · 首次搜索时自动创建";
        String id = session.deviceId;
        if (id.length() > 10) id = id.substring(0, 4) + "…" + id.substring(id.length() - 6);
        return "游客 ID · " + id;
    }

    private void rebuildContinue(JSONArray history) {
        continueRow.removeAllViews();
        if (history.length() == 0) {
            LinearLayout empty = Ui.row(context);
            empty.setBackground(Theme.round(Theme.SURFACE, Theme.dp(context, 14)));
            empty.setPadding(Theme.dp(context, 16), Theme.dp(context, 14),
                    Theme.dp(context, 16), Theme.dp(context, 14));
            empty.addView(Ui.icon(context, R.drawable.ic_history, 22, Theme.TEXT_TERTIARY));
            TextView hint = Ui.text(context, "看过的短剧会出现在这里", 12, Theme.TEXT_TERTIARY);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.leftMargin = Theme.dp(context, 8);
            empty.addView(hint, hintParams);
            empty.setOnClickListener(v -> LibraryActivity.open(context, LibraryActivity.MODE_HISTORY));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.screenWidth(context) - Theme.dp(context, 36),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = Theme.dp(context, 2);
            params.rightMargin = Theme.dp(context, 2);
            continueRow.addView(empty, params);
            return;
        }
        int count = Math.min(history.length(), 10);
        for (int i = 0; i < count; i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            Drama drama = new Drama();
            drama.seriesId = item.optString("series_id");
            drama.title = item.optString("title", "未命名短剧");
            drama.cover = item.optString("cover");
            drama.coverFallback = DramaParser.browserCoverFallback(drama.cover);
            int index = item.optInt("index", 0);
            int episodeCount = item.optInt("episode_cnt", 0);
            LinearLayout card = Ui.column(context);
            FrameLayout coverBox = new FrameLayout(context);
            CoverImageView cover = new CoverImageView(context);
            cover.setCornerRadius(10);
            cover.setHeightRatio(4f / 3f);
            cover.load(drama.coverUrl());
            coverBox.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView badge = Ui.medium(context, "第" + (index + 1) + "集", 9, 0xFFFFFFFF);
            badge.setBackground(Theme.round(0x99000000, Theme.dp(context, 6)));
            badge.setPadding(Theme.dp(context, 5), Theme.dp(context, 2),
                    Theme.dp(context, 5), Theme.dp(context, 2));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            badgeParams.setMargins(0, 0, Theme.dp(context, 5), Theme.dp(context, 5));
            coverBox.addView(badge, badgeParams);
            card.addView(coverBox, new LinearLayout.LayoutParams(
                    Theme.dp(context, 112), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView title = Ui.medium(context, drama.title, 12, Theme.TEXT);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    Theme.dp(context, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = Theme.dp(context, 6);
            card.addView(title, titleParams);
            String progressText = episodeCount > 0
                    ? "第" + (index + 1) + "集 / 共" + episodeCount + "集"
                    : "上次看到第" + (index + 1) + "集";
            TextView progress = Ui.text(context, progressText, 10, Theme.TEXT_TERTIARY);
            progress.setSingleLine(true);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    Theme.dp(context, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
            progressParams.topMargin = Theme.dp(context, 2);
            card.addView(progress, progressParams);
            card.setOnClickListener(v -> {
                PlayerActivity.IntentBuilder builder = new PlayerActivity.IntentBuilder(context,
                        drama.seriesId, drama.title, drama.cover,
                        item.optString("vid"), index);
                String episodes = item.optString("episode_vids", "");
                if (!episodes.isEmpty()) builder.episodes(episodes);
                builder.launch();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Theme.dp(context, 112), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Theme.dp(context, 10);
            continueRow.addView(card, params);
        }
    }

    private void confirmClearCache() {
        long bytes = MediaCache.size(context) + ImageLoader.diskSize();
        new AlertDialog.Builder(context)
                .setTitle("清理缓存")
                .setMessage("将删除封面缓存和已下载的剧集（" + formatSize(bytes) + "），确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialog, which) -> {
                    MediaCache.clear(context);
                    OfflineStore.clear(context);
                    ImageLoader.clearDisk();
                    Ui.toast(context, "缓存已清理");
                    onShown();
                })
                .show();
    }

    private void showSettings() {
        Dialog dialog = new Dialog(context, R.style.Theme_Hongguo_Sheet);
        LinearLayout root = Ui.column(context);
        root.setBackgroundResource(R.drawable.bg_sheet);
        root.setPadding(Theme.dp(context, 18), Theme.dp(context, 16),
                Theme.dp(context, 18), Theme.dp(context, 16));
        root.addView(Ui.medium(context, "播放设置", 17, Theme.TEXT));

        root.addView(settingLabel("默认清晰度"));
        String[] qualities = {"auto", "360p", "480p", "720p", "1080p"};
        String[] labels = {"自动", "360P", "480P", "720P", "1080P"};
        String current = AppPrefs.quality(context);
        FlowLayout qualityFlow = new FlowLayout(context);
        TextView[] qualityChips = new TextView[qualities.length];
        for (int i = 0; i < qualities.length; i++) {
            final String value = qualities[i];
            TextView chip = Ui.chip(context, labels[i], qualities[i].equals(current));
            qualityChips[i] = chip;
            chip.setOnClickListener(v -> {
                for (int j = 0; j < qualities.length; j++) {
                    Ui.selectChip(qualityChips[j], qualities[j].equals(value));
                }
                AppPrefs.setQuality(context, value);
            });
            qualityFlow.addView(chip);
        }
        root.addView(qualityFlow);

        Switch autoNext = new Switch(context);
        autoNext.setText("自动播放下一集");
        autoNext.setTextSize(14);
        autoNext.setTextColor(Theme.TEXT);
        autoNext.setChecked(AppPrefs.autoNext(context));
        autoNext.setPadding(0, Theme.dp(context, 14), 0, Theme.dp(context, 6));
        autoNext.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setAutoNext(context, isChecked));
        root.addView(autoNext);

        Switch preload = new Switch(context);
        preload.setText("自动预加载下一集");
        preload.setTextSize(14);
        preload.setTextColor(Theme.TEXT);
        preload.setChecked(AppPrefs.autoPreload(context));
        preload.setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 6));
        preload.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setAutoPreload(context, isChecked));
        root.addView(preload);

        Switch fillScreen = new Switch(context);
        fillScreen.setText("播放器画面铺满");
        fillScreen.setTextSize(14);
        fillScreen.setTextColor(Theme.TEXT);
        fillScreen.setChecked(AppPrefs.fillScreen(context));
        fillScreen.setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 6));
        fillScreen.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppPrefs.setFillScreen(context, isChecked));
        root.addView(fillScreen);

        TextView done = Ui.medium(context, "完成", 14, Color.WHITE);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Theme.round(Theme.BRAND, Theme.dp(context, 22)));
        done.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 12));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 46));
        doneParams.topMargin = Theme.dp(context, 20);
        root.addView(done, doneParams);
        done.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView settingLabel(String text) {
        TextView label = Ui.medium(context, text, 13, Theme.TEXT);
        label.setPadding(0, Theme.dp(context, 18), 0, 0);
        return label;
    }

    private void showAbout() {
        new AlertDialog.Builder(context)
                .setTitle("免责声明")
                .setMessage("版本 v0.5.8\n\n"
                        + "本应用仅用于个人 Android 开发学习、技术研究与界面设计练习，"
                        + "不提供任何影视内容的存储、分发、售卖或商业服务。\n\n"
                        + "所有剧集、封面、演员肖像、商标及相关内容的版权归原权利人所有。\n\n"
                        + "请勿将本应用用于商业用途或非法传播。使用本应用产生的任何后果及法律责任"
                        + "由使用者自行承担。\n\n"
                        + "如权利人认为内容侵权，请联系删除。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1fGB", bytes / 1024.0 / 1024 / 1024);
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.0fMB", bytes / 1024.0 / 1024);
        }
        return Math.max(bytes / 1024, 0) + "KB";
    }
}
