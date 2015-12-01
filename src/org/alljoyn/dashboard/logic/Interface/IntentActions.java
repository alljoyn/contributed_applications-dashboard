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

public class IntentActions {

    // FOR INTENTS

    public static final String AJ_ON_REFRESH_AP_LIST = "org.alljoyn.dashboard.refresh_ap_list";
    public static final String AJ_ON_DEVICE_FOUND = "org.alljoyn.dashboard.device_found";
    public static final String AJ_ON_DEVICE_LOST = "org.alljoyn.dashboard.device_lost";
    public static final String AJ_ON_DEVICE_ALIVE = "org.alljoyn.dashboard.device_alive";
    public static final String AJ_ON_NEW_NOTIFICATION_ARRIVED = "org.alljoyn.dashboard.new_notification_arrived";
    public static final String AJ_ON_NEW_NOTIFICATION_REMOVED = "org.alljoyn.dashboard.new_notification_removed";
    public static final String AJ_ON_DEVICE_ICON_AVAILABLE = "org.alljoyn.dashboard.device_icon_available";

    public static final String AJ_ON_SESSION_LOST_WITH_DEVICE = "org.alljoyn.dashboard.session_lost_with_device";
    public static final String AJ_ON_DEVICE_AUTHENTICATION_ERROR = "org.alljoyn.dashboard_device_auth_fail";
    public static final String AJ_ON_DEVICE_AUTHENTICATION_SUCCESS = "org.alljoyn.dashboard_device_auth_ok";
    public static final String AJ_ON_DEVICE_PASSWORD_SAVED = "org.alljoyn.dashboard_device_password_saved";

    public static final String AJ_ON_WIFI_STATE_CHANGED = "org.alljoyn.dashboard.wifi_state_changed";
    public static final String AJ_ON_WIFI_CONNECTED = "org.alljoyn.dashboard.wifi_connection_by_request";
    public static final String AJ_ON_WIFI_TIMEOUT = "org.alljoyn.dashboard.wifi_time_out";

    public static final String AJ_ON_WIFI_DISABLED = "org.alljoyn.dashboard.wifi_disabled";
    public static final String AJ_ON_WIFI_CANT_CONNECT = "org.alljoyn.dashboard.wifi_cant_connect";
    public static final String AJ_ON_USER_EXIT_APP = "org.alljoyn.dashboard.user_exit_app";
    public static final String AJ_ON_SCROLL_TO_TOP_AP_LIST = "org.alljoyn.dashboard.scroll_to_top_ap_list";

    // Control panel
    public static final String AJ_ON_CONTROL_NOTIFICATION_ARRIVED = "org.alljoyn.dashboard_control_notification_received";
    public static final String AJ_ON_CONTROL_POPUP_AVAILABLE = "org.alljoyn.dashboard_control_popup_available";
    public static final String AJ_ON_CONTROL_PANELS_AVAILABLE = "org.alljoyn.dashboard_control_panel_available";
    public static final String AJ_ON_CONTROL_PANEL_SESSION_LOST = "org.alljoyn.dashboard_control_panel_session_lost";
    public static final String AJ_ON_CONTROL_PANEL_VALUE_CHANGED = "org.alljoyn.dashboard_control_panel_value_changed";
    public static final String AJ_ON_CONTROL_PANELS_METADATA_CHANGED = "org.alljoyn.dashboard_control_panel_metadata_changed";
    public static final String AJ_ON_CONTROL_PANELS_ERROR_OCCURED = "org.alljoyn.dashboard_control_panel_error";
    public static final String AJ_ON_CONTROL_POPUP_ERROR_OCCURED = "org.alljoyn.dashboard_control_popup_error";

    // public static final String AJ_ON_LOGIC_WIFI_CONNECT_FAIL =
    // "org.alljoyn.dashboard.logic.wifi.connect.fail";
    public static final String AJ_ON_LOGIC_WIFI_AUTHENTICATION_ERROR = "org.alljoyn.dashboard.logic.wifi_authentication_error";

    public static final String AJ_ON_DEVICE_STATUS_CHANGED = "org.alljoyn.dashboard.device_status_state";

    public static final String AJ_ON_SHOW_TOAST_ACTION = "SHOW_TOAST_ACTION";

}
