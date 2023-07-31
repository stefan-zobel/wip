/*
 * Copyright (c) 1997 - 1998 by Visual Numerics, Inc. All rights reserved.
 *
 * Permission to use, copy, modify, and distribute this software is freely
 * granted by Visual Numerics, Inc., provided that the copyright notice
 * above and the following warranty disclaimer are preserved in human
 * readable form.
 *
 * Because this software is licensed free of charge, it is provided
 * "AS IS", with NO WARRANTY.  TO THE EXTENT PERMITTED BY LAW, VNI
 * DISCLAIMS ALL WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO ITS PERFORMANCE, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * VNI WILL NOT BE LIABLE FOR ANY DAMAGES WHATSOEVER ARISING OUT OF THE USE
 * OF OR INABILITY TO USE THIS SOFTWARE, INCLUDING BUT NOT LIMITED TO DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, PUNITIVE, AND EXEMPLARY DAMAGES, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */
package math.imsl;

/**
 * Some ancient (but quick) trigonometric routines by Visual Numerics.
 */
public final class Trig {

    private static final double TANH_COEFF[] = { -0.25828756643634709, -0.11836106330053497, 0.0098694426480063976,
            -0.00083579866234458197, 7.0904321198942996e-5, -6.01642431812e-6, 5.1052419079999996e-7,
            -4.3320729076999997e-8, 3.6759990550000002e-9, -3.1192849599999999e-10, 2.6468827999999999e-11,
            -2.2460229999999999e-12, 1.9058700000000001e-13, -1.6171999999999999e-14, 1.372e-15,
            -1.1600000000000001e-16, 8.9999999999999999e-18 };

    public static double sech2(double x) {
        double y = cosh(x);
        return 1.0 / (y * y);
    }

    public static double cosh(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }
        if (Double.isInfinite(x)) {
            return Double.POSITIVE_INFINITY;
        }
        double y = Math.exp(Math.abs(x));
        if (y < 94906265.62) {
            return 0.5 * (y + 1.0 / y);
        }
        return 0.5 * y;
    }

    public static double tanh(double d) {
        double d2 = Math.abs(d);
        double d1;
        if (Double.isNaN(d)) {
            d1 = Double.NaN;
        } else if (d2 < 1.82501e-8) {
            d1 = d;
        } else if (d2 <= 1.0) {
            d1 = d * (1.0 + csevl(2.0 * d * d - 1.0, TANH_COEFF));
        } else if (d2 < 7.9772948850000001) {
            d2 = Math.exp(d2);
            d1 = sign((d2 - 1.0 / d2) / (d2 + 1.0 / d2), d);
        } else {
            d1 = sign(1.0, d);
        }
        return d1;
    }

    private static double sign(double d, double d1) {
        double d2 = (d >= 0.0) ? d : -d;
        return (d1 >= 0.0) ? d2 : -d2;
    }

    private static double csevl(double d, double[] ad) {
        double d2 = 0.0;
        double d1 = 0.0;
        double d3 = 0.0;
        double d4 = 2.0 * d;
        for (int i = ad.length - 1; i >= 0; i--) {
            d3 = d2;
            d2 = d1;
            d1 = (d4 * d2 - d3) + ad[i];
        }
        return 0.5 * (d1 - d3);
    }

    private Trig() {
        throw new AssertionError();
    }
}
