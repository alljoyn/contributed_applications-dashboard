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
package org.alljoyn.dashboard.ui.application;

import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.ui.util.UISharedPreferencesManager;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.inputmethod.InputMethodManager;

public class AlljoynOnApplication extends Application {

    // private static final String TAG = "DASHBOARD_AlljoynOnApplication";

    private static AlljoynOnApplication m_instance;

    private static Handler m_handler;

    private boolean isDialogDisplayed;
    private boolean isNotificationWithActionPopUpDisplayed;

    /**
     * Load the native alljoyn_java library. The actual AllJoyn code is written
     * in C++ and the alljoyn_java library provides the language bindings from
     * Java to C++ and vice versa.
     */
    static {
        try {
            System.loadLibrary("alljoyn_java");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public AlljoynOnApplication() {
        super();

        m_instance = this;
    }

    public static AlljoynOnApplication getInstance() {
        return m_instance;
    }

    public static Handler getHanlder() {
        return m_handler;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: Rachel: check keyStore name

        HandlerThread thread = new HandlerThread("AllJoynOnLooper");
        thread.start();
        m_handler = new Handler(thread.getLooper());

        UISharedPreferencesManager.initPreferences(this);
        initListeners();

    }

    public void initListeners() {

        WifiUtil.getInstance().init(this);
        if (UISharedPreferencesManager.isToSAccepted()) {
            final String keyStoreFileName = getApplicationContext().getFileStreamPath("alljoyn_keystore").getAbsolutePath();
            DeviceManagerImpl.getInstance().init(this, keyStoreFileName);
            NotificationsManagerImpl.getInstance().init(this);
        }
    }

    public InputMethodManager getImm() {
        return (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    public void setSetupHomeWifiOn() {
        isDialogDisplayed = true;
    }

    public void setSetupHomeWifiOff() {
        isDialogDisplayed = false;
    }

    public boolean isSetupHomeWifiDisplayed() {
        return isDialogDisplayed;
    }

    public void setNotificationWithActionPopUpDisplayed(boolean value) {
        isNotificationWithActionPopUpDisplayed = value;
    }

    public boolean istNotificationWithActionPopUpDisplayed() {
        return isNotificationWithActionPopUpDisplayed;
    }

}
