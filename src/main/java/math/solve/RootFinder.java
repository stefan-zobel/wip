/*
 * Class:        RootFinder
 * Description:  Provides methods to solve non-linear equations.
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
 *
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
package math.solve;

import math.fun.DFunction;
import math.fun.MathConsts;

/**
 * Provides methods to find the root of a non-linear equation.
 */
public final class RootFinder {

    /**
     * Computes a root {@code x} of the function in {@code f} using the
     * Brent-Dekker method. The interval {@code [a, b]} must contain the root
     * {@code x}. The calculations are done with an approximate relative
     * precision {@code tol}. Returns {@code x} such that {@code f(x) = 0}.
     * 
     * @param a
     *            left endpoint of initial interval
     * @param b
     *            right endpoint of initial interval
     * @param f
     *            the function which is evaluated
     * @param tol
     *            accuracy goal
     * @return the root {@code x} such that {@code f(x) = 0}
     */
    public static double brentDekker(double a, double b, DFunction f, double tol) {

        final int MAX_ITER = 150; // Max number of iterations

        // special case a > b
        if (b < a) {
            double tmp = a;
            a = b;
            b = tmp;
        }
        if (tol < MathConsts.MIN_TOL) {
            tol = MathConsts.MIN_TOL;
        }

        // initialization
        double fa = f.apply(a);
        if (Math.abs(fa) <= MathConsts.MIN_VAL) {
            return a;
        }
        double fb = f.apply(b);
        if (Math.abs(fb) <= MathConsts.MIN_VAL) {
            return b;
        }

        double c = a;
        double fc = fa;
        double e = b - a;
        double d = e;

        if (Math.abs(fc) < Math.abs(fb)) {
            a = b;
            b = c;
            c = a;
            fa = fb;
            fb = fc;
            fc = fa;
        }

        for (int i = 0; i < MAX_ITER; i++) {
            double tol2 = tol + (4.0 * MathConsts.BIG_INV * Math.abs(b));
            double xm = 0.5 * (c - b);

            if (Math.abs(fb) <= MathConsts.MIN_VAL) {
                return b;
            }
            if (Math.abs(xm) <= tol2) {
                if (Math.abs(b) > MathConsts.MIN_VAL) {
                    return b;
                } else {
                    return 0.0;
                }
            }

            if ((Math.abs(e) >= tol2) && (Math.abs(fa) > Math.abs(fb))) {
                double s;
                double p;
                double q;
                if (a != c) {
                    // inverse quadratic interpolation
                    q = fa / fc;
                    double r = fb / fc;
                    s = fb / fa;
                    p = s * (2.0 * xm * q * (q - r) - (b - a) * (r - 1.0));
                    q = (q - 1.0) * (r - 1.0) * (s - 1.0);
                } else {
                    // linear interpolation
                    s = fb / fa;
                    p = 2.0 * xm * s;
                    q = 1.0 - s;
                }

                // adjust signs
                if (p > 0.0) {
                    q = -q;
                }
                p = Math.abs(p);

                // is interpolation acceptable?
                if (((2.0 * p) >= (3.0 * xm * q - Math.abs(tol2 * q))) || (p >= Math.abs(0.5 * e * q))) {
                    d = xm;
                    e = d;
                } else {
                    e = d;
                    d = p / q;
                }
            } else {
                // bisection necessary
                d = xm;
                e = d;
            }

            a = b;
            fa = fb;
            if (Math.abs(d) > tol2) {
                b += d;
            } else if (xm < 0.0) {
                b -= tol2;
            } else {
                b += tol2;
            }
            fb = f.apply(b);
            if (fb * Math.signum(fc) > 0.0) {
                c = a;
                fc = fa;
                d = e = b - a;
            } else {
                a = b;
                b = c;
                c = a;
                fa = fb;
                fb = fc;
                fc = fa;
            }
        }

        return b;
    }

    private RootFinder() {
        throw new AssertionError();
    }
}
