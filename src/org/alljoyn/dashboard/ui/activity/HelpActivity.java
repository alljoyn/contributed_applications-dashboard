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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class HelpActivity extends BaseActivity
{

    public static final String URL_EXTRA = "help_url";

    private class HelloWebViewClient extends WebViewClient
    {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            view.loadUrl(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url)
        {
            new Timer().schedule(new TimerTask()
            {
                @Override
                public void run() {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            View mProgressBarLayOut=findViewById(R.id.progressbarLayout);
                            mProgressBarLayOut.setVisibility(View.GONE);
                            WebView  mWebView = (WebView) findViewById(R.id.webview);
                            mWebView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }, 100);
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.page_help);
        TextView m_titleText = (TextView) findViewById(R.id.navtitleback_title_text);
        m_titleText.setText(R.string.help_title);
        View view=findViewById(R.id.navtitleback_title_block);

        view.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                markGoingBack();
                finish();
            }
        });

        Intent intent = getIntent();
        WebView  mWebView = (WebView) findViewById(R.id.webview);

        Bundle extras=intent.getExtras();
        if (extras != null)
        {
            String Url=(String)extras.get(URL_EXTRA);
            if (!Url.toLowerCase().startsWith("http://") && !Url.toLowerCase().startsWith("https://"))
            {
                Url="http://"+Url;
            }

            mWebView.loadUrl(Url);
        }

        mWebView.setWebViewClient(new HelloWebViewClient());
    }

}
