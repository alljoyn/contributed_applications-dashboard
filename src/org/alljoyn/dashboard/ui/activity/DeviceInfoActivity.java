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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.adapter.DeviceInfoAdapter;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class DeviceInfoActivity extends BaseActivity {
    private final static String TAG = "DASHBOARD_DeviceInfoActivity";

    private UUID m_deviceId;

    private ViewGroup m_deviceInfoLayout = null;

    // private ImageView m_helpButton = null;
    private TextView m_titleText = null;
    private ImageView m_deviceIcon = null;
    private TextView m_deviceNameText = null;

    private ListView m_deviceInfoList = null;
    // private ArrayList<AJDeviceInfoElement> m_arrayList;
    private DeviceInfoAdapter m_arrayAdapter;

    // broadcast receiver
    private BroadcastReceiver m_receiver;

    // private String m_defaultLanguage = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        m_deviceId = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        if (m_deviceId == null) {
            Log.e(TAG, "m_deviceId is NULL - go to the NavigationActivity by default.");
            startActivity(new Intent(DeviceInfoActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }

        m_deviceInfoLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_device_info, null);

        // add UI elements
        // m_helpButton = (ImageView)
        // m_deviceInfoLayout.findViewById(R.id.device_info_help_button);
        m_titleText = (TextView) m_deviceInfoLayout.findViewById(R.id.navtitleback_title_text);
        m_deviceInfoList = (ListView) m_deviceInfoLayout.findViewById(R.id.device_info_list);
        m_deviceIcon = (ImageView) m_deviceInfoLayout.findViewById(R.id.device_info_header_icon);
        m_deviceNameText = (TextView) m_deviceInfoLayout.findViewById(R.id.device_info_device_name);

        // setup
        final OnClickListener onBack = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                markGoingBack();
                DeviceInfoActivity.this.finish();
            }
        };

        // m_titleText.setOnClickListener(onBack);

        View view = m_deviceInfoLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);

        m_titleText.setText(R.string.device_info_title);

        /*
         * m_helpButton.setOnClickListener(new OnClickListener() {
         *
         * @Override public void onClick(View arg0) { final Intent helpIntent =
         * new Intent(DeviceInfoActivity.this, HelpActivity.class); //TODO: add
         * help URL - helpIntent.putExtra(HelpActivity.URL_EXTRA, helpUrl);
         * DeviceInfoActivity.this.startActivity(helpIntent); } });
         */

        // set up list
        m_arrayAdapter = new DeviceInfoAdapter(this, R.layout.list_item_device_info);
        m_arrayAdapter.setLayoutInflator(getLayoutInflater());

        getAboutAndFillTheList();

        setContentView(m_deviceInfoLayout);

        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                Log.d(TAG, "onReceive: " + intent.getAction());

                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                if (IntentActions.AJ_ON_DEVICE_FOUND.equals(action)) {
                    // refresh the device UI states in case they've changed
                    UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    if (deviceId != null && m_deviceId.equals(deviceId)) {
                        Log.d(TAG, "onReceive device found: " + deviceId);
                        updateDeviceStatusView();
                        getAboutAndFillTheList();
                    }
                } else if (IntentActions.AJ_ON_DEVICE_LOST.equals(action)) {
                    // refresh the device UI states in case they've changed
                    UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    if (deviceId != null && m_deviceId.equals(deviceId)) {
                        Log.d(TAG, "onReceive device lost: " + deviceId);
                        // existing views are invalid
                        // onDeviceLost();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();

        // about actions
        filter.addAction(IntentActions.AJ_ON_DEVICE_LOST);
        filter.addAction(IntentActions.AJ_ON_DEVICE_FOUND);
        registerReceiver(m_receiver, filter);

        // TODO: Add sample data
        // TODO: implement connection error / no control service state
    }

    @Override
    public void onResume() {
        super.onResume();

        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            device.setHandleAsynchPings(false);
        }

        updateDeviceStatusView();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        try {
            if (m_receiver != null) {
                unregisterReceiver(m_receiver);
            }
        } catch (Exception e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            Log.e(TAG, "onDestroy  " + writer.toString());
        }
    }

    private void updateDeviceStatusView() {
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            UIUtil.setDeviceIcon(m_deviceIcon, device.getId());
            m_deviceNameText.setText(device.getFriendlyName());
        }
    }

    private void getAboutAndFillTheList() {
        @SuppressWarnings("rawtypes")
        final AsyncTask bkgdTask = new AsyncTask() {
            private Device m_asyncDevice;

            @Override
            protected void onPreExecute() {
                m_asyncDevice = UIUtil.getDevice(m_deviceId);
            }

            @Override
            protected Object doInBackground(Object... arg0) {
                if (m_asyncDevice == null) {
                    return null;
                }
                return m_asyncDevice.getAbout(true);
            }

            @Override
            protected void onPostExecute(Object result) {
                if (result != null) {
                    final Map<String, Object> aboutMap = (Map<String, Object>) result;

                    // m_defaultLanguage = (String)
                    // aboutMap.get(AboutKeys.ABOUT_DEFAULT_LANGUAGE);//keep it
                    // for later use

                    final List<AJDeviceInfoElement> results = new ArrayList<AJDeviceInfoElement>();
                    for (Entry<String, Object> entry : aboutMap.entrySet()) {
                        results.add(new AJDeviceInfoElement(entry.getKey(), entry.getValue()));
                    }
                    m_arrayAdapter.setData(results);
                    m_deviceInfoList.setAdapter(m_arrayAdapter);
                }
            }
        };
        bkgdTask.execute();
    }

    public class AJDeviceInfoElement {
        // TODO: implement this class once a spec is defined
        public String key;
        public Object value;

        public AJDeviceInfoElement(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

}
