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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceAction;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.Device.ServiceType;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ManageDeviceActivity extends BaseActivity {
    private final static String TAG = "DASHBOARD_ManageDeviceActivity";

    private Timer m_restartTimer;
    private boolean restartRequested = false;
    private UUID m_deviceId;

    private ViewGroup m_manageDeviceLayout = null;
    protected AlertDialog m_languageDialog;

    private TextView m_titleText = null;
    private ImageView m_icon = null;
    private EditText m_editText = null;
    private ImageView m_underline = null;

    private TextView m_notifToggleText = null;
    private TextView m_passwordText = null;
    private TextView m_deviceLanguage = null;
    private TextView m_moreInfoText = null;
    private TextView m_restartText = null;
    private TextView m_resetText = null;
    private TextView m_helpText = null;
    private TextView m_savedText = null;
    private ImageView m_cancelButton = null;
    private ImageView m_confirmButton = null;
    private CheckBox m_pinToNearby;
    private View m_helpSeperator = null;

    private ViewGroup m_toggleLayout = null;
    private TextView m_muteOnText = null;
    private TextView m_muteOffText = null;

    private boolean m_isConfigSupported = true;

    // broadcast receiver
    private BroadcastReceiver m_receiver;
    IntentFilter m_filter = new IntentFilter();

    private ProgressDialog restartProgressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        m_deviceId = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        if (m_deviceId == null) {
            Log.e(TAG, "m_deviceId is NULL - go to the NavigationActivity by default.");
            startActivity(new Intent(ManageDeviceActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }

        final Device device = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
        if (device != null) {
            if (!device.isServiceSupported(ServiceType.CONFIG)) {
                m_isConfigSupported = false;
            }
        }

        m_manageDeviceLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_manage_device, null);

        // add UI elements
        m_titleText = (TextView) m_manageDeviceLayout.findViewById(R.id.navtitleback_title_text);
        m_icon = (ImageView) m_manageDeviceLayout.findViewById(R.id.manage_device_header_icon);
        m_editText = (EditText) m_manageDeviceLayout.findViewById(R.id.manage_device_edit_name);
        m_underline = (ImageView) m_manageDeviceLayout.findViewById(R.id.manage_device_edit_underline);

        m_notifToggleText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_mute_notif);
        m_toggleLayout = (ViewGroup) m_manageDeviceLayout.findViewById(R.id.manage_device_toggle_layout);
        m_muteOnText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_toggle_on);
        m_muteOffText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_toggle_off);
        m_deviceLanguage = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_notification_language);
        m_passwordText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_password);
        m_moreInfoText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_more_info);
        m_restartText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_restart);
        m_resetText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_reset);
        m_helpText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_item_help);
        m_helpSeperator = m_manageDeviceLayout.findViewById(R.id.manage_device_item_help_seperator);
        m_savedText = (TextView) m_manageDeviceLayout.findViewById(R.id.manage_device_text_saved);
        m_cancelButton = (ImageView) m_manageDeviceLayout.findViewById(R.id.manage_device_button_cancel);
        m_confirmButton = (ImageView) m_manageDeviceLayout.findViewById(R.id.manage_device_button_confirm);
        m_pinToNearby = (CheckBox) m_manageDeviceLayout.findViewById(R.id.manage_device_pin_check_box);

        // setup
        final OnClickListener onBack = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                UIUtil.hideIme(m_cancelButton);
                markGoingBack();
                ManageDeviceActivity.this.finish();
            }
        };

        View view = m_manageDeviceLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);

        m_titleText.setText(R.string.manage_device_title);

        m_cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (device != null) {
                    m_editText.setText(device.getFriendlyName());
                }

                m_editText.clearFocus();
                turnOffEditNameUI();
            }
        });
        m_confirmButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                updateConfig(getConfigMapForRename());
            }
        });

        if (m_isConfigSupported && device != null && device.getStatus() == DeviceStatus.AVAILABLE) {
            m_editText.setEnabled(false);
            m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

            final AsyncTask getMaxLengthTask = new AsyncTask<UUID, Void, Void>() {
                UUID deviceId = null;
                Short limit = -1;
                String helpPage = null;

                @Override
                protected Void doInBackground(UUID... params) {
                    Log.d(TAG, "getMaxLengthTask doInBackground ");
                    deviceId = params[0];

                    if (device != null) {

                        Map<String, Object> map = device.getAbout(false);
                        if (map == null) {
                            map = device.getAbout(true);
                        }
                        if (map != null) {
                            if (map.containsKey("MaxLength")) {
                                limit = (Short) map.get("MaxLength");
                            }

                            helpPage = (String) map.get("SupportUrl");
                            if (helpPage != null && !helpPage.isEmpty()) {
                                device.setHelpUrl(helpPage);
                            }

                        }

                    }

                    // after retrieving MaxLength runonUIThread make
                    // m_editText,m_editButton visible
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            final String helpUrl = device.getHelpURL();
                            if (helpUrl != null && !helpUrl.isEmpty() && !m_helpText.isClickable()) {
                                m_helpText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
                                m_helpText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_help_icon, 0, 0, 0);
                                m_helpText.setVisibility(View.VISIBLE);
                                m_helpSeperator.setVisibility(View.VISIBLE);
                                m_helpText.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View arg0) {
                                        final Intent helpIntent = new Intent(ManageDeviceActivity.this, HelpActivity.class);
                                        helpIntent.putExtra(HelpActivity.URL_EXTRA, helpUrl);
                                        hideKeyboardThenLaunchActivityIntent(helpIntent);
                                    }
                                });
                            }

                            if (limit != -1) {
                                Log.d(TAG, "getMaxLengthTask  doInBackground limit is set to " + limit);

                                InputFilter[] filters = new InputFilter[1];
                                filters[0] = new InputFilter.LengthFilter(limit);
                                m_editText.setFilters(filters);
                            }
                            // TODO Auto-generated method stub
                            m_editText.setEnabled(true);
                            m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.manage_devices_edit_icon, 0);
                        }
                    });

                    return null;
                }

            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                getMaxLengthTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new UUID[] { m_deviceId }));
            } else {
                getMaxLengthTask.execute(new UUID[] { m_deviceId });
            }

            m_editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            m_editText.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focus) {
                    if (focus) {
                        turnOnEditNameUI();
                    } else {
                        turnOffEditNameUI();
                    }
                }
            });
        } else {
            m_editText.setEnabled(false);
            m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        if (device != null) {
            m_pinToNearby.setChecked(device.isPinned());
        }
        m_pinToNearby.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Device d = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
                if (d == null) {
                    return;
                }
                boolean on = ((CheckBox) v).isChecked();
                d.pinToNearby(on);
                if (!on && d.getStatus().equals(DeviceStatus.UNAVAILABLE)) {
                    DeviceManagerImpl.getInstance().removeDevice(m_deviceId);
                    final Intent launchIntent = new Intent(ManageDeviceActivity.this, NearbyDevicesActivity.class);
                    ManageDeviceActivity.this.startActivity(launchIntent);
                }
            }
        });

        // notifications

        if (device != null && device.isNotificationOn()) {
            m_muteOnText.setVisibility(View.INVISIBLE);
            m_muteOffText.setVisibility(View.VISIBLE);
        } else {
            m_muteOnText.setVisibility(View.VISIBLE);
            m_muteOffText.setVisibility(View.INVISIBLE);
        }

        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                Log.d(TAG, "onReceive: " + intent.getAction());
                if (intent == null || intent.getAction() == null || intent.getExtras() == null) {
                    return;
                }
                UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                if (IntentActions.AJ_ON_DEVICE_FOUND.equals(intent.getAction())) {
                    // refresh the device UI states in case they've changed
                    if (m_deviceId.equals(deviceId)) {
                        Log.d(TAG, "onReceive device found: " + deviceId);
                        updateDeviceStatusView();
                    }
                } else if (IntentActions.AJ_ON_DEVICE_ALIVE.equals(intent.getAction())) {
                    // refresh the device UI states in case they've changed
                    if (m_deviceId.equals(deviceId)) {
                        Log.d(TAG, "onReceive device alive: " + deviceId);
                        updateDeviceStatusView();
                    }
                } else if (IntentActions.AJ_ON_DEVICE_LOST.equals(intent.getAction())) {
                    // refresh the device UI states in case they've changed
                    if (m_deviceId.equals(deviceId)) {
                        Log.d(TAG, "onReceive device lost: " + deviceId);
                        // existing views are invalid
                        // onDeviceLost();
                    }
                } else if (IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE.equals(intent.getAction())) {
                    if (deviceId != null && deviceId.equals(m_deviceId) && restartRequested) {
                        if (m_restartTimer != null) {
                            m_restartTimer.cancel();
                            m_restartTimer.purge();
                        }
                        final Intent launchIntent = new Intent(ManageDeviceActivity.this, NearbyDevicesActivity.class);
                        ManageDeviceActivity.this.startActivity(launchIntent);
                    }
                }
            }
        };

        // about actions
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_LOST);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_FOUND);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_ALIVE);
        m_filter.addAction(IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE);

        setContentView(m_manageDeviceLayout);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (m_receiver != null) {
            registerReceiver(m_receiver, m_filter);
        }

        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            device.setHandleAsynchPings(false);
        }

        updateDeviceStatusView();
        setActivityState();
        turnOffEditNameUI();

        m_editText.clearFocus();

        // make sure another view can have focus
        m_icon.setFocusable(true);
        m_icon.setFocusableInTouchMode(true);

        // ensure the keyboard is hidden anytime this activity comes into view
        UIUtil.blockIme(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            if (m_receiver != null) {
                unregisterReceiver(m_receiver);
            }
        } catch (Exception e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            Log.e(TAG, "onPause " + writer.toString());
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        UIUtil.hideIme(m_cancelButton);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

    }

    private Map<String, Object> getConfigMapForRename() {
        String newname = m_editText.getText().toString();
        if (newname == null || newname.trim().length() == 0) {
            Device device = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
            if (device != null) {
                m_editText.setText(device.getFriendlyName());
            }
            launchBasicAlertDialog("Error", "Name cannot be empty.", null, null, "OK");
            return null;
        }

        Map<String, Object> config = new HashMap<String, Object>();
        config.put(AboutKeys.ABOUT_DEVICE_NAME, m_editText.getText().toString());

        m_editText.clearFocus();
        turnOffEditNameUI();
        flashSaved();

        return config;
    }

    private void updateConfig(final Map<String, Object> config) {
        final AsyncTask task = new AsyncTask<UUID, Void, DeviceResponse>() {
            UUID deviceId = null;

            @Override
            protected DeviceResponse doInBackground(UUID... params) {
                deviceId = params[0];
                Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
                if (d != null) {
                    return d.setConfig(config);
                } else {
                    return new DeviceResponse(ResponseCode.Status_ERROR);
                }
            }

            @Override
            protected void onPostExecute(DeviceResponse result) {
                if (result.getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "Failed to change name for deivce  UUID = " + deviceId);
                    // if (m_passwordDialog == null ||
                    // !m_passwordDialog.isShowing()) {
                    if (!wrongAjPassword) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                launchBasicAlertDialog("Failed to change Device Name", 0, String.format(String.format("Unable to change the device name ")), getString(R.string.dismiss), null, null,
                                        null, null, null);
                            }
                        });
                    } else {
                        // password dialog is showing. password error is
                        // probably why this action failed.
                        // Avoid double dialogs.
                        Log.d(TAG, "Password dialog is showing. Not displaying an extra error dialog above it.");
                    }
                }

            }

        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new UUID[] { m_deviceId }));
        } else {
            task.execute(new UUID[] { m_deviceId });
        }

    }

    private void hideKeyboardThenLaunchActivityIntent(final Intent launchIntent) {
        UIUtil.hideIme(m_cancelButton);

        m_manageDeviceLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                ManageDeviceActivity.this.startActivity(launchIntent);

                markGoingToNewPage();
            }
        }, 100);
    }

    private void turnOnEditNameUI() {
        m_underline.setVisibility(View.VISIBLE);
        m_cancelButton.setVisibility(View.VISIBLE);
        m_confirmButton.setVisibility(View.VISIBLE);
        if (m_isConfigSupported) {
            m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        m_savedText.setVisibility(View.INVISIBLE);
    }

    private void turnOffEditNameUI() {
        m_underline.setVisibility(View.INVISIBLE);
        m_cancelButton.setVisibility(View.INVISIBLE);
        m_confirmButton.setVisibility(View.INVISIBLE);
        if (m_isConfigSupported) {
            if (m_editText.isEnabled()) {
                m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.manage_devices_edit_icon, 0);
            }
        }

        m_savedText.setVisibility(View.INVISIBLE);

        m_editText.setSelection(0);

        UIUtil.hideIme(m_cancelButton);
    }

    private void flashSaved() {
        m_underline.setVisibility(View.INVISIBLE);
        m_cancelButton.setVisibility(View.INVISIBLE);
        m_confirmButton.setVisibility(View.INVISIBLE);
        if (m_isConfigSupported) {
            m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        m_savedText.setVisibility(View.VISIBLE);

        m_manageDeviceLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (m_isConfigSupported) {
                    if (m_editText.isEnabled()) {
                        m_editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.manage_devices_edit_icon, 0);
                    }
                }
                m_savedText.setVisibility(View.INVISIBLE);
            }
        }, 1000);
    }

    @Override
    protected void onNewDevicePassword(UUID deviceId) {
        Log.i(TAG, "onNewDevicePassword UUID = " + deviceId);
        if (!deviceId.equals(m_deviceId)) {
            return;
        }

        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d == null) {
            return;
        }
        DeviceAction lastAction;
        try {
            lastAction = (DeviceAction) d.getTag(Device.DEVICE_TAG_LAST_ACTION);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (lastAction == null) {
            return;
        }
        Log.i(TAG, "onNewDevicePassword = last Action = " + lastAction + "device name = " + d.getFriendlyName());
        if (DeviceAction.SET_CONFIG == lastAction) {
            updateConfig(getConfigMapForRename());
        } else if (DeviceAction.FACTORY_RESET == lastAction) {
            requestFactoryReset(deviceId);
        } else if (DeviceAction.RESTART_DEVICE == lastAction) {
            restartDevice();
        }

    }

    @Override
    protected void onDevicePasswordDialogDismissed(UUID deviceId) {
        Log.i(TAG, "onDevicePasswordDialogDismissed UUID = " + deviceId);
        if (!deviceId.equals(m_deviceId)) {
            return;
        }

        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d == null) {
            return;
        }
        DeviceAction lastAction = d.getLastAction();

        Log.i(TAG, "onDevicePasswordDialogDismissed = last Action = " + lastAction + "device name = " + d.getFriendlyName());
        if (DeviceAction.FACTORY_RESET == lastAction) {
            cancelFactoryReset();
        } else if (DeviceAction.RESTART_DEVICE == lastAction) {
            cancelRestartDevice();
        }
    }

    protected void restartDevice() {
        Log.d(TAG, "restartDevice");
        if (m_restartTimer != null) {
            m_restartTimer.cancel();
            m_restartTimer.purge();
        }
        if (restartProgressDialog != null && restartProgressDialog.isShowing()) {
            restartProgressDialog.dismiss();
            restartProgressDialog = null;
        }
        Device currentDevice = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
        if (currentDevice == null) {
            return;
        }

        restartRequested = true;

        RestartDeviceTask task = new RestartDeviceTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new UUID[] { m_deviceId }));
        } else {
            task.execute(new UUID[] { m_deviceId });
        }
    }

    protected void cancelRestartDevice() {
        Log.d(TAG, "cancelRestartDevice");

        restartRequested = false;

        if (m_restartTimer != null) {
            m_restartTimer.cancel();
            m_restartTimer.purge();
        }
        if (restartProgressDialog != null && restartProgressDialog.isShowing()) {
            restartProgressDialog.dismiss();
            restartProgressDialog = null;
        }
    }

    private void updateDeviceStatusView() {
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            UIUtil.setDeviceIcon(m_icon, device.getId());
            m_editText.setText(device.getFriendlyName());
        }
    }

    private void setActivityState() {
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device == null) {
            return;
        }

        final DeviceStatus status = UIUtil.getDeviceStatus(this, device);

        // /////////////////
        // start by setting the default state for each item
        // /////////////////

        // device notifications
        // remarked, as no device declares that it supports notifications
        if (device.isServiceSupported(Device.ServiceType.NOTIFICATION)) {
            m_notifToggleText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_notifToggleText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_mute_icon, 0, 0, 0);
            final OnClickListener toggleClickListener = new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    final Device notifDevice = UIUtil.getDevice(m_deviceId);
                    if (notifDevice != null) {
                        if (notifDevice.isNotificationOn()) {
                            m_muteOffText.setVisibility(View.INVISIBLE);
                            m_muteOnText.setVisibility(View.VISIBLE);
                            notifDevice.turnOffNotifications();
                        } else {
                            m_muteOffText.setVisibility(View.VISIBLE);
                            m_muteOnText.setVisibility(View.INVISIBLE);
                            notifDevice.turnOnNotifications();
                        }
                    }
                }
            };
            m_notifToggleText.setOnClickListener(toggleClickListener);
            m_toggleLayout.setOnClickListener(toggleClickListener);
        } else {
            // device notifications
            m_notifToggleText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_notifToggleText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_mute_icon_off, 0, 0, 0);
            m_notifToggleText.setOnClickListener(null);
            m_toggleLayout.setOnClickListener(null);
        }

        // device language
        if (device.isServiceSupported(Device.ServiceType.CONFIG)) {
            m_deviceLanguage.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_deviceLanguage.setText(getString(R.string.manage_device_notification_language) + " " + getLanguageDisplayName(device.getDefaultLanguage()));
            m_deviceLanguage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_language_icon, 0, 0, 0);
            m_deviceLanguage.setOnClickListener(new OnClickListener() {
                private String m_selectedLanguage;
                private String m_selectedLanguageValue;

                @Override
                public void onClick(View arg0) {
                    @SuppressWarnings("rawtypes")
                    final AsyncTask bkgdTask = new AsyncTask() {
                        private Device m_asyncDevice;

                        @Override
                        protected void onPreExecute() {
                            m_asyncDevice = UIUtil.getDevice(m_deviceId);
                        }

                        @Override
                        protected Object doInBackground(Object... arg0) {
                            if (m_asyncDevice != null) {
                                return m_asyncDevice.getAbout(false);
                            }

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Object result) {
                            if (result != null) {
                                final Map<String, Object> aboutMap = (Map<String, Object>) result;
                                final Object supportedLangs = aboutMap.get(AboutKeys.ABOUT_SUPPORTED_LANGUAGES);

                                // shouldn't be null.
                                if (supportedLangs == null) {
                                    launchBasicAlertDialog(getString(R.string.manage_device_select_language), "No supported languages", getString(android.R.string.ok), new Runnable() {
                                        @Override
                                        public void run() {
                                            dismissAlertDialog();
                                        }
                                    }, null);
                                    return;
                                }

                                final String[] itemsValues = (String[]) supportedLangs;
                                final String[] items = getLanguageDisplayNames(itemsValues);

                                // get dialog layout
                                final View dialogLayout = getLayoutInflater().inflate(R.layout.alert_dialog, null);
                                final TextView titleView = (TextView) dialogLayout.findViewById(R.id.alert_title_text);
                                final TextView messageView = (TextView) dialogLayout.findViewById(R.id.alert_msg_text);
                                final Button posView = (Button) dialogLayout.findViewById(R.id.alert_button_pos);
                                final Button neutralView = (Button) dialogLayout.findViewById(R.id.alert_button_neutral);
                                final Button negView = (Button) dialogLayout.findViewById(R.id.alert_button_neg);

                                final AlertDialog.Builder builder = new AlertDialog.Builder(ManageDeviceActivity.this, AlertDialog.THEME_HOLO_DARK);

                                builder.setTitle(R.string.manage_device_select_language);
                                builder.setView(dialogLayout);
                                titleView.setVisibility(View.GONE);
                                messageView.setVisibility(View.GONE);
                                neutralView.setVisibility(View.GONE);

                                // search in the items for the last selected
                                // language to highlight it.
                                int checkedItem = -1;
                                final String currentLang = m_asyncDevice.getDefaultLanguage();
                                for (int i = 0; i < itemsValues.length; i++) {
                                    if (itemsValues[i].equals(currentLang)) {
                                        checkedItem = i;
                                        m_selectedLanguage = items[i]; // e.g.
                                                                       // 'English'
                                        m_selectedLanguageValue = itemsValues[i]; // e.g.
                                                                                  // 'en'
                                        break;
                                    }
                                }
                                builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        m_selectedLanguage = items[which]; // e.g.
                                                                           // 'English'
                                        m_selectedLanguageValue = itemsValues[which]; // e.g.
                                                                                      // 'en'
                                    }
                                });

                                posView.setText(android.R.string.cancel);
                                posView.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (m_languageDialog != null) {
                                            m_languageDialog.dismiss();
                                            m_languageDialog = null;
                                        }
                                    }
                                });

                                negView.setText(android.R.string.ok);
                                negView.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        final Device d = UIUtil.getDevice(m_deviceId);
                                        if (d != null) {
                                            final Map<String, Object> config = new HashMap<String, Object>();
                                            config.put(AboutKeys.ABOUT_DEFAULT_LANGUAGE, m_selectedLanguageValue);
                                            updateConfig(config);
                                        }

                                        if (m_selectedLanguage == null) {
                                            for (int i = 0; i < itemsValues.length; i++) {
                                                if (itemsValues[i].equals(m_selectedLanguageValue)) {
                                                    m_selectedLanguage = items[i];
                                                    break;
                                                }
                                            }
                                            if (m_selectedLanguage == null) {
                                                // default to english
                                                m_selectedLanguage = itemsValues[0];
                                            }
                                        }
                                        m_deviceLanguage.setText(getString(R.string.manage_device_notification_language) + " " + m_selectedLanguage);

                                        if (m_languageDialog != null) {
                                            m_languageDialog.dismiss();
                                            m_languageDialog = null;
                                        }
                                    }
                                });

                                if (m_languageDialog != null) {
                                    m_languageDialog.dismiss();
                                }
                                m_languageDialog = builder.create();
                                m_languageDialog.setOnShowListener(new OnShowListener() {
                                    @SuppressLint("NewApi")
                                    @Override
                                    public void onShow(DialogInterface di) {
                                        final Dialog dialog = (Dialog) di;

                                        // title layout
                                        final int titleLayoutId = dialog.getContext().getResources().getIdentifier("android:id/title_template", null, null);
                                        final LinearLayout titleLayout = (LinearLayout) dialog.findViewById(titleLayoutId);
                                        titleLayout.setGravity(Gravity.CENTER);
                                    }
                                });
                                m_languageDialog.show();
                            }
                        }
                    };
                    bkgdTask.execute();
                }
            });
        } else {
            m_deviceLanguage.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_deviceLanguage.setText(getString(R.string.manage_device_notification_language) + " " + getLanguageDisplayName(device.getDefaultLanguage()));
            m_deviceLanguage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_language_icon_off, 0, 0, 0);
        }

        // /////////////////////////////////////////////////////////////////////////////////////////////////////
        // device password
        if (device.isServiceSupported(Device.ServiceType.CONFIG)) {
            m_passwordText.setText(UIUtil.isPasscodeSet(device) ? R.string.manage_device_passphrase_edit : R.string.manage_device_passphrase_set);
            m_passwordText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_passwordText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_password_icon, 0, 0, 0);
            m_passwordText.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    final Intent launchIntent = new Intent(ManageDeviceActivity.this, EnterDevicePasscodeActivity.class);
                    launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, m_deviceId);
                    hideKeyboardThenLaunchActivityIntent(launchIntent);
                }
            });

        } else {
            m_passwordText.setText(UIUtil.isPasscodeSet(device) ? R.string.manage_device_passphrase_edit : R.string.manage_device_passphrase_set);
            m_passwordText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_passwordText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_password_icon_off, 0, 0, 0);
        }

        // device info
        m_moreInfoText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
        m_moreInfoText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_more_icon, 0, 0, 0);
        m_moreInfoText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final Intent launchIntent = new Intent(ManageDeviceActivity.this, DeviceInfoActivity.class);
                launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, m_deviceId);
                hideKeyboardThenLaunchActivityIntent(launchIntent);
            }
        });

        // restart device
        if (device.isServiceSupported(Device.ServiceType.CONFIG)) {
            m_restartText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_restartText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon, 0, 0, 0);
            m_restartText.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    final Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            final Device restartDevice = UIUtil.getDevice(m_deviceId);
                            if (restartDevice != null) {
                                try {
                                    restartDevice();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    };
                    launchBasicAlertDialog(getString(R.string.alert_title_confirm_restart_device), getString(R.string.alert_message_confirm_restart_device), getString(R.string.cancel), null,
                            getString(R.string.restart), run);
                }
            });
        } else {
            m_restartText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_restartText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
        }

        // factory reset device
        if (device.isServiceSupported(Device.ServiceType.CONFIG)) {
            m_resetText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_resetText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_reset_icon, 0, 0, 0);
            m_resetText.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    launchFactoryResetPrompt(m_deviceId);
                }
            });
        } else {
            m_resetText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_resetText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_reset_icon_off, 0, 0, 0);
        }

        // help

        final String helpUrl = device.getHelpURL(); // "https://www.alljoyn.org/alljoyn-on";
        if (helpUrl != null && !helpUrl.isEmpty()) {
            m_helpText.setTextColor(getResources().getColor(R.color.manage_device_text_enabled));
            m_helpText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_help_icon, 0, 0, 0);

            m_helpText.setVisibility(View.VISIBLE);
            m_helpSeperator.setVisibility(View.VISIBLE);
            m_helpText.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    final Intent helpIntent = new Intent(ManageDeviceActivity.this, HelpActivity.class);
                    helpIntent.putExtra(HelpActivity.URL_EXTRA, helpUrl);
                    hideKeyboardThenLaunchActivityIntent(helpIntent);
                }
            });
        } else {
            m_helpText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_helpText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_help_icon_off, 0, 0, 0);
        }

        // OPEN THIS CODE WHEN HELP URL IS NOT HARD CODDED.
        /*
         * //final String helpUrl = device.getHelpURL(); if (helpUrl != null &&
         * helpUrl.length() != 0) { m_helpText.setVisibility(View.VISIBLE);
         * m_helpSeperator.setVisibility(View.VISIBLE);
         * m_helpText.setOnClickListener(new OnClickListener() {
         *
         * @Override public void onClick(View arg0) { final Intent helpIntent =
         * new Intent(ManageDeviceActivity.this, HelpViewActivity.class);
         * helpIntent.putExtra(HelpActivity.URL_EXTRA, helpUrl);
         * hideKeyboardThenLaunchActivityIntent(helpIntent); } }); } else { //
         * don't show if no help url is available
         * m_helpText.setVisibility(View.GONE);
         * m_helpSeperator.setVisibility(View.GONE); }
         */

        // //////////////
        // now adjust the UI based on the device state
        // //////////////

        switch (status) {
        case AVAILABLE:
            // the default settings above are correct for this state
            break;
        case UNAVAILABLE:
            // device notifications
            // -- leave this as is

            // device password
            m_passwordText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_passwordText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_password_icon_off, 0, 0, 0);
            m_passwordText.setOnClickListener(null);

            // device info
            m_moreInfoText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_moreInfoText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_more_icon_off, 0, 0, 0);
            m_moreInfoText.setOnClickListener(null);

            // restart device
            m_restartText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_restartText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_restartText.setOnClickListener(null);

            // factory reset device
            m_resetText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_resetText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_resetText.setOnClickListener(null);

            // remove device
            // -- leave this as is

            // help
            // -- leave this as is
            break;
        case CONFIGURING:
            // device notifications
            m_notifToggleText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_notifToggleText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_mute_icon_off, 0, 0, 0);
            m_notifToggleText.setOnClickListener(null);
            m_toggleLayout.setOnClickListener(null);

            // device password
            m_passwordText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_passwordText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_password_icon_off, 0, 0, 0);
            m_passwordText.setOnClickListener(null);

            // device info
            m_moreInfoText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_moreInfoText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_more_icon_off, 0, 0, 0);
            m_moreInfoText.setOnClickListener(null);

            // restart device
            m_restartText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_restartText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_restartText.setOnClickListener(null);

            // factory reset device
            m_resetText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_resetText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_resetText.setOnClickListener(null);

            // remove device
            // -- leave this as is

            // help
            m_helpText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_helpText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_help_icon_off, 0, 0, 0);
            m_helpText.setOnClickListener(null);

            break;
        case UNCONFIGURED:
            // device notifications
            m_notifToggleText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_notifToggleText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_mute_icon_off, 0, 0, 0);
            m_notifToggleText.setOnClickListener(null);
            m_toggleLayout.setOnClickListener(null);

            // device password
            m_passwordText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_passwordText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_password_icon_off, 0, 0, 0);
            m_passwordText.setOnClickListener(null);

            // device info
            m_moreInfoText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_moreInfoText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_more_icon_off, 0, 0, 0);
            m_moreInfoText.setOnClickListener(null);

            // restart device
            m_restartText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_restartText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_restartText.setOnClickListener(null);

            // factory reset device
            m_resetText.setTextColor(getResources().getColor(R.color.manage_device_text_disabled));
            m_resetText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.manage_devices_restart_icon_off, 0, 0, 0);
            m_resetText.setOnClickListener(null);

            // remove device
            // -- leave this as is

            // help
            // -- leave this as is
            break;
        }
    }

    protected class RestartDeviceTask extends AsyncTask<UUID, Void, DeviceResponse> {
        UUID deviceId = null;

        @Override
        protected void onPreExecute() {
            Log.i(TAG, "onPreExecute");
            restartProgressDialog = new ProgressDialog(ManageDeviceActivity.this);
            restartProgressDialog.setCancelable(true);
            restartProgressDialog.setMessage(getString(R.string.manage_device_restert_loading));
            restartProgressDialog.show();

        };

        @Override
        protected void onPostExecute(DeviceResponse result) {
            Log.i(TAG, "onPostExecute");
            super.onPostExecute(result);
            Log.i(TAG, "restart: onPostExecute - result = " + result.getStatus());
            if (result.getStatus() == ResponseCode.Status_OK) {
                m_restartTimer = new Timer();
                m_restartTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        // asome all went OK
                        if (!wrongAjPassword) {
                            final Intent launchIntent = new Intent(ManageDeviceActivity.this, NearbyDevicesActivity.class);
                            ManageDeviceActivity.this.startActivity(launchIntent);
                        }
                    }
                }, 3 * 1000);
                // timer
            } else {
                Log.i(TAG, "restart: onPostExecute - Status not OK");
                restartProgressDialog.dismiss();
                Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
                String deviceName = "";
                if (d != null) {
                    deviceName = d.getFriendlyName();
                }
                launchBasicAlertDialog(getString(R.string.status_error), 0, String.format(getString(R.string.alert_message_restart_failed), deviceName), getString(R.string.dismiss), null, null, null,
                        null, null);
            }
        }

        @Override
        protected DeviceResponse doInBackground(UUID... params) {
            Log.i(TAG, "doInBackground");
            deviceId = params[0];
            Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
            if (d != null) {
                d.removeTag(UIUtil.DEVICE_TAG_SHOW_RESET_LAYOUT);
                DeviceResponse response = d.restartDevice();
                return response;
            }
            return new DeviceResponse(ResponseCode.Status_ERROR);
        }

    }

    public String getLanguageDisplayName(String rfc5646) {
        String displayLanguage = new Locale(rfc5646).getDisplayLanguage();
        return ((displayLanguage != null && displayLanguage.length() > 0) ? displayLanguage : rfc5646);
    }

    public String[] getLanguageDisplayNames(String[] rfc5646Codes) {
        String[] displayLanguages = new String[rfc5646Codes.length];
        for (int i = 0; i < rfc5646Codes.length; i++) {
            displayLanguages[i] = getLanguageDisplayName(rfc5646Codes[i]);
        }
        return displayLanguages;
    }

}
