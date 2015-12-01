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
package org.alljoyn.dashboard.ui.util;

import org.alljoyn.onboarding.OnboardingService.AuthType;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class UISharedPreferencesManager
{

    private static final String PREF_VAL_TRUE = "TRUE";
    private static final String PREF_VAL_FALSE = "FALSE";
    private static final String PREF_KEY_TOS_ACCEPTED = "TOS_ACCEPTED";
    private static final String PREF_KEY_SHOW_ONBOARD_FINISHED = "SHOW_ONBOARD_FINISHED";
    private static final String PREF_KEY_NETWORK_NAME = "NETWORK_NAME";
    private static final String PREF_KEY_NETWORK_PASSWORD = "NETWORK_PASSWORD";
    private static final String PREF_KEY_NETWORK_AUTHTYPE = "NETWORK_AUTHTYPE";
    private static final String PREF_KEY_NETWORK_HIDDEN = "NETWORK_HIDDEN";
    private static final String PREF_KEY_SHOW_NO_WIFI_FIRST_LAUNCH = "SHOW_NO_WIFI_FIRST_LAUNCH";
    private static final String PREF_KEY_SAVED_HOME_NETWORK = "SAVED_HOME_NETWORK";

    private static SharedPreferences APP_PREFERENCES;

    public static void initPreferences(Context context)
    {
        APP_PREFERENCES = context.getSharedPreferences("APP_PREFERENCES", Context.MODE_PRIVATE);
    }

    private static void storePreference(String key, String value)
    {
        if (APP_PREFERENCES == null)
        {
            return;
        }

        final Editor e = APP_PREFERENCES.edit();
        e.putString(key, value);
        e.commit();
    }

    private static String getPreference(String key, String defValue)
    {
        if (APP_PREFERENCES == null)
        {
            return defValue;
        }

        return APP_PREFERENCES.getString(key, defValue);
    }

    public static boolean isToSAccepted()
    {
        return true;//PREF_VAL_TRUE.equals(getPreference(PREF_KEY_TOS_ACCEPTED, PREF_VAL_FALSE));
    }

    public static int isHomeNetworkSet() {
        return Integer.parseInt(getPreference(PREF_KEY_SAVED_HOME_NETWORK, "0"));
    }

    public static void toSHasBeenAccepted()
    {
        storePreference(PREF_KEY_TOS_ACCEPTED, PREF_VAL_TRUE);
    }

    public static boolean showOnboardFinished()
    {
        return PREF_VAL_TRUE.equals(getPreference(PREF_KEY_SHOW_ONBOARD_FINISHED, PREF_VAL_TRUE));
    }

    public static void stopShowingOnboardFinished()
    {
        storePreference(PREF_KEY_SHOW_ONBOARD_FINISHED, PREF_VAL_FALSE);
    }

    public static String getStoredNetworkName()
    {
        return getPreference(PREF_KEY_NETWORK_NAME, "");
    }

    public static String getStoredNetworkPassword()
    {
        return getPreference(PREF_KEY_NETWORK_PASSWORD, "");
    }

    public static boolean isStoredNetworkHidden() {
        if (APP_PREFERENCES == null) {
            return false;
        } else {
            return APP_PREFERENCES.getBoolean(PREF_KEY_NETWORK_HIDDEN, false);
        }
    }

    public static AuthType getStoredAuthType()
    {
        if (APP_PREFERENCES == null)
        {
            return AuthType.ANY;
        }

        int storedVal = APP_PREFERENCES.getInt(PREF_KEY_NETWORK_AUTHTYPE, -1);
        if (storedVal == -1) {
            return AuthType.ANY;
        }
        return AuthType.values()[storedVal];
    }

    public static void setHomeNetwork(boolean isVerified, boolean skipped) {
        if(isVerified) {
            storePreference(PREF_KEY_SAVED_HOME_NETWORK, "1");
        } else if(skipped) {
            storePreference(PREF_KEY_SAVED_HOME_NETWORK, "-1");
        }
    }

    public static void setStoredNetwork(String name, String password, AuthType authType, boolean isHidden)
    {
        // assuming arguments are correctly formatted
        storePreference(PREF_KEY_NETWORK_NAME, name);
        storePreference(PREF_KEY_NETWORK_PASSWORD, password);

        final Editor e = APP_PREFERENCES.edit();
        if (e != null)
        {
            if (authType != null) {
                e.putInt(PREF_KEY_NETWORK_AUTHTYPE, authType.ordinal());
            }
            e.putBoolean(PREF_KEY_NETWORK_HIDDEN, isHidden);
            e.commit();
        }
    }

    public static boolean showNoWiFiFirstLaunch()
    {
        return PREF_VAL_TRUE.equals(getPreference(PREF_KEY_SHOW_NO_WIFI_FIRST_LAUNCH, PREF_VAL_TRUE));
    }

    public static void stopShowingNoWiFiFirstLaunch()
    {
        storePreference(PREF_KEY_SHOW_NO_WIFI_FIRST_LAUNCH, PREF_VAL_FALSE);
    }

}
