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
package org.alljoyn.dashboard.logic.Interface;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.alljoyn.about.transport.AboutTransport;
import org.alljoyn.about.transport.IconTransport;
import org.alljoyn.bus.OnPingListener;
import org.alljoyn.config.transport.ConfigTransport;
import org.alljoyn.ioe.controlpanelservice.ControlPanelCollection;
import org.alljoyn.ioe.controlpanelservice.ControlPanelService;
import org.alljoyn.onboarding.OnboardingService.AuthType;
import org.alljoyn.onboarding.sdk.WiFiNetwork;
import org.alljoyn.onboarding.transport.MyScanResult;
import org.alljoyn.onboarding.transport.OnboardingTransport;

// if the device has not been on boarded, this is the actual device (Coffee machine, toaster oven etc.)
// if the device has on boarded,this is the application that runs on the machine.
// (machine can have more than one application running on it)
public interface Device {

    public static final char[] DEFAULT_PINCODE = new char[] { '0', '0', '0', '0', '0', '0' };

    public enum IconDataRequestType {
        EMPTY("EMPTY"), SDCARD("SDCARD"), RESOURCE("RESOURCE"), DEVICE_CONTENT("DEVICE_CONTENT");

        private final String brandname;

        private IconDataRequestType(String brand) {
            this.brandname = brand;
        }

        @Override
        public String toString() {
            return brandname;
        }
    }

    public enum DeviceStatus {
        CONFIGURING, AVAILABLE, UNAVAILABLE,
        // onboarding
        UNCONFIGURED, GONE // i.e deleted
        // CONFIGURATION_FAILED,
    }

    public static String DEVICE_TAG_LAST_ACTION = "device_tag_last_action";

    public enum DeviceAction {
        NONE,
        // About
        GET_ABOUT,
        // About Icon
        GET_ICON_MIME_TYPE, GET_ICON_URL, GET_ICON_SIZE,
        // Config
        GET_CONFIG, SET_CONFIG, SET_PASSWORD, RESET_PASSWORD, RESTART_DEVICE, FACTORY_RESET,
        // Onboarding
        ONBOARD_DEVICE, GET_VISIBLE_NETWORKS,
        // Control Panels
        REQUEST_CONTROL_PANELS, REQUEST_CONTROL_POPUP
    }

    public enum ServiceType {
        ABOUT(AboutTransport.INTERFACE_NAME), ABOUT_ICON(IconTransport.INTERFACE_NAME), CONFIG(ConfigTransport.INTERFACE_NAME), ONBOARDING(OnboardingTransport.INTERFACE_NAME), NOTIFICATION(
                "org.alljoyn.Notification"), CONTROL_PANEL(ControlPanelService.INTERFACE_PREFIX);

        private String m_interface = "";

        private ServiceType(String interfaceName) {
            m_interface = interfaceName;
        }

        public String getInterface() {
            return m_interface;
        }

    }// enum

    // Tag manage
    public Object getTag(String key);

    public void setTag(String key, Object value);

    public Map<String, Object> getAllTags();

    public boolean hasTag(String key);

    public void removeTag(String key);

    public void setAPWifiInfo(WiFiNetwork SSID);

    public WiFiNetwork getAPWifiInfo();

    public UUID getId();

    public String getOnboardedNetworkSSID();

    public void setOnboardedNetworkSSID(String onboardedNetworkSSID);

    public void setDefaultLanguage(String l);

    public String getDefaultLanguage();

    // matt methods

    public DeviceAction getLastAction();

    public String getFriendlyName();

    public DeviceStatus getStatus();

    public void setStatus(DeviceStatus status);

    public void setHelpUrl(String l);

    public String getHelpURL();

    public void turnOnNotifications();

    public void turnOffNotifications();

    public boolean isNotificationOn();

    public boolean isPinned();

    public void pinToNearby(boolean favorite);

    public DeviceResponse restartDevice();

    public DeviceResponse factoryReset();

    public DeviceResponse ping();

    public void setHandleAsynchPings(boolean handle);

    public OnPingListener getOnPingListener();

    public void pingAsync(OnPingListener onPingListener);

    public Map<String, Object> getAbout(boolean force);

    public Map<String, Object> getConfig();

    public DeviceResponse setConfig(Map<String, Object> config);

    public char[] getPassphrase();

    public DeviceResponse setPassphrase(char[] passphrase, boolean shouldUpdateRemoteDevice);

    public void resetPassphrase();

    public int getIconSize();

    public DeviceResponse getIconUrl();

    public DeviceResponse getDeviceIconContent();

    public String getStoredIconUrl();

    public void setStoredIconUrl(String url);

    public String getIconMimeType();

    // when the logic discover new onboarded aj device it will fire intent in
    // type IrefreshType.AJ_ON_REFRESH_DEVICE_LIST

    boolean isServiceSupported(ServiceType service);

    public MyScanResult[] getVisibleNetworks();

    public void stopControlPanelSession();

    public boolean isControlPanelReachanble();

    public void requestControlPanels();

    public Collection<ControlPanelCollection> getPanels();

    public void requestControlPopup(String popupObjectPath);

    public ControlPanelCollection getControlPopups();

    public class OnboardingParams {
        public OnboardingParams(String networkName, String networkPass, AuthType selectedAuthType, boolean isPasswordASCII, boolean manually) {
            m_networkName = networkName;
            m_networkPass = networkPass;
            m_selectedAuthType = selectedAuthType;
            m_isPasswordASCII = isPasswordASCII;
            m_manually = manually;
        }

        public String m_networkName;
        public String m_networkPass;
        public AuthType m_selectedAuthType;
        public boolean m_isPasswordASCII;
        public boolean m_manually;
    }

};
