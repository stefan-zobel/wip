/*
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
/*
 * Copyright © 1999 CERN - European Organization for Nuclear Research.
 * Permission to use, copy, modify, distribute and sell this software and
 * its documentation for any purpose is hereby granted without fee, provided
 * that the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation. CERN makes no representations about the suitability of this
 * software for any purpose. It is provided "as is" without expressed or
 * implied warranty.
 */
package math.cern;

import static math.MathConsts.*;

/**
 * This is a utility class that provides computation methods related to the
 * &Gamma; (Gamma) family of functions.
 * <p>
 * <b>Implementation:</b>
 * </p>
 * Some code taken and adapted from the <A
 * HREF="http://www.sci.usq.edu.au/staff/leighb/graph/Top.html">Java 2D Graph
 * Package 2.4</A>, which in turn is a port from the <A
 * HREF="http://people.ne.mediaone.net/moshier/index.html#Cephes">Cephes 2.2</A>
 * Math Library (C). Most Cephes code (missing from the 2D Graph Package)
 * directly ported.
 * 
 * @author wolfgang.hoschek@cern.ch
 */
public final class GammaFun {

    //@formatter:off
    private static final double STIR[] = { 7.87311395793093628397E-4,
            -2.29549961613378126380E-4, -2.68132617805781232825E-3,
            3.47222221605458667310E-3, 8.33333333333482257126E-2, };

    private static final double MAX_STIR = 143.01608;

    private static final double P[] = { 1.60119522476751861407E-4,
            1.19135147006586384913E-3, 1.04213797561761569935E-2,
            4.76367800457137231464E-2, 2.07448227648435975150E-1,
            4.94214826801497100753E-1, 9.99999999999999996796E-1 };

    private static final double Q[] = { -2.31581873324120129819E-5,
            5.39605580493303397842E-4, -4.45641913851797240494E-3,
            1.18139785222060435552E-2, 3.58236398605498653373E-2,
            -2.34591795718243348568E-1, 7.14304917030273074085E-2,
            1.00000000000000000320E0 };

    private static final double A[] = { 8.11614167470508450300E-4,
            -5.95061904284301438324E-4, 7.93650340457716943945E-4,
            -2.77777777730099687205E-3, 8.33333333333331927722E-2 };

    private static final double B[] = { -1.37825152569120859100E3,
            -3.88016315134637840924E4, -3.31612992738871184744E5,
            -1.16237097492762307383E6, -1.72173700820839662146E6,
            -8.53555664245765465627E5 };

    private static final double C[] = { -3.51815701436523470549E2,
            -1.70642106651881159223E4, -2.20528590553854454839E5,
            -1.13933444367982507207E6, -2.53252307177582951285E6,
            -2.01889141433532773231E6 };

    private static final double DIGAMMA_C7[][] = {
            {1.3524999667726346383e4, 4.5285601699547289655e4, 4.5135168469736662555e4,
             1.8529011818582610168e4, 3.3291525149406935532e3, 2.4068032474357201831e2,
             5.1577892000139084710, 6.2283506918984745826e-3},
            {6.9389111753763444376e-7, 1.9768574263046736421e4, 4.1255160835353832333e4,
               2.9390287119932681918e4, 9.0819666074855170271e3,
               1.2447477785670856039e3, 6.7429129516378593773e1, 1.0}
           };

    private static final double DIGAMMA_C4[][] = {
            {-2.728175751315296783e-15, -6.481571237661965099e-1, -4.486165439180193579,
             -7.016772277667586642, -2.129404451310105168},
            {7.777885485229616042, 5.461177381032150702e1,
             8.929207004818613702e1, 3.227034937911433614e1, 1.0}
           };

    /* Chebyshev coefficients for trigamma (x + 3), 0 <= x <= 1. In Yudell
       Luke: The special functions and their approximations, Vol. II,
       Academic Press, p. 301, 1969. */
    private static final int TRIGAMMA_N = 15;

    private static final double TRIGAMMA_A[] = { 2.0 * 0.33483869791094938576,
            -0.05518748204873009463, 0.00451019073601150186, -0.00036570588830372083,
            2.943462746822336e-5, -2.35277681515061e-6, 1.8685317663281e-7,
            -1.475072018379e-8, 1.15799333714e-9, -9.043917904e-11,
            7.029627e-12, -5.4398873e-13, 0.4192525e-13, -3.21903e-15, 0.2463e-15,
            -1.878e-17, 0.0, 0.0 };
    //@formatter:on

    /**
     * Returns the Gamma function of the argument.
     * 
     * @param x x value
     * @return gamma(x)
     */
    public static double gamma(double x) {
        double q = Math.abs(x);
        double p;
        double z;

        if (q > 33.0) {
            if (x < 0.0) {
                p = Math.floor(q);
                if (p == q) {
                    throw new GammaException("Overflow", x, "p == q", 1);
                }

                z = q - p;
                if (z > 0.5) {
                    p += 1.0;
                    z = q - p;
                }
                z = q * Math.sin(Math.PI * z);
                if (z == 0.0) {
                    throw new GammaException("Overflow", x, "z == 0.0", 2);
                }
                z = Math.abs(z);
                z = Math.PI / (z * stirlingFormula(q));

                return -z;
            } else {
                return stirlingFormula(x);
            }
        }

        z = 1.0;
        while (x >= 3.0) {
            x -= 1.0;
            z *= x;
        }

        while (x < 0.0) {
            if (x == 0.0) {
                throw new GammaException("Singular", "x == 0.0", 3);
            } else if (x > -1.E-9) {
                return (z / ((1.0 + 0.5772156649015329 * x) * x));
            }
            z /= x;
            x += 1.0;
        }

        while (x < 2.0) {
            if (x == 0.0) {
                throw new GammaException("Singular", "x == 0.0", 4);
            } else if (x < 1.e-9) {
                return (z / ((1.0 + 0.5772156649015329 * x) * x));
            }
            z /= x;
            x += 1.0;
        }

        if ((x == 2.0) || (x == 3.0)) {
            return z;
        }

        x -= 2.0;
        p = Polynomial.polevl(x, P, 6);
        q = Polynomial.polevl(x, Q, 7);
        return z * p / q;
    }

    /**
     * Returns the natural logarithm of the gamma function.
     * 
     * @param x x value
     * @return ln(gamma(x))
     */
    public static double lnGamma(double x) {
        double p;
        double q;

        if (x < -34.0) {
            q = -x;
            final double w = lnGamma(q);
            p = Math.floor(q);
            if (p == q) {
                throw new GammaException("Overflow", x, "p == q", 1);
            }
            double z = q - p;
            if (z > 0.5) {
                p += 1.0;
                z = p - q;
            }
            z = q * Math.sin(Math.PI * z);
            if (z == 0.0) {
                throw new GammaException("Overflow", x, "z == 0.0", 2);
            }
            z = LN_PI - Math.log(z) - w;
            return z;
        }

        if (x < 13.0) {
            double z = 1.0;
            while (x >= 3.0) {
                x -= 1.0;
                z *= x;
            }
            while (x < 2.0) {
                if (x == 0.0) {
                    throw new GammaException("Overflow", "x == 0.0", 3);
                }
                z /= x;
                x += 1.0;
            }
            if (z < 0.0) {
                z = -z;
            }
            if (x == 2.0) {
                return Math.log(z);
            }
            x -= 2.0;
            p = x * Polynomial.polevl(x, B, 5) / Polynomial.p1evl(x, C, 6);
            return (Math.log(z) + p);
        }

        if (x > 2.556348e305) {
            throw new GammaException("Overflow", "x > 2.556348e305", 4);
        }

        q = (x - 0.5) * Math.log(x) - x + 0.91893853320467274178;
        if (x > 1.0e8) {
            return q;
        }

        p = 1.0 / (x * x);
        if (x >= 1000.0) {
            q += ((7.9365079365079365079365e-4 * p - 2.7777777777777777777778e-3)
                    * p + 0.0833333333333333333333)
                    / x;
        } else {
            q += Polynomial.polevl(p, A, 4) / x;
        }
        return q;
    }

    /**
     * Returns the Incomplete Gamma function.
     * 
     * @param a
     *            the parameter of the gamma distribution.
     * @param x
     *            the integration end point.
     * @return the incomplete gamma of x
     */
    public static double incompleteGamma(final double a, final double x) {
        if (x <= 0 || a <= 0) {
            return 0.0;
        }

        if (x > 1.0 && x > a) {
            return 1.0 - incompleteGammaComplement(a, x);
        }

        /* Compute x**a * exp(-x) / gamma(a) */
        final double ax = a * Math.log(x) - x - lnGamma(a);
        if (ax < -MAX_LOG) {
            return 0.0;
        }

        /* power series */
        double r = a;
        double c = 1.0;
        double ans = 1.0;

        do {
            r += 1.0;
            c *= x / r;
            ans += c;
        } while (c / ans > MACH_EPS_DBL);

        return (ans * Math.exp(ax) / a);
    }

    /**
     * Returns the Complemented Incomplete Gamma function.
     * 
     * @param a
     *            the parameter of the gamma distribution.
     * @param x
     *            the integration start point.
     * @return the complemented incomplete gamma of x
     */
    public static double incompleteGammaComplement(final double a,
            final double x) {
        if (x <= 0 || a <= 0) {
            return 1.0;
        }

        if (x < 1.0 || x < a) {
            return 1.0 - incompleteGamma(a, x);
        }

        final double ax = a * Math.log(x) - x - lnGamma(a);
        if (ax < -MAX_LOG) {
            return 0.0;
        }

        /* continued fraction */
        double y = 1.0 - a;
        double z = x + y + 1.0;
        double c = 0.0;
        double pkm2 = 1.0;
        double qkm2 = x;
        double pkm1 = x + 1.0;
        double qkm1 = z * x;
        double ans = pkm1 / qkm1;

        double t;

        do {
            c += 1.0;
            y += 1.0;
            z += 2.0;
            final double yc = y * c;
            final double pk = pkm1 * z - pkm2 * yc;
            final double qk = qkm1 * z - qkm2 * yc;
            if (qk != 0) {
                final double r = pk / qk;
                t = Math.abs((ans - r) / r);
                ans = r;
            } else {
                t = 1.0;
            }

            pkm2 = pkm1;
            pkm1 = pk;
            qkm2 = qkm1;
            qkm1 = qk;
            if (Math.abs(pk) > BIG) {
                pkm2 *= BIG_INV;
                pkm1 *= BIG_INV;
                qkm2 *= BIG_INV;
                qkm1 *= BIG_INV;
            }
        } while (t > MACH_EPS_DBL);

        return ans * Math.exp(ax);
    }

    /**
     * Returns the Gamma function computed by Stirling's formula. The polynomial
     * STIR is valid for 33 <= x <= 172.
     */
    private static double stirlingFormula(final double x) {
        double w = 1.0 / x;
        w = 1.0 + w * Polynomial.polevl(w, STIR, 4);
        double y = Math.exp(x);

        if (x > MAX_STIR) {
            /* Avoid overflow in Math.pow() */
            final double v = Math.pow(x, 0.5 * x - 0.25);
            y = v * (v / y);
        } else {
            y = Math.pow(x, x - 0.5) / y;
        }
        y = SQRT_TWO_PI * y * w;
        return y;
    }

    /**
     * Returns the value of the logarithmic derivative of the Gamma
     * function {@code psi(x) = Gamma’(x) / Gamma(x)}.
     * 
     * @param x x value
     * @return digamma(x)
     */
    public static double digamma(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }
        double digX = 0.0;
        if (x >= 3.0) {
            double prodPj = 0.0;
            double prodQj = 0.0;
            double x2 = 1.0 / (x * x);
            for (int j = 4; j >= 0; j--) {
                prodPj = prodPj * x2 + DIGAMMA_C4[0][j];
                prodQj = prodQj * x2 + DIGAMMA_C4[1][j];
            }
            digX = Math.log(x) - (0.5 / x) + (prodPj / prodQj);
        } else if (x >= 0.5) {
            double prodPj = 0.0;
            double prodQj = 0.0;
            for (int j = 7; j >= 0; j--) {
                prodPj = x * prodPj + DIGAMMA_C7[0][j];
                prodQj = x * prodQj + DIGAMMA_C7[1][j];
            }
            digX = (x - 1.46163214496836234126) * (prodPj / prodQj);
        } else {
            double f = (1.0 - x) - Math.floor(1.0 - x);
            digX = digamma(1.0 - x) + (Math.PI / Math.tan(Math.PI * f));
        }
        return digX;
    }

    /**
     * Returns the value of the trigamma function {@code d(psi(x))/dx}, the
     * derivative of the {@link #digamma(double)} function, evaluated at
     * {@code x}.
     * 
     * @param x x value
     * @return trigamma(x)
     */
    public static double trigamma(double x) {
        if (Double.isNaN(x)) {
            return Double.NaN;
        }

        double y;
        double sum;

        if (x < 0.5) {
            y = (1.0 - x) - Math.floor(1.0 - x);
            sum = Math.PI / Math.sin(Math.PI * y);
            return (sum * sum) - trigamma(1.0 - x);
        }

        if (x >= 40.0) {
            // asymptotic series
            y = 1.0 / (x * x);
            sum = 1.0 + y * (1.0 / 6.0 - y * (1.0 / 30.0 - y * (1.0 / 42.0 - 1.0 / 30.0 * y)));
            sum += 0.5 / x;
            return sum / x;
        }

        int p = (int) x;
        y = x - p;
        sum = 0.0;

        if (p > 3) {
            for (int i = 3; i < p; i++) {
                sum -= 1.0 / ((y + i) * (y + i));
            }
        } else if (p < 3) {
            for (int i = 2; i >= p; i--) {
                sum += 1.0 / ((y + i) * (y + i));
            }
        }

        return sum + Polynomial.evalChebyStar(TRIGAMMA_A, TRIGAMMA_N, y);
    }

    private GammaFun() {
    }
}
