package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaCalendarMainBtnView;
import org.telegram.ui.Components.MonthView;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;

import java.util.ArrayList;
import java.util.Calendar;

public class MediaCalendarActivity extends BaseFragment {

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    FrameLayout contentView;
    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;
    int startFromYear;
    int startFromMonth;
    int monthCount;
    CalendarAdapter adapter;
    Callback callback;
    SparseArray<SparseArray<MonthView.PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private int photosVideosTypeFilter;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;
    private FrameLayout bottomOverlay;
    private MediaCalendarMainBtnView bottomOverlayText;
    private NumberTextView selectedDaysCountTextView;
    private ArrayList<View> actionModeViews = new ArrayList<>();

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate) {
        super(args);
        this.photosVideosTypeFilter = photosVideosTypeFilter;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 48));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed()) {
                    finishFragment();
                }
            }
        });

        bottomOverlay = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int allWidth = MeasureSpec.getSize(widthMeasureSpec);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bottomOverlayText.getLayoutParams();
                layoutParams.width = allWidth;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.getThemePaint(Theme.key_paint_chatComposeBackground));
            }
        };
        bottomOverlay.setWillNotDraw(false);
        bottomOverlay.setPadding(0, AndroidUtilities.dp(1.5f), 0, 0);
        bottomOverlay.setVisibility(View.INVISIBLE);
        bottomOverlay.setClipChildren(false);
        contentView.addView(bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

        bottomOverlayText = new MediaCalendarMainBtnView(context);
        bottomOverlay.addView(bottomOverlayText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 1.5f, 0, 0));

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        loadNext();
        updateColors();

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        //actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        //actionBar.setAllowOverlayTitle(true);
        // actionBar.setActionModeOverrideColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        createActionMode();

        bottomOverlay.setVisibility(View.VISIBLE);
        bottomOverlayText.setText("SELECT DAYS");

        bottomOverlayText.setOnClickListener(v -> {
            if (!actionBar.isActionModeShowed()) {
                selectedDaysCountTextView.setNumber(0, false);
                bottomOverlayText.updateColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
                bottomOverlayText.setText("CLEAR HISTORY", true);
                actionBar.showActionMode(true);
            } else {
                /*bottomOverlayText.updateColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                bottomOverlayText.setText("SELECT DAYS", false);
                actionBar.hideActionMode();*/
                selectedDaysCountTextView.setNumber(1, true);
            }
        });

        return fragmentView;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createActionMode() {
        final ActionBarMenu actionMode = actionBar.createActionMode(false, "CalendarSelector");
        //actionMode.setBackground(null);

        selectedDaysCountTextView = new NumberTextView(actionMode.getContext());
        selectedDaysCountTextView.setTextSize(18);
        selectedDaysCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDaysCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        selectedDaysCountTextView.setZeroText("Select Days");
        //todo переделать на получение значения из ресурсов
        selectedDaysCountTextView.setSuffix(" Days");
        actionMode.addView(selectedDaysCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDaysCountTextView.setOnTouchListener((v, event) -> true);
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), true);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), true);
        bottomOverlayText.updateColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
    }

    @Override
    public boolean onBackPressed() {
        if (actionBar.isActionModeShowed()) {
            actionBar.hideActionMode();
            return false;
        }
        return super.onBackPressed();
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<MonthView.PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    MonthView.PeriodDay periodDay = new MonthView.PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).getCurrentYear() * 100 + ((MonthView) child).getCurrentMonthInYear();
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        }
        ;
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.getCurrentYear() == year && monthView.getCurrentMonthInYear() == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
            monthView.setCallback(new MonthView.Callback() {
                @Override
                public void onDateSelected(int messageId, int startOffset) {

                }

                @Override
                public boolean onCheckEnterItems() {
                    return checkEnterItems;
                }

                @Override
                public int getListViewHeight() {
                    return listView.getMeasuredHeight();
                }
            });
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = this::updateColors;
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);

        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
