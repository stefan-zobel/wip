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
 * This is a utility class that provides computation methods related to the Beta
 * family of functions.
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
public final class BetaFun {

    private static final double THRESHOLD = 3.0 * MACH_EPS_DBL;

    /**
     * Returns the Beta function of the arguments.
     * 
     * <pre>
     *                          -         -
     *                         | (alpha) | (beta)
     * Beta( alpha, beta )  =  ------------------.
     *                            -
     *                           | (alpha+beta)
     * </pre>
     * 
     * @param alpha the alpha parameter
     * @param beta the beta parameter
     * @return the value of the beta function
     */
    public static double beta(final double alpha, final double beta) {
        double y = alpha + beta;
        y = GammaFun.gamma(y);
        if (y == 0.0) {
            return 1.0;
        }

        if (alpha > beta) {
            y = GammaFun.gamma(alpha) / y;
            y *= GammaFun.gamma(beta);
        } else {
            y = GammaFun.gamma(beta) / y;
            y *= GammaFun.gamma(alpha);
        }

        return y;
    }

    /**
     * Returns the natural logarithm of the beta function.
     * 
     * @param alpha the alpha parameter
     * @param beta the beta parameter
     * @return the value of the natural log of the beta function
     */
    public static double lnBeta(final double alpha, final double beta) {
        return GammaFun.lnGamma(alpha) + GammaFun.lnGamma(beta)
                - GammaFun.lnGamma(alpha + beta);
    }

    /**
     * Returns the Incomplete Beta Function evaluated from zero to <tt>x</tt>.
     * 
     * @param alpha
     *            the alpha parameter of the beta distribution.
     * @param beta
     *            the beta parameter of the beta distribution.
     * @param x
     *            the integration end point.
     * @return the value of the incomplete beta function
     */
    public static double incompleteBeta(final double alpha, final double beta,
            final double x) {

        if (alpha <= 0.0 || beta <= 0.0) {
            throw new BetaException("Domain error",
                    "alpha <= 0.0 || beta <= 0.0", 1);
        }

        if ((x <= 0.0) || (x >= 1.0)) {
            if (x == 0.0) {
                return 0.0;
            }
            if (x == 1.0) {
                return 1.0;
            }
            throw new BetaException("Domain error", x,
                    "(x < 0.0) || (x > 1.0)", 2);
        }

        if ((beta * x) <= 1.0 && x <= 0.95) {
            return powerSeries(alpha, beta, x);
        }

        double w = 1.0 - x;
        double a;
        double b;
        double xc;
        double x_;

        boolean flag = false;

        /* Reverse a and b if x is greater than the mean. */
        if (x > (alpha / (alpha + beta))) {
            flag = true;
            a = beta;
            b = alpha;
            xc = x;
            x_ = w;
        } else {
            a = alpha;
            b = beta;
            xc = w;
            x_ = x;
        }

        double t;
        if (flag && (b * x_) <= 1.0 && x_ <= 0.95) {
            t = powerSeries(a, b, x_);
            if (t <= MACH_EPS_DBL) {
                t = 1.0 - MACH_EPS_DBL;
            } else {
                t = 1.0 - t;
            }
            return t;
        }

        /* Choose expansion for better convergence. */
        double y = x_ * (a + b - 2.0) - (a - 1.0);
        if (y < 0.0) {
            w = incompleteBetaFraction1(a, b, x_);
        } else {
            w = incompleteBetaFraction2(a, b, x_) / xc;
        }

        // @formatter:off
        /*
         Multiply w by the factor

           a      b   _             _     _
          x  (1-x)   | (a+b) / ( a | (a) | (b) )

        */
        // @formatter:on

        y = a * Math.log(x_);
        t = b * Math.log(xc);
        if ((a + b) < MAX_GAMMA && Math.abs(y) < MAX_LOG
                && Math.abs(t) < MAX_LOG) {
            t = Math.pow(xc, b);
            t *= Math.pow(x_, a);
            t /= a;
            t *= w;
            t *= GammaFun.gamma(a + b) / (GammaFun.gamma(a) * GammaFun.gamma(b));
            if (flag) {
                if (t <= MACH_EPS_DBL) {
                    t = 1.0 - MACH_EPS_DBL;
                } else {
                    t = 1.0 - t;
                }
            }
            return t;
        }

        /* Resort to logarithms */
        y += t + GammaFun.lnGamma(a + b) - GammaFun.lnGamma(a) - GammaFun.lnGamma(b);
        y += Math.log(w / a);
        if (y < MIN_LOG) {
            t = 0.0;
        } else {
            t = Math.exp(y);
        }

        if (flag) {
            if (t <= MACH_EPS_DBL) {
                t = 1.0 - MACH_EPS_DBL;
            } else {
                t = 1.0 - t;
            }
        }
        return t;
    }

    /**
     * Power series for incomplete beta integral.
     * <p/>
     * Use when b*x is small and x not too close to 1.
     * 
     * @param a the a parameter
     * @param b the b parameter
     * @return the value of the power series
     */
    private static double powerSeries(final double a, final double b,
            final double x) {

        double n = 2.0;
        double s = 0.0;

        double u = (1.0 - b) * x;
        double v = u / (a + 1.0);
        double t1 = v;
        double t = u;

        final double ai = 1.0 / a;
        final double z = MACH_EPS_DBL * ai;

        while (Math.abs(v) > z) {
            u = (n - b) * x / n;
            t *= u;
            v = t / (a + n);
            s += v;
            n += 1.0;
        }

        s += t1;
        s += ai;

        u = a * Math.log(x);

        if ((a + b) < MAX_GAMMA && Math.abs(u) < MAX_LOG) {
            t = GammaFun.gamma(a + b) / (GammaFun.gamma(a) * GammaFun.gamma(b));
            s = s * t * Math.pow(x, a);
        } else {
            t = GammaFun.lnGamma(a + b) - GammaFun.lnGamma(a) - GammaFun.lnGamma(b) + u
                    + Math.log(s);
            if (t < MIN_LOG) {
                s = 0.0;
            } else {
                s = Math.exp(t);
            }
        }
        return s;
    }

    /**
     * Continued fraction expansion #1 for incomplete beta integral.
     */
    private static double incompleteBetaFraction1(final double a,
            final double b, final double x) {
        double k1 = a;
        double k2 = a + b;
        double k3 = a;
        double k4 = a + 1.0;
        double k5 = 1.0;
        double k6 = b - 1.0;
        double k7 = k4;
        double k8 = a + 2.0;

        double pkm1 = 1.0;
        double qkm1 = 1.0;
        double pkm2 = 0.0;
        double qkm2 = 1.0;

        double r = 1.0;
        double ans = 1.0;
        int n = 0;

        do {
            double xk = -(x * k1 * k2) / (k3 * k4);
            double pk = pkm1 + pkm2 * xk;
            double qk = qkm1 + qkm2 * xk;
            pkm2 = pkm1;
            pkm1 = pk;
            qkm2 = qkm1;
            qkm1 = qk;

            xk = (x * k5 * k6) / (k7 * k8);
            pk = pkm1 + pkm2 * xk;
            qk = qkm1 + qkm2 * xk;
            pkm2 = pkm1;
            pkm1 = pk;
            qkm2 = qkm1;
            qkm1 = qk;

            if (qk != 0) {
                r = pk / qk;
            }

            double t;
            if (r != 0) {
                t = Math.abs((ans - r) / r);
                ans = r;
            } else {
                t = 1.0;
            }
            if (t < THRESHOLD) {
                return ans;
            }

            k1 += 1.0;
            k2 += 1.0;
            k3 += 2.0;
            k4 += 2.0;
            k5 += 1.0;
            k6 -= 1.0;
            k7 += 2.0;
            k8 += 2.0;

            if ((Math.abs(qk) + Math.abs(pk)) > BIG) {
                pkm2 *= BIG_INV;
                pkm1 *= BIG_INV;
                qkm2 *= BIG_INV;
                qkm1 *= BIG_INV;
            }
            if ((Math.abs(qk) < BIG_INV) || (Math.abs(pk) < BIG_INV)) {
                pkm2 *= BIG;
                pkm1 *= BIG;
                qkm2 *= BIG;
                qkm1 *= BIG;
            }
        } while (++n < 300);

        return ans;
    }

    /**
     * Continued fraction expansion #2 for incomplete beta integral.
     */
    private static double incompleteBetaFraction2(final double a,
            final double b, final double x) {
        double k1 = a;
        double k2 = b - 1.0;
        double k3 = a;
        double k4 = a + 1.0;
        double k5 = 1.0;
        double k6 = a + b;
        double k7 = a + 1.0;
        double k8 = a + 2.0;

        double pkm1 = 1.0;
        double qkm1 = 1.0;
        double pkm2 = 0.0;
        double qkm2 = 1.0;

        final double z = x / (1.0 - x);

        double r = 1.0;
        double ans = 1.0;
        int n = 0;

        do {
            double xk = -(z * k1 * k2) / (k3 * k4);
            double pk = pkm1 + pkm2 * xk;
            double qk = qkm1 + qkm2 * xk;
            pkm2 = pkm1;
            pkm1 = pk;
            qkm2 = qkm1;
            qkm1 = qk;

            xk = (z * k5 * k6) / (k7 * k8);
            pk = pkm1 + pkm2 * xk;
            qk = qkm1 + qkm2 * xk;
            pkm2 = pkm1;
            pkm1 = pk;
            qkm2 = qkm1;
            qkm1 = qk;

            if (qk != 0) {
                r = pk / qk;
            }

            double t;
            if (r != 0) {
                t = Math.abs((ans - r) / r);
                ans = r;
            } else {
                t = 1.0;
            }
            if (t < THRESHOLD) {
                return ans;
            }

            k1 += 1.0;
            k2 -= 1.0;
            k3 += 2.0;
            k4 += 2.0;
            k5 += 1.0;
            k6 += 1.0;
            k7 += 2.0;
            k8 += 2.0;

            if ((Math.abs(qk) + Math.abs(pk)) > BIG) {
                pkm2 *= BIG_INV;
                pkm1 *= BIG_INV;
                qkm2 *= BIG_INV;
                qkm1 *= BIG_INV;
            }
            if ((Math.abs(qk) < BIG_INV) || (Math.abs(pk) < BIG_INV)) {
                pkm2 *= BIG;
                pkm1 *= BIG;
                qkm2 *= BIG;
                qkm1 *= BIG;
            }
        } while (++n < 300);

        return ans;
    }

    private BetaFun() {
    }
}
