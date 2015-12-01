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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.alljoyn.ns.Notification;
import org.alljoyn.ns.NotificationText;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.NotificationsManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper;
import org.alljoyn.dashboard.ui.activity.NotificationsActivity;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.os.Vibrator;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class UIUtil
{

    private static final String TAG = "DASHBOARD_UIUtil";

    public static String DEVICE_TAG_SHOW_RESET_LAYOUT = "device_tag_show_reset_layout";

    public static boolean isApplicationInBackground(Context context)
    {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty())
        {
            final ComponentName topActivity = tasks.get(0).topActivity;
            if (!topActivity.getPackageName().equals(context.getPackageName()))
            {
                return true;
            }
        }

        return false;
    }

    public static boolean isNotificationsActivityVisible(Context context)
    {
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty())
        {
            final ComponentName topActivity = tasks.get(0).topActivity;
            if (topActivity.getClassName().equals(NotificationsActivity.class.getName()))
            {
                return true;
            }
        }

        return false;
    }

    public static String getAppVersion(Activity activity)
    {
        try
        {
            return activity.getApplication().getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        }
        catch (NameNotFoundException e) {
            Writer writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            Log.e(TAG,"getAppVersion  "+writer.toString());
        }

        return "";
    }

    public static boolean isWifiOff(Context context)
    {
        final WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return !wifi.isWifiEnabled();
    }

    public static DeviceStatus getDeviceStatus(Context context, Device device)
    {
        // if wifi is turned off, treat the UI as if the device is out of network
        return device.getStatus();
    }

    public static int getDeviceStatusTextId(DeviceStatus status)
    {
        switch (status)
        {
        case AVAILABLE:
            return R.string.status_available;
        case UNAVAILABLE:
        case GONE:
            return R.string.status_unavailable;
        case CONFIGURING:
            return R.string.status_configuring;
        case UNCONFIGURED:
            return R.string.status_unconfigured;
        default:
            return R.string.status_unknown;
        }
    }

    public static int getDeviceStatusIconId(DeviceStatus status)
    {
        switch (status)
        {
        case AVAILABLE:
            return R.drawable.my_devices_status_available_icon;// green
        case UNAVAILABLE:
        case GONE:
            return R.drawable.my_devices_status_off_icon;//gray
        case CONFIGURING:
        case UNCONFIGURED:
            return R.drawable.my_devices_status_configuring_icon;// yellow
        default:
            return R.drawable.my_devices_status_off_icon;//gray
        }
    }

    public static String getNotificationTextAsString(Notification notification)
    {
        if (notification != null && notification.getText() != null)
        {
            final Device device = getDevice(notification.getAppId());
            if(device == null) {
                return "";
            }
            final String lang = device.getDefaultLanguage();

            final Iterator<NotificationText> iter = notification.getText().iterator();

            NotificationText nextText;
            while (iter.hasNext())
            {
                nextText = iter.next();
                if ((nextText != null) && (lang != null) && lang.equals(nextText.getLanguage()))
                {
                    // if the language matches, this is the text we want to display
                    return nextText.getText();
                }
            }

            // no language matched, return the first entry in the array by default
            return (notification.getText().size() > 0) ? notification.getText().get(0).getText() : "";
        }

        return "";
    }

    public static void setDeviceIcon(ImageView view, UUID deviceId)
    {
        Device device = getDevice(deviceId);
        if (device!=null){
            Bitmap b=DeviceManagerImpl.getInstance().getDeviceImage(deviceId);
            if (b!=null){
                view.setImageBitmap(b);
            }else{
                view.setImageResource(R.drawable.my_devices_icon_reg);
            }
        }else{
            view.setImageResource(R.drawable.my_devices_icon_reg);
        }
    }



    public static ArrayList<Device> getMyDevices()
    {
        // refresh the list
        return (ArrayList<Device>) DeviceManagerImpl.getInstance().getDevices();
    }

    public static Device getDevice(UUID uuid)
    {
        return DeviceManagerImpl.getInstance().getDevice(uuid);
    }

    public static NotificationWrapper getNotification(int id)
    {
        final ArrayList<NotificationWrapper> fullList = NotificationsManagerImpl.getInstance().getNotificationList();

        NotificationWrapper nw;
        Notification n;
        final Iterator<NotificationWrapper> iter = fullList.iterator();
        while (iter.hasNext())
        {
            nw = iter.next();

            n = nw.getNotification();
            if (n != null && id == n.getMessageId())
            {
                return nw;
            }
        }

        // notification doesn't exist
        return null;
    }

    public static void vibrate(Context context, long time)
    {
        final Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // some devices like the Droid 2 will vibrate twice unless
        // we define the pattern in which to vibrate
        final long[] pattern = new long[] { 350 , time };
        v.vibrate(pattern, -1);
    }

    public static void showToast(final Activity activity, final String msg)
    {
        final Runnable runOnUi = new Runnable()
        {
            @Override
            public void run()
            {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        };
        runOnUiThread(activity, runOnUi);
    }

    public static void showToastFromIntent(final Context context, final String msg)
    {
        if (context instanceof Activity)
        {
            // don't send intent if it's not necessary
            showToast((Activity) context, msg);
        }
        else
        {
            final Intent toastIntent = new Intent(IntentActions.AJ_ON_SHOW_TOAST_ACTION);
            toastIntent.putExtra(IntentExtraKeys.EXTRA_TOAST_MSG, msg);
            context.sendBroadcast(toastIntent);
        }
    }

    public static void runOnUiThread(final Activity activity, final Runnable runOnUi)
    {
        if (Looper.getMainLooper().getThread().getId() == Thread.currentThread().getId())
        {
            // confirmed we're on the UI thread
            runOnUi.run();
        }
        else
        {
            // we're not on the UI thread, make sure to call the toast from that thread
            activity.runOnUiThread(runOnUi);
        }
    }

    //Show soft keyboard
    public static void forceIme()
    {
        if (AlljoynOnApplication.getInstance().getImm() != null)
        {
            AlljoynOnApplication.getInstance().getImm().toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    public static void hideIme(Activity a)
    {
        InputMethodManager imm = AlljoynOnApplication.getInstance().getImm();
        if (imm != null)
        {
            View currentFocused = a.getCurrentFocus();
            if (currentFocused != null) {
                AlljoynOnApplication.getInstance().getImm().hideSoftInputFromWindow(currentFocused.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    // Hide soft keyboard
    public static void hideIme(View v)
    {
        if (v!=null && AlljoynOnApplication.getInstance().getImm() != null)
        {
            AlljoynOnApplication.getInstance().getImm().hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
        }
    }

    // keep the keyboard from being shown by default
    public static void blockIme(Activity activity)
    {
        if (activity != null ){
            Window win =activity.getWindow();
            if (win!=null)
            {
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        }
    }

    public static void hashEditTextForPasswords(EditText edit)
    {
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    public static void removeHashFromEditText(EditText edit)
    {
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setTransformationMethod(null);
        edit.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }


    public static String getUserFriendlyAP(String text)
    {
        String modtext = text;
        boolean modified = false;
        if (modtext != null && modtext.startsWith(DeviceManagerImpl.WifiAJLookupPrefix))
        {
            // remove the AJ header and replace underscores with spaces
            modtext = modtext.substring(3);
            modified = true;
        }

        if (modtext != null && modtext.endsWith(DeviceManagerImpl.WifiAJLookupSuffix))
        {
            // remove the AJ header and replace underscores with spaces
            modtext = modtext.substring(0, modtext.length() -3);
            modified = true;
        }
        if (modtext != null && modified){
            return modtext.replaceAll("_", " ");
        }

        // don't modify
        return text;
    }

    public static boolean isPasscodeSet(Device device)
    {
        return (device != null) && (device.getPassphrase() != null) && !(device.getPassphrase().toString().equals(Device.DEFAULT_PINCODE.toString()));
    }

    /** Build a String from a raw resource file. */
    public static String readRawTextFile(int resId, Activity activity)
    {
        InputStream inputStream = activity.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader buffreader = new BufferedReader(inputreader);
        String line;
        StringBuilder text = new StringBuilder();

        try
        {
            while (( line = buffreader.readLine()) != null)
            {
                text.append(line);
                text.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return text.toString();
    }
}
