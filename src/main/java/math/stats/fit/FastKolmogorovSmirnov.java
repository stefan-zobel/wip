/*
 * Class:        KolmogorovSmirnovDistQuick
 * Description:  Kolmogorov-Smirnov 2-sided 1-sample distribution
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
 * @author       Richard Simard
 * @since        January 2010
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

import static math.MathConsts.PI_SQUARED;
import static math.MathConsts.SQRT_PI_HALF;

import math.MathConsts;
import math.cern.Arithmetic;

/**
 * Methods for the two-sided *Kolmogorov–Smirnov* distribution of the
 * {@code Kolmogorov–Smirnov} test statistic {@code D_n} given an ordered sample
 * of {@code n} independent uniforms {@code U_i} over {@code (0,1)}.
 * <p>
 * https://www.jstatsoft.org/article/view/v039i11
 * <p>
 * https://en.wikipedia.org/wiki/Kolmogorov%E2%80%93Smirnov_test
 */
final class FastKolmogorovSmirnov {

    private static final int NEXACT = 500;

    private static final double PI2 = PI_SQUARED;
    private static final double PI4 = PI2 * PI2;

    // for the Durbin matrix algorithm
    private static final double NORM = 1.0e140;
    private static final double INORM = 1.0e-140;
    private static final int LOGNORM = 140;

    /*
     * For n <= NEXACT, we use exact algorithms: the Durbin matrix and the
     * Pomeranz algorithms. For n > NEXACT, we use asymptotic methods except for
     * x close to 0 where we still use the method of Durbin for n <= NKOLMO. For
     * n > NKOLMO, we use asymptotic methods only and so the precision is less
     * for x close to 0. We could increase the limit NKOLMO to 10^6 to get
     * better precision for x close to 0, but at the price of a slower speed.
     */
    private static final int NKOLMO = 100_000;

    /**
     * Computes the <b>complementary</b> *Kolmogorov–Smirnov* distribution
     * {@code P[D_n >= x]} with parameter {@code n}, in a form that is more
     * precise in the upper tail.
     * <p>
     * It returns at least 10 decimal digits of precision everywhere for all
     * {@code n <= 500}, at least 6 decimal digits of precision for
     * {@code 500 < n <= 200_000}, and a few correct decimal digits (1 to 5) for
     * {@code n > 200_000}.
     * <p>
     * Restriction: {@code n >= 1}
     */
    static double barF(int n, double x) {
        double v = barFConnu(n, x);
        if (v >= 0.0) {
            return v;
        }

        final double w = n * x * x;
        if (n <= NEXACT) {
            if (w < 4.0) {
                return 1.0 - cdf(n, x);
            } else {
                return 2.0 * KolmogorovSmirnovP.kolmoSmirnovPlusBarUpper(n, x);
            }
        }

        if (w >= 2.65) {
            return 2.0 * KolmogorovSmirnovP.kolmoSmirnovPlusBarUpper(n, x);
        }

        return 1.0 - cdf(n, x);
    }

    /**
     * Computes the *Kolmogorov–Smirnov* distribution function
     * {@code u = P[D_n <= x]} with parameter {@code n}.
     * <p>
     * This method uses Pomeranz’s recursion algorithm and the Durbin matrix
     * algorithm for
     * {@code n <= 500}, which returns at least 13 decimal digits of precision.
     * It uses the Pelz-Good asymptotic expansion in the central part of the
     * range for {@code n > 500} and returns at least 7 decimal digits of
     * precision everywhere for
     * {@code 500 < n <= 100_000}. For {@code n > 100_000}, it returns at least
     * 5 decimal digits of precision for all {@code u > 10^(-16)}, and a few
     * correct decimals when {@code u <= 10^(-16)}.
     * <p>
     * Restriction: {@code n >= 1}
     */
    static double cdf(int n, double x) {
        double u = cdfConnu(n, x);
        if (u >= 0.0) {
            return u;
        }

        final double w = n * x * x;
        if (n <= NEXACT) {
            if (w < 0.754693) {
                return durbinMatrix(n, x);
            }
            if (w < 4.0) {
                return pomeranz(n, x);
            }
            return 1.0 - barF(n, x);
        }

        if ((w * x * n <= 7.0) && (n <= NKOLMO)) {
            return durbinMatrix(n, x);
        }

        return pelz(n, x);
    }

    private static double barFConnu(int n, double x) {
        final double w = n * x * x;

        if ((w >= 370.0) || (x >= 1.0)) {
            return 0.0;
        }
        if ((w <= 0.0274) || (x <= 0.5 / n)) {
            return 1.0;
        }
        if (n == 1) {
            return 2.0 - 2.0 * x;
        }

        if (x <= 1.0 / n) {
            double t = 2.0 * x * n - 1.0;
            if (n <= NEXACT) {
                double v = Arithmetic.factoPow(n);
                return 1.0 - v * Math.pow(t, (double) n);
            }
            double v = Arithmetic.logFactorial(n) + n * Math.log(t / n);
            return 1.0 - Math.exp(v);
        }

        if (x >= 1.0 - 1.0 / n) {
            return 2.0 * Math.pow(1.0 - x, (double) n);
        }

        return -1.0;
    }

    private static double cdfConnu(int n, double x) {
        // For nx^2 > 18, barF(n, x) is smaller than 5e-16
        if ((n * x * x >= 18.0) || (x >= 1.0)) {
            return 1.0;
        }
        if (x <= 0.5 / n) {
            return 0.0;
        }
        if (n == 1) {
            return 2.0 * x - 1.0;
        }

        if (x <= 1.0 / n) {
            double t = 2.0 * x * n - 1.0;
            if (n <= NEXACT) {
                double w = Arithmetic.factoPow(n);
                return w * Math.pow(t, (double) n);
            }
            double w = Arithmetic.logFactorial(n) + n * Math.log(t / n);
            return Math.exp(w);
        }

        if (x >= 1.0 - 1.0 / n) {
            return 1.0 - 2.0 * Math.pow(1.0 - x, (double) n);
        }

        return -1.0;
    }

    private static double pomeranz(int n, double x) {
        // The Pomeranz algorithm to compute the KS distribution
        final double EPS = 1.0e-15;
        final int ENO = 350;
        // for renormalization of V
        final double RENO = Math.scalb(1.0, ENO);
        final double t = n * x;

        final double[] A = new double[2 * n + 3];
        final double[] Atflo = new double[2 * n + 3];
        final double[] Atcei = new double[2 * n + 3];
        final double[][] V = new double[2][n + 2];
        final double[][] H = new double[4][n + 2]; // = pow(w, j) / factorial(j)

        calcFloorCeil(n, t, A, Atflo, Atcei);

        for (int j = 1; j <= n + 1; j++) {
            V[0][j] = 0.0;
        }
        for (int j = 2; j <= n + 1; j++) {
            V[1][j] = 0.0;
        }
        V[1][1] = RENO;

        // Precompute H[][] = (A[j] - A[j-1]^k / k!
        H[0][0] = 1.0;
        double w = 2.0 * A[2] / n;
        for (int j = 1; j <= n + 1; j++) {
            H[0][j] = w * H[0][j - 1] / j;
        }

        H[1][0] = 1.0;
        w = (1.0 - 2.0 * A[2]) / n;
        for (int j = 1; j <= n + 1; j++) {
            H[1][j] = w * H[1][j - 1] / j;
        }

        H[2][0] = 1.0;
        w = A[2] / n;
        for (int j = 1; j <= n + 1; j++) {
            H[2][j] = w * H[2][j - 1] / j;
        }

        H[3][0] = 1.0;
        for (int j = 1; j <= n + 1; j++) {
            H[3][j] = 0.0;
        }

        int coreno = 1; // counter: how many renormalizations
        int r1 = 0; // Indices i and i-1 for V[i][]
        int r2 = 1;
        double sum;

        for (int i = 2; i <= 2 * n + 2; i++) {
            int jlow = (int) (2 + Atflo[i]);
            if (jlow < 1) {
                jlow = 1;
            }
            int jup = (int) (Atcei[i]);
            if (jup > n + 1) {
                jup = n + 1;
            }

            int klow = (int) (2 + Atflo[i - 1]);
            if (klow < 1) {
                klow = 1;
            }
            int kup0 = (int) (Atcei[i - 1]);

            // find to which case it corresponds
            w = (A[i] - A[i - 1]) / n;
            int s = -1;
            for (int j = 0; j < 4; j++) {
                if (Math.abs(w - H[j][1]) <= EPS) {
                    s = j;
                    break;
                }
            }

            double minsum = RENO;
            r1 = (r1 + 1) & 1; // i - 1
            r2 = (r2 + 1) & 1; // i

            for (int j = jlow; j <= jup; j++) {
                int kup = kup0;
                if (kup > j) {
                    kup = j;
                }
                sum = 0.0;
                for (int k = kup; k >= klow; k--) {
                    sum += V[r1][k] * H[s][j - k];
                }
                V[r2][j] = sum;
                if (sum < minsum) {
                    minsum = sum;
                }
            }

            if (minsum < 1.0e-280) {
                // V is too small: renormalize to avoid underflow of
                // probabilities
                for (int j = jlow; j <= jup; j++) {
                    V[r2][j] *= RENO;
                }
                coreno++; // keep track of log of RENO
            }
        }

        sum = V[r2][n + 1];
        w = Arithmetic.logFactorial(n) - coreno * ENO * MathConsts.LN_2 + Math.log(sum);
        if (w >= 0.) {
            return 1.0;
        }
        return Math.exp(w);
    }

    private static void calcFloorCeil(int n /* sample size */,
            double t /* = nx */, double[] A /* A_i */,
            double[] Atflo /* floor (A_i - t) */,
            double[] Atcei /* ceiling(A_i + t) */
    ) {
        // Precompute A_i, floors, and ceilings for limits of sums in the
        // Pomeranz algorithm
        final int ell = (int) t; // floor(t)
        final double w = Math.ceil(t) - t;
        double z = t - ell; // t - floor(t)

        if (z > 0.5) {
            for (int i = 2; i <= 2 * n + 2; i += 2) {
                Atflo[i] = i / 2 - 2 - ell;
            }
            for (int i = 1; i <= 2 * n + 2; i += 2) {
                Atflo[i] = i / 2 - 1 - ell;
            }

            for (int i = 2; i <= 2 * n + 2; i += 2) {
                Atcei[i] = i / 2 + ell;
            }
            for (int i = 1; i <= 2 * n + 2; i += 2) {
                Atcei[i] = i / 2 + 1 + ell;
            }

        } else if (z > 0.0) {
            for (int i = 1; i <= 2 * n + 2; i++) {
                Atflo[i] = i / 2 - 1 - ell;
            }

            for (int i = 2; i <= 2 * n + 2; i++) {
                Atcei[i] = i / 2 + ell;
            }
            Atcei[1] = 1.0 + ell;

        } else { // z == 0.0
            for (int i = 2; i <= 2 * n + 2; i += 2) {
                Atflo[i] = i / 2 - 1 - ell;
            }
            for (int i = 1; i <= 2 * n + 2; i += 2) {
                Atflo[i] = i / 2 - ell;
            }

            for (int i = 2; i <= 2 * n + 2; i += 2) {
                Atcei[i] = i / 2 - 1 + ell;
            }
            for (int i = 1; i <= 2 * n + 2; i += 2) {
                Atcei[i] = i / 2 + ell;
            }
        }

        if (w < z) {
            z = w;
        }
        A[0] = A[1] = 0.0;
        A[2] = z;
        A[3] = 1.0 - A[2];
        for (int i = 4; i <= 2 * n + 1; i++) {
            A[i] = A[i - 2] + 1.0;
        }
        A[2 * n + 2] = n;
    }

    private static double durbinMatrix(int n, double d) {

        final int k = (int) (n * d) + 1;
        final int m = 2 * k - 1;
        final double h = k - n * d;
        final double[] H = new double[m * m];
        final double[] Q = new double[m * m];
        final int[] pQ = new int[1];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                if (i - j + 1 < 0) {
                    H[i * m + j] = 0;
                } else {
                    H[i * m + j] = 1;
                }
            }
        }
        for (int i = 0; i < m; i++) {
            H[i * m] -= Math.pow(h, (double) (i + 1));
            H[(m - 1) * m + i] -= Math.pow(h, (double) (m - i));
        }
        H[(m - 1) * m] += (2.0 * h - 1.0 > 0.0 ? Math.pow(2.0 * h - 1.0, (double) m) : 0.0);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                if (i - j + 1 > 0) {
                    for (int g = 1; g <= i - j + 1; g++) {
                        H[i * m + j] /= g;
                    }
                }
            }
        }

        mPower(H, 0, Q, pQ, m, n);
        double s = Q[(k - 1) * m + k - 1];

        for (int i = 1; i <= n; i++) {
            s = s * (double) i / n;
            if (s < INORM) {
                s *= NORM;
                pQ[0] -= LOGNORM;
            }
        }
        s *= Math.pow(10.0, (double) pQ[0]);
        return s;
    }

    private static double pelz(int n, double x) {
        /*
         * Approximating the Lower Tail-Areas of the Kolmogorov-Smirnov
         * One-Sample Statistic
         * 
         * Wolfgang Pelz and I. J. Good, Journal of the Royal Statistical
         * Society, Series B. Vol. 38, No. 2 (1976), pp. 152-156
         */
        final int JMAX = 20;
        final double EPS = 1.0e-10;
        final double RACN = Math.sqrt((double) n);
        final double z = RACN * x;
        final double z2 = z * z;
        final double z4 = z2 * z2;
        final double z6 = z4 * z2;
        final double w = PI2 / (2.0 * z * z);

        int j = 0;
        double term = 1.0;
        double sum = 0.0;

        while (j <= JMAX && term > EPS * sum) {
            double ti = j + 0.5;
            term = Math.exp(-ti * ti * w);
            sum += term;
            j++;
        }
        sum *= MathConsts.SQRT_TWO_PI / z;

        term = 1.0;
        double tom = 0.0;
        j = 0;
        while (j <= JMAX && Math.abs(term) > EPS * Math.abs(tom)) {
            double ti = j + 0.5;
            term = (PI2 * ti * ti - z2) * Math.exp(-ti * ti * w);
            tom += term;
            j++;
        }
        sum += tom * SQRT_PI_HALF / (RACN * 3.0 * z4);

        term = 1.0;
        tom = 0.0;
        j = 0;
        while (j <= JMAX && Math.abs(term) > EPS * Math.abs(tom)) {
            double ti = j + 0.5;
            term = 6.0 * z6 + 2.0 * z4 + PI2 * (2.0 * z4 - 5.0 * z2) * ti * ti
                    + PI4 * (1.0 - 2.0 * z2) * ti * ti * ti * ti;
            term *= Math.exp(-ti * ti * w);
            tom += term;
            j++;
        }
        sum += tom * SQRT_PI_HALF / (n * 36.0 * z * z6);

        term = 1.0;
        tom = 0.0;
        j = 1;
        while (j <= JMAX && term > EPS * tom) {
            double ti = j;
            term = PI2 * ti * ti * Math.exp(-ti * ti * w);
            tom += term;
            j++;
        }
        sum -= tom * SQRT_PI_HALF / (n * 18.0 * z * z2);

        term = 1.0;
        tom = 0.0;
        j = 0;
        while (j <= JMAX && Math.abs(term) > EPS * Math.abs(tom)) {
            double ti = j + 0.5;
            ti = ti * ti;
            term = -30.0 * z6 - 90.0 * z6 * z2 + PI2 * (135.0 * z4 - 96.0 * z6) * ti
                    + PI4 * (212.0 * z4 - 60.0 * z2) * ti * ti + PI2 * PI4 * ti * ti * ti * (5.0 - 30.0 * z2);
            term *= Math.exp(-ti * w);
            tom += term;
            j++;
        }
        sum += tom * SQRT_PI_HALF / (RACN * n * 3240.0 * z4 * z6);

        term = 1.0;
        tom = 0.0;
        j = 1;
        while (j <= JMAX && Math.abs(term) > EPS * Math.abs(tom)) {
            double ti = j * j;
            term = (3.0 * PI2 * ti * z2 - PI4 * ti * ti) * Math.exp(-ti * w);
            tom += term;
            j++;
        }
        sum += tom * SQRT_PI_HALF / (RACN * n * 108.0 * z6);

        return sum;
    }

    private static void mPower(double[] A, int eA, double[] V, int[] eV, int m, int n) {
        if (n == 1) {
            for (int i = 0; i < m * m; i++) {
                V[i] = A[i];
            }
            eV[0] = eA;
            return;
        }
        mPower(A, eA, V, eV, m, n / 2);

        double[] B = new double[m * m];
        int[] pB = new int[1];

        mMultiply(V, V, B, m);
        pB[0] = 2 * (eV[0]);
        if (B[(m / 2) * m + (m / 2)] > NORM) {
            renormalize(B, m, pB);
        }

        if (n % 2 == 0) {
            for (int i = 0; i < m * m; i++) {
                V[i] = B[i];
            }
            eV[0] = pB[0];
        } else {
            mMultiply(A, B, V, m);
            eV[0] = eA + pB[0];
        }

        if (V[(m / 2) * m + (m / 2)] > NORM) {
            renormalize(V, m, eV);
        }
    }

    private static void mMultiply(double[] A, double[] B, double[] C, int m) {
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                double s = 0.0;
                for (int k = 0; k < m; k++) {
                    s += A[i * m + k] * B[k * m + j];
                }
                C[i * m + j] = s;
            }
        }
    }

    private static void renormalize(double[] V, int m, int[] p) {
        for (int i = 0; i < m * m; i++) {
            V[i] *= INORM;
        }
        p[0] += LOGNORM;
    }

    private FastKolmogorovSmirnov() {
        throw new AssertionError();
    }
}
