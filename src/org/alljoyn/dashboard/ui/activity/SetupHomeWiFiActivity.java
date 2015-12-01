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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.Util;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.adapter.SimpleTextSpinnerAdapter;
import org.alljoyn.dashboard.ui.adapter.WifiNetworkSpinnerAdapter;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
import org.alljoyn.dashboard.ui.util.UISharedPreferencesManager;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.dashboard.ui.wrappers.AuthTypeWrapper;
import org.alljoyn.onboarding.OnboardingService.AuthType;
import org.alljoyn.onboarding.sdk.OnboardingIllegalArgumentException;
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
import android.widget.Spinner;
import android.widget.TextView;


public class SetupHomeWiFiActivity extends BaseActivity {
    private static final String TAG = "DASHBOARD_SetupHomeWiFi";
    private Spinner mSelectWifi;
    private Spinner mSelectAuthType;
    private EditText mWiFiSSID;
    private EditText mWiFiPassword;
    private CheckBox mWiFiShowPassword;
    private TextView mWiFiSSIDLabel;
    private TextView mPasswordLabel;
    private CheckBox mHiddenSSID;
    private Button mNextButton;
    private Button mSkipButton;

    private View mPasswordView;
    private View mProgressbarView;
    private WifiNetworkSpinnerAdapter mNearbyAPs;
    private ArrayAdapter<AuthTypeWrapper> mAuthTypes;
    private List<WiFiNetworkConfiguration> mResults;
    private WiFiNetwork selectedAP = null;
    private AuthTypeWrapper selectedAuth;

    public static String mAPName;
    public static String mAPPassword;
    public static AuthType mAuthType;
    public static boolean mIsHidden;

    boolean skipToHome;

    private boolean isInFront;

    private ViewGroup m_toggleLayout = null;
    private TextView m_toggleOnText = null;
    private TextView m_toggleOffText = null;
    private BroadcastReceiver m_scanReceiver;
    private final IntentFilter m_scanFilter=new IntentFilter();

    //flag to indicate if a user has clicked on one of the buttons or changed some text.
    private boolean hasUserClicked=false;
    // flag to prevent misfire of WifiSpinner on first time.
    private boolean isSelectWifiSpinnerFlag=false;


    private final BroadcastReceiver m_internalBroadCastReciver=new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {

            String s=Util.bundleToString(intent.getExtras());
            Log.i(TAG, "SetupHomeWiFiActivity reciver '" +intent.getAction() + (s.length()==0?"' ":"' "+s));
            if (OnboardingManager.STATE_CHANGE_ACTION.equals(intent.getAction())) {

                if (intent.hasExtra(OnboardingManager.EXTRA_ONBOARDING_STATE)) {
                    String stateString = intent.getStringExtra(OnboardingManager.EXTRA_ONBOARDING_STATE);
                    if (stateString != null) {
                        OnboardingState onboardingState = OnboardingState.getOnboardingStateByString(stateString);
                        if (onboardingState != null) {
                            if (onboardingState == OnboardingState.CONNECTED_OTHER_WIFI) {
                                unregisterReceiver(m_internalBroadCastReciver);
                                restoreUIAfterAsynchTask();
                                Log.d(TAG, String.format("Saving  SSID :'%s, AuthType:%s pass:%s ,hidden:%b", mAPName, mAuthType.toString(),mAPPassword,mIsHidden));
                                UISharedPreferencesManager.setStoredNetwork(mAPName, mAPPassword, mAuthType,mIsHidden);
                                UISharedPreferencesManager.setHomeNetwork(true,false);
                                UIUtil.showToastFromIntent(SetupHomeWiFiActivity.this, getString(R.string.toast_connected_to, UIUtil.getUserFriendlyAP(mAPName)));
                                goToNextStep();
                            }
                        }
                    }
                }
            } else if (OnboardingManager.ERROR.equals(intent.getAction())) {
                unregisterReceiver(m_internalBroadCastReciver);
                if (intent.hasExtra(OnboardingManager.EXTRA_ERROR_DETAILS)) {
                    String errorDetails = intent.getStringExtra(OnboardingManager.EXTRA_ERROR_DETAILS);
                    if (errorDetails != null) {
                        OnboardingErrorType onboardingErrorType = OnboardingErrorType.getOnboardingErrorTypeByString(errorDetails);
                        if (onboardingErrorType != null) {
                            if (onboardingErrorType == OnboardingErrorType.OTHER_WIFI_AUTH) {
                                restoreUIAfterAsynchTask();
                                mWiFiPassword.setText("");
                                showPopup(getString(R.string.wifi_authentication_title), getString(R.string.wifi_authentication_message, mAPName));
                            } else if (onboardingErrorType == OnboardingErrorType.OTHER_WIFI_TIMEOUT) {
                                restoreUIAfterAsynchTask();
                                mWiFiPassword.setText("");
                                showPopup(getString(R.string.wifi_connection_title), getString(R.string.wifi_connection_message, mAPName));
                            }
                        }

                    }else{
                        restoreUIAfterAsynchTask();
                        mWiFiPassword.setText("");
                        showPopup(getString(R.string.wifi_connection_title), getString(R.string.wifi_connection_message, mAPName));
                    }
                }
            }
        }
    };



    IntentFilter m_internalBroadCastFiter = new IntentFilter();



    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // prevent tapping outside from closing the activity
        blockOutsideClicks();

        // prevent the window from moving around the screen when the height changes
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        Window window = getWindow();

        if (window!=null){
            lp.copyFrom(window.getAttributes());
            lp.gravity = Gravity.TOP + Gravity.CENTER_HORIZONTAL;
            window.setAttributes(lp);
        }

        skipToHome = getIntent().getBooleanExtra(IntentExtraKeys.EXTRA_BOOLEAN_KEY, false);
        setContentView(R.layout.page_setup_home_wifi);

        mSelectWifi = (Spinner) findViewById(R.id.select_wifi_network);
        mSelectAuthType = (Spinner) findViewById(R.id.connect_wifi_authType_entry);
        // mAdvancedToggle = (ToggleButton) findViewById(R.id.advanced_options_toggle);
        mWiFiSSID = (EditText) findViewById(R.id.wifiSSID);
        mPasswordView=findViewById(R.id.password_layout);

        mWiFiPassword = (EditText) findViewById(R.id.wifiPassword);
        mWiFiShowPassword = (CheckBox) findViewById(R.id.wifi_show_password);
        mPasswordLabel = (TextView) findViewById(R.id.enter_wifi_password);
        mWiFiSSIDLabel = (TextView) findViewById(R.id.select_wifi);

        mNextButton = (Button) findViewById(R.id.onboarding_startup_next);
        mSkipButton = (Button) findViewById(R.id.onboarding_startup_skip);
        //mBadInfoLabel = (TextView) findViewById(R.id.bad_info);
        mHiddenSSID=(CheckBox) findViewById(R.id.hiddenssid_checkbox);
        mProgressbarView=findViewById(R.id.progressbar_layout);

        mSelectAuthType.setVisibility(View.GONE);
        mWiFiSSID.setVisibility(View.GONE);
        m_toggleLayout = (ViewGroup) findViewById(R.id.manage_device_toggle_layout);
        m_toggleOnText =(TextView) findViewById(R.id.toggle_on);
        m_toggleOffText =(TextView) findViewById(R.id.toggle_off);

        m_internalBroadCastFiter.addAction(OnboardingManager.STATE_CHANGE_ACTION);
        m_internalBroadCastFiter.addAction(OnboardingManager.ERROR);


        final OnClickListener toggleClickListener = new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                handleUserClick(false);

                if (mWiFiSSID.getVisibility() == View.GONE)
                {
                    m_toggleOnText.setVisibility(View.VISIBLE);
                    m_toggleOffText.setVisibility(View.GONE);

                    mWiFiSSID.setVisibility(View.VISIBLE);
                    mWiFiSSID.setText("");
                    mWiFiSSIDLabel.setVisibility(View.GONE);
                    mWiFiPassword.setText("");
                    mPasswordView.setVisibility(View.GONE);
                    mSelectAuthType.setSelection(0);
                    mSelectWifi.setVisibility(View.GONE);
                    mSelectAuthType.setVisibility(View.VISIBLE);
                    mHiddenSSID.setVisibility(View.VISIBLE);

                    //processSelectedAP();

                }
                else
                {
                    m_toggleOnText.setVisibility(View.GONE);
                    m_toggleOffText.setVisibility(View.VISIBLE);
                    mWiFiSSID.setVisibility(View.GONE);
                    mWiFiSSIDLabel.setVisibility(View.VISIBLE);
                    mSelectWifi.setVisibility(View.VISIBLE);
                    mSelectAuthType.setVisibility(View.GONE);
                    mWiFiPassword.setText("");
                    mHiddenSSID.setVisibility(View.GONE);
                    mHiddenSSID.setChecked(false);
                    processSelectedAP();

                }
            }
        };

        m_toggleLayout.setOnClickListener(toggleClickListener);

        //Handle showing the password
        mWiFiShowPassword.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                handleUserClick(false);
                if (buttonView.isChecked()){
                    UIUtil.removeHashFromEditText(mWiFiPassword);
                    mWiFiPassword.setSelection(mWiFiPassword.getText().length(), mWiFiPassword.getText().length());
                }else{
                    UIUtil.hashEditTextForPasswords(mWiFiPassword);
                    mWiFiPassword.setSelection(mWiFiPassword.getText().length(), mWiFiPassword.getText().length());
                }
            }
        });

        //Set the adapter to list the nearby access points
        mNearbyAPs = new WifiNetworkSpinnerAdapter(this, R.layout.list_item_spinner);
        mSelectWifi.setAdapter(mNearbyAPs);

        //Handle the selection

        mSelectWifi.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if(isSelectWifiSpinnerFlag){
                    handleUserClick(false);
                }else{
                    isSelectWifiSpinnerFlag=true;
                }
                selectedAP = mNearbyAPs.getItem(position);
                Log.d(TAG, String.format("Item selected, name:'%s', capabilities:'%s'", selectedAP.getSSID(), selectedAP.getAuthType()));
                mWiFiPassword.setText("");
                processSelectedAP();
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) { }
        });

        //Setup the Auth types we want to support
        mAuthTypes = new SimpleTextSpinnerAdapter(this, R.layout.list_item_spinner);
        AuthType[] types = new AuthType[] {
                AuthType.OPEN,
                AuthType.WEP,
                AuthType.WPA2_AUTO,
                AuthType.WPA_AUTO
        };
        for(AuthType type: types) {
            mAuthTypes.add(new AuthTypeWrapper(type));
        }
        mSelectAuthType.setAdapter(mAuthTypes);

        //Handle the selection of an AuthType
        mSelectAuthType.setOnItemSelectedListener(new OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {

                handleUserClick(false);

                selectedAuth = mAuthTypes.getItem(position);
                final AuthType selectedAuthType = selectedAuth.value;
                Log.d(TAG, String.format("Item selected, label:'%s', type:'%s'", selectedAuth.toString(), selectedAuthType));
                if (!AuthType.OPEN.equals(selectedAuthType))
                {
                    mPasswordView.setVisibility(View.VISIBLE);
                    if(mWiFiPassword.getText().toString().length() == 0){
                        mNextButton.setEnabled(false);
                    }else{
                        mNextButton.setEnabled(true);
                    }

                } else {
                    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mWiFiPassword.getWindowToken(), 0);
                    mPasswordView.setVisibility(View.GONE);
                    if(mWiFiSSID.getText().toString().trim().length() > 0){
                        mNextButton.setEnabled(true);
                    }else{
                        mNextButton.setEnabled(false);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) { }
        });

        //Handle the verifiy button press

        mNextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                handleUserClick(false);
                mNextButton.setEnabled(false);
                mProgressbarView.setVisibility(View.VISIBLE);
                mSkipButton.setEnabled(true);
                mSkipButton.setText(R.string.cancel);
                blockUIBeforAsynchTask();
                doVerify();
                registerReceiver(m_internalBroadCastReciver, m_internalBroadCastFiter);
                try {
                    WifiUtil.getInstance().connectToNetwork(new WiFiNetworkConfiguration(mAPName, mAuthType, mAPPassword, mIsHidden));
                } catch (WifiDisabledException e) {
                    e.printStackTrace();
                    verifyWifiIsEnabled();
                } catch (OnboardingIllegalArgumentException e) {
                    e.printStackTrace();
                    launchBasicAlertDialog("Illegal parameters", "Please verify that the network credentials legal.", getString(R.string.dismiss), null, null);
                }
            }
        });


        //Dismiss the activity and save that the user selected skip
        mSkipButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                handleUserClick(false);
                UISharedPreferencesManager.setHomeNetwork(false,true);
                goToNextStep();
            }
        });

        //Monitor as text is entered to enable the verify button
        mWiFiSSID.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                handleUserClick(false);
                if( (mWiFiSSID.getText().toString().trim().length() > 0 && selectedAuth.value == AuthType.OPEN)
                        || (mWiFiPassword.getText().toString().trim().length() > 0&& mWiFiSSID.getText().toString().trim().length() > 0 && selectedAuth.value != AuthType.OPEN) ){
                    mNextButton.setEnabled(true);
                } else {
                    mNextButton.setEnabled(false);
                }
            }
        });
        //Monitor as text is entered to enable the verify button
        mWiFiPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                handleUserClick(false);


                if( (mWiFiPassword.getText().toString().trim().length() > 0 && mWiFiSSID.getVisibility() == View.GONE)
                        || (mWiFiPassword.getText().toString().trim().length() > 0 && mWiFiSSID.getText().toString().trim().length() >0) ){
                    mNextButton.setEnabled(true);
                } else {
                    if (mPasswordView.getVisibility()==View.VISIBLE){
                        mNextButton.setEnabled(false);
                    }
                }
            }
        });


        m_scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())){
                    mSelectWifi.setEnabled(true);
                    processScanResults();
                }
            }
        };
        m_scanFilter.addAction(OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(m_scanReceiver, m_scanFilter);
        try {
            OnboardingManager.getInstance().scanWiFi();
        } catch (WifiDisabledException e) {
            verifyWifiIsEnabled();
            e.printStackTrace();
        }

        //List<WiFiNetwork> nonAJSoftAP=OnboardingManager.getInstance().getWifiScanResults(WifiFilter.TARGET);
       // if (nonAJSoftAP!=null && !nonAJSoftAP.isEmpty()){
        //    processScanResults();
       // }

         mSelectWifi.setEnabled(false);
         mNextButton.setEnabled(false);


    }


    // this function unregisters the m_scanReceiver after a user clicked /changed something in the screen!
    private void handleUserClick(boolean isPause)
    {
        if (!isPause && selectedAP==null){
            return;
        }
        if (!hasUserClicked){
            hasUserClicked=true;
            unregisterReceiver(m_scanReceiver);
        }
    }

    @Override
    public void onBackPressed()
    {
        goToNextStep();
    }

    //Start the process to verify the entered information.
    private void doVerify() {
        boolean hasPassword = false;
        // manual
        if(mWiFiSSID.getVisibility() == View.VISIBLE) {
            mAPName = mWiFiSSID.getText().toString();
            mAuthType = selectedAuth.value;
        }
        // auto
        else {
            mAPName = selectedAP.getSSID();
            mAuthType = selectedAP.getAuthType();
        }
        if(mPasswordLabel.getVisibility() == View.VISIBLE) {
            hasPassword = true;
        }
        if(hasPassword) {
            mAPPassword = mWiFiPassword.getText().toString();
        } else {
            mAPPassword = null;
        }
        mIsHidden = mHiddenSSID.getVisibility()==View.VISIBLE && mHiddenSSID.isChecked();
    }

    //Logic to determine if the password field should be shown and if the verify button should be enabled
    private void processSelectedAP() {

        if (selectedAP==null) {
            return;
        }
        if(!selectedAP.getAuthType().equals(AuthType.OPEN)) {
            mPasswordView.setVisibility(View.VISIBLE);
            if( mWiFiPassword.getText().toString().length() > 0 &&
                    (mWiFiSSID.getVisibility()==View.GONE || (mWiFiSSID.getVisibility()==View.VISIBLE &&  mWiFiSSID.getText().toString().trim().length() > 0))){
                mNextButton.setEnabled(true);
            }else{
                mNextButton.setEnabled(false);
            }
        }else{
            mPasswordView.setVisibility(View.GONE);
            ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(mWiFiPassword.getWindowToken(), 0);
            mWiFiPassword.setText("");
            mNextButton.setEnabled(true);
        }
    }
    /*
	//TODO: There is a bug and calling the connectToHomeNetwork does not actually validate that the network info is correct
	private boolean connectToNetwork() {
		return DeviceManagerImpl.getWifiManager().createHomeNetwork(mAPName, mAPPassword, mAuthType, true);
	}*/

    //Populate the Spinner with the scan results
    //Only show 1 entry if multiple results are nearby
    public void processScanResults() {

        Log.d(TAG, " processScanResults ");
        final List<WiFiNetwork> candidateTargetNetworks = OnboardingManager.getInstance().getWifiScanResults(WifiFilter.TARGET);
        if (candidateTargetNetworks == null)
        {
            Log.d(TAG, " processScanResults no nonAJSoftAP");
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

                WiFiNetwork currentNet = null;
                try {
                    currentNet = OnboardingManager.getInstance().getCurrentNetwork();
                } catch (WifiDisabledException e) {
                    verifyWifiIsEnabled();
                    e.printStackTrace();
                }
                String currentSSID = null;
                if (currentNet != null){
                    currentSSID = WifiUtil.getInstance().getNormalizedSSID(currentNet.getSSID());
                }
                int count = 0;
                final Iterator<WiFiNetwork> it = candidateTargetNetworks.iterator();
                while (it.hasNext()) {
                    final WiFiNetwork network = it.next();

                    boolean isProtected = false;
                    if (!network.getAuthType().equals(AuthType.OPEN)) {
                        isProtected = true;
                    }

                    mNearbyAPs.add(network, isProtected);
                    if (currentSSID != null && currentSSID.equals(WifiUtil.getInstance().getNormalizedSSID(network.getSSID()))) {
                        mSelectWifi.setSelection(count);
                    }
                    count++;
                }

            }
        });
    }

    private void blockUIBeforAsynchTask(){

        UIUtil.hideIme(mWiFiPassword);
        if (mPasswordView.getVisibility()==View.VISIBLE){
            mWiFiPassword.setEnabled(false);
            mWiFiShowPassword.setEnabled(false);
        }
        if (mSelectWifi.getVisibility()==View.VISIBLE){
            mSelectWifi.setEnabled(false);
        }

        if (mSelectAuthType.getVisibility()==View.VISIBLE){
            mSelectAuthType.setEnabled(false);
        }
        if (mWiFiSSID.getVisibility()==View.VISIBLE){
            mWiFiSSID.setEnabled(false);
        }

        if (mHiddenSSID.getVisibility()==View.VISIBLE){
            mHiddenSSID.setEnabled(false);
        }
        m_toggleLayout.setEnabled(false);



    }


    private void restoreUIAfterAsynchTask(){
        mProgressbarView.setVisibility(View.GONE);
        boolean enableNext=false;

        if (mPasswordView.getVisibility()==View.VISIBLE){
            mWiFiPassword.setEnabled(true);
            mWiFiShowPassword.setEnabled(true);
            if(mWiFiPassword.getText().toString().length()>  0){
                enableNext=true;
            }
        }else{
            enableNext=true;
        }
        if (mSelectWifi.getVisibility()==View.VISIBLE){
            mSelectWifi.setEnabled(true);
        }

        if (mSelectAuthType.getVisibility()==View.VISIBLE){
            mSelectAuthType.setEnabled(true);
        }
        if (mWiFiSSID.getVisibility()==View.VISIBLE){
            mWiFiSSID.setEnabled(true);
            if (enableNext){
                mNextButton.setEnabled(true);
            }
        }

        if (mHiddenSSID.getVisibility()==View.VISIBLE){
            mHiddenSSID.setEnabled(true);
        }




        m_toggleLayout.setEnabled(true);
    }

    //Show a popup while verifying the network information
    private void showPopup(String title, String message)
    {
        launchBasicAlertDialog(title,
                message,
                "Dismiss",
                new Runnable()
        {
            @Override
            public void run()
            {
                dismissAlertDialog();
            }
        },
        null);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        isSelectWifiSpinnerFlag=false;
        isInFront = true;
        AlljoynOnApplication.getInstance().setSetupHomeWifiOn();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        handleUserClick(true);
        isInFront = false;
        AlljoynOnApplication.getInstance().setSetupHomeWifiOff();
        finish();
    }

    public boolean isInFront(){
        return isInFront;
    }

    private void goToNextStep()
    {
        UIUtil.hideIme(mWiFiPassword);
        WifiUtil.getInstance().enableAllWifiNetworks();
        if (skipToHome)
        {
            final Intent launchIntent = new Intent(SetupHomeWiFiActivity.this, NearbyDevicesActivity.class);
            setResult(RESULT_OK);
            startActivity(launchIntent);
        }
        finish();
    }
}
