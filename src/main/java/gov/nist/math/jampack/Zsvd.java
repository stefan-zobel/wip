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
package gov.nist.math.jampack;

/**
 * Zsvd implements the singular value decomposition of a Zmat. Specifically if X
 * is an {@code m x n} matrix with {@code m >= n} there are unitary matrices U
 * and V such that
 * 
 * <pre>
*     U^H*X*V = | S |
*               | 0 |
 * </pre>
 * 
 * where S = diag(s1,...,sm) with
 * 
 * <pre>
 *     {@code s1 >= s2 >= ... >= sn >=0}.
 * </pre>
 * 
 * If {@code m < n} the decomposition has the form
 * 
 * <pre>
*     U^H*X*V = | S  0 |,
 * </pre>
 * 
 * where S is diagonal of order m. The diagonals of S are the singular values of
 * A. The columns of U are the left singular vectors of A and the columns of V
 * are the right singular vectors.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Zsvd {

    /** Limits the number of iterations in the SVD algorithm */
    private static final int MAXITER = 30;

    /** The matrix of left singular vectors */
    public final Zmat U;

    /** The matrix of right singular vectors */
    public final Zmat V;

    /** The diagonal matrix of singular values */
    public final Zdiagmat S;

    /**
     * Computes the SVD of a Zmat XX. Throws a ZException if the maximum number
     * of iterations is exceeded.
     * 
     * @param XX a Zmat
     * @throws   ZException
     *                Thrown if maximum number of iterations is exceeded.<br>
     *                Passed from below.
     */
    public Zsvd(Zmat XX) throws ZException {

        int i, il, iu, iter, j, k, kk, m, mc;

        double as, at, au, axkk, axkk1, dmax, dmin, ds, ea, es, shift, ss, t;

        Z xkk, xkk1, xk1k1;

        final Rot P = new Rot();

        /* Initialization */
        final Z scale = new Z();
        final Z zr = new Z();

        Zmat X = new Zmat(XX);

        Z1 h;
        Z1 temp = new Z1(Math.max(X.nr, X.nc));

        mc = Math.min(X.nr, X.nc);
        double[] d = new double[mc];
        double[] e = new double[mc];

        S = new Zdiagmat(mc);
        U = Eye.o(X.nr);
        V = Eye.o(X.nc);

        m = Math.min(X.nr, X.nc);

        /*
         * Reduction to Bidiagonal form.
         */
        for (k = 1; k <= m; k++) {

            h = House.genc(X, k, X.nr, k);
            House.ua(h, X, k, X.nr, k + 1, X.nc, temp);
            House.au(U, h, 1, U.nr, k, U.nc, temp);

            if (k != X.nc) {
                h = House.genr(X, k, k + 1, X.nc);
                House.au(X, h, k + 1, X.nr, k + 1, X.nc, temp);
                House.au(V, h, 1, V.nr, k + 1, V.nc, temp);
            }
        }

        /*
         * Scale the bidiagonal matrix so that its elements are real.
         */
        for (k = 1; k <= m; k++) {
            kk = k - 1;
            xkk = X.get(k, k);
            axkk = Z.abs(xkk);
            X.put(k, k, axkk, 0.0);
            d[kk] = axkk;
            scale.div(scale.conj(xkk), axkk);
            if (k < X.nc) {
                xkk1 = X.get(k, k + 1);
                X.put(k, k + 1, xkk1.times(scale, xkk1));
            }
            scale.conj(scale);
            for (i = 1; i <= U.nr; i++) {
                U.put(i, k, zr.times(U.get(i, k), scale));
            }

            if (k < X.nc) {

                xkk1 = X.get(k, k + 1);
                axkk1 = Z.abs(xkk1);
                X.put(k, k + 1, axkk1, 0.0);
                e[kk] = axkk1;
                scale.div(scale.conj(xkk1), axkk1);
                if (k < X.nr) {
                    xk1k1 = X.get(k + 1, k + 1);
                    X.put(k + 1, k + 1, xk1k1.times(scale, xk1k1));
                }
                for (i = 1; i <= V.nr; i++) {
                    V.put(i, k + 1, zr.times(V.get(i, k + 1), scale));
                }
            }
        }

        m = m - 1; // Zero based loops from here on.
        /*
         * If X has more columns than rows, rotate out the extra superdiagonal
         * element.
         */
        if (X.nr < X.nc) {
            t = e[m];
            for (k = m; k >= 0; k--) {
                Rot.genr(d[k], t, P);
                d[k] = P.zr;
                if (k != 0) {
                    t = P.sr * e[k - 1];
                    e[k - 1] = P.c * e[k - 1];
                }
                Rot.ap(V, P, 1, V.nr, k + 1, X.nr + 1);
                Rot.ap(X, P, 1, X.nr, k + 1, X.nr + 1);
            }
        }
        /*
         * Calculate the singular values of the bidiagonal matrix.
         */
        iu = m;
        iter = 0;
        while (true) {
            /*
             * These two loops determine the rows (il to iu) to iterate on.
             */
            while (iu > 0) {
                if (Math.abs(e[iu - 1]) > 1.0e-16 * (Math.abs(d[iu]) + Math.abs(d[iu - 1])))
                    break;
                e[iu - 1] = 0.;
                iter = 0;
                iu = iu - 1;
            }
            iter = iter + 1;
            if (iter > MAXITER) {
                throw new ZException("Maximum number of iterations exceeded.");
            }
            if (iu == 0)
                break;

            il = iu - 1;
            while (il > 0) {
                if (Math.abs(e[il - 1]) <= 1.0e-16 * (Math.abs(d[il]) + Math.abs(d[il - 1])))
                    break;
                il = il - 1;
            }
            if (il != 0) {
                e[il - 1] = 0.0;
            }
            /*
             * Compute the shift (formulas adapted from LAPACK).
             */
            dmax = Math.max(Math.abs(d[iu]), Math.abs(d[iu - 1]));
            dmin = Math.min(Math.abs(d[iu]), Math.abs(d[iu - 1]));
            ea = Math.abs(e[iu - 1]);
            if (dmin == 0.0) {
                shift = 0.0;
            } else if (ea < dmax) {
                as = 1.0 + dmin / dmax;
                at = (dmax - dmin) / dmax;
                au = ea / dmax;
                au = au * au;
                shift = dmin * (2.0 / (Math.sqrt(as * as + au) + Math.sqrt(at * at + au)));
            } else {
                au = dmax / ea;
                if (au == 0.0) {
                    shift = (dmin * dmax) / ea;
                } else {
                    as = 1.0 + dmin / dmax;
                    at = (dmax - dmin) / dmax;
                    t = 1.0 / (Math.sqrt(1.0 + (as * au) * (as * au)) + Math.sqrt(1.0 + (at * au) * (at * au)));
                    shift = (t * dmin) * au;
                }
            }
            /*
             * Perform the implicitly shifted QR step.
             */
            t = Math.max(Math.max(Math.abs(d[il]), Math.abs(e[il])), shift);
            ds = d[il] / t;
            es = e[il] / t;
            ss = shift / t;
            Rot.genr((ds - ss) * (ds + ss), ds * es, P);
            for (i = il; i < iu; i++) {
                t = P.c * d[i] - P.sr * e[i];
                e[i] = P.sr * d[i] + P.c * e[i];
                d[i] = t;
                t = -P.sr * d[i + 1];
                d[i + 1] = P.c * d[i + 1];
                Rot.ap(V, P, 1, V.nr, 1 + i, 1 + i + 1);
                Rot.genc(d[i], t, P);
                d[i] = P.zr;
                t = P.c * e[i] + P.sr * d[i + 1];
                d[i + 1] = P.c * d[i + 1] - P.sr * e[i];
                e[i] = t;
                Rot.aph(U, P, 1, U.nr, 1 + i, 1 + i + 1);
                if (i != iu - 1) {
                    t = P.sr * e[i + 1];
                    e[i + 1] = P.c * e[i + 1];
                    Rot.genr(e[i], t, P);
                    e[i] = P.zr;
                }
            }
        }

        /*
         * Sort the singular values, setting negative values of d to positive.
         */
        for (k = m; k >= 0; k--) {
            if (d[k] < 0) {
                d[k] = -d[k];
                for (i = 0; i < X.nc; i++) {
                    V.scaleRe(i, k, -1.0);
                    V.scaleIm(i, k, -1.0);
                }
            }
            for (j = k; j < m; j++) {
                if (d[j] < d[j + 1]) {
                    t = d[j];
                    d[j] = d[j + 1];
                    d[j + 1] = t;
                    for (i = 0; i < X.nr; i++) {
                        t = U.re(i, j);
                        U.setRe(i, j, U.re(i, j + 1));
                        U.setRe(i, j + 1, t);
                        t = U.im(i, j);
                        U.setIm(i, j, U.im(i, j + 1));
                        U.setIm(i, j + 1, t);
                    }
                    for (i = 0; i < X.nc; i++) {
                        t = V.re(i, j);
                        V.setRe(i, j, V.re(i, j + 1));
                        V.setRe(i, j + 1, t);
                        t = V.im(i, j);
                        V.setIm(i, j, V.im(i, j + 1));
                        V.setIm(i, j + 1, t);
                    }
                }
            }
        }
        /*
         * Return the decomposition;
         */
        S.setRe(d);
        X = null;
        temp = null;
        h = null;
        d = null;
        e = null;
    }
}
