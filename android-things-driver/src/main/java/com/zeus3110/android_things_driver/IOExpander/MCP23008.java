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

package com.zeus3110.android_things_driver.IOExpander;

import android.support.annotation.IntDef;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class MCP23008 implements AutoCloseable {

    // I2C address for the io expander.
    @IntDef({MCP23008_ADDRESS0, MCP23008_ADDRESS1, MCP23008_ADDRESS2, MCP23008_ADDRESS3, MCP23008_ADDRESS4, MCP23008_ADDRESS5, MCP23008_ADDRESS6, MCP23008_ADDRESS7})
    public @interface SlaveAddress {}
    public static final int MCP23008_ADDRESS0 = 0x20;
    public static final int MCP23008_ADDRESS1 = 0x21;
    public static final int MCP23008_ADDRESS2 = 0x22;
    public static final int MCP23008_ADDRESS3 = 0x23;
    public static final int MCP23008_ADDRESS4 = 0x24;
    public static final int MCP23008_ADDRESS5 = 0x25;
    public static final int MCP23008_ADDRESS6 = 0x26;
    public static final int MCP23008_ADDRESS7 = 0x27;

    @IntDef({MCP23008_PIN0, MCP23008_PIN1, MCP23008_PIN2, MCP23008_PIN3, MCP23008_PIN4, MCP23008_PIN5, MCP23008_PIN6, MCP23008_PIN7, MCP23008_PIN_ALL})
    public @interface IOPin {}
    public static final int MCP23008_PIN0 = 0b00000001;
    public static final int MCP23008_PIN1 = 0b00000010;
    public static final int MCP23008_PIN2 = 0b00000100;
    public static final int MCP23008_PIN3 = 0b00001000;
    public static final int MCP23008_PIN4 = 0b00010000;
    public static final int MCP23008_PIN5 = 0b00100000;
    public static final int MCP23008_PIN6 = 0b01000000;
    public static final int MCP23008_PIN7 = 0b10000000;
    public static final int MCP23008_PIN_ALL = 0b11111111;

    /* MCP23008 registers */
    private static final int IODIR = 0x00;
    private static final int IPOL = 0x01;
    private static final int GPINTEN = 0x02;
    private static final int DEFVAL = 0x03;
    private static final int INTCON = 0x04;
    private static final int IOCON = 0x05;
    private static final int GPPU = 0x06;
    private static final int INTF = 0x07;
    private static final int INTCAP = 0x08;
    private static final int GPIO = 0x09;
    private static final int OLAT = 0x0A;
    
    private static final String TAG = MCP23008.class.getSimpleName();

    private I2cDevice mDevice;

    /**
     * Create a new MCP23008 sensor driver connected on the given bus.
     * @param bus I2C bus the sensor is connected to.
     * @throws IOException
     */
    public MCP23008(String bus, @SlaveAddress int Address) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        mDevice = pioService.openI2cDevice(bus, Address);

        try {
            Reset();
        } catch (IOException|RuntimeException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
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

    public void Reset() throws IOException {
        mDevice.writeRegByte(IODIR, (byte)0x0FF );     // reset port as input port
        for (int reg = IPOL; reg <= OLAT; reg++ ) {
            mDevice.writeRegByte( reg, (byte)0x00 );
        }
    }

    /** Set pins to input mode
     *
     * This function is used to set which pins are inputs (if any). Example:
     * SetInputPins ( MCP23008_PIN0 | MCP23008_PIN1 | MCP23008_PIN2 );
     * Note that these are set to input in addition to the previously set.
     * In other words, the following:
     * SetInputPins ( MCP23008_PIN1 );
     * SetInputPins ( MCP23008_PIN2 );
     * Results in at least two pins set to input.
     *
     * @param pins A bitmask of pins to set to input mode.
     */
    public void SetInputPins(int pins) throws IOException {
        byte value = mDevice.readRegByte( IODIR );
        mDevice.writeRegByte( IODIR, (byte)(value | pins));
    }

    /** Set pins to output mode
     *
     * This function is used to set which pins are outputs (if any). Example:
     * set_outputs ( MCP23008_PIN0 | MCP23008_PIN1 | MCP23008_PIN2 );
     * Note that these are set to output in addition to the previously set.
     * In other words, the following:
     * set_outputs ( Pin_GP1 );
     * set_outputs ( Pin_GP2 );
     * Results in at least two pins set to output.
     *
     * @param pins A bitmask of pins to set to output mode.
     */
    public void SetOutputPins(int pins ) throws IOException {
        byte value = mDevice.readRegByte( IODIR );
        mDevice.writeRegByte( IODIR, (byte)(value & (~pins)));
    }

    public byte ReadPinDirection() throws IOException {
        return mDevice.readRegByte(IODIR);
    }

    /** Write to the output pins.
     *
     * This function is used to set output pins on or off.
     *
     * @param values A bitmask indicating whether a pin should be on or off.
     */
    public void WriteOutputs(byte values) throws IOException {
        mDevice.writeRegByte( GPIO, values );
    }

    /** Read back the outputs.
     *
     * This function is used to read the last values written to the output pins.
     *
     * @returns The value from the OLAT register.
     */
    public byte ReadOutputs() throws IOException {
        return mDevice.readRegByte(OLAT);
    }

    /** Read from the input pins.
     *
     * This function is used to read the values from the input pins.
     *
     * @returns A bitmask of the current state of the input pins.
     */
    public byte ReadInputs () throws IOException {
        return mDevice.readRegByte(GPIO);
    }

    /** Set the input pin polarity.
     *
     * This function sets the polarity of the input pins.
     * A 1 bit is inverted polarity, a 0 is normal.
     *
     * @param values A bitmask of the input polarity.
     */
    public void SetInputPolarity(byte values) throws IOException {
        mDevice.writeRegByte( IPOL, values );
    }

    /** Read back the current input pin polarity.
     *
     * This function reads the current state of the input pin polarity.
     *
     * @returns The value from the IPOL register.
     */
    public byte GetInputPolarity () throws IOException {
        return mDevice.readRegByte(IPOL);
    }

    /** Enable and disable the internal pull-up resistors for input pins.
     *
     * This function enables the internal 100 kΩ pull-up resistors.
     * A 1 bit enables the pull-up resistor for the corresponding input pin.
     *
     * @param values A bitmask indicating which pull-up resistors should be enabled/disabled.
     */
    public void SetPullups(byte values) throws IOException {
        mDevice.writeRegByte( GPPU, values );
    }

    /** Get the current state of the internal pull-up resistors.
     *
     * @returns The current state of the pull-up resistors.
     */
    public byte GetPullups () throws IOException {
        return mDevice.readRegByte(GPPU);
    }

    /** Generate an interrupt when a pin changes.
     *
     * This function enables interrupt generation for the specified pins.
     * The interrupt is active-low by default.
     * The function acknowledge_interrupt must be called before another
     * interrupt will be generated.
     *
     * @param pins A bitmask of the pins that may generate an interrupt.
     */
    public void Interrupt_on_Changes(int pins) throws IOException {
        int value = (int)mDevice.readRegByte( INTCON );
        value &= ~pins;
        mDevice.writeRegByte( INTCON, (byte)value );
        value = (int)mDevice.readRegByte( GPINTEN );
        value |= pins;
        mDevice.writeRegByte( GPINTEN, (byte)value );
    }

    /** Disables interrupts for the specified pins.
     *
     * @param pins A bitmask indicating which interrupts should be disabled.
     */
    public void DisableInterrupt(int pins) throws IOException {
        int value = (int)mDevice.readRegByte( GPINTEN );
        value &= ~pins;
        mDevice.writeRegByte( GPINTEN, (byte)value );
    }

    /** Acknowledge a generated interrupt.
     *
     * This function must be called when an interrupt is generated to discover
     * which pin caused the interrupt and to enable future interrupts.
     *
     * [0] An output paramter that specifies which pin generated the interrupt.
     * [1] The current state of the input pins.
     */
    public byte[] AcknowledgeInterrupt() throws IOException {
        byte ret[] = new byte[2];
        ret[0] = mDevice.readRegByte( INTF );
        ret[1] = mDevice.readRegByte( INTCAP );
        return ret;
    }
}
