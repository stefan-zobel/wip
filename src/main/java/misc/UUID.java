package misc;

import java.security.SecureRandom;

/**
 * A couple of {@code UUID} generation mechanisms.
 */
public final class UUID {

    private static final SecureRandom ng = new SecureRandom();

    private static final int BASE_64 = 64;

    // our Base64 alphabet
    //@formatter:off
    private static final char[] digits = { '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
        'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
        'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i',
        'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '_', '~' };
    //@formatter:on

    /**
     * Equivalent to <blockquote>
     * 
     * <pre>
     * String id = UUID.randomUUID().toString().replace("-", "");
     * </pre>
     * 
     * </blockquote> but with 128 bits of randomness instead of 122 bits and at
     * least twice as fast.
     * 
     * @return a UUID string
     */
    public static String random128BitHex() {
        byte[] rnd = new byte[16];
        ng.nextBytes(rnd);
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (rnd[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (rnd[i] & 0xff);
        }
        StringBuilder sb = new StringBuilder(32);
        sb.append(digits(msb >> 32, 8));
        sb.append(digits(msb >> 16, 4));
        sb.append(digits(msb, 4));
        sb.append(digits(lsb >> 48, 4));
        sb.append(digits(lsb, 12));
        return sb.toString();
    }

    /** Returns val represented by the specified number of hex digits. */
    private static String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

    /**
     * Returns a 120-bit {@code UUID} in a Base64 encoding. The generated uuid
     * has a fixed length of 20 characters.
     * 
     * @return a 120-bit {@code UUID} in a Base64 encoding (20 characters long)
     */
    public static String random120BitBase64() {
        return toBase64_120bit(new long[] { ng.nextLong() >>> 16, ng.nextLong() >>> 16, ng.nextInt() >>> 8 });
    }

    /**
     * Returns a 144-bit {@code UUID} in a Base64 encoding. The generated uuid
     * has a fixed length of 24 characters.
     * 
     * @return a 144-bit {@code UUID} in a Base64 encoding (24 characters long)
     */
    public static String random144BitBase64() {
        return toBase64_144bit(new long[] { ng.nextLong() >>> 16, ng.nextLong() >>> 16, ng.nextLong() >>> 16 });
    }

    private static String toBase64_120bit(long[] values) {
        //@formatter:off
        char[] buf = new char[] { '0', '0', '0', '0', '0', '0', '0', '0', '0',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0' }; // 20 chars always
        //@formatter:on
        int charPos = 19;
        for (int i = 0; i < values.length; ++i) {
            long longVal = -values[i];
            while (longVal <= -BASE_64) {
                buf[charPos--] = digits[(int) (-(longVal % BASE_64))];
                longVal = longVal / BASE_64;
            }
            buf[charPos] = digits[(int) (-longVal)];
            charPos = (i == 0) ? 11 : 3;
        }
        return new String(buf);
    }

    private static String toBase64_144bit(long[] values) {
        //@formatter:off
        char[] buf = new char[] { '0', '0', '0', '0', '0', '0', '0', '0', '0',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0' }; // 24 chars always
        //@formatter:on
        int charPos = 23;
        for (int i = 0; i < values.length; ++i) {
            long longVal = -values[i];
            while (longVal <= -BASE_64) {
                buf[charPos--] = digits[(int) (-(longVal % BASE_64))];
                longVal = longVal / BASE_64;
            }
            buf[charPos] = digits[(int) (-longVal)];
            charPos = (i == 0) ? 15 : 7;
        }
        return new String(buf);
    }

    private UUID() {
        throw new AssertionError();
    }

    public static void main(String[] args) {
        System.out.println(toBase64_120bit(new long[] { 0, 0, 0 }));
        System.out.println(toBase64_144bit(new long[] { 0, 0, 0 }));
        System.out.println(toBase64_120bit(new long[] { 1, 1, 1 }));
        System.out.println(toBase64_144bit(new long[] { 1, 1, 1 }));
        System.out.println(toBase64_120bit(new long[] { 9, 9, 9 }));
        System.out.println(toBase64_144bit(new long[] { 9, 9, 9 }));
        System.out.println(toBase64_120bit(new long[] { 99, 99, 99 }));
        System.out.println(toBase64_144bit(new long[] { 99, 99, 99 }));
        System.out.println(toBase64_120bit(new long[] { 281474976710655L, 281474976710655L, 16777215 }));
        System.out.println(toBase64_144bit(new long[] { 281474976710655L, 281474976710655L, 281474976710655L }));

        System.out.println();
        final int COUNT = 100;
        for (int i = 0; i < COUNT; ++i) {
            String s = UUID.random144BitBase64();
            if (s.length() != 24) {
                throw new IllegalStateException();
            }
            System.out.println(s);
        }
    }
}
