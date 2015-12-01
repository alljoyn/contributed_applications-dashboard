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
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.alljoyn.ioe.controlpaneladapter.ControlPanelAdapter;
import org.alljoyn.ioe.controlpaneladapter.ControlPanelExceptionHandler;
import org.alljoyn.ioe.controlpanelservice.ControlPanelCollection;
import org.alljoyn.ioe.controlpanelservice.ControlPanelException;
import org.alljoyn.ioe.controlpanelservice.ui.AlertDialogWidget;
import org.alljoyn.ioe.controlpanelservice.ui.ContainerWidget;
import org.alljoyn.ioe.controlpanelservice.ui.ControlPanelEventsListener;
import org.alljoyn.ioe.controlpanelservice.ui.DeviceControlPanel;
import org.alljoyn.ioe.controlpanelservice.ui.UIElement;
import org.alljoyn.ioe.controlpanelservice.ui.UIElementType;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceDetailsActivity extends BaseActivity implements ControlPanelExceptionHandler, ControlPanelEventsListener {
    private final static String TAG = "DASHBOARD_DeviceDetailsActivity";

    private UUID m_deviceId;

    private ViewGroup m_deviceDetailsLayout = null;

    private TextView m_titleText = null;
    private ImageView m_manageDevicesButton = null;
    private ImageView m_deviceIcon = null;
    private TextView m_deviceNameText = null;
    private TextView m_deviceStatusText = null;
    private ImageView m_deviceStatusIcon = null;
    private ImageView m_controlButton;

    private LinearLayout m_controlPanelLayout = null;
    private TextView m_noControlsText = null;

    private ViewGroup m_deviceUnavailableLayout = null;
    private TextView m_deviceUnavailableText = null;

    private TextView m_notificationsText;
    private FrameLayout m_notificationsButton = null;

    private View m_progressLayout = null;

    AlertDialog m_selectPanelAlert;

    // control panel elements
    ArrayList<DeviceControlPanelWrapper> m_panelsArray;
    String[] m_panelsNameArray;
    int m_selectedPanelIndex;
    private boolean m_requestingPanels = false;
    DeviceControlPanelWrapper m_selectedPanel;
    private View m_controlPanelView;

    private ControlPanelAdapter m_controlPanelAdapter;
    private AlertDialog m_controlPanelAlertDialog;

    // broadcast receiver
    private BroadcastReceiver m_receiver;

    private final IntentFilter m_filter = new IntentFilter();

    // to catch changes in language in onResume.
    private String m_deviceLanguage = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m_selectedPanelIndex = 0;
        m_controlPanelView = null;

        m_deviceId = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        if (m_deviceId == null) {
            Log.e(TAG, "onCreate m_deviceId is NULL - go to the NavigationActivity by default.");
            startActivity(new Intent(DeviceDetailsActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device == null) {
            Log.w(TAG, "onCreate device not found: " + m_deviceId);
            startActivity(new Intent(DeviceDetailsActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }

        m_panelsArray = new ArrayList<DeviceDetailsActivity.DeviceControlPanelWrapper>();
        m_deviceDetailsLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_device_details, null);

        // add UI elements
        m_titleText = (TextView) m_deviceDetailsLayout.findViewById(R.id.navtitleback_title_text);
        m_manageDevicesButton = (ImageView) m_deviceDetailsLayout.findViewById(R.id.device_details_manage_button);
        m_notificationsText = (TextView) m_deviceDetailsLayout.findViewById(R.id.notifications_device_text);
        m_notificationsButton = (FrameLayout) m_deviceDetailsLayout.findViewById(R.id.notification_icon_device_frame_layout);
        m_controlButton = (ImageView) m_deviceDetailsLayout.findViewById(R.id.device_details_control_button);
        m_deviceIcon = (ImageView) m_deviceDetailsLayout.findViewById(R.id.device_details_header_icon);
        m_deviceNameText = (TextView) m_deviceDetailsLayout.findViewById(R.id.device_details_device_name);
        m_deviceStatusText = (TextView) m_deviceDetailsLayout.findViewById(R.id.device_details_status_text);
        m_deviceStatusIcon = (ImageView) m_deviceDetailsLayout.findViewById(R.id.device_details_status_icon);
        m_controlPanelLayout = (LinearLayout) m_deviceDetailsLayout.findViewById(R.id.device_details_control_panel);
        m_progressLayout = m_deviceDetailsLayout.findViewById(R.id.progressbarLayout);

        m_notificationsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final Intent launchIntent = new Intent(DeviceDetailsActivity.this, NotificationsActivity.class);
                launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, m_deviceId);
                launchIntent.putExtra(IntentExtraKeys.EXTRA_ACTIVITY_ORIGIN, "DeviceDetailsActivity");
                DeviceDetailsActivity.this.startActivity(launchIntent);

                markGoingToNewPage();
            }
        });

        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {

                Log.d(TAG, "onReceive: " + intent.getAction());

                String action = intent.getAction();
                if (intent == null || action == null) {
                    return;
                }
                Bundle extra = intent.getExtras();

                if (IntentActions.AJ_ON_DEVICE_FOUND.equals(action)) {
                    if (extra != null) {
                        // refresh the device UI states in case they've changed
                        UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                        if (m_deviceId.equals(deviceId)) {
                            Log.d(TAG, "onReceive device found: " + deviceId);
                            onDeviceFound();
                        }
                    }
                } else if (IntentActions.AJ_ON_DEVICE_ALIVE.equals(action)) {
                    if (extra != null) {
                        // refresh the device UI states in case they've changed
                        UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                        if (m_deviceId.equals(deviceId)) {
                            Log.d(TAG, "onReceive device alive: " + deviceId);
                            onDeviceAlive();
                        }
                    }
                } else if (IntentActions.AJ_ON_DEVICE_LOST.equals(action)) {
                    // refresh the device UI states in case they've changed
                    if (extra != null) {
                        UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                        if (m_deviceId.equals(deviceId)) {
                            Log.d(TAG, "onReceive device lost: " + deviceId);
                            // existing views are invalid
                            m_requestingPanels = false;
                            onDeviceLost();
                        }
                    }
                } else if (IntentActions.AJ_ON_DEVICE_STATUS_CHANGED.equals(action)) {
                    // refresh the device UI states in case they've changed
                    if (extra != null) {
                        UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                        if (m_deviceId.equals(deviceId)) {
                            Log.d(TAG, "onReceive device status changed: " + deviceId);
                            updateDeviceStatusView();
                            updateControlPanelView();
                        }
                    }
                } else if (IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED.equals(action) || IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED.equals(action)) {
                    // refresh this list so it's up to date the next time it's
                    // viewed
                    updateNotificationsCounterView();
                } else if (IntentActions.AJ_ON_CONTROL_PANELS_AVAILABLE.equals(action)) {
                    final UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    final String text = "Received PANELS_AVAILABLE for device: '" + deviceId + "'";
                    Log.d(TAG, text);
                    m_requestingPanels = false;
                    updateControlPanelsArray();
                    updateControlPanelView();
                } else if (IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST.equals(intent.getAction()) && intent.getExtras() != null) {
                    final UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    final String text = "Received SESSION_LOST for device: '" + deviceId + "' Calling Device.ping to check device health";
                    Log.d(TAG, text);
                    DeviceResponse pingStatus = device.ping();
                    if (pingStatus.getStatus().equals(ResponseCode.Status_OK)) {
                        Log.d(TAG, "onReceive(SESSION_LOST) Device.ping returned OK. Device is still alive.");
                    }
                }

                else if (IntentActions.AJ_ON_CONTROL_PANELS_ERROR_OCCURED.equals(intent.getAction()) && intent.getExtras() != null) {
                    final UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    final String reason = intent.getStringExtra(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON);
                    final String text = "Received an error notification: '" + reason + "' for device: '" + deviceId + "'";
                    Log.w(TAG, text);
                    if (m_deviceId.equals(deviceId)) {
                        m_requestingPanels = false;
                        DeviceResponse pingStatus = device.ping();
                        if (pingStatus.getStatus().equals(ResponseCode.Status_OK)) {
                            Log.d(TAG, "Device is still alive.");
                        }
                    }
                }

                else if (IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED.equals(intent.getAction()) && intent.getExtras() != null) {
                    final UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                    final String reason = intent.getStringExtra(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON);
                    Log.w(TAG, "Received an error notification: '" + reason + "' for device: '" + deviceId + "'");
                    // if (m_deviceId.equals(deviceId)) {
                    // }
                }
            }
        };

        // about actions
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_LOST);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_FOUND);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_ALIVE);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE);
        m_filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED);
        m_filter.addAction(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED);
        m_filter.addAction(IntentActions.AJ_ON_DEVICE_STATUS_CHANGED);

        // control panel actions
        m_filter.addAction(IntentActions.AJ_ON_CONTROL_PANELS_AVAILABLE);
        m_filter.addAction(IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST);
        m_filter.addAction(IntentActions.AJ_ON_CONTROL_PANELS_ERROR_OCCURED);
        m_filter.addAction(IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED);
        registerReceiver(m_receiver, m_filter);

        // The view to show when device is not connected
        // m_deviceDetailsNotConnectedText = (TextView)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_text_not_connected);
        m_noControlsText = (TextView) m_deviceDetailsLayout.findViewById(R.id.device_details_text_no_controls);
        // m_stateLayout = (ViewGroup)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_state_layout);
        // m_deviceUnconfiguredText1 = (TextView)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_state_text_1);
        // m_deviceUnconfiguredText2 = (TextView)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_state_text_2);
        // m_deviceUnconfiguredText3 = (TextView)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_state_text_3);
        // m_ConfigureDeviceButton = (Button)
        // m_deviceDetailsLayout.findViewById(R.id.device_details_state_button_1);
        m_deviceUnavailableLayout = (ViewGroup) m_deviceDetailsLayout.findViewById(R.id.device_details_unavailable_layout);
        m_deviceUnavailableText = (TextView) m_deviceDetailsLayout.findViewById(R.id.device_details_text_unavailable);

        // setup
        final OnClickListener onBack = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                asynchStopControlPanelSession();
                markGoingBack();
                DeviceDetailsActivity.this.finish();
            }
        };

        View view = m_deviceDetailsLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);
        m_titleText.setText(R.string.device_details_title);

        m_manageDevicesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final Intent launchIntent = new Intent(DeviceDetailsActivity.this, ManageDeviceActivity.class);
                launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, m_deviceId);
                DeviceDetailsActivity.this.startActivity(launchIntent);

                markGoingToNewPage();
            }
        });

        setContentView(m_deviceDetailsLayout);

        m_controlButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_selectPanelAlert == null) {
                    // get dialog layout
                    final View dialogLayout = getLayoutInflater().inflate(R.layout.alert_dialog, null);
                    final TextView titleView = (TextView) dialogLayout.findViewById(R.id.alert_title_text);
                    final TextView messageView = (TextView) dialogLayout.findViewById(R.id.alert_msg_text);
                    final Button posView = (Button) dialogLayout.findViewById(R.id.alert_button_pos);
                    final Button neutralView = (Button) dialogLayout.findViewById(R.id.alert_button_neutral);
                    final Button negView = (Button) dialogLayout.findViewById(R.id.alert_button_neg);

                    final AlertDialog.Builder builder = new AlertDialog.Builder(DeviceDetailsActivity.this);

                    builder.setTitle("select panel");
                    builder.setView(dialogLayout);
                    titleView.setVisibility(View.GONE);
                    messageView.setVisibility(View.GONE);
                    neutralView.setVisibility(View.GONE);

                    builder.setSingleChoiceItems(m_panelsNameArray, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            m_selectedPanelIndex = which;
                        }
                    });

                    posView.setText(android.R.string.ok);
                    posView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            m_selectedPanel = m_panelsArray.get(m_selectedPanelIndex);

                            // existing views are invalid
                            m_controlPanelView = null;
                            updateControlPanelView();

                            if (m_selectPanelAlert != null) {
                                m_selectPanelAlert.dismiss();
                                m_selectPanelAlert = null;
                            }
                        }
                    });

                    negView.setText(android.R.string.cancel);
                    negView.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (m_selectPanelAlert != null) {
                                m_selectPanelAlert.dismiss();
                                m_selectPanelAlert = null;
                            }
                        }
                    });

                    m_selectPanelAlert = builder.create();
                }

                if (!m_selectPanelAlert.isShowing()) {
                    m_selectPanelAlert.show();
                }
            }
        });

        // TODO: Add sample data
        // TODO: implement connection error / no control service state
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (m_receiver != null) {
            registerReceiver(m_receiver, m_filter);
        }

        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            device.setHandleAsynchPings(false);
        }

        updateNotificationsCounterView();
        updateDeviceStatusView();

        if (device != null) {

            // check if device has not lost the CP session. If so, the current CP is stale
            if (device.getPanels() == null) {
                m_controlPanelView = null;
                m_selectedPanel = null;
            }

            // check if language has changed. If so, the current CP is stale
            if (!device.getDefaultLanguage().equalsIgnoreCase(m_deviceLanguage)) {
                Log.i(TAG, String.format("onResume language has changed from %s to %s. Refetching control panel", m_deviceLanguage, device.getDefaultLanguage()));
                m_controlPanelView = null;
                m_selectedPanel = null;
                m_deviceLanguage = device.getDefaultLanguage();
            }
        }

        updateControlPanelView();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

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

    @Override
    public void onBackPressed() {
        asynchStopControlPanelSession();
        super.onBackPressed();
    }

    private void asynchStopControlPanelSession() {
        Log.d(TAG, "asynchStopControlPanelSession");
        final AsyncTask<Object, Object, Object> bkgdTask = new AsyncTask<Object, Object, Object>() {
            private Device m_asyncDevice;

            @Override
            protected void onPreExecute() {
                m_asyncDevice = UIUtil.getDevice(m_deviceId);
            }

            @Override
            protected Object doInBackground(Object... arg0) {
                if (m_asyncDevice != null) {
                    m_asyncDevice.stopControlPanelSession();
                }
                return null;
            }
        };
        bkgdTask.execute();
    }

    private void asyncRequestControlPanels() {

        Log.d(TAG, "asyncRequestControlPanels");

        if (m_requestingPanels) {
            Log.d(TAG, "asyncRequestControlPanels already requesting. avoid calling join session twice");
            return;
        }
        m_requestingPanels = true;
        Log.d(TAG, "asyncRequestControlPanels no current request. requesting..");

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
                    m_asyncDevice.requestControlPanels();
                }
                return null;
            }
        };
        bkgdTask.execute();
    }

    private class RetrieveControlPanelViewTask extends AsyncTask<DeviceControlPanel, Void, UIElement> {

        @Override
        // Get the Control panel root element in the background
        protected UIElement doInBackground(DeviceControlPanel... params) {
            UIElement rootContainerElement = null;
            try {
                DeviceControlPanel controlPanel = params[0];
                rootContainerElement = controlPanel.getRootElement(DeviceDetailsActivity.this);
            } catch (ControlPanelException e) {
                String errMsg = "Failed to access remote methods of control panel, Error: '" + e.getMessage() + "'";
                Log.e(TAG, errMsg, e);
            }
            return rootContainerElement;
        }

        @Override
        protected void onPostExecute(UIElement rootContainerElement) {
            if (rootContainerElement == null) {
                return;
            }
            Log.d(TAG, "onPostExecute: creating ControlPanelAdapter");
            m_controlPanelAdapter = new ControlPanelAdapter(DeviceDetailsActivity.this, DeviceDetailsActivity.this);
            UIElementType elementType = rootContainerElement.getElementType();
            Log.d(TAG, "Found root container of type: '" + elementType + "'");

            if (elementType == UIElementType.CONTAINER) {
                ContainerWidget container = ((ContainerWidget) rootContainerElement);
                m_controlPanelView = m_controlPanelAdapter.createContainerView(container);
                updateControlPanelView();
            } else if (elementType == UIElementType.ALERT_DIALOG) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Window w = getWindow();
                        if (w != null) {
                            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                        }
                    }
                });
                AlertDialogWidget alertDialogWidget = ((AlertDialogWidget) rootContainerElement);
                m_controlPanelAlertDialog = m_controlPanelAdapter.createAlertDialog(alertDialogWidget);
                m_controlPanelAlertDialog.setCancelable(false);
                m_controlPanelAlertDialog.setCanceledOnTouchOutside(false);
                m_controlPanelAlertDialog.setOnDismissListener(new AlertDialog.OnDismissListener() {

                    @Override
                    public void onDismiss(DialogInterface arg0) {
                        String text = "Dialog dismissed.";
                        Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show();
                        Log.d(TAG, text);
                    }
                });
                m_controlPanelAlertDialog.show();
            }
        }
    }

    protected void asyncRetrieveControlPanelView(DeviceControlPanel controlPanel) {
        Log.d(TAG, "asyncRetrieveControlPanelView");
        RetrieveControlPanelViewTask task = new RetrieveControlPanelViewTask();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new DeviceControlPanel[] { controlPanel }));
        } else {
            task.execute(new DeviceControlPanel[] { controlPanel });
        }
    }

    /**
     * This method is called from BaseActivity after the password dialog has
     * saved a new password to try with the device.
     */
    @Override
    protected void onNewDevicePassword(UUID deviceId) {
        Log.i(TAG, "onNewDevicePassord, retrying getting control panel view");
        if (deviceId.equals(m_deviceId)) {
            // existing views are invalid
            m_controlPanelView = null;
            updateControlPanelView();
        }
    }

    // ======================= Class MyDeviceControlPanel
    // ==========================
    public class DeviceControlPanelWrapper {

        // This class is used only to ovveride toString().
        // The spinner adapter uses this method to display the elements names in
        // the spinner.
        DeviceControlPanel panel;

        public DeviceControlPanelWrapper(DeviceControlPanel panel) {
            this.panel = panel;
        }

        @Override
        public String toString() {
            return panel.getCollection().getName();
        }
    }

    // ======================== end of MyDeviceControlPanel
    // ============================

    // ======================== ControlPanelExceptionHandler implementation ====
    @Override
    public void handleControlPanelException(final ControlPanelException e) {
        Log.e(TAG, "handleControlPanelException", e);
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "handleControlPanelException calling Device.ping to check device health");
                    DeviceResponse pingStatus = device.ping();
                    if (pingStatus.getStatus().equals(ResponseCode.Status_OK)) {
                        Log.d(TAG, "handleControlPanelException Device.ping returned OK. Device is still alive.");
                    }
                }
            }).start();
        }
    }

    // ======================== ControlPanelEventsListener implementation ======

    @Override
    public void errorOccurred(DeviceControlPanel device, final String reason) {
        Log.d(TAG, "errorOccurred");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final String text = "Received an error notification: '" + reason + "'";
                Log.w(TAG, text);
            }
        });
    }

    @Override
    public void metadataChanged(final DeviceControlPanel device, final UIElement uielement) {
        Log.d(TAG, "metadataChanged");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "METADATA_CHANGED : Received metadata changed signal, device: '" + device.getDevice().getDeviceId() + "', ObjPath: '" + uielement.getObjectPath() + "', element type: '"
                        + uielement.getElementType() + "'");
                m_controlPanelAdapter.onMetaDataChange(uielement);
            }
        });
    }

    @Override
    public void notificationActionDismiss(DeviceControlPanel device) {

        Log.d(TAG, "notificationActionDismiss");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (m_controlPanelAlertDialog != null && m_controlPanelAlertDialog.isShowing()) {
                    m_controlPanelAlertDialog.dismiss();
                }
            }
        });
    }

    @Override
    public void valueChanged(DeviceControlPanel device, final UIElement uielement, final Object newValue) {

        Log.d(TAG, "valueChanged");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_controlPanelAdapter.onValueChange(uielement, newValue);
                final String text = "Received value changed signal, ObjPath: '" + uielement.getObjectPath() + "', NewValue: '" + newValue + "'";
                Log.d(TAG, text);
            }
        });
    }

    // ======================== End ControlPanelEventsListener implementation
    // ======

    // load the panels that the control panel service returned, into the panel
    // selector
    private void updateControlPanelsArray() {

        Log.d(TAG, "updateControlPanelsArray");
        // clear
        m_panelsArray.clear();

        Device currentDevice = UIUtil.getDevice(m_deviceId);
        if (currentDevice == null) {
            Log.w(TAG, "No device found for device id " + m_deviceId);
            return;
        }

        // find the default language of the device

        String defaultLanguage = currentDevice.getDefaultLanguage(); // "en_US",
                                                                     // "es_SP"
        if (defaultLanguage == null || defaultLanguage.length() == 0) {
            defaultLanguage = "en";
        }

        Log.d(TAG, "The default language is " + defaultLanguage);

        // About may report the default language as en_US, which is not
        // according to RFC 5646
        // ControlPanel, on the other side, normalizes en_US to en-US according
        // to RFC 5646
        // In order to have a match, we will normalize the About default
        // language according to RFC 5646
        String defaultLanguage_IETF_RFC5646 = defaultLanguage.replace('_', '-'); // "en-US",
                                                                                 // "es-SP"

        Log.d(TAG, "The RFC5646 default language is " + defaultLanguage_IETF_RFC5646);

        // iterate through the panels ControlPanelCollection.
        Collection<ControlPanelCollection> panels = currentDevice.getPanels();
        if (panels != null) {
            Iterator<ControlPanelCollection> panelsIterator = panels.iterator();
            while (panelsIterator.hasNext()) {
                ControlPanelCollection cpCollection = panelsIterator.next();

                String localLanguage = defaultLanguage_IETF_RFC5646;
                ;

                Set<String> languages = cpCollection.getLanguages();
                if (languages.contains(defaultLanguage_IETF_RFC5646)) {
                    Log.i(TAG, "Found default language: " + defaultLanguage_IETF_RFC5646);
                    localLanguage = defaultLanguage_IETF_RFC5646;
                } else if (!defaultLanguage_IETF_RFC5646.equals("en") && languages.contains("en")) {
                    Log.i(TAG, "Didn't find default language: " + defaultLanguage_IETF_RFC5646 + " displaying English");
                    localLanguage = "en";
                } else if (!languages.isEmpty()) {
                    Iterator<String> languageIt = languages.iterator();
                    if (languageIt != null && languageIt.hasNext()) {
                        localLanguage = languageIt.next();
                        Log.i(TAG, "Didn't find default language: " + defaultLanguage_IETF_RFC5646 + " and no English, displaying " + localLanguage);
                    }
                } else {
                    Log.i(TAG, "Control Panel Collection has no language. displaying default language: " + defaultLanguage_IETF_RFC5646);
                }

                Iterator<DeviceControlPanel> deviceControlPanelIterator = cpCollection.getControlPanels().iterator();
                while (deviceControlPanelIterator.hasNext()) {
                    DeviceControlPanel currentDeviceControlPanel = deviceControlPanelIterator.next();
                    if (currentDeviceControlPanel.getLanguage().equals(localLanguage)) {
                        m_panelsArray.add(new DeviceControlPanelWrapper(currentDeviceControlPanel));
                    }
                }
            }
        }

        // fill the panels array
        if (m_panelsArray.size() > 0) {
            m_panelsNameArray = new String[m_panelsArray.size()];
            for (int i = 0; i < m_panelsArray.size(); i++) {
                Log.d(TAG, "updateControlPanelsArray adding item: " + m_panelsArray.get(i).toString());
                m_panelsNameArray[i] = m_panelsArray.get(i).toString();
            }
            m_selectedPanel = m_panelsArray.get(0);
        }
    }

    // update the control panel view
    private void updateControlPanelView() {
        Log.d(TAG, "updateControlPanelView of device: " + m_deviceId);

        final Device device = UIUtil.getDevice(m_deviceId);
        if (device == null) {
            Log.w(TAG, "updateControlPanelView device not found: " + m_deviceId);
            showDeviceUnavailableView();
            return;
        }

        final DeviceStatus status = UIUtil.getDeviceStatus(this, device);
        Log.d(TAG, "updateControlPanelView device status is: " + status);
        switch (status) {
        case AVAILABLE:
            Log.d(TAG, "updateControlPanelView device is available: " + m_deviceId);
            if (!device.isServiceSupported(Device.ServiceType.CONTROL_PANEL)) {
                // device doesn't support control panel
                Log.w(TAG, "updateControlPanelView device doesn't support control panel service: " + m_deviceId);
                showNoControlPanelView();
            } else if (m_controlPanelView != null) {
                // Got the CP View. a simple repaint
                Log.d(TAG, "updateControlPanelView got control view for this device. showing it.");
                showControlPanelView();
            } else {
                if (m_selectedPanel != null) {
                    // We have a selected panel, but no View yet
                    Log.d(TAG, "updateControlPanelView got control panel but no view. requesting from device:" + m_deviceId);
                    showRetrievingControlPanelView();
                    asyncRetrieveControlPanelView(m_selectedPanel.panel);
                } else {
                    // no panels. get them
                    Log.d(TAG, "updateControlPanelView no control panel yet. requesting from device: " + m_deviceId);
                    showRetrievingControlPanelView();
                    asyncRequestControlPanels();
                }
            }
            break;
        case UNAVAILABLE:
        case GONE:
            Log.d(TAG, "updateControlPanelView device is unavailable: " + m_deviceId);
            showDeviceUnavailableView();
            break;
        default:
            break;
        }
    }

    private void showRetrievingControlPanelView() {
        Log.w(TAG, "showRetrievingControlPanelView");
        m_controlButton.setVisibility(View.GONE);
        m_progressLayout.setVisibility(View.VISIBLE);
        return;
    }

    // toggle to no-control-view
    private void showNoControlPanelView() {
        Log.w(TAG, "showNoControlPanelView");
        m_progressLayout.setVisibility(View.GONE);
        m_controlButton.setVisibility(View.GONE);
        m_controlPanelLayout.setVisibility(View.GONE);
        m_deviceUnavailableLayout.setVisibility(View.GONE);
        m_noControlsText.setVisibility(View.VISIBLE);
        return;
    }

    // toggle to control panel view
    private void showControlPanelView() {
        Log.d(TAG, "showControlPanelView");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_progressLayout.setVisibility(View.GONE);
                m_deviceUnavailableLayout.setVisibility(View.GONE);
                m_noControlsText.setVisibility(View.GONE);
                m_controlPanelLayout.removeAllViews();
                m_controlPanelLayout.addView(m_controlPanelView);
                m_controlPanelLayout.setVisibility(View.VISIBLE);

                // enable the selector button only if there is a choice.
                if (m_panelsArray.size() > 1) {
                    m_controlButton.setVisibility(View.VISIBLE);
                } else {
                    m_controlButton.setVisibility(View.GONE);
                }
            }
        });
    }

    // toggle to unavailable-device-view
    private void showDeviceUnavailableView() {
        Log.d(TAG, "showDeviceUnavailableView");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_progressLayout.setVisibility(View.GONE);
                m_controlButton.setVisibility(View.GONE);
                m_controlPanelLayout.setVisibility(View.GONE);
                m_noControlsText.setVisibility(View.GONE);
                m_deviceUnavailableLayout.setVisibility(View.VISIBLE);

            }
        });
    }

    // update the notification button
    private void updateNotificationsCounterView() {
        String notificationCount = "";
        if (NotificationsManagerImpl.getInstance().getUnreadNotificationsCount(m_deviceId) > 0) {
            notificationCount = "" + NotificationsManagerImpl.getInstance().getUnreadNotificationsCount(m_deviceId);
        }
        Log.d(TAG, "updateNotificationsCounterView count=" + notificationCount);
        m_notificationsText.setText(notificationCount);
    }

    // update the device status views
    private void updateDeviceStatusView() {
        Log.d(TAG, "updateDeviceStatusView");
        final Device device = UIUtil.getDevice(m_deviceId);
        if (device == null) {
            Log.w(TAG, "updateDeviceStatusView device not found: " + m_deviceId);
            m_deviceStatusText.setText(UIUtil.getDeviceStatusTextId(DeviceStatus.UNAVAILABLE));
            m_deviceStatusIcon.setImageResource(UIUtil.getDeviceStatusIconId(DeviceStatus.UNAVAILABLE));
        } else {
            DeviceStatus status = UIUtil.getDeviceStatus(this, device);
            UIUtil.setDeviceIcon(m_deviceIcon, device.getId());
            m_deviceNameText.setText(device.getFriendlyName());
            m_deviceStatusText.setText(UIUtil.getDeviceStatusTextId(status));
            m_deviceStatusIcon.setImageResource(UIUtil.getDeviceStatusIconId(status));
        }
    }

    private void onDeviceFound() {
        Log.d(TAG, "onDeviceFound. updating the views");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                m_controlPanelView = null;
                m_selectedPanel = null;
                updateDeviceStatusView();
                updateControlPanelView();
            }
        });
    }

    private void onDeviceAlive() {
        Log.d(TAG, "onDeviceAlive. updating the views");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                updateDeviceStatusView();
                updateControlPanelView();
            }
        });
    }

    private void onDeviceLost() {
        Log.d(TAG, "onDeviceLost. updating the views");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                // existing views are invalid
                m_controlPanelView = null;
                m_selectedPanel = null;
                updateDeviceStatusView();
                showDeviceUnavailableView();
                final Device device = UIUtil.getDevice(m_deviceId);
                launchBasicAlertDialog(getString(R.string.alert_title_device_not_found), 0, getString(R.string.alert_message_device_not_found, device == null ? "device" : device.getFriendlyName()),
                        getString(R.string.dismiss), null, null, null, null, null);
            }
        });
    }

}
