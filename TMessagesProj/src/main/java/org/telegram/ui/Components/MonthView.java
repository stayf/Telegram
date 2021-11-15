package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
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

    public static class VisibleDay {
        public int date;
        public int dayOfWeek;
        public int lastDayOfMonth;
        public RectF drawRegion = new RectF();
        public boolean isAvailable;
    }

    public interface Callback {

        boolean onCheckEnterItems();

        int getListViewHeight();

        void onLongPress(int time);

        void onSelectDate(int time);

        void onClick(PeriodDay periodDay);
    }

    private final int currentAccount = UserConfig.selectedAccount;

    SimpleTextView titleView;

    int currentYear;
    int currentMonthInYear;
    int daysInMonth;
    int startDayOfWeek;
    int cellCount;
    int startMonthTime;

    SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
    SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

    private final Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaintBold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint activeTextPaintWhite = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCyclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedCycleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedRangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    boolean attached;
    private Callback callback;
    private final SparseArray<VisibleDay> visibleDays = new SparseArray<>();
    private int selectedDateStart;
    private int selectedDateEnd;

    public MonthView(Context context) {
        super(context);
        setWillNotDraw(false);
        selectedCyclePaint.setColor(0xFF50A5E6);
        selectedCycleStrokePaint.setColor(0xFF50A5E6);
        selectedCycleStrokePaint.setStrokeWidth(AndroidUtilities.dp(2));
        selectedCycleStrokePaint.setStyle(Paint.Style.STROKE);

        selectedRangePaint.setColor(0x2650A5E6);

        activeTextPaintWhite.setTextSize(AndroidUtilities.dp(16));
        activeTextPaintWhite.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaintWhite.setTextAlign(Paint.Align.CENTER);
        activeTextPaintWhite.setColor(Color.WHITE);

        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        textPaintBold.setTextSize(AndroidUtilities.dp(16));
        textPaintBold.setTextAlign(Paint.Align.CENTER);
        textPaintBold.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaintBold.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        titleView = new SimpleTextView(context);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
    }

    public float selectRangeAnimationProgress;

    public void setSelectedDates(int selectedDateStart, int selectedDateEnd) {
        this.selectedDateStart = selectedDateStart;
        this.selectedDateEnd = selectedDateEnd;
        selectRangeAnimationProgress = 0;
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
        visibleDays.clear();
        for (int i = 0; i < daysInMonth; i++) {
            calendar.set(year, monthInYear, i + 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            VisibleDay visibleDay = new VisibleDay();
            visibleDay.date = (int) (calendar.getTimeInMillis() / 1000);
            visibleDay.dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            visibleDay.lastDayOfMonth = daysInMonth;
            visibleDays.put(i, visibleDay);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
    }

    boolean pressed;
    float pressedX;
    float pressedY;

    private Runnable longPressRunnable;

    private void cleanLongPressRunnable() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            cleanLongPressRunnable();
            pressed = true;
            pressedX = event.getX();
            pressedY = event.getY();
            longPressRunnable = () -> {
                if (pressed && callback != null) {
                    VisibleDay visibleDay = findVisibleDay();
                    if (visibleDay != null && visibleDay.isAvailable) {
                        callback.onLongPress(visibleDay.date);
                    }
                }
            };
            postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressed && callback != null) {
                PeriodDay periodDay = findPeriod();
                if (periodDay != null) {
                    callback.onClick(periodDay);
                }
                VisibleDay visibleDay = findVisibleDay();
                if (visibleDay != null && visibleDay.isAvailable) {
                    callback.onSelectDate(visibleDay.date);
                }
            }
            pressed = false;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            cleanLongPressRunnable();
            pressed = false;
        }
        return pressed;
    }

    private PeriodDay findPeriod() {
        if (imagesByDays != null) {
            for (int i = 0; i < imagesByDays.size(); i++) {
                if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                    return messagesByDays.valueAt(i);
                }
            }
        }
        return null;
    }

    private VisibleDay findVisibleDay() {
        for (int i = 0; i < visibleDays.size(); i++) {
            if (visibleDays.get(i).drawRegion.contains(pressedX, pressedY)) {
                return visibleDays.get(i);
            }
        }
        return null;
    }

    private final Path tmpPath = new Path();
    private final RectF tmpRectF = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cycleSize = AndroidUtilities.dp(44);
        float cycleRadius = cycleSize / 2f;

        int currentCell = 0;
        int currentColumn = startDayOfWeek;

        float xStep = getMeasuredWidth() / 7f;
        float yStep = AndroidUtilities.dp(44 + 8);

        for (int i = 0; i < daysInMonth; i++) {
            float cx = xStep * currentColumn + xStep / 2f;
            float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
            visibleDays.get(i).drawRegion.set(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(44) / 2f, cy + AndroidUtilities.dp(44) / 2f);

            final int currentDay = visibleDays.get(i).date;
            final boolean isSelectedStartOrEnd = (selectedDateStart != 0 && selectedDateStart == currentDay) || (selectedDateEnd != 0 && selectedDateEnd == currentDay);
            final boolean isSelectedBetweenDays = (selectedDateStart != 0 && selectedDateEnd != 0) && (currentDay > selectedDateStart && currentDay < selectedDateEnd);

            final float innerCycleRadius = cycleRadius - AndroidUtilities.dp(4);
            float scaleFactor = 1f;

            if (isSelectedStartOrEnd || isSelectedBetweenDays) {
                scaleFactor = innerCycleRadius / cycleRadius;
            }

            selectedRangePaint.setAlpha((int) (selectRangeAnimationProgress * 41));
            if (isSelectedStartOrEnd) {
                RectF drawRegion = visibleDays.get(i).drawRegion;
                canvas.drawCircle(cx, cy, innerCycleRadius, selectedCyclePaint);
                canvas.drawCircle(cx, cy, cycleRadius - AndroidUtilities.dp(1), selectedCycleStrokePaint);
                if (selectedDateStart != 0 && selectedDateEnd != 0) {
                    int dayOfWeek = visibleDays.get(i).dayOfWeek;
                    int lastDayOfMonth = visibleDays.get(i).lastDayOfMonth;
                    int dayOfMonth = i + 1;
                    if (selectedDateStart == currentDay) {
                        //справа
                        if (dayOfWeek != 1 && dayOfMonth != lastDayOfMonth) {
                            tmpPath.moveTo(cx + xStep / 2f, drawRegion.bottom);
                            tmpPath.lineTo(cx + xStep / 2f, drawRegion.top);
                            tmpPath.lineTo(cx, drawRegion.top);
                            tmpRectF.set(drawRegion.left, drawRegion.top, drawRegion.right, drawRegion.bottom);
                            tmpPath.arcTo(tmpRectF, -90, 180, false);
                            tmpPath.close();
                            canvas.drawPath(tmpPath, selectedRangePaint);
                        }
                    } else {
                        //слева
                        if (dayOfWeek != 2 && dayOfMonth != 1) {
                            tmpPath.moveTo(cx - xStep / 2f, drawRegion.bottom);
                            tmpPath.lineTo(cx - xStep / 2f, drawRegion.top);
                            tmpPath.lineTo(cx, drawRegion.top);
                            tmpRectF.set(drawRegion.left, drawRegion.top, drawRegion.right, drawRegion.bottom);
                            tmpPath.arcTo(tmpRectF, -90, -180, false);
                            tmpPath.close();
                            canvas.drawPath(tmpPath, selectedRangePaint);
                        }
                    }
                }
                tmpPath.reset();
            } else if (isSelectedBetweenDays) {
                RectF drawRegion = visibleDays.get(i).drawRegion;
                int dayOfWeek = visibleDays.get(i).dayOfWeek;
                if (dayOfWeek == 1) {
                    //вс
                    int dayOfMonth = i + 1;
                    if (dayOfMonth == 1) {
                        canvas.drawArc(drawRegion, 90, 180, true, selectedRangePaint);
                        canvas.drawArc(drawRegion, -90, 180, true, selectedRangePaint);
                    } else {
                        canvas.drawArc(drawRegion, -90, 180, true, selectedRangePaint);
                        canvas.drawRect(cx - xStep / 2f, drawRegion.top, cx, drawRegion.bottom, selectedRangePaint);
                    }
                } else if (dayOfWeek == 2) {
                    //пн
                    int lastDayOfMonth = visibleDays.get(i).lastDayOfMonth;
                    int dayOfMonth = i + 1;
                    if (dayOfMonth == lastDayOfMonth) {
                        canvas.drawArc(drawRegion, 90, 180, true, selectedRangePaint);
                        canvas.drawArc(drawRegion, -90, 180, true, selectedRangePaint);
                    } else {
                        canvas.drawArc(drawRegion, 90, 180, true, selectedRangePaint);
                        canvas.drawRect(cx, drawRegion.top, cx + xStep / 2f, drawRegion.bottom, selectedRangePaint);
                    }
                } else {
                    //нужно проверить является ли первым или последним днем месяца
                    int dayOfMonth = i + 1;
                    int lastDayOfMonth = visibleDays.get(i).lastDayOfMonth;
                    if (dayOfMonth == 1) {
                        canvas.drawArc(drawRegion, 90, 180, true, selectedRangePaint);
                        canvas.drawRect(cx, drawRegion.top, cx + xStep / 2f, drawRegion.bottom, selectedRangePaint);
                    } else if (dayOfMonth == lastDayOfMonth) {
                        canvas.drawArc(visibleDays.get(i).drawRegion, -90, 180, true, selectedRangePaint);
                        canvas.drawRect(cx - xStep / 2f, drawRegion.top, cx, drawRegion.bottom, selectedRangePaint);
                    } else {
                        canvas.drawRect(cx - xStep / 2f, drawRegion.top, cx + xStep / 2f, drawRegion.bottom, selectedRangePaint);
                    }
                }
            }

            int nowTime = (int) (System.currentTimeMillis() / 1000L);
            if (nowTime < startMonthTime + (i + 1) * 86400) {
                int oldAlpha = textPaint.getAlpha();
                textPaint.setAlpha((int) (oldAlpha * 0.3f));
                canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                textPaint.setAlpha(oldAlpha);
                visibleDays.get(i).isAvailable = false;
            } else {
                visibleDays.get(i).isAvailable = true;
                if (messagesByDays != null && messagesByDays.get(i, null) != null) {
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
                        if (alpha != 1f || isSelectedStartOrEnd || isSelectedBetweenDays) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            s = s * scaleFactor;
                            canvas.scale(s, s, cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f || isSelectedStartOrEnd || isSelectedBetweenDays) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaintWhite.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaintWhite);
                        activeTextPaintWhite.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaintWhite);
                    }
                } else {
                    if (isSelectedStartOrEnd) {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaintWhite);
                    } else if (isSelectedBetweenDays) {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaintBold);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                }
            }

            currentColumn++;
            if (currentColumn >= 7) {
                currentColumn = 0;
                currentCell++;
            }
        }
        if (selectedDateStart != 0 && selectedDateEnd != 0) {
            selectRangeAnimationProgress += 0.1f;
            if (selectRangeAnimationProgress > 1f) {
                selectRangeAnimationProgress = 1f;
            } else {
                invalidate();
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