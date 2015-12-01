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
import org.alljoyn.dashboard.ui.util.UIUtil;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

public class LicenseActivity extends BaseActivity
{
    public static final String TEXT_RES_ID_EXTRA = "text_res_id";
    public static final String THIRD_PARTY_LICENCE_EXTRA = "show_thirdpartylicences_id";
    public static final String SHOW_LICENCE_EXTRA = "show_license_id";
    private ViewGroup m_licenseLayout = null;

    private TextView m_titleText = null;
    private TextView m_text = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        boolean isTPL=false;
        boolean isLicence=false;
        Intent intent = getIntent();
        Bundle extras=intent.getExtras();
        if (extras != null)
        {
            isTPL = getIntent().getBooleanExtra(THIRD_PARTY_LICENCE_EXTRA, false);
            isLicence = getIntent().getBooleanExtra(SHOW_LICENCE_EXTRA, false);
        }

        m_licenseLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_license, null);
        m_titleText = (TextView) m_licenseLayout.findViewById(R.id.navtitleback_title_text);
        m_text = (TextView) m_licenseLayout.findViewById(R.id.license_text);

        setContentView(m_licenseLayout);

        final OnClickListener onBack = new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                markGoingBack();
                LicenseActivity.this.finish();
            }
        };

        View view = m_licenseLayout.findViewById(R.id.navtitleback_title_block);
        view.setOnClickListener(onBack);

        final int textId = getIntent().getIntExtra(TEXT_RES_ID_EXTRA, 0);

        if (textId > 0)
        {
            m_text.setText(textId);
        }

        if (isTPL)
        {
            m_titleText.setText(R.string.settings_third_party_licenses_title);
            m_text.setText(UIUtil.readRawTextFile(R.raw.thirdpartylicenses, this));
        }
    }
}
