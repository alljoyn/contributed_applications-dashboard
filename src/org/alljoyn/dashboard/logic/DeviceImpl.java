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
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.about.client.AboutClient;
import org.alljoyn.about.icon.AboutIconClient;
import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.Mutable.StringValue;
import org.alljoyn.bus.OnPingListener;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.alljoyn.config.ConfigServiceImpl;
//import org.alljoyn.config.client.ConfigClient;
import org.alljoyn.ioe.controlpanelservice.ControlPanelCollection;
import org.alljoyn.ioe.controlpanelservice.ControlPanelException;
import org.alljoyn.ioe.controlpanelservice.ControlPanelService;
import org.alljoyn.ioe.controlpanelservice.ControllableDevice;
import org.alljoyn.ioe.controlpanelservice.DeviceEventsListener;
import org.alljoyn.ioe.controlpanelservice.Unit;
import org.alljoyn.ioe.controlpanelservice.ui.UIElement;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.DeviceManager;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.onboarding.client.OnboardingClient;
import org.alljoyn.onboarding.client.OnboardingClientImpl;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.transport.MyScanResult;
import org.alljoyn.onboarding.transport.ScanInfo;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.ClientBase;
import org.alljoyn.services.common.ServiceAvailabilityListener;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

// if the device has not been on boarded, this is the actual device (Coffee machine, toaster oven etc.)
// if the device has on boarded,this is the application that runs on the machine.
// (machine can have more than one application running on it)
public class DeviceImpl implements Device, ServiceAvailabilityListener {

    private static final int PING_TIMEOUT = 5000;
    private final String TAG = "DASHBOARD_DeviceImpl";
    UUID m_uniqueId;// from the about //-----
    // private ScanResult m_SelfAPInfo;
    WiFiNetwork m_apNetworkInfo;
    String m_friendlyName = "";// ----
    DeviceStatus m_status;// ??

    boolean m_favorite;// added to my devices, should be saved to persistence
    Map<String, Object> m_tag;// should contain all ui extras
    // (muteDeviceNotifications etc)
    char[] m_password; // for alljoyn secure interface //------
    // String m_ssidPassword;
    String m_onboardedNetworkSSID; // ----
    Map<String, Object> m_about;// info from the about interface
    Map<String, Object> m_config;

    AboutObjectDescription[] m_interfaces = null;// from the about {
    // }

    String m_serviceName; // will be changed every time the device bus connect
    // to the daemon
    // private boolean m_readyToOnboard;

    private String m_defaultLanguage = "en";
    private short m_port;

    // matt variables
    private boolean m_notificationOn = true;
    private String m_helpUrl = "";
    private boolean m_aboutDirty;
    private boolean m_configDirty;
    private AboutClient m_aboutClient;
    private ConfigClientHack m_configClient;
    private OnboardingClient m_onboardingClient;

    private AboutIconClient m_iconClient;

    private ControllableDevice m_controllableClient;
    //
    private Collection<ControlPanelCollection> m_panels;
    private ControlPanelCollection m_controlPopups;

    protected UIElement m_uielementChanged;
    protected UIElement m_elementType;
    private ArrayList<AboutObjectDescription> m_controlPanelObjectDescriptions;

    // private IntentFilter m_WifiIntentFilter = null;
    // private OnboardingParams m_onboardingParams;

    private String m_storeAboutIconUrl = null;

    @Override
    public String getStoredIconUrl() {
        return m_storeAboutIconUrl;
    }

    @Override
    public void setStoredIconUrl(String url) {
        m_storeAboutIconUrl = url;
    }

    private OnPingListener m_devicePingListener = null;

    // for announcement
    public DeviceImpl(String serviceName, UUID uniqueId) {
        Log.i(TAG, "New Device " + serviceName + "," + uniqueId);
        setServiceName(serviceName);
        m_favorite = false;
        m_aboutDirty = true;
        m_configDirty = true;
        m_status = DeviceStatus.AVAILABLE;
        m_password = DEFAULT_PINCODE;
        m_uniqueId = uniqueId;
        m_apNetworkInfo = null;
    }

    // For soft AP
    public DeviceImpl(WiFiNetwork scanInfo) {
        Log.i(TAG, "New Device " + scanInfo.getSSID());
        m_friendlyName = scanInfo.getSSID();
        m_apNetworkInfo = scanInfo;
        m_favorite = false;
        m_aboutDirty = true;
        m_configDirty = true;
        m_status = DeviceStatus.UNCONFIGURED;
        m_password = DEFAULT_PINCODE;
        m_uniqueId = null;
    }

    @Override
    public void setAPWifiInfo(WiFiNetwork apInfo) {
        m_apNetworkInfo = apInfo;
    }

    @Override
    public WiFiNetwork getAPWifiInfo() {
        return m_apNetworkInfo;
    }

    @Override
    public UUID getId() {
        return m_uniqueId;
    }

    protected void setId(UUID uniqueId) {
        m_uniqueId = uniqueId;
    }

    public short getPort() {
        return m_port;
    }

    protected void setPort(short port) {
        m_port = port;
    }

    @Override
    public DeviceResponse setPassphrase(char[] password, boolean shouldUpdateRemoteDevice) {
        if (shouldUpdateRemoteDevice) {
            setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.SET_PASSWORD);
        }

        Log.i(TAG, "setPassphrase " + toLogString() + ", new pass:" + password.toString() + ", shouldUpdateRemoteDevice = " + shouldUpdateRemoteDevice);

        if (password == null || password.length == 0) {
            Log.i(TAG, "setPassphrase for " + m_friendlyName + "new pass == null or empty.");
            return new DeviceResponse(ResponseCode.Status_ERROR, "new pass == null or empty");
        }
        if (shouldUpdateRemoteDevice) {
            if (m_configClient == null) {
                try {
                    m_configClient = new ConfigClientHack(m_serviceName, AjManager.getInstance().getBusAttachment(),this, m_port);
                } catch (Exception e) {
                    Log.e(TAG, "setPassphrase: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                    e.printStackTrace();
                    return new DeviceResponse(ResponseCode.Status_ERROR, "Set Password, cant create session", e);
                }
            }
            if (connectToDevice(m_configClient).getStatus() != ResponseCode.Status_OK) {
                // ERROR
                return new DeviceResponse(ResponseCode.Status_ERROR, "Set Password, cant create session");
            }
            try {
                m_configClient.setPasscode(AjManager.getInstance().getDeamonRealm(), password);
            } catch (BusException e) {
                Log.e(TAG, "setPassphrase: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
                ResponseCode errorCode = ResponseCode.Status_ERROR;
                if (e.getLocalizedMessage().contains("MaxSizeExceeded")) {
                    errorCode = ResponseCode.Status_ERROR_MAX_SIZE_EXCEEDED;
                }
                return new DeviceResponse(errorCode, "Set Password, cant set password", e);
            }
            StringValue key = new StringValue();
            BusAttachment bus = AjManager.getInstance().getBusAttachment();
            if (bus != null) {
                Status status = bus.getPeerGUID(m_serviceName, key);
                if (status.equals(Status.OK)) {
                    Log.i(TAG, "Bus attachment clear key for device " + m_friendlyName);
                    bus.clearKeys(key.value);
                }
            }
        }
        m_password = password;
        Bundle extras = new Bundle();
        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
        if (m_apNetworkInfo != null) {
            extras.putString(IntentExtraKeys.EXTRA_DEVICE_SSID, m_apNetworkInfo.getSSID());
        }
        updateDeviceInPreference();
        if (!shouldUpdateRemoteDevice) {
            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_DEVICE_PASSWORD_SAVED, extras);
        }
        return new DeviceResponse(ResponseCode.Status_OK);
    }

    @Override
    public char[] getPassphrase() {
        Log.i(TAG, "getPassphrase " + m_password.toString() + " device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
        return m_password;
    }

    @Override
    public void resetPassphrase() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.RESET_PASSWORD);
        Log.i(TAG, "resetPassphrase device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
        setPassphrase(DEFAULT_PINCODE, true);
    }

    @Override
    public DeviceResponse setConfig(Map<String, Object> config) {
        Log.i(TAG, "setConfig " + toLogString());
        Log.i(TAG, String.format("New configuration :%s", Util.mapToString(config)));

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.SET_CONFIG);
        if (isServiceSupported(Device.ServiceType.CONFIG)) {
            if (m_configClient == null) {
                try {
                    if (m_serviceName == null) {
                        Log.e(TAG, "setConfig deviceId=" + m_uniqueId + " service name is null");
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                    m_configClient = new ConfigClientHack(m_serviceName, AjManager.getInstance().getBusAttachment(), this, m_port);
                    if (m_configClient == null)// check if m_configClient is not
                        // null
                    {
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "setConfig deviceId=" + m_uniqueId ,e);
                    return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                }
            }

            try {
                if (connectToDevice(m_configClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "setConfig deviceId=" + m_uniqueId + " failed to establish session");
                    return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION);
                }
                m_configClient.setConfig(config, m_defaultLanguage);
                return new DeviceResponse(ResponseCode.Status_OK);

            } catch (BusException e) {

                Log.e(TAG, "setConfig deviceId=" + m_uniqueId ,e);
                return new DeviceResponse(ResponseCode.Status_ERROR);
            } catch (Exception e) {
                Log.e(TAG, "setConfig deviceId=" + m_uniqueId ,e);
                return new DeviceResponse(ResponseCode.Status_ERROR);
            }
        }
        return new DeviceResponse(ResponseCode.Status_ERROR);
    }

    @Override
    public String getFriendlyName() {
        return m_friendlyName;
    }

    @Override
    public DeviceStatus getStatus() {
        return m_status;
    }

    /*
     * @Override public boolean isInSoftAPMode() { return m_isInSoftAPMode; }
     *
     * public void setSoftAPMode(boolean isInSoftAPMode) { m_isInSoftAPMode =
     * isInSoftAPMode; }
     */
    @Override
    public Object getTag(String key) {
        if (m_tag == null) {
            return null;
        }
        return m_tag.get(key);
    }

    @Override
    public void setTag(String key, Object value) {
        if (m_tag == null) {
            m_tag = new HashMap<String, Object>();
        }
        m_tag.put(key, value);
    }

    @Override
    public Map<String, Object> getAllTags() {
        return m_tag;
    }

    @Override
    public boolean hasTag(String key) {
        if (m_tag == null) {
            return false;
        }
        return m_tag.containsKey(key);
    }

    @Override
    public void removeTag(String key) {
        if (m_tag != null) {
            m_tag.remove(key);
        }
    }

    @Override
    public String getOnboardedNetworkSSID() {
        return m_onboardedNetworkSSID;
    }

    @Override
    public void setOnboardedNetworkSSID(String onboardedNetworkSSID) {
        m_onboardedNetworkSSID = onboardedNetworkSSID;
    }

    @Override
    public Map<String, Object> getAbout(boolean force) {
        Log.d(TAG, "getAbout " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ABOUT);
        if (force || m_aboutDirty) // request new about.
        {
            if (m_aboutClient == null) {
                try {
                    if (m_serviceName == null) {
                        return null;
                    }
                    m_aboutClient = AboutServiceImpl.getInstance().createAboutClient(m_serviceName, this, m_port);
                } catch (Exception e) {
                    Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()), e);
                    return m_about;
                }
            }

            DeviceResponse status = connectToDevice(m_aboutClient);
            if (status.getStatus() != ResponseCode.Status_OK) {
                // ERROR
                Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                return m_about;
            }
            try {
                m_about = m_aboutClient.getAbout(m_defaultLanguage);
            } catch (BusException e) {
                Log.e(TAG, "getAbout: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
            }
            m_aboutDirty = false;
            disconnectFromDevice(m_aboutClient);
        }
        return m_about;
    }

    private DeviceResponse processPingStatus(Status status) {
        DeviceResponse deviceResponse;

        switch (status) {
        case OK:
            Log.e(TAG, "ping: got an OK response for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_OK);
            break;
        case TIMEOUT:
        case ALLJOYN_PING_REPLY_TIMEOUT:
            Log.e(TAG, "ping: got a TIMEOUT response for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_ERROR_TIMEOUT);
            break;
        case ALLJOYN_PING_REPLY_UNREACHABLE:
            Log.e(TAG, "ping: got an UNREACHABLE response for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_ERROR_PEER_NAME_NOT_FOUND, "busName unreachable");
            break;
        case ALLJOYN_PING_REPLY_UNKNOWN_NAME:
            Log.e(TAG, "ping: got an UNKOWN response for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_ERROR_PEER_NAME_NOT_FOUND, "busName not found");
            break;
        case ALLJOYN_PING_REPLY_INCOMPATIBLE_REMOTE_ROUTING_NODE:
            Log.e(TAG, "ping: got a INCOMPATIBLE REMOTE ROUTING NODE response for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_ERROR_NOT_SUPPORTED);
            break;
        case ALLJOYN_PING_FAILED:
        default:
            Log.e(TAG, "ping: Generic error invoking ping for device " + toLogString());
            deviceResponse = new DeviceResponse(ResponseCode.Status_ERROR);
            break;
        }

        return deviceResponse;
    }

    private void handlePingResponse(ResponseCode responseCode) {
        DeviceStatus deviceStatus = getStatus();
        switch (responseCode) {
        case Status_OK:
            if (deviceStatus != DeviceStatus.AVAILABLE) { // device became AVAILABLE
                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onDeviceAlive(m_serviceName);
            }
            break;
        case Status_ERROR_TIMEOUT:
        case Status_ERROR_PEER_NAME_NOT_FOUND:
            if (deviceStatus == DeviceStatus.AVAILABLE) { // device became NOT AVAILABLE
                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onDeviceLost(m_serviceName);
            }
            break;
        case Status_ERROR_NOT_SUPPORTED:
        case Status_ERROR:
        default:
            break;
        }
    }

    private boolean m_HandleAsynchPings = false;

    @Override
    public void setHandleAsynchPings(boolean handle) {
        m_HandleAsynchPings = handle;
    }

    @Override
    public OnPingListener getOnPingListener() {
        if (m_devicePingListener == null) {
            m_devicePingListener = new OnPingListener() {
                @Override
                public void onPing(Status status, Object context) {
                    if (((String) context).compareTo(m_serviceName) == 0) { // Check to see if response is still applicable to this device
                        if (m_HandleAsynchPings) {
                            handlePingResponse(processPingStatus(status).getStatus());
                            m_HandleAsynchPings = false;
                        }
                    }
                    super.onPing(status, context);
                }
            };
        }
        return m_devicePingListener;
    }

    @Override
    public DeviceResponse ping() {

        DeviceResponse deviceResponse;
        Log.d(TAG, "ping: pinging device " + toLogString());

        if (m_serviceName == null || m_serviceName.isEmpty()) {
            Log.e(TAG, "ping: busName is empty");
            return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME, "busName is empty");
        }

        // Call ping synchronously
        Status status = AjManager.getInstance().getBusAttachment().ping(m_serviceName, PING_TIMEOUT);
        Log.i(TAG, "ping: ping reply status for device " + toLogString() + ": " + status);

        deviceResponse = processPingStatus(status);
        ResponseCode responseCode = deviceResponse.getStatus();
        if (responseCode == ResponseCode.Status_ERROR_NOT_SUPPORTED) { // Treat devices that cannot be pinged as unavailable
            deviceResponse =  new DeviceResponse(ResponseCode.Status_ERROR_PEER_NAME_NOT_FOUND, "unable to ping busName. Assumed unavailable");
        }
        handlePingResponse(deviceResponse.getStatus());

        Log.i(TAG, "ping: returned device response for device " + toLogString() + ": " + deviceResponse.getStatus());

        return deviceResponse;
    }

    @Override
    public void pingAsync(OnPingListener onPingListener) {
        // Call ping asynchronously passing the device's onPingListener if none is provided and the service name as the context
        m_HandleAsynchPings = true;
        AjManager.getInstance().getBusAttachment().ping(m_serviceName, PING_TIMEOUT, (onPingListener == null ? getOnPingListener() : onPingListener), m_serviceName);
    }

    protected AboutObjectDescription[] getInterfaces() {
        return m_interfaces;
    }

    protected void setInterfaces(AboutObjectDescription[] objectDescriptions) {
        m_interfaces = objectDescriptions;

        if (m_interfaces == null) {
            return;
        }

        m_controlPanelObjectDescriptions = new ArrayList<AboutObjectDescription>();

        for (int i = 0; i < m_interfaces.length; i++) {
            AboutObjectDescription bos = m_interfaces[i];
            String path = bos.path;
            if (path.startsWith("/ControlPanel")) {
                m_controlPanelObjectDescriptions.add(bos);
            }
        }
    }

    @Override
    public boolean isServiceSupported(ServiceType service) {
        if (m_interfaces != null) {
            for (int i = 0; i < m_interfaces.length; i++) {
                String[] interfaces = m_interfaces[i].interfaces;
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

    public String getServiceName() {
        return m_serviceName;
    }

    protected void setServiceName(String serviceName) {
        // reset the service only if this is a new bus name
        boolean nameChanged = m_serviceName == null || !m_serviceName.equalsIgnoreCase(serviceName);
        Log.i(TAG, "setServiceName. device = " + toLogString() + " old: " + m_serviceName + ", new: " + serviceName + ", needs reset services? " + nameChanged);

        m_serviceName = serviceName;

        if (nameChanged) {
            resetServices();
        }
    }

    @Override
    public void pinToNearby(boolean favorite) {
        Log.i(TAG, "setFavorite: device " + toLogString() + " ,favorite = " + favorite);
        m_favorite = favorite;
        if (favorite) {
            saveDeviceToPreference();
        } else {
            removeDeviceFromPreference();
        }
    }

    @Override
    public String getHelpURL() {
        return m_helpUrl;
    }

    @Override
    public void turnOnNotifications() {
        m_notificationOn = true;
    }

    @Override
    public void turnOffNotifications() {
        m_notificationOn = false;
    }

    @Override
    public boolean isNotificationOn() {
        return m_notificationOn;
    }

    @Override
    public boolean isPinned() {
        return m_favorite;
    }

    @Override
    public void setStatus(DeviceStatus status) {
        if (status.equals(m_status)) {
            return;
        }

        Log.i(TAG, "setStatus: set device status for " + toLogString() + ", old status = " + m_status + ", new status = " + status);
        m_status = status;
        if (DeviceStatus.GONE.equals(status)) {
        //if (DeviceStatus.GONE.equals(status) || DeviceStatus.HIDDEN.equals(status)) {
            resetServices();

            m_panels = null;
            m_controlPopups = null;
            m_uielementChanged = null;
            m_elementType = null;
        }

        else if (DeviceStatus.UNCONFIGURED == m_status) {
            if (m_apNetworkInfo != null) {
                m_friendlyName = m_apNetworkInfo.getSSID();
            }
        }

        Bundle extras = new Bundle();
        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_DEVICE_STATUS_CHANGED, extras);
        // ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).UpdateTheUi(IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE,
        // "", m_uniqueId);
    }

    public void setDirty() // Should rarely used by the UI!!
    {
        m_aboutDirty = true;
        ;
        m_configDirty = true;
    }

    private DeviceResponse connectToDevice(ClientBase client) {
        Log.d(TAG, "connectToDevice " + toLogString());

        if (client == null) {
            return new DeviceResponse(ResponseCode.Status_ERROR, "fail connect to device, client == null");
        }
        if (client.isConnected()) {
            return new DeviceResponse(ResponseCode.Status_OK);
        }

        Status status = client.connect();
        Log.i(TAG, "connectToDevice: device " + m_friendlyName + "-ServiceName = " + m_serviceName + "-uniqueId = " + m_uniqueId + "-AJReturnStatus=" + status.name());

        switch (status) {
        case OK: {
            Log.d(TAG, "connectToDevice. Join Session OK");
            return new DeviceResponse(ResponseCode.Status_OK);
        }
        case ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED: {
            Log.d(TAG, "connectToDevice: Join Session returned ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED. Ignoring");
            return new DeviceResponse(ResponseCode.Status_OK);
        }
        case BUS_NOT_CONNECTED: {
            Log.e(TAG, "connectToDevice: Join Session returned BUS_NOT_CONNECTED. Device is unavailable. Pseudo ping");
            return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION, "device unreachable");
        }
        case ALLJOYN_JOINSESSION_REPLY_FAILED: {
            Log.e(TAG, "connectToDevice: Join Session returned ALLJOYN_JOINSESSION_REPLY_FAILED. Device is unavailable. Pseudo ping");
            return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION, "device unreachable");
        }
        case ALLJOYN_JOINSESSION_REPLY_UNREACHABLE: {
            Log.e(TAG, "connectToDevice: Join Session returned ALLJOYN_JOINSESSION_REPLY_UNREACHABLE. Device is unavailable. Pseudo ping");
            return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION, "device unreachable");
        }
        default: {
            Log.e(TAG, "connectToDevice: Join session returned error: " + status.name());
            return new DeviceResponse(ResponseCode.Status_ERROR, "Failed connecting to device");

        }
        }
    }

    private void disconnectFromDevice(ClientBase client) {
        Log.d(TAG, "disconnectFromDevice " + toLogString());

        if (client != null) {
            Log.d(TAG, "DeviceImpl.disconnectFromDevice client is not null");
            if (client.isConnected()) {
                Log.d(TAG, "DeviceImpl.disconnectFromDevice client is connected. Disconnecting");
                client.disconnect();
            } else {
                Log.d(TAG, "DeviceImpl.disconnectFromDevice client is NOT connected. name = " + m_friendlyName);
            }
        }
    }

    @Override
    public Map<String, Object> getConfig() {
        Log.d(TAG, "getConfig " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_CONFIG);
        if (m_configDirty)// request new about.
        {
            if (m_configClient == null) {
                try {
                    if (m_serviceName == null) {
                        return m_config;
                    }
                    m_configClient = new ConfigClientHack(m_serviceName, AjManager.getInstance().getBusAttachment(), this, m_port);
                } catch (Exception e) {
                    Log.e(TAG, "getConfig: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                    e.printStackTrace();
                    return m_config;
                }
            }
            DeviceResponse status = connectToDevice(m_configClient);
            if (status.getStatus() != ResponseCode.Status_OK) {
                // ERROR
                Log.e(TAG, "getConfig: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                return m_config;
            }
            try {
                m_config = m_configClient.getConfig(m_defaultLanguage);
            } catch (BusException e) {
                Log.e(TAG, "getConfig: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()), e);
            }
            m_configDirty = false;

        }
        return m_config;
    }

    // for recovery
    public void setFriendlyName(String name) {
        m_friendlyName = name;
    }

    protected void setAbout(Map<String, Object> serviceMetadata) {
        m_about = serviceMetadata;
    }

    @Override
    public DeviceResponse restartDevice() {

        Log.i(TAG, "restartDevice device: " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.RESTART_DEVICE);
        if (m_configClient == null) {
            try {
                if (m_serviceName == null) {
                    return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                }
                m_configClient = new ConfigClientHack(m_serviceName, AjManager.getInstance().getBusAttachment(), this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "restartDevice: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
                return new DeviceResponse(ResponseCode.Status_ERROR, "fail resetDevice, aboutConfigClient == null");
            }
        }
        DeviceResponse status = connectToDevice(m_configClient);
        if (status.getStatus() != ResponseCode.Status_OK) {
            // ERROR
            return status;
        }
        try {
            m_configClient.restart();
        } catch (BusException e) {
            Log.e(TAG, "restartDevice: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
            return new DeviceResponse(ResponseCode.Status_ERROR, "fail reset device", e);
        }
        return new DeviceResponse(ResponseCode.Status_OK);

    }

    private void saveDeviceToPreference() {
        Log.i(TAG, "SaveDeviceToPreference " + m_uniqueId);
        SharedPreferencesManager.saveDevice(m_uniqueId, getThinString());
    }

    private void removeDeviceFromPreference() {
        Log.i(TAG, "removeDeviceFromPreference " + m_uniqueId);
        SharedPreferencesManager.removeDevice(m_uniqueId);
    }

    protected void updateDeviceInPreference() {
        Log.i(TAG, "updateDeviceInPreference " + m_uniqueId);
        if (m_favorite) {
            SharedPreferencesManager.updateDevice(m_uniqueId, getThinString());
        }
    }

    private String getThinString() {
        StringBuilder thinDevice = new StringBuilder();
        if (m_apNetworkInfo != null) {
            thinDevice.append(SharedPreferencesManager.deviceSsid + SharedPreferencesManager.keyValueSeparator + m_apNetworkInfo.getSSID());
        }
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceId + SharedPreferencesManager.keyValueSeparator + m_uniqueId);
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceFriendlyName + SharedPreferencesManager.keyValueSeparator + m_friendlyName);
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.devicePassword + SharedPreferencesManager.keyValueSeparator + String.valueOf(m_password));
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceOnboardedNet + SharedPreferencesManager.keyValueSeparator + m_onboardedNetworkSSID);
        thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceIconUrl + SharedPreferencesManager.keyValueSeparator + m_storeAboutIconUrl);
        //thinDevice.append(SharedPreferencesManager.pairsSeparator + SharedPreferencesManager.deviceStatus + SharedPreferencesManager.keyValueSeparator + this.m_status.ordinal());
        return thinDevice.toString();
    }

    /***************** ServiceAvailabilityListener ************/
    @Override
    public void connectionLost() {
        Log.i(TAG, "connectionLost with device " + m_friendlyName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_SESSION_LOST_WITH_DEVICE, m_uniqueId);
    }

    /***************** ServiceAvailabilityListener ************/

    @Override
    public DeviceResponse factoryReset() {

        Log.i(TAG, "factoryReset " + toLogString());

        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.FACTORY_RESET);
        if (m_configClient == null) {
            try {
                if (m_serviceName == null) {
                    return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                }
                m_configClient = new ConfigClientHack(m_serviceName, AjManager.getInstance().getBusAttachment(), this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "factoryReset: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()), e);
                return new DeviceResponse(ResponseCode.Status_ERROR, "factoryReset: failed to createFeatureConfigClient");
            }
        }

        if (connectToDevice(m_configClient).getStatus() != ResponseCode.Status_OK) {
            Log.e(TAG, "factoryReset: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
            return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION, "factoryReset: failed to join session");
        }
        try {
            m_configClient.factoryReset();
            return new DeviceResponse(ResponseCode.Status_OK);
        } catch (Exception e) {
            Log.e(TAG, "factoryReset: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()), e);
            return new DeviceResponse(ResponseCode.Status_ERROR, "factoryReset: failed to factory reset device");
        }

    }

    @Override
    public MyScanResult[] getVisibleNetworks() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_VISIBLE_NETWORKS);
        Log.i(TAG, "getVisibleNetworks for " + m_friendlyName);

        if (m_onboardingClient == null) {
            if (m_serviceName == null) {
                return null;
            }
            m_onboardingClient = new OnboardingClientImpl(m_serviceName, AjManager.getInstance().getBusAttachment(), this, m_port);
        }

        if (m_onboardingClient != null) {
            if (!m_onboardingClient.isConnected()) {
                if (connectToDevice(m_onboardingClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "getVisibleNetworks: could not connect to device " + m_friendlyName);
                    return null;
                }
            }
            ScanInfo visibleNetworks = null;
            try {
                visibleNetworks = m_onboardingClient.getScanInfo();
            } catch (BusException e) {
                Log.e(TAG, "getVisibleNetworks: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
            }
            // disconnect m_onboardingClient.
            disconnectFromDevice(m_onboardingClient);

            if (visibleNetworks != null) {
                Log.i(TAG, "getVisibleNetworks: visibleNetworks != null for device " + m_friendlyName);
                // FOR LOG
                MyScanResult[] networks = visibleNetworks.getScanResults();
                if (networks != null) {
                    StringBuilder s = new StringBuilder();
                    for (int i = 0; i < networks.length; i++) {
                        s.append(networks[i].m_ssid + ",");
                    }
                    Log.i(TAG, "getVisibleNetworks: visibleNetworks for device " + m_friendlyName + ": " + s.toString());
                }
                return visibleNetworks.getScanResults();
            } else {
                Log.i(TAG, "getVisibleNetworks: No visibleNetworks for device " + m_friendlyName);
            }
        }
        return null;
    }

    /***************** Onboarding - END ************/

    @Override
    public int getIconSize() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_SIZE);

        int size = -1;

        if (m_iconClient == null) {
            try {
                if (m_serviceName == null) {
                    return size;
                }
                m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "getIconSize: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
                return size;
            }
        }
        if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
            return size;
        }
        try {
            size = m_iconClient.getSize();
        } catch (BusException e) {
            Log.e(TAG, "getIconSize: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()),e);
        } catch (Exception e) {
            Log.e(TAG, "getIconSize: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()),e);
        }
        return size;
    }

    @Override
    public DeviceResponse getIconUrl() {
        Log.i(TAG, "getIconUrl " + toLogString());
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_URL);
        if (isServiceSupported(Device.ServiceType.ABOUT_ICON)) {
            Log.i(TAG, "getIconUrl");
            if (m_iconClient == null) {
                try {
                    if (m_serviceName == null) {
                        Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + " service name is null");
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                    m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
                    if (m_iconClient == null){// check if m_iconClient is not null
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId ,e);
                    return new DeviceResponse(ResponseCode.Status_ERROR);
                }
            }
            try {
                if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId + " failed to establish session");
                    return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION);
                }
                String url = m_iconClient.GetUrl();
                Log.d(TAG, "getIconUrl " + toLogString() + " returned url:[" + url+"]");
                return new DeviceResponse(ResponseCode.Status_OK, url);
            } catch (BusException e) {
                Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId ,e);
                return new DeviceResponse(ResponseCode.Status_ERROR);
            }catch (Exception e){
                Log.e(TAG, "getIconUrl deviceId=" + m_uniqueId ,e);
                return new DeviceResponse(ResponseCode.Status_ERROR);
            }
        }
        return new DeviceResponse(ResponseCode.Status_ERROR_NOT_SUPPORTED);

    }

    @Override
    public String getIconMimeType() {
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.GET_ICON_MIME_TYPE);
        if (m_iconClient == null) {
            try {
                if (m_serviceName == null) {
                    return null;
                }
                m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
            } catch (Exception e) {
                Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
                e.printStackTrace();
                return "";
            }
        }
        if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
            Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
            return "";
        }
        try {
            return m_iconClient.getMimeType();
        } catch (BusException e) {
            Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()),e);
            return "";
        }catch (Exception e){
            Log.e(TAG, "getIconMimeType: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()),e);
            return "";
        }


    }

    @Override
    public DeviceResponse getDeviceIconContent() {
        Log.i(TAG, "getDeviceIconContent " + toLogString());
        if (isServiceSupported(Device.ServiceType.ABOUT_ICON)) {
            Log.i(TAG, "getDeviceIconContent isServiceSupported");
            if (m_iconClient == null) {
                try {
                    if (m_serviceName == null) {
                        Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " Service name is null");
                        return new DeviceResponse(ResponseCode.Status_ERROR_NO_PEER_NAME);
                    }
                    m_iconClient = AboutServiceImpl.getInstance().createAboutIconClient(m_serviceName, this, m_port);
                } catch (Exception e) {
                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId ,e);
                    return new DeviceResponse(ResponseCode.Status_ERROR);
                }
            }
            FileOutputStream outStream = null;
            try {
                if (connectToDevice(m_iconClient).getStatus() != ResponseCode.Status_OK) {
                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " failed to establish session");
                    return new DeviceResponse(ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION);
                }
                int size = m_iconClient.getSize();
                if (size > 0) {
                    Log.d(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " content size is " + size);
                    byte[] iconContent = m_iconClient.GetContent();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(iconContent, 0, iconContent.length);
                    if (bitmap != null) {
                        String url = DeviceManager.ABOUT_ICON_LOCAL_PREFIX_URL + m_uniqueId.toString();
                        String md5String = Util.calcMD5FromString(url);
                        String md5name = "i_" + (md5String != null ? md5String.toLowerCase(Locale.US) : "");
                        String resStringIdentifier = org.alljoyn.dashboard.logic.Util.getDensityName(DeviceManagerImpl.m_context);
                        String imageInSD = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Alljoyn/" + resStringIdentifier + "/" + md5name + ".png";
                        File file = new File(imageInSD);
                        if (!file.isFile()) {
                            File parentDir = new File(file.getAbsoluteFile().getParent());
                            if (!parentDir.exists()) {
                                parentDir.mkdirs();
                                if (!parentDir.isDirectory()){
                                    Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId +" unable to generate direcotry "+parentDir.getAbsolutePath());
                                    return new DeviceResponse(DeviceResponse.ResponseCode.Status_ERROR);
                                }
                            }
                        }
                        outStream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                        outStream.flush();
                        Log.d(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + " url " + url + " resolved to " + imageInSD);
                        return new DeviceResponse(DeviceResponse.ResponseCode.Status_OK, url);
                    }else{
                        Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId +" failed to generate bitmap");
                    }
                } else {
                    Log.d(TAG, "getDeviceIconContent deviceId=" + m_uniqueId + "content size is 0 revert to default image");
                    return new DeviceResponse(DeviceResponse.ResponseCode.Status_OK, DeviceManager.ABOUT_ICON_DEFAULT_URL);
                }
            } catch (BusException e) {
                Log.e(TAG, "requestIcon: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()),e);
                Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId ,e);
            }catch (Exception e){
                Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId ,e);
            }
            finally {
                if (outStream != null) {
                    try {
                        outStream.close();
                    } catch (Exception e) {
                        Log.e(TAG, "getDeviceIconContent deviceId=" + m_uniqueId ,e);
                    }
                }
            }
        }
        return new DeviceResponse(DeviceResponse.ResponseCode.Status_ERROR);
    }

    @Override
    public void setHelpUrl(String l) {
        m_helpUrl = l;
    }

    @Override
    public void setDefaultLanguage(String l) {
        Log.d(TAG, String.format("setDefaultLanguage old=%s, new=%s", m_defaultLanguage, l));
        m_defaultLanguage = l;
    }

    @Override
    public String getDefaultLanguage() {
        return m_defaultLanguage;
    }

    // ************** Control Panel **************
    @Override
    public void stopControlPanelSession() {
        Log.d(TAG, "stopControlPanelSession with the device: " + toLogString());
        if (m_controllableClient == null) {
            Log.w(TAG, "stopControlPanelSession device has no controllable client: " + m_uniqueId);
            return;
        }
        Log.d(TAG, "End the session of control panel service with the device: " + m_uniqueId);
        m_controllableClient.endSession();
    }

    @Override
    public boolean isControlPanelReachanble() {
        if (m_controllableClient == null) {
            return false;
        }
        return m_controllableClient.isReachable();
    }

    @Override
    public void requestControlPanels() {
        Log.d(TAG, "requestControlPanels " + toLogString());
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.REQUEST_CONTROL_PANELS);
        try {
            if (m_controllableClient == null) {
                Log.d(TAG, "requestControlPanels m_controllableClient is null. creating one.");
                if (m_serviceName == null || m_serviceName.trim().length() == 0) {
                    return;
                }
                m_controllableClient = ControlPanelService.getInstance().getControllableDevice(m_uniqueId.toString(), m_serviceName);
            } else {
                Log.d(TAG, "requestControlPanels m_controllableClient is not null. using it.");
            }
            if (m_controllableClient.getSessionId() != null) {
                Log.d(TAG, "requestControlPanels already have a session. fetching the existing panel from controllable device: " + m_uniqueId);
                Collection<Unit> unitCollection = m_controllableClient.getUnitCollection();

                if (unitCollection != null && !unitCollection.isEmpty()) {
                    Log.d(TAG, "requestControlPanels already have a session. controllable device has a unit. returning unit's collection");
                    m_panels = unitCollection.iterator().next().getControlPanelCollection();
                    Bundle extras = new Bundle();
                    extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                    ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANELS_AVAILABLE, extras);
                }

            } else { // m_controllableClient.getSessionId() == null
                Log.d(TAG, "requestControlPanels has no session with controllable device. starting a session with controllable device: " + m_uniqueId);
                /* listen to Control Panel Adapter responses */
                DeviceEventsListener deviceController = new DeviceEventsListener() {

                    @Override
                    public void sessionLost(ControllableDevice device) {
                        Log.d(TAG, "Control panel lost session for device: " + device.getDeviceId());
                        m_panels = null;
                        Bundle extras = new Bundle();
                        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST, extras);
                    }

                    @Override
                    public void errorOccurred(ControllableDevice device, String reason) {
                        Log.d(TAG, "Control panel error occurred for device: " + device.getDeviceId() + ", reason: " + reason);

                        try {
                            org.alljoyn.bus.Status status = org.alljoyn.bus.Status.valueOf(reason);
                            switch (status) {

                            case ALLJOYN_JOINSESSION_REPLY_ALREADY_JOINED:
                                Log.e(TAG, "requestControlPanels: sesssion already joined. This is not a Failure, continue");
                                break;

                            case ALLJOYN_JOINSESSION_REPLY_FAILED:
                            case ALLJOYN_JOINSESSION_REPLY_UNREACHABLE:
                            case ALLJOYN_JOINSESSION_REPLY_REJECTED:
                            case BUS_REPLY_IS_ERROR_MESSAGE:
                            case BUS_NO_ROUTE:
                            case BUS_NO_ENDPOINT: {
                                Log.e(TAG, "requestControlPanels: Join Session failed. Device: '" + m_uniqueId + "'is unavailable at busName: '" + m_serviceName + "'");
                                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onDeviceLost(m_serviceName);
                                break;
                            }
                            default:
                                Bundle extras = new Bundle();
                                extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                                extras.putString(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON, reason);

                                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANELS_ERROR_OCCURED, extras);
                                break;
                            }
                        } catch (IllegalArgumentException ilae) {
                            Log.e(TAG, "requestControlPanels  " ,ilae);
                            Bundle extras = new Bundle();
                            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                            extras.putString(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON, reason);

                            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANELS_ERROR_OCCURED, extras);
                        }

                    }

                    @Override
                    public void sessionEstablished(ControllableDevice device, Collection<ControlPanelCollection> panels) {
                        Log.d(TAG, "Control panel session established for device: " + device.getDeviceId());
                        m_panels = panels;
                        Bundle extras = new Bundle();
                        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onDeviceAlive(m_serviceName);
                        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANELS_AVAILABLE, extras);

                    }
                };
                if (m_controlPanelObjectDescriptions != null) {
                    for (int i = 0; i < m_controlPanelObjectDescriptions.size(); i++) {
                        AboutObjectDescription bos = m_controlPanelObjectDescriptions.get(i);
                        m_controllableClient.addControlPanel(bos.path, bos.interfaces);
                    }
                    Log.d(TAG, "Start a session of control panel service with the device: " + m_serviceName);
                    m_controllableClient.startSession(deviceController);
                }
            }
        } catch (ControlPanelException e) {
            Log.e(TAG, "requestControlPanels: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()), e);
            Bundle extras = new Bundle();
            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
            extras.putString(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON, "startSession failed");
            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANELS_ERROR_OCCURED, extras);
        }

    }

    @Override
    public Collection<ControlPanelCollection> getPanels() {
        return m_panels;
    }

    @Override
    public void requestControlPopup(final String popupObjectPath) {
        Log.d(TAG, "requestControlPopup " + toLogString() + ", objPath: " + popupObjectPath);
        setTag(DEVICE_TAG_LAST_ACTION, DeviceAction.REQUEST_CONTROL_POPUP);

        try {
            if (m_controllableClient == null) {
                if (m_serviceName == null) {
                    return;
                }
                m_controllableClient = ControlPanelService.getInstance().getControllableDevice(m_uniqueId.toString(), m_serviceName);
            }
            if (m_controllableClient != null) {
                /* listen to Control Panel Adapter responses */
                DeviceEventsListener deviceController = new DeviceEventsListener() {

                    @Override
                    public void sessionLost(ControllableDevice device) {
                        Log.d(TAG, "Session lost for device: " + device.getDeviceId());
                        Bundle extras = new Bundle();
                        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_PANEL_SESSION_LOST, extras);
                    }

                    @Override
                    public void errorOccurred(ControllableDevice device, String reason) {
                        Log.d(TAG, "Error occurred for device: " + device.getDeviceId());
                        Bundle extras = new Bundle();
                        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                        extras.putString(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON, reason);
                        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED, extras);
                    }

                    @Override
                    public void sessionEstablished(ControllableDevice device, Collection<ControlPanelCollection> panels) {
                        Log.d(TAG, "Session established for device: " + device.getDeviceId());

                        try {
                            m_controlPopups = device.createNotificationAction(popupObjectPath);

                            Log.d(TAG, "ControllableDevice.createNotificationAction ok");
                            Bundle extras = new Bundle();
                            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_POPUP_AVAILABLE, extras);
                        } catch (ControlPanelException e) {
                            Log.e(TAG, "ControllableDevice.createNotificationAction failed with exception: " + e.getMessage(), e);
                            Bundle extras = new Bundle();
                            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, m_uniqueId);
                            extras.putString(IntentExtraKeys.CONTROL_PANEL_ERROR_REASON, e.getMessage());
                            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_POPUP_ERROR_OCCURED, extras);
                        }
                    }
                };

                Log.d(TAG, "Start a session of control panel service with the device: " + m_serviceName);
                m_controllableClient.startSession(deviceController);

            }
        } catch (ControlPanelException e) {
            Log.e(TAG, "requestControlPanels: device " + m_friendlyName + "-ServiceName = " + m_serviceName + (m_apNetworkInfo == null ? "" : "-ssid = " + m_apNetworkInfo.getSSID()));
            e.printStackTrace();
        }

    }

    @Override
    public ControlPanelCollection getControlPopups() {
        return m_controlPopups;
    }

    public void resetServices() {
        Log.i("TAG", "resetServices ending all sessions of device: " + toLogString());
        if (m_aboutClient != null && m_aboutClient.isConnected()) {
            m_aboutClient.disconnect();
        }
        m_aboutClient = null;

        if (m_configClient != null && m_configClient.isConnected()) {
            m_configClient.disconnect();
        }
        m_configClient = null;

        if (m_onboardingClient != null && m_onboardingClient.isConnected()) {
            m_onboardingClient.disconnect();
        }
        m_onboardingClient = null;

        if (m_iconClient != null && m_iconClient.isConnected()) {
            m_iconClient.disconnect();
        }
        m_iconClient = null;

        if (m_controllableClient != null && m_controllableClient.getSessionId() != null) {
            m_controllableClient.endSession();
        }
        m_controllableClient = null;
    }

    private String toLogString() {
        return String.format("[busName=%s, appId=%s, name=%s]", m_serviceName, m_uniqueId, m_friendlyName);
    }

    @Override
    public DeviceAction getLastAction() {
        DeviceAction lastAction;
        try {
            lastAction = (DeviceAction) getTag(Device.DEVICE_TAG_LAST_ACTION);
        } catch (Exception e) {
            e.printStackTrace();
            return DeviceAction.NONE;
        }
        if (lastAction == null) {
            return DeviceAction.NONE;
        }
        return lastAction;
    }

}
