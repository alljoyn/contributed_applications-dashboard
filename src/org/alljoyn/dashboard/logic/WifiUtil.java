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
package org.alljoyn.dashboard.logic;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
import org.alljoyn.onboarding.sdk.OnboardingIllegalArgumentException;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.sdk.WiFiNetworkConfiguration;
import org.alljoyn.onboarding.sdk.WifiDisabledException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Bundle;
import android.util.Log;

public class WifiUtil {

    static String TAG = "DASHBOARD_WifiUtil";

    private static WifiUtil instance;
    private MulticastLock m_multicastLock;
    BroadcastReceiver m_scanner;
    private Timer m_wifiTimeout = new Timer();
    private volatile WifiConfiguration targetWifiConfiguration=null;
    private Context context;
    private static Boolean m_synchBoolean=Boolean.valueOf(false);
    private final int timeout=20*1000;

    public static WifiUtil getInstance() {
        if (instance == null) {
            instance = new WifiUtil();
        }
        return instance;
    }

    public void init(Context c)
    {
        context = c;
    }

    public String getCurrentSSID()
    {
        WiFiNetwork network;
        try {
            network = OnboardingManager.getInstance().getCurrentNetwork();
        } catch (WifiDisabledException e) {
            e.printStackTrace();
            return "";
        }
        if (network == null) {
            return "";
        }
        return network.getSSID();
    }

    public String getNormalizedSSID(String ssid) {
        if (ssid == null || ssid.length() == 0) {
            return ssid;
        }

        if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
            return ssid.substring(1, ssid.length() - 1);
        }

        return ssid;
    }

    public boolean isSsidEquals(String ssid1, String ssid2)
    {
        if (ssid1 == null || ssid1.length() == 0 || ssid2 == null || ssid2.length() == 0) {
            return false;
        }
        if (ssid1.startsWith("\"") && ssid1.endsWith("\"") && ssid1.length()>=2){
            ssid1 = ssid1.substring(1,ssid1.length()-1);
        }
        if (ssid2.startsWith("\"") && ssid2.endsWith("\"") && ssid2.length()>=2 ){
            ssid2 = ssid2.substring(1,ssid2.length()-1);
        }
        return ssid1.equals(ssid2);
    }

    public boolean isWifiEnable() {
        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        Log.i(TAG, "isWifiEnable: wifiState " + m_wifi.getWifiState());
        int wifiState = m_wifi.getWifiState();

        if (wifiState == WifiManager.WIFI_STATE_ENABLED || wifiState == WifiManager.WIFI_STATE_ENABLING) {
            return true;
        }
        return false;
    }

    public void enableWifi(boolean b) {
        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        m_wifi.setWifiEnabled(b);
    }

    public void enableAllWifiNetworks() {
        final long startMillis = System.currentTimeMillis();
        if (context!=null){
	        Log.d(TAG, "enableAllWifiNetworks start");
	        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	        if (m_wifi!=null){
		        List<WifiConfiguration> configuredNetworks = m_wifi.getConfiguredNetworks();
		        for (WifiConfiguration wifiConfiguration: configuredNetworks) {
		            if (wifiConfiguration.status == WifiConfiguration.Status.DISABLED) {
		                Log.d(TAG, "enableAllWifiNetworks enabling " + wifiConfiguration.SSID);
		                m_wifi.enableNetwork(wifiConfiguration.networkId, false);
		            }
		        }
	        }
        }
        Log.d(TAG, String.format("enableAllWifiNetworks end after %s milliseconds",Long.toString(System.currentTimeMillis() - startMillis)));
    }

    public void connectToNetwork(WiFiNetworkConfiguration netConfiguration) throws WifiDisabledException, OnboardingIllegalArgumentException{
        OnboardingManager.getInstance().connectToNetwork(netConfiguration, OnboardingManager.DEFAULT_WIFI_CONNECTION_TIMEOUT);
    }

    public void connectToNetwork(String ssid)
    {
        Log.d(TAG, "connectToNetwork: " + ssid + " CurrentSSID: " + getCurrentSSID());

        if (ssid == null)
        {

            ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_WIFI_CANT_CONNECT);
            return;
        }
        if (isSsidEquals(ssid, getCurrentSSID()))
        {

            ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_WIFI_CONNECTED);
        }
        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> configuredNetworks = m_wifi.getConfiguredNetworks();

        for (WifiConfiguration current : configuredNetworks) {
            if (current.SSID != null && isSsidEquals(current.SSID,ssid)){
                Log.d(TAG, "connectToNetwork found");
                connect(current,timeout);
                return;
            }
        }

        ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_WIFI_CANT_CONNECT);
    }

    private boolean connect(final WifiConfiguration wifiConfig, final int timeoutMsec) {
        Log.i(TAG, "connect  SSID=" + wifiConfig.SSID + " within " + timeoutMsec);
        boolean res;

        // this is the ying of the needed acquireMulticastLock that enables boards can discover our bundled daemon (Moto-X)
        releaseMulticastLock();

        synchronized (m_synchBoolean) {
            targetWifiConfiguration=wifiConfig;
        }
        m_wifiTimeout.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.e(TAG,  "Network Listener AJ_ON_WIFI_TIMEOUT  when trying to connect to "+ getNormalizedSSID(wifiConfig.SSID));
                Bundle extras = new Bundle();
                extras.putParcelable(IntentExtraKeys.WIFI_WIFICONFIGURATION, wifiConfig);
                ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_WIFI_TIMEOUT, extras);
            }
        }, timeoutMsec);

        initBroadcastReceiver();
        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        res=false;
        res=m_wifi.disconnect();
        Log.i(TAG, "connect disconnect  status="+res);
        res=m_wifi.enableNetwork(wifiConfig.networkId, true);
        Log.i(TAG, "connect enableNetwork [true] status=" + res);
        return res;
    }

    private void initBroadcastReceiver()
    {
        m_scanner = new BroadcastReceiver()
        {
            // will get here after scan
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String s=Util.bundleToString(intent.getExtras());
                Log.i(TAG, "Network Listener '" +intent.getAction() + (s.length()==0?"' ":"' "+s));

                // The state of Wi-Fi connectivity has changed. (Connected, Disconnected, Connecting etc.)
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())){
                    NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (networkInfo != null){
                        NetworkInfo.State currentState = networkInfo.getState();
                        if(currentState != null){

                            if(!networkInfo.isConnected()){
                                Log.i(TAG, "Network Listener Got network changed but isConnected is false");
                                return;
                            }

                            WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                            if(null == wifiInfo){
                                Log.i(TAG, "Network Listener Couldn't get WifiInfo");
                                //TODO resolve this
                            }
                            Bundle extras = new Bundle();

                            Log.d(TAG, "Network Listener success in connecting to "+ getCurrentSSID());

                            synchronized (m_synchBoolean) {
                                // this is needed so that boards can discover our bundled daemon (Moto-X)
                                acquireMulticastLock();

                                if (targetWifiConfiguration!=null)
                                {
                                    if (((wifiInfo!=null) && isSsidEquals(targetWifiConfiguration.SSID,wifiInfo.getSSID())) ||
                                            isSsidEquals(targetWifiConfiguration.SSID,getCurrentSSID()))
                                    {
                                        m_wifiTimeout.cancel();
                                        m_wifiTimeout.purge();
                                        m_wifiTimeout=new Timer();

                                        extras.putParcelable(IntentExtraKeys.WIFI_WIFICONFIGURATION, targetWifiConfiguration);
                                        targetWifiConfiguration=null;
                                    }
                                }
                                if(!extras.containsKey(IntentExtraKeys.WIFI_WIFICONFIGURATION)&& wifiInfo != null){
                                    extras.putParcelable(IntentExtraKeys.WIFI_WIFICONFIGURATION, wifiInfo);
                                }
                                ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_WIFI_CONNECTED, extras);
                            }

                        }
                    }

                }

                else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction()))
                {
                    if (intent.hasExtra(WifiManager.EXTRA_SUPPLICANT_ERROR)){
                        if (intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0) ==1 )
                        {
                            synchronized (m_synchBoolean){
                                if (targetWifiConfiguration!=null){
                                    Log.e(TAG, "Network Listener ERROR_AUTHENTICATING when trying to connect to " + getNormalizedSSID(targetWifiConfiguration.SSID));
                                    m_wifiTimeout.cancel();
                                    m_wifiTimeout.purge();
                                    m_wifiTimeout=new Timer();
                                    Bundle extras = new Bundle();
                                    extras.putParcelable(IntentExtraKeys.WIFI_WIFICONFIGURATION, targetWifiConfiguration);
                                    ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_LOGIC_WIFI_AUTHENTICATION_ERROR,extras);
                                    targetWifiConfiguration=null;
                                }

                            }

                            //need to add code to remove the netwrok id so we can change in the second try!!!
                        }
                    }
                }

            }
        };


        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);

        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        context.registerReceiver(m_scanner, filter,null,AlljoynOnApplication.getHanlder());
    }


    public void acquireMulticastLock() {
        Log.d(TAG, "acquireMulticastLock");

        // we must renew the multicast lock every time.
        // After Wi-Fi switches, we have a non-null, held, lock but it's not
        // working - Android ignores it.

        // keep the environment clean. Dispose old lock
        if (m_multicastLock != null) {
            Log.d(TAG, "releasing MulticastLock");
            m_multicastLock.release();
            Log.d(TAG, "released MulticastLock");
        }

        Log.d(TAG, "acquiring MulticastLock");
        WifiManager m_wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        m_multicastLock = m_wifi.createMulticastLock("multicastLock");
        m_multicastLock.acquire();
        Log.d(TAG, "acquired MulticastLock");
    }

    public void releaseMulticastLock() {
        Log.d(TAG, "releaseMulticastLock");
        if (m_multicastLock != null) {
            Log.d(TAG, "releasing MulticastLock");
            m_multicastLock.release();
            m_multicastLock = null;
            Log.d(TAG, "released MulticastLock");
        }
    }

}
