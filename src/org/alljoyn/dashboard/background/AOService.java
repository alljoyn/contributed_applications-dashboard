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
package org.alljoyn.dashboard.background;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.params.HttpConnectionParams;

import android.app.Service;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class AOService extends Service {

    private static final String TAG = "DASHBOARD_AOService";

    public static final String GET_IMAGE_ACTION = "org.alljoyn.dashboard.background.GET_IMAGE";
    public static final String IMAGE_RETRIEVED_ACTION = "org.alljoyn.dashboard.background.IMAGE_RETRIVED";

    public static final String BUNDLE_IMAGE_URL = "bundle_image_url";
    public static final String BUNDLE_IMAGE_DIMENSION = "bundle_image_dimension";

    private static final long DELAY_BETWEEN_FAILURES = 15 * 60 * 1000;
    private static final int MAX_RETRIES = 10;

    private static final int CMD_DOWNLOAD_FILE = 1;

    /**
     * Class for clients to access. This service always runs in the same process
     * as its clients. That's why we don't no need to use IPC.
     */

    private boolean m_isRunning = false;
    private final IBinder mBinder = new LocalBinder();
    private Handler m_commandHandler = null;

    private final BlockingQueue<DownloadDataRequestInfo> imageDownloadQueue = new ArrayBlockingQueue<DownloadDataRequestInfo>(100);

    private class DownloadDataRequestInfo {
        public String url;
        public String dimension;
        public long intialTimeRequest;
        public int retries;
        public long lastTimeRequested;

    }

    public class LocalBinder extends Binder {
        AOService getService() {
            return AOService.this;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        return mBinder;
    }

    /**
     * 
     */
    @Override
    public void onCreate() {
        super.onCreate();

        m_commandHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                onHandleCommandMessage(msg);
            }
        };

        m_isRunning = true;

        new Thread("AOServiceMainThread") {
            DownloadDataRequestInfo ddri;

            @Override
            public void run() {
                Log.i(TAG, "AOServiceMainThread startring run");

                while (isRunning()) {

                    try {
                        ddri = imageDownloadQueue.take();

                        if (ddri.retries >= MAX_RETRIES) {
                            Log.i(TAG, "Tried to download more then 10 time don't try again !!!");
                            continue;
                        }
                        ddri.lastTimeRequested = System.currentTimeMillis();

                    } catch (InterruptedException e) {

                        Writer writer = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(writer);
                        e.printStackTrace(printWriter);
                        Log.e(TAG, writer.toString());
                        continue;
                    }
                    AndroidHttpClient client = AndroidHttpClient.newInstance("Get Image");

                    FileOutputStream fos = null;
                    try {
                        String imageExtension = "png";
                        // we assume the image is in png format!!!
                        /*
                         * if (ddri.url!=null && ddri.url.lastIndexOf(".")!=-1){
                         * imageExtension
                         * =ddri.url.substring(ddri.url.lastIndexOf(".")+1); }
                         * 
                         * if(imageExtension==null ||
                         * imageExtension.length()!=3){ imageExtension="png"; }
                         */

                        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
                        String md5value = org.alljoyn.dashboard.logic.Util.calcMD5FromString(ddri.url);
                        if (md5value == null) {
                            continue;
                        }
                        String md5name = "i_" + md5value.toLowerCase() + "." + imageExtension;
                        String combinedURl = ddri.url;

                        Log.i(TAG, "AOServiceMainThread URL do download " + combinedURl);
                        File file = new File(path + "/Alljoyn/" + ddri.dimension + "/" + md5name);

                        if (file.isFile()) {
                            Log.i(TAG, "File Already Exists don't need to download !!!");
                            Log.i(TAG, "AOServiceMainThread sending intent with download image " + combinedURl + " in file " + file.getAbsolutePath());
                            Intent intent = new Intent(IMAGE_RETRIEVED_ACTION);
                            Bundle extras = new Bundle();
                            extras.putString(AOService.BUNDLE_IMAGE_URL, ddri.url);
                            extras.putString(AOService.BUNDLE_IMAGE_DIMENSION, ddri.dimension);
                            intent.putExtras(extras);
                            sendBroadcast(intent);
                            continue;
                        }

                        // final HttpParams params = client.getParams();

                        HttpConnectionParams.setConnectionTimeout(client.getParams(), 3000);
                        HttpConnectionParams.setSoTimeout(client.getParams(), 5000);
                        HttpClientParams.setRedirecting(client.getParams(), true);

                        HttpGet httpget = new HttpGet(combinedURl);
                        HttpResponse response;

                        response = client.execute(httpget);

                        long size = response.getEntity().getContentLength();
                        if (size > 0 && response.getStatusLine().getStatusCode() == 200) {
                            if (!file.isFile()) {
                                File parentDir = new File(file.getAbsoluteFile().getParent());
                                if (!parentDir.exists()) {
                                    parentDir.mkdirs();
                                }
                            }

                            fos = new FileOutputStream(file);
                            response.getEntity().writeTo(fos);
                            response.getEntity().consumeContent();
                            fos.flush();

                            Log.i(TAG, "AOServiceMainThread sending intent with download image " + combinedURl + " in file " + file.getAbsolutePath());
                            Intent intent = new Intent(IMAGE_RETRIEVED_ACTION);
                            Bundle extras = new Bundle();
                            extras.putString(AOService.BUNDLE_IMAGE_URL, ddri.url);
                            extras.putString(AOService.BUNDLE_IMAGE_DIMENSION, ddri.dimension);
                            intent.putExtras(extras);
                            sendBroadcast(intent);
                        }

                    } // end of try
                    catch (Exception e) {

                        Writer writer = new StringWriter();
                        PrintWriter printWriter = new PrintWriter(writer);
                        e.printStackTrace(printWriter);
                        Log.e(TAG, writer.toString());
                        Message msg = m_commandHandler.obtainMessage(CMD_DOWNLOAD_FILE);
                        ddri.retries = ddri.retries + 1;
                        msg.obj = ddri;
                        m_commandHandler.sendMessageDelayed(msg, DELAY_BETWEEN_FAILURES);

                    }// end of catch

                    finally {
                        if (fos != null) {
                            try {
                                fos.close();
                            } catch (Exception e) {
                                Writer writer = new StringWriter();
                                PrintWriter printWriter = new PrintWriter(writer);
                                e.printStackTrace(printWriter);
                                Log.e(TAG, writer.toString());
                            }
                        }
                        client.close();

                    }
                }
            }

        }.start();

    }

    /**
     * All commands are passed here.
     * 
     * @param msg
     */
    private void onHandleCommandMessage(Message msg) {
        if (msg == null) {
            return;
        }

        switch (msg.what) {

        case CMD_DOWNLOAD_FILE:
            if (msg.obj instanceof DownloadDataRequestInfo) {
                try {
                    imageDownloadQueue.put((DownloadDataRequestInfo) msg.obj);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            break;

        }

    }

    public boolean isRunning() {
        return m_isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand");
        String action = intent.getAction();
        if (action != null && action.equals(GET_IMAGE_ACTION)) {
            Bundle extra = intent.getExtras();
            if (extra != null) {
                Message msg = m_commandHandler.obtainMessage(CMD_DOWNLOAD_FILE);
                DownloadDataRequestInfo ddri = new DownloadDataRequestInfo();
                ddri.dimension = extra.getString(BUNDLE_IMAGE_DIMENSION);
                ddri.url = extra.getString(BUNDLE_IMAGE_URL);
                if (!ddri.dimension.isEmpty() && !ddri.url.isEmpty()) {
                    ddri.intialTimeRequest = System.currentTimeMillis();
                    ddri.lastTimeRequested = 0;
                    ddri.retries = 0;
                    msg.arg1 = startId;
                    msg.obj = ddri;
                    m_commandHandler.sendMessage(msg);
                }
            }
        }
        return START_NOT_STICKY;
    }

}
