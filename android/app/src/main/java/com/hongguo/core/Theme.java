package com.hongguo.core;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Shared design tokens and small drawing helpers for the light Hongguo-style UI. */
public final class Theme {
    public static final int BRAND = 0xFFFF2C46;
    public static final int BRAND_DARK = 0xFFE01D36;
    public static final int BRAND_SOFT = 0xFFFFEDF0;
    public static final int BRAND_WARM = 0xFFFF7A45;
    public static final int GOLD = 0xFFFFB020;
    public static final int SILVER = 0xFF9AA6B2;
    public static final int BRONZE = 0xFFC08457;

    public static final int BG = 0xFFF5F6F8;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int SURFACE_ALT = 0xFFF2F3F5;
    public static final int DIVIDER = 0xFFEEF0F3;
    public static final int TEXT = 0xFF17181A;
    public static final int TEXT_SECONDARY = 0xFF6B7078;
    public static final int TEXT_TERTIARY = 0xFFA0A5AD;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    private static Typeface medium;
    private static Typeface black;
    private static Typeface regular;

    private Theme() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static float dpF(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    public static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static Typeface regular() {
        if (regular == null) regular = Typeface.create("sans-serif", Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium() {
        if (medium == null) medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        return medium;
    }

    public static Typeface black() {
        if (black == null) black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        return black;
    }

    public static GradientDrawable round(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable roundStroke(int color, int radiusPx, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = round(color, radiusPx);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    public static GradientDrawable gradient(int[] colors, int radiusPx, GradientDrawable.Orientation orientation) {
        GradientDrawable drawable = new GradientDrawable(orientation, colors);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    public static RippleDrawable ripple(int maskColor, final Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(maskColor), content, null);
    }

    public static void lightStatusBar(Activity activity, int color) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    public static void darkStatusBar(Activity activity, int color) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        decor.setSystemUiVisibility(flags);
    }

    public static void applyWindowBackground(Activity activity, int color) {
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(color));
        activity.getWindow().setStatusBarColor(color);
    }

    public static int blend(int from, int to, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.argb(a, r, g, b);
    }

    public static String formatCount(long value) {
        if (value >= 100_000_000L) {
            return trim(value / 100_000_000.0) + "亿";
        }
        if (value >= 10_000L) {
            return trim(value / 10_000.0) + "万";
        }
        return Long.toString(value);
    }

    private static String trim(double value) {
        if (value >= 100) return Long.toString(Math.round(value));
        String text = String.format(java.util.Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
