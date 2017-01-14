/*
 * Copyright 2017 zeus3110
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zeus3110.androidthingsdriver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import java.io.IOException;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import com.zeus3110.android_things_driver.Display.SB1602B;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private SB1602B mLCD;
    private ButtonInputDriver mButtonInputDriver;

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

            Log.i(TAG, "Registering I2C LCD driver");
            mLCD = new SB1602B(BoardDefaults.getI2cBus());

            Log.i(TAG, "Registered Drivers");

            mLCD.setContrast(0x3E);
            mLCD.putByteArray(0,"Hello, 810!".getBytes());
            mLCD.putIcon(0x1FFF);
            mLCD.putByteArray(1,"ｱｲｳｴｵ".getBytes("ms932"));


        } catch (IOException e) {
            Log.e(TAG, "Error configuring Driver", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error configuring Driver", e);
        }
    }
}
