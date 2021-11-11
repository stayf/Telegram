package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import java.time.YearMonth;
import java.util.Calendar;

public class MonthView extends FrameLayout {
    public static class PeriodDay {
        public MessageObject messageObject;
        public int startOffset;
        public float enterAlpha = 1f;
        public float startEnterDelay = 1f;
        public boolean wasDrawn;
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
        boolean onCheckEnterItems();
        int getListViewHeight();
    }

    private int currentAccount = UserConfig.selectedAccount;

    SimpleTextView titleView;

    int currentYear;
    int currentMonthInYear;
    int daysInMonth;
    int startDayOfWeek;
    int cellCount;
    int startMonthTime;

    SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
    SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

    SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
    SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    boolean attached;
    float animationProgress = 1f;
    private Callback callback;

    public MonthView(Context context) {
        super(context);
        setWillNotDraw(false);
        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView = new SimpleTextView(context);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
        boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
        currentYear = year;
        currentMonthInYear = monthInYear;
        this.messagesByDays = messagesByDays;

        if (dateChanged) {
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                    imagesByDays.valueAt(i).setParentView(null);
                }
                imagesByDays = null;
            }
        }
        if (messagesByDays != null) {
            if (imagesByDays == null) {
                imagesByDays = new SparseArray<>();
            }

            for (int i = 0; i < messagesByDays.size(); i++) {
                int key = messagesByDays.keyAt(i);
                if (imagesByDays.get(key, null) != null) {
                    continue;
                }
                ImageReceiver receiver = new ImageReceiver();
                receiver.setParentView(this);
                PeriodDay periodDay = messagesByDays.get(key);
                MessageObject messageObject = periodDay.messageObject;
                if (messageObject != null) {
                    if (messageObject.isVideo()) {
                        TLRPC.Document document = messageObject.getDocument();
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                        TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                        if (thumb == qualityThumb) {
                            qualityThumb = null;
                        }
                        if (thumb != null) {
                            if (messageObject.strippedThumb != null) {
                                receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                            } else {
                                receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                            }
                        }
                    } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                        TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                        TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                        if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                            if (currentPhotoObject == currentPhotoObjectThumb) {
                                currentPhotoObjectThumb = null;
                            }
                            if (messageObject.strippedThumb != null) {
                                receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                            } else {
                                receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                            }
                        } else {
                            if (messageObject.strippedThumb != null) {
                                receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                            } else {
                                receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                            }
                        }
                    }
                    receiver.setRoundRadius(AndroidUtilities.dp(22));
                    imagesByDays.put(key, receiver);
                }
            }
        }

        YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
        daysInMonth = yearMonthObject.lengthOfMonth();

        Calendar calendar = Calendar.getInstance();
        calendar.set(year, monthInYear, 0);
        startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
        startMonthTime = (int) (calendar.getTimeInMillis() / 1000L);

        int totalColumns = daysInMonth + startDayOfWeek;
        cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
        calendar.set(year, monthInYear + 1, 0);
        titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
    }

    boolean pressed;
    float pressedX;
    float pressedY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pressed = true;
            pressedX = event.getX();
            pressedY = event.getY();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressed) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                        //if (callback != null) {
                        PeriodDay periodDay = messagesByDays.valueAt(i);
                        //prepareBlurBitmap();
                        Bundle args = new Bundle();
                        //todo только личная переписка
                        //добавить блур
                        /*args.putLong("chat_id", -dialogId);
                        presentFragmentAsPreview(new ChatActivity(args));*/
                        //callback.onDateSelected(periodDay.messageObject.getId(), periodDay.startOffset);
                        //finishFragment();
                        break;
                        //}
                    }
                }
            }
            pressed = false;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            pressed = false;
        }
        return pressed;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int currentCell = 0;
        int currentColumn = startDayOfWeek;

        float xStep = getMeasuredWidth() / 7f;
        float yStep = AndroidUtilities.dp(44 + 8);
        for (int i = 0; i < daysInMonth; i++) {
            float cx = xStep * currentColumn + xStep / 2f;
            float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
            int nowTime = (int) (System.currentTimeMillis() / 1000L);
            if (nowTime < startMonthTime + (i + 1) * 86400) {
                int oldAlpha = textPaint.getAlpha();
                textPaint.setAlpha((int) (oldAlpha * 0.3f));
                canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                textPaint.setAlpha(oldAlpha);
            } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                float alpha = 1f;
                if (imagesByDays.get(i) != null) {
                    if (callback.onCheckEnterItems() && !messagesByDays.get(i).wasDrawn) {
                        messagesByDays.get(i).enterAlpha = 0f;
                        messagesByDays.get(i).startEnterDelay = (cy + getY()) / callback.getListViewHeight() * 150;
                    }
                    if (messagesByDays.get(i).startEnterDelay > 0) {
                        messagesByDays.get(i).startEnterDelay -= 16;
                        if (messagesByDays.get(i).startEnterDelay < 0) {
                            messagesByDays.get(i).startEnterDelay = 0;
                        } else {
                            invalidate();
                        }
                    }
                    if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                        messagesByDays.get(i).enterAlpha += 16 / 220f;
                        if (messagesByDays.get(i).enterAlpha > 1f) {
                            messagesByDays.get(i).enterAlpha = 1f;
                        } else {
                            invalidate();
                        }
                    }
                    alpha = messagesByDays.get(i).enterAlpha;
                    if (alpha != 1f) {
                        canvas.save();
                        float s = 0.8f + 0.2f * alpha;
                        canvas.scale(s, s, cx, cy);
                    }
                    imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                    imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                    imagesByDays.get(i).draw(canvas);
                    blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                    messagesByDays.get(i).wasDrawn = true;
                    if (alpha != 1f) {
                        canvas.restore();
                    }
                }
                if (alpha != 1f) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);

                    oldAlpha = textPaint.getAlpha();
                    activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    activeTextPaint.setAlpha(oldAlpha);
                } else {
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                }

            } else {
                canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
            }

            currentColumn++;
            if (currentColumn >= 7) {
                currentColumn = 0;
                currentCell++;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (imagesByDays != null) {
            for (int i = 0; i < imagesByDays.size(); i++) {
                imagesByDays.valueAt(i).onAttachedToWindow();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        if (imagesByDays != null) {
            for (int i = 0; i < imagesByDays.size(); i++) {
                imagesByDays.valueAt(i).onDetachedFromWindow();
            }
        }
    }

    public int getCurrentYear() {
        return currentYear;
    }

    public int getCurrentMonthInYear() {
        return currentMonthInYear;
    }

    public int getDaysInMonth() {
        return daysInMonth;
    }

    public int getStartDayOfWeek() {
        return startDayOfWeek;
    }

    public int getCellCount() {
        return cellCount;
    }

    public int getStartMonthTime() {
        return startMonthTime;
    }
}