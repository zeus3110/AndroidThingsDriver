package com.zeus3110.dsm501a_test;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.zeus3110.android_things_driver.Sensor.DSM501A;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private ButtonInputDriver mButtonInputDriver;
    private DSM501A mCO2Sensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting ButtonActivity");

        try {
            Log.i(TAG, "Registering button driver");
            // Initialize and register the InputDriver that will emit SPACE key events
            // on GPIO state changes.
            mButtonInputDriver = new ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);
            mButtonInputDriver.register();

            Log.i(TAG, "Registering PWM Input dust Sensor driver");
            mCO2Sensor = new DSM501A("BCM4");

            Log.i(TAG, "Registered Drivers");

        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    private void ReadTest() {

        int co2_data;

        Log.i(TAG, "Sensor Read Test");
        try{
            Log.i(TAG,"Pulse width: " + String.valueOf(mCO2Sensor.GetPulseWidth()) + " ms");
            Log.i(TAG,"Dust density: " + String.valueOf(mCO2Sensor.GetDustDensity()) + " mg/m3");

        } catch (Exception e){
            Log.e(TAG, "Other Error", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            Log.i(TAG,"Key Down Event");
            ReadTest();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        if (mButtonInputDriver != null) {
            mButtonInputDriver.unregister();
            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Button driver", e);
            } finally{
                mButtonInputDriver = null;
            }
        }
    }
}
