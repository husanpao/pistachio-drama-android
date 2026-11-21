package com.hongguo.core;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/** App shell: five tabs with a persistent bottom navigation bar. */
public class MainActivity extends Activity implements HomeView.TabHost {
    private FrameLayout content;
    private BottomNav nav;
    private final View[] tabs = new View[5];
    private int current = -1;
    private long lastBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.lightStatusBar(this, Theme.SURFACE);
        getWindow().setNavigationBarColor(Theme.SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new BottomNav(this);
        nav.setListener(this::switchTab);
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 60)));

        setContentView(root);
        switchTab(0);
    }

    @Override
    public void openTab(int index) {
        nav.select(index, true);
    }

    private void switchTab(int index) {
        if (index == current) return;
        if (index < 0 || index > 4) return;
        View old = current >= 0 ? tabs[current] : null;
        if (old instanceof FeedView) ((FeedView) old).onHidden();
        current = index;
        View tab = tabs[index];
        if (tab == null) {
            tab = createTab(index);
            tabs[index] = tab;
            content.addView(tab, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        for (View view : tabs) {
            if (view != null) view.setVisibility(view == tab ? View.VISIBLE : View.GONE);
        }
        tab.setVisibility(View.VISIBLE);
        tab.setAlpha(0f);
        tab.animate().alpha(1f).setDuration(170).start();
        if (tab instanceof MineView) ((MineView) tab).onShown();
        if (tab instanceof FeedView) ((FeedView) tab).onShown();
        applyChrome(index);
        nav.select(index, false);
    }

    private void applyChrome(int index) {
        if (index == 0) {
            Theme.darkStatusBar(this, android.graphics.Color.BLACK);
            nav.setDark(true);
            content.setBackgroundColor(android.graphics.Color.BLACK);
            getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        } else {
            Theme.lightStatusBar(this, Theme.SURFACE);
            nav.setDark(false);
            content.setBackgroundColor(Theme.BG);
            getWindow().setNavigationBarColor(Theme.SURFACE);
        }
    }

    private View createTab(int index) {
        switch (index) {
            case 0:
                return new FeedView(this, drama -> DetailActivity.open(this, drama));
            case 1:
                return new BrowseView(this);
            case 2:
                return new RankView(this);
            case 3:
                return new NewView(this);
            default:
                return new MineView(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current == 4 && tabs[4] instanceof MineView) {
            ((MineView) tabs[4]).onShown();
        }
        if (current == 0 && tabs[0] instanceof FeedView) {
            applyChrome(0);
            ((FeedView) tabs[0]).onShown();
        }
    }

    @Override
    protected void onPause() {
        if (current == 0 && tabs[0] instanceof FeedView) ((FeedView) tabs[0]).onHidden();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            openTab(0);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
        }
    }
}
