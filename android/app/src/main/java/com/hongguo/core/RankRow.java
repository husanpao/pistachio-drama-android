package com.hongguo.core;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One row of the leaderboard. */
public final class RankRow extends LinearLayout {
    private final TextView rank;
    private final CoverImageView cover;
    private final TextView title;
    private final TextView meta;
    private final TextView score;
    private Drama drama;

    public RankRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Theme.dp(context, 14), Theme.dp(context, 11),
                Theme.dp(context, 14), Theme.dp(context, 11));
        setBackgroundColor(Theme.SURFACE);
        setClickable(true);

        rank = Ui.bold(context, "1", 13, Color.WHITE);
        rank.setGravity(Gravity.CENTER);
        rank.setBackground(Theme.round(Theme.GOLD, Theme.dp(context, 8)));
        addView(rank, new LayoutParams(Theme.dp(context, 26), Theme.dp(context, 26)));

        cover = new CoverImageView(context);
        cover.setCornerRadius(8);
        LayoutParams coverParams = new LayoutParams(Theme.dp(context, 72), Theme.dp(context, 96));
        coverParams.leftMargin = Theme.dp(context, 12);
        addView(cover, coverParams);

        LinearLayout info = Ui.column(context);
        LayoutParams infoParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = Theme.dp(context, 12);
        addView(info, infoParams);

        title = Ui.medium(context, "", 15, Theme.TEXT);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(Theme.dp(context, 1), 1f);
        info.addView(title);

        meta = Ui.text(context, "", 11, Theme.TEXT_TERTIARY);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams metaParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Theme.dp(context, 6);
        info.addView(meta, metaParams);

        score = Ui.medium(context, "", 12, Theme.GOLD);
        LayoutParams scoreParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoreParams.topMargin = Theme.dp(context, 6);
        info.addView(score, scoreParams);

        ImageViewArrow arrow = new ImageViewArrow(context);
        addView(arrow, new LayoutParams(Theme.dp(context, 16), Theme.dp(context, 16)));

        setOnClickListener(v -> {
            if (drama != null) DetailActivity.open(context, drama);
        });
        Ui.pressable(this);
    }

    public void bind(Drama value, int position) {
        drama = value;
        int number = value.rank > 0 ? value.rank : position + 1;
        rank.setText(String.valueOf(number));
        if (number == 1) {
            rank.setBackground(Theme.round(Theme.GOLD, Theme.dp(getContext(), 8)));
            rank.setTextColor(Color.WHITE);
        } else if (number == 2) {
            rank.setBackground(Theme.round(Theme.SILVER, Theme.dp(getContext(), 8)));
            rank.setTextColor(Color.WHITE);
        } else if (number == 3) {
            rank.setBackground(Theme.round(Theme.BRONZE, Theme.dp(getContext(), 8)));
            rank.setTextColor(Color.WHITE);
        } else {
            rank.setBackground(null);
            rank.setTextColor(Theme.TEXT_TERTIARY);
        }
        title.setText(value.title);
        cover.load(value.coverUrl());
        String tag = value.primaryTag();
        String heat = value.heatLabel();
        String metaText;
        if (!tag.isEmpty() && !heat.isEmpty()) metaText = tag + "  ·  " + heat;
        else metaText = tag.isEmpty() ? heat : tag;
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? GONE : VISIBLE);
        if (!value.score.isEmpty()) {
            score.setText("评分 " + value.score);
            score.setVisibility(VISIBLE);
        } else {
            score.setVisibility(GONE);
        }
    }

    private static final class ImageViewArrow extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        ImageViewArrow(Context context) {
            super(context);
            paint.setColor(Theme.TEXT_TERTIARY);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(Theme.dpF(context, 1.6f));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(w * 0.36f, h * 0.24f);
            path.lineTo(w * 0.66f, h * 0.5f);
            path.lineTo(w * 0.36f, h * 0.76f);
            canvas.drawPath(path, paint);
        }
    }
}
