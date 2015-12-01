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
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.Util;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceAction;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.adapter.NearbyDevicesAdapter;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.WifiDisabledException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener2;
import com.handmark.pulltorefresh.library.PullToRefreshGridView;

public class NearbyDevicesActivity extends BaseActivity {
    private BroadcastReceiver m_receiver = null;

    private final IntentFilter m_filter = new IntentFilter();

    private final String TAG = "DASHBOARD_MainDevicesActivity";

    ////////////////////////////////////////////
    ///////////// UI ELEMENTS //////////////////
    ////////////////////////////////////////////

    // NAV TITLE
    private ImageView m_navTitleHelpButton = null;
    private ImageView m_navTitleSettingsButton = null;
    private TextView m_navTitleNotificationsText;
    private FrameLayout m_navTitleNotificationsButton = null;

    // MY DEVICES
    private PullToRefreshGridView mPullRefreshGridView;
    private GridView m_myDevicesGrid = null;
    private ImageView m_myDevicesEmptyImage = null;
    private ArrayList<Device> m_myDevicesArrayList = null;
    private NearbyDevicesAdapter m_nearbyDevicesArrayAdapter = null;

    // GENERAL
    private ViewGroup m_navigationLayout = null;

    ////////////////////////////////////////////
    ///////////// UI ELEMENTS END //////////////
    ////////////////////////////////////////////

    private static final Comparator<Device> DeviceNameComparator = new Comparator<Device>() {
        @Override
        public int compare(Device d1, Device d2) {
            if (d1 == null || d2 == null) {
                return 0;
            }
            // compare Status by ascending order (Configuring, Available, Unavailable, Unconfigured, Gone)
            int StatusOrdinalD1 = d1.getStatus().ordinal();
            int StatusOrdinalD2 = d2.getStatus().ordinal();
            if (StatusOrdinalD1 == StatusOrdinalD2) {
                // compare Pinned by descending order (Pinned, Unpinned)
                int PinnedD1 = (d1.isPinned() ? 1 : 0);
                int PinnedD2 = (d2.isPinned() ? 1 : 0);
                if (PinnedD1 == PinnedD2) {
                    // compare the name by ascending case-insensitive lexicographic order
                    return d1.getFriendlyName().compareToIgnoreCase(d2.getFriendlyName());
                }
                return (PinnedD2 - PinnedD1);
            }
            return (StatusOrdinalD1 - StatusOrdinalD2);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // log app name and version
        PackageInfo packageInfo;
        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = packageInfo.versionName;
            String appName = packageInfo.applicationInfo.loadLabel(getPackageManager()).toString();
            Log.i(TAG, String.format("==========================\n%s Version %s\n==========================", appName, version));
        } catch (NameNotFoundException e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            Log.e(TAG, "onDestroy  " + writer.toString());
        }

        WifiUtil.getInstance().acquireMulticastLock();

        // /////////////////////////////////////////////////////////////
        m_navigationLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_nearby_devices, null);

        // NAV TITLE
        m_navTitleHelpButton = (ImageView) m_navigationLayout.findViewById(R.id.navtitlemenu_help_button);
        m_navTitleNotificationsText = (TextView) m_navigationLayout.findViewById(R.id.navtitlemenu_notifications_text);
        m_navTitleNotificationsButton = (FrameLayout) m_navigationLayout.findViewById(R.id.notification_icon_layout);
        m_navTitleSettingsButton = (ImageView) m_navigationLayout.findViewById(R.id.navtitlemenu_settings_image);

        m_navTitleHelpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final Intent intent = new Intent(NearbyDevicesActivity.this, LicenseActivity.class);
                intent.putExtra(LicenseActivity.TEXT_RES_ID_EXTRA, R.string.help_my_devices);
                NearbyDevicesActivity.this.startActivity(intent);

                markGoingToNewPage();
            }
        });

        updateNotificationTextView();
        m_navTitleNotificationsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {

                Intent launchIntent = new Intent(NearbyDevicesActivity.this, NotificationsActivity.class);
                launchIntent.putExtra(IntentExtraKeys.EXTRA_ACTIVITY_ORIGIN, "ManageDeviceActivity");
                NearbyDevicesActivity.this.startActivity(launchIntent);

                markGoingToNewPage();
            }
        });

        m_navTitleSettingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                NearbyDevicesActivity.this.startActivity(new Intent(NearbyDevicesActivity.this, SettingsActivity.class));

                markGoingToNewPage();
            }
        });

        mPullRefreshGridView = (PullToRefreshGridView) m_navigationLayout.findViewById(R.id.pull_refresh_grid);
        m_myDevicesGrid = mPullRefreshGridView.getRefreshableView();

        // Set a listener to be invoked when the list should be refreshed.
        mPullRefreshGridView.setOnRefreshListener(new OnRefreshListener2<GridView>() {
            @Override
            public void onPullDownToRefresh(PullToRefreshBase<GridView> refreshView) {
                String currentSSID = WifiUtil.getInstance().getCurrentSSID();
                if (currentSSID != null) {
                    refreshView.getLoadingLayoutProxy().setLastUpdatedLabel("Connected to " + currentSSID);
                }
                new GetDataTask().execute();
            }

            @Override
            public void onPullUpToRefresh(PullToRefreshBase<GridView> refreshView) {
                new GetDataTask().execute();
            }

        });

        m_myDevicesEmptyImage = (ImageView) m_navigationLayout.findViewById(R.id.nearby_devices_image_empty);
        m_myDevicesEmptyImage.setVisibility(View.GONE);

        m_myDevicesArrayList = UIUtil.getMyDevices();
        if (m_nearbyDevicesArrayAdapter == null) {
            m_nearbyDevicesArrayAdapter = new NearbyDevicesAdapter(this, R.layout.list_item_nearby_device, m_myDevicesArrayList);
        }
        m_myDevicesGrid.setAdapter(m_nearbyDevicesArrayAdapter);

        m_myDevicesGrid.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View arg0, MotionEvent arg1) {
                if (m_nearbyDevicesArrayAdapter.isResetMode()) {
                    m_nearbyDevicesArrayAdapter.endResetMode();
                    removeAllREsetTagFromDevices();
                }
                return false;
            }
        });

        // /////////////////////////////////////////////////////////////

        setContentView(m_navigationLayout);

        m_receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Receive intent: Action = " + intent.getAction() + " extras = " + Util.bundleToString(intent.getExtras()));
                final String action = intent.getAction();
                if (IntentActions.AJ_ON_REFRESH_AP_LIST.equals(action)) {
                    onMyDevicesGridChanged();
                } else if (IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE.equals(action)) {
                    onMyDevicesGridChanged();
                } else if (IntentActions.AJ_ON_DEVICE_FOUND.equals(action) || IntentActions.AJ_ON_DEVICE_ALIVE.equals(action) || IntentActions.AJ_ON_DEVICE_LOST.equals(action)) {
                    // refresh both lists so they're up to date the next time
                    // they're viewed
                    onMyDevicesGridChanged();
                } else if (IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED.equals(action) || IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED.equals(action)) {
                    // refresh this list so it's up to date the next time it's
                    // viewed
                    updateNotificationTextView();
                } else if (IntentActions.AJ_ON_SHOW_TOAST_ACTION.equals(action)) {
                    final String msg = intent.getStringExtra(IntentExtraKeys.EXTRA_TOAST_MSG);
                    UIUtil.showToast(NearbyDevicesActivity.this, msg);
                }
            }
        };

        m_filter.addAction(IntentActions.AJ_ON_REFRESH_AP_LIST);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_FOUND);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_ALIVE);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_LOST);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE);
        m_filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED);
        m_filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED);
        m_filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        m_filter.addAction(IntentActions.AJ_ON_SHOW_TOAST_ACTION);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        final String action = intent.getAction();
        if (IntentActions.AJ_ON_SCROLL_TO_TOP_AP_LIST.equals(action)) {
            scrollToTop();
        }
    }

    public void refreshGrid() {
        onMyDevicesGridChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (m_receiver != null) {
            registerReceiver(m_receiver, m_filter);
        }

        updateNotificationTextView();
        onMyDevicesGridChanged();

        mPullRefreshGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Auto scan when onCreate");
                    OnboardingManager.getInstance().scanWiFi();
                } catch (WifiDisabledException e) {
                    verifyWifiIsEnabled();
                    Log.e(TAG, "WifiDisabledException", e);
                }
            }
        }, 7 * 1000);

        DeviceManagerImpl.getInstance().startPinging();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DeviceManagerImpl.getInstance().stopPinging();
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
        removeAllREsetTagFromDevices();
    }

    /**
     * Override {@link BaseActivity#onBackPressed()} so that we don't call
     * {@link BaseActivity#markGoingBack()}
     */
    @Override
    public void onBackPressed() {
        finish();
    }

    private void scrollToTop() {
        if (m_myDevicesGrid != null) {
            m_myDevicesGrid.post(new Runnable() {
                @Override
                public void run() {
                    m_myDevicesGrid.smoothScrollToPosition(0);
                    // m_myDevicesGrid.setSelection(0);
                }
            });
        }
    }

    private void removeAllREsetTagFromDevices() {
        ArrayList<Device> devices = (ArrayList<Device>) DeviceManagerImpl.getInstance().getDevices();
        for (Device d : devices) {
            d.removeTag(UIUtil.DEVICE_TAG_SHOW_RESET_LAYOUT);
        }
        m_nearbyDevicesArrayAdapter.notifyDataSetChanged();
    }

    private void updateNotificationTextView() {
        String notificationCount = "";
        if (NotificationsManagerImpl.getInstance().getUnreadNotificationsCount() > 0) {
            notificationCount = "" + NotificationsManagerImpl.getInstance().getUnreadNotificationsCount();
        }
        m_navTitleNotificationsText.setText(notificationCount);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        WifiUtil.getInstance().releaseMulticastLock();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        removeAllREsetTagFromDevices();
        return super.onTouchEvent(event);
    }

    private void onMyDevicesGridChanged() {
        m_myDevicesArrayList = (ArrayList<Device>) DeviceManagerImpl.getInstance().getDevices();
        Collections.sort(m_myDevicesArrayList, DeviceNameComparator);
        if (m_myDevicesArrayList != null && m_myDevicesEmptyImage != null) {
            if (m_myDevicesArrayList.size() > 0) {
                m_myDevicesEmptyImage.setVisibility(View.INVISIBLE);
            } else {
                m_myDevicesEmptyImage.setVisibility(View.VISIBLE);
            }
        }

        m_nearbyDevicesArrayAdapter.clear();

        if (m_myDevicesArrayList != null) {
            m_nearbyDevicesArrayAdapter.addAll(m_myDevicesArrayList);
        }

    }

    public void onDeviceSelected(UUID deviceId, String deviceSSID) {
        Device currentDevice = null;
        if (deviceId != null) {
            currentDevice = DeviceManagerImpl.getInstance().getDevice(deviceId);
        } else if (deviceSSID != null) {
            currentDevice = DeviceManagerImpl.getInstance().getDeviceBySSID(deviceSSID);
        }

        if (currentDevice == null) {
            return;
        }
        /*
         * These two cases should be removed. The Device Details and Manage
         * Device pages are designed to display a device in the in the
         * CONFIGURING state. Also, the device objects in the My Devices list DO
         * NOT have an SSID. Without an SSID, a device cannot be onboarded. The
         * user should be directed to the NearbyDevices list (where the device
         * objects do have an SSID), just like the user does from the
         * ManageDeviceConnection page. To resolve this, we should let the user
         * go to the Nearby Devices list on their own, or if they select the
         * device here, they can also go to the CONFIGURE button from the Manage
         * Device page.
         */
        if (DeviceStatus.CONFIGURING.equals(currentDevice.getStatus())) {
            return;
        }

        if (DeviceStatus.UNCONFIGURED.equals(currentDevice.getStatus())) {
            final Intent launchIntent = new Intent(this, SetupDeviceStepOneActivity.class);
            launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_SSID, deviceSSID);
            startActivity(launchIntent);
        } else {
            final Intent launchIntent = new Intent(this, DeviceDetailsActivity.class);
            launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, deviceId);
            startActivity(launchIntent);

            markGoingToNewPage();
        }

    }

    @Override
    protected void onNewDevicePassword(UUID deviceId) {
        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d != null && DeviceAction.FACTORY_RESET.equals(d.getTag(Device.DEVICE_TAG_LAST_ACTION))) {
            requestFactoryReset(deviceId);
        }
    }

    @Override
    protected void onDevicePasswordDialogDismissed(UUID deviceId) {
        Log.i(TAG, "onDevicePasswordDialogDismissed UUID = " + deviceId);

        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d == null) {
            return;
        }
        DeviceAction lastAction = d.getLastAction();

        Log.i(TAG, "onDevicePasswordDialogDismissed = last Action = " + lastAction + "device name = " + d.getFriendlyName());
        if (DeviceAction.FACTORY_RESET == lastAction) {
            cancelFactoryReset();
        }
    }

    private class GetDataTask extends AsyncTask<Object, Object, Object> {

        @Override
        protected void onPreExecute() {
            // Simulates a background job.
            // refreshGrid();
            DeviceManagerImpl.getInstance().scan();
            // Call onRefreshComplete when the list has been refreshed.
            mPullRefreshGridView.onRefreshComplete();
        }

        @Override
        protected Object doInBackground(Object... params) {
            // TODO Auto-generated method stub
            return null;
        }
    }
}
