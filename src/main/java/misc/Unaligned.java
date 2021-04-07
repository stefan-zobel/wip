package misc;

import java.nio.ByteOrder;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public final class Unaligned {
    static final Unsafe U = UnsafeAccess.unsafe;

    public static final boolean BIG_ENDIAN = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);

    /**
     * Fetches a value at some byte offset into a given Java object.
     * More specifically, fetches a value within the given object
     * <code>o</code> at the given offset, or (if <code>o</code> is
     * null) from the memory address whose numerical value is the
     * given offset.  <p>
     *
     * The specification of this method is the same as {@code
     * sun.misc.Unsafe.getLong(Object, long)} except that the offset
     * does not need to have been obtained from
     * {@code sun.misc.Unsafe.objectFieldOffset()} on the
     * {@link java.lang.reflect.Field} of some Java field.  The value
     * in memory is raw data, and need not correspond to any Java
     * variable.  Unless <code>o</code> is null, the value accessed
     * must be entirely within the allocated object.  The endianness
     * of the value in memory is the endianness of the native platform.
     *
     * <p> The read will be atomic with respect to the largest power
     * of two that divides the GCD of the offset and the storage size.
     * For example, getLongUnaligned will make atomic reads of 2-, 4-,
     * or 8-byte storage units if the offset is zero mod 2, 4, or 8,
     * respectively.  There are no other guarantees of atomicity.
     * <p>
     * 8-byte atomicity is only guaranteed on platforms on which
     * support atomic accesses to longs.
     *
     * @param o Java heap object in which the value resides, if any, else
     *        null
     * @param offset The offset in bytes from the start of the object
     * @return the value fetched from the indicated object
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    public static long getLongUnaligned(Object o, long offset) {
        if ((offset & 7) == 0) {
            return U.getLong(o, offset);
        } else if ((offset & 3) == 0) {
            return makeLong(U.getInt(o, offset),
                            U.getInt(o, offset + 4));
        } else if ((offset & 1) == 0) {
            return makeLong(U.getShort(o, offset),
                            U.getShort(o, offset + 2),
                            U.getShort(o, offset + 4),
                            U.getShort(o, offset + 6));
        } else {
            return makeLong(U.getByte(o, offset),
                            U.getByte(o, offset + 1),
                            U.getByte(o, offset + 2),
                            U.getByte(o, offset + 3),
                            U.getByte(o, offset + 4),
                            U.getByte(o, offset + 5),
                            U.getByte(o, offset + 6),
                            U.getByte(o, offset + 7));
        }
    }

    /**
     * As {@link #getLongUnaligned(Object, long)} but with an
     * additional argument which specifies the endianness of the value
     * as stored in memory.
     *
     * @param o Java heap object in which the variable resides
     * @param offset The offset in bytes from the start of the object
     * @param bigEndian The endianness of the value
     * @return the value fetched from the indicated object
     * @since 9
     */
    public static long getLongUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getLongUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    public static int getIntUnaligned(Object o, long offset) {
        if ((offset & 3) == 0) {
            return U.getInt(o, offset);
        } else if ((offset & 1) == 0) {
            return makeInt(U.getShort(o, offset),
                           U.getShort(o, offset + 2));
        } else {
            return makeInt(U.getByte(o, offset),
                           U.getByte(o, offset + 1),
                           U.getByte(o, offset + 2),
                           U.getByte(o, offset + 3));
        }
    }

    /** @see #getLongUnaligned(Object, long, boolean) */
    public static int getIntUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getIntUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    public static short getShortUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return U.getShort(o, offset);
        } else {
            return makeShort(U.getByte(o, offset),
                             U.getByte(o, offset + 1));
        }
    }

    /** @see #getLongUnaligned(Object, long, boolean) */
    public static short getShortUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getShortUnaligned(o, offset));
    }

    /** @see #getLongUnaligned(Object, long) */
    public static char getCharUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return U.getChar(o, offset);
        } else {
            return (char)makeShort(U.getByte(o, offset),
                                   U.getByte(o, offset + 1));
        }
    }

    /** @see #getLongUnaligned(Object, long, boolean) */
    public static char getCharUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getCharUnaligned(o, offset));
    }

    /**
     * Stores a value at some byte offset into a given Java object.
     * <p>
     * The specification of this method is the same as {@code
     * sun.misc.Unsafe.getLong(Object, long)} except that the offset does
     * not need to have been obtained from
     * {@code sun.misc.Unsafe.objectFieldOffset()} on the
     * {@link java.lang.reflect.Field} of some Java field.  The value
     * in memory is raw data, and need not correspond to any Java
     * variable.  The endianness of the value in memory is the
     * endianness of the native platform.
     * <p>
     * The write will be atomic with respect to the largest power of
     * two that divides the GCD of the offset and the storage size.
     * For example, putLongUnaligned will make atomic writes of 2-, 4-,
     * or 8-byte storage units if the offset is zero mod 2, 4, or 8,
     * respectively.  There are no other guarantees of atomicity.
     * <p>
     * 8-byte atomicity is only guaranteed on platforms on which
     * support atomic accesses to longs.
     *
     * @param o Java heap object in which the value resides, if any, else
     *        null
     * @param offset The offset in bytes from the start of the object
     * @param x the value to store
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    public static void putLongUnaligned(Object o, long offset, long x) {
        if ((offset & 7) == 0) {
            U.putLong(o, offset, x);
        } else if ((offset & 3) == 0) {
            putLongParts(o, offset,
                         (int)(x >> 0),
                         (int)(x >>> 32));
        } else if ((offset & 1) == 0) {
            putLongParts(o, offset,
                         (short)(x >>> 0),
                         (short)(x >>> 16),
                         (short)(x >>> 32),
                         (short)(x >>> 48));
        } else {
            putLongParts(o, offset,
                         (byte)(x >>> 0),
                         (byte)(x >>> 8),
                         (byte)(x >>> 16),
                         (byte)(x >>> 24),
                         (byte)(x >>> 32),
                         (byte)(x >>> 40),
                         (byte)(x >>> 48),
                         (byte)(x >>> 56));
        }
    }

    /**
     * As {@link #putLongUnaligned(Object, long, long)} but with an additional
     * argument which specifies the endianness of the value as stored in memory.
     * @param o Java heap object in which the value resides
     * @param offset The offset in bytes from the start of the object
     * @param x the value to store
     * @param bigEndian The endianness of the value
     * @throws RuntimeException No defined exceptions are thrown, not even
     *         {@link NullPointerException}
     * @since 9
     */
    public static void putLongUnaligned(Object o, long offset, long x, boolean bigEndian) {
        putLongUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    public static void putIntUnaligned(Object o, long offset, int x) {
        if ((offset & 3) == 0) {
            U.putInt(o, offset, x);
        } else if ((offset & 1) == 0) {
            putIntParts(o, offset,
                        (short)(x >> 0),
                        (short)(x >>> 16));
        } else {
            putIntParts(o, offset,
                        (byte)(x >>> 0),
                        (byte)(x >>> 8),
                        (byte)(x >>> 16),
                        (byte)(x >>> 24));
        }
    }

    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public static void putIntUnaligned(Object o, long offset, int x, boolean bigEndian) {
        putIntUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    public static void putShortUnaligned(Object o, long offset, short x) {
        if ((offset & 1) == 0) {
            U.putShort(o, offset, x);
        } else {
            putShortParts(o, offset,
                          (byte)(x >>> 0),
                          (byte)(x >>> 8));
        }
    }

    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public static void putShortUnaligned(Object o, long offset, short x, boolean bigEndian) {
        putShortUnaligned(o, offset, convEndian(bigEndian, x));
    }

    /** @see #putLongUnaligned(Object, long, long) */
    public static void putCharUnaligned(Object o, long offset, char x) {
        putShortUnaligned(o, offset, (short)x);
    }

    /** @see #putLongUnaligned(Object, long, long, boolean) */
    public static void putCharUnaligned(Object o, long offset, char x, boolean bigEndian) {
        putCharUnaligned(o, offset, convEndian(bigEndian, x));
    }

    // -- Swapping --

    public static short swap(short x) {
        return Short.reverseBytes(x);
    }

    public static char swap(char x) {
        return Character.reverseBytes(x);
    }

    public static int swap(int x) {
        return Integer.reverseBytes(x);
    }

    public static long swap(long x) {
        return Long.reverseBytes(x);
    }

    // These methods construct integers from bytes.  The byte ordering
    // is the native endianness of this platform.
    private static long makeLong(byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        return ((toUnsignedLong(i0) << pickPos(56, 0))
              | (toUnsignedLong(i1) << pickPos(56, 8))
              | (toUnsignedLong(i2) << pickPos(56, 16))
              | (toUnsignedLong(i3) << pickPos(56, 24))
              | (toUnsignedLong(i4) << pickPos(56, 32))
              | (toUnsignedLong(i5) << pickPos(56, 40))
              | (toUnsignedLong(i6) << pickPos(56, 48))
              | (toUnsignedLong(i7) << pickPos(56, 56)));
    }

    private static long makeLong(short i0, short i1, short i2, short i3) {
        return ((toUnsignedLong(i0) << pickPos(48, 0))
              | (toUnsignedLong(i1) << pickPos(48, 16))
              | (toUnsignedLong(i2) << pickPos(48, 32))
              | (toUnsignedLong(i3) << pickPos(48, 48)));
    }

    private static long makeLong(int i0, int i1) {
        return (toUnsignedLong(i0) << pickPos(32, 0))
             | (toUnsignedLong(i1) << pickPos(32, 32));
    }

    private static int makeInt(short i0, short i1) {
        return (toUnsignedInt(i0) << pickPos(16, 0))
             | (toUnsignedInt(i1) << pickPos(16, 16));
    }

    private static int makeInt(byte i0, byte i1, byte i2, byte i3) {
        return ((toUnsignedInt(i0) << pickPos(24, 0))
              | (toUnsignedInt(i1) << pickPos(24, 8))
              | (toUnsignedInt(i2) << pickPos(24, 16))
              | (toUnsignedInt(i3) << pickPos(24, 24)));
    }

    private static short makeShort(byte i0, byte i1) {
        return (short)((toUnsignedInt(i0) << pickPos(8, 0))
                     | (toUnsignedInt(i1) << pickPos(8, 8)));
    }

    // Zero-extend an integer
    private static int toUnsignedInt(byte n)    { return n & 0xff; }
    private static int toUnsignedInt(short n)   { return n & 0xffff; }
    private static long toUnsignedLong(byte n)  { return n & 0xffl; }
    private static long toUnsignedLong(short n) { return n & 0xffffl; }
    private static long toUnsignedLong(int n)   { return n & 0xffffffffl; }

    private static int pickPos(int top, int pos) { return BIG_ENDIAN ? top - pos : pos; }

    // These methods write integers to memory from smaller parts
    // provided by their caller.  The ordering in which these parts
    // are written is the native endianness of this platform.
    private static void putLongParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        U.putByte(o, offset + 0, pick(i0, i7));
        U.putByte(o, offset + 1, pick(i1, i6));
        U.putByte(o, offset + 2, pick(i2, i5));
        U.putByte(o, offset + 3, pick(i3, i4));
        U.putByte(o, offset + 4, pick(i4, i3));
        U.putByte(o, offset + 5, pick(i5, i2));
        U.putByte(o, offset + 6, pick(i6, i1));
        U.putByte(o, offset + 7, pick(i7, i0));
    }

    private static void putLongParts(Object o, long offset, short i0, short i1, short i2, short i3) {
        U.putShort(o, offset + 0, pick(i0, i3));
        U.putShort(o, offset + 2, pick(i1, i2));
        U.putShort(o, offset + 4, pick(i2, i1));
        U.putShort(o, offset + 6, pick(i3, i0));
    }

    private static void putLongParts(Object o, long offset, int i0, int i1) {
        U.putInt(o, offset + 0, pick(i0, i1));
        U.putInt(o, offset + 4, pick(i1, i0));
    }

    private static void putIntParts(Object o, long offset, short i0, short i1) {
        U.putShort(o, offset + 0, pick(i0, i1));
        U.putShort(o, offset + 2, pick(i1, i0));
    }

    private static void putIntParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3) {
        U.putByte(o, offset + 0, pick(i0, i3));
        U.putByte(o, offset + 1, pick(i1, i2));
        U.putByte(o, offset + 2, pick(i2, i1));
        U.putByte(o, offset + 3, pick(i3, i0));
    }

    private static void putShortParts(Object o, long offset, byte i0, byte i1) {
        U.putByte(o, offset + 0, pick(i0, i1));
        U.putByte(o, offset + 1, pick(i1, i0));
    }

    private static byte  pick(byte  le, byte  be) { return BIG_ENDIAN ? be : le; }
    private static short pick(short le, short be) { return BIG_ENDIAN ? be : le; }
    private static int   pick(int   le, int   be) { return BIG_ENDIAN ? be : le; }

    // Maybe byte-reverse an integer
    private static char convEndian(boolean big, char n)   { return big == BIG_ENDIAN ? n : Character.reverseBytes(n); }
    private static short convEndian(boolean big, short n) { return big == BIG_ENDIAN ? n : Short.reverseBytes(n)    ; }
    private static int convEndian(boolean big, int n)     { return big == BIG_ENDIAN ? n : Integer.reverseBytes(n)  ; }
    private static long convEndian(boolean big, long n)   { return big == BIG_ENDIAN ? n : Long.reverseBytes(n)     ; }
}
