package misc;

import java.security.SecureRandom;

/**
 * A 96-bit ID generator employing a Base85 encoding that produces 15-character
 * ID strings that can be used as unique identifiers.
 */
public final class IDGen {

    private static final SecureRandom sr = new SecureRandom();

    private static final int BASE_85 = 85;
    private static final long MAX_MOD = Long.MAX_VALUE % BASE_85 + 1;
    private static final long MAX_DIV = Long.MAX_VALUE / BASE_85;
    private static final long FIRST_NOV_2014 = 1414800000000L;

    //@formatter:off
   private static final char[] digits = { '!', '$', ';', '(', ')', '*', ',',
            '-', '.', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':',
            '<', '=', '>', '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I',
            'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
            'W', 'X', 'Y', 'Z', '[', ']', '^', '_', '`', 'a', 'b', 'c', 'd',
            'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '{', '|', '}', '~' };
   //@formatter:on

    public static void main(String[] args) {
        System.out.println(next(-2L));
        System.out.println(next(-1L));
        System.out.println(next(0L));
        System.out.println(next(1L));
        System.out.println(next(2L));
        System.out.println();
        System.out.println(next());
        System.out.println(next());
        System.out.println(next());
        System.out.println(next());
        System.out.println(next());
        System.out.println();
        for (int i = 0; i < 1000; ++i) {
            System.out.println(next());
        }
    }

    private IDGen() {
        throw new AssertionError();
    }

    public static String next() {
        return next(sr.nextLong());
    }

    private static String next(long suffix) {
        //@formatter:off
      return toBase85(
            (int) ((System.currentTimeMillis() - FIRST_NOV_2014) / 1000L),
            suffix);
      //@formatter:on
    }

    private static String toBase85(int intVal, long longVal) {
        if (longVal >= 0L) {
            return toBase85(intVal, longVal, -1L);
        }
        longVal &= Long.MAX_VALUE;
        long low = longVal % BASE_85 + MAX_MOD;
        //@formatter:off
      return toBase85(intVal,
            ((longVal / BASE_85) + MAX_DIV + (low / BASE_85)),
            (low % BASE_85));
      //@formatter:on
    }

    private static String toBase85(int intVal, long longVal, long remainder) {
        //@formatter:off
      char[] buf = new char[] { '!', '!', '!', '!', '!', '!', '!',
            '!', '!', '!', '!', '!', '!', '!', '!' }; // 15 chars always
      //@formatter:on

        intVal = -intVal;
        longVal = -longVal;

        int charPos = (remainder < 0L) ? 14 : 13;
        while (longVal <= -BASE_85) {
            buf[charPos--] = digits[(int) (-(longVal % BASE_85))];
            longVal = longVal / BASE_85;
        }
        buf[charPos] = digits[(int) (-longVal)];
        if (remainder >= 0L) {
            buf[14] = digits[(int) (remainder)];
        }

        charPos = 4; // position to integer part
        while (intVal <= -BASE_85) {
            buf[charPos--] = digits[-(intVal % BASE_85)];
            intVal = intVal / BASE_85;
        }
        buf[charPos] = digits[-intVal];

        return new String(buf);
    }
}
