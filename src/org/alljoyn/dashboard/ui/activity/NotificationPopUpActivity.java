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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.alljoyn.ioe.controlpaneladapter.ControlPanelAdapter;
import org.alljoyn.ioe.controlpanelservice.ControlPanelCollection;
import org.alljoyn.ioe.controlpanelservice.ControlPanelException;
import org.alljoyn.ioe.controlpanelservice.ui.AlertDialogWidget;
import org.alljoyn.ioe.controlpanelservice.ui.ContainerWidget;
import org.alljoyn.ioe.controlpanelservice.ui.ControlPanelEventsListener;
import org.alljoyn.ioe.controlpanelservice.ui.DeviceControlPanel;
import org.alljoyn.ioe.controlpanelservice.ui.UIElement;
import org.alljoyn.ioe.controlpanelservice.ui.UIElementType;
import org.alljoyn.ns.NotificationText;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
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
import android.util.Pair;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NotificationPopUpActivity extends BaseActivity {

    private ViewGroup m_NotificationWithActionPopUpActivityLayout = null;

    private TextView m_titleText;
    private TextView m_noticationMessage;
    private Button m_dismissButton;
    private Button m_launchButton;
    private TextView m_deviceStatusText;
    private ImageView m_deviceStatusIcon;
    private TextView m_deviceNameText;
    private ImageView m_deviceIcon;

    private LinearLayout m_progressLayout;

    private View m_buttonsLayout;
    private ControlPanelAdapter m_controlPanelAdapter;
    private final IntentFilter m_controlPanelFilter = new IntentFilter();
    private View m_controlPanelView;
    private LinearLayout m_controlPanelLayout = null;
    private AlertDialog m_controlPanelAlertDialog;

    private List<NotificationWrapper> m_notificationsList;

    private final static String TAG = "NotificationWithActionPopUpActivity";

    private boolean isListentingToControlPanelReceiverRequired = false;

    private Device device;

    private final BroadcastReceiver m_controlPanelReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final Bundle extra = intent.getExtras();

            if (extra != null && IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST.equals(action)) {

            } else if (extra != null && IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED.equals(action)) {

            } else if (extra != null && IntentActions.AJ_ON_CONTROL_POPUP_AVAILABLE.equals(action)) {

                ControlPanelCollection notificationActionCollection = device.getControlPopups();

                DeviceControlPanel deviceControlPanel = null;

                final String objPath = notificationActionCollection.getObjectPath();
                Log.d(TAG, "ControlPanelCollection objPath: " + objPath);

                Collection<DeviceControlPanel> controlPanels = notificationActionCollection.getControlPanels();
                for (DeviceControlPanel controlPanel : controlPanels) {
                    String cpLanugage = controlPanel.getLanguage();
                    Log.d(TAG, String.format("Control Panel language: %s", cpLanugage));
                    if (cpLanugage.equalsIgnoreCase(device.getDefaultLanguage())) {
                        deviceControlPanel = controlPanel;
                        Log.d(TAG, "Found a control panel that matches the default language");
                        break;
                    }
                }
                if (deviceControlPanel == null && !controlPanels.isEmpty()) {
                    Log.w(TAG, "Could not find a control panel that matches phone the default language");
                    deviceControlPanel = controlPanels.iterator().next();
                    Log.d(TAG, String.format("Defaulting to the control panel of language: %s", deviceControlPanel.getLanguage()));
                }

                if (deviceControlPanel == null) {
                    Log.e(TAG, "DeviceControlPanel is null!!! Can't continue");
                    return;
                }

                asyncRetrieveControlPanelView(deviceControlPanel);
            }
        }

    };


    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            int messageId = intent.getIntExtra(IntentExtraKeys.NOTIFICATION_ID, -1);
            UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);

            Log.i(TAG, "got dismissed." + messageId + " For device " + deviceId);
            launchBasicAlertDialog(context.getString(R.string.notification_dismissed_dialog_title), context.getString(R.string.notification_dismissed_dialog_msg), context.getString(R.string.dismiss), new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            }, null, null);

        }
    };
    protected void asyncRetrieveControlPanelView(DeviceControlPanel controlPanel) {
        RetrieveControlPanelViewTask task = new RetrieveControlPanelViewTask();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new DeviceControlPanel[] { controlPanel }));
        } else {
            task.execute(new DeviceControlPanel[] { controlPanel });
        }
    }

    private class RetrieveControlPanelViewTask extends AsyncTask<DeviceControlPanel, Void, UIElement> {

        @Override
        // Get the Control panel root element in the background
        protected UIElement doInBackground(DeviceControlPanel... params) {
            UIElement rootContainerElement = null;
            try {
                DeviceControlPanel controlPanel = params[0];
                rootContainerElement = controlPanel.getRootElement(new ControlPanelEventsListener() {

                    @Override
                    public void valueChanged(DeviceControlPanel device, final UIElement uielement, final Object newValue) {
                        Log.d(TAG, "valueChanged Received value changed signal, ObjPath: '" + uielement.getObjectPath() + "', NewValue: '" + newValue + "'");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                m_controlPanelAdapter.onValueChange(uielement, newValue);
                            }
                        });
                    }

                    @Override
                    public void notificationActionDismiss(DeviceControlPanel arg0) {
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
                    public void metadataChanged(final DeviceControlPanel device, final UIElement uielement) {
                        Log.d(TAG, "METADATA_CHANGED : Received metadata changed signal, device: '" + device.getDevice().getDeviceId() + "', ObjPath: '" + uielement.getObjectPath()
                                + "', element type: '" + uielement.getElementType() + "'");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                m_controlPanelAdapter.onMetaDataChange(uielement);
                            }
                        });
                    }

                    @Override
                    public void errorOccurred(DeviceControlPanel device, final String reason) {
                        Log.w(TAG, "Received an error notification: '" + reason + "'");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            }
                        });
                    }
                });
            } catch (ControlPanelException e) {
                Log.e(TAG, "Failed to access remote methods of control panel", e);
            }
            return rootContainerElement;
        }

        @Override
        protected void onPostExecute(UIElement rootContainerElement) {
            if (rootContainerElement == null) {
                return;
            }
            Log.d(TAG, "onPostExecute: creating ControlPanelAdapter");
            m_controlPanelAdapter = new ControlPanelAdapter(NotificationPopUpActivity.this, NotificationPopUpActivity.this);
            UIElementType elementType = rootContainerElement.getElementType();
            Log.d(TAG, "Found root container of type: '" + elementType + "'");

            if (elementType == UIElementType.CONTAINER) {
                ContainerWidget container = ((ContainerWidget) rootContainerElement);
                m_controlPanelView = m_controlPanelAdapter.createContainerView(container);
                m_controlPanelLayout.removeAllViews();
                m_controlPanelLayout.addView(m_controlPanelView);
                m_controlPanelLayout.setVisibility(View.VISIBLE);
                m_progressLayout.setVisibility(View.GONE);
            } else if (elementType == UIElementType.ALERT_DIALOG) {
            	
            	
            	
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    	
                    	m_buttonsLayout.setVisibility(View.VISIBLE);                        
                        m_noticationMessage.setVisibility(View.VISIBLE);
                        m_progressLayout.setVisibility(View.GONE);
                        /*
                        Window w = getWindow();
                        if (w != null) {
                            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));                            
                        }*/
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




    /**
     * Encapsulated navigation information needed to navigate between notification  messages.
     */
    private class Navigation {
        // public Navigation(
        List<NotificationWrapper> list;
        int messageId;

        public Navigation(List<NotificationWrapper> list, int messageId) {
            this.list = list;
            this.messageId = messageId;
        }

        public Pair<Boolean, Integer> isLeftCapable() {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getNotification().getMessageId() == messageId) {
                    if (i == 0) {
                        return new Pair<Boolean, Integer>(false, 0);
                    } else {
                        return new Pair<Boolean, Integer>(true, list.get(i - 1).getNotification().getMessageId());
                    }
                }
            }
            return new Pair<Boolean, Integer>(false, 0);
        }

        public Pair<Boolean, Integer> isRightCapable() {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getNotification().getMessageId() == messageId) {
                    if (i == list.size() - 1) {
                        return new Pair<Boolean, Integer>(false, 0);
                    } else {
                        return new Pair<Boolean, Integer>(true, list.get(i + 1).getNotification().getMessageId());
                    }
                }
            }
            return new Pair<Boolean, Integer>(false, 0);
        }

        public Pair<Integer, Integer> getRelativePosition() {
            int position = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getNotification().getMessageId() == messageId) {
                    position = i + 1;
                    break;
                }
            }

            return new Pair<Integer, Integer>(position, list.size());
        }
    }

    private void fillScreen(final int messageId, boolean isSwipe) {

        final NotificationWrapper notif = getNoficication(messageId);

        if (notif == null)
        {
            // shouldn't happen because the list is not dynamic.
            Log.e(TAG, "fillScreen: notification is null, can't continue");
            return;
        }

        final UUID deviceId = notif.getNotification().getAppId();

        device = DeviceManagerImpl.getInstance().getDevice(deviceId);

        if (device != null) {
            m_titleText.setText(device.getFriendlyName());
        }
        if (isSwipe) {

            final Navigation nav = new Navigation(m_notificationsList, messageId);
            final TextView navText = (TextView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.nav_text);
            navText.setVisibility(View.VISIBLE);

            navText.setText(nav.getRelativePosition().first + "/" + nav.getRelativePosition().second);

            if (device != null){
                m_titleText.setText(device.getFriendlyName());
            }
        }

        View view = m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                NotificationPopUpActivity.this.finish();
            }
        });


        m_controlPanelLayout = (LinearLayout) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.device_details_control_panel);
        m_noticationMessage = (TextView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.notification_message);
        m_noticationMessage.setText("");

        m_dismissButton = (Button) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.dismiss_button);
        m_dismissButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationsManagerImpl.getInstance().dismiss(messageId, deviceId);
                notif.getNotification().dismiss();
                finish();
            }
        });

        m_buttonsLayout = m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.button_layout);
        m_deviceStatusText = (TextView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.device_details_status_text);
        m_deviceStatusIcon = (ImageView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.device_details_status_icon);
        m_deviceNameText = (TextView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.device_details_device_name);
        m_deviceIcon = (ImageView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.device_details_header_icon);

        m_launchButton = (Button) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.launch_button);
        m_progressLayout = (LinearLayout) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.progressbar_layout);

        m_launchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	
            	m_buttonsLayout.setVisibility(View.GONE);
                //m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.title_right_side).setVisibility(View.GONE);

                m_noticationMessage.setVisibility(View.GONE);
                m_progressLayout.setVisibility(View.VISIBLE);
                m_buttonsLayout.setVisibility(View.GONE);
                isListentingToControlPanelReceiverRequired = true;

                registerReceiver(m_controlPanelReceiver, m_controlPanelFilter, null, AlljoynOnApplication.getHanlder());
                device.requestControlPopup(notif.getNotification().getResponseObjectPath());
            }
        });

        View launch_button = m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.launch_button);
        if (!((notif.getNotification().getResponseObjectPath() != null) && !notif.getNotification().getResponseObjectPath().isEmpty())) {
            launch_button.setVisibility(View.GONE);
        } else {
            launch_button.setVisibility(View.VISIBLE);
        }

        List<NotificationText> list = notif.getNotification().getText();

        for (NotificationText text : list) {

            m_noticationMessage.setText(m_noticationMessage.getText() + text.getText() + " ");
        }
        updateDeviceStatusView(deviceId);

    }

    private NotificationWrapper getNoficication(int messageId){
        if (m_notificationsList == null || m_notificationsList.isEmpty()) {
            return null;
        }
        for (int i = 0; i < m_notificationsList.size(); i++) {
            if (m_notificationsList.get(i).getNotification().getMessageId() == messageId) {
                return m_notificationsList.get(i);
            }
        }
        return null;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerReceiver(notificationReceiver, new IntentFilter(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED));

        // prevent tapping outside from closing the activity
        blockOutsideClicks();

        m_NotificationWithActionPopUpActivityLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_notification_popup, null);

        m_controlPanelFilter.addAction(IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST);
        m_controlPanelFilter.addAction(IntentActions.AJ_ON_CONTROL_POPUP_AVAILABLE);
        m_controlPanelFilter.addAction(IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED);
        m_titleText = (TextView) m_NotificationWithActionPopUpActivityLayout.findViewById(R.id.navtitleback_title_text);
        Intent intent = getIntent();

        Bundle extras = intent.getExtras();
        if (extras!=null){
            int messageId = extras.getInt(IntentExtraKeys.NOTIFICATION_ID);
            UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
            m_notificationsList = NotificationsManagerImpl.getInstance().getNotificationList(deviceId);
            fillScreen(messageId, extras.containsKey(IntentExtraKeys.EXTRA_SWIPE_ALLOWED));
        }
        setContentView(m_NotificationWithActionPopUpActivityLayout);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlljoynOnApplication.getInstance().setNotificationWithActionPopUpDisplayed(false);
        if (isListentingToControlPanelReceiverRequired) {
            unregisterReceiver(m_controlPanelReceiver);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlljoynOnApplication.getInstance().setNotificationWithActionPopUpDisplayed(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationReceiver != null){
            try{
                unregisterReceiver(notificationReceiver);
            }
            catch (Exception e){
            	Log.e(TAG,"onDestroy() ",e);
            }
        }
    }

    // update the device status views
    private void updateDeviceStatusView(UUID deviceId) {
        // Log.d(TAG, "updateDeviceStatusView");
        final Device device = UIUtil.getDevice(deviceId);
        if (device == null) {
            // Log.w(TAG, "updateDeviceStatusView device not found: " +
            // deviceId);
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

}
