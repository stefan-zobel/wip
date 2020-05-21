package net.volcanite.test;

import java.util.Random;

public final class TestUtil {

    private static final Random rnd = new Random();

    public static final byte[] randomBytes() {
        int len = rnd.nextInt(400) + 1;
        byte[] b = new byte[len];
        rnd.nextBytes(b);
        return b;
    }

    private TestUtil() {
        throw new AssertionError();
    }
}
