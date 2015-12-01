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

import java.util.Collection;
import java.util.Map;

import org.alljoyn.about.AboutService;
import org.alljoyn.about.AboutServiceImpl;
import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.alljoyn.bus.alljoyn.DaemonInit;
import org.alljoyn.config.ConfigServiceImpl;
import org.alljoyn.ioe.controlpanelservice.ControlPanelException;
import org.alljoyn.ioe.controlpanelservice.ControlPanelService;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.login.security.SrpAnonymousKeyListener;
import org.alljoyn.services.android.utils.AndroidLogger;
import org.alljoyn.services.common.AnnouncementHandler;
import org.alljoyn.services.common.BusObjectDescription;

import android.content.Context;
import android.util.Log;

public class AjManager implements AboutListener {

    private final String TAG = "DASHBOARD_AjManager";
    private Context m_context;
    private static AjManager current;

    private static final String SESSIONLESS_MATCH_RULE = "sessionless='t',type='error'";

    /**
     * The daemon should advertise itself "quietly" (directly to the calling
     * port) This is to reply directly to a TC looking for a daemon
     */
    private static final String DAEMON_QUIET_PREFIX = "quiet@";
    private static BusAttachment m_busAttachment;
    private AboutService m_aboutClient;
    private String m_deamonName;

    public static AjManager getInstance() {
        if (current == null) {
            current = new AjManager();
        }
        return current;
    }

    protected void init(Context c, String keyStorekeyStoreFileName) {
        m_context = c;

        // prepare daemon
        boolean b = DaemonInit.PrepareDaemon(m_context.getApplicationContext());
        Log.i(TAG, "connectToAJ PrepareDaemon returned " + b);

    }

    public void connectToAJ() {

        Log.i(TAG, "connectToAJ start");
        if (m_context == null) {
            Log.e(TAG, "Failed to connect AJ, m_context == null !!");
            return;
        }

        // create the bus attchment
        m_busAttachment = new BusAttachment(m_context.getPackageName(), BusAttachment.RemoteMessage.Receive);

        // Debug Alljoyn daemon
        // m_busAttachment.setDaemonDebug("ALL", 7);
        // m_busAttachment.setLogLevels("ALLJOYN=7");
        // m_busAttachment.setLogLevels("NETWORK=7");
        // m_busAttachment.setLogLevels("TCP=7");
        // m_busAttachment.useOSLogging(true);

        Status busConnectStatus = m_busAttachment.connect();
        Log.i(TAG, "m_busAttachment.connect() returned with status " + busConnectStatus);

        // set the daemon name to broadcast for daemonless boards
        m_deamonName = "org.alljoyn.BusNode.d" + m_busAttachment.getGlobalGUIDString();

        // request the name
        int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;
        Status reqStatus = m_busAttachment.requestName(m_deamonName, flag);
        Log.i(TAG, "m_busAttachment.requestName deamon name = " + m_deamonName + "flag = ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE returned with status " + reqStatus);
        if (reqStatus == Status.OK) {
            // advertise the name with a quite prefix for TC to find it
            Status adStatus = m_busAttachment.advertiseName(DAEMON_QUIET_PREFIX + m_deamonName, SessionOpts.TRANSPORT_ANY);
            Log.i(TAG, "m_busAttachment.advertiseName returned with status " + reqStatus);
            if (adStatus != Status.OK) {
                m_busAttachment.releaseName(m_deamonName);
                Log.w(TAG, "failed to advertise daemon name " + m_deamonName);
            } else {
                Log.d(TAG, "Succefully advertised daemon name " + m_deamonName);
            }
        }

        try {
            m_aboutClient = AboutServiceImpl.getInstance();
            m_aboutClient.setLogger(Util.getGenericLogger());
            Log.i(TAG, "connectToAJ  startAboutClient");
            m_aboutClient.startAboutClient(m_busAttachment);

            Log.i(TAG, "connectToAJ addAnnouncementHandler");
            //m_aboutClient.addAnnouncementHandler(this, null);
            m_busAttachment.registerAboutListener(this);
            m_busAttachment.whoImplements(new String[] {"org.alljoyn.About"});
            
            // config
            Log.i(TAG, "connectToAJ startConfigClient");
            ConfigServiceImpl.getInstance().startConfigClient(m_busAttachment);
            // Add auth listener - needed for Config and Onboarding
            String keyStoreFileName = m_context.getFileStreamPath("alljoyn_keystore").getAbsolutePath();

            SrpAnonymousKeyListener m_authListener = new SrpAnonymousKeyListener(new AlljoynOnAuthPasswordHandler(), new AndroidLogger());
            Status authStatus = m_busAttachment.registerAuthListener("ALLJOYN_SRP_KEYX ALLJOYN_ECDHE_PSK", m_authListener, keyStoreFileName);
            Log.d(TAG, "BusAttachment.registerAuthListener status = " + authStatus);
            if (authStatus != Status.OK) {
                Log.e(TAG, "Failed to ");
            }

            // Initialize ControlPanelService
            ControlPanelService controlPanelService = ControlPanelService.getInstance();
            try {
                controlPanelService.init(m_busAttachment);
                Log.d(TAG, "Initialized ControlPanelService with BusAttachment " + m_busAttachment.getUniqueName());
            } catch (ControlPanelException e) {
                Log.e(TAG, "Unable to start ControlPanelService, Error: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "fail to init Device manager services", e);
        }
        Log.i(TAG, " connectToAJ before init notificationService");
        NotificationsManagerImpl.getInstance().startReceiver();

        // DO the AJ addMatch.
        Status status = m_busAttachment.addMatch(SESSIONLESS_MATCH_RULE);
        Log.i(TAG, "BusAttachment.addMatch() status = " + status);

        Log.i(TAG, " connectToAJ Done");
    }

    public void disconnectFromAJ() {
        /*
         * It is important to unregister the BusObject before disconnecting from
         * the bus. Failing to do so could result in a resource leak.
         */
        Log.i(TAG, "disconnectFromAJ ...... ");
        try {

            if (m_busAttachment != null && m_busAttachment.isConnected()) {
                DeviceManagerImpl.getInstance().stopPinging();

                Collection<Device> devices = DeviceManagerImpl.getInstance().getDevices();
                if (devices != null) {
                    for (Device d : devices) {
                        ((DeviceImpl) d).resetServices();
                    }
                }

                Status s = m_busAttachment.removeMatch(SESSIONLESS_MATCH_RULE);
                Log.i(TAG, "disconnectFromAJ removeMatch returned with status " + s);
//                if (m_aboutClient != null) {
//                    m_aboutClient.stopAboutClient();
//                    Log.i(TAG, "disconnectFromAJ AboutClient Stoped");
//                } else {
//                    Log.i(TAG, "disconnectFromAJ AboutClient == null");
//                }
                m_busAttachment.unregisterAboutListener(this);
                
                ConfigServiceImpl.getInstance().stopConfigClient();
                Log.i(TAG, "disconnectFromAJ ConfigClient Stoped");
                NotificationsManagerImpl.getInstance().stopReceiver();

                ControlPanelService.getInstance().shutdown();

                s = m_busAttachment.cancelAdvertiseName(DAEMON_QUIET_PREFIX + m_deamonName, SessionOpts.TRANSPORT_ANY);
                Log.i(TAG, "disconnectFromAJ cancelAdvertiseName  returned with status " + s);
                s = m_busAttachment.releaseName(m_deamonName);
                Log.i(TAG, "disconnectFromAJ releaseName  returned with status " + s);

                m_busAttachment.disconnect();

                m_busAttachment = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error when disconnectFromAJ ");
            e.printStackTrace();
        }
        Log.i(TAG, "disconnected !!!! ");
    }

    public BusAttachment getBusAttachment() {
        return m_busAttachment;
    }

//    public AboutService getAboutClient() {
//        return m_aboutClient;
//    }

    protected String getDeamonRealm() {
        return m_deamonName;// m_deamonRealm;
    }


    @Override
    public void announced(final String busName, int version, final short port, final AboutObjectDescription[] objectDescriptions, final Map<String, Variant> aboutData) {
        Log.d("DASHBOARD_DeviceManagerImpl", "AJMANAGER ANNOUNCED: "+busName);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ((DeviceManagerImpl) DeviceManagerImpl.getInstance()).onAnnouncement(busName, port, objectDescriptions, aboutData);
            }
        }).start();
    }
}
