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
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Util;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.adapter.SimpleTextSpinnerAdapter;
import org.alljoyn.dashboard.ui.adapter.WifiNetworkSpinnerAdapter;
import org.alljoyn.dashboard.ui.util.UISharedPreferencesManager;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.dashboard.ui.wrappers.AuthTypeWrapper;
import org.alljoyn.onboarding.OnboardingService.AuthType;
import org.alljoyn.onboarding.sdk.OnboardingIllegalArgumentException;
import org.alljoyn.onboarding.sdk.OnboardingIllegalStateException;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.OnboardingManager.OnboardingErrorType;
import org.alljoyn.onboarding.sdk.OnboardingManager.OnboardingState;
import org.alljoyn.onboarding.sdk.OnboardingManager.WifiFilter;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.sdk.WiFiNetworkConfiguration;
import org.alljoyn.onboarding.sdk.WifiDisabledException;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class SetupDeviceStepOneActivity extends SetupDeviceBaseActivity {

    private CheckBox m_useHomeCheckBox;

    private Spinner m_SelectWifi;
    private Spinner m_SelectAuthType;
    private EditText m_WiFiSSID;
    private EditText m_WiFiPassword;
    private CheckBox m_WiFiShowPassword;
    private TextView m_WiFiSSIDLabel;
    private TextView m_header;

    private Button m_NextButton;
    private Button m_cancelButton;

    private View m_PasswordView;

    private WifiNetworkSpinnerAdapter mNearbyAPs;
    private ArrayAdapter<AuthTypeWrapper> mAuthTypes;
    private WiFiNetwork selectedAP;
    private AuthTypeWrapper selectedAuth;
    private RelativeLayout m_chooseNetworkLayout;

    private ViewGroup m_toggleLayout = null;
    private TextView m_toggleOnText = null;
    private TextView m_toggleOffText = null;
    private RelativeLayout m_wifiLayout;
    private CheckBox m_saveAsHome;
    private CheckBox mHiddenSSID;


    private String m_onboardPass;
    private AuthType m_selectedAuthType;

    BroadcastReceiver m_receiver;

    //flag to indicate if a user has clicked on one of the buttons or changed some text.
    private boolean hasUserClicked=false;
    // flag to prevent misfire of WifiSpinner on first time.
    private boolean isSelectWifiSpinnerFlag=false;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {

        m_deviceSSID = (String) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_SSID);
        m_onboardName = null;

        super.onCreate(savedInstanceState);

        WiFiNetwork currentNet = null;

        try {
            currentNet = OnboardingManager.getInstance().getCurrentNetwork();
        } catch (WifiDisabledException e1) {
            launchBasicAlertDialog("ERROR", "Can't get current network", getString(R.string.dismiss), null, null, null);
            e1.printStackTrace();
        }
        if (currentNet != null){
            m_currentAP = currentNet.getSSID();
        }
        else{
            launchBasicAlertDialog("ERROR", "Can't get current network", getString(R.string.dismiss), null, null, null);
        }
        setContentView(R.layout.page_startup_device_step_one);

        m_header = (TextView) findViewById(R.id.setup_d2_intro_text);
        m_header.setText(String.format("You are about to setup the device \"%s\". Please select the network you want to use.", UIUtil.getUserFriendlyAP(m_deviceSSID)));
        m_useHomeCheckBox = (CheckBox) findViewById(R.id.setup_d2_home_network);

        m_wifiLayout = (RelativeLayout) findViewById(R.id.setup_d2_enter_wifi_layout);
        m_chooseNetworkLayout = (RelativeLayout) findViewById(R.id.setup_d2_choose_network_header);

        m_SelectWifi = (Spinner) findViewById(R.id.setup_d2_select_wifi_network);
        m_SelectAuthType = (Spinner) findViewById(R.id.setup_d2_connect_wifi_authType_entry);
        // mAdvancedToggle = (ToggleButton)
        // findViewById(R.id.advanced_options_toggle);
        m_WiFiSSID = (EditText) findViewById(R.id.setup_d2_wifiSSID);
        m_PasswordView = findViewById(R.id.setup_d2_password_layout);

        m_WiFiPassword = (EditText) findViewById(R.id.setup_d2_wifiPassword);
        m_WiFiShowPassword = (CheckBox) findViewById(R.id.setup_d2_wifi_show_password);
        m_WiFiSSIDLabel = (TextView) findViewById(R.id.setup_d2_select_wifi);

        m_saveAsHome = (CheckBox) findViewById(R.id.setup_d2_save_as_home);

        m_NextButton = (Button) findViewById(R.id.setup_d2_next);
        m_cancelButton = (Button) findViewById(R.id.setup_d2_calcel);

        m_progressLayout = (LinearLayout) findViewById(R.id.setup_d2_progressbar_layout);
        m_progressText =  (TextView) findViewById(R.id.setup_d2_progress_text);

        mHiddenSSID=(CheckBox) findViewById(R.id.hiddenssid_checkbox);

        m_SelectAuthType.setVisibility(View.GONE);
        m_WiFiSSID.setVisibility(View.GONE);
        m_toggleLayout = (ViewGroup) findViewById(R.id.setup_d2_toggle_layout);
        m_toggleOnText = (TextView) findViewById(R.id.setup_d2_toggle_on);
        m_toggleOffText = (TextView) findViewById(R.id.setup_d2_toggle_off);

        // no home network
        String homeNetwork = UISharedPreferencesManager.getStoredNetworkName();
        if (homeNetwork == null || homeNetwork.length() == 0) {
            m_useHomeCheckBox.setVisibility(View.GONE);
            m_useHomeCheckBox.setChecked(false);
            m_saveAsHome.setEnabled(false);
        } else {
            m_useHomeCheckBox.setText(String.format("Use the home network %s", homeNetwork));
            m_useHomeCheckBox.setChecked(true);
            m_wifiLayout.setVisibility(View.GONE);
        }

        if (m_useHomeCheckBox.isChecked()) {
            m_chooseNetworkLayout.setVisibility(View.GONE);
            m_wifiLayout.setVisibility(View.GONE);
            m_NextButton.setEnabled(true);

            // put window in center of the screen, vertical
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            final Window window = getWindow();
            if (window != null) {
                lp.copyFrom(window.getAttributes());
                lp.gravity = Gravity.CENTER_VERTICAL + Gravity.CENTER_HORIZONTAL;
                window.setAttributes(lp);
            }
        } else {

            // attach window to top so that height resize doesn't move the
            // window
            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            final Window window = getWindow();
            if (window != null) {
                lp.copyFrom(window.getAttributes());
                lp.gravity = Gravity.TOP + Gravity.CENTER_HORIZONTAL;
                window.setAttributes(lp);
            }
        }

        m_useHomeCheckBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                handleUserClick(false);
                if (buttonView.isChecked()) {
                    m_chooseNetworkLayout.setVisibility(View.GONE);
                    m_wifiLayout.setVisibility(View.GONE);
                    m_NextButton.setEnabled(true);

                    // put window in center of the screen, vertical
                    final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    final Window window = getWindow();
                    if (window != null) {
                        lp.copyFrom(window.getAttributes());
                        lp.gravity = Gravity.CENTER_VERTICAL + Gravity.CENTER_HORIZONTAL;
                        window.setAttributes(lp);
                    }
                } else {
                    m_chooseNetworkLayout.setVisibility(View.VISIBLE);
                    m_wifiLayout.setVisibility(View.VISIBLE);
                    m_NextButton.setEnabled(false);

                    // attach window to top so that height resize doesn't move
                    // the window
                    final WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    final Window window = getWindow();
                    if (window != null) {
                        lp.copyFrom(window.getAttributes());
                        lp.gravity = Gravity.TOP + Gravity.CENTER_HORIZONTAL;
                        window.setAttributes(lp);
                    }
                }
            }
        });

        final OnClickListener toggleClickListener = new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                handleUserClick(false);
                m_WiFiPassword.setText("");
                m_WiFiSSID.setText("");
                if (m_WiFiSSID.getVisibility() == View.GONE) {
                    m_toggleOnText.setVisibility(View.VISIBLE);
                    m_toggleOffText.setVisibility(View.GONE);

                    m_WiFiSSID.setVisibility(View.VISIBLE);
                    m_WiFiSSIDLabel.setVisibility(View.GONE);

                    m_PasswordView.setVisibility(View.GONE);
                    m_SelectAuthType.setSelection(0);
                    m_SelectWifi.setVisibility(View.GONE);
                    m_SelectAuthType.setVisibility(View.VISIBLE);
                    mHiddenSSID.setVisibility(View.VISIBLE);


                    // processSelectedAP();

                } else {
                    m_toggleOnText.setVisibility(View.GONE);
                    m_toggleOffText.setVisibility(View.VISIBLE);

                    m_WiFiSSID.setVisibility(View.GONE);
                    m_WiFiSSIDLabel.setVisibility(View.VISIBLE);

                    m_SelectWifi.setVisibility(View.VISIBLE);
                    m_SelectAuthType.setVisibility(View.GONE);
                    mHiddenSSID.setVisibility(View.GONE);
                    mHiddenSSID.setChecked(false);
                    processSelectedAP();

                }
            }
        };

        m_toggleLayout.setOnClickListener(toggleClickListener);

        // Handle showing the password
        m_WiFiShowPassword.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView.isChecked()) {
                    UIUtil.removeHashFromEditText(m_WiFiPassword);
                    m_WiFiPassword.setSelection(m_WiFiPassword.getText().length(), m_WiFiPassword.getText().length());
                } else {
                    UIUtil.hashEditTextForPasswords(m_WiFiPassword);
                    m_WiFiPassword.setSelection(m_WiFiPassword.getText().length(), m_WiFiPassword.getText().length());
                }
            }
        });

        // Set the adapter to list the nearby access points
        mNearbyAPs = new WifiNetworkSpinnerAdapter(this, R.layout.list_item_spinner);
        m_SelectWifi.setAdapter(mNearbyAPs);

        // Handle the selection
        m_SelectWifi.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if(isSelectWifiSpinnerFlag){
                    handleUserClick(false);
                }else{
                    isSelectWifiSpinnerFlag=true;
                }
                selectedAP = mNearbyAPs.getItem(position);
                Log.d(TAG, String.format("Item selected, name:'%s', capabilities:'%s'", selectedAP.getSSID(), selectedAP.getAuthType()));
                m_WiFiPassword.setText("");
                processSelectedAP();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        // Setup the Auth types we want to support
        mAuthTypes = new SimpleTextSpinnerAdapter(this, R.layout.list_item_spinner);
        AuthType[] types = new AuthType[] { AuthType.OPEN, AuthType.WEP,  AuthType.WPA2_AUTO, AuthType.WPA_AUTO };
        for (AuthType type : types) {
            mAuthTypes.add(new AuthTypeWrapper(type));
        }

        m_SelectAuthType.setAdapter(mAuthTypes);

        // Handle the selection of an AuthType
        m_SelectAuthType.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                handleUserClick(false);
                selectedAuth = mAuthTypes.getItem(position);
                final AuthType selectedAuthType = selectedAuth.value;
                Log.d(TAG, String.format("Item selected, label:'%s', type:'%s'", selectedAuth.toString(), selectedAuthType));
                if (!AuthType.OPEN.equals(selectedAuthType)) {
                    m_PasswordView.setVisibility(View.VISIBLE);
                    if (m_WiFiPassword.getText().toString().length() == 0) {
                        m_NextButton.setEnabled(false);
                    } else {
                        m_NextButton.setEnabled(true);
                    }

                } else {
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(m_PasswordView.getWindowToken(), 0);
                    m_PasswordView.setVisibility(View.GONE);
                    if (m_WiFiSSID.getText().toString().trim().length() > 0) {
                        m_NextButton.setEnabled(true);
                    } else {
                        m_NextButton.setEnabled(false);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        m_NextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                handleUserClick(false);
                m_currentAP = WifiUtil.getInstance().getCurrentSSID();
                // if we got to press the login button, it means we have the new
                // UUID (m_deviceId) of the device.
                final Device curDevice = DeviceManagerImpl.getInstance().getDeviceBySSID(m_deviceSSID);
                if (curDevice == null) {
                    UIUtil.hideIme(SetupDeviceStepOneActivity.this);
                    launchBasicAlertDialog(getString(R.string.alert_title_device_not_found), String.format(getString(R.string.alert_msg_device_no_longer_available), m_deviceSSID),
                            getString(R.string.dismiss), new Runnable() {

                                @Override
                                public void run() {
                                    finish();
                                }
                            }, null, null);
                    return;
                }
                // onboard to home network
                if (m_useHomeCheckBox.isChecked()) {
                    m_onboardName = UISharedPreferencesManager.getStoredNetworkName();
                    m_onboardPass = UISharedPreferencesManager.getStoredNetworkPassword();
                    m_selectedAuthType = UISharedPreferencesManager.getStoredAuthType();
                    m_isHidden = UISharedPreferencesManager.isStoredNetworkHidden();

                    target = new WiFiNetworkConfiguration(m_onboardName, m_selectedAuthType, m_onboardPass, m_isHidden);
                    goToStepTwo();
                }
                // set onboard params and connect to it, go to next step only
                // when connected.
                else {
                    // not advanced, take info from spinner
                    if (m_toggleOffText.getVisibility() == View.VISIBLE) {
                        m_onboardName = selectedAP.getSSID();
                        m_onboardPass = m_WiFiPassword.getText().toString();
                        m_selectedAuthType = selectedAP.getAuthType();
                        m_isHidden = false; // if it was in the spinner, then it was in the scan and it's not hidden.
                    } else // advanced, take from text views
                    {
                        m_onboardName = m_WiFiSSID.getText().toString();
                        m_onboardPass = m_WiFiPassword.getText().toString();
                        m_selectedAuthType = selectedAuth.value;
                        m_isHidden = mHiddenSSID.isChecked();
                    }
                    target = new WiFiNetworkConfiguration(m_onboardName, m_selectedAuthType, m_onboardPass,m_isHidden);
                    try {
                        WifiUtil.getInstance().connectToNetwork(target);
                    } catch (WifiDisabledException e) {
                        verifyWifiIsEnabled();
                        e.printStackTrace();
                    } catch (OnboardingIllegalArgumentException e) {
                        launchBasicAlertDialog("Illegal parameters", "Please verify that the network credentials legal.", getString(R.string.dismiss), null, null);
                        e.printStackTrace();
                    }
                    m_progressLayout.setVisibility(View.VISIBLE);
                    prepareBroadcasrReciever();
                    blockUIBeforAsynchTask();
                }
            }
        });

        // Dismiss the activity and save that the user selected skip
        m_cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                launchAreYouSureCancelSetupDialog(UIUtil.getUserFriendlyAP(m_deviceSSID), new Runnable() {

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
                                dismissAlertDialog();
                                SetupDeviceStepOneActivity.this.finish();
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
                                            SetupDeviceStepOneActivity.this.finish();
                                        }
                                    }
                                }
                            }, new IntentFilter(OnboardingManager.STATE_CHANGE_ACTION));
                    }
                });
            }
        });

        // Monitor as text is entered to enable the verify button
        m_WiFiSSID.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                handleUserClick(false);
                if ((m_WiFiSSID.getText().toString().trim().length() > 0 && selectedAuth.value == AuthType.OPEN)
                        || (m_WiFiPassword.getText().toString().trim().length() > 0 && m_WiFiSSID.getText().toString().trim().length() > 0 && selectedAuth.value != AuthType.OPEN)) {
                    m_NextButton.setEnabled(true);
                } else {
                    m_NextButton.setEnabled(false);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                // TODO Auto-generated method stub

            }
        });
        // Monitor as text is entered to enable the verify button
        m_WiFiPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handleUserClick(false);
                if ((m_WiFiPassword.getText().toString().trim().length() > 0 && m_WiFiSSID.getVisibility() == View.GONE)
                        || (m_WiFiPassword.getText().toString().trim().length() > 0 && m_WiFiSSID.getText().toString().trim().length() > 0)) {
                    m_NextButton.setEnabled(true);
                } else {
                    m_NextButton.setEnabled(false);
                }
            }
        });

        prepareBroadcasrReciever();
        try {
            OnboardingManager.getInstance().scanWiFi();
        } catch (WifiDisabledException e) {
            verifyWifiIsEnabled();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (m_receiver != null) {
            try {
                unregisterReceiver(m_receiver);
            } catch (Exception e) {
                Writer writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                Log.e(TAG, "onDestroy  " + writer.toString());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (m_useHomeCheckBox.isChecked() || (m_WiFiPassword.getText().toString().trim().length() > 0 && m_WiFiSSID.getVisibility() == View.GONE)
                || (m_WiFiPassword.getText().toString().trim().length() > 0 && m_WiFiSSID.getText().toString().trim().length() > 0)) {
            m_NextButton.setEnabled(true);
        } else {
            m_NextButton.setEnabled(false);
        }
    }


    // this function unregisters the m_scanReceiver after a user clicked /changed something in the screen!
    private void handleUserClick(boolean isPause)
    {
        if (!isPause && selectedAP==null){
            return;
        }
        if (!hasUserClicked){
            hasUserClicked=true;
            unregisterReceiver(m_receiver);
        }
    }

    private void goToStepTwo() {
        // hide the keyboard before leaving the page
        UIUtil.hideIme(m_WiFiSSID);
        final Intent launchIntent = new Intent(SetupDeviceStepOneActivity.this, SetupDeviceStepTwoActivity.class);
        launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_SSID, m_deviceSSID);
        SetupDeviceStepOneActivity.this.startActivity(launchIntent);
        finish();
    }

    private  void manageWifiAuthenticationLocalFailed() {
        launchBasicAlertDialog(getString(R.string.alert_title_wifi_authentication_failed), 0,
                String.format("Please check the \"%s\" network password and try again.", UIUtil.getUserFriendlyAP(target.getSSID())), getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {
                m_WiFiPassword.setText("");
                m_progressLayout.setVisibility(View.INVISIBLE);
                unBlockUI();
            }
        }, null, null, null, null, null, new Runnable() {
            @Override
            public void run() {
                dismissAlertDialog();
                m_WiFiPassword.setText("");
                m_progressLayout.setVisibility(View.INVISIBLE);
                unBlockUI();

            }
        });
    }

    private void prepareBroadcasrReciever() {
        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received intent: Action = " + intent.getAction()
                        + (intent.getExtras() != null ? Util.bundleToString(intent.getExtras()) : ""));

                if (OnboardingManager.STATE_CHANGE_ACTION.equals(intent.getAction())) {
                    String state = intent.getStringExtra(OnboardingManager.EXTRA_ONBOARDING_STATE);
                    if (OnboardingState.CONNECTED_OTHER_WIFI.name().equals(state)) {
                        if (m_saveAsHome.getVisibility() == View.VISIBLE && m_saveAsHome.isChecked()) {
                            UISharedPreferencesManager.setStoredNetwork(m_onboardName, m_onboardPass, m_selectedAuthType, m_isHidden);
                        }
                        unregisterReceiver(this);
                        m_receiver = null;
                        UIUtil.showToastFromIntent(SetupDeviceStepOneActivity.this, getString(R.string.toast_connected_to, UIUtil.getUserFriendlyAP(m_onboardName)));
                        goToStepTwo();
                    }
                } else if (OnboardingManager.ERROR.equals(intent.getAction())) {
                    String errorDetails = intent.getStringExtra(OnboardingManager.EXTRA_ERROR_DETAILS);
                    if (OnboardingErrorType.OTHER_WIFI_AUTH.name().equals(errorDetails)) {
                        manageWifiAuthenticationLocalFailed();
                    } else if (OnboardingErrorType.OTHER_WIFI_TIMEOUT.name().equals(errorDetails)) {
                        manageWifiConnectionFailed(target.getSSID());
                    }

                } else if (OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    processScanResults();
                }

            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(OnboardingManager.STATE_CHANGE_ACTION);
        filter.addAction(OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(OnboardingManager.ERROR);
        registerReceiver(m_receiver, filter);
    }

    // Logic to determine if the password field should be shown and if the
    // verify button should be enabled
    private void processSelectedAP() {

        if (!selectedAP.getAuthType().equals(AuthType.OPEN)) {
            m_PasswordView.setVisibility(View.VISIBLE);
            if (m_WiFiPassword.getText().toString().length() > 0
                    && (m_WiFiSSID.getVisibility() == View.GONE || (m_WiFiSSID.getVisibility() == View.VISIBLE && m_WiFiSSID.getText().toString().trim().length() > 0))) {
                m_NextButton.setEnabled(true);
            } else {
                m_NextButton.setEnabled(false);
            }
        } else {
            m_PasswordView.setVisibility(View.GONE);
            m_NextButton.setEnabled(true);
        }
    }

    // Populate the Spinner with the scan results
    // Only show 1 entry if multiple results are nearby
    public void processScanResults() {
        final List<WiFiNetwork> candidateTargetNetworks = OnboardingManager.getInstance().getWifiScanResults(WifiFilter.TARGET);
        if (candidateTargetNetworks == null) {
            return;
        }
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNearbyAPs.clear();
                Collections.sort(candidateTargetNetworks, new Comparator<WiFiNetwork>() {
                    @Override
                    public int compare(WiFiNetwork o1, WiFiNetwork o2) {
                        if (o1.getLevel() > o2.getLevel()) {
                            return -1;
                        } else if (o1.getLevel() < o2.getLevel()) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                });

                final String currentSSID = WifiUtil.getInstance().getCurrentSSID();
                int count = 0;
                final Iterator<WiFiNetwork> it = candidateTargetNetworks.iterator();
                while (it.hasNext()) {
                    final WiFiNetwork target = it.next();

                    boolean isProtected = false;
                    if (!target.getAuthType().equals(AuthType.OPEN)) {
                        isProtected = true;
                    }

                    mNearbyAPs.add(target, isProtected);
                    if (currentSSID != null && currentSSID.equals(WifiUtil.getInstance().getNormalizedSSID(target.getSSID()))) {
                        m_SelectWifi.setSelection(count);
                    }
                    count++;
                }
            }
        });
    }

    private void blockUIBeforAsynchTask() {

        UIUtil.hideIme(m_WiFiPassword);

        m_NextButton.setEnabled(false);
        if (m_PasswordView.getVisibility() == View.VISIBLE) {
            m_WiFiPassword.setEnabled(false);
            m_WiFiShowPassword.setEnabled(false);
        }
        if (m_SelectWifi.getVisibility() == View.VISIBLE) {
            m_SelectWifi.setEnabled(false);
        }

        if (m_SelectAuthType.getVisibility() == View.VISIBLE) {
            m_SelectAuthType.setEnabled(false);
        }
        if (m_WiFiSSID.getVisibility() == View.VISIBLE) {
            m_WiFiSSID.setEnabled(false);
        }
        if (m_useHomeCheckBox.getVisibility() == View.VISIBLE) {
            m_useHomeCheckBox.setEnabled(false);
        }
        if (m_saveAsHome.getVisibility() == View.VISIBLE) {
            m_saveAsHome.setEnabled(false);
        }

        if (mHiddenSSID.getVisibility() == View.VISIBLE) {
            mHiddenSSID.setEnabled(false);
        }


        m_toggleLayout.setEnabled(false);

    }

    private void unBlockUI() {

        m_NextButton.setEnabled(true);
        if (m_PasswordView.getVisibility() == View.VISIBLE) {
            m_WiFiPassword.setEnabled(true);
            m_WiFiShowPassword.setEnabled(true);
        }
        if (m_SelectWifi.getVisibility() == View.VISIBLE) {
            m_SelectWifi.setEnabled(true);
        }

        if (m_SelectAuthType.getVisibility() == View.VISIBLE) {
            m_SelectAuthType.setEnabled(true);
        }
        if (m_WiFiSSID.getVisibility() == View.VISIBLE) {
            m_WiFiSSID.setEnabled(true);
        }
        if (m_useHomeCheckBox.getVisibility() == View.VISIBLE) {
            m_useHomeCheckBox.setEnabled(true);
        }
        if (m_saveAsHome.getVisibility() == View.VISIBLE) {
            m_saveAsHome.setEnabled(true);
        }

        if (mHiddenSSID.getVisibility() == View.VISIBLE) {
            mHiddenSSID.setEnabled(true);
        }

        m_toggleLayout.setEnabled(true);

    }
}
