/*
 * Copyright (C) 2022 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.doze;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class PocketDetectionService extends Service {
    private static final String TAG = "XiaomiPartsPocketDetectionService";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /* xiaomi.sensor.large_area_detect */
    private static final int TYPE_LARGE_AREA_TOUCH_SENSOR = 33171031;

    private PowerManager mPowerManager;
    private KeyguardManager mKeyguardManager;
    private SensorManager mSensorManager;
    private Sensor mTouchSensor;

    private boolean mUserPresent;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");
        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mTouchSensor = mSensorManager.getDefaultSensor(TYPE_LARGE_AREA_TOUCH_SENSOR);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenStateFilter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        unregisterReceiver(mScreenStateReceiver);
        mSensorManager.unregisterListener(mSensorListener, mTouchSensor);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    if (DEBUG) Log.d(TAG, "Received ACTION_SCREEN_ON mUserPresent=" + mUserPresent);
                    if (mUserPresent) return;
                    mSensorManager.registerListener(mSensorListener,
                            mTouchSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    if (DEBUG) Log.d(TAG, "Received ACTION_SCREEN_OFF");
                    mSensorManager.unregisterListener(mSensorListener, mTouchSensor);
                    mUserPresent = false;
                    break;
                case Intent.ACTION_USER_PRESENT:
                    if (DEBUG) Log.d(TAG, "Received ACTION_USER_PRESENT");
                    // disable when unlocked
                    mSensorManager.unregisterListener(mSensorListener, mTouchSensor);
                    mUserPresent = true;
                    break;
            }
        }
    };

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }

        @Override
        public void onSensorChanged(SensorEvent event) {
            boolean isTouchDetected = event.values[0] == 1;
            boolean isOnKeyguard = mKeyguardManager.isKeyguardLocked();

            if (DEBUG)
                Log.d(TAG, "onSensorChanged type=" + event.sensor.getType()
                        + " value=" + event.values[0] + " isTouchDetected="
                        + isTouchDetected + " isOnKeyguard=" + isOnKeyguard);

            if (isTouchDetected && isOnKeyguard) {
                Log.d(TAG, "In pocket, going to sleep");
                mPowerManager.goToSleep(SystemClock.uptimeMillis());
            }
        }
    };
}
