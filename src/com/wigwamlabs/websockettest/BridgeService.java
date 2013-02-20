package com.wigwamlabs.websockettest;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;

public class BridgeService extends Service implements Accessory.Callback, WebSocketServer.Callback {
    interface Callback {
        void onAccessoryState(boolean connected);

        void onWebSocketState(boolean running);
    }

    public class LocalBinder extends Binder {
        public BridgeService getService() {
            return BridgeService.this;
        }
    }

    private static final String TAG = BridgeService.class.getSimpleName();
    private final IBinder mBinder = new LocalBinder();
    private Accessory mAccessory;
    private BroadcastReceiver mAccessoryDetachedBroadcastReceiver;
    private WebSocketServer mWebSocketServer;
    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();

        closeAccessory();
        closeWebSocketServer();
    }

    void addCallback(Callback callback) {
        if (!mCallbacks.contains(callback)) {
            mCallbacks.add(callback);

            notifyAccessoryStateChanged();
            notifyWebSocketStateChanged();
        }
    }

    void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    private void notifyWebSocketStateChanged() {
        for (final Callback c : mCallbacks) {
            c.onWebSocketState(mWebSocketServer != null);
        }
    }

    private void notifyAccessoryStateChanged() {
        for (final Callback c : mCallbacks) {
            c.onAccessoryState(mAccessory != null);
        }
    }

    void openAccessory(final UsbAccessory accessory) {
        if (mAccessory != null) {
            mAccessory.close();
        }
        mAccessory = new Accessory(this, accessory, this);
        notifyAccessoryStateChanged();

        mAccessoryDetachedBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                    final UsbAccessory closeAccessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (accessory.equals(closeAccessory)) {
                        closeAccessory();
                    }
                }
            }
        };
        final IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mAccessoryDetachedBroadcastReceiver, mIntentFilter);

        startWebSocketServer();
    }

    private void closeAccessory() {
        if (mAccessory != null) {
            mAccessory.close();
        }
        mAccessory = null;
        if (mAccessoryDetachedBroadcastReceiver != null) {
            unregisterReceiver(mAccessoryDetachedBroadcastReceiver);
            mAccessoryDetachedBroadcastReceiver = null;
        }

        closeWebSocketServer();
    }

    public void writeToAccessory(byte[] bytes) {
        Log.d(TAG, "writeToAccessory() " + new String(bytes));
        if (mAccessory != null) {
            mAccessory.write(bytes);
        }
    }

    @Override
    public void onReadFromAccessory(byte[] buf) {
        Log.d(TAG, "onReadFromAccessory() " + new String(buf));
        mWebSocketServer.send(buf);
    }

    @Override
    public void onAccessoryError() {
        Log.d(TAG, "onAccessoryError()");
        closeAccessory();
    }

    private void startWebSocketServer() {
        if (mWebSocketServer != null) {
            return;
        }

        mWebSocketServer = new WebSocketServer(this);
        notifyWebSocketStateChanged();
    }

    private void closeWebSocketServer() {
        if (mWebSocketServer != null) {
            try {
                mWebSocketServer.stop(500);
            } catch (final Exception e) {
                Log.e(TAG, "Exception when closing server", e);
            }
            mWebSocketServer = null;
            notifyWebSocketStateChanged();
        }
    }

    @Override
    public void onWebSocketMessage(String message) {
        writeToAccessory(message.getBytes());
    }
}
