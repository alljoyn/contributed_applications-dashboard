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
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Util;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.Device.ServiceType;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.onboarding.sdk.OnboardingConfiguration;
import org.alljoyn.onboarding.sdk.OnboardingIllegalArgumentException;
import org.alljoyn.onboarding.sdk.OnboardingIllegalStateException;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.OnboardingManager.OnboardingErrorType;
import org.alljoyn.onboarding.sdk.OnboardingManager.OnboardingState;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.sdk.WifiDisabledException;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SetupDeviceStepThreeActivity extends SetupDeviceBaseActivity {
    private final String TAG = "DASHBOARD_Setup Device Step #3";

    private TextView m_instruction1 = null;
    // private ImageView m_helpButton = null;
    private EditText m_textEntry = null;
    private Button m_cancelButton = null;
    private Button m_finishButton = null;

    private Timer m_errorTimer;

    // according to AJAPPS-3
    private final int wifiConnectionTimeout = 20 * 1000;
    private final int annoucementTimeout = 25 * 1000;

    private BroadcastReceiver m_receiver;
    private final IntentFilter m_intentFilter = new IntentFilter();;

    private State m_state;
    String newName = "";
    private ViewGroup m_deviceSetupThreeLayout;

    private boolean m_isConfigSupported = false;

    private final Handler m_handler = new Handler();
    enum State {

        ONBOARD_DEVICE, RENAME, IDLE
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        m_state = State.IDLE;
        // setContentView(R.layout.page_startup_device_step_three);

        m_deviceSSID = (String) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_SSID);
        Device device = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (device == null) {
            return;
        }
        m_deviceSetupThreeLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_startup_device_step_three, null);

        setContentView(m_deviceSetupThreeLayout);

        m_instruction1 = (TextView) m_deviceSetupThreeLayout.findViewById(R.id.setup_text_instruction);
        m_instruction1.setText(String.format(getString(R.string.setup_device3_instruction_base), UIUtil.getUserFriendlyAP(m_deviceSSID)));

        m_textEntry = (EditText) m_deviceSetupThreeLayout.findViewById(R.id.setup_d_rename_name_entry);
        m_textEntry.setVisibility(View.GONE);

        m_deviceSetupThreeLayout.findViewById(R.id.setup_d_rename_text_view).setVisibility(View.GONE);

        // progress bar
        m_progressLayout = (LinearLayout) m_deviceSetupThreeLayout.findViewById(R.id.setup_d3_rename_progressbar_layout);
        m_progressText = (TextView) m_deviceSetupThreeLayout.findViewById(R.id.setup_d3_rename_progress_text);

        // buttons.
        m_cancelButton = (Button) m_deviceSetupThreeLayout.findViewById(R.id.setup_d_rename_cancel);
        m_finishButton = (Button) m_deviceSetupThreeLayout.findViewById(R.id.setup_d_rename_finish);

        // device is open

        m_finishButton.setEnabled(false);

        m_finishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                
                if (m_textEntry != null && m_textEntry.getVisibility() != View.GONE) {
                    UIUtil.hideIme(m_textEntry);
                    newName = m_textEntry.getText().toString().trim();
                }

                if (!m_isConfigSupported || newName.length() == 0) {
                    backtoNearbyDevices();
                } else {
                    RenameTask task = new RenameTask();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    } else {
                        task.execute();
                    }
                }
            }
        });

        m_cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (m_cancelButton.getText().equals(getString(R.string.dismiss)) || m_state != State.ONBOARD_DEVICE) {
                    backtoNearbyDevices();
                } else {
                    if (m_state == State.ONBOARD_DEVICE) {
                        launchAreYouSureCancelSetupDialog(UIUtil.getUserFriendlyAP(m_deviceSSID), new Runnable() {

                            @Override
                            public void run() {
                                if (m_state == State.ONBOARD_DEVICE) {
                                    callAbort();
                                } else {
                                    backtoNearbyDevices();
                                }
                            }
                        });
                    }
                }

            }
        });

        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "receive intent: Action = " + intent.getAction() + (intent.getExtras() != null ? Util.bundleToString(intent.getExtras()) : ""));
                if (OnboardingManager.STATE_CHANGE_ACTION.equals(intent.getAction())) {
                    String stateString = intent.getStringExtra(OnboardingManager.EXTRA_ONBOARDING_STATE);
                    OnboardingState onboardingState = OnboardingState.getOnboardingStateByString(stateString);
                    if (onboardingState == null) {
                        Log.e(TAG, "received STATE_CHANGE_ACTION without onboardingState - return.");
                        return;
                    }
                    try {
                        switch (onboardingState) {

                        case CONNECTING_ONBOARDEE_WIFI:
                            updateProgress(context.getString(R.string.setup_device3_progress_connecting_to_device));
                            break;

                        case FINDING_ONBOARDEE:
                            updateProgress(context.getString(R.string.setup_device3_progress_looking_for_device_on_network));
                            break;

                        case JOINING_SESSION:
                            updateProgress(context.getString(R.string.setup_device3_progress_configuring_device));
                            break;

                        case CONNECTING_TARGET_WIFI:
                            updateProgress(String.format(context.getString(R.string.setup_device3_progress_connecting_to_target_wifi), m_onboardName));
                            break;

                        case VERIFYING_ONBOARDED:
                            updateProgress(String.format(context.getString(R.string.setup_device3_progress_verifying_onboarded), UIUtil.getUserFriendlyAP(m_deviceSSID), WifiUtil.getInstance().getNormalizedSSID(m_onboardName)));
                            break;
                            // onboarding done.
                        case VERIFIED_ONBOARDED:
                            Log.i(TAG, context.getString(R.string.setup_device3_progress_verified_onboarded));
                            handleVerifedOnboardee(intent);
                            break;

                        case ABORTING:
                            updateProgress(context.getString(R.string.aborting));
                            break;
                        case ABORTED:
                            backtoNearbyDevices();
                            break;
                        default:
                            // updateProgress(stateString);
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "onReceive error ", e);
                    }
                }

                else if (OnboardingManager.ERROR.equals(intent.getAction())) {
                    handleError(intent);
                }
            }
        };

        m_intentFilter.addAction(OnboardingManager.STATE_CHANGE_ACTION);
        m_intentFilter.addAction(OnboardingManager.ERROR);

        // this code will run after onResume.
        m_handler.post(new Runnable() {
            
            @Override
            public void run() {
                // will register the receiver on OnResume.
                runOnboarding();
            }
        });
    }

    private void callAbort() {
        try {
            OnboardingManager.getInstance().abortOnboarding();
        } catch (OnboardingIllegalStateException e) {
            Log.e(TAG, "Failed to abort onboarding, process will continue", e);
			final View dialogLayout = getLayoutInflater().inflate(R.layout.alert_dialog, null);
            final TextView titleView = (TextView) dialogLayout.findViewById(R.id.alert_title_text);
            final TextView messageView = (TextView) dialogLayout.findViewById(R.id.alert_msg_text);
            final Button posView = (Button) dialogLayout.findViewById(R.id.alert_button_pos);
            final Button neutralView = (Button) dialogLayout.findViewById(R.id.alert_button_neutral);
            final Button negView = (Button) dialogLayout.findViewById(R.id.alert_button_neg);
            
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogLayout);
            builder.setCancelable(true);
            titleView.setText(R.string.status_error);
            messageView.setText(R.string.alert_msg_cant_abort);
            posView.setText(R.string.dismiss);
            neutralView.setVisibility(View.GONE);
            negView.setVisibility(View.GONE);
            
            final AlertDialog dialog = builder.create();
            dialog.show();
            
            // dismiss after 3 sec
            new Timer().schedule(new TimerTask() {
                public void run() {
                    dialog.dismiss(); 
                    this.cancel(); 
                }
            }, 3*1000); 
            
            return;
        }

        updateProgress(getString(R.string.aborting));
        registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(OnboardingManager.EXTRA_ONBOARDING_STATE)) {
                    String value = intent.getStringExtra(OnboardingManager.EXTRA_ONBOARDING_STATE);
                    if (OnboardingState.ABORTED.toString().equals(value)) {
                        unregisterReceiver(this);
                        Device d = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
                        if (d != null) {
                            d.setStatus(DeviceStatus.UNCONFIGURED);
                        }
                        hideProgress();
                        backtoNearbyDevices();
                    }
                }
            }
        }, new IntentFilter(OnboardingManager.STATE_CHANGE_ACTION));

    }

    private void handleVerifedOnboardee(Intent intent) {
        m_state = State.RENAME;
        m_cancelButton.setText(R.string.dismiss);
        final UUID onboardeeAppId = (UUID) intent.getSerializableExtra(OnboardingManager.EXTRA_DEVICE_APPID);
        dismissAlertDialog();

        Device device = DeviceManagerImpl.getInstance().getDevice(onboardeeAppId);
        if (device != null) {
            device.setAPWifiInfo(new WiFiNetwork(intent.getStringExtra(OnboardingManager.EXTRA_DEVICE_ONBOARDEE_SSID)));
            device.setOnboardedNetworkSSID(intent.getStringExtra(OnboardingManager.EXTRA_DEVICE_TARGET_SSID));
            device.pinToNearby(true);// and save to preference.
            device.setStatus(DeviceStatus.AVAILABLE);
            m_isConfigSupported = device.isServiceSupported(ServiceType.CONFIG);
        } else {
            Log.e(TAG, "Can't find device by appId: " + onboardeeAppId);
        }

        if (m_isConfigSupported && device != null) {

            Log.i(TAG, "getAbout for max name length. new state = RENAME");
            AsyncTask<Void, Void, Short> getAboutTask = new AsyncTask<Void, Void, Short>() {

                @Override
                protected Short doInBackground(Void... params) {
                    // get Limit of MaxLength
                    Short limit = -1;
                    Device device = DeviceManagerImpl.getInstance().getDevice(onboardeeAppId);
                    Map<String, Object> map = device.getAbout(false);
                    if (map == null) {
                        map = device.getAbout(true);
                    }
                    if (map != null) {
                        if (map.containsKey("MaxLength")) {
                            limit = (Short) map.get("MaxLength");
                        }
                    }
                    // TODO Auto-generated method stub
                    return limit;
                }

                @Override
                protected void onPostExecute(Short limit) {
                    // TODO Auto-generated method stub
                    // onpost
                    if (limit != -1) {
                        InputFilter[] filters = new InputFilter[1];
                        filters[0] = new InputFilter.LengthFilter(limit);
                        ((EditText) findViewById(R.id.setup_d_rename_name_entry)).setFilters(filters);
                    }
                    findViewById(R.id.setup_d_rename_name_entry).setVisibility(View.VISIBLE);
                    findViewById(R.id.setup_d_rename_text_view).setVisibility(View.VISIBLE);
                    m_instruction1.setText(String.format(getString(R.string.setup_device3_instruction_rename), UIUtil.getUserFriendlyAP(m_deviceSSID)));
                    hideProgress();
                    m_finishButton.setEnabled(true);
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                getAboutTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                getAboutTask.execute();
            }

        } else {
            m_instruction1.setText(String.format(getString(R.string.setup_device3_instruction_onboarding_completed), UIUtil.getUserFriendlyAP(m_deviceSSID)));
            hideProgress();
            m_finishButton.setEnabled(true);
        }
    }

    private void handleError(Intent intent) {
        String errorType = intent.getStringExtra(OnboardingManager.EXTRA_ERROR_DETAILS);
        if (errorType == null) {
            return;
        }

        switch (OnboardingErrorType.getOnboardingErrorTypeByString(errorType)) {
        case ONBOARDEE_WIFI_AUTH:
            manageWifiAuthFailed(true);
            break;
        case ONBOARDEE_WIFI_TIMEOUT:
            Log.e(TAG, "ONBOARDEE_WIFI_TIMEOUT");
            handleOnboardeeWifiTimeout();
            break;
        case TARGET_WIFI_AUTH:
            manageWifiAuthFailed(false);
            break;
        case TARGET_WIFI_TIMEOUT:
            Log.e(TAG, "TARGET_WIFI_TIMEOUT");
            manageWifiConnectionFailed(target.getSSID());

            break;
        case VERIFICATION_TIMEOUT:
        case FIND_ONBOARDEE_TIMEOUT:
            handleVerifyOnboardeeError();
            break;
        case JOIN_SESSION_ERROR:
        case JOIN_SESSION_TIMEOUT:
            handleJoinSessionError();
            break;
        case CONFIGURING_ONBOARDEE_WAITING_FOR_SIGNAL_TIMEOUT:
            // for configure return 2.
            break;
        case ERROR_CONFIGURING_ONBOARDEE:
            // can be authentication problem
            Log.e(TAG, "ERROR_CONFIGURING_ONBOARDEE ");
            handleErrorConfiguringOnboardee();
            break;
        case ORIGINAL_WIFI_AUTH: // when abort
            manageWifiConnectionFailed(m_currentAP);
            break;
        case ORIGINAL_WIFI_TIMEOUT:// when abort
            manageWifiConnectionFailed(m_currentAP);
            break;
        default:
            updateProgress("ERROR !!! " + errorType);
        }
    }

    private void handleVerifyOnboardeeError() {
        launchBasicAlertDialog(getString(R.string.alert_title_verification), 0, getString(R.string.alert_message_verification, UIUtil.getUserFriendlyAP(m_deviceSSID)), getString(R.string.dismiss),
                new Runnable() {
            @Override
            public void run() {
                callAbort();
            }
        }, getString(R.string.wait), new Runnable() {
            @Override
            public void run() {
                runOnboarding();
            }
        }, null, null);
    }

    private void handleJoinSessionError() {
        // error when configuring
        launchBasicAlertDialog(getString(R.string.alert_title_configuration_error), 0, String.format(getString(R.string.alert_title_configuration_error), UIUtil.getUserFriendlyAP(m_deviceSSID)),
                getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {
                callAbort();
            }
        }, null, null);
    }

    private void handleErrorConfiguringOnboardee() {
        m_errorTimer = new Timer();
        m_errorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!wrongAjPassword) {
                            launchBasicAlertDialog(getString(R.string.status_error), 0, String.format(getString(R.string.setup_device3_ErrorConfiguringOnboardee), UIUtil.getUserFriendlyAP(m_deviceSSID)), getString(R.string.dismiss), new Runnable() {
                                @Override
                                public void run() {
                                    callAbort();
                                }
                            }, null, null, null, null);
                        }
                    }
                });
            }
        }, 2 * 1000);
    }

    private void runOnboarding() {
        m_state = State.ONBOARD_DEVICE;
        try {
            OnboardingManager.getInstance().runOnboarding(new OnboardingConfiguration(onboardee, wifiConnectionTimeout, annoucementTimeout, target, wifiConnectionTimeout, annoucementTimeout));
            Device current = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
            if (current != null && current.getStatus() == DeviceStatus.UNCONFIGURED) {
                current.setStatus(DeviceStatus.CONFIGURING);
            }
        } catch (OnboardingIllegalStateException e) {
            e.printStackTrace();
            UIUtil.showToast(this, "OnboardingIllegalStateException");
        } catch (OnboardingIllegalArgumentException e) {
            e.printStackTrace();
            UIUtil.showToast(this, "OnboardingIllegalArgumentException");
        } catch (WifiDisabledException e) {
            e.printStackTrace();
            UIUtil.showToast(this, "WifiDisabledException");
        }
    }

    private void handleOnboardeeWifiTimeout() {
        launchBasicAlertDialog(getString(R.string.alert_title_connection_failed), 0, getString(R.string.alert_message_connection_to_device_failed, UIUtil.getUserFriendlyAP(m_deviceSSID)),
                getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {

                UIUtil.hideIme(m_textEntry);
                callAbort();
            }
        }, null, null, null, null);
    }

    protected void manageWifiAuthFailed(final boolean isOnboardee) {
        String ssid;
        if (isOnboardee) {
            ssid = m_deviceSSID;
        } else {
            ssid = target.getSSID();
        }
        Log.e(TAG, "manageWifiAuthenticationFailed");
        launchBasicAlertDialog(getString(R.string.alert_title_wifi_authentication_failed), 0, String.format("Please check the \"%s\" network password and try again.", UIUtil.getUserFriendlyAP(ssid)),
                getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {
                UIUtil.hideIme(m_textEntry);
                Intent launchIntent;
                if (isOnboardee) {
                    launchIntent = new Intent(SetupDeviceStepThreeActivity.this, SetupDeviceStepTwoActivity.class);
                } else {
                    launchIntent = new Intent(SetupDeviceStepThreeActivity.this, SetupDeviceStepOneActivity.class);
                }
                launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_SSID, m_deviceSSID);
                SetupDeviceStepThreeActivity.this.startActivity(launchIntent);
                finish();
            }
        }, null, null, null, null);
    }

    @Override
    protected void manageWifiConnectionFailed(final String ssid) {

        Log.e(TAG, "manageWifiAuthenticationFailed");
        launchBasicAlertDialog(getString(R.string.alert_title_connection_failed), 0, getString(R.string.alert_message_wifi_connection_failed, UIUtil.getUserFriendlyAP(ssid)),
                getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {

                UIUtil.hideIme(m_textEntry);
                callAbort();
            }
        }, null, null, null, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        // unregister receivers
        if (m_receiver != null) {
            try {
                unregisterReceiver(m_receiver);
            } catch (Exception e) {
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "onPause  " + writer.toString());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (m_receiver != null && m_intentFilter != null) {
            registerReceiver(m_receiver, m_intentFilter);
        }
    }

    private void showSetNameChangeFailedPopup() {
        launchBasicAlertDialog(getString(R.string.alert_title_name_change_failed), 0, String.format(getString(R.string.alert_message_name_change_failed), UIUtil.getUserFriendlyAP(m_deviceSSID)),
                getString(R.string.dismiss), new Runnable() {

            @Override
            public void run() {
                backtoNearbyDevices();
            }
        }, null, null, null, null);
    }

    /**
     * This method is called from BaseActivity after the password dialog has
     * saved a new password to try with the device.
     */
    @Override
    protected void onNewDevicePassword(UUID deviceId) {
        Log.i(TAG, "New password saved, retrying onboarding");

        Device currentOnboarded = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (currentOnboarded != null && deviceId.equals(currentOnboarded.getId())) {
            handleOnNewDevicePassword();
        }
    }

    /**
     * This method is called from BaseActivity after the password dialog has
     * saved a new password to try with the device.
     */
    @Override
    protected void onNewDevicePassword(String ssid) {
        Log.i(TAG, "New password saved, retrying onboarding");

        Device currentOnboarded = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (currentOnboarded != null && ssid.equals(currentOnboarded.getAPWifiInfo().getSSID())) {
            handleOnNewDevicePassword();
        }
    }

    private void handleOnNewDevicePassword() {
        if (m_errorTimer != null) {
            m_errorTimer.cancel();
            m_errorTimer.purge();
        }

        if (m_state == State.ONBOARD_DEVICE) {

            runOnboarding();
        } else if (m_state == State.RENAME) {
            RenameTask task = new RenameTask();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                task.execute();
            }
        }
    }

    protected void backtoNearbyDevices() {
        Log.i(TAG, "goToNextStep");

        // hide the keyboard before leaving the page
        UIUtil.hideIme(m_textEntry);
        final Intent launchIntent = new Intent(SetupDeviceStepThreeActivity.this, NearbyDevicesActivity.class);
        launchIntent.setAction(IntentActions.AJ_ON_SCROLL_TO_TOP_AP_LIST);
        SetupDeviceStepThreeActivity.this.startActivity(launchIntent);
        Device d = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (d != null && d.getStatus() == DeviceStatus.CONFIGURING) {
            d.setStatus(DeviceStatus.UNCONFIGURED);
        }
        finish();
    }

    @Override
    public void onBackPressed() {

        launchAreYouSureCancelSetupDialog(UIUtil.getUserFriendlyAP(m_deviceSSID), new Runnable() {

            @Override
            public void run() {
                callAbort();
            }
        });

    }

    protected class RenameTask extends AsyncTask<Void, Void, DeviceResponse> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            m_finishButton.setEnabled(false);
            updateProgress(String.format("Configuring \"%s\" device...", UIUtil.getUserFriendlyAP(m_deviceSSID)));
        };

        @Override
        protected void onPostExecute(DeviceResponse result) {
            Log.i(TAG, "RenameTask onPostExecute");
            super.onPostExecute(result);
            m_finishButton.setEnabled(true);
            if (ResponseCode.Status_OK != result.getStatus()) {
                Log.e(TAG, "device.rename() returned error: " + result.getMsg());
                Timer m_errorTimer = new Timer();
                m_errorTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!wrongAjPassword) {
                                    showSetNameChangeFailedPopup();
                                }
                            }
                        });
                    }
                }, 2 * 1000);
            } else {
                backtoNearbyDevices();
            }
        }

        @Override
        protected DeviceResponse doInBackground(Void... params) {
            Device device = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
            if (device == null) {
                return new DeviceResponse(ResponseCode.Status_ERROR);
            }
            Map<String, Object> config = new HashMap<String, Object>();
            config.put(AboutKeys.ABOUT_DEVICE_NAME, newName);
            DeviceResponse status = device.setConfig(config);
            return status;
        }

    }
}
