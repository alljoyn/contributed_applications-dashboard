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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alljoyn.ns.Notification;
import org.alljoyn.ns.NotificationReceiver;
import org.alljoyn.ns.NotificationService;
import org.alljoyn.ns.NotificationServiceException;
import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceStatus;
import org.alljoyn.dashboard.logic.Interface.IntentActions;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper;
import org.alljoyn.dashboard.logic.Interface.NotificationWrapper.ReadStatus;
import org.alljoyn.dashboard.logic.Interface.NotificationsManager;
import org.alljoyn.dashboard.ui.activity.NearbyDevicesActivity;
import org.alljoyn.dashboard.ui.activity.NotificationsActivity;
import org.alljoyn.dashboard.ui.application.AlljoynOnApplication;
import org.alljoyn.dashboard.ui.application.NativeNotificationHandler;
import org.alljoyn.dashboard.ui.util.UISharedPreferencesManager;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class NotificationsManagerImpl extends BroadcastReceiver implements NotificationReceiver, NotificationsManager {
    private static final String TAG = "DASHBOARD_NotificationManager";

    private static NotificationsManagerImpl m_current;

    private List<NotificationWrapper> m_notificationList;
    private final int m_maxListSize = 50;

    private Context m_context;

    /**
     * Android OS NotificationService args
     */

    public static NotificationsManagerImpl getInstance() {
        if (m_current == null) {
            m_current = new NotificationsManagerImpl();
        }
        return m_current;
    }

    private NotificationsManagerImpl() {
        m_notificationList = new ArrayList<NotificationWrapper>();
    }

    public void init(Context context) {
        m_context = context;
        final IntentFilter filter = new IntentFilter();
        filter.addAction(NativeNotificationHandler.NOTIFICATION_CLEARED_ACTION);
        context.registerReceiver(this, filter, null, AlljoynOnApplication.getHanlder());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && NativeNotificationHandler.NOTIFICATION_CLEARED_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "onReceive: " + NativeNotificationHandler.NOTIFICATION_CLEARED_ACTION);
            markAllNotificationsAsCleared();
        } else {
            Log.w(TAG, "onReceive. intent is null. ignoring");
            return;
        }
    }

    @Override
    public synchronized void dismiss(int messageID, UUID uuid) {
        Log.d(TAG, "dismiss has been called for UUID " + uuid.toString() + " messageID " + messageID);

        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return;
        }

        for (NotificationWrapper notificationWrapper : m_notificationList) {
            if (notificationWrapper.getNotification().getMessageId() == messageID) {
                m_notificationList.remove(notificationWrapper);
                Bundle extras = new Bundle();
                extras.putInt(IntentExtraKeys.NOTIFICATION_ID, messageID);
                extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, uuid);
                // extras.putSerializable(IntentExtraKeys.DEVICE_ID,
                // notification.getAppId());
                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED, extras);
                break;
            }
        }
    }

    @Override
    public void receive(Notification notification) {
        Log.d(TAG, "======================== Notification =========================");
        Log.d(TAG, String.format("Id: '%s', MessageType: '%s' DeviceId: '%s', DeviceName: '%s', CustomAttributes: '%s', FirstMsgLang: '%s', FirstMsg: '%s', ResponsePath: '%s'",
                notification.getMessageId(), notification.getMessageType(), notification.getDeviceId(), notification.getDeviceName(), notification.getCustomAttributes().toString(), notification
                .getText().get(0).getLanguage(), notification.getText().get(0).getText(), notification.getResponseObjectPath()));
        Log.d(TAG, "======================== Notification =========================");

        String responseObjectPath = notification.getResponseObjectPath();
        boolean withAction = ((responseObjectPath != null) && !responseObjectPath.isEmpty());

        if (withAction) {
            handleNotificationWithAction(notification);
        } else {
            handleNotification(notification);
        }
    }

    private void handleNotificationWithAction(Notification notification) {

        Log.d(TAG, "handleNotificationWithAction.");

        if (!UISharedPreferencesManager.isToSAccepted()) {
            Log.d(TAG, "EULA not accepted yet, so we will not display a native notification, which might take the user past the EULA");
            return;
        }

        String controlObjectPath = notification.getResponseObjectPath();
        UUID deviceId = notification.getAppId();
        NotificationWrapper notificationWrapper = new NotificationWrapper(notification, System.currentTimeMillis());
        addToNotificationList(notificationWrapper);

        Bundle extras = new Bundle();
        extras.putInt(IntentExtraKeys.NOTIFICATION_ID, notification.getMessageId());
        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, notification.getAppId());
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED, extras);

        if (!UIUtil.isApplicationInBackground(m_context)) {
            Log.d(TAG, "handleNotificationWithAction. Application is in foreground. Fire an intent that will be picked up by the current Activity.");
            extras.clear();
            extras.putInt(IntentExtraKeys.NOTIFICATION_ID, notification.getMessageId());
            extras.putString(IntentExtraKeys.CONTROL_OBJ_PATH, controlObjectPath);
            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, deviceId);
            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_CONTROL_NOTIFICATION_ARRIVED, extras);
        } else {
            Log.d(TAG, "handleNotificationWithAction. Application is in background. Show a native Android notification in the upper bar.");
            Intent launchIntent = new Intent(m_context, NearbyDevicesActivity.class);
            launchIntent.setAction(IntentActions.AJ_ON_CONTROL_NOTIFICATION_ARRIVED);
            // Extract from Notification
            launchIntent.putExtra(IntentExtraKeys.EXTRA_DEVICE_ID, deviceId);
            launchIntent.putExtra(IntentExtraKeys.NOTIFICATION_ID, notification.getMessageId());
            launchIntent.putExtra(IntentExtraKeys.CONTROL_OBJ_PATH, controlObjectPath);

            // launching from background. Needs a new task, as there is no
            // parent activity
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            NativeNotificationHandler.display(m_context, notification, System.currentTimeMillis(), launchIntent);

        }

    }

    private void handleNotification(Notification notification) {

        Log.d(TAG, "handleNotification.");

        // update ui only for added devices.
        final UUID uniqueId = notification.getAppId();
        Device device = DeviceManagerImpl.getInstance().getDevice(uniqueId);
        if (device != null && !device.isNotificationOn()) {
            Log.i(TAG, "notification for " + notification.getAppId() + " -this device has notifications turned off");
            return;
        }

        // create a wrapper object for displaying the Notification
        NotificationWrapper notificationWrapper = new NotificationWrapper(notification, System.currentTimeMillis());

        // add to the list of Notifications
        addToNotificationList(notificationWrapper);

        if (!UISharedPreferencesManager.isToSAccepted()) {
            Log.d(TAG, "EULA not accepted yet, so we will not display a native notification, which might take the user past the EULA");
            return;
        }

        if ((notificationWrapper == null) || (notificationWrapper.getNotification() == null) || (notificationWrapper.getReadStatus() != ReadStatus.NEW)) {
            // notification has been read in app, has been cleared from the
            // tray, or doesn't exist -> do nothing
            Log.d(TAG, "notification has been read in app, has been cleared from the tray, or doesn't exist -> do nothing");
            return;
        }

        // show an upper bar item
        Log.d(TAG, "displayNativeNotification");

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(m_context);
        final boolean shouldDisplayNativeNotification = pref.getBoolean(m_context.getString(R.string.settings_notifications_key), true);

        if (shouldDisplayNativeNotification) {
            if (UIUtil.isNotificationsActivityVisible(m_context)) {

                // don't show notification if NotificationActivity is in the
                // foreground, but still vibrate if the setting is on
                Log.d(TAG, "User is viewing FeedActivity - don't show notification, but still vibrate if the setting is on.");

                final boolean shouldVibrate = pref.getBoolean(m_context.getString(R.string.settings_vibrate_key), true);
                final AudioManager audioManager = (AudioManager) m_context.getSystemService(Context.AUDIO_SERVICE);

                if (shouldVibrate && audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    UIUtil.vibrate(m_context, 200);
                }

                // make sure all notification are cleared
                markAllNotificationsAsRead();
                NativeNotificationHandler.clearNativeNotificationBar(m_context);

            } else {

                Log.d(TAG, "User is not viewing FeedActivity. Displaying a native Android notification in the top bar");

                Intent launchIntent = new Intent(m_context, NotificationsActivity.class);
                NativeNotificationHandler.display(m_context, notificationWrapper.getNotification(), notificationWrapper.getTime(), launchIntent);
            }
        } else {
            Log.d(TAG, "notifications turned off in preferences.");
        }

        // refresh open screens
        Bundle extras = new Bundle();
        extras.putInt(IntentExtraKeys.NOTIFICATION_ID, notification.getMessageId());
        extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, notification.getAppId());
        ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_ARRIVED, extras);
    }

    private synchronized void addToNotificationList(NotificationWrapper notification) {
        if (m_notificationList != null && m_notificationList.size() == m_maxListSize) {
            NotificationWrapper removed = m_notificationList.remove(m_notificationList.size() - 1);

            Bundle extras = new Bundle();
            extras.putInt(IntentExtraKeys.NOTIFICATION_ID, removed.getNotification().getMessageId());
            extras.putSerializable(IntentExtraKeys.EXTRA_DEVICE_ID, removed.getNotification().getAppId());
            ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED, extras);

        }
        if (m_notificationList == null) {
            m_notificationList = new ArrayList<NotificationWrapper>();
        }
        m_notificationList.add(0, notification);
    }

    @Override
    public synchronized int getUnclearedNotificationsCount() {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return 0;
        }

        int newCount = 0;
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);
            Device d = DeviceManagerImpl.getInstance().getDevice(current.getNotification().getAppId());
            if (d != null && d.getStatus() != DeviceStatus.GONE) {
                if (current.getReadStatus() == ReadStatus.NEW) {
                    newCount++;
                }
            }
        }
        return newCount;
    }

    @Override
    public synchronized int getUnreadNotificationsCount() {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return 0;
        }

        int newCount = 0;
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);

            Device d = DeviceManagerImpl.getInstance().getDevice(current.getNotification().getAppId());
            if (d != null && d.getStatus() != DeviceStatus.GONE) {
                if (current.getReadStatus() != ReadStatus.READ) {
                    newCount++;
                }
            }
        }
        return newCount;
    }

    @Override
    public synchronized int getUnreadNotificationsCount(UUID deviceId) {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return 0;
        }

        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d == null) {
            return 0;
        }

        int newCount = 0;
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);
            if (deviceId == null) {
                d = DeviceManagerImpl.getInstance().getDevice(current.getNotification().getAppId());
                if (d != null && d.getStatus() != DeviceStatus.GONE && current.getReadStatus() != ReadStatus.READ) {
                    newCount++;
                }
            } else if (current.getReadStatus() != ReadStatus.READ && deviceId.equals(current.getNotification().getAppId())) {
                newCount++;
            }
        }
        return newCount;
    }

    /*
     * @Override public void removeNotificationsForDevice(UUID deviceID) { if
     * (m_notificationList == null || m_notificationList.isEmpty()) return;
     *
     * if (deviceID == null) return; Vector<NotificationWrapper> removalObj =
     * new Vector<NotificationWrapper>(); for (NotificationWrapper current:
     * m_notificationList) { if
     * (deviceID.equals(current.getNotification().getAppId()))
     * removalObj.add(current); }
     *
     * for (NotificationWrapper obj: removalObj) {
     * m_notificationList.remove(obj); }
     * ((DeviceManagerImpl)DeviceManagerImpl.getInstance
     * ()).updateTheUi(IntentActions.AJ_ON_NEW_NOTIFICATION_REMOVED); }
     */
    @Override
    public synchronized void markAllNotificationsAsCleared() {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return;
        }
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);
            current.markAsCleared();
        }
    }

    @Override
    public synchronized void markAllNotificationsAsRead(UUID deviceId) {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return;
        }
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);
            if (deviceId != null && current.getNotification().getAppId().equals(deviceId)) {
                current.markAsRead();
            }else {
                current.markAsRead();
            }
        }
    }

    @Override
    public synchronized void markAllNotificationsAsRead() {
        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return;
        }
        for (int i = 0; i < m_notificationList.size(); i++) {
            NotificationWrapper current = m_notificationList.get(i);
            current.markAsRead();
        }
    }

    @Override
    public synchronized ArrayList<NotificationWrapper> getNotificationList() {
        // return m_notificationList;
        ArrayList<NotificationWrapper> filteredList = new ArrayList<NotificationWrapper>();
        for (NotificationWrapper current : m_notificationList) {
            Device d = DeviceManagerImpl.getInstance().getDevice(current.getNotification().getAppId());
            if (d != null && d.getStatus() != DeviceStatus.GONE) {
                filteredList.add(current);
            }
        }
        return filteredList;
    }

    @Override
    public ArrayList<NotificationWrapper> getNotificationList(UUID deviceId) {
        if (deviceId == null) {
            return getNotificationList();
        }

        Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
        if (d == null || d.getStatus() == DeviceStatus.GONE) {
            return new ArrayList<NotificationWrapper>();
        }
        synchronized (this) {
            ArrayList<NotificationWrapper> filteredList = new ArrayList<NotificationWrapper>();
            for (NotificationWrapper current : m_notificationList) {
                if (deviceId.equals(current.getNotification().getAppId())) {
                    filteredList.add(current);
                }
            }
            return filteredList;
        }
    }

    public synchronized void removeNotificationByMessageID(int messageID) {

        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return;
        }
        for (NotificationWrapper notificationWrapper : m_notificationList) {
            if (notificationWrapper.getNotification().getMessageId() == messageID) {
                m_notificationList.remove(notificationWrapper);
                break;
            }
        }

    }

    @Override
    public synchronized NotificationWrapper getNotificationByMessageID(int messageID) {

        if (m_notificationList == null || m_notificationList.isEmpty()) {
            return null;
        }

        for (NotificationWrapper notificationWrapper : m_notificationList) {
            if (notificationWrapper.getNotification().getMessageId() == messageID) {
                return notificationWrapper;
            }
        }
        return null;
    }

    /**
     * Starts receiver functionality
     */
    @Override
    public void startReceiver() {
        try {
            NotificationService.getInstance().initReceive(AjManager.getInstance().getBusAttachment(), this);
        } catch (NotificationServiceException nse) {
            Log.e(TAG, "NotificationService receiver failed, Error: " + nse.getMessage());
        }
    }

    /**
     * Unregister transportConsumerChannel BusObject and SignalHandler
     */
    @Override
    public void stopReceiver() {

        try {
            // NotificationService.getInstance().shutdownReceiver();
            NotificationService.getInstance().shutdown();
        } catch (NotificationServiceException nse) {
            Log.e(TAG, "NotificationService failed to shutdown, Error: " + nse.getMessage());
        }
    }

}
