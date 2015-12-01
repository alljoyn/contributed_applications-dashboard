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

import org.alljoyn.ns.Notification;

public class NotificationWrapper
{

    public enum ReadStatus
    {
        NEW,
        CLEARED,
        READ,
    }

    private ReadStatus m_readStatus;

    long m_time;
    Notification m_notification;

    public NotificationWrapper(Notification notification, long time)
    {
        m_notification = notification;
        m_readStatus = ReadStatus.NEW;
        m_time = time;
    }

    public long getTime()
    {
        return m_time;
    }

    public Notification getNotification()
    {
        return m_notification;
    }

    public void markAsCleared()
    {
        // if a notification is "read", that also implies it's cleared
        if (m_readStatus != ReadStatus.READ)
        {
            m_readStatus = ReadStatus.CLEARED;
        }
    }

    public void markAsRead()
    {
        m_readStatus = ReadStatus.READ;
    }

    public ReadStatus getReadStatus()
    {
        return m_readStatus;
    }

}
