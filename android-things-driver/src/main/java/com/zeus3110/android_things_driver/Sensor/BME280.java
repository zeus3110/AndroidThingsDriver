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
    reference
    https://github.com/androidthings/contrib-drivers/tree/master/bmx280
    https://github.com/BoschSensortec/BME280_driver
 */

package com.zeus3110.android_things_driver.Sensor;

import android.support.annotation.IntDef;
import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;


/**
 * Driver for the BME 280 temperature/humidity/pressure sensor.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class BME280 implements AutoCloseable {

    private static final String TAG = BME280.class.getSimpleName();

    /**
     * Chip ID for the BME280
     */
    public static final int CHIP_ID_BMP280 = 0x58;
    /**
     * Chip ID for the BME280
     */
    public static final int CHIP_ID_BME280 = 0x60;
    /**
     * I2C address for the sensor.
     */
    public static final int I2C_ADDRESS = 0x76;

    // Sensor constants from the datasheet.
    // https://cdn-shop.adafruit.com/datasheets/BST-BME280-DS001-11.pdf
    /**
     * Mininum temperature in Celsius the sensor can measure.
     */
    public static final float MIN_TEMP_C = -40f;
    /**
     * Maximum temperature in Celsius the sensor can measure.
     */
    public static final float MAX_TEMP_C = 85f;
    /**
     * Minimum pressure in hPa the sensor can measure.
     */
    public static final float MIN_PRESSURE_HPA = 300f;
    /**
     * Maximum pressure in hPa the sensor can measure.
     */
    public static final float MAX_PRESSURE_HPA = 1100f;
    /**
     * Minimum humidity in % the sensor can measure.(added humidity support)
     */
    public static final float MIN_HUMIDITY_PERCENT = 0f;
    /**
     * Maximum humidity in % the sensor can measure.(added humidity support)
     */
    public static final float MAX_HUMIDITY_PERCENT = 100f;
    /**
     * Maximum power consumption in micro-amperes when measuring temperature.
     */
    public static final float MAX_POWER_CONSUMPTION_TEMP_UA = 325f;
    /**
     * Maximum power consumption in micro-amperes when measuring pressure.
     */
    public static final float MAX_POWER_CONSUMPTION_PRESSURE_UA = 720f;
    /**
     * Maximum frequency of the measurements.
     */
    public static final float MAX_FREQ_HZ = 181f;
    /**
     * Minimum frequency of the measurements.
     */
    public static final float MIN_FREQ_HZ = 23.1f;

    /**
     * Power mode.
     */
    @IntDef({MODE_SLEEP, MODE_FORCED, MODE_NORMAL})
    public @interface Mode {}

    public static final int MODE_SLEEP = 0;
    public static final int MODE_FORCED = 1;
    public static final int MODE_NORMAL = 2;

    /**
     * Oversampling multiplier.
     */
    @IntDef({OVERSAMPLING_SKIPPED, OVERSAMPLING_1X})
    public @interface Oversampling {}
    public static final int OVERSAMPLING_SKIPPED = 0;
    public static final int OVERSAMPLING_1X = 1;

    // Registers
    private static final int BME280_REG_TEMP_CALIB_1 = 0x88;
    private static final int BME280_REG_TEMP_CALIB_2 = 0x8A;
    private static final int BME280_REG_TEMP_CALIB_3 = 0x8C;

    private static final int BME280_REG_PRESS_CALIB_1 = 0x8E;
    private static final int BME280_REG_PRESS_CALIB_2 = 0x90;
    private static final int BME280_REG_PRESS_CALIB_3 = 0x92;
    private static final int BME280_REG_PRESS_CALIB_4 = 0x94;
    private static final int BME280_REG_PRESS_CALIB_5 = 0x96;
    private static final int BME280_REG_PRESS_CALIB_6 = 0x98;
    private static final int BME280_REG_PRESS_CALIB_7 = 0x9A;
    private static final int BME280_REG_PRESS_CALIB_8 = 0x9C;
    private static final int BME280_REG_PRESS_CALIB_9 = 0x9E;

    private static final int BME280_REG_HUMID_CALIB_1 = 0xA1;       // Humidity support
    private static final int BME280_REG_HUMID_CALIB_2 = 0xE1;
    private static final int BME280_REG_HUMID_CALIB_3 = 0xE3;
    private static final int BME280_REG_HUMID_CALIB_4 = 0xE4;
    private static final int BME280_REG_HUMID_CALIB_5 = 0xE5;
    private static final int BME280_REG_HUMID_CALIB_6 = 0xE7;


    private static final int BME280_REG_ID = 0xD0;
    private static final int BME280_REG_CTRL = 0xF4;
    private static final int BME280_REG_CTRL_HUMID = 0xF2;       // Humidity support

    private static final int BME280_REG_PRESS = 0xF7;
    private static final int BME280_REG_TEMP = 0xFA;
    private static final int BME280_REG_HUMID = 0xFD;       // Humidity support

    private static final int BME280_POWER_MODE_MASK = 0b00000011;
    private static final int BME280_POWER_MODE_SLEEP = 0b00000000;
    private static final int BME280_POWER_MODE_NORMAL = 0b00000011;
    private static final int BME280_OVERSAMPLING_PRESSURE_MASK = 0b00011100;
    private static final int BME280_OVERSAMPLING_PRESSURE_BITSHIFT = 2;
    private static final int BME280_OVERSAMPLING_TEMPERATURE_MASK = 0b11100000;
    private static final int BME280_OVERSAMPLING_TEMPERATURE_BITSHIFT = 5;
    private static final int BME280_OVERSAMPLING_HUMID_MASK = 0b00000111;       // Humidity support
    private static final int BME280_OVERSAMPLING_HUMID_BITSHIFT = 0;

    private I2cDevice mDevice;
    private final int[] mTempCalibrationData = new int[3];
    private final int[] mPressureCalibrationData = new int[9];
    private final int[] mHumidityCalibrationData = new int[6];       // Humidity support
    private final byte[] mBuffer = new byte[3]; // for reading sensor values
    private final byte[] mBuffer2 = new byte[2]; // for reading sensor values(Humidity support)
    private boolean mEnabled = false;
    private int mChipId;
    private int mMode;
    private int mPressureOversampling;
    private int mTemperatureOversampling;
    private int mHumidityOversampling;       // Humidity support

    /**
     * Create a new BME280 sensor driver connected on the given bus.
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     */
    public BME280(String bus) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        I2cDevice device = pioService.openI2cDevice(bus, I2C_ADDRESS);
        try {
            connect(device);
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Create a new BMP/BME280 sensor driver connected to the given I2c device.
     * @param device I2C device of the sensor.
     * @throws IOException
     */
    /*package*/  BME280(I2cDevice device) throws IOException {
        connect(device);
    }

    private void connect(I2cDevice device) throws IOException {
        mDevice = device;

        mChipId = mDevice.readRegByte(BME280_REG_ID);

        // Read temperature calibration data (3 words). First value is unsigned.
        mTempCalibrationData[0] = mDevice.readRegWord(BME280_REG_TEMP_CALIB_1) & 0xffff;
        mTempCalibrationData[1] = (short) mDevice.readRegWord(BME280_REG_TEMP_CALIB_2);
        mTempCalibrationData[2] = (short) mDevice.readRegWord(BME280_REG_TEMP_CALIB_3);
        // Read pressure calibration data (9 words). First value is unsigned.
        mPressureCalibrationData[0] = mDevice.readRegWord(BME280_REG_PRESS_CALIB_1) & 0xffff;
        mPressureCalibrationData[1] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_2);
        mPressureCalibrationData[2] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_3);
        mPressureCalibrationData[3] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_4);
        mPressureCalibrationData[4] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_5);
        mPressureCalibrationData[5] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_6);
        mPressureCalibrationData[6] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_7);
        mPressureCalibrationData[7] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_8);
        mPressureCalibrationData[8] = (short) mDevice.readRegWord(BME280_REG_PRESS_CALIB_9);
        // Read humidity calibration data (3byte and 3word data) added Humidity support
        mHumidityCalibrationData[0] = (mDevice.readRegByte(BME280_REG_HUMID_CALIB_1) & 0xFF);
        mHumidityCalibrationData[1] = (short) mDevice.readRegWord(BME280_REG_HUMID_CALIB_2);
        mHumidityCalibrationData[2] = (mDevice.readRegByte(BME280_REG_HUMID_CALIB_3) & 0xFF);
        mHumidityCalibrationData[3] = (short) ((mDevice.readRegByte(BME280_REG_HUMID_CALIB_4) << 4)|(mDevice.readRegByte(BME280_REG_HUMID_CALIB_5)&0x00F));
        mHumidityCalibrationData[4] = (short) (mDevice.readRegWord(BME280_REG_HUMID_CALIB_5) >> 4);
        mHumidityCalibrationData[5] = mDevice.readRegByte(BME280_REG_HUMID_CALIB_6);
    }



    /**
     * Set the power mode of the sensor.
     * @param mode power mode.
     * @throws IOException
     */
    public void setMode(@Mode int mode) throws IOException {
        int regCtrl = mDevice.readRegByte(BME280_REG_CTRL) & 0xff;
        if (mode == MODE_SLEEP) {
            regCtrl &= ~BME280_POWER_MODE_MASK;
        } else {
            regCtrl |= BME280_POWER_MODE_NORMAL;
        }
        mDevice.writeRegByte(BME280_REG_CTRL, (byte)(regCtrl));
        mMode = mode;
    }

    /**
     * Set oversampling multiplier for the temperature measurement.
     * @param oversampling temperature oversampling multiplier.
     * @throws IOException
     */
    public void setTemperatureOversampling(@Oversampling int oversampling) throws IOException {
        int regCtrl = mDevice.readRegByte(BME280_REG_CTRL) & 0xff;
        if (oversampling == OVERSAMPLING_SKIPPED) {
            regCtrl &= ~BME280_OVERSAMPLING_TEMPERATURE_MASK;
        } else {
            regCtrl |= 1 << BME280_OVERSAMPLING_TEMPERATURE_BITSHIFT;
        }
        mDevice.writeRegByte(BME280_REG_CTRL, (byte)(regCtrl));
        mTemperatureOversampling = oversampling;
    }

    /**
     * Set oversampling multiplier for the pressure measurement.
     * @param oversampling pressure oversampling multiplier.
     * @throws IOException
     */
    public void setPressureOversampling(@Oversampling int oversampling) throws IOException {
        int regCtrl = mDevice.readRegByte(BME280_REG_CTRL) & 0xff;
        if (oversampling == OVERSAMPLING_SKIPPED) {
            regCtrl &= ~BME280_OVERSAMPLING_PRESSURE_MASK;
            mEnabled = false;
        } else {
            regCtrl |= 1 << BME280_OVERSAMPLING_PRESSURE_BITSHIFT;
            mEnabled = true;
        }
        mDevice.writeRegByte(BME280_REG_CTRL, (byte)(regCtrl));
        mPressureOversampling = oversampling;
    }

    /**
     * Set oversampling multiplier for the humidity measurement. (added humidity support)
     * @param oversampling pressure humidity multiplier.
     * @throws IOException
     */
    public void setHumidityOversampling(@Oversampling int oversampling) throws IOException {
        int regCtrl = mDevice.readRegByte(BME280_REG_CTRL_HUMID) & 0xff;
        if (oversampling == OVERSAMPLING_SKIPPED) {
            regCtrl &= ~BME280_OVERSAMPLING_HUMID_MASK;
            mEnabled = false;
        } else {
            regCtrl |= 1 << BME280_OVERSAMPLING_HUMID_BITSHIFT;
            mEnabled = true;
        }
        mDevice.writeRegByte(BME280_REG_CTRL_HUMID, (byte)(regCtrl));
        mHumidityOversampling = oversampling;
    }

    /**
     * Close the driver and the underlying device.
     */
    @Override
    public void close() throws IOException {
        if (mDevice != null) {
            try {
                mDevice.close();
            } finally {
                mDevice = null;
            }
        }
    }

    /**
     * Returns the sensor chip ID.
     */
    public int getChipId() {
        return mChipId;
    }

    /**
     * Read the current temperature.
     *
     * @return the current temperature in degrees Celsius
     */
    public float readTemperature() throws IOException, IllegalStateException {
        if (mTemperatureOversampling == OVERSAMPLING_SKIPPED) {
            throw new IllegalStateException("temperature oversampling is skipped");
        }
        int rawTemp = readSample(BME280_REG_TEMP);
        return compensateTemperature(rawTemp, mTempCalibrationData)[0];
    }

    /**
     * Read the current barometric pressure. If you also intend to use temperature readings, prefer
     * {@link #readTemperatureAndPressure()} instead since sampling the current pressure already
     * requires sampling the current temperature.
     *
     * @return the barometric pressure in hPa units
     * @throws IOException
     */
    public float readPressure() throws IOException, IllegalStateException {
        float[] values = readTemperatureAndPressure();
        return values[1];
    }

    /**
     * Read the current barometric pressure. If you also intend to use temperature readings, prefer
     * {@link #readTemperatureAndPressure()} instead since sampling the current pressure already
     * requires sampling the current temperature.
     *
     * @return the barometric pressure in hPa units
     * @throws IOException
     */
    public float readHumidity() throws IOException, IllegalStateException {
        float[] values = readTemperatureAndHumidity();
        return values[1];
    }



    /**
     * Read the current temperature and barometric pressure.
     *
     * @return a 2-element array. The first element is temperature in degrees Celsius, and the
     * second is barometric pressure in hPa units.
     * @throws IOException
     */
    public float[] readTemperatureAndPressure() throws IOException, IllegalStateException {
        if (mTemperatureOversampling == OVERSAMPLING_SKIPPED) {
            throw new IllegalStateException("temperature oversampling is skipped");
        }
        if (mPressureOversampling == OVERSAMPLING_SKIPPED) {
            throw new IllegalStateException("pressure oversampling is skipped");
        }
        // The pressure compensation formula requires the fine temperature reading, so we always
        // read temperature first.
        int rawTemp = readSample(BME280_REG_TEMP);
        float[] temperatures = compensateTemperature(rawTemp, mTempCalibrationData);
        int rawPressure = readSample(BME280_REG_PRESS);
        float pressure = compensatePressure(rawPressure, temperatures[1], mPressureCalibrationData);
        return new float[]{temperatures[0], pressure};
    }

    /**
     * Read the current temperature and humidity.
     *
     * @return a 2-element array. The first element is temperature in degrees Celsius, and the
     * second is humidity in % units.
     * @throws IOException
     */
    public float[] readTemperatureAndHumidity() throws IOException, IllegalStateException {
        if (mTemperatureOversampling == OVERSAMPLING_SKIPPED) {
            throw new IllegalStateException("temperature oversampling is skipped");
        }
        if (mHumidityOversampling == OVERSAMPLING_SKIPPED) {
            throw new IllegalStateException("humidity oversampling is skipped");
        }
        // The pressure compensation formula requires the fine temperature reading, so we always
        // read temperature first.
        int rawTemp = readSample(BME280_REG_TEMP);
        float[] temperatures = compensateTemperature(rawTemp, mTempCalibrationData);
        int rawHumidity = readSample16bit(BME280_REG_HUMID);
        float humidity = compensateHumidity(rawHumidity, temperatures[1], mHumidityCalibrationData);
        return new float[]{temperatures[0], humidity};
    }


    /**
     * Reads 20 bits from the given address.
     * @throws IOException
     */
    private int readSample(int address) throws IOException, IllegalStateException {
        if (mDevice == null) {
            throw new IllegalStateException("I2C device is already closed");
        }
        synchronized (mBuffer) {
            mDevice.readRegBuffer(address, mBuffer, 3);
            // msb[7:0] lsb[7:0] xlsb[7:4]
            int msb = mBuffer[0] & 0xff;
            int lsb = mBuffer[1] & 0xff;
            int xlsb = mBuffer[2] & 0xf0;
            // Convert to 20bit integer
            return (msb << 16 | lsb << 8 | xlsb) >> 4;
        }
    }

    /**
     * Reads 16 bits from the given address.
     * @throws IOException
     */
    private int readSample16bit(int address) throws IOException, IllegalStateException {
        if (mDevice == null) {
            throw new IllegalStateException("I2C device is already closed");
        }
        synchronized (mBuffer2) {
            mDevice.readRegBuffer(address, mBuffer2, 2);
            // msb[7:0] lsb[7:0]
            int msb = mBuffer2[0] & 0xff;
            int lsb = mBuffer2[1] & 0xff;
            // Convert to 16bit integer
            return (msb << 8 | lsb);
        }
    }

    // Compensation formula from the BME280 datasheet.
    // https://ae-bst.resource.bosch.com/media/_tech/media/datasheets/BST-BME280_DS001-11.pdf

    @VisibleForTesting
    static float[] compensateTemperature(int rawTemp, int[] calibrationData) {
        int dig_T1 = calibrationData[0];
        int dig_T2 = calibrationData[1];
        int dig_T3 = calibrationData[2];

        float adc_T = (float) rawTemp;
        float var1 = (adc_T / 16384f - ((float) dig_T1) / 1024f) * ((float) dig_T2);
        float var2 = ((adc_T / 131072f - ((float) dig_T1) / 8192f) * (adc_T / 131072f
                - ((float) dig_T1) / 8192f)) * ((float) dig_T3);
        float fineTemp = var1 + var2;
        return new float[]{fineTemp / 5120.0f, fineTemp};
    }

    // Compensation formula from the BME280 datasheet.
    // https://ae-bst.resource.bosch.com/media/_tech/media/datasheets/BST-BME280_DS001-11.pdf
    @VisibleForTesting
    static float compensatePressure(int rawPressure, float fineTemperature,
                                    int[] calibration) {
        int dig_P1 = calibration[0];
        int dig_P2 = calibration[1];
        int dig_P3 = calibration[2];
        int dig_P4 = calibration[3];
        int dig_P5 = calibration[4];
        int dig_P6 = calibration[5];
        int dig_P7 = calibration[6];
        int dig_P8 = calibration[7];
        int dig_P9 = calibration[8];

        float var1 = (fineTemperature / 2.0f) - 64000.0f;
        float var2 = var1 * var1 * ((float) dig_P6) / 32768.0f;
        var2 = var2 + var1 * ((float) dig_P5) * 2.0f;
        var2 = (var2 / 4.0f) + (((float) dig_P4) * 65536.0f);
        var1 = (((float) dig_P3) * var1 * var1 / 524288.0f + ((float) dig_P2) * var1) / 524288.0f;
        var1 = (1.0f + var1 / 32768.0f) * ((float) dig_P1);
        if (var1 == 0.0) {
            return 0; // avoid exception caused by division by zero
        }
        float p = 1048576.0f - (float) rawPressure;
        p = (p - (var2 / 4096.0f)) * 6250.0f / var1;
        var1 = ((float) dig_P9) * p * p / 2147483648.0f;
        var2 = p * ((float) dig_P8) / 32768.0f;
        p = p + (var1 + var2 + ((float) dig_P7)) / 16.0f;
        // p is in Pa, convert to hPa
        return p / 100.0f;
    }

    // Compensation formula from the BME280 API
    // https://github.com/BoschSensortec/BME280_driver
    @VisibleForTesting
    static float compensateHumidity(int rawHumidity, float fineTemperature,
                                    int[] calibration) {
        int dig_H1 = calibration[0];
        int dig_H2 = calibration[1];
        int dig_H3 = calibration[2];
        int dig_H4 = calibration[3];
        int dig_H5 = calibration[4];
        int dig_H6 = calibration[5];

        double var_h = fineTemperature - 76800.0f;

        if ((var_h > 0) || (var_h < 0))
            var_h = (rawHumidity -
                    (((double)dig_H4) * 64.0 +
                            ((double)dig_H5) / 16384.0 * var_h))*
                    (((double)dig_H2) / 65536.0 *
                            (1.0 + ((double)dig_H6)
                                    / 67108864.0 * var_h * (1.0 + ((double)
                                    dig_H3) / 67108864.0 * var_h)));
        else
            return 0.0f;
        var_h = var_h * (1.0 - ((double)dig_H1)*var_h / 524288.0);
        if (var_h > 100.0)
            var_h = 100.0;
        else if (var_h < 0.0)
            var_h = 0.0;
        return (float)var_h;
    }

}