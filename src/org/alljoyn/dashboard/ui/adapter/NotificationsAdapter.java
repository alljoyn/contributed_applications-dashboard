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
package org.alljoyn.dashboard.ui.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper;
import org.alljoyn.dashboard.ui.activity.NotificationPopUpActivity;
import org.alljoyn.dashboard.ui.activity.NotificationsActivity;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

public class NotificationsAdapter extends BaseAdapter {
    public static final String TIME_FORMAT = "h:mm aa";

    Activity m_activity = null;
    ViewGroup m_rowView = null;
    ImageView m_icon = null;
    TextView m_desc = null;
    TextView m_name = null;
    TextView m_time = null;
    CheckBox m_checkbox = null;
    ImageView m_notfication_action = null;

    private final LayoutInflater inflater;
    private boolean m_isFullList = false;
    private final List<Integer> selectedNotifcationList = new ArrayList<Integer>();

    private List<NotificationWrapper> m_values;

    public NotificationsAdapter(Activity activity, List<NotificationWrapper> values) {

        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.m_values = values;
        m_activity = activity;
        // m_activity = activity;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        NotificationWrapper m_ajNotification = (NotificationWrapper) getItem(position);
        UUID  deviceUUID = m_ajNotification.getNotification().getAppId();


        m_rowView = (ViewGroup) inflater.inflate(R.layout.list_item_notification, parent, false);
        m_rowView.setTag(R.string.device_id, deviceUUID);

        m_icon = (ImageView) m_rowView.findViewById(R.id.notification_item_icon);
        m_desc = (TextView) m_rowView.findViewById(R.id.notification_item_desc);
        m_name = (TextView) m_rowView.findViewById(R.id.notification_item_name);
        m_time = (TextView) m_rowView.findViewById(R.id.notification_item_time);
        m_notfication_action = (ImageView) m_rowView.findViewById(R.id.notification_action_item);
        m_checkbox = (CheckBox) m_rowView.findViewById(R.id.checkbox);

        OnClickListener click = new OnClickListener() {
            @Override
            public void onClick(View view) {

                final Runnable launchDeviceDetails = new Runnable() {
                    @Override
                    public void run() {

                        final Intent launchIntent = new Intent(m_activity, NotificationPopUpActivity.class);
                        if (getItem(position)!=null){
                            if (!m_isFullList) {
                                launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, ((NotificationWrapper) getItem(position)).getNotification().getAppId());
                            }
                            launchIntent.putExtra(IntentExtraKeys.NOTIFICATION_ID, ((NotificationWrapper) getItem(position)).getNotification().getMessageId());
                        }
                        launchIntent.putExtra(IntentExtraKeys.EXTRA_SWIPE_ALLOWED, true);
                        m_activity.startActivity(launchIntent);

                        // m_activity.markGoingToNewPage();
                    }
                };
                launchDeviceDetails.run();

            }
        };

        if (((NotificationsActivity) m_activity).isMultipleSelectionActivated()) {
            m_checkbox.setVisibility(View.VISIBLE);
            Integer messageID = new Integer(m_ajNotification.getNotification().getMessageId());
            if (selectedNotifcationList.contains(messageID)) {
                m_checkbox.setChecked(true);
            }

        } else {
            m_checkbox.setVisibility(View.GONE);
            m_rowView.setOnClickListener(click);
        }

        m_checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (((NotificationWrapper) getItem(position)) != null && ((NotificationWrapper) getItem(position)).getNotification() != null) {
                    Integer messageID = Integer.valueOf(((NotificationWrapper) getItem(position)).getNotification().getMessageId());
                    if (buttonView.isChecked()) {
                        if (!selectedNotifcationList.contains(messageID)) {
                            selectedNotifcationList.add(messageID);
                        }
                    } else {
                        if (selectedNotifcationList.contains(messageID)) {
                            selectedNotifcationList.remove(messageID);
                        }
                    }
                }
            }
        });

        String responseObjectPath = m_ajNotification.getNotification().getResponseObjectPath();

        if (((responseObjectPath != null) && !responseObjectPath.isEmpty())) {
            m_notfication_action.setVisibility(View.VISIBLE);
        } else {
            m_notfication_action.setVisibility(View.GONE);
        }
        // set the device/notification data
        UIUtil.setDeviceIcon(m_icon, deviceUUID);
        final Device device = DeviceManagerImpl.getInstance().getDevice(deviceUUID);
        m_desc.setText(UIUtil.getNotificationTextAsString(m_ajNotification.getNotification()));
        m_name.setText((device != null) ? device.getFriendlyName() : m_ajNotification.getNotification().getDeviceName());
        m_time.setText(DateFormat.format(TIME_FORMAT, m_ajNotification.getTime()).toString().toLowerCase());

        return m_rowView;
    }

    public void updateData(List<NotificationWrapper> values, UUID deviceID) {
        this.m_values = values;
        if (deviceID == null) {
            m_isFullList = true;
        }

        // selectedNotifcationList.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return m_values.size();
    }

    @Override
    public Object getItem(int position) {
        if (m_values == null || m_values.size() == 0) {
            return null;
        } else {
            return m_values.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        // TODO Auto-generated method stub
        return position;
    }

    public List<Integer> getSelectedNotificationsMessageIdList() {
        return selectedNotifcationList;

    }

}
