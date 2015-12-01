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

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UISharedPreferencesManager;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

public class SplashBackgroundActivity extends BaseActivity
{
    private ViewGroup m_welcomeLayout = null;
    private boolean m_showSetHomeWifi = true;

    final int setupHome = 1222;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        m_showSetHomeWifi = getIntent().getBooleanExtra(IntentExtraKeys.EXTRA_BOOLEAN_KEY, true);
        if (intent.hasExtra(IntentExtraKeys.EXTRA_BOOLEAN_KEY)){
            m_showSetHomeWifi = getIntent().getBooleanExtra(IntentExtraKeys.EXTRA_BOOLEAN_KEY, true);
        }
        m_welcomeLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_welcome, null);

        setContentView(m_welcomeLayout);

        showSetupHomeWiFiActivity();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    private void showSetupHomeWiFiActivity()
    {
        String name = UISharedPreferencesManager.getStoredNetworkName();
        if ((name == null  || name.length() == 0)&& m_showSetHomeWifi)
        {
            Intent startup = new Intent(SplashBackgroundActivity.this, SetupHomeWiFiActivity.class);
            startup.putExtra(IntentExtraKeys.EXTRA_BOOLEAN_KEY, true);
            startActivityForResult(startup, setupHome);
        }
        else
        {
            final Intent launchIntent = new Intent(SplashBackgroundActivity.this, NearbyDevicesActivity.class);
            startActivity(launchIntent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("TAG", "onActivityResult requestCode = " + requestCode);
        if (requestCode == setupHome){
            finish();
        }
    }


}
