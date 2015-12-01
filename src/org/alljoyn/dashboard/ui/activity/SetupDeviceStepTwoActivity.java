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

import jim.h.common.android.zxinglib.integrator.IntentIntegrator;
import jim.h.common.android.zxinglib.integrator.IntentResult;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.onboarding.OnboardingService.AuthType;
import org.alljoyn.onboarding.sdk.OnboardingIllegalStateException;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.OnboardingManager.OnboardingState;
import org.alljoyn.onboarding.sdk.WiFiNetworkConfiguration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SetupDeviceStepTwoActivity extends SetupDeviceBaseActivity {
    private final String TAG = "DASHBOARD_Setup Device Step #2";

    private EditText m_textEntry = null;
    private Button m_nextButton = null;
    // private RelativeLayout m_scanQRLayout;

    private CheckBox m_wifi_show_password = null;

    private final Handler m_handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.page_startup_device_step_two);

        m_deviceSSID = (String) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_SSID);

        if (m_deviceSSID == null) {
            Log.e(TAG, "m_deviceId and m_deviceSSID are NULL!");
        }
        Device device = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (device == null) {
            return;
        }
        // add UI elements
        TextView m_instruction1 = (TextView) findViewById(R.id.setup_device_1_text_instruction);

     // progress bar
        m_progressLayout = (LinearLayout) findViewById(R.id.setup_d2_progressbar_layout);
        m_progressText = (TextView) findViewById(R.id.setup_d2_progress_text);
        
        // for security
        m_textEntry = (EditText) findViewById(R.id.setup_device_1_pasword_entry);
        m_wifi_show_password = (CheckBox) findViewById(R.id.setup_device_1_show_password_checkbox);
        TextView m_password_instruction = (TextView) findViewById(R.id.setup_device_1_password_text_view);
        RelativeLayout m_scanQRLayout = (RelativeLayout) findViewById(R.id.setup_device_1_scan_layout);
        Button m_scanQRButton = (Button) findViewById(R.id.setup_device_1_scanqr_button);

        // buttons.
        Button m_cancelButton = (Button) findViewById(R.id.setup_device_1_cancel);
        m_nextButton = (Button) findViewById(R.id.setup_device_1_next);

        // device is open
        if (device.getAPWifiInfo().getAuthType().equals(AuthType.OPEN)) {
            m_instruction1.setText(String.format("Now we will configure your\"%s\" device.\n\nPlease press next to proceed.", UIUtil.getUserFriendlyAP(m_deviceSSID)));
            m_textEntry.setVisibility(View.GONE);
            m_scanQRLayout.setVisibility(View.GONE);
            m_wifi_show_password.setVisibility(View.GONE);
            m_password_instruction.setVisibility(View.GONE);
            m_nextButton.setEnabled(true);
        } else {
            m_instruction1.setText(String.format(
                    "Now we need to configure your \"%s\" device, which requires a password or QR code.\n\nPlease refer to your device instruction manual to find your password or QR code.",
                    UIUtil.getUserFriendlyAP(m_deviceSSID)));
            m_textEntry.setVisibility(View.VISIBLE);
            m_scanQRLayout.setVisibility(View.VISIBLE);
            m_wifi_show_password.setVisibility(View.VISIBLE);
            m_password_instruction.setVisibility(View.VISIBLE);
            m_nextButton.setEnabled(false);

            TextWatcher textWatcher = new TextWatcher() {

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (m_textEntry.getText().length() == 0) {
                        m_nextButton.setEnabled(false);
                    } else {
                        m_nextButton.setEnabled(true);
                    }
                }
            };
            m_textEntry.addTextChangedListener(textWatcher);
        }

        m_nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Device device = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
                if (device == null) {
                    manageWifiConnectionFailed(m_deviceSSID);
                    return;
                }
                UIUtil.hideIme(m_textEntry);
                // device.setSsidPassword(m_textEntry.getText().toString());
                onboardee = new WiFiNetworkConfiguration(m_deviceSSID, device.getAPWifiInfo().getAuthType(), m_textEntry.getText().toString());
                goToStepThree();
            }
        });

        m_cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                launchAreYouSureCancelSetupDialog(

                        UIUtil.getUserFriendlyAP(m_deviceSSID),

                        new Runnable() {
                            @Override
                            public void run() {
                                Device d = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
                                if (d != null && d.getStatus() == DeviceStatus.CONFIGURING){
                                    d.setStatus(DeviceStatus.UNCONFIGURED);
                                }
                                
                                try {
                                    OnboardingManager.getInstance().abortOnboarding();
                                } catch (OnboardingIllegalStateException e) {
                                    Log.e(TAG, "Failed to abort onboarding, process will continue", e);
                                    if (WifiUtil.getInstance()!=null){
                                    	WifiUtil.getInstance().enableAllWifiNetworks(); // just in case ....
                                    }
                                    SetupDeviceStepTwoActivity.this.finish();
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
                                                if (d != null){
                                                    d.setStatus(DeviceStatus.UNCONFIGURED);
                                                }
                                                SetupDeviceStepTwoActivity.this.finish();
                                            }
                                        }
                                    }
                                }, new IntentFilter(OnboardingManager.STATE_CHANGE_ACTION));
                                
                                
                                
                            }
                        });

            }
        });

        UIUtil.hashEditTextForPasswords(m_textEntry);

        m_wifi_show_password.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    UIUtil.removeHashFromEditText(m_textEntry);
                    m_textEntry.setSelection(m_textEntry.getText().length(), m_textEntry.getText().length());
                } else {

                    UIUtil.hashEditTextForPasswords(m_textEntry);
                    m_textEntry.setSelection(m_textEntry.getText().length(), m_textEntry.getText().length());
                }

            }

        });

        m_scanQRButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                IntentIntegrator.initiateScan(SetupDeviceStepTwoActivity.this, R.layout.page_scan_qr_code, R.id.viewfinder_view, R.id.preview_view, true);
            }
        });

    }
    
    private void goToStepThree() {
        // hide the keyboard before leaving the page
        UIUtil.hideIme(m_textEntry);
        final Intent launchIntent = new Intent(SetupDeviceStepTwoActivity.this, SetupDeviceStepThreeActivity.class);
        launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_SSID, m_deviceSSID);
        SetupDeviceStepTwoActivity.this.startActivity(launchIntent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case IntentIntegrator.REQUEST_CODE:
            if (resultCode == RESULT_CANCELED) {
                Log.e(TAG, "IntentIntegrator.RESULT_CANCELED.");
            } else if (resultCode == RESULT_OK) {
                final IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
                if (result == null) {
                    return;
                }

                final String scan = result.getContents();
                if (scan != null) {
                    m_handler.post(new Runnable() {
                        @Override
                        public void run() {
                            processQRScan(scan);
                        }
                    });
                } else {
                    Log.e(TAG, "IntentIntegrator.REQUEST_CODE scanResult is NULL.");

                    launchQRCodeErrorDialog();
                }
            }
            break;
        default:
            break;
        }
    }

    private void processQRScan(String scan) {

        final String VERSION_PARAM = "0";
        final String SSID_PARAM = "3";
        final String PASSPHRASE_PARAM = "7";
        final String AUTH_PARAM = "8";

        final String QR_CODE_VERSION = "ONBRDG";
        final String WEP_AUTH = "WEP";
        final String WPA_AUTH = "WPA";
        final String WPA2_AUTH = "WPA2";
        final String WPS_AUTH = "WPS";

        boolean parseFailed = false;
        final Uri uri = Uri.parse(scan);

        final String version = uri.getQueryParameter(VERSION_PARAM);
        if (version == null || !version.equals(QR_CODE_VERSION)) {
            // not a valid QR version
            parseFailed = true;
        }
        final String ssid = uri.getQueryParameter(SSID_PARAM);
        if (ssid == null) {
            parseFailed = true;
        }
        final String passphrase = uri.getQueryParameter(PASSPHRASE_PARAM);
        if (passphrase == null) {
            parseFailed = true;
        } else{
            // update the field:
            m_textEntry.setText(passphrase);
        }
        final String auth = uri.getQueryParameter(AUTH_PARAM);
        if (auth == null || !(auth.equals(WEP_AUTH) || auth.equals(WPA_AUTH) || auth.equals(WPA2_AUTH) || auth.equals(WPS_AUTH))) {
            // not a valid auth type
            parseFailed = true;
        }

        if (parseFailed) {
            Log.e(TAG, "ProcessQRScan FAILED!!! pass: " + passphrase + ", ssid: " + ssid + ", auth: " + auth + ", version: " + version);

            launchQRCodeErrorDialog();
            return;
        }
        Log.i(TAG, "ProcessQRScan PASSED! pass: " + passphrase + ", ssid: " + ssid + ", auth: " + auth + ", version: " + version);

        // set the ssid and passphrase we just scanned
        m_deviceSSID = ssid;

        final Device device = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
        if (device == null) {
            return;
        }
        // device.setSsidPassword(passphrase);
        onboardee = new WiFiNetworkConfiguration(m_deviceSSID, device.getAPWifiInfo().getAuthType(), m_textEntry.getText().toString());
        goToStepThree();
    }

    protected void launchQRCodeErrorDialog() {
        launchBasicAlertDialog(getString(R.string.alert_title_qr_code_error), getString(R.string.alert_message_qr_code_error), getString(R.string.okay), null, null);
    }

    protected void launchIncorrectDeviceNetworkPasswordDialog() {
        launchBasicAlertDialog(getString(R.string.alert_title_incorrect_device_network_password), getString(R.string.alert_message_incorrect_device_network_password), getString(R.string.okay), null,
                null);
    }
}
