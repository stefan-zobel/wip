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
package mmap.impl;

import java.lang.reflect.Method;
import sun.misc.Unsafe;

/**
 * Access to processor and memory-system properties, control of the amount of
 * direct memory a process can access and some native array helper methods.
 */
@SuppressWarnings("restriction")
public final class Native {

    // These numbers represent the point at which we have empirically
    // determined that the average cost of a JNI call exceeds the expense
    // of an element by element copy. These numbers may change over time.
    public static final int JNI_COPY_TO_ARRAY_THRESHOLD = 6;
    public static final int JNI_COPY_FROM_ARRAY_THRESHOLD = 6;
    // This number limits the number of bytes to copy per call to Unsafe's
    // copyMemory method. A limit is imposed to allow for safepoint polling
    // during a large copy
    public static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static Unsafe unsafe() {
        return U;
    }

    // -- Processor and memory-system properties --

    public static int pageSize() {
        return PAGE_SIZE;
    }

    public static long pageCount(long size) {
        return (size + (long) pageSize() - 1L) / pageSize();
    }

    public static boolean unaligned() {
        return UNALIGNED;
    }

    // These methods do no bounds checking. Verification that the copy will not
    // result in memory corruption should be done prior to invocation.
    // All positions and lengths are specified in bytes.

    /**
     * Copy from given source array to destination address.
     *
     * @param src
     *            source array
     * @param srcBaseOffset
     *            offset of first element of storage in source array
     * @param srcPos
     *            offset within source array of the first element to read
     * @param dstAddr
     *            destination address
     * @param length
     *            number of bytes to copy
     */
    public static void copyFromArray(Object src, long srcBaseOffset, long srcPos, long dstAddr, long length) {
        long offset = srcBaseOffset + srcPos;
        while (length > 0L) {
            long size = (length > UNSAFE_COPY_THRESHOLD) ? UNSAFE_COPY_THRESHOLD : length;
            U.copyMemory(src, offset, null, dstAddr, size);
            length -= size;
            offset += size;
            dstAddr += size;
        }
    }

    /**
     * Copy from source address into given destination array.
     *
     * @param srcAddr
     *            source address
     * @param dst
     *            destination array
     * @param dstBaseOffset
     *            offset of first element of storage in destination array
     * @param dstPos
     *            offset within destination array of the first element to write
     * @param length
     *            number of bytes to copy
     */
    public static void copyToArray(long srcAddr, Object dst, long dstBaseOffset, long dstPos, long length) {
        long offset = dstBaseOffset + dstPos;
        while (length > 0L) {
            long size = (length > UNSAFE_COPY_THRESHOLD) ? UNSAFE_COPY_THRESHOLD : length;
            U.copyMemory(null, srcAddr, dst, offset, size);
            length -= size;
            srcAddr += size;
            offset += size;
        }
    }

    public static void copySwapFromCharArray(Object src, long srcPos, long dstAddr, long length) {
        copySwapFromShortArray(src, srcPos, dstAddr, length);
    }

    public static void copySwapToCharArray(long srcAddr, Object dst, long dstPos, long length) {
        copySwapToShortArray(srcAddr, dst, dstPos, length);
    }

    public static native void copySwapFromShortArray(Object src, long srcPos, long dstAddr, long length);

    public static native void copySwapToShortArray(long srcAddr, Object dst, long dstPos, long length);

    public static native void copySwapFromIntArray(Object src, long srcPos, long dstAddr, long length);

    public static native void copySwapToIntArray(long srcAddr, Object dst, long dstPos, long length);

    public static native void copySwapFromLongArray(Object src, long srcPos, long dstAddr, long length);

    public static native void copySwapToLongArray(long srcAddr, Object dst, long dstPos, long length);

    private Native() {
        throw new AssertionError();
    }

    private static final Unsafe U = UnsafeAccess.unsafe;
    private static final int PAGE_SIZE = U.pageSize();
    private static final boolean UNALIGNED;
    private static final boolean IS_WINDOWS;
    static {
        try {
            Class<?> clsNioBits = Class.forName("java.nio.Bits");
            Method unaligned = clsNioBits.getDeclaredMethod("unaligned");
            unaligned.setAccessible(true);
            UNALIGNED = Boolean.class.cast(unaligned.invoke(null)).booleanValue();
            IS_WINDOWS = System.getProperty("os.name").contains("Windows");
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }
}
