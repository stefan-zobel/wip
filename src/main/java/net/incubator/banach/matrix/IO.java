/*
 * Copyright 2020 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.incubator.banach.matrix;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * IO helper functions.
 */
final class IO {

    private static final byte BIG_ENDIAN = 1;
    private static final byte LITTLE_ENDIAN = 2;
    private static final byte DT_FLOAT_BITS = 32;
    private static final byte DT_DOUBLE_BITS = 64;
    private static final String WRONG_IS_POS = "Wrong InputStream position";

    static long writeMatrixHeaderB(int rows, int cols, int dtNumBits,
            byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        checkNumBits(dtNumBits);
        os.write(BIG_ENDIAN);
        os.write(dtNumBits);
        putIntB(rows, bytes, os);
        putIntB(cols, bytes, os);
        return 10L;
    }

    static long writeMatrixHeaderL(int rows, int cols, int dtNumBits,
            byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        checkNumBits(dtNumBits);
        os.write(LITTLE_ENDIAN);
        os.write(dtNumBits);
        putIntL(rows, bytes, os);
        putIntL(cols, bytes, os);
        return 10L;
    }

    private static void checkNumBits(int dtNumBits) throws IllegalArgumentException {
        if (dtNumBits != DT_FLOAT_BITS && dtNumBits != DT_DOUBLE_BITS) {
            throw new IllegalArgumentException("Wrong datatype (number of bits = " + dtNumBits + ")");
        }
    }

    static boolean isBigendian(byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 1);
        if (BIG_ENDIAN == bytes[0]) {
            return true;
        } else if (LITTLE_ENDIAN == bytes[0]) {
            return false;
        }
        throw new IOException(WRONG_IS_POS);
    }

    static boolean isDoubleType(byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 1);
        if (DT_DOUBLE_BITS == bytes[0]) {
            return true;
        } else if (DT_FLOAT_BITS == bytes[0]) {
            return false;
        }
        throw new IOException(WRONG_IS_POS);
    }

    static int readRows(boolean bigendian, byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 4);
        return bigendian ? getIntB(bytes) : getIntL(bytes);
    }

    static int readCols(boolean bigendian, byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 4);
        return bigendian ? getIntB(bytes) : getIntL(bytes);
    }

    static long putDoubleL(double x, byte[] bytes /* byte[8] */, OutputStream os) throws IOException {
        os.write(putLongL(Double.doubleToRawLongBits(x), bytes), 0, 8);
        return 8L;
    }

    static double getDoubleL(byte[] bytes /* byte[8] */, InputStream is) throws IOException {
        is.read(bytes, 0, 8);
        return getDoubleL(bytes);
    }

    static long putDoubleB(double x, byte[] bytes /* byte[8] */, OutputStream os) throws IOException {
        os.write(putLongB(Double.doubleToRawLongBits(x), bytes), 0, 8);
        return 8L;
    }

    static double getDoubleB(byte[] bytes /* byte[8] */, InputStream is) throws IOException {
        is.read(bytes, 0, 8);
        return getDoubleB(bytes);
    }

    static long putFloatL(float x, byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        os.write(putIntL(Float.floatToRawIntBits(x), bytes), 0, 4);
        return 4L;
    }

    static float getFloatL(byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 4);
        return getFloatL(bytes);
    }

    static long putFloatB(float x, byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        os.write(putIntB(Float.floatToRawIntBits(x), bytes), 0, 4);
        return 4L;
    }

    static float getFloatB(byte[] bytes /* byte[4] */, InputStream is) throws IOException {
        is.read(bytes, 0, 4);
        return getFloatB(bytes);
    }

    static long putIntB(int x, byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        os.write(putIntB(x, bytes));
        return 4L;
    }

    static long putIntL(int x, byte[] bytes /* byte[4] */, OutputStream os) throws IOException {
        os.write(putIntL(x, bytes));
        return 4L;
    }

    private static byte[] putLongB(long x, byte[] bytes) {
        bytes[0] = (byte) (x >> 56);
        bytes[1] = (byte) (x >> 48);
        bytes[2] = (byte) (x >> 40);
        bytes[3] = (byte) (x >> 32);
        bytes[4] = (byte) (x >> 24);
        bytes[5] = (byte) (x >> 16);
        bytes[6] = (byte) (x >> 8);
        bytes[7] = (byte) x;
        return bytes;
    }

    private static byte[] putLongL(long x, byte[] bytes) {
        bytes[7] = (byte) (x >> 56);
        bytes[6] = (byte) (x >> 48);
        bytes[5] = (byte) (x >> 40);
        bytes[4] = (byte) (x >> 32);
        bytes[3] = (byte) (x >> 24);
        bytes[2] = (byte) (x >> 16);
        bytes[1] = (byte) (x >> 8);
        bytes[0] = (byte) x;
        return bytes;
    }

    private static byte[] putIntB(int x, byte[] bytes) {
        bytes[0] = (byte) (x >> 24);
        bytes[1] = (byte) (x >> 16);
        bytes[2] = (byte) (x >> 8);
        bytes[3] = (byte) x;
        return bytes;
    }

    private static byte[] putIntL(int x, byte[] bytes) {
        bytes[3] = (byte) (x >> 24);
        bytes[2] = (byte) (x >> 16);
        bytes[1] = (byte) (x >> 8);
        bytes[0] = (byte) x;
        return bytes;
    }

    private static float getFloatL(byte[] bytes) {
        return Float.intBitsToFloat(getIntL(bytes));
    }

    private static float getFloatB(byte[] bytes) {
        return Float.intBitsToFloat(getIntB(bytes));
    }

    static double getDoubleL(byte[] bytes) {
        return Double.longBitsToDouble(getLongL(bytes));
    }

    static double getDoubleB(byte[] bytes) {
        return Double.longBitsToDouble(getLongB(bytes));
    }

    //@formatter:off
    private static long getLongL(byte[] bytes) {
        return makeLong(bytes[7],
                        bytes[6],
                        bytes[5],
                        bytes[4],
                        bytes[3],
                        bytes[2],
                        bytes[1],
                        bytes[0]);
    }
    //@formatter:on

    //@formatter:off
    private static long getLongB(byte[] bytes) {
        return makeLong(bytes[0],
                        bytes[1],
                        bytes[2],
                        bytes[3],
                        bytes[4],
                        bytes[5],
                        bytes[6],
                        bytes[7]);
    }
    //@formatter:on

    //@formatter:off
    private static int getIntL(byte[] bytes) {
        return makeInt(bytes[3],
                       bytes[2],
                       bytes[1],
                       bytes[0]);
    }
    //@formatter:on

    //@formatter:off
    private static int getIntB(byte[] bytes) {
        return makeInt(bytes[0],
                       bytes[1],
                       bytes[2],
                       bytes[3]);
    }
    //@formatter:on

    //@formatter:off
    private static long makeLong(byte b7, byte b6, byte b5, byte b4,
            byte b3, byte b2, byte b1, byte b0) {
        return ((((long) b7       ) << 56) |
                (((long) b6 & 0xff) << 48) |
                (((long) b5 & 0xff) << 40) |
                (((long) b4 & 0xff) << 32) |
                (((long) b3 & 0xff) << 24) |
                (((long) b2 & 0xff) << 16) |
                (((long) b1 & 0xff) <<  8) |
                (((long) b0 & 0xff)      ));
    }
    //@formatter:on

    //@formatter:off
    private static int makeInt(byte b3, byte b2, byte b1, byte b0) {
        return (((b3       ) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) <<  8) |
                ((b0 & 0xff)      ));
    }
    //@formatter:off

    private IO() {
        throw new AssertionError();
    }
}
