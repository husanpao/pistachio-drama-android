package com.hongguo.core;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** Cover thumbnail with rounded clipping, optional poster aspect and async loading. */
public class CoverImageView extends ImageView {
    private int radiusPx;
    private float heightRatio;

    public CoverImageView(Context context) {
        super(context);
        init(context, 12, 0f);
    }

    public CoverImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, 12, 0f);
    }

    private void init(Context context, int radiusDp, float heightRatio) {
        this.radiusPx = Theme.dp(context, radiusDp);
        this.heightRatio = heightRatio;
        setScaleType(ScaleType.CENTER_CROP);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        setPlaceholder(0xFFE9EBEF, 0xFFDFE2E7);
    }

    public void setCornerRadius(int radiusDp) {
        this.radiusPx = Theme.dp(getContext(), radiusDp);
        invalidateOutline();
    }

    /** height = width * ratio; 0 disables the aspect override. */
    public void setHeightRatio(float ratio) {
        this.heightRatio = ratio;
        requestLayout();
    }

    public void setPlaceholder(int topColor, int bottomColor) {
        GradientDrawable drawable = Theme.gradient(
                new int[]{topColor, bottomColor}, radiusPx, GradientDrawable.Orientation.TL_BR);
        setImageDrawable(drawable);
    }

    public void load(String url) {
        ImageLoader.load(url, this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (heightRatio > 0) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = Math.round(width * heightRatio);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
