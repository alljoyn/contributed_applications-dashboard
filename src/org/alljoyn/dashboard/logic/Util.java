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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.services.common.BusObjectDescription;
import org.alljoyn.services.common.utils.GenericLogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

public class Util {
    private static final String TAG = "DASHBOARD_Util";
    public static SharedPreferences pref;

    private static final GenericLogger m_logger = new GenericLogger() {
        @Override
        public void debug(String TAG, String msg) {
            Log.d(TAG, msg);
        }

        @Override
        public void info(String TAG, String msg) {
            // To change body of implemented methods use File | Settings | File
            // Templates.
            Log.i(TAG, msg);
        }

        @Override
        public void warn(String TAG, String msg) {
            // To change body of implemented methods use File | Settings | File
            // Templates.
            Log.w(TAG, msg);
        }

        @Override
        public void error(String TAG, String msg) {
            // To change body of implemented methods use File | Settings | File
            // Templates.
            Log.e(TAG, msg);
        }

        @Override
        public void fatal(String TAG, String msg) {
            // To change body of implemented methods use File | Settings | File
            // Templates.
            Log.wtf(TAG, msg);
        }
    };

    public static void saveDevices(ArrayList<DeviceImpl> devices) {

    }

    public static ArrayList<DeviceImpl> uploadDevices() {
        return null;
    }

    public static GenericLogger getGenericLogger() {
        return m_logger;
    }

    public static String calcMD5FromString(String input) {
        if (input == null || input.length() == 0) {
            return null;
        }
        try {
            MessageDigest hashEngine = MessageDigest.getInstance("MD5");
            hashEngine.update(input.getBytes("iso-8859-1"), 0, input.length());
            return convertToHex(hashEngine.digest());
        } catch (Exception e) {
            return null;
        }
    }

    public static final String convertToHex(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX_CODE[(b >> 4) & 0xF]);
            r.append(HEX_CODE[(b & 0xF)]);
        }
        String hexadecimalString = r.toString();

        return hexadecimalString;
    }

    public static String convertHexToString(String hex) {

        m_logger.debug(TAG, "convertHexToString: " + hex);
        if (hex == null || hex.length() % 2 != 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // split into two characters
        for (int i = 0; i < hex.length() - 1; i += 2) {

            // grab the hex in pairs
            String output = hex.substring(i, (i + 2));
            // convert hex to decimal
            int decimal = Integer.parseInt(output, 16);
            // convert the decimal to character
            sb.append((char) decimal);
        }
        m_logger.debug(TAG, "convertHexToString: " + sb.toString());
        return sb.toString();
    }

    /**
     * A utility for conversion of password to hexa array
     */
    private static final char[] HEX_CODE = "0123456789ABCDEF".toCharArray();

    public static String toHexadecimalString(String pass) {

        byte[] data;
        try {
            data = pass.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed getting bytes of passcode by UTF-8", e);
            data = pass.getBytes();
        }
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX_CODE[(b >> 4) & 0xF]);
            r.append(HEX_CODE[(b & 0xF)]);
        }
        String hexadecimalString = r.toString();

        return hexadecimalString;
    }

    public static String bundleToString(Bundle bundle) {
        if (bundle == null) {
            return "";
        }
        Set<String> keys = bundle.keySet();
        if (keys == null || keys.size() == 0) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        for (String key : keys) {
            s.append("  " + key + " = " + bundle.get(key));
        }
        return s.toString();
    }

    public static String mapToString(Map<String, Object> map) {
        StringBuilder s = new StringBuilder('[');
        if (map != null) {
            Set<String> keys = map.keySet();
            for (String key : keys) {
                s.append('{').append(key).append('=').append(map.get(key)).append('}');
            }
        }
        s.append(']');
        return s.toString();
    }

    public static String getBusObjectDescriptionArrayToString(AboutObjectDescription[] objectDescriptions) {
        if (objectDescriptions == null) {
            return "";
        }

        StringBuffer s = new StringBuffer();
        for (AboutObjectDescription objDesc : objectDescriptions) {
            s.append("{" + objDesc.path + ";");
            String[] interfaces = objDesc.interfaces;
            for (String inter : interfaces) {
                s.append(inter + ",");
            }
            s.append("}");
        }
        return s.toString();
    }

    public static String getDensityName(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        if (density >= 4.0) {
            return "xxxhdpi";
        }
        if (density >= 3.0) {
            return "xxhdpi";
        }
        if (density >= 2.0) {
            return "xhdpi";
        }
        if (density >= 1.5) {
            return "hdpi";
        }
        if (density >= 1.0) {
            return "mdpi";
        }
        return "ldpi";
    }

}
