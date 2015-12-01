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

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.config.client.ConfigClientImpl;
import org.alljoyn.services.common.ServiceAvailabilityListener;

public class ConfigClientHack extends ConfigClientImpl
{
    public final static String TAG = ConfigClientHack.class.getName();
    
    public ConfigClientHack(String deviceName, BusAttachment bus, ServiceAvailabilityListener serviceAvailabilityListener, short port)
    {
        super(deviceName, bus, serviceAvailabilityListener, port);
    }

    @Override
    public SessionOpts createSessionOpts() {
        SessionOpts sessionOpts   = new SessionOpts();
        sessionOpts.traffic       = SessionOpts.TRAFFIC_MESSAGES;
        sessionOpts.isMultipoint  = false;
        sessionOpts.proximity     = SessionOpts.PROXIMITY_ANY;
        sessionOpts.transports    = SessionOpts.TRANSPORT_ANY;
        sessionOpts.transports    &= (~SessionOpts.TRANSPORT_UDP);
        return sessionOpts;
    }//createSessionOpts
}
