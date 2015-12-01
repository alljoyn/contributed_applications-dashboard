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
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.alljoyn.ioe.controlpaneladapter.ControlPanelAdapter;
import org.alljoyn.ioe.controlpaneladapter.ControlPanelExceptionHandler;
import org.alljoyn.ioe.controlpanelservice.ControlPanelCollection;
import org.alljoyn.ioe.controlpanelservice.ControlPanelException;
import org.alljoyn.ioe.controlpanelservice.ui.AlertDialogWidget;
import org.alljoyn.ioe.controlpanelservice.ui.ControlPanelEventsListener;
import org.alljoyn.ioe.controlpanelservice.ui.DeviceControlPanel;
import org.alljoyn.ioe.controlpanelservice.ui.UIElement;
import org.alljoyn.ioe.controlpanelservice.ui.UIElementType;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.ColorDrawable;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public abstract class BaseActivity extends Activity implements ControlPanelExceptionHandler {

    protected AlertDialog m_passwordDialog;
    boolean wrongAjPassword = false;
    protected AlertDialog m_alertDialog = null;
    protected BroadcastReceiver m_baseReceiver = null;
    protected IntentFilter m_baseFilter = null;
    private final Map<String, AlertDialog> m_controlDialogs = new HashMap<String, AlertDialog>(2);

    private ProgressDialog factoryResetProgressDialog;
    private Timer m_factoryResetTimer;
    private UUID factoryResetDeviceId = null;

    private boolean m_goingBack = false;
    private boolean m_goingToNewPage = false;

    public boolean m_isShowing = false;

    private final String TAG = getClass().getName();

    private volatile boolean  isDestroying=false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkForNotificationWithAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // This activity might be launched from native notification bar due to a
        // Notification w/ Action that was received when the app was in
        // background.
        // parse the calling intent and extract the needed Control Panel
        // information.
        checkForNotificationWithAction(getIntent());

        // register a broadcast receiver
        m_baseReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "receive intent " + intent.getAction());
                final String action = intent.getAction();
                final Bundle extra = intent.getExtras();
                if (action == null) {
                    Log.i(TAG, "action == null. return");
                    return;
                }
                if (extra == null) {
                    Log.i(TAG, "extra == null. continue.");
                }


                if (extra!=null && IntentActions.AJ_ON_DEVICE_AUTHENTICATION_ERROR.equals(action))
                {
                    UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                    String ssid = extra.getString(IntentExtraKeys.EXTRA_DEVICE_SSID);
                    Device device = DeviceManagerImpl.getInstance().getDevice(deviceId);
                    if (device != null)
                    {
                        Log.i(TAG, "device != null. launch Password Prompt ");
                        // user needs to confirm the device password - launch this from whatever activity the user is currently on
                    }
                    else
                    {
                        device = DeviceManagerImpl.getInstance().getDeviceBySSID(ssid);
                        if (device == null){
                            Log.i(TAG, "device == null. Don't launch Password Prompt ");
                            return;
                        }
                    }
                    wrongAjPassword = true;
                    launchPasswordPrompt(deviceId, ssid);
                }
                else if (extra!=null && IntentActions.AJ_ON_DEVICE_PASSWORD_SAVED.equals(action))
                {
                    wrongAjPassword = false;
                    // the device password has changed. the activity may want to repeat the action that failed due to wrong passord.
                    final UUID deviceId = (UUID)extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                    if (deviceId != null){
                        onNewDevicePassword(deviceId);
                    }
                    else{
                        final String deviceSSID = extra.getString(IntentExtraKeys.EXTRA_DEVICE_SSID);;
                        if (deviceSSID != null){
                            onNewDevicePassword(deviceSSID);
                        }
                    }
                }
                else if (IntentActions.AJ_ON_LOGIC_WIFI_AUTHENTICATION_ERROR.equals(action))
                {

                    if (intent.hasExtra(IntentExtraKeys.WIFI_WIFICONFIGURATION)){
                        WifiConfiguration config=	(WifiConfiguration)intent.getParcelableExtra(IntentExtraKeys.WIFI_WIFICONFIGURATION);
                        String ssid=config.SSID;
                        if (ssid!=null)
                        {
                            ssid= WifiUtil.getInstance().getNormalizedSSID(ssid);

                        }
                        manageWifiAuthenticationFailed(ssid);
                    } else {
                        manageWifiAuthenticationFailed("");
                    }

                } else if (IntentActions.AJ_ON_WIFI_DISABLED.equals(action)) {
                    verifyWifiIsEnabled();
                } else if (extra != null && IntentActions.AJ_ON_CONTROL_POPUP_AVAILABLE.equals(action)) {

                    final UUID deviceId = (UUID) extra.getSerializable(IntentExtraKeys.EXTRA_DEVICE_ID);
                    final Device device = DeviceManagerImpl.getInstance().getDevice(deviceId);

                    if (device != null) {
                        // a new popup has arrived from this device. With
                        // control buttons.
                        launchControlPopup(device);
                    }
                } else if (extra != null && IntentActions.AJ_ON_CONTROL_NOTIFICATION_ARRIVED.equals(action)) {
                    checkForNotificationWithAction(intent);
                } else if (IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE.equals(intent.getAction())){
                    if (intent.hasExtra(IntentExtraKeys.EXTRA_DEVICE_ID)) {
                        UUID intentDeviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
                        if (intentDeviceId.equals(factoryResetDeviceId)) {
                            if (m_factoryResetTimer != null) {
                                m_factoryResetTimer.cancel();
                                m_factoryResetTimer.purge();
                            }
                            if (factoryResetProgressDialog != null && factoryResetProgressDialog.isShowing()) {
                                factoryResetProgressDialog.dismiss();
                                factoryResetProgressDialog = null;
                            }
                            Device d = DeviceManagerImpl.getInstance().removeDevice(intentDeviceId);
                            if (d != null){
                                d.pinToNearby(false);
                            }
                            Log.i(TAG, "factoryResetReceiver received AJ_ON_SESSION_LOST_WITH_DEVICE, launch NearbyDevicesActivity");
                            final Intent launchIntent = new Intent(BaseActivity.this, NearbyDevicesActivity.class);
                            BaseActivity.this.startActivity(launchIntent);
                        }
                    }
                }

            }
        };

        m_baseFilter = new IntentFilter();
        m_baseFilter.addAction(IntentActions.AJ_ON_DEVICE_AUTHENTICATION_ERROR);
        m_baseFilter.addAction(IntentActions.AJ_ON_DEVICE_PASSWORD_SAVED);
        m_baseFilter.addAction(IntentActions.AJ_ON_LOGIC_WIFI_AUTHENTICATION_ERROR);
        // m_baseFilter.addAction(IntentActions.AJ_ON_CONTROL_POPUP_AVAILABLE);
        m_baseFilter.addAction(IntentActions.AJ_ON_CONTROL_NOTIFICATION_ARRIVED);
        m_baseFilter.addAction(IntentActions.AJ_ON_WIFI_DISABLED);
        m_baseFilter.addAction(IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE);
    }

    private void checkForNotificationWithAction(Intent intent) {

        Log.d(TAG, "checkForNotificationWithAction. start");
        if (!IntentActions.AJ_ON_CONTROL_NOTIFICATION_ARRIVED.equals(intent.getAction())) {
            return;
        }

        final UUID deviceId = (UUID) intent.getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        final String objPath = intent.getStringExtra(IntentExtraKeys.CONTROL_OBJ_PATH);
        Log.d(TAG, "Extras: deviceId=" + deviceId + ", objPath=" + objPath);

        if (deviceId != null && objPath != null && !objPath.isEmpty()) {

            if (AlljoynOnApplication.getInstance().istNotificationWithActionPopUpDisplayed()) {
                Log.d(TAG, String.format("checkForNotificationWithAction. Already showing a dialog for %s. Ignoring second notification.", objPath));
                return;
            }

            Log.d(TAG, String.format("checkForNotificationWithAction. Show a dialog for %s.", objPath));

            Intent notificationWithActionPopUpIntenet = new Intent(BaseActivity.this, NotificationPopUpActivity.class);
            if (intent.getExtras() != null) {
                notificationWithActionPopUpIntenet.putExtras(intent.getExtras());
                startActivity(notificationWithActionPopUpIntenet);
            }

        }
    }

    protected void manageWifiAuthenticationFailed(String SSID) {
        if (AlljoynOnApplication.getInstance().isSetupHomeWifiDisplayed()) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                launchBasicAlertDialog(getString(R.string.alert_title_wifi_authentication_failed), getString(R.string.alert_msg_wifi_authentication_failed), getString(android.R.string.ok),
                        new Runnable() {
                    @Override
                    public void run() {
                        dismissAlertDialog();
                    }
                }, null);
            }
        });
    }

    protected void onNewDevicePassword(String ssid) {
        // to be implemented by activities whose AJ call was interrupted by password request, and wish to resume
    }

    protected void onNewDevicePassword(UUID deviceId) {
        // to be implemented by activities whose AJ call was interrupted by password request, and wish to resume
    }

    protected void onDevicePasswordDialogDismissed(UUID deviceId) {
        // to be implemented by activities whose AJ call was interrupted by password request, and wish to resume
    }

    @Override
    protected void onResume() {
        super.onResume();
        m_isShowing = true;
        verifyWifiIsEnabled();
        // now that the activity is in the foreground, make sure we receive
        // intents
        if (m_baseReceiver != null && m_baseFilter != null) {
            registerReceiver(m_baseReceiver, m_baseFilter);
        }
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause()");

        super.onPause();
        if (m_goingBack) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
        if (m_goingToNewPage) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        m_goingToNewPage = false;
        m_goingBack = false;

        m_isShowing = false;

        dismissAlertDialog();

        if (m_baseReceiver != null) {
            unregisterReceiver(m_baseReceiver);
        }

        // make sure we receive intents only when the activity is in the
        // foreground,
        // to make sure only one activity responds to it

    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");
        synchronized (this) {
            isDestroying=true;
        }
        super.onDestroy();

        m_goingBack = false;
        m_goingToNewPage = false;
    }

    @Override
    public void onBackPressed() {
        markGoingBack();
        super.onBackPressed();
    }

    public void markGoingBack() {
        Log.v(TAG, "markGoingBack()");
        m_goingBack = true;
    }

    public void markGoingToNewPage() {
        Log.v(TAG, "markGoingToNewPage()");
        m_goingToNewPage = true;
    }

    protected void launchBasicAlertDialog(String title, String message, String posButton, final Runnable onPosClicked, String negButton) {
        launchBasicAlertDialog(title, 0, message, posButton, onPosClicked, null, null, negButton, null, null, null);
    }

    protected void launchBasicAlertDialog(String title, String message, String posButton, final Runnable onPosClicked, String negButton, final Runnable onNegClicked) {
        launchBasicAlertDialog(title, 0, message, posButton, onPosClicked, null, null, negButton, onNegClicked, null, null);
    }

    protected void launchBasicAlertDialog(String title, String message, String posButton, final Runnable onPosClicked, String negButton, final Runnable onNegClicked, final OnCancelListener onCancel) {
        launchBasicAlertDialog(title, 0, message, posButton, onPosClicked, null, null, negButton, onNegClicked, onCancel, null);
    }

    protected void launchBasicAlertDialog(String title, int iconId, String message, String posButton, final Runnable onPosClicked, String negButton, final Runnable onNegClicked) {
        launchBasicAlertDialog(title, iconId, message, posButton, onPosClicked, null, null, negButton, onNegClicked, null, null);
    }

    protected void launchBasicAlertDialog(String title, int iconId, String message, String posButton, final Runnable onPosClicked, String neutralButton, final Runnable onNeutralClicked,
            String negButton, final Runnable onNegClicked) {
        launchBasicAlertDialog(title, iconId, message, posButton, onPosClicked, neutralButton, onNeutralClicked, negButton, onNegClicked, null, null);
    }

    protected void launchBasicAlertDialog(String title, int iconId, String message, String posButton, final Runnable onPosClicked, String neutralButton, final Runnable onNeutralClicked,
            String negButton, final Runnable onNegClicked, final OnCancelListener onCancel, final Runnable onBackClicked) {
        // get dialog layout
        final View dialogLayout = getLayoutInflater().inflate(R.layout.alert_dialog, null);
        final TextView titleView = (TextView) dialogLayout.findViewById(R.id.alert_title_text);
        final TextView messageView = (TextView) dialogLayout.findViewById(R.id.alert_msg_text);
        final Button posView = (Button) dialogLayout.findViewById(R.id.alert_button_pos);
        final Button neutralView = (Button) dialogLayout.findViewById(R.id.alert_button_neutral);
        final Button negView = (Button) dialogLayout.findViewById(R.id.alert_button_neg);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogLayout);
        builder.setCancelable(true);

        if (title != null) {
            titleView.setText(title);
            if (iconId != 0) {
                titleView.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0, 0);
                titleView.setPadding(titleView.getPaddingLeft(), titleView.getPaddingTop(), titleView.getPaddingLeft() * 4,
                        titleView.getPaddingBottom());
            }
        } else {
            titleView.setVisibility(View.GONE);
        }

        if (message != null) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        if (posButton != null) {
            posView.setText(posButton);
            posView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onPosClicked != null) {
                        onPosClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        } else {
            posView.setVisibility(View.GONE);
        }

        if (neutralButton != null) {
            neutralView.setText(neutralButton);
            neutralView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onNeutralClicked != null) {
                        onNeutralClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        } else {
            neutralView.setVisibility(View.GONE);
        }

        if (negButton != null) {
            negView.setText(negButton);
            negView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onNegClicked != null) {
                        onNegClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        } else {
            negView.setVisibility(View.GONE);
        }

        if (onCancel != null) {
            builder.setOnCancelListener(onCancel);
        }

        if (onBackClicked != null) {
            builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent arg2) {

                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        onBackClicked.run();
                        return true;
                    }
                    // TODO Auto-generated method stub
                    return false;
                }
            });
        }

        // don't allow alert dialogs to stack on top of each other
        dismissAlertDialog();

        m_alertDialog = builder.create();
        if (m_isShowing) {
            m_alertDialog.show();
        } else {
            Log.d(TAG, "Alert dialog was created after activity was paused. Not showing it");
            m_alertDialog = null;
        }
    }

    protected void dismissAlertDialog() {
        Log.d(TAG, "dismissAlertDialog");
        if (m_alertDialog != null && m_alertDialog.isShowing() ) {
            synchronized (this) {
                if (isDestroying){
                    Log.d(TAG, "dismissAlertDialog abort isDestroying activated");
                    return;
                }
            }
            Log.d(TAG, "dismissAlertDialog dismissing");
            m_alertDialog.dismiss();
            m_alertDialog = null;
        }
    }

    private void launchControlPopup(Device device) {
        try {
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

            UIElement rootContainerElement = deviceControlPanel.getRootElement(new ControlPanelEventsListener() {

                @Override
                public void notificationActionDismiss(DeviceControlPanel arg0) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                AlertDialog controlDialog = m_controlDialogs.get(objPath);
                                if (controlDialog == null) {
                                    Log.w(TAG, "notificationActionDismiss. Couldn't find an AlertDialog for " + objPath);
                                    return;
                                }
                                m_controlDialogs.remove(objPath);
                                if (controlDialog.isShowing()) {
                                    controlDialog.dismiss();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception while dismissing a dialog", e);
                            }
                        }
                    });
                }

                @Override
                public void valueChanged(DeviceControlPanel arg0, UIElement arg1, Object arg2) {
                }

                @Override
                public void metadataChanged(DeviceControlPanel arg0, UIElement arg1) {
                }

                @Override
                public void errorOccurred(DeviceControlPanel arg0, String arg1) {
                }
            });

            if (rootContainerElement == null) {
                Log.e(TAG, "launchControlPopup RootContainerElement wasn't created!!! Can't continue");
                return;
            }

            ControlPanelAdapter controlPanelAdapter = new ControlPanelAdapter(this, this);

            UIElementType elementType = rootContainerElement.getElementType();
            Log.d(TAG, "launchControlPopup Found root container of type: '" + elementType + "'");

            if (elementType == UIElementType.ALERT_DIALOG) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Window window = getWindow();
                        if (window != null) {
                            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                        }
                    }
                });
                AlertDialogWidget alertDialogWidget = ((AlertDialogWidget) rootContainerElement);
                AlertDialog controlDialog = controlPanelAdapter.createAlertDialog(alertDialogWidget);
                controlDialog.setCancelable(false);
                controlDialog.setCanceledOnTouchOutside(false);
                controlDialog.setOnDismissListener(new AlertDialog.OnDismissListener() {

                    @Override
                    public void onDismiss(DialogInterface arg0) {
                        String text = "Dialog dismissed. Removing objPath: " + objPath;
                        m_controlDialogs.remove(objPath);
                        Log.d(TAG, text);
                    }
                });

                if (m_isShowing) {
                    m_controlDialogs.put(objPath, controlDialog);
                    controlDialog.show();
                } else {
                    Log.d(TAG, "Control dialog was created after activity was paused. Not showing it");
                }
            }
        } // try
        catch (ControlPanelException cpe) {
            handleControlPanelException(cpe);
        }
    }

    @Override
    public void handleControlPanelException(ControlPanelException e) {
        Log.e(TAG, "Error in calling remote object: '" + e.getMessage() + "'", e);
    }

    protected void launchAreYouSureCancelSetupDialog(String devicename, Runnable cancelLogic) {
        UIUtil.hideIme(this);
        launchBasicAlertDialog(getString(R.string.alert_title_confirm_cancel_device_setup),
                               getString(R.string.alert_message_confirm_cancel_device_setup, devicename),
                               getString(R.string.no),
                               null,
                               getString(R.string.yes),
                               cancelLogic
                               );
    }

    protected void verifyWifiIsEnabled() {
        if (!WifiUtil.getInstance().isWifiEnable()) {
            final String appName = getString(getApplicationInfo().labelRes);

            launchBasicAlertDialog(getString(R.string.alert_title_wi_fi_is_disabled), String.format(getString(R.string.alert_message_wi_fi_is_disabled), appName), getString(R.string.quit),
                    new Runnable() {
                @Override
                public void run() {
                    dismissAlertDialog();

                    // System.exit(0) isn't working well.
                    // instead just send Android to home. Mimic the home
                    // button.
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }, getString(R.string.enable), new Runnable() {
                @Override
                public void run() {
                    WifiUtil.getInstance().enableWifi(true);
                    dismissAlertDialog();
                }
            }, new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dismissAlertDialog();

                    // System.exit(0) isn't working well.
                    // instead just send Android to home. Mimic the home
                    // button.
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    protected void launchNoWifiAlertDialog(boolean isFeedPage) {
        launchBasicAlertDialog(getString(R.string.alert_title_no_wifi), 0, getString(isFeedPage ? R.string.alert_message_no_wifi_feed : R.string.alert_message_no_wifi),
                getString(R.string.wifi_settings), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
            }
        }, getString(R.string.enable), new Runnable() {
            @Override
            public void run() {
                WifiManager wifiManager = (WifiManager) BaseActivity.this.getSystemService(Context.WIFI_SERVICE);
                wifiManager.setWifiEnabled(true);
            }
        }, getString(R.string.cancel), null);
    }


    public void launchPasswordPrompt(final UUID deviceuUuid, final String ssid)
    {
        if (m_passwordDialog == null)
        {
            // get dialog layout
            final View confirmPasswordLayout = getLayoutInflater().inflate(R.layout.alert_confirm_password, null);
            final Button posView = (Button) confirmPasswordLayout.findViewById(R.id.alert_pass_confirm);
            final Button negView = (Button) confirmPasswordLayout.findViewById(R.id.alert_pass_cancel);
            final EditText passwordView = (EditText) confirmPasswordLayout.findViewById(R.id.alert_confirm_password_edit);

            // create and init dialog builder
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setView(confirmPasswordLayout);
            builder.setCancelable(true);

            negView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    UIUtil.hideIme(passwordView);

                    if (m_passwordDialog != null) {
                        m_passwordDialog.dismiss();
                    }

                    onDevicePasswordDialogDismissed(deviceuUuid);
                }
            });

           // posView.setText(R.string.confirm);
            posView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    UIUtil.hideIme(passwordView);

                    int length = passwordView.getText().toString().length();
                    char[] enteredPassword = new char[length];
                    passwordView.getText().getChars(0, length, enteredPassword, 0);

                    Device device = DeviceManagerImpl.getInstance().getDevice(deviceuUuid);
                    if (device == null){
                        device = DeviceManagerImpl.getInstance().getDeviceBySSID(ssid);
                    }
                    if (device != null)
                    {
                        device.setPassphrase(enteredPassword, false);
                    }

                    if (m_passwordDialog != null) {
                        m_passwordDialog.dismiss();
                    }
                }
            });
            // text insertion.
            TextWatcher textWatcher = new TextWatcher() {

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {

                    if (passwordView.getText() == null || passwordView.getText().length() == 0) {
                        posView.setEnabled(false);
                    } else {
                        posView.setEnabled(true);
                    }
                }
            };
            passwordView.addTextChangedListener(textWatcher);

            m_passwordDialog = builder.create();
        }

        if (!m_passwordDialog.isShowing()) {
            Log.i(TAG, "m_passwordDialog not showing: show it");
            m_passwordDialog.show();
        } else {
            Log.i(TAG, "m_passwordDialog already showing.");
        }
    }

    public void launchFactoryResetPrompt(final UUID deviceId) {
        launchBasicAlertDialog("Factory Reset", R.drawable.notify_alert_icon, "Warning: This will reset the device to the original configuration and remove all data.", getString(R.string.cancel),
                new Runnable() {
            @Override
            public void run() {
                dismissAlertDialog();
            }
        }, "Confirm", new Runnable() {
            @Override
            public void run() {
                requestFactoryReset(deviceId);
                dismissAlertDialog();
            }
        });
    }

    public void launchFactoryResetError(final UUID deviceId) {
        launchBasicAlertDialog("Device Unavailable", R.drawable.notify_alert_icon,
                "Error: This device cannot be reset because it is currently unavailable. Please check the connection and try again.", getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {
                dismissAlertDialog();
            }
        }, null, null);
    }

    protected void requestFactoryReset(UUID deviceId) {
        Log.d(TAG, "requestFactoryReset " + deviceId);
        if (m_factoryResetTimer != null) {
            m_factoryResetTimer.cancel();
            m_factoryResetTimer.purge();
        }
        if (factoryResetProgressDialog != null && factoryResetProgressDialog.isShowing()) {
            factoryResetProgressDialog.dismiss();
            factoryResetProgressDialog = null;
        }
        Device currentDevice = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (currentDevice == null) {
            return;
        }

        factoryResetDeviceId = deviceId;

        FactoryResetTask task = new FactoryResetTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (new UUID[] { deviceId }));
        } else {
            task.execute(new UUID[] { deviceId });
        }
    }

    protected void cancelFactoryReset() {
        Log.d(TAG, "cancelFactoryReset");

        factoryResetDeviceId = null;

        if (m_factoryResetTimer != null)
        {
            m_factoryResetTimer.cancel();
            m_factoryResetTimer.purge();
        }
        if (factoryResetProgressDialog != null && factoryResetProgressDialog.isShowing()) {
            factoryResetProgressDialog.dismiss();
            factoryResetProgressDialog = null;
        }
    }

    protected class FactoryResetTask extends AsyncTask<UUID, Void, DeviceResponse>
    {
        UUID deviceId = null;

        @Override
        protected void onPreExecute() {
            Log.i(TAG, "onPreExecute");
            factoryResetProgressDialog = new ProgressDialog(BaseActivity.this);
            factoryResetProgressDialog.setCancelable(true);
            factoryResetProgressDialog.setMessage(getString(R.string.manage_device_factory_reset_loading));
            factoryResetProgressDialog.show();

        };

        @Override
        protected void onPostExecute(DeviceResponse result) {
            Log.i(TAG, "onPostExecute");
            super.onPostExecute(result);

            Device device=DeviceManagerImpl.getInstance().getDevice(deviceId);
            String deviceName=null;
            if (device!=null && device.getFriendlyName()!=null && !device.getFriendlyName().isEmpty()){
                deviceName=device.getFriendlyName();
            }

            Log.d(TAG, "Factory reset of device '" + deviceName + "' returned result: " + result.getStatus().name());
            switch (result.getStatus()) {
            case Status_OK:
                if (m_factoryResetTimer != null)
                {
                    m_factoryResetTimer.cancel();
                    m_factoryResetTimer.purge();
                }
                m_factoryResetTimer = new Timer();
                m_factoryResetTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        if (!wrongAjPassword)
                        {
                            Log.i(TAG, "factory reset timeout - probably password Ok, remove device.");
                            Device d = DeviceManagerImpl.getInstance().removeDevice(deviceId);
                            if (d != null){
                                d.pinToNearby(false);
                            }
                            if (factoryResetProgressDialog != null && factoryResetProgressDialog.isShowing()) {
                                factoryResetProgressDialog.dismiss();
                                factoryResetProgressDialog = null;
                            }
                            Log.i(TAG, "factoryReset, launch NearbyDevicesActivity");
                            final Intent launchIntent = new Intent(BaseActivity.this, NearbyDevicesActivity.class);
                            BaseActivity.this.startActivity(launchIntent);
                        }
                    }
                }, 3*1000);
                break;
            case Status_ERROR:
            case Status_ERROR_CANT_ESTABLISH_SESSION:
            case Status_ERROR_NO_PEER_NAME:
                factoryResetProgressDialog.dismiss();
                launchBasicAlertDialog(getString(R.string.alert_title_factory_reset_failed),
                        String.format(getString(R.string.alert_message_factory_reset_failed), deviceName),
                        getString(R.string.dismiss), null, null);
                break;
            default:
                break;

            }
        }

        @Override
        protected DeviceResponse doInBackground(UUID... params) {
            Log.i(TAG, "doInBackground");
            deviceId = params[0];
            Device d =DeviceManagerImpl.getInstance().getDevice(deviceId);
            if (d != null)
            {
                d.removeTag(UIUtil.DEVICE_TAG_SHOW_RESET_LAYOUT);
                DeviceResponse response = d.factoryReset();
                return response;
            }
            return new DeviceResponse(ResponseCode.Status_ERROR);
        }

    }

    /**
     * When running the activity as a dialog, this trick prevents touches
     * outside the activity from closing it.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void blockOutsideClicks() {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            setFinishOnTouchOutside(false);
        } else {
            // fortunately, on older android devices activity will not be closed
            // on a touch outside, so do nothing
        }
    }
}
