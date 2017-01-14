package com.zeus3110.sensors_test;

import android.app.Activity;
import android.os.Bundle;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import com.zeus3110.android_things_driver.IOExpander.MCP23008;
import com.zeus3110.android_things_driver.Sensor.BME280;
import com.zeus3110.android_things_driver.Sensor.MhZ19Pwm;
import com.zeus3110.android_things_driver.Sensor.TSL2561;
import com.zeus3110.android_things_driver.Sensor.Veml6070;

import android.util.Log;
import android.view.KeyEvent;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private ButtonInputDriver mButtonInputDriver;
    private Veml6070 mUVSensor;
    private MhZ19Pwm mCO2Sensor;
    private TSL2561 mLumiSensor;
    private BME280 mTempSensor;
    private MCP23008 mIOExpander;

    int OutData=0x01;

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


            Log.i(TAG, "Registering I2C UV Sensor driver");
            mUVSensor=new Veml6070(BoardDefaults.getI2cBus());
            mUVSensor.setMode(Veml6070.IT_4);

            Log.i(TAG, "Registering PWM Input CO2 Sensor driver");
            mCO2Sensor = new MhZ19Pwm(BoardDefaults.getGPIOForPwmIn());

            Log.i(TAG, "Registering I2C Luminance Sensor driver");
            mLumiSensor = new TSL2561(BoardDefaults.getI2cBus(),TSL2561.TSL2561_ADDRESS_GND);
            mLumiSensor.SetGainAndIntegtime(TSL2561.TIMING_GAIN_16,TSL2561.TIMING_TIME_402);

            Log.i(TAG, "Registering I2C Temperature Sensor driver");
            mTempSensor = new BME280(BoardDefaults.getI2cBus());
            mTempSensor.setMode(BME280.MODE_NORMAL);
            mTempSensor.setTemperatureOversampling(BME280.OVERSAMPLING_1X);
            mTempSensor.setPressureOversampling(BME280.OVERSAMPLING_1X);
            mTempSensor.setHumidityOversampling(BME280.OVERSAMPLING_1X);

            Log.i(TAG, "Registering I2C GPIO Expander driver");
            mIOExpander = new MCP23008(BoardDefaults.getI2cBus(), MCP23008.MCP23008_ADDRESS0);
            // set Pin0-3 to output pin
            mIOExpander.SetOutputPins(MCP23008.MCP23008_PIN3|MCP23008.MCP23008_PIN2|MCP23008.MCP23008_PIN1|MCP23008.MCP23008_PIN0);
            Log.i(TAG,"Pin Direction: " + String.valueOf((int)(0xFF &mIOExpander.ReadPinDirection() )));

            Log.i(TAG, "Registered Drivers");

        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pins", e);
        }
    }

    private void ReadTest(){
        float lux_data;
        float temp_data;
        float press_data;
        float humid_data;
        float uv_data;
        int co2_data;

        Log.i(TAG, "Sensor Read Test");
        try{
            uv_data=mUVSensor.ReadUVData();
            lux_data=mLumiSensor.GetLuxData();
            co2_data = mCO2Sensor.GetCO2PPM();
            temp_data = mTempSensor.readTemperature();
            press_data = mTempSensor.readPressure();
            humid_data = mTempSensor.readHumidity();

            Log.i(TAG,"Sensor Data: "+String.valueOf(uv_data)+" uW/cm²");
            Log.i(TAG,"Luminance Data: "+String.valueOf(lux_data)+" lux");
            Log.i(TAG,"Sensor Data: "+String.valueOf(co2_data)+" ppm");
            Log.i(TAG,"Temperature Data: "+String.valueOf(temp_data)+" ℃");
            Log.i(TAG,"Humidity Data: "+String.valueOf(humid_data)+" %");
            Log.i(TAG,"Pressure Data: "+String.valueOf(press_data)+" hPa");

        } catch (IOException e) {
            Log.e(TAG, "Error Sensor Data Read", e);
        } catch (Exception e){
            Log.e(TAG, "Other Error", e);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            Log.i(TAG,"Key Down Event");
            try {
                byte val;
                Log.i(TAG,"Write Output Data: " + String.valueOf(OutData));
                mIOExpander.WriteOutputs((byte)OutData);
                val = mIOExpander.ReadOutputs();
                Log.i(TAG,"Written Output Data: " + String.valueOf(0xFF&val));

                ReadTest();

            } catch (IOException e) {
                Log.e(TAG, "IO Write Error", e);
            } finally {
                OutData = (OutData == 0x08) ? 0x01 : (OutData << 1);
            }
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
