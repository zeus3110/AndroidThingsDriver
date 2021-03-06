/*
 * Copyright 2016 zeus3110
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

package com.zeus3110.android_things_driver.Sensor;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class MhZ19Pwm implements AutoCloseable {
    private static final String TAG = MhZ19Pwm.class.getSimpleName();

    private Gpio mGpio;
    private long onTime;

    private static final int CO2_MAX_PPM = 5000;
    private static final int PWM_CYCLE_MS = 1004;

    private PulseMesThread mThread;

    /**
     * Create a new MH-Z19 sensor driver connected on the given port.
     * @param pin GPIO pin name the sensor is connected to.
     * @throws IOException
     */
    public MhZ19Pwm(String pin) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        mGpio = pioService.openGpio(pin);

        try {
            mGpio.setDirection(Gpio.DIRECTION_IN);
            mGpio.setEdgeTriggerType(Gpio.EDGE_BOTH);

        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }

        mThread = new PulseMesThread();
        mThread.start();
        Log.i(TAG,"Start port read thread");
    }

    public long GetPulseWidth() {
        return onTime;
    }

    public int GetCO2PPM() {

        return (int)((CO2_MAX_PPM)*(onTime-2)/(PWM_CYCLE_MS-4));
    }

    public class PulseMesThread extends Thread {

        public long mStartTime, mEndTime;
        private boolean mRunning = true;

        public void run() {
            try {
                while (mRunning) {
                    while (mGpio.getValue() == false) {
                        mStartTime = System.currentTimeMillis();
                    }

                    // falseが返る直前の時間を保持
                    while (mGpio.getValue() == true) {
                        mEndTime = System.currentTimeMillis();
                    }

                    onTime = mEndTime - mStartTime;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void quit() {
            mRunning = false;
        }
    };

    /**
     * Close the driver and the underlying device.
     */
    @Override
    public void close() throws IOException {
        mThread.quit();
        if (mGpio!=null) {
            try {
                mGpio.close();
            } finally {
                mGpio = null;
            }
        }
    }
}
