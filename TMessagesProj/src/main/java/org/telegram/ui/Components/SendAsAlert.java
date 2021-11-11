package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;

import java.util.ArrayList;

public class SendAsAlert {

    public interface Delegate {
        void popupOpened();

        void popupClosed(boolean force);
    }

    private static ArrayList<TLRPC.Peer> cachedChats;
    private static long lastCacheTime;
    private static long lastCacheDid;
    private static int lastCachedAccount;

    public static void clearCache() {
        cachedChats = null;
    }

    private RecyclerListView listView;
    private TLRPC.Peer selectedPeer;
    private ArrayList<TLRPC.Peer> chats;
    private final int currentAccount = UserConfig.selectedAccount;
    private ActionBarPopupWindow popupWindow;
    private boolean isLoading;
    private Delegate delegate;
    private AccountInstance accountInstance;
    private long dialogId;
    private TLRPC.ChatFull info;

    public void open(Context context, long dialogId, AccountInstance accountInstance, TLRPC.Peer currentPeer, View parent, Delegate delegate, TLRPC.ChatFull info) {
        if (context == null || isLoading || delegate == null) {
            return;
        }
        this.delegate = delegate;
        this.accountInstance = accountInstance;
        this.dialogId = dialogId;
        this.info = info;
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == dialogId && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 2 * 60 * 1000) {
            showAlert(context, cachedChats, currentPeer, parent);
        } else {
            isLoading = true;
            final AlertDialog progressDialog = new AlertDialog(context, 3);
            TLRPC.TL_channels_getSendAs req = new TLRPC.TL_channels_getSendAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(dialogId);
            int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                isLoading = false;
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response != null) {
                    TLRPC.TL_channels_sendAsPeers res = (TLRPC.TL_channels_sendAsPeers) response;
                    cachedChats = res.peers;
                    lastCacheDid = dialogId;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    showAlert(context, res.peers, currentPeer, parent);
                }
            }));
            progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(500);
            } catch (Exception ignore) {

            }
        }
    }

    private void showAlert(Context context, ArrayList<TLRPC.Peer> peers, TLRPC.Peer currentPeer, View parent) {
        if (parent == null || parent.getContext() == null) {
            return;
        }
        chats = new ArrayList<>(peers);
        selectedPeer = currentPeer;

        LinearLayout containerLayout = new LinearLayout(context);
        containerLayout.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        containerLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 16, 16, 16, 12));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        textView.setText(LocaleController.getString("SendMessageAsTitle", R.string.SendMessageAsTitle));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));

        Drawable shadowDrawable3 = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable3.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        containerLayout.setBackground(shadowDrawable3);

        listView.setOnItemClickListener((view, position) -> {
            if (chats.get(position) == selectedPeer) {
                return;
            }
            selectedPeer = chats.get(position);
            if (view instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) view).setChecked(true, true);
            }
            for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                View child = listView.getChildAt(a);
                if (child != view && view instanceof GroupCreateUserCell) {
                    ((GroupCreateUserCell) child).setChecked(false, true);
                }
            }
            accountInstance.getMessagesController().updateSendUs(dialogId, selectedPeer, info, () -> {
                dismiss(false);
                clearCache();
            });
        });

        containerLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        popupWindow = new ActionBarPopupWindow(containerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (popupWindow != this) {
                    return;
                }
                popupWindow = null;
                delegate.popupClosed(false);
            }
        };
        popupWindow.setPauseNotifications(false);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.getContentView().setFocusableInTouchMode(true);

        int[] location = new int[2];
        parent.getLocationInWindow(location);

        float maxHeight = AndroidUtilities.displaySize.y * 0.65f;
        int availableHeight = location[1] - AndroidUtilities.dp(23) - AndroidUtilities.statusBarHeight;

        int measuredHeightLimit;
        if (availableHeight > maxHeight || availableHeight < AndroidUtilities.dp(160)) {
            measuredHeightLimit = (int) maxHeight;
        } else {
            measuredHeightLimit = availableHeight;
        }

        containerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(measuredHeightLimit, View.MeasureSpec.AT_MOST));

        int textViewMeasuredHeight = (textView.getMeasuredHeight() + AndroidUtilities.dp(28));
        int containerMeasuredHeight = containerLayout.getMeasuredHeight();
        listView.getLayoutParams().height = containerMeasuredHeight - textViewMeasuredHeight;
        popupWindow.showAtLocation(parent, Gravity.LEFT | Gravity.TOP, location[0] - AndroidUtilities.dp(10), location[1] - AndroidUtilities.dp(23) - containerMeasuredHeight);
        delegate.popupOpened();
    }

    public void dismiss(boolean force) {
        if (popupWindow != null && popupWindow.isShowing()) {
            delegate.popupClosed(true);
            popupWindow.dismiss(!force);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new GroupCreateUserCell(context, 2, 0, false, false);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            long did = MessageObject.getPeerId(selectedPeer);
            if (holder.itemView instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                Object object = cell.getObject();
                long id = 0;
                if (object != null) {
                    if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = ((TLRPC.User) object).id;
                    }
                }
                cell.setChecked(did == id, false);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            long did = MessageObject.getPeerId(chats.get(position));
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }
            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            cell.setObject(object, null, status, false);
        }
    }
}
