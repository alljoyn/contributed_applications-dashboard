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

import java.util.Arrays;
import java.util.UUID;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.logic.DeviceManagerImpl;
import org.alljoyn.dashboard.logic.Interface.Device;
import org.alljoyn.dashboard.logic.Interface.Device.DeviceAction;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse;
import org.alljoyn.dashboard.logic.Interface.DeviceResponse.ResponseCode;
import org.alljoyn.dashboard.logic.Interface.IntentExtraKeys;
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

public class EnterDevicePasscodeActivity extends BaseActivity
{
    private final static String TAG = "DASHBOARD_EnterDevicePasscodeActivity";

    private ViewGroup m_enterDevicePasscodeLayout = null;

    private TextView m_titleText = null;
    private TextView m_instuctionText;
    private CheckBox m_showPasswords;
    private EditText m_entryNew = null;
    private EditText m_entryConfirm = null;
    private EditText m_entryCurrent = null;
    private Button m_cancelButton;
    private Button m_confirmButton;

    private UUID m_deviceId;
    private TextView m_errorInfo = null;

    char[] m_newPassword;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        m_deviceId = (UUID) getIntent().getSerializableExtra(IntentExtraKeys.EXTRA_DEVICE_ID);
        if (m_deviceId == null)
        {
            Log.e(TAG, "m_deviceId is NULL - go to the NavigationActivity by default.");
            startActivity(new Intent(EnterDevicePasscodeActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }
        Device device = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
        if (device == null)
        {
            Log.e(TAG, "m_device is NULL - go to the NavigationActivity by default.");
            startActivity(new Intent(EnterDevicePasscodeActivity.this, NearbyDevicesActivity.class));
            finish();
            return;
        }
        m_enterDevicePasscodeLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_enter_device_passcode, null);

        // add UI elements
        m_titleText = (TextView) m_enterDevicePasscodeLayout.findViewById(R.id.navtitleback_title_text);
        m_titleText.setText("Change Device Password");



        m_instuctionText = (TextView) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_title);
        m_instuctionText.setText(getString(R.string.enter_device_password_title, device.getFriendlyName()));
        m_showPasswords = (CheckBox) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_show_password);

        // buttons.
        m_cancelButton =  (Button) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_calcel);
        m_confirmButton =  (Button) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_confirm);
        m_errorInfo = (TextView) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_error_text);

        //textBoxes
        m_entryCurrent =  (EditText) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_entry_current);
        m_entryNew = (EditText) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_entry_new);
        m_entryConfirm = (EditText) m_enterDevicePasscodeLayout.findViewById(R.id.enter_device_passcode_entry_confiem);

        TextWatcher textWatcher = new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                if(m_entryCurrent.getText()== null || m_entryNew.getText() == null || m_entryConfirm.getText() == null
                        || m_entryCurrent.getText().length() == 0 || m_entryNew.getText().length() == 0 || m_entryConfirm.getText().length() == 0){
                    m_confirmButton.setEnabled(false);
                }
                else{
                    m_confirmButton.setEnabled(true);
                }
            }
        };
        m_entryCurrent.addTextChangedListener(textWatcher);
        m_entryNew.addTextChangedListener(textWatcher);
        m_entryConfirm.addTextChangedListener(textWatcher);


        if (m_showPasswords.isChecked())
        {
            UIUtil.removeHashFromEditText(m_entryCurrent);
            UIUtil.removeHashFromEditText(m_entryNew);
            UIUtil.removeHashFromEditText(m_entryConfirm);
        }
        else
        {
            UIUtil.hashEditTextForPasswords(m_entryCurrent);
            UIUtil.hashEditTextForPasswords(m_entryNew);
            UIUtil.hashEditTextForPasswords(m_entryConfirm);
        }

        m_showPasswords.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                if (buttonView.isChecked()){
                    UIUtil.removeHashFromEditText(m_entryCurrent);
                    UIUtil.removeHashFromEditText(m_entryNew);
                    UIUtil.removeHashFromEditText(m_entryConfirm);
                }else{
                    UIUtil.hashEditTextForPasswords(m_entryCurrent);
                    UIUtil.hashEditTextForPasswords(m_entryNew);
                    UIUtil.hashEditTextForPasswords(m_entryConfirm);
                }
            }
        });
        m_confirmButton.setEnabled(false);
        m_confirmButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                final Device saveDevice = UIUtil.getDevice(m_deviceId);
                if (saveDevice == null)
                {
                    return;
                }
                char[] currentPass = editableToCharArray(m_entryCurrent.getText());
                if (!Arrays.equals(saveDevice.getPassphrase(),currentPass))
                {
                    m_errorInfo.setText("Current Password Incorrect");
                    m_errorInfo.setVisibility(View.VISIBLE);
                    return;
                }
                else if (!Arrays.equals(editableToCharArray(m_entryNew.getText()),editableToCharArray(m_entryConfirm.getText())))
                {
                    m_errorInfo.setText("New Password Does Not Match");
                    m_errorInfo.setVisibility(View.VISIBLE);
                    return;
                }
                m_errorInfo.setVisibility(View.GONE);
                m_newPassword = editableToCharArray(m_entryNew.getText());
                changeThePassword();			}
        });

        // setup
        final OnClickListener onBack = new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                markGoingBack();
                finish();
            }
        };
        //m_titleText.setOnClickListener(onBack);

        View view=m_enterDevicePasscodeLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);

        m_cancelButton.setOnClickListener(onBack);

        setContentView(m_enterDevicePasscodeLayout);
    }

    /*protected void onResume()
	{
		super.onResume();

		final Device device = UIUtil.GetDevice(m_deviceId);

		if (device != null)
		{
			UIUtil.SetDeviceIcon(m_deviceIcon, device.getId());
		}
	}*/


    private char[] editableToCharArray(Editable editable)
    {
        if (editable == null || editable.length() == 0) {
            return new char[0];
        }
        char[] pass = new char[editable.length()];
        editable.getChars(0, editable.length(), pass, 0);
        return pass;
    }

    private void changeThePassword()
    {
        AsyncTask<Void, Void, DeviceResponse> changePass = new AsyncTask<Void, Void, DeviceResponse>() {

            @Override
            protected DeviceResponse doInBackground(Void... params) {
                Device device = DeviceManagerImpl.getInstance().getDevice(m_deviceId);
                return device.setPassphrase(m_newPassword, true);
            }

            @Override
            protected void onPostExecute(DeviceResponse result) {
                super.onPostExecute(result);
                if (result.getStatus() == ResponseCode.Status_OK) {
                    Log.e(TAG, "device password set - go to the NearbyDevicesActivity by default.");
                    markGoingBack();
                    startActivity(new Intent(EnterDevicePasscodeActivity.this, NearbyDevicesActivity.class));
                    finish();
                    return;
                }
                else if (result.getStatus() == ResponseCode.Status_ERROR_CANT_ESTABLISH_SESSION
                        || result.getStatus() == ResponseCode.Status_ERROR_NO_PEER_NAME)
                {
                    Runnable run = new Runnable() {

                        @Override
                        public void run() {
                            Log.e(TAG, "m_deviceId is NULL - go to the NearbyDevicesActivity by default.");
                            markGoingBack();
                            startActivity(new Intent(EnterDevicePasscodeActivity.this, NearbyDevicesActivity.class));
                            finish();
                        }
                    };
                    launchBasicAlertDialog("ERROR", "Can't change password.",getString(R.string.dismiss), run, null);
                }
                else if(result.getStatus() == ResponseCode.Status_ERROR_MAX_SIZE_EXCEEDED){
                    launchBasicAlertDialog("ERROR", "Password is too long, please try again.",getString(R.string.dismiss), null, null);
                }
                else
                {
                    //wait for password error intent.
                    Log.e(TAG, "error on setPassphrase " + result.getMsg());

                }

            }
        };
        changePass.execute();

    }

    @Override
    protected void onNewDevicePassword(UUID deviceId) {
        Log.i(TAG, "New password saved, retrying change password");
        if (deviceId.equals(m_deviceId))
        {
            Device d = DeviceManagerImpl.getInstance().getDevice(deviceId);
            if (d != null && DeviceAction.SET_PASSWORD.equals(d.getTag(Device.DEVICE_TAG_LAST_ACTION)))
            {
                changeThePassword();
            }
        }
    }

}
