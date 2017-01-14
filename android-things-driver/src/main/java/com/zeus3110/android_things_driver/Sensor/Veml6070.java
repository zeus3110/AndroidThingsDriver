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

/*
  Example Driver for VEML6070-Breakout (Digital UV Light Sensor).
  Rset=270k on breakout, UVA sensitivity: 5.625 uW/cm²/step

  Integration Times and UVA Sensitivity:
    Rset=270k -> 1T=112.5ms ->   5.625 uW/cm²/step
*/

package com.zeus3110.android_things_driver.Sensor;

import android.support.annotation.IntDef;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class Veml6070 implements AutoCloseable {

    // I2C address for the sensor.
    public static final int I2C_ADDRESS = 0x38;
    public static final int I2C_ADDRESS_MSB = 0x39;

    // Config Data for VEML6070
    public static final byte VEML6070_SETTINGS = 0x02;  // ACK=0 ACK_THD=0 SD=0

    //
    public static final float UVA_SENSE_STEP_270K_1T = 5.625f;       // 5.625 uW/cm²/step 270kOhm 1T

    private int integTime;

    /**
     * Power mode.
     */
    @IntDef({IT_1_2, IT_1, IT_2, IT_4})
    public @interface IntegrateTime {}
    public static final int IT_1_2 = 0x0;   // 1/2T
    public static final int IT_1 = 0x1;     // 1T
    public static final int IT_2 = 0x2;     // 2T
    public static final int IT_4 = 0x3;     // 4T

    private I2cDevice mDevice;
    private I2cDevice mDeviceMsb;


    /**
     * Create a new VEML6070 sensor driver connected on the given bus.
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     */
    public Veml6070(String bus) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        I2cDevice device = pioService.openI2cDevice(bus, I2C_ADDRESS);
        I2cDevice device_data = pioService.openI2cDevice(bus, I2C_ADDRESS_MSB);

        try {
            connect(device,device_data);
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void connect(I2cDevice device,I2cDevice device_data) throws IOException {
        mDevice = device;
        mDeviceMsb = device_data;

        // Initializing VEML6070
        setMode(IT_1);      // IT:1T
    }

    /**
     * Close the driver and the underlying device.
     */
    @Override
    public void close() throws IOException {
        if ((mDevice!=null)&&(mDeviceMsb!=null)) {
            try {
                    mDevice.close();
                    mDeviceMsb.close();;
            } finally {
                mDevice = null;
                mDeviceMsb = null;
            }
        }
    }

    /**
     * Set the power mode of the sensor.
     * @param integtime Integrate time
     * @throws IOException
     */
    public void setMode(@IntegrateTime int integtime) throws IOException {
        byte[] buf = new byte[1];
        buf[0] = (byte) (VEML6070_SETTINGS | (integtime << 2));
        mDevice.write(buf,1);
        integTime=integtime;
    }

    public float ReadUVData() throws IOException{
        int val;
        float UVVal=0.0f;

        switch(integTime) {
            case IT_1_2:
                UVVal=ReadWordData()*UVA_SENSE_STEP_270K_1T*2.0f;
                break;
            case IT_1:
                UVVal=ReadWordData()*UVA_SENSE_STEP_270K_1T/1.0f;
                break;
            case IT_2:
                UVVal=ReadWordData()*UVA_SENSE_STEP_270K_1T/2.0f;
                break;
            case IT_4:
                UVVal=ReadWordData()*UVA_SENSE_STEP_270K_1T/4.0f;
                break;
            default:
                UVVal=0.0f;
        }
        return UVVal;
    }

    private int ReadWordData() throws IOException {
        int val;
        byte[] buf = new byte[1];
        mDevice.read(buf,1);
        val=buf[0]&0x000000FF;
        mDeviceMsb.read(buf,1);
        val=val|((buf[0]<<8)&0x0000FF00);
        return val;
    }
}
