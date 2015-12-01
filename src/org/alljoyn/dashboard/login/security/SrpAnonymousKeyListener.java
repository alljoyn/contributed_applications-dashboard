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
package org.alljoyn.dashboard.login.security;

import org.alljoyn.bus.AuthListener;
import org.alljoyn.services.common.DefaultGenericLogger;
import org.alljoyn.services.common.utils.GenericLogger;

public class SrpAnonymousKeyListener implements AuthListener
{

    private final String TAG = "DASHBOARD_SrpAnonymousKeyListener";
    // ---------------- AuthListener Interface -------------------- //
    /*
     * Any time a method call comes in on a secure interface AllJoyn checks to see if it already
     * knows how to decrypt the contents of the method call.  If it cannot decrypt the method call,
     * the requested method call is called.
     * 
     *  ** mechanism = The Authentication mechanism specified (ALLJOYN_SRP_KEYX, ALLJOYN_SRP_LOGON or ALLJOYN_RSA_KEYX)
     *  ** peer = the well-known-name or unique name of the peer that initiated the Authentication request.
     *  ** count = A count of the number of authentication request attempts made.
     *     The count for the first Authentication request starts at 1
     *  ** userName = The user name of the user making the authentication request.
     *     A user name is not required for all forms of authentications and may be an empty string.
     *  ** AuthRequest[] The specific type of authentication information being requested.  Depending on what type
     *     of authentication mechanism is being used, one or more of these requests may be made:
     * CertificateRequest (RSA key exchange)
     * LogonEntryRequest (SRP logon)
     * PasswordRequest (SRP key exchange, SRP logon, or RSA key exchange)
     * PrivateKeyRequest (RSA key exchange)
     * UserNameRequest (SRP logon)
     * VerifyRequest (RSA key exchange)
     */
    public static String KEY_STORE_FINE_NAME;

    AuthPasswordHandler m_passwordHandler;
    private GenericLogger m_logger;

    public SrpAnonymousKeyListener(AuthPasswordHandler passwordHandler, GenericLogger logger)
    {
        m_logger = logger;
        if (m_logger == null)
        {
            m_logger =  new DefaultGenericLogger();
        }
        m_passwordHandler = passwordHandler;
        //		KEY_STORE_FINE_NAME = keyStoreFileName;
        //		getApplicationContext().getFileStreamPath("alljoyn_keystore").getAbsolutePath();
    }

    /* there is a single AuthListener per authentication mechanism and each is shared between all incoming
     * (as a server) and outgoing (as a client) calls and it cannot be unregistered.
     * As a service (look at Code 29 p37): when the requested() callback is invoked the peerName will
     * be set as the BusAttachment unique name and the AuthRequest will be cast to PasswordRequest and
     * set the passphrase to the devicePassPhrase obtained from the Config store.
     * As a client (look at Code 30 p38): when the requested() callback is invoked the peerName will be
     * set as the WKN of the intended service so the AuthRequest will be cast to PasswordRequest and the
     * passphrase to the corresponding password from the device list store.
     */
    @Override
    public boolean requested(String mechanism, String peer, int count, String userName,  AuthRequest[] requests)
    {
        m_logger.info(TAG, " ** " + "requested, mechanism = " + mechanism + " peer = " + peer);
        if (!mechanism.equals("ALLJOYN_PIN_KEYX") && !mechanism.equals("ALLJOYN_SRP_KEYX") && !mechanism.equals("ALLJOYN_ECDHE_PSK"))
        {
            return false;
        }
        else
        {
            if (!(requests[0] instanceof PasswordRequest))
            {
                return false;
            }
            char [] pinCode = {};
            // if pincode not set for this peer, the function will return null, at that case, use the default one.
            if (m_passwordHandler != null && m_passwordHandler.getPassword(peer)!= null)
            {
                pinCode = m_passwordHandler.getPassword(peer);
            }
            ((PasswordRequest) requests[0]).setPassword(pinCode);
            return true;
        }
    }

    /*
     * Once the authentication has finished the completed call-back method is called.
     *	mechanism = The authentication mechanism that was just completed
     *	authPeer = The peerName (well-known name or unique name)
     *	authenticated = A boolean variable indicating if the authentication attempt completed successfuly.
     */
    @Override
    public void completed(String mechanism, String authPeer, boolean authenticated)
    {
        /*if (! authenticated)
		{
			m_logger.info(TAG, " ** " + authPeer + " failed to authenticate");
			return;
		}
		m_logger.info(TAG, " ** " + authPeer + " successfully authenticated");*/

        m_passwordHandler.completed(mechanism, authPeer, authenticated);
    }

}
