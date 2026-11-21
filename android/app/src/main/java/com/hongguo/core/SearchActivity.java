package com.hongguo.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

/** Full-screen search with history, hot keywords and a paged result grid. */
public class SearchActivity extends Activity {
    private EditText input;
    private ScrollView suggestions;
    private FlowLayout historyRow;
    private FlowLayout hotRow;
    private FrameLayout resultHost;
    private PagedDramaView results;
    private TextView resultCount;
    private HongguoApi api;
    private String query = "";
    private String searchId = "";
    private String passback = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String[] HOT = {
            "总裁", "甜宠", "逆袭", "古装", "重生", "战神", "豪门", "穿越"
    };

    public static void open(Context context) {
        context.startActivity(new Intent(context, SearchActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.lightStatusBar(this, Theme.SURFACE);
        api = new HongguoApi(this);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Theme.SURFACE);

        LinearLayout bar = Ui.row(this);
        bar.setPadding(Theme.dp(this, 6), Theme.dp(this, 8),
                Theme.dp(this, 10), Theme.dp(this, 8));
        LinearLayout back = Ui.row(this);
        back.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 8), Theme.dp(this, 8));
        back.addView(Ui.icon(this, R.drawable.ic_back, 22, Theme.TEXT));
        back.setOnClickListener(v -> handleBack());
        bar.addView(back);

        LinearLayout pill = Ui.row(this);
        pill.setBackgroundResource(R.drawable.bg_pill);
        pill.setPadding(Theme.dp(this, 12), 0, Theme.dp(this, 12), 0);
        pill.addView(Ui.icon(this, R.drawable.ic_search, 17, Theme.TEXT_TERTIARY));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(Theme.TEXT);
        input.setHintTextColor(Theme.TEXT_TERTIARY);
        input.setHint("搜你想看的短剧");
        input.setBackground(null);
        input.setPadding(Theme.dp(this, 8), 0, 0, 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                    && (keyCode == android.view.KeyEvent.KEYCODE_ENTER
                    || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                submit();
                return true;
            }
            return false;
        });
        pill.addView(input, new LinearLayout.LayoutParams(0,
                Theme.dp(this, 40), 1f));
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                0, Theme.dp(this, 40), 1f);
        pillParams.leftMargin = Theme.dp(this, 2);
        bar.addView(pill, pillParams);
        Ui.ripple(pill);

        TextView action = Ui.medium(this, "搜索", 14, Theme.BRAND);
        action.setPadding(Theme.dp(this, 12), Theme.dp(this, 8),
                Theme.dp(this, 6), Theme.dp(this, 8));
        action.setOnClickListener(v -> submit());
        bar.addView(action);
        root.addView(bar);

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        suggestions = new ScrollView(this);
        suggestions.setBackgroundColor(Theme.SURFACE);
        suggestions.setVerticalScrollBarEnabled(false);
        LinearLayout suggestionContent = Ui.column(this);
        suggestionContent.setPadding(Theme.dp(this, 18), Theme.dp(this, 10),
                Theme.dp(this, 18), Theme.dp(this, 24));
        suggestions.addView(suggestionContent);

        LinearLayout historyHeader = Ui.row(this);
        historyHeader.addView(Ui.medium(this, "搜索历史", 14, Theme.TEXT));
        View spacer = new View(this);
        historyHeader.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView clear = Ui.text(this, "清空", 11, Theme.TEXT_TERTIARY);
        clear.setPadding(Theme.dp(this, 8), Theme.dp(this, 4),
                Theme.dp(this, 2), Theme.dp(this, 4));
        clear.setOnClickListener(v -> {
            AppPrefs.clearSearchHistory(this);
            rebuildHistory();
        });
        historyHeader.addView(clear);
        suggestionContent.addView(historyHeader);
        historyRow = new FlowLayout(this);
        historyRow.setPadding(0, Theme.dp(this, 8), 0, Theme.dp(this, 8));
        suggestionContent.addView(historyRow);
        rebuildHistory();

        TextView hotHeader = Ui.medium(this, "热门搜索", 14, Theme.TEXT);
        hotHeader.setPadding(0, Theme.dp(this, 18), 0, 0);
        suggestionContent.addView(hotHeader);
        hotRow = new FlowLayout(this);
        hotRow.setPadding(0, Theme.dp(this, 8), 0, Theme.dp(this, 8));
        suggestionContent.addView(hotRow);
        buildHot();

        body.addView(suggestions, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        resultHost = new FrameLayout(this);
        resultHost.setVisibility(View.GONE);
        body.addView(resultHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        results = new PagedDramaView(this, 2, false);
        results.setEmpty("没有找到相关短剧", "换个关键词试试看");
        LinearLayout resultHeader = results.header();
        resultCount = Ui.text(this, "搜索结果", 12, Theme.TEXT_TERTIARY);
        resultCount.setPadding(Theme.dp(this, 4), Theme.dp(this, 14),
                Theme.dp(this, 4), Theme.dp(this, 10));
        resultHeader.addView(resultCount);
        resultHost.addView(results, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        input.requestFocus();
        handler.postDelayed(() -> {
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 220);
    }

    private void rebuildHistory() {
        historyRow.removeAllViews();
        JSONArray history = AppPrefs.searchHistory(this);
        if (history.length() == 0) {
            historyRow.addView(Ui.text(this, "还没有搜索记录", 11, Theme.TEXT_TERTIARY));
            return;
        }
        for (int i = 0; i < history.length(); i++) {
            String value = history.optString(i);
            if (value.isEmpty()) continue;
            TextView chip = Ui.chip(this, value, false);
            historyRow.addView(chip);
            chip.setOnClickListener(v -> {
                input.setText(value);
                input.setSelection(value.length());
                submit();
            });
        }
    }

    private void buildHot() {
        hotRow.removeAllViews();
        for (String keyword : HOT) {
            TextView chip = Ui.chip(this, keyword, false);
            hotRow.addView(chip);
            chip.setOnClickListener(v -> {
                input.setText(keyword);
                input.setSelection(keyword.length());
                submit();
            });
        }
    }

    private void submit() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) {
            Ui.toast(this, "请输入搜索关键词");
            return;
        }
        query = value;
        AppPrefs.addSearchHistory(this, value);
        rebuildHistory();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        suggestions.setVisibility(View.GONE);
        resultHost.setVisibility(View.VISIBLE);
        resultCount.setText("正在搜索「" + query + "」…");
        searchId = "";
        passback = "";
        results.setPageListener((page, reset) -> {
            searchId = page.searchId;
            passback = page.passback;
            if (reset) {
                resultCount.setText("「" + query + "」找到 " + page.items.size() + " 部短剧");
            } else {
                int total = results.items().size();
                resultCount.setText("「" + query + "」找到 " + total + " 部短剧");
            }
        });
        results.restart(offset -> api.search(query, offset, searchId, passback));
    }

    private void handleBack() {
        if (resultHost.getVisibility() == View.VISIBLE) {
            resultHost.setVisibility(View.GONE);
            suggestions.setVisibility(View.VISIBLE);
            return;
        }
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null && keyboard.isActive()) {
            keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }
}
