package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class MediaCalendarMainBtnView extends View {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int rippleColor;

    private StaticLayout textLayout;
    private StaticLayout textLayoutOut;
    private int layoutTextWidth;
    private final TextPaint layoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Drawable selectableBackground;

    ValueAnimator replaceAnimator;
    float replaceProgress = 1f;
    boolean animatedFromBottom;
    int textColor;
    int panelBackgroundColor;
    int counterColor;
    CharSequence lastText;

    public MediaCalendarMainBtnView(Context context) {
        super(context);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        layoutPaint.setTextSize(AndroidUtilities.dp(15));
        layoutPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
    }

    public void setText(CharSequence text, boolean animatedFromBottom) {
        if (lastText == text) {
            return;
        }
        lastText = text;
        this.animatedFromBottom = animatedFromBottom;
        textLayoutOut = textLayout;
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();

        if (textLayoutOut != null) {
            if (replaceAnimator != null) {
                replaceAnimator.cancel();
            }
            replaceProgress = 0;
            replaceAnimator = ValueAnimator.ofFloat(0, 1f);
            replaceAnimator.addUpdateListener(animation -> {
                replaceProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            replaceAnimator.setDuration(150);
            replaceAnimator.start();
        }
    }

    public void setText(CharSequence text) {
        layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
        textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        setContentDescription(text);
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (selectableBackground != null) {
            selectableBackground.setState(getDrawableState());
        }
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        if (selectableBackground != null) {
            return selectableBackground == drawable || super.verifyDrawable(drawable);
        }
        return super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (selectableBackground != null) {
            selectableBackground.jumpToCurrentState();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (textLayout != null) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(0));
                int contentWidth;
                if (getMeasuredWidth() == ((View) getParent()).getMeasuredWidth()) {
                    contentWidth = getMeasuredWidth() - AndroidUtilities.dp(96);
                } else {
                    contentWidth = lineWidth;
                    contentWidth += AndroidUtilities.dp(48);
                }
                int x = (getMeasuredWidth() - contentWidth) / 2;
                rect.set(
                        x, getMeasuredHeight() / 2f - contentWidth / 2f,
                        x + contentWidth, getMeasuredHeight() / 2f + contentWidth / 2f
                );
                if (!rect.contains(event.getX(), event.getY())) {
                    setPressed(false);
                    return false;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    public void updateColor(int color) {
        layoutPaint.setColor(textColor = color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int color = Theme.getColor(Theme.key_chat_messagePanelBackground);
        if (panelBackgroundColor != color) {
            textPaint.setColor(panelBackgroundColor = color);
        }
        color = Theme.getColor(Theme.key_chat_goDownButtonCounterBackground);
        if (counterColor != color) {
            paint.setColor(counterColor = color);
        }

        if (getParent() != null) {
            int contentWidth = getMeasuredWidth();
            int x = (getMeasuredWidth() - contentWidth) / 2;
            if (rippleColor != Theme.getColor(Theme.key_chat_fieldOverlayText) || selectableBackground == null) {
                selectableBackground = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(60), 0, ColorUtils.setAlphaComponent(rippleColor = Theme.getColor(Theme.key_chat_fieldOverlayText), 26));
                selectableBackground.setCallback(this);
            }
            int start = (getLeft() + x) <= 0 ? x - AndroidUtilities.dp(20) : x;
            int end = x + contentWidth > ((View) getParent()).getMeasuredWidth() ? x + contentWidth + AndroidUtilities.dp(20) : x + contentWidth;
            selectableBackground.setBounds(
                    start, getMeasuredHeight() / 2 - contentWidth / 2,
                    end, getMeasuredHeight() / 2 + contentWidth / 2
            );
            selectableBackground.draw(canvas);
        }
        if (textLayout != null) {
            canvas.save();
            if (replaceProgress != 1f && textLayoutOut != null) {
                int oldAlpha = layoutPaint.getAlpha();

                canvas.save();
                canvas.translate((getMeasuredWidth() - textLayoutOut.getWidth()) / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                canvas.translate(0, (animatedFromBottom ? -1f : 1f) * AndroidUtilities.dp(18) * replaceProgress);
                layoutPaint.setAlpha((int) (oldAlpha * (1f - replaceProgress)));
                textLayoutOut.draw(canvas);
                canvas.restore();

                canvas.save();
                canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                canvas.translate(0, (animatedFromBottom ? 1f : -1f) * AndroidUtilities.dp(18) * (1f - replaceProgress));
                layoutPaint.setAlpha((int) (oldAlpha * (replaceProgress)));
                textLayout.draw(canvas);
                canvas.restore();

                layoutPaint.setAlpha(oldAlpha);
            } else {
                canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                textLayout.draw(canvas);
            }

            canvas.restore();
        }
    }
}
