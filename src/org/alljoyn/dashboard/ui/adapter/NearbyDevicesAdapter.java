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

import java.util.List;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.ui.activity.NearbyDevicesActivity;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class NearbyDevicesAdapter extends ArrayAdapter<Device> {

    final String TAG = "DASHBOARD_NearbyDevicesAdapter";

    NearbyDevicesActivity m_activity = null;
    ViewGroup m_rowView = null;
    // RelativeLayout m_item = null;
    TextView m_title = null;
    ImageView m_icon = null;
    ImageView m_statusIcon = null;
    TextView m_statusText = null;

    private static UUID m_resetUUID;

    private View m_resetDeviceLayout;
    private ImageView m_resetImage;

    public NearbyDevicesAdapter(Activity activity, int textViewResourceId, List<Device> objects) {
        super(activity, textViewResourceId, objects);

        m_activity = (NearbyDevicesActivity) activity;
    }

    public void endResetMode() {
        m_resetUUID = null;
    }

    public boolean isResetMode() {
        return m_resetUUID != null;
    }
    @Override
    public  void notifyDataSetChanged()
    {
        super.notifyDataSetChanged();
        StringBuffer buff = new StringBuffer();
        int count = getCount();
        if (count == 0) {
            return;
        }
        for (int i = 0; i < count; i++){
            buff.append(getItem(i).getFriendlyName());
            buff.append(",");
        }
        Log.i(TAG, "Device list: "+(buff.length()>0?buff.toString().substring(0,buff.length()-1):" empty"));
    }
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Device device = getItem(position);
        m_rowView = (ViewGroup) inflater.inflate(R.layout.list_item_nearby_device, parent, false);
        // m_item = (RelativeLayout)
        // m_rowView.findViewById(R.id.my_devices_item);
        m_icon = (ImageView) m_rowView.findViewById(R.id.nearby_devices_item_icon);
        m_title = (TextView) m_rowView.findViewById(R.id.nearby_devices_item_title);
        m_statusIcon = (ImageView) m_rowView.findViewById(R.id.nearby_devices_item_status_icon);
        m_statusText = (TextView) m_rowView.findViewById(R.id.nearby_devices_item_status_text);

        m_resetImage = (ImageView) m_rowView.findViewById(R.id.nearby_devices_item_reset_icon);
        m_resetDeviceLayout = m_rowView.findViewById(R.id.nearby_devices_item_reset_layout);
        if (m_resetUUID != null && device.getId() != null && device.getId().equals(m_resetUUID)) {
            m_resetDeviceLayout.setVisibility(View.VISIBLE);
        } else {
            m_resetDeviceLayout.setVisibility(View.INVISIBLE);
        }
        // set the correct icon
        UIUtil.setDeviceIcon(m_icon, device.getId());

        if (device.getStatus().equals(DeviceStatus.AVAILABLE) || (device.getAPWifiInfo() != null && !device.getFriendlyName().equals(device.getAPWifiInfo().getSSID()))) {
            // if the device has been onboarded, display its friendly name as is
            m_title.setText(device.getFriendlyName());
        } else {
            // if we haven't onboarded the device, the friendly name will be set
            // to
            // its SSID - in this case we always want to remove the "AP_" before
            // displaying it
            m_title.setText(UIUtil.getUserFriendlyAP(device.getFriendlyName()));
        }

        final DeviceStatus status = UIUtil.getDeviceStatus(m_activity, device);
        m_statusText.setText(UIUtil.getDeviceStatusTextId(status));
        m_statusIcon.setImageResource(UIUtil.getDeviceStatusIconId(status));

        if (device.getStatus().equals(DeviceStatus.CONFIGURING)) {
            Animation mAnimation = new AlphaAnimation(1, 0);
            mAnimation.setDuration(400);
            mAnimation.setRepeatCount(Animation.INFINITE);
            mAnimation.setRepeatMode(Animation.REVERSE);
            m_statusIcon.startAnimation(mAnimation);
        } else {
            m_statusIcon.clearAnimation();
        }

        m_rowView.setOnLongClickListener(onLongClick);

        m_rowView.setClickable(true);
        m_rowView.setOnClickListener(onClick);

        m_rowView.setTag(R.string.device_id, device.getId());
        m_rowView.setTag(R.string.device_ssid, device.getAPWifiInfo() == null ? "" : device.getAPWifiInfo().getSSID());
        m_resetImage.setTag(R.string.device_id, device.getId());

        return m_rowView;
    }

    final OnLongClickListener onLongClick = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {

            final UUID deviceId = (UUID) v.getTag(R.string.device_id);
            m_resetUUID = deviceId;
            if (deviceId == null) {
                return false;
            }
            Device current = DeviceManagerImpl.getInstance().getDevice(deviceId);
            if (current == null) {
                return false;
            }
            current.setTag(UIUtil.DEVICE_TAG_SHOW_RESET_LAYOUT, true);
            m_resetDeviceLayout = v.findViewById(R.id.nearby_devices_item_reset_layout);
            // m_resetImage.setTag(R.string.device_id, current.getId());
            m_resetDeviceLayout.setVisibility(View.VISIBLE);
            m_resetImage = (ImageView) v.findViewById(R.id.nearby_devices_item_reset_icon);
            m_resetImage.setOnClickListener(onResetClick);
            return true;
        }
    };

    final OnClickListener onResetClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // repaint the item with the overlay removed
            m_resetUUID = null;
            notifyDataSetChanged();

            final UUID deviceId = (UUID) v.getTag(R.string.device_id);
            if (deviceId == null) {
                return;
            }
            final Device current = DeviceManagerImpl.getInstance().getDevice(deviceId);
            if (current == null) {
                return;
            }
            current.setTag(UIUtil.DEVICE_TAG_SHOW_RESET_LAYOUT, false);
            if (current.getStatus().equals(DeviceStatus.UNAVAILABLE)) {
                m_activity.launchFactoryResetError(deviceId);
            } else {
                m_activity.launchFactoryResetPrompt(deviceId);
            }
        }
    };

    final OnClickListener onClick = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (m_resetUUID != null) {
                m_resetUUID = null;
                notifyDataSetChanged();
                return;
            }

            final UUID deviceId = (UUID) view.getTag(R.string.device_id);
            final String deviceSSID = (String) view.getTag(R.string.device_ssid);
            Log.d(TAG,"OnClickListener devicesSSID = ["+deviceSSID+"] ,deviceId = ["+deviceId +"]");
            m_activity.onDeviceSelected(deviceId, deviceSSID);
        }
    };

}
