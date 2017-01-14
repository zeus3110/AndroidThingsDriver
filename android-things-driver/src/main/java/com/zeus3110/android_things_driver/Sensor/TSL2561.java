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

import android.support.annotation.IntDef;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class TSL2561 implements AutoCloseable {

    private static final String TAG = TSL2561.class.getSimpleName();

    // I2C address for the sensor.
    @IntDef({TSL2561_ADDRESS_GND, TSL2561_ADDRESS_FLOAT, TSL2561_ADDRESS_VDD})
    public @interface SlaveAddress {}
    public static final int TSL2561_ADDRESS_GND = 0x29;
    public static final int TSL2561_ADDRESS_FLOAT = 0x39;
    public static final int TSL2561_ADDRESS_VDD = 0x49;

    private I2cDevice mDevice;

    // Register definition
    private static final int TSL2561_CONTROL = 0x00;
    private static final int TSL2561_TIMING = 0x01;
    private static final int TSL2561_THRESHLOWLOW = 0x02;
    private static final int TSL2561_THRESHHIGHLOW = 0x04;
    private static final int TSL2561_INTERRUPT = 0x06;
    private static final int TSL2561_CRC = 0x08;
    private static final int TSL2561_ID = 0x0A;
    private static final int TSL2561_DATA0LOW = 0x0C;
    private static final int TSL2561_DATA0HIGH = 0x0D;
    private static final int TSL2561_DATA1LOW = 0x0E;
    private static final int TSL2561_DATA1HIGH = 0x0F;

    // TIMING PARAMETER
    @IntDef({TIMING_GAIN_1, TIMING_GAIN_16})
    public @interface SensorGainReg {}
    public static final int TIMING_GAIN_1 = 0x00;
    public static final int TIMING_GAIN_16 = 0x10;
    @IntDef({TIMING_TIME_13R7, TIMING_TIME_101, TIMING_TIME_402, TIMING_TIME_MANU})
    public @interface SensorIntegTimeReg {}
    public static final int TIMING_TIME_13R7 = 0x00;
    public static final int TIMING_TIME_101 = 0x01;
    public static final int TIMING_TIME_402 = 0x02;
    public static final int TIMING_TIME_MANU = 0x03;
    private static final int TIMING_DEFAULT = TIMING_GAIN_1 | TIMING_TIME_402;

    // ID
    private static final int I_AM_TSL2561 = 0x50;
    private static final int REG_NO_MASK = 0x0F;

    // COMMAND
    private static final int CMD_CMDMODE = 0b10000000;
    private static final int CMD_CLEAR = 0b01000000;
    private static final int CMD_WORD = 0b00100000;
    private static final int CMD_BLOCK = 0b00010000;
    private static final int CMD_SINGLE = CMD_CMDMODE;
    private static final int CMD_MULTI = CMD_CMDMODE | CMD_WORD;

    private float SensorGain;
    private float SensorIntegTime;


    /**
     * Create a new TSL2561 sensor driver connected on the given bus.
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     */
    public TSL2561(String bus, @SlaveAddress int Address) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        mDevice = pioService.openI2cDevice(bus, Address);

        try {
            InitSensor();
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    // Initialize
    private void InitSensor() throws IOException
    {
        SensorPowerUp();
        SetTimingReg(TIMING_DEFAULT);
    }

    /////////////// Read Lux from sensor //////////////////////
    /*
    0    < CH1/CH0 < 0.50 Lux = 0.0304  x CH0-0.062  x CH0 x ((CH1/CH0)1.4)
    0.50 < CH1/CH0 < 0.61 Lux = 0.0224  x CH0-0.031  x CH1
    0.61 < CH1/CH0 < 0.80 Lux = 0.0128  x CH0-0.0153 x CH1
    0.80 < CH1/CH0 < 1.30 Lux = 0.00146 x CH0-0.00112x CH1
    1.30 < CH1/CH0  Lux = 0
     */
    public float GetLuxData() throws IOException {
        double lux0, lux1;
        double ratio;
        double lux;
        int ch0, ch1;

        ch0 = (ReadRegData(TSL2561_DATA0LOW) | (ReadRegData(TSL2561_DATA0HIGH)<<8) )& 0x0000FFFF;
        ch1 = (ReadRegData(TSL2561_DATA1LOW) | (ReadRegData(TSL2561_DATA1HIGH)<<8) )& 0x0000FFFF;

        if (ch0 == 0xFFFF) {
            return 2500.0f;
        }

        lux0 = (double)ch0;
        lux1 = (double)ch1;
        ratio = lux1 / lux0;

        ReadTimingReg();

        lux0 *= (402.0/SensorIntegTime);
        lux1 *= (402.0/SensorIntegTime);
        lux0 /= SensorGain;
        lux1 /= SensorGain;

        if (ratio <= 0.5) {
            lux = 0.03040 * lux0 - 0.06200 * lux0 * Math.pow(ratio,1.4);
        } else if (ratio <= 0.61) {
            lux = 0.02240 * lux0 - 0.03100 * lux1;
        } else if (ratio <= 0.80) {
            lux = 0.01280 * lux0 - 0.01530 * lux1;
        } else if (ratio <= 1.30) {
            lux = 0.00146 * lux0 - 0.00112 * lux1;
        } else {
            lux = 0;
        }
        return (float)lux;
    }

    // Timing Register
    public int SetGainAndIntegtime(@SensorGainReg int gain, @SensorIntegTimeReg int time) throws IOException {
        int param;
        param = gain | time;
        return SetTimingReg(param);
    }

    private int SetTimingReg(int parameter) throws IOException
    {
        int buf;

        buf = (byte)(parameter & 0x000000FF);
        WriteRegData(TSL2561_TIMING, parameter);

        buf = ReadRegData(TSL2561_TIMING);

        return buf;
    }

    private int ReadTimingReg() throws IOException
    {
        int i;
        int buf;

        buf = ReadRegData(TSL2561_TIMING);

        if ((buf & TIMING_GAIN_16)!= 0x0){
            SensorGain = 16.0f;
        } else {
            SensorGain = 1.0f;
        }

        i = buf & 0x3;
        switch (i) {
            case 0:
                SensorIntegTime = 13.7f;
                break;
            case 1:
                SensorIntegTime = 101.0f;
                break;
            case 2:
                SensorIntegTime = 402.0f;
                break;
            default:
                SensorIntegTime = 1.0f;
                break;
        }

        Log.i(TAG,"SensorGain Data: "+String.valueOf(SensorGain));
        Log.i(TAG,"SensorIntegTime Data: "+String.valueOf(SensorIntegTime));

        return (int)buf;
    }

    /**
     * Close the driver and the underlying device.
     */
    @Override
    public void close() throws IOException {
        if (mDevice!=null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    // ID
    public int ReadID() throws IOException {
        int buf;

        buf = ReadRegData(TSL2561_ID);

        return buf;
    }

    public boolean IsTSL2561() throws IOException {
        int id;

        id=ReadID();

        return ((id & 0x040)!=0x0);
    }

    // Power ON/OFF
    public void SensorPowerUp() throws IOException{
        WriteRegData(TSL2561_CONTROL, 0x03);
    }

    public void SensorPowerDown() throws IOException{
        WriteRegData(TSL2561_CONTROL, 0x00);
    }

    private void WriteRegData(int RegAddress, int RegData) throws IOException{
        byte buf[]=new byte[1];
        int bufdata;
        bufdata = CMD_SINGLE | RegAddress;
        buf[0] = (byte)(bufdata & 0x000000FF);
        mDevice.write(buf,1);
        buf[0] = (byte)(RegData & 0x000000FF);
        mDevice.write(buf,1);
    }

    private int ReadRegData(int RegAddress) throws IOException{
        byte buf[]=new byte[1];
        int bufdata;
        bufdata = CMD_SINGLE | RegAddress;
        buf[0] = (byte)(bufdata & 0x000000FF);
        mDevice.write(buf,1);
        mDevice.read(buf,1);
        return (buf[0] & 0x000000FF);
    }

}


