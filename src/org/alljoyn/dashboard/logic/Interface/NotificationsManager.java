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

import java.util.ArrayList;
import java.util.UUID;


public interface NotificationsManager
{

    public int getUnclearedNotificationsCount();

    public int getUnreadNotificationsCount();

    public int getUnreadNotificationsCount(UUID m_deviceId);

    //	public void removeNotificationsForDevice(UUID deviceID);

    public void markAllNotificationsAsCleared();

    public void markAllNotificationsAsRead();

    public void markAllNotificationsAsRead(UUID deviceId);

    public ArrayList<NotificationWrapper> getNotificationList();

    public ArrayList<NotificationWrapper> getNotificationList(UUID m_deviceId);
    /**
     * Starts receiver functionality
     */
    public void startReceiver();

    /**
     * Unregister transportConsumerChannel BusObject and SignalHandler
     */
    public void stopReceiver();

    public NotificationWrapper getNotificationByMessageID(int messageID);

}
