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
/*package org.alljoyn.dashboard.ui.activity;

import java.io.IOException;

import org.alljoyn.dashboard.R;
import org.alljoyn.dashboard.ui.application.AlljoynOnIntentReceiver;

import android.app.ProgressDialog;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ScanQRCodeActivity extends BaseActivity
{
	private final static String TAG = "DASHBOARD_ScanQRCodeActivity";

	private ViewGroup m_scanQRCodeLayout = null;

    private TextView m_titleText = null;
    //private ImageView m_helpButton = null;

    private Camera m_camera;
    private CameraPreview m_cameraPreview = null;
    private ImageView m_scannerBar = null;

    private ProgressDialog m_loadingPopup;


    @Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

	    final String deviceSSID = (String) getIntent().getSerializableExtra(AlljoynOnIntentReceiver.EXTRA_DEVICE_SSID);

	    try
	    {
	    	m_camera = Camera.open();
    		m_cameraPreview = new CameraPreview(this, m_camera);

	    	if (m_camera == null)
	    	{
	    		int i = 0;
	    		final int numCameras = Camera.getNumberOfCameras();
	    		while (m_camera == null && i < numCameras)
	    		{
	    			m_camera = Camera.open(i);
	    		}
	    	}

	    	if (m_camera != null)
	    	{
	    		m_cameraPreview = new CameraPreview(this, m_camera);
	    	}
	    	else
	    	{
		    	launchBasicAlertDialog(getString(R.string.alert_title_camera_error), getString(R.string.alert_message_camera_error), getString(R.string.okay), null, null);
	    	}
	    }
	    catch (Exception e)
	    {
	    	Log.e(TAG, "fail to create CameraPreview", e);

	    	launchBasicAlertDialog(getString(R.string.alert_title_camera_error), getString(R.string.alert_message_camera_error), getString(R.string.okay), null, null);
	    }

	    m_scanQRCodeLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.page_scan_qr_code, null);

	    // add UI elements
	    m_titleText = (TextView) m_scanQRCodeLayout.findViewById(R.id.navtitleback_title_text);
	    //m_helpButton = (ImageView) m_scanQRCodeLayout.findViewById(R.id.scan_qr_code_help_button);
	    m_scannerBar = (ImageView) m_scanQRCodeLayout.findViewById(R.id.scan_qr_code_scanner_bar);

	    // setup
        final FrameLayout preview = (FrameLayout) m_scanQRCodeLayout.findViewById(R.id.camera_preview);
        if (m_cameraPreview != null)
        {
            preview.addView(m_cameraPreview, 0);
        }

        //TODO: Implement QR scan process that will go to next activity, add device to my devices, and store network settings to device object

		final OnClickListener onBack = new OnClickListener()
    	{
    		@Override
    		public void onClick(View arg0)
    		{
    			ScanQRCodeActivity.this.finish();

    			handleBack();
    		}
    	};

	    m_titleText.setOnClickListener(onBack);
	    m_titleText.setText(R.string.scan_qr_code_title);

	    m_scannerBar.setVisibility(View.INVISIBLE);

	    /*m_helpButton.setOnClickListener(new OnClickListener()
    	{
    		@Override
    		public void onClick(View view)
    		{
    			final Intent helpIntent = new Intent(ScanQRCodeActivity.this, HelpActivity.class);
    			//TODO: add help URL - helpIntent.putExtra(HelpActivity.URL_EXTRA, helpUrl);
    			ScanQRCodeActivity.this.startActivity(helpIntent);
    		}
    	});*

		setContentView(m_scanQRCodeLayout);
	}

    protected void onStop()
    {
    	super.onStop();

    	if (m_cameraPreview != null && m_cameraPreview.m_surfaceCamera != null)
        {
    		m_cameraPreview.m_surfaceCamera.release();
        }

    	dismissLoadingPopup();
    }

    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
    {

        private SurfaceHolder m_holder;
        private Camera m_surfaceCamera;
        private int m_size;

        public CameraPreview(Context context, Camera camera)
        {
            super(context);
            m_surfaceCamera = camera;

            m_holder = getHolder();
            m_holder.addCallback(this);
            m_holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            m_size = Math.min(height, width);
            setMeasuredDimension(m_size, m_size);
        }

        public void surfaceCreated(SurfaceHolder holder)
        {
            try
            {
            	m_surfaceCamera.setPreviewDisplay(holder);
            	m_surfaceCamera.setDisplayOrientation(90);
            	m_surfaceCamera.startPreview();

        	    m_scannerBar.setVisibility(View.VISIBLE);

        	    final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) m_scannerBar.getLayoutParams();
        	    params.setMargins(0, m_size/2, 0, 0);
        	    m_scannerBar.setLayoutParams(params);
            }
            catch (IOException e)
            { }
        }

        public void surfaceDestroyed(SurfaceHolder holder)
        {
        	if (m_surfaceCamera != null)
        	{
        		m_surfaceCamera.release();
        	}

    	    m_scannerBar.setVisibility(View.INVISIBLE);
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
        {
            if (m_holder.getSurface() == null)
            {
            	return;
            }

            try
            {
            	m_surfaceCamera.stopPreview();

        	    m_scannerBar.setVisibility(View.INVISIBLE);
            }
            catch (Exception e)
            {
            	Log.e(TAG, "fail to stop preview",e);
            }

            try
            {
            	m_surfaceCamera.setPreviewDisplay(m_holder);
                m_surfaceCamera.startPreview();

        	    m_scannerBar.setVisibility(View.VISIBLE);
            }
            catch (Exception e)
            {
            	Log.e(TAG, "fail on surfaceChanged",e);
            }
        }
    }

	private void showLoadingPopup()
	{
		m_loadingPopup = ProgressDialog.show(this, "", getString(R.string.alert_message_connecting_to_network), true);
		m_loadingPopup.setCancelable(true);
	}

	private void dismissLoadingPopup()
	{
		if (m_loadingPopup != null)
		{
			m_loadingPopup.dismiss();
		}
	}

}
 */
