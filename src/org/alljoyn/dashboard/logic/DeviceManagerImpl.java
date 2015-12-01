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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.alljoyn.about.AboutKeys;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Variant;
import org.alljoyn.dashboard.background.AOService;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.Device.ServiceType;
import org.alljoyn.dashboard.logic.Interface.DeviceManager;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
import org.alljoyn.onboarding.sdk.OnboardingIllegalArgumentException;
import org.alljoyn.onboarding.sdk.OnboardingIllegalStateException;
import org.alljoyn.onboarding.sdk.OnboardingManager;
import org.alljoyn.onboarding.sdk.OnboardingManager.WifiFilter;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.sdk.WifiDisabledException;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.utils.TransportUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

public class DeviceManagerImpl implements DeviceManager {

    private final String TAG = "DASHBOARD_DeviceManagerImpl";

    Hashtable<String, Device> m_deviceList = new Hashtable<String, Device>();
    protected static Context m_context;
    private BroadcastReceiver m_receiver;

    public static final String WifiAJLookupPrefix = "AJ_";
    public static final String WifiAJLookupSuffix = "_AJ";

    private static DeviceManager m_current;

    private ImageCacheManger m_ImageCacheManager = null;
    private final boolean m_isRunning = true;
    private static Bitmap defaultImage;

    private Timer m_pingTimer = null;
    
    private boolean m_processAnnouncements = false;

    public static DeviceManager getInstance() {
        if (m_current == null) {
            m_current = new DeviceManagerImpl();
        }
        return m_current;
    }

    // protected for the DummyDeviceManager
    protected DeviceManagerImpl() {
    }

    @Override
    public void init(Context c, String keyStorekeyStoreFileName) {
        m_context = c;

        SharedPreferencesManager.init(c);

        m_receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    refreshScanResult(intent);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(OnboardingManager.WIFI_SCAN_RESULTS_AVAILABLE_ACTION);
        m_context.registerReceiver(m_receiver, filter, null, AlljoynOnApplication.getHanlder());

        AjManager.getInstance().init(c, keyStorekeyStoreFileName);
        AjManager.getInstance().connectToAJ();
        // init onboarding service apk.
        try {
            OnboardingManager.getInstance().init(m_context, AjManager.getInstance().getBusAttachment());
        } catch (OnboardingIllegalArgumentException e) {
            Log.i(TAG, "shouldn't happen !!");
            e.printStackTrace();
        } catch (OnboardingIllegalStateException e) {
            e.printStackTrace();
            Log.i(TAG, "OnboardingManager already init.shotdown and reinit to make sure the parameters are updated.");
            try {
                OnboardingManager.getInstance().shutDown();
            } catch (OnboardingIllegalStateException e1) {
                Log.i(TAG, "shouldn't happen !!");
                e1.printStackTrace();
            }
            try {
                OnboardingManager.getInstance().init(m_context, AjManager.getInstance().getBusAttachment());
            } catch (OnboardingIllegalArgumentException e1) {
                Log.i(TAG, "shouldn't happen !!");
                e1.printStackTrace();
            } catch (OnboardingIllegalStateException e1) {
                Log.i(TAG, "shouldn't happen !!");
                e1.printStackTrace();
            }
        }
        
        m_processAnnouncements = true;

        m_ImageCacheManager = new ImageCacheManger();
        m_ImageCacheManager.init();

        defaultImage = BitmapFactory.decodeResource(c.getResources(), org.alljoyn.dashboard.R.drawable.my_devices_icon_reg);
        loadDevicesFromPreference();
    }

    public static Bitmap getDefaultBimapImage() {
        return defaultImage;
    }

    private ArrayList<Device> getAllDevices() {
        Log.i(TAG, "getAllDevices()");
        if (m_deviceList != null) {
            return new ArrayList<Device>(m_deviceList.values());
        } else {
            return new ArrayList<Device>();
        }
    }

    @Override
    public ArrayList<Device> getDevices() {
        Log.i(TAG, "getDevices()");
        if (m_deviceList == null) {
            return new ArrayList<Device>();
        }

        Collection<Device> allDevices = getAllDevices();
        Iterator<Device> iterator = allDevices.iterator();
        if (iterator != null) {
            Device current;
            ArrayList<Device> result = new ArrayList<Device>();
            while (iterator.hasNext()) {
                current = iterator.next();
                if (current.getStatus() != DeviceStatus.GONE) {
                    result.add(current);
                }
            }
            return result;
        }
        return new ArrayList<Device>();
    }

    @Override
    public boolean contains(UUID deviceID) {
        return m_deviceList.containsKey(deviceID.toString());
    }

    @Override
    public Device getDevice(UUID deviceID) {
        if (m_deviceList == null || deviceID == null) {
            return null;
        }

        return m_deviceList.get(deviceID.toString());
    }

    @Override
    public Device removeDevice(UUID deviceID) {
        if (deviceID == null) {
            return null;
        }
        // NotificationsManagerImpl.getInstance().removeNotificationsForDevice(deviceID);
        DeviceImpl d = (DeviceImpl) getDevice(deviceID);
        if (d != null) {
            d.setStatus(DeviceStatus.GONE);
            m_ImageCacheManager.removeDeviceFromUUUIDList(deviceID);
            updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED);
            return d;
        }
        return null;
        // return m_deviceList.remove(deviceID.toString());
    }

    @Override
    public Device getDeviceBySSID(String ssid) {
        return getDeviceBySSID(ssid, true);
    }

    private Device getDeviceBySSID(String ssid, Collection<Device> devices, boolean filterOutGoneDevices) {

        Log.i(TAG, "getDeviceBySSID");
        if (devices == null || ssid == null) {
            return null;
        }

        if (ssid != null && ssid.length() > 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        Iterator<Device> iterator = devices.iterator();
        if (iterator == null) {
            return null;
        }
        while (iterator.hasNext()) {
            Device current = iterator.next();
            if (current.getAPWifiInfo() != null && WifiUtil.getInstance().isSsidEquals(ssid, current.getAPWifiInfo().getSSID())) {
                if (filterOutGoneDevices && current.getStatus().equals(DeviceStatus.GONE)) {
                    return null;
                }
                return current;
            }
        }

        return null;

    }

    private Device getDeviceBySSID(String ssid, boolean filterOutGoneDevices) {

        Log.i(TAG, "getDeviceBySSID");
        Collection<Device> devices = getAllDevices();
        if (devices == null || ssid == null) {
            return null;
        }

        if (ssid != null && ssid.length() > 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }

        Iterator<Device> iterator = devices.iterator();
        if (iterator == null) {
            return null;
        }
        while (iterator.hasNext()) {
            Device current = iterator.next();
            if (current.getAPWifiInfo() != null && current.getAPWifiInfo().getSSID() != null && WifiUtil.getInstance().isSsidEquals(ssid, current.getAPWifiInfo().getSSID())) {
                if (filterOutGoneDevices && current.getStatus().equals(DeviceStatus.GONE)) {
                    return null;
                }
                return current;
            }
        }

        return null;
    }

    protected Device getDeviceByServiceName(String serviceName) {
        Collection<Device> devices = getAllDevices();
        if (devices == null) {
            return null;
        }

        Iterator<Device> iterator = devices.iterator();
        if (iterator != null) {
            Device current;
            while (iterator.hasNext()) {
                current = iterator.next();
                if (((DeviceImpl) current).getServiceName() != null && ((DeviceImpl) current).getServiceName().equals(serviceName)) {
                    return current;
                }
            }
        }

        return null;
    }

    private void loadDevicesFromPreference() {

        Log.i(TAG, "loadDevicesFromPreference");
        Map<String, String> devices = SharedPreferencesManager.getAllDevices();
        if (devices == null || devices.isEmpty()) {
            return;
        }
        Iterator<Entry<String, String>> iterator = devices.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> pairs = iterator.next();
            String deviceIdTmp = pairs.getKey();
            UUID deviceId = UUID.fromString(deviceIdTmp);// TransportUtil.byteArrayToUUID(deviceIdTmp.getBytes());

            Log.i(TAG, "loadDevicesFromPreference load from " + deviceId);
            boolean newDevice;
            DeviceImpl d = (DeviceImpl) getDevice(deviceId);
            if (d != null)// we have this device, probably from announcement
            {
                newDevice = false;
            } else {
                d = new DeviceImpl("", deviceId); // onboardedNetworkSSID will
                // be added below.
                newDevice = true;
            }
            String deviceData = pairs.getValue();
            d.setId(deviceId);
            String[] fields = deviceData.split(SharedPreferencesManager.pairsSeparator);
            for (int i = 0; i < fields.length; i++) {
                String[] keyValue = fields[i].split(SharedPreferencesManager.keyValueSeparator);// .substring(0,
                // end)
                if (keyValue[0].equals(SharedPreferencesManager.deviceSsid)) {
                    d.setAPWifiInfo(new WiFiNetwork(keyValue[1]));
                } else if (keyValue[0].equals(SharedPreferencesManager.deviceFriendlyName)) {
                    if (newDevice) {
                        d.setFriendlyName(keyValue[1]);
                    }
                } else if (keyValue[0].equals(SharedPreferencesManager.devicePassword)) {
                    d.setPassphrase(keyValue[1].toCharArray(), false); // todo:
                    // manage
                    // char[]
                } else if (keyValue[0].equals(SharedPreferencesManager.deviceOnboardedNet)) {
                    d.setOnboardedNetworkSSID(keyValue[1]);
                } else if (keyValue[0].equals(SharedPreferencesManager.deviceIconUrl)) {
                    if (keyValue[1] != null && !keyValue[1].isEmpty() && !keyValue[1].equals("null")) {
                        d.setStoredIconUrl(keyValue[1]);
                    }

                }
            }

            d.pinToNearby(true);
            if (newDevice) {
                d.setStatus(DeviceStatus.UNAVAILABLE);
                if (deviceId != null) {
                    m_deviceList.put(deviceId.toString(), d);
                }
            }
        }
    }

    public void onDeviceLost(String serviceName) {
        DeviceImpl device = (DeviceImpl) getDeviceByServiceName(serviceName);
        /*
         * device can be null, if the following happened: The busAttachment on the device reconnect to the daemon, as a result, the service name changed. The device sent Announced with the new service
         * name. The dashboard updated the device with the new name (it identify the device by it's unique id) When we got onDeviceLost, the serviceName no longer attacked to a device.
         */
        Log.d(TAG, "onDeviceLost busName = " + serviceName + (device != null ? ", friendly name = " + device.getFriendlyName() : " device not found"));
        if (device != null) {
            if (DeviceStatus.CONFIGURING.equals(device.getStatus())) {
                Log.d(TAG, "lost CONFIGURING device - nothing to do");
                return;
                // onboarding process. DO NOT DO ANYTHING.
                // until the onboarding process will end, we should keep the
                // device in our map to follow its state.
            }

            if (device.getStatus() == DeviceStatus.CONFIGURING) {
                Log.i(TAG, "lost Configuring device. don't do anything");
            }

            if (device.isPinned()) {
                Log.d(TAG, "lost a pinned device");
                device.setStatus(DeviceStatus.UNAVAILABLE);
            } else {
                Log.d(TAG, "lost an unpinned device. Deleting the device");
                removeDevice(device.getId());// mark as gone.
            }
            updateTheUi(IntentActions.AJ_ON_DEVICE_LOST, device.getId());

        }
    }

    public void onDeviceAlive(String busName) {
        DeviceImpl device = (DeviceImpl) getDeviceByServiceName(busName);

        Log.d(TAG, "onDeviceAlive busName = " + busName + (device != null ? ", friendly name = " + device.getFriendlyName() : " device not found"));
        if (device != null) {
            Log.d(TAG, "onDeviceAlive device is alive busName = " + busName + ", friendly name = " + device.getFriendlyName());
            if (device.getStatus() == DeviceStatus.GONE || device.getStatus() == DeviceStatus.UNAVAILABLE) {
                device.setStatus(DeviceStatus.AVAILABLE);
                updateTheUi(IntentActions.AJ_ON_DEVICE_ALIVE, device.getId());
            } else {
                Log.i(TAG, "onDeviceAlive Device is not gone nor unavailable, don't change status");
            }
        } else {
            Log.w(TAG, "onDeviceAlive found no device for busName: " + busName);
        }
    }

    private boolean isServiceSupported(ServiceType service, AboutObjectDescription[] objectDescriptions) {
        if (objectDescriptions != null) {
            for (int i = 0; i < objectDescriptions.length; i++) {
                String[] interfaces = objectDescriptions[i].interfaces;
                if (interfaces.length == 0) {
                    return false;
                }

                for (int j = 0; j < interfaces.length; j++) {
                    String currentInterface = interfaces[j];
                    if (currentInterface.startsWith(service.getInterface())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public synchronized void onAnnouncement(String serviceName, short port, AboutObjectDescription[] objectDescriptions, Map<String, Variant> serviceMetadata) {

        if (!m_processAnnouncements) {
            return;
        }
        
        Map<String, Object> newMap = null;
        try {
            newMap = TransportUtil.fromVariantMap(serviceMetadata);
        } catch (BusException e) {
            e.printStackTrace();
        }
        if (newMap == null) {
            Log.e(TAG, "onAnnouncement: serviceMetadata map = null !! ignoring.");
            return;
        }

        Log.i(TAG,
                "================================================================================================================================\n" + "onAnnouncement" + "\nServiceName = ["
                        + serviceName + "]" + (newMap.get(AboutKeys.ABOUT_APP_ID) != null ? "\nAppID = [" + ((UUID) newMap.get(AboutKeys.ABOUT_APP_ID)).toString() + "]" : "")
                        + ((String) newMap.get(AboutKeys.ABOUT_DEVICE_ID) != null ? "\nDeviceID = [" + newMap.get(AboutKeys.ABOUT_DEVICE_ID) + "]" : "") + "\nport = " + port
                        + "\nobjectDescriptions = " + Util.getBusObjectDescriptionArrayToString(objectDescriptions) + "\nserviceMetadata =" + Util.mapToString(newMap)
                        + "\n====================================================================================================================================");

        UUID uniqueId = (UUID) newMap.get(AboutKeys.ABOUT_APP_ID);
        if (uniqueId == null) {
            Log.e(TAG, "onAnnouncement: received null device uuid!! ignoring.");
            return;
        }

        String friendlyName = String.valueOf(newMap.get(AboutKeys.ABOUT_DEVICE_NAME));
        String defaultLanguage = String.valueOf(newMap.get(AboutKeys.ABOUT_DEFAULT_LANGUAGE));
        String helpPage = (String) newMap.get("SupportUrl");

        WiFiNetwork currentNetwork = null;
        try {
            if(OnboardingManager.getInstance() != null) {
                currentNetwork = OnboardingManager.getInstance().getCurrentNetwork();
            }
        } catch (WifiDisabledException e) {
            Log.e(TAG, "onAnnouncement: WifiDisabledException: shouldn't happend, update ui", e);
            updateTheUi(IntentActions.AJ_ON_WIFI_DISABLED);
            return;
        }

        Log.i(TAG, "onAnnouncement: Received announcment from device uuid [" + uniqueId + "] Name = [" + friendlyName + "] on Wi-Fi [" + (currentNetwork != null ? currentNetwork.getSSID() : "null") + "]");

        DeviceImpl device = null;
        if (currentNetwork != null && currentNetwork.getSSID() != null && (currentNetwork.getSSID().startsWith(WifiAJLookupPrefix) || currentNetwork.getSSID().endsWith(WifiAJLookupSuffix))
                && isServiceSupported(ServiceType.ONBOARDING, objectDescriptions)) {

            Log.i(TAG, "onAnnouncement: connected to AP,probably in the middle of onboarding process.");
            device = (DeviceImpl) getDeviceBySSID(currentNetwork.getSSID(), false);
            if (device != null) {
                Log.i(TAG, "onAnnouncement: connected to AP, found device and update uuid.");
                device.setId(uniqueId);
                device.setServiceName(serviceName);
                // Add the device to the map by it's uuid instead of it's ssid.
                m_deviceList.remove(currentNetwork.getSSID());
                m_ImageCacheManager.removeDeviceFromUUUIDList(uniqueId);
                m_deviceList.put(uniqueId.toString(), device);
            } else {
                Log.i(TAG, "onAnnouncement: connected to AP, CAN'T find device !!! Return.");
            }
            return;
        } else {
            Log.i(TAG, "onAnnouncement: Not in OnboardingMode");
        }

        // find device by UUID from announcement
        device = (DeviceImpl) getDevice(uniqueId);

        if (device == null) {
            Log.i(TAG, "onAnnouncement: can't find device by uuid -- " + uniqueId + "create new device");
            device = new DeviceImpl(serviceName, uniqueId);
            device.setPort(port);
            m_deviceList.put(uniqueId.toString(), device);
        }

        device.setServiceName(serviceName);
        device.setAbout(newMap);
        device.setFriendlyName(friendlyName);
        device.setInterfaces(objectDescriptions);
        device.setDirty();
        device.setPort(port);
        device.setDefaultLanguage(defaultLanguage);
        device.setHelpUrl(helpPage);

        boolean wasDeviceGone = false;
        if (device.getStatus() == DeviceStatus.GONE) {
            wasDeviceGone = true;
        }

        device.setStatus(DeviceStatus.AVAILABLE);
        device.updateDeviceInPreference();// will update only if added.
        updateTheUi(IntentActions.AJ_ON_DEVICE_FOUND, "", device.getId());
        if (wasDeviceGone) {
            updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED);
        }

    }

    // ------------------------ WifiManagerListener --------------------------
    // Device not on boarded. so don't change the onboardedNetworkSSID
    private synchronized void refreshScanResult(Intent intent) {

        Log.d(TAG, "refreshScanResult");
        List<WiFiNetwork> results = null;
        if (intent != null && intent.hasExtra(OnboardingManager.EXTRA_ONBOARDEES_AP)) {
            results = intent.getParcelableArrayListExtra(OnboardingManager.EXTRA_ONBOARDEES_AP);
        } else {
            results = OnboardingManager.getInstance().getWifiScanResults(WifiFilter.ONBOARDABLE);
        }

        if (results == null) {
            Log.d(TAG, "Scan Result: No Results");
            return;
        }

        Collection<Device> devices = getAllDevices();
        StringBuffer buffer = new StringBuffer();
        for (WiFiNetwork wifiNetwork : results) {
            buffer.append(String.format("Scan Result SSID = [%s] , authType= [%s] , level = [%s] ", wifiNetwork.getSSID(), wifiNetwork.getAuthType(), wifiNetwork.getLevel()));
            String ssidFromScanInfo = wifiNetwork.getSSID();
            DeviceImpl currentDevice = (DeviceImpl) getDeviceBySSID(ssidFromScanInfo, devices, false);
            if (currentDevice == null) {
                currentDevice = new DeviceImpl(wifiNetwork);
                m_deviceList.put(ssidFromScanInfo, currentDevice);
            } else {
                currentDevice.setAPWifiInfo(wifiNetwork);
                buffer.append(String.format(", state = [" + currentDevice.getStatus().toString() + "] , friendlyName = [" + currentDevice.getFriendlyName() + "] \t "));
                switch (currentDevice.getStatus()) {
                case CONFIGURING:
                case UNCONFIGURED:
                case AVAILABLE:
                    break;
                case UNAVAILABLE:
                case GONE:
                    currentDevice.setStatus(DeviceStatus.UNCONFIGURED);
                    break;
                }

            }
            Log.d(TAG, buffer.toString());
            buffer.setLength(0);
        }

        removeOldAccessPoints(results);
        updateTheUi(IntentActions.AJ_ON_REFRESH_AP_LIST);
    }

    private void removeOldAccessPoints(List<WiFiNetwork> results) {
        Log.d(TAG, "removeOldAccessPoints");
        Device currentDevice;
        String current_ssid;
        Vector<String> removalKeys = new Vector<String>();
        Set<Entry<String, Device>> entrySet = m_deviceList.entrySet();
        Set<String> ScanSSIDList = new HashSet<String>();

        for (WiFiNetwork scan : results) {
            String ssid = WifiUtil.getInstance().getNormalizedSSID(scan.getSSID());
            if (ssid != null && !ssid.isEmpty()) {
                ScanSSIDList.add(ssid);
            }
        }

        for (Entry<String, Device> entry : entrySet) {
            currentDevice = entry.getValue();
            if (currentDevice.getAPWifiInfo() == null) {
                continue;
            }
            current_ssid = WifiUtil.getInstance().getNormalizedSSID(currentDevice.getAPWifiInfo().getSSID());
            // Log.i(TAG, "removeOldAccessPoints current_ssid = " +
            // current_ssid);
            if (currentDevice.getStatus() == DeviceStatus.UNCONFIGURED && current_ssid != null && current_ssid.length() > 0) {

                String ssid = WifiUtil.getInstance().getNormalizedSSID(current_ssid);
                if (ssid == null || ssid.isEmpty()) {
                    continue;
                }

                if (!ScanSSIDList.contains(ssid)) {
                    Log.i(TAG, "removeOldAccessPoints Add " + current_ssid + " to removal list.");
                    removalKeys.add(entry.getKey());
                    // if we're going to remove the device from device list we
                    // must also clear it from image cache.
                    if (currentDevice.getId() != null) {
                        if (m_ImageCacheManager != null) {
                            m_ImageCacheManager.removeDeviceFromUUUIDList(currentDevice.getId());
                        }
                    }
                }
            }
        }
        // remove the entries
        for (String key : removalKeys) {
            if (key != null && key.length() > 0) {
                try{
                 m_ImageCacheManager.removeDeviceFromUUUIDList(UUID.fromString(key));
                }catch (IllegalArgumentException e){
                    Log.e(TAG,"Unable to parse UUID from "+key,e);
                }
                Device removed = m_deviceList.remove(key);
                if (removed == null) {
                    Log.i(TAG, "removeOldAccessPoints Wasn't Removed !!!!!!!!" + key);
                } else {
                    Log.i(TAG, "removeOldAccessPoints Remove " + key);
                }
            }
        }
    }

    @Override
    public void scan() {
        try {
            OnboardingManager.getInstance().scanWiFi();
        } catch (WifiDisabledException e) {
            updateTheUi(IntentActions.AJ_ON_WIFI_DISABLED);
            e.printStackTrace();
        }
    }

    // ------------------------ WifiManagerListener - END -------------------
    // ******************** Update the ui
    protected void updateTheUi(String refreshType) {
        updateTheUi(refreshType, null, null);
    }

    protected void updateTheUi(String refreshType, UUID m_uniqueId) {
        updateTheUi(refreshType, null, m_uniqueId);
    }

    protected void updateTheUi(String refreshType, String msg, UUID m_uniqueId) {
        Intent intent = new Intent(refreshType);
        if (msg != null && msg.length() > 0) {
            intent.putExtra(IntentExtraKeys.MSG, msg);
        }
        if (m_uniqueId != null) {
            intent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
        }

        String deviceName = "";
        Device d = getDevice(m_uniqueId);
        if (d != null) {
            deviceName = d.getFriendlyName();
        }
        Log.i(TAG, "Send intent: Action = " + refreshType + ((m_uniqueId != null) ? " UUID = " + m_uniqueId : "") + (!deviceName.isEmpty() ? " device name = " + deviceName : "")
                + ((msg != null && !msg.isEmpty()) ? " msg = " + msg : ""));

        m_context.sendBroadcast(intent);
    }

    protected void updateTheUi(String refreshType, Bundle extras) {
        Intent intent = new Intent(refreshType);
        if (extras != null && !extras.isEmpty()) {
            intent.putExtras(extras);
        }

        Log.i(TAG, "Send intent: Action = " + refreshType + (extras != null ? Util.bundleToString(extras) : ""));

        m_context.sendBroadcast(intent);
    }


    private enum ImageCacheObjectState {
        GET_ICON_URL, GET_CONTNET_ICON
    };

    @Override
    public Bitmap getDeviceImage(UUID deviceID) {
        Log.d(TAG, "getDeviceImage  [" + deviceID + "]");
        Device device = getDevice(deviceID);
        if (device != null) {
            if (device.getStoredIconUrl() == null) {
                Log.w(TAG, "getDeviceImage  [" + deviceID + "] getStoredUrl is null try to add to Queue");
                m_ImageCacheManager.addNewDeviceToQueue(deviceID);
            } else {
                Log.d(TAG, "getDeviceImage  [" + deviceID + "] getStoredUrl " + device.getStoredIconUrl());
                if (device.getStoredIconUrl().equals(ABOUT_ICON_DEFAULT_URL)) {
                    Log.d(TAG, "getDeviceImage  [" + deviceID + "] getStoredUrl value is default image");
                    return defaultImage;
                } else {
                    String md5String = Util.calcMD5FromString(device.getStoredIconUrl());
                    String md5name = "i_" + (md5String != null ? md5String.toLowerCase(Locale.US) : "");

                    int imageID = m_context.getResources().getIdentifier(md5name, "drawable", m_context.getPackageName());
                    if (imageID > 0) {
                        Bitmap b = BitmapFactory.decodeResource(m_context.getResources(), imageID);
                        if (b != null) {
                            Log.d(TAG, "getDeviceImage  [" + deviceID + "] success in loading ImageResource " + imageID);
                            return b;
                        }
                        Log.e(TAG, "getDeviceImage  [" + deviceID + "] failed in loading ImageResource " + imageID);
                    }

                    String resStringIdentifier = org.alljoyn.dashboard.logic.Util.getDensityName(m_context);
                    String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Alljoyn/" + resStringIdentifier + "/" + md5name + ".png";
                    File imgFile = new File(imageInSD);
                    if (imgFile.exists()) {
                        Bitmap b = BitmapFactory.decodeFile(imageInSD);
                        if (b != null) {
                            Log.d(TAG, "getDeviceImage  [" + deviceID + "] success in loading image from  " + imageInSD);
                            return b;
                        } else {
                            Log.e(TAG, "getDeviceImage  [" + deviceID + "] failed in loading image from  " + imageInSD + " file is corrupted");
                            m_ImageCacheManager.refreshDeviceInQueue(deviceID);
                        }
                    } else {
                        if (m_ImageCacheManager.isInQueue(deviceID)) {
                            Log.e(TAG, "getDeviceImage  [" + deviceID + "] failed in loading image from  " + imageInSD + " file does NOT exist");
                            m_ImageCacheManager.refreshDeviceInQueue(deviceID);
                        }
                    }
                }// not ABOUT_ICON_DEFAULT_URL
            }// URL exist ;
        }// device !=null
        Log.e(TAG, "getDeviceImage  [" + deviceID + "] using default Image");
        return defaultImage;
    }

    private class ImageCacheManger {

        private class ImageCacheObject {

            public ImageCacheObject(UUID uuid) {
                m_UUID = uuid;
                m_LastTimeTried = 0;
                m_Count = 0;
                m_ImageCacheObjectState = ImageCacheObjectState.GET_ICON_URL;

            }

            public UUID m_UUID;
            public long m_LastTimeTried;
            public int m_Count;
            public int m_Redownload;
            public ImageCacheObjectState m_ImageCacheObjectState;

            @Override
            public String toString(){
                String s="DeviceId=["+ m_UUID+"] Sate=["+m_ImageCacheObjectState.toString()+"] + m_Count =["+m_Count+"]";
                return  s;
            }
        }

        private final Map<UUID, ImageCacheObject> m_UUIDtoImageCacheObject = new HashMap<UUID, ImageCacheObject>();

        private final BlockingQueue<ImageCacheObject> m_imageDownloadQueue = new ArrayBlockingQueue<ImageCacheObject>(100);
        private final Map<String, Set<UUID>> m_URLtoUUIDSet = new HashMap<String, Set<UUID>>();

        public void removeDeviceFromUUUIDList(UUID uuid) {
            Log.w(TAG,  "removeDeviceFromUUUIDList ["+uuid+"]" );
            m_UUIDtoImageCacheObject.remove(uuid);
        }

        public void addNewDeviceToQueue(UUID uuid) {

            if (!isInQueue(uuid)) {
                ImageCacheObject cacheObject = new ImageCacheObject(uuid);
                m_UUIDtoImageCacheObject.put(uuid, cacheObject);
                m_imageDownloadQueue.add(cacheObject);

            }else{
                Log.w(TAG,  "addNewDeviceToQueue ["+uuid+"] already in Queue" );
            }
        }

        public boolean isInQueue(UUID m_UUID) {
            return (m_UUIDtoImageCacheObject.get(m_UUID) != null);
        }

        public void refreshDeviceInQueue(UUID uuid) {
            ImageCacheObject cacheObject = m_UUIDtoImageCacheObject.get(uuid);
            if (cacheObject != null && ++cacheObject.m_Redownload <= 2) {
                if (!m_imageDownloadQueue.contains(cacheObject)) {
                    m_imageDownloadQueue.add(cacheObject);
                    Log.d(TAG, "refreshDeviceInQueue  [" + uuid + "] redownload number "+cacheObject.m_Redownload+" from 2");
                }
            }
        }

        private boolean checkIfUrlResourceAvilable(String url) {
            String md5String = Util.calcMD5FromString(url);
            String md5name = "i_" + (md5String != null ? md5String.toLowerCase(Locale.US) : "");
            int imageID = m_context.getResources().getIdentifier(md5name, "drawable", m_context.getPackageName());
            if (imageID > 0) {
                return true;
            } else {
                String resStringIdentifier = org.alljoyn.dashboard.logic.Util.getDensityName(m_context);
                String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Alljoyn/" + resStringIdentifier + "/" + md5name + ".png";
                File imgFile = new File(imageInSD);
                // check if exist in SD card
                if (imgFile.exists()) {
                    return true;
                }
            }

            return false;

        }

        public void init() {
            new Thread("ImageCacheMangerThread") {

                @Override
                public void run() {
                    while (m_isRunning) {
                        try {
                            final ImageCacheObject cacheObject = m_imageDownloadQueue.take();

                            Log.d(TAG, "ImageCacheMangerThread handling " + cacheObject.toString());

                            // in case the wifi is disabled or we are not
                            // connected to the correct ap make our thread sleep
                            // for 10 sec maybe it will be better.
                            WiFiNetwork currentNetwork = null;
                            String normalizedSSID = "";
                            try {
                                currentNetwork = OnboardingManager.getInstance().getCurrentNetwork();
                            } catch (WifiDisabledException e) {
                                Log.d(TAG, "ImageCacheMangerThread Wifi is disabled sleep for 10 sec");
                                Thread.sleep(10 * 1000);
                            }


                            if (currentNetwork != null) {
                                normalizedSSID = WifiUtil.getInstance().getNormalizedSSID(currentNetwork.getSSID());
                            }
                            if (!WifiUtil.getInstance().isWifiEnable() || normalizedSSID.startsWith(WifiAJLookupPrefix) || normalizedSSID.endsWith(WifiAJLookupSuffix)) {
                                Log.i(TAG, "ImageCacheMangerThread  Not connected to WIFI UUID " + cacheObject.m_UUID.toString());
                                Thread.sleep(10 * 1000);
                                m_imageDownloadQueue.put(cacheObject);

                            } else {

                                final Device device = getDevice(cacheObject.m_UUID);
                                if (device == null) {
                                    Log.e(TAG, "ImageCacheMangerThread Device" + cacheObject.m_UUID + " isn't in device list remove from removeDeviceFromUUUIDList.");
                                    removeDeviceFromUUUIDList(cacheObject.m_UUID);
                                    continue;
                                }
                                /***
                                 *  handle ImageCacheObjectState.GET_ICON_URL
                                 */
                                if (cacheObject.m_ImageCacheObjectState == ImageCacheObjectState.GET_ICON_URL) {
                                    Log.d(TAG, "ImageCacheMangerThread GET_ICON_URL  " + device.getId());
                                    DeviceResponse response = device.getIconUrl();

                                    // Handle special case where device doesn't support about icon
                                    // don't push it again to the queue ,remove from deviceFromUUUIDList after 5 minutes so it can be read again maybe the some other device
                                    // go the UUID !!
                                    if (response.getStatus() == ResponseCode.Status_ERROR_NOT_SUPPORTED) {
                                        Log.e(TAG, "ImageCacheMangerThread GET_ICON_URL " + device.getId() + " doesn't support " + Device.ServiceType.ABOUT_ICON+ " interface, don't push to queue");
                                        new Timer().schedule(new TimerTask() {
                                            @Override
                                            public void run() {
                                                removeDeviceFromUUUIDList(cacheObject.m_UUID);
                                            }
                                        }, 5*60 *1000);
                                        continue;
                                    }

                                    if (response.getStatus() == ResponseCode.Status_OK) {
                                        Log.d(TAG, "ImageCacheMangerThread GET_ICON_URL  " + device.getId() + " was successful");
                                        if (response.getMsg() != null && !response.getMsg().isEmpty()) {
                                            device.setStoredIconUrl(response.getMsg());
                                            Log.d(TAG, "ImageCacheMangerThread GET_ICON_URL  " + device.getId() + " url " + response.getMsg());
                                            if (!checkIfUrlResourceAvilable(response.getMsg())) {
                                                Log.d(TAG, "ImageCacheMangerThread icon not in device ask serivce to bring " + response.getMsg());
                                                Bundle extras = new Bundle();
                                                extras.putString(AOService.BUNDLE_IMAGE_URL, response.getMsg());
                                                extras.putString(AOService.BUNDLE_IMAGE_DIMENSION, org.alljoyn.dashboard.logic.Util.getDensityName(DeviceManagerImpl.m_context));
                                                Intent downloadImageIntent = new Intent(DeviceManagerImpl.m_context, AOService.class);
                                                downloadImageIntent.setAction(AOService.GET_IMAGE_ACTION);
                                                downloadImageIntent.putExtras(extras);
                                                DeviceManagerImpl.m_context.startService(downloadImageIntent);
                                                Set<UUID> set = m_URLtoUUIDSet.get(response.getMsg());
                                                if (set == null) {
                                                    m_URLtoUUIDSet.put(response.getMsg(), new HashSet<UUID>(Arrays.asList(device.getId())));
                                                } else {
                                                    set.add(device.getId());
                                                    m_URLtoUUIDSet.put(response.getMsg(), set);
                                                }
                                            } else {
                                                Log.d(TAG, "ImageCacheMangerThread  resource is available for " + device.getId() + " send AJ_ON_DEVICE_ICON_AVAILABLE");
                                                updateTheUi(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE, device.getId());
                                            }
                                        } else {
                                            Log.d(TAG, "ImageCacheMangerThread GET_ICON_URL  " + device.getId() + " is 'EMPTY' ask to fetch GET_CONTNET_ICON");
                                            ImageCacheObject local = new ImageCacheObject(cacheObject.m_UUID);
                                            local.m_ImageCacheObjectState=ImageCacheObjectState.GET_CONTNET_ICON;
                                            m_imageDownloadQueue.add(local);
                                        }
                                    } else {
                                        cacheObject.m_LastTimeTried = System.currentTimeMillis();
                                        cacheObject.m_Count = cacheObject.m_Count + 1;
                                        if (cacheObject.m_Count < 100) {
                                            Log.e(TAG, "ImageCacheMangerThread GET_ICON_URL " + device.getId() + " failed  try later");
                                            new Timer().schedule(new TimerTask() {
                                                @Override
                                                public void run() {
                                                    m_imageDownloadQueue.add(cacheObject);
                                                }
                                            }, 5 * 1000);
                                        } else {
                                            Log.e(TAG, "ImageCacheMangerThread GET_ICON_URL " + device.getId() + " failed  more then 100 time stop");
                                            removeDeviceFromUUUIDList(cacheObject.m_UUID);
                                        }

                                    }
                                }
                                /***
                                 *  handle ImageCacheObjectState.GET_CONTNET_ICON
                                 */
                                else if (cacheObject.m_ImageCacheObjectState == ImageCacheObjectState.GET_CONTNET_ICON) {
                                    Log.d(TAG, "ImageCacheMangerThread GET_CONTNET_ICON " + device.getId());
                                    DeviceResponse response = device.getDeviceIconContent();
                                    if (response.getStatus() == ResponseCode.Status_OK) {
                                        Log.d(TAG, "ImageCacheMangerThread GET_CONTNET_ICON " + device.getId() + " successful  url generated" + response.getMsg());
                                        device.setStoredIconUrl(response.getMsg());
                                        updateTheUi(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE, device.getId());
                                    } else {
                                        cacheObject.m_LastTimeTried = System.currentTimeMillis();
                                        cacheObject.m_Count = cacheObject.m_Count + 1;
                                        if (cacheObject.m_Count < 50) {
                                            Log.e(TAG, "ImageCacheMangerThread GET_CONTNET_ICON " + device.getId() + "failed  try later");
                                            new Timer().schedule(new TimerTask() {
                                                @Override
                                                public void run() {
                                                    m_imageDownloadQueue.add(cacheObject);
                                                }
                                            }, 30 * 1000);

                                        } else {
                                            Log.e(TAG, "ImageCacheMangerThread GET_CONTNET_ICON " + device.getId() + "failed  more then 50 time stop");
                                            removeDeviceFromUUUIDList(cacheObject.m_UUID);
                                        }
                                    }
                                }
                            }
                        }
                        catch (InterruptedException e) {
                            Log.e(TAG, "ImageCacheMangerThread  error", e);
                        }
                    }
                }
            }.start();
        }

        public ImageCacheManger() {
            Log.i(TAG, "ImageCacheManger Constructor");
            m_context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action != null) {
                        if (action.equals(AOService.IMAGE_RETRIEVED_ACTION)) {
                            Log.i(TAG, "ImageCacheManger BroadcastReceiver  got IMAGE_RETRIEVED_ACTION ");
                            Bundle extra = intent.getExtras();
                            if (extra != null) {
                                String url = extra.getString(AOService.BUNDLE_IMAGE_URL);
                                Log.i(TAG, "ImageCacheManger BroadcastReceiver  got IMAGE_RETRIEVED_ACTION " + url);
                                Set<UUID> set = m_URLtoUUIDSet.get(url);
                                if (set != null) {
                                    for (UUID uuid : set) {
                                        Log.i(TAG, "ImageCacheManger BroadcastReceiver Traversing via " + url + " UUID " + uuid.toString());
                                        updateTheUi(IntentActions.AJ_ON_DEVICE_ICON_AVAILABLE, uuid);
                                    }
                                }
                            }
                        }
                    }
                }
            }, new IntentFilter(AOService.IMAGE_RETRIEVED_ACTION), null, AlljoynOnApplication.getHanlder());

        }

    }// ImageCacheManger

    @Override
    public boolean startPinging() {
        return startPinging(DEFAULT_HEARTBEAT);
    }

    @Override
    public boolean startPinging(int heartbeatSeconds) {
        boolean wasRunning = stopPinging();
        if (heartbeatSeconds > 0) {
            if (m_pingTimer == null) {
                Log.i(TAG, "startPinging with heartbeat = " + heartbeatSeconds + " seconds");
                m_pingTimer = new Timer();
                m_pingTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        for (Device device : getAllDevices()) {
                            DeviceStatus deviceStatus = device.getStatus();
                            if (deviceStatus == DeviceStatus.AVAILABLE || deviceStatus == DeviceStatus.UNAVAILABLE || deviceStatus == DeviceStatus.GONE) {
                                device.pingAsync(device.getOnPingListener());
                            }
                        }
                    }
                }, 0, heartbeatSeconds * 1000);
            } else {
                Log.i(TAG, "startPinging called when pinging has already started");
            }
        } else {
            Log.i(TAG, "startPinging called with invalid heartbeat = " + heartbeatSeconds);
        }
        return wasRunning;
    }

    @Override
    public boolean stopPinging() {
        if (m_pingTimer != null) {
            Log.i(TAG, "stopPinging");
            m_pingTimer.cancel();
            m_pingTimer.purge();
            m_pingTimer = null;
            return true;
        }
        Log.i(TAG, "stopPinging called when pinging is already stopped");
        return false;
    }
}
