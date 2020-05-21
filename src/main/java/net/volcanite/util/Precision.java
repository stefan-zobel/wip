package net.volcanite.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

public final class Precision {

    public static double round(double x) {
        return round(x, 5);
    }

    public static double round(double x, int scale) {
        if (isBadNum(x)) {
            return 0.0;
        }
        return BigDecimal.valueOf(x).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static int roundToIntStochastically(double x) {
        if (isBadNum(x)) {
            return 0;
        }
        double fraction = x % 1.0;
        if (Math.abs(fraction) == 0.5) {
            // stochastic rounding if the fractional part is exactly 0.5
            if (ThreadLocalRandom.current().nextDouble() >= 0.5) {
                x += 0.5;
            } else {
                x -= 0.5;
            }
        }
        return (int) Math.rint(x);
    }

    public static boolean isBadNum(double x) {
        return Double.isNaN(x) || Double.isInfinite(x);
    }

    private Precision() {
    }
}
