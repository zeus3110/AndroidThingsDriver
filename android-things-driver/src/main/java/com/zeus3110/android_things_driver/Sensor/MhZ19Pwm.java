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

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

public class MhZ19Pwm implements AutoCloseable {

    private Gpio mGpio;
    private long startTime;
    private int onTime;

    // queue for 10 sample moving average
    private Queue<Integer> queue = new ArrayDeque<Integer>();
    private final int queue_size = 10;

    private static final int CO2_MAX_PPM = 5000;
    private static final int PWM_CYCLE_MS = 1004;

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
            mGpio.registerGpioCallback(new GpioCallback() {
                @Override
                public boolean onGpioEdge(Gpio gpio){
                    // do something
                    try {
                        if (mGpio.getValue()) {
                            startTime = System.currentTimeMillis();
                        } else {
                            onTime = (int)(System.currentTimeMillis() - startTime);
                            if(queue.size() >= queue_size) {
                                queue.remove();
                            }
                            queue.add(onTime);
                        }
                    } catch (IOException e) {

                    }

                    // Return true to continue listening to events
                    return true;
                }
            });
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    public long GetPulseWidth() {
        return onTime;
    }

    public int GetCO2PPM() {
        int onTime_avg=0;

        for(Integer element: queue){
            onTime_avg = onTime_avg + element;
        }
        if(queue.size() > 0){
            onTime_avg = onTime_avg / queue.size();
        }

        return ((CO2_MAX_PPM)*(onTime_avg-2)/(PWM_CYCLE_MS-4));
    }

    /**
     * Close the driver and the underlying device.
     */
    @Override
    public void close() throws IOException {
        if (mGpio!=null) {
            try {
                mGpio.close();
            } finally {
                mGpio = null;
            }
        }
    }
}
