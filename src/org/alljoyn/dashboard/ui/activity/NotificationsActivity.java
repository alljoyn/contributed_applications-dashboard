/*
* Copyright AllSeen Alliance. All rights reserved.
*
*    Permission to use, copy, modify, and/or distribute this software for any
*    purpose with or without fee is hereby granted, provided that the above
*    copyright notice and this permission notice appear in all copies.
*
*    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
*    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
*    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
*    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
*    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
*    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
*    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/
package org.alljoyn.dashboard.ui.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper;
import org.alljoyn.dashboard.ui.adapter.NotificationsAdapter;
import org.alljoyn.dashboard.ui.application.NativeNotificationHandler;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class NotificationsActivity extends BaseActivity {

    private final String TAG = "NotificationsActivity";

    // //////////////////////////////////////////
    // /////////// UI ELEMENTS //////////////////
    // //////////////////////////////////////////

    // NAV TITLE
    private final TextView m_navTitleText = null;

    // FEED
    private ListView m_feedList = null;
    private TextView m_feedEmptyText1 = null;
    private TextView m_feedEmptyText2 = null;
    private Button m_deleteButton = null;
    private Button m_dsimmissButton = null;

    // GENERAL
    private ViewGroup m_navigationLayout = null;

    private UUID m_deviceId = null;
    // //////////////////////////////////////////
    // /////////// UI ELEMENTS END //////////////
    // //////////////////////////////////////////

    private boolean isOriginDeviceDetailsActivity = false;
    private boolean isReceiverRegistered = false;
    private NotificationsAdapter notificationAdapter;

    private boolean isMultipleSelectionActivated = false;

    private final BroadcastReceiver m_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED.equals(action) || IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED.equals(action)) {
                UUID notificationUuid = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                if (m_deviceId != null && !m_deviceId.equals(notificationUuid)) {
                    // Don't refresh !!
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateNotificationList();
                    }
                });
            } else if (IntentActions.AJ_ON_SHOW_TOAST_ACTION.equals(action)) {
                if (m_isShowing) {
                    final String msg = intent.getStringExtra(IntentExtraKeys.EXTRA_TOAST_MSG);
                    UIUtil.showToast(NotificationsActivity.this, msg);
                }
            }
        }
    };

    public boolean isMultipleSelectionActivated() {
        return isMultipleSelectionActivated;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra(IntentExtraKeys.EXTRA_DEVICE_ID)) {
            m_deviceId = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        }

        if (getIntent().hasExtra(IntentExtraKeys.EXTRA_ACTIVITY_ORIGIN)) {
            String origin = getIntent().getStringExtra(IntentExtraKeys.EXTRA_ACTIVITY_ORIGIN);
            if (origin.equals("DeviceDetailsActivity")) {
                isOriginDeviceDetailsActivity = true;
            }

        }

        // /////////////////////////////////////////////////////////////
        m_navigationLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_notifications, null);

        // NAV TITLE
        // m_navTitleText = (TextView)
        // m_navigationLayout.findViewById(R.id.navtitlemenu_text);
        // setup nav elements
        final OnClickListener onBack = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                markGoingBack();
                if (isOriginDeviceDetailsActivity) {
                    finish();
                } else {
                    startActivity(new Intent(NotificationsActivity.this, NearbyDevicesActivity.class));
                }

            }
        };
        // m_navTitleText.setOnClickListener(onBack);

        ImageView v = (ImageView) m_navigationLayout.findViewById(R.id.navtitleback_help_button);
        v.setBackgroundResource(R.drawable.manage_devices_edit_icon);
        v.setVisibility(View.VISIBLE);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMultipleSelectionActivated) {
                    isMultipleSelectionActivated = false;
                    m_navigationLayout.findViewById(R.id.button_layout).setVisibility(View.GONE);
                } else {
                    isMultipleSelectionActivated = true;
                    if (notificationAdapter.getCount() > 0) {
                        m_navigationLayout.findViewById(R.id.button_layout).setVisibility(View.VISIBLE);
                    }
                }
                updateNotificationList();
            }
        });

        m_deleteButton = (Button) m_navigationLayout.findViewById(R.id.delete);
        m_deleteButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                List<Integer> list = notificationAdapter.getSelectedNotificationsMessageIdList();
                for (final Integer messageID : list) {
                    Log.d(TAG, String.format("Delete Notofication with messageID=%d", messageID));
                    NotificationsManagerImpl.getInstance().removeNotificationByMessageID(messageID);

                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateNotificationList();
                    }
                });
            }
        });

        m_dsimmissButton = (Button) m_navigationLayout.findViewById(R.id.dismiss);
        m_dsimmissButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Integer> list = notificationAdapter.getSelectedNotificationsMessageIdList();

                for (Integer messageID : list) {
                    Log.d(TAG, String.format("Dismiss Notofication with messageID=%d", messageID));
                    NotificationWrapper notificationWrapper = NotificationsManagerImpl.getInstance().getNotificationByMessageID(messageID);
                    if (notificationWrapper != null && notificationWrapper.getNotification() != null) {
                        notificationWrapper.getNotification().dismiss();
                    }
                }

            }
        });

        View view = m_navigationLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);

        TextView m_titleText = (TextView) m_navigationLayout.findViewById(R.id.navtitleback_title_text);
        m_titleText.setText(R.string.navmenu_feed);

        // FEED
        m_feedList = (ListView) m_navigationLayout.findViewById(R.id.notification_list);
        m_feedEmptyText1 = (TextView) m_navigationLayout.findViewById(R.id.notification_text_empty_1);
        m_feedEmptyText2 = (TextView) m_navigationLayout.findViewById(R.id.notification_text_empty_2);

        notificationAdapter = new NotificationsAdapter(this, new ArrayList<NotificationWrapper>());

        m_feedList.setAdapter(notificationAdapter);

        // /////////////////////////////////////////////////////////////

        setContentView(m_navigationLayout);
        updateNotificationList();

    }

    @Override
    public void onResume() {
        super.onResume();

        if (m_receiver != null && !isReceiverRegistered) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED);
            filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED);
            filter.addAction(IntentActions.AJ_ON_SHOW_TOAST_ACTION);

            registerReceiver(m_receiver, filter);
            isReceiverRegistered = true;
        }

        NativeNotificationHandler.clearNativeNotificationBar(this);

        NotificationsManagerImpl.getInstance().markAllNotificationsAsRead(m_deviceId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (m_receiver != null) {
            isReceiverRegistered = false;
            unregisterReceiver(m_receiver);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    public void resetFocus() {

        if (m_feedList != null) {
            m_feedList.setSelected(false);
            m_feedList.clearFocus();
            m_feedList.setPressed(false);

            View v;
            for (int i = 0; i < m_feedList.getCount(); i++) {
                v = m_feedList.getChildAt(i);
                if (v != null) {
                    v.setSelected(false);
                    v.clearFocus();
                    v.setPressed(false);
                }
            }
        }
    }

    private void updateNotificationList() {
        // if m_deviceId == null, will return the full list
        final ArrayList<NotificationWrapper> feedArrayList = NotificationsManagerImpl.getInstance().getNotificationList(m_deviceId);

        if (feedArrayList != null && m_feedEmptyText1 != null && m_feedEmptyText2 != null) {
            if (feedArrayList.size() > 0) {
                m_feedEmptyText1.setVisibility(View.INVISIBLE);
                m_feedEmptyText2.setVisibility(View.INVISIBLE);
                m_feedList.setVisibility(View.VISIBLE);
                notificationAdapter.updateData(feedArrayList, m_deviceId);

            } else {
                m_feedEmptyText1.setVisibility(View.VISIBLE);
                m_feedEmptyText2.setVisibility(View.VISIBLE);
                m_feedList.setVisibility(View.INVISIBLE);
                m_navigationLayout.findViewById(R.id.button_layout).setVisibility(View.GONE);
                isMultipleSelectionActivated = false;
            }
        }

    }

    @Override
    public void onBackPressed() {
        markGoingBack();
        if (isOriginDeviceDetailsActivity) {
            finish();
        } else {
            startActivity(new Intent(NotificationsActivity.this, NearbyDevicesActivity.class));
            finish();
        }
    }

}
