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
package org.alljoyn.dashboard.ui.activity;

import java.util.Timer;
import java.util.TimerTask;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.Interface.IntentActions;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;

public class WelcomeActivity extends BaseActivity
{
    private ViewGroup m_welcomeLayout = null;

    private boolean m_launchedEULA = false;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        m_welcomeLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_welcome, null);

        setContentView(m_welcomeLayout);

        final Activity instance = this;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!instance.isFinishing()) {
                    m_launchedEULA = true;

                    final Intent intent = new Intent(WelcomeActivity.this, SplashBackgroundActivity.class);
                    WelcomeActivity.this.startActivity(intent);

                    final Timer timer2 = new Timer();
                    timer2.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // AJAPPS-49: allow this activity to capture the back key before the EULA loads
                            finish();
                        }
                    }, 3000);
                }
            }
        }, 400);
    }

    @Override
    protected void verifyWifiIsEnabled(){}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (KeyEvent.KEYCODE_BACK == keyCode)
        {
            if (handleBack())
            {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed()
    {
        if (!handleBack())
        {
            super.onBackPressed();
        }
    }

    protected boolean handleBack()
    {
        // AJAPPS-49: make sure we exit the app even if the the request to
        // launch the EULA has already occurred
        finish();
        if (m_launchedEULA)
        {
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    final Intent intent = new Intent(IntentActions.AJ_ON_USER_EXIT_APP);
                    sendBroadcast(intent);
                }
            }, 1000);
        }

        return false;
    }

}
