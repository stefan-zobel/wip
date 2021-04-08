/*
 * Class:        KolmogorovSmirnovPlusDist
 * Description:  Kolmogorov-Smirnov+ distribution
 * Environment:  Java
 * Software:     SSJ 
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
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
package math.stats.fit;

import math.cern.Arithmetic;

/**
 * Methods for the *Kolmogorov–Smirnov+* distribution of the
 * {@code Kolmogorov–Smirnov} test statistic {@code D_n^+} given an ordered
 * sample of {@code n} independent uniforms {@code U_i} over {@code (0,1)}.
 * <p>
 * https://en.wikipedia.org/wiki/Kolmogorov%E2%80%93Smirnov_test
 */
final class KolmogorovSmirnovP {

    private static final double EPSILON = 1.0e-12;

    /**
     * Computes the <b>complementary</b> distribution function
     * bar(F)<sup>n</sup>(<em>x</em>) with parameter {@code n}.
     */
    static double barF(int n, double x) {
        if (n <= 0) {
            throw new IllegalArgumentException("n <= 0 : " + n);
        }
        if (x <= 0.0) {
            return 1.0;
        }
        if ((x >= 1.0) || (n * x * x >= 365.0)) {
            return 0.0;
        }
        if (n == 1) {
            return 1.0 - x;
        }

        // the alternating series is stable and fast for n*x very small
        // (frontier: alternating series)
        if (n * x <= 6.5) {
            return 1.0 - cdf(n, x);
        }

        // (frontier: asymptotic)
        if (n >= 200000) {
            return kolmoSmirnovPlusBarAsymp(n, x);
        }

        // (frontier: non-alternating series)
        if ((n <= 4000) || (n * x * x > 1.0)) {
            return kolmoSmirnovPlusBarUpper(n, x);
        }

        return kolmoSmirnovPlusBarAsymp(n, x);
    }

    /**
     * Computes the *Kolmogorov–Smirnov+* distribution function {@code F_n(x)}
     * with parameter {@code n}. * The relative error on
     * {@code F_n(x) = P[D_n^+ <= x]} is always less than {@code 10^-5}.
     * 
     * @param n
     * @param x
     * @return value of the distribution function {@code F_n(x)}
     */
    static double cdf(int n, double x) {
        if (n <= 0) {
            throw new IllegalArgumentException("n <= 0 : " + n);
        }
        if (x <= 0.0) {
            return 0.0;
        }
        if ((x >= 1.0) || (n * x * x >= 25.0)) {
            return 1.0;
        }
        if (n == 1) {
            return x;
        }

        double q;
        double term;
        double sum = 0.0;
        double logCom = Math.log((double) n);

        // --------------------------------------------------------------
        // the alternating series is stable and fast for n*x very small
        // --------------------------------------------------------------

        // (frontier: alternating series)
        if (n * x <= 6.5) {
            int jmax = (int) (n * x);
            int sign = -1;
            for (int j = 1; j <= jmax; j++) {
                double jreal = j;
                double njreal = n - j;
                q = jreal / n - x;
                // we must avoid log(0.0) for j = jmax and n*x near an integer
                if (-q > Double.MIN_NORMAL) {
                    term = logCom + jreal * Math.log(-q) + (njreal - 1.0) * Math.log1p(-q);
                    sum += sign * Math.exp(term);
                }
                sign = -sign;
                logCom += Math.log(njreal / (j + 1));
            }
            // add the term j = 0
            sum += Math.exp((n - 1) * Math.log1p(x));
            return sum * x;
        }

        // -----------------------------------------------------------
        // For nx > NxParam (= 6.5), we use the other exact series for small
        // n, and the asymptotic form for n larger than NPARAM (= 4000)
        // -----------------------------------------------------------

        // (frontier: non-alternating series)
        if (n <= 4000) {
            int jmax = (int) (n * (1.0 - x));
            if (1.0 - x - (double) jmax / n <= 0.0) {
                --jmax;
            }
            for (int j = 1; j <= jmax; j++) {
                double jreal = j;
                double njreal = n - j;
                q = jreal / n + x;
                term = logCom + (jreal - 1.0) * Math.log(q) + njreal * Math.log1p(-q);
                sum += Math.exp(term);
                logCom += Math.log(njreal / (jreal + 1.0));
            }
            sum *= x;

            // add the term j = 0; avoid log(0.0)
            if (1.0 > x) {
                sum += Math.exp(n * Math.log1p(-x));
            }
            return 1.0 - sum;
        }

        // --------------------------
        // Use an asymptotic formula
        // --------------------------

        term = 2.0 / 3.0;
        q = x * x * n;
        sum = 1.0 - Math.exp(-2.0 * q)
                * (1.0 - term * x * (1.0 - x * (1.0 - term * q) - term / n * (0.2 - 19.0 / 15.0 * q + term * q * q)));
        return sum;
    }

    static double kolmoSmirnovPlusBarUpper(int n, double x) {
        /*
         * Compute the probability of the complementary KS+ distribution in the
         * upper tail using Smirnov's stable formula
         */
        if (n > 200000) {
            return kolmoSmirnovPlusBarAsymp(n, x);
        }

        int jmax = (int) (n * (1.0 - x));
        // Avoid log(0) for j = jmax and q ~ 1.0
        if ((1.0 - x - (double) jmax / n) <= 0.0) {
            jmax--;
        }

        final int jdiv = (n > 3000) ? 2 : 3;
        int j = jmax / jdiv + 1;

        double logCom = Arithmetic.logFactorial(n) - Arithmetic.logFactorial(j) - Arithmetic.logFactorial(n - j);
        final double LOGJM = logCom;
        double sum = 0.0;

        while (j <= jmax) {
            double q = (double) j / n + x;
            double term = logCom + (j - 1) * Math.log(q) + (n - j) * Math.log1p(-q);
            double t = Math.exp(term);
            sum += t;
            logCom += Math.log((double) (n - j) / (j + 1));
            if (t <= sum * EPSILON) {
                break;
            }
            j++;
        }

        j = jmax / jdiv;
        logCom = LOGJM + Math.log((double) (j + 1) / (n - j));

        while (j > 0) {
            double q = (double) j / n + x;
            double term = logCom + (j - 1) * Math.log(q) + (n - j) * Math.log1p(-q);
            double t = Math.exp(term);
            sum += t;
            logCom += Math.log((double) j / (n - j + 1));
            if (t <= sum * EPSILON) {
                break;
            }
            j--;
        }

        sum *= x;
        // add the term j = 0
        sum += Math.exp(n * Math.log1p(-x));
        return sum;
    }

    private static double kolmoSmirnovPlusBarAsymp(int n, double x) {
        /*
         * Compute the probability of the complementary KS+ distribution using
         * an asymptotic formula
         */
        double t = (6.0 * n * x + 1);
        double z = t * t / (18.0 * n);
        double v = 1.0 - (2.0 * z * z - 4.0 * z - 1.0) / (18.0 * n);
        if (v <= 0.0) {
            return 0.0;
        }
        v = v * Math.exp(-z);
        if (v >= 1.0) {
            return 1.0;
        }
        return v;
    }

    private KolmogorovSmirnovP() {
        throw new AssertionError();
    }
}
