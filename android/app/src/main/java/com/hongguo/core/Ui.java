package com.hongguo.core;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

/** Small factory helpers so the custom screens stay declarative and consistent. */
public final class Ui {
    private Ui() {
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Theme.regular());
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView medium(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color);
        view.setTypeface(Theme.medium());
        return view;
    }

    public static TextView bold(Context context, String value, float sizeSp, int color) {
        TextView view = text(context, value, sizeSp, color);
        view.setTypeface(Theme.black());
        return view;
    }

    public static TextView black(Context context, String value, float sizeSp, int color) {
        return bold(context, value, sizeSp, color);
    }

    public static TextView title(Context context, String value) {
        TextView view = medium(context, value, 19, Theme.TEXT);
        view.setLetterSpacing(0.01f);
        return view;
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static FrameLayout frame(Context context) {
        return new FrameLayout(context);
    }

    public static View space(Context context, int dp) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, Theme.dp(context, dp)));
        return space;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(Theme.DIVIDER);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(context, 0.6f))));
        return view;
    }

    public static void padding(View view, int left, int top, int right, int bottom) {
        Context context = view.getContext();
        view.setPadding(Theme.dp(context, left), Theme.dp(context, top),
                Theme.dp(context, right), Theme.dp(context, bottom));
    }

    public static void margin(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            Context context = view.getContext();
            ((ViewGroup.MarginLayoutParams) params).setMargins(Theme.dp(context, left),
                    Theme.dp(context, top), Theme.dp(context, right), Theme.dp(context, bottom));
            view.setLayoutParams(params);
        }
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(int width, int height,
                                               int left, int top, int right, int bottom,
                                               Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(Theme.dp(context, left), Theme.dp(context, top),
                Theme.dp(context, right), Theme.dp(context, bottom));
        return params;
    }

    public static LinearLayout.LayoutParams weighted(int width, int height, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        return params;
    }

    public static FrameLayout.LayoutParams flp(int width, int height, int gravity) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = gravity;
        return params;
    }

    public static ImageView icon(Context context, int res, int sizeDp, int tint) {
        ImageView view = new ImageView(context);
        view.setImageResource(res);
        view.setColorFilter(tint);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                Theme.dp(context, sizeDp), Theme.dp(context, sizeDp)));
        return view;
    }

    public static TextView chip(Context context, String label, boolean selected) {
        TextView view = medium(context, label, 13, selected ? Theme.BRAND : Theme.TEXT_SECONDARY);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        padding(view, 14, 7, 14, 7);
        view.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    public static void selectChip(TextView view, boolean selected) {
        view.setTextColor(selected ? Theme.BRAND : Theme.TEXT_SECONDARY);
        view.setTypeface(selected ? Theme.medium() : Theme.regular());
        view.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
    }

    public static void ripple(View view) {
        Drawable background = view.getBackground();
        view.setBackground(Theme.ripple(0x1A000000, background));
    }

    public static void onClick(View view, Runnable action) {
        view.setOnClickListener(v -> action.run());
    }

    public static void pressable(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(130).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    public static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static TextView badge(Context context, String label, int backgroundRes, int color) {
        TextView view = medium(context, label, 10, color);
        view.setBackgroundResource(backgroundRes);
        padding(view, 6, 2, 6, 2);
        return view;
    }

    public static TextView pill(Context context, String label, int background, int color) {
        TextView view = medium(context, label, 11, color);
        GradientDrawable bg = Theme.round(background, Theme.dp(context, 8));
        view.setBackground(bg);
        padding(view, 6, 3, 6, 3);
        return view;
    }

    public static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    public static int screenHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : Theme.dp(context, 24);
    }

    public static void setFontFeature(TextView view) {
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public static LinearLayout sectionHeader(Context context, String title, String subtitle,
                                             String action, Runnable actionClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Theme.dp(context, 6), 0, Theme.dp(context, 10));
        TextView titleView = medium(context, title, 17.5f, Theme.TEXT);
        row.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = text(context, subtitle, 11, Theme.TEXT_TERTIARY);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleParams.leftMargin = Theme.dp(context, 8);
            row.addView(subtitleView, subtitleParams);
        }
        View spacer = new View(context);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (action != null && !action.isEmpty()) {
            TextView actionView = text(context, action, 12, Theme.TEXT_TERTIARY);
            row.addView(actionView);
            ImageView arrow = icon(context, R.drawable.ic_arrow_right, 13, Theme.TEXT_TERTIARY);
            row.addView(arrow);
            if (actionClick != null) onClick(row, actionClick);
        }
        return row;
    }
}
