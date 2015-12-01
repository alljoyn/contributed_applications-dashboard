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
import java.util.UUID;

import android.content.Context;
import android.graphics.Bitmap;

public interface DeviceManager {

    public static String ABOUT_ICON_DEFAULT_URL = "local://defaultURL";
    public static String ABOUT_ICON_LOCAL_PREFIX_URL = "local://DeviceContent/";
    public static final int DEFAULT_HEARTBEAT = 10;

    public void init(Context c, String keyStorekeyStoreFileName);

    public Collection<Device> getDevices();

    public Device getDevice(UUID deviceID);

    public Device getDeviceBySSID(String ssid);

    public boolean contains(UUID deviceID);

    public void scan();

    Device removeDevice(UUID deviceID);



    public Bitmap getDeviceImage(UUID deviceID);

    public boolean startPinging();

    public boolean startPinging(int heartbeatSeconds);

    public boolean stopPinging();
}
