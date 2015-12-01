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

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.ui.util.UIUtil;
import org.alljoyn.onboarding.sdk.OnboardingConfiguration;
import org.alljoyn.onboarding.sdk.WiFiNetworkConfiguration;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SetupDeviceBaseActivity extends BaseActivity {

    protected static String m_deviceSSID;
    protected static String m_currentAP;
    protected static String m_onboardName;
    protected static boolean m_isHidden;
    protected static OnboardingConfiguration onboardingConfiguration;
    protected static WiFiNetworkConfiguration target;
    protected static WiFiNetworkConfiguration onboardee;

 // progress bar
    protected LinearLayout m_progressLayout;
    protected TextView m_progressText;
    
    protected String TAG = "DASHBOARD_Setup Device";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // prevent tapping outside from closing the activity
        blockOutsideClicks();
    }

    @Override
    public void onBackPressed() {

        launchAreYouSureCancelSetupDialog(

                UIUtil.getUserFriendlyAP(m_deviceSSID),

                new Runnable() {

                    @Override
                    public void run() {
                        // don't call super.OnBackPressed() or else the transition
                        // animation will be called
                        SetupDeviceBaseActivity.this.finish();
                    }
                });

    }

    protected void updateProgress(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (m_progressText != null && m_progressLayout != null){
                    m_progressText.setText(msg);
                    m_progressLayout.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    protected void hideProgress() {
        if (m_progressLayout != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    m_progressLayout.setVisibility(View.GONE);
                }
            });
        }
    }
    
    protected void handleError(String popupTitle, String popupMsg, final String ssid) {
        if (popupTitle == null || popupMsg == null) {
            if (m_currentAP == null || WifiUtil.getInstance().isSsidEquals(ssid, m_currentAP)) {
                enableAllWifi();
            } else {
                verifyWifiConnection(m_currentAP);
            }
            return;
        }
        launchBasicAlertDialog(popupTitle, 0, popupMsg, getString(R.string.dismiss), new Runnable() {
            @Override
            public void run() {
                verifyWifiConnection(ssid);
            }
        }, null, null, null, null);
    }

    protected void verifyWifiConnection(String lastFailedSsid) {
        Log.d(TAG, String.format("verifyWifiConnection lastFailedSsid=%s currentAP=%s", lastFailedSsid, m_currentAP));
        if (m_currentAP == null || (lastFailedSsid != null && WifiUtil.getInstance().isSsidEquals(lastFailedSsid, m_currentAP))) {
            enableAllWifi();
        } else {
            WifiUtil.getInstance().connectToNetwork(m_currentAP);
        }
    }


    protected void enableAllWifi() {
        Log.i(TAG, "enableAllWifi");
        WifiUtil.getInstance().enableAllWifiNetworks();
        final Intent launchIntent = new Intent(SetupDeviceBaseActivity.this, NearbyDevicesActivity.class);
        SetupDeviceBaseActivity.this.startActivity(launchIntent);
        finish();
    }


    protected void manageWifiConnectionFailed(final String ssid) {
        String popupTitle = getString(R.string.alert_title_connection_failed);
        String popupMsg = getString(R.string.alert_message_wifi_connection_failed, UIUtil.getUserFriendlyAP(target.getSSID()));

        handleError(popupTitle, popupMsg, ssid);
    }
}

