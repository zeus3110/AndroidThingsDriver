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
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

import static android.content.ContentValues.TAG;
import static java.lang.Math.pow;

public class DSM501A implements AutoCloseable {
    private static final String TAG = DSM501A.class.getSimpleName();

    private Gpio mGpio;

    private final long MES_CYCLE = 30000;

    private long calcCycleTime_ms = MES_CYCLE;
    private long calcOnIntegTime_ms = 10 ;

    private PulseMesThread mThread;

    /**
     * Create a new MH-Z19 sensor driver connected on the given port.
     * @param pin GPIO pin name the sensor is connected to.
     * @throws IOException
     */
    public DSM501A(String pin) throws IOException {
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
        return calcOnIntegTime_ms;
    }

    public float GetDustDensity() {
        float ratio;
        ratio = (float)(calcOnIntegTime_ms*100.0f/calcCycleTime_ms);
        return (float)(0.001915* Math.pow(ratio,2) + 0.09522f*ratio - 0.04884);
    }

    public class PulseMesThread extends Thread {

        public long pulseStartTime_ms, cycleStartTime_ms, cycleTime_ms, currentTime_ms, integOnTime;
        boolean previosSignal, currentSignal;
        private boolean mRunning = true;


        public void run() {
            integOnTime = 0;
            cycleTime_ms = MES_CYCLE;
            previosSignal = false;

            try {
                cycleStartTime_ms = System.currentTimeMillis();
                while (mRunning) {
                    currentTime_ms = System.currentTimeMillis();
                    currentSignal = mGpio.getValue();
                    if (previosSignal) {
                        if(!currentSignal) {
                            integOnTime = integOnTime + (currentTime_ms - pulseStartTime_ms);
                            previosSignal = false;
                        }
                    } else {
                        if(currentSignal) {
                            pulseStartTime_ms = currentTime_ms;
                            previosSignal = true;
                        }
                    }

                    cycleTime_ms = currentTime_ms - cycleStartTime_ms;
                    if(cycleTime_ms >= MES_CYCLE ) {
                        Log.i(TAG, "Cycle end: " + String.valueOf(cycleTime_ms) + " ms");
                        // keep value
                        calcCycleTime_ms = cycleTime_ms;
                        calcOnIntegTime_ms = integOnTime;
                        // reset value
                        integOnTime = 0;
                        cycleStartTime_ms = currentTime_ms;
                        previosSignal = false;
                    }

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
