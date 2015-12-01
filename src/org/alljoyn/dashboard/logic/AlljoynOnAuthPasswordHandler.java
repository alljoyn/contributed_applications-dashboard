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

import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.login.security.AuthPasswordHandler;

import android.os.Bundle;
import android.util.Log;

public class AlljoynOnAuthPasswordHandler implements AuthPasswordHandler {

    private final String           TAG = this.getClass().getName();

    @Override
    public char[] getPassword(String peerName) {
        Device d = ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).getDeviceByServiceName(peerName);
        if (d  != null)
        {
            Log.d(TAG, "getPassword for device " + d.getFriendlyName() + " authPeer(serviceName)= " + peerName + " --> " + String.valueOf(d.getPassphrase()));
            return d.getPassphrase();
        }
        return Device.DEFAULT_PINCODE;
    }

    @Override
    public void completed(String mechanism, String authPeer, boolean authenticated) {
        Log.d(TAG, "Auth completed: mechanism = " + mechanism + " authPeer= " + authPeer + " --> " + authenticated);
        Device d = ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).getDeviceByServiceName(authPeer);
        if (d  != null)
        {
            String ajOnDeviceAuthenticationResult = authenticated ?
                    IntentActions.AJ_ON_DEVICE_AUTHENTICATION_SUCCESS :
                        IntentActions.AJ_ON_DEVICE_AUTHENTICATION_ERROR;
            Bundle extra = new Bundle();
            if (d.getId() != null) {
                extra.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, d.getId());
            }
            if (d.getAPWifiInfo() != null) {
                extra.putString(IntentExtraKeys.EXTRA_DEVICE_SSID, d.getAPWifiInfo().getSSID());
            }
            ((DeviceManagerImpl)DeviceManagerImpl.getInstance()).updateTheUi(ajOnDeviceAuthenticationResult,extra);
        }
    }
}
