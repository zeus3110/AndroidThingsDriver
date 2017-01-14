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

package com.zeus3110.android_things_driver.Display;

import android.graphics.drawable.Icon;
import android.support.annotation.IntRange;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class SB1602B implements AutoCloseable {

    private static final String TAG = SB1602B.class.getSimpleName();

    // I2C address for the LCD Driver.
    public static final int SB1602B_ADDRESS = 0x3E;

    private I2cDevice mDevice;

    // Command definition
    private static final int MaxCharsInALine = 0x10; //    buffer depth for one line (no scroll function used)
    private static final byte DEFAULT_CONTRAST = 0x35;
    private static final byte COMMAND = 0x00;
    private static final byte DATA = 0x40;

    private static final byte Comm_FunctionSet_Normal = 0x38;
    private static final byte Comm_FunctionSet_Extended = 0x39;
    private static final byte Comm_InternalOscFrequency = 0x14;
    private static final byte Comm_ContrastSet = 0x70;
    private static final byte Comm_PwrIconContrast = 0x5C;
    private static final byte Comm_FollowerCtrl = 0x60;
    private static final byte Comm_DisplayOnOff = 0x0C;
    private static final byte Comm_ClearDisplay = 0x01;
    private static final byte Comm_EntryModeSet = 0x04;
    private static final byte Comm_ReturnHome = 0x02;
    private static final byte Comm_SetCGRAM = 0x40;

    private int Cursor[] = {0, 0};      //　keeps X axis of cusor

    // LCD Icon (for SB1602B)
    private static final int IconNum = 16;
    private static final byte IconData[]= {
            // アイコンアドレス, 該当ビット
            0x00, 0x10, // 0b10000,
            0x02, 0x10, // 0b10000,
            0x04, 0x10, // 0b10000,
            0x06, 0x10, // 0b10000,

            0x07, 0x10, // 0b10000,
            0x07, 0x08, // 0b01000,
            0x09, 0x10, // 0b10000,
            0x0B, 0x10, // 0b10000,

            0x0D, 0x08, // 0b01000,
            0x0D, 0x04, // 0b00100,
            0x0D, 0x02, // 0b00010,
            0x0D, 0x10, // 0b10000,

            0x0F, 0x10, // 0b10000, // アンテナマーク
    };
    private byte[] IconBuffer = new byte[IconNum];


    /**
     * Create a new SB1602B driver connected on the given bus.
     * @param bus I2C bus the driver is connected to.
     * @throws IOException
     */
    public SB1602B(String bus) throws IOException, InterruptedException {
        PeripheralManagerService pioService = new PeripheralManagerService();
        mDevice = pioService.openI2cDevice(bus, SB1602B_ADDRESS);     
        try {
            initLCD();
        } catch (IOException|RuntimeException|InterruptedException e) {
            try {
                close();
            } catch (IOException|RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void initLCD() throws IOException, InterruptedException {
         byte InitSequence0[] = {
                Comm_FunctionSet_Normal,
                Comm_ReturnHome,             //    This may be required to reset the scroll function
                Comm_FunctionSet_Extended,
                Comm_InternalOscFrequency,
                Comm_ContrastSet | ( DEFAULT_CONTRAST       & 0xF),
                Comm_PwrIconContrast | ((DEFAULT_CONTRAST >> 4) & 0x3),
                Comm_FollowerCtrl | 0x0A,
        };

        byte InitSequence1[]  = {
                Comm_DisplayOnOff,
                Comm_ClearDisplay,
                Comm_EntryModeSet,
        };

        for(byte val: InitSequence0){
            writeCommand(val);
        }

        Thread.sleep(200);

        for(byte val: InitSequence1){
            writeCommand(val);
        }

    }

    private void writeResistor(byte Command, byte Data) throws IOException {
        byte buf[] = {Command, Data};
        mDevice.write(buf,2);
    }

    private void writeCommand(byte Data) throws IOException {
        writeResistor(COMMAND, Data);
    }

    private void writeData(byte Data) throws IOException {
        writeResistor(DATA, Data);
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

    /** Put string : "puts()"
     *
     * @param YAxsis line# (0 for upper, 1 for lower)
     * @param buf array of data
     */
    public void putByteArray(@IntRange(from=0, to=1) int YAxsis, byte[] buf) throws IOException
    {
        byte c;
        for(int i=0; i<MaxCharsInALine ;i++){
            c= (i<buf.length)? buf[i]: 0x20;
            putCharXY(c, i, YAxsis);
        }
    }

    /** Put character into specified screen position
     *
     * @param c character code
     * @param XAxsis horizontal character position on the LCD
     * @param YAxsis vertical character position on the LCD
     */
    public void putCharXY(byte c, @IntRange(from=0, to=MaxCharsInALine-1) int XAxsis,@IntRange(from=0, to=1) int YAxsis) throws IOException
    {
        byte Comm_SetDDRAMAddress = (byte)0x80;
        byte DDRAMAddress_Offset[] = { 0x00, 0x40 };

        writeCommand( (byte)((Comm_SetDDRAMAddress | DDRAMAddress_Offset[ YAxsis ]) + XAxsis) );
        writeData(c);
    }

    /** Clear the LCD
     */
    public void clearLCD() throws IOException {
        writeCommand(Comm_ClearDisplay);
        Cursor[0] = 0;
        Cursor[1] = 0;
    }

    /** Contrast adjustment
     *
     * @param contrast value (from 0x00 to 0x3E)
     */
    public void setContrast(@IntRange(from=0, to=0x3E) int contrast) throws IOException
    {
        writeCommand( Comm_FunctionSet_Extended );
        writeCommand( (byte)(Comm_ContrastSet | (contrast & 0x0f) ) );
        writeCommand( (byte)(Comm_PwrIconContrast | ((contrast>>4) & 0x03) ) );
        writeCommand( Comm_FunctionSet_Normal   );
    }

    /** Icon operation (for SB1602B)
     *
     * @param flag bitpattern to choose ICON
     */
    public void putIcon(int flag) throws IOException
    {
        int i;

        for(i=0; i<IconData.length/2; i++) {
            if((flag & (0x1000>>i))!=0x00 ){ // 該当ビットが立っていたら
                IconBuffer[IconData[i*2]] |= IconData[i*2+1];  // バッファを立てます。
            } else {
                IconBuffer[IconData[i*2]] &= ~IconData[i*2+1]; // バッファをクリアします。
            }
        }
        // 一括でLCDに書き込みます。
        for(i=0; i<16; i++) {
            writeCommand(Comm_FunctionSet_Extended); // 0b00111001); // コマンド
            writeCommand((byte)(Comm_SetCGRAM + i)); // 0b01000000+i);       // アイコン領域のアドレスを設定
            writeData(IconBuffer[i]); // アイコンデータ
        }
    }
}


