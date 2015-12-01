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
import org.alljoyn.dashboard.logic.WifiUtil;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.RingtonePreference;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceClickListener
{
    protected AlertDialog m_alertDialog = null;

    private boolean m_goingBack = false;
    private boolean m_goingToNewPage = false;

    private boolean m_isShowing = false;

    private final String TAG = getClass().getName();

    private String m_version = "";
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        m_version = UIUtil.getAppVersion(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        addPreferencesFromResource(R.xml.settings);
        setContentView(R.layout.page_settings);

        final TextView m_titleText = (TextView) findViewById(R.id.navtitleback_title_text);
        m_titleText.setText(R.string.application_settings);

        final View titleTextLayout = findViewById(R.id.navtitleback_title_block);
        titleTextLayout.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                markGoingBack();
                finish();
            }
        });

        final Preference homeWifiSetup = findPreference(getString(R.string.settings_setup_home_wifi_key));
        final CheckBoxPreference notifications = (CheckBoxPreference) findPreference(getString(R.string.settings_notifications_key));
        final RingtonePreference ringtone = (RingtonePreference) findPreference(getString(R.string.settings_ringtone_key));
        final CheckBoxPreference vibrate = (CheckBoxPreference) findPreference(getString(R.string.settings_vibrate_key));
        final Preference changelog = findPreference(getString(R.string.settings_changelog_key));
        final Preference openSourceLicense = findPreference(getString(R.string.settings_third_party_licenses_key));

        homeWifiSetup.setOnPreferenceClickListener(this);
        notifications.setOnPreferenceClickListener(this);
        ringtone.setOnPreferenceClickListener(this);
        vibrate.setOnPreferenceClickListener(this);
        changelog.setOnPreferenceClickListener(this);
        openSourceLicense.setOnPreferenceClickListener(this);

        notifications.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue)
            {
                if (newValue.toString().equals("true"))
                {
                    ringtone.setEnabled(true);
                    vibrate.setEnabled(true);
                }
                else
                {
                    ringtone.setEnabled(false);
                    vibrate.setEnabled(false);
                }
                return true;
            }
        });

        changelog.setSummary(getString(R.string.settings_changelog_summary) + " " +m_version );
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume()");
        super.onResume();

        m_isShowing = true;
        verifyWifiIsEnabled();
    }

    @Override
    protected void onPause()
    {
        if (m_goingBack) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
        if (m_goingToNewPage) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        m_goingToNewPage = false;
        m_goingBack = false;

        m_isShowing = false;

        Log.v(TAG, "onPause()");
        super.onPause();

        dismissAlertDialog();
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");
        super.onDestroy();

        m_goingBack = false;
        m_goingToNewPage = false;
    }

    @Override
    public void onBackPressed() {
        markGoingBack();
        super.onBackPressed();
    }

    public void markGoingBack() {
        Log.v(TAG, "markGoingBack()");
        m_goingBack = true;
    }

    public void markGoingToNewPage() {
        Log.v(TAG, "markGoingToNewPage()");
        m_goingToNewPage = true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference)
    {
        final String key = preference.getKey();

        if (key.equals(getString(R.string.settings_setup_home_wifi_key)))
        {
            Intent startup = new Intent(SettingsActivity.this, SetupHomeWiFiActivity.class);
            startActivity(startup);
            return true;
        }
        else if (key.equals(getString(R.string.settings_changelog_key)))
        {
            // no need to use our custom alert dialog...
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(true);
            builder.setMessage(getString(R.string.settings_changelog_text, m_version));
            builder.create().show();
            return true;
        }
        else if (key.equals(getString(R.string.settings_third_party_licenses_key)))
        {
            final Intent intent = new Intent(SettingsActivity.this, LicenseActivity.class);
            intent.putExtra(LicenseActivity.THIRD_PARTY_LICENCE_EXTRA, true);
            startActivity(intent);

            markGoingToNewPage();
            return true;
        }
        return false;
    }

    protected void verifyWifiIsEnabled()
    {
        if (!WifiUtil.getInstance().isWifiEnable())
        {
            final String appName = getString(getApplicationInfo().labelRes);

            launchBasicAlertDialog(getString(R.string.alert_title_wi_fi_is_disabled),
                    String.format(getString(R.string.alert_message_wi_fi_is_disabled), appName),
                    getString(R.string.quit),
                    new Runnable()
            {
                @Override
                public void run()
                {
                    dismissAlertDialog();

                    // System.exit(0) isn't working well.
                    // instead just send Android to home. Mimic the home button.
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            },
            getString(R.string.enable),
            new Runnable()
            {
                @Override
                public void run()
                {
                    WifiUtil.getInstance().enableWifi(true);
                    dismissAlertDialog();
                }
            },
            new OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    dismissAlertDialog();

                    // System.exit(0) isn't working well.
                    // instead just send Android to home. Mimic the home button.
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    protected void launchBasicAlertDialog(String title, String message, String posButton, final Runnable onPosClicked, String negButton,
            final Runnable onNegClicked, final OnCancelListener onCancel)
    {
        launchBasicAlertDialog(title, 0, message, posButton, onPosClicked, null, null, negButton, onNegClicked, onCancel, null);
    }

    protected void launchBasicAlertDialog(String title, int iconId, String message, String posButton, final Runnable onPosClicked, String neutralButton,
            final Runnable onNeutralClicked, String negButton, final Runnable onNegClicked, final OnCancelListener onCancel, final Runnable  onBackClicked)
    {
        // get dialog layout
        final View dialogLayout = getLayoutInflater().inflate(R.layout.alert_dialog, null);
        final TextView titleView = (TextView) dialogLayout.findViewById(R.id.alert_title_text);
        final TextView messageView = (TextView) dialogLayout.findViewById(R.id.alert_msg_text);
        final Button posView = (Button) dialogLayout.findViewById(R.id.alert_button_pos);
        final Button neutralView = (Button) dialogLayout.findViewById(R.id.alert_button_neutral);
        final Button negView = (Button) dialogLayout.findViewById(R.id.alert_button_neg);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogLayout);
        builder.setCancelable(true);

        if (title != null)
        {
            titleView.setText(title);
            if (iconId != 0)
            {
                titleView.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0, 0);
                titleView.setPadding(titleView.getPaddingLeft(),
                        titleView.getPaddingTop(),
                        titleView.getPaddingLeft()*4, // required to center the text in the header (the padding
                        // to the left of the text is set to 10dp, but if the icon
                        // is present it extends the padding due to its width of
                        // 23dp and additional 7dp padding).
                        // (10dp + 23dp 7dp) = (10dp * 4)
                        titleView.getPaddingBottom());
            }
        }
        else
        {
            titleView.setVisibility(View.GONE);
        }

        if (message != null)
        {
            messageView.setText(message);
        }
        else
        {
            messageView.setVisibility(View.GONE);
        }

        if (posButton != null)
        {
            posView.setText(posButton);
            posView.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (onPosClicked != null)
                    {
                        onPosClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        }
        else
        {
            posView.setVisibility(View.GONE);
        }

        if (neutralButton != null)
        {
            neutralView.setText(neutralButton);
            neutralView.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (onNeutralClicked != null)
                    {
                        onNeutralClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        }
        else
        {
            neutralView.setVisibility(View.GONE);
        }

        if (negButton != null)
        {
            negView.setText(negButton);
            negView.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (onNegClicked != null)
                    {
                        onNegClicked.run();
                    }

                    dismissAlertDialog();
                }
            });
        }
        else
        {
            negView.setVisibility(View.GONE);
        }

        if (onCancel != null)
        {
            builder.setOnCancelListener(onCancel);
        }

        if (onBackClicked!=null){
            builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode,KeyEvent arg2) {

                    if(keyCode == KeyEvent.KEYCODE_BACK){
                        onBackClicked.run();
                        return true;
                    }
                    // TODO Auto-generated method stub
                    return false;
                }
            });
        }

        // don't allow alert dialogs to stack on top of each other
        dismissAlertDialog();

        m_alertDialog = builder.create();
        if (m_isShowing) {
            m_alertDialog.show();
        } else {
            Log.d(TAG, "Alert dialog was created after activity was paused. Not showing it");
            m_alertDialog = null;
        }
    }

    protected void dismissAlertDialog()
    {
        if (m_alertDialog != null)
        {
            m_alertDialog.dismiss();
            m_alertDialog = null;
        }
    }

}
