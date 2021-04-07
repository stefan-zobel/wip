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
 * Schur implements the Schur decomposition of a matrix. Specifically, given a
 * square matrix A, there is a unitary matrix U such that
 * 
 * <pre>
 *      T = U^H AU
 * </pre>
 * 
 * is upper triangular. Schur represents T and U as Zmats.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Schur {

    /** The upper triangular matrix. */
    public final Zmat T;

    /** The unitary matrix. */
    public final Zmat U;

    /** Limits the number of iterations in the QR algorithm */
    private static final int MAXITER = 30;

    /**
     * Creats a Schur decomposition from a square Zmat.
     * 
     * @param A
     *            The Zmat whose Schur decomposition is to be computed
     * @throws    ZException
     *                Thrown for non-quadratic matrix.<br>
     *                Thrown for maximum iteration count exceeded.
     */
    public Schur(Zmat A) throws ZException {
        int i, il, iter, iu;
        double d, sd, sf;
        Z b = new Z(), c = new Z(), disc = new Z(), kappa = new Z(), p, q, r, r1 = new Z(), r2 = new Z(), s,
                z1 = new Z(), z2 = new Z();
        Rot P = new Rot();

        if (A.nr != A.nc) {
            throw new ZException("Nonsquare matrix");
        }

        /* Reduce to Hessenberg form and set up T and U */

        Zhess H = new Zhess(A);
        T = new Zmat(H.H);
        U = H.U;

        iu = T.nr;
        iter = 0;
        while (true) {

            // Locate the range in which to iterate.
            while (iu > 1) {
                d = Z.abs1(T.get(iu, iu)) + Z.abs1(T.get(iu - 1, iu - 1));
                sd = Z.abs1(T.get(iu, iu - 1));
                if (sd >= 1.0e-16 * d)
                    break;
                T.put(iu, iu - 1, 0.0, 0.0);
                iter = 0;
                iu = iu - 1;
            }
            if (iu == 1)
                break;

            iter = iter + 1;
            if (iter >= MAXITER) {
                throw new ZException("Maximum number of iterations exceeded.");
            }
            il = iu - 1;
            while (il > 1) {
                d = Z.abs1(T.get(il, il)) + Z.abs1(T.get(il - 1, il - 1));
                sd = Z.abs1(T.get(il, il - 1));
                if (sd < 1.0e-16 * d)
                    break;
                il = il - 1;
            }
            if (il != 1) {
                T.put(il, il - 1, 0.0, 0.0);
            }

            // Compute the shift.
            p = T.get(iu - 1, iu - 1);
            q = T.get(iu - 1, iu);
            r = T.get(iu, iu - 1);
            s = T.get(iu, iu);

            sf = Z.abs1(p) + Z.abs1(q) + Z.abs1(r) + Z.abs1(s);
            p.div(p, sf);
            q.div(q, sf);
            r.div(r, sf);
            s.div(s, sf);

            c.minus(z1.times(p, s), z2.times(r, q));
            b.plus(p, s);

            disc.sqrt(disc.minus(z1.times(b, b), z2.times(4, c)));
            r1.div(r1.plus(b, disc), 2);
            r2.div(r2.minus(b, disc), 2);
            if (Z.abs1(r1) > Z.abs1(r2)) {
                r2.div(c, r1);
            } else {
                r1.div(c, r2);
            }
            if (Z.abs1(z1.minus(r1, s)) < Z.abs1(z2.minus(r2, s))) {
                kappa.times(sf, r1);
            } else {
                kappa.times(sf, r2);
            }

            // Perform the QR step.
            p.minus(T.get(il, il), kappa);
            q.eq(T.get(il + 1, il));
            Rot.genc(p.re, p.im, q.re, q.im, P);
            for (i = il; i < iu; i++) {
                Rot.pa(P, T, i, i + 1, i, T.nc);
                Rot.aph(T, P, 1, Math.min(i + 2, iu), i, i + 1);
                Rot.aph(U, P, 1, U.nr, i, i + 1);
                if (i != iu - 1) {
                    Rot.genc(T, i + 1, i + 2, i, P);
                }
            }
        }
    }

    /**
     * Zhess implements the unitary reduction to Hessenberg form by a unitary
     * similarity transformation. Specifically, given a square matrix A, there
     * is a unitary matrix U such that
     * 
     * <pre>
     *      H = U^H AU
     * </pre>
     * 
     * is upper Hessenberg. Zhess represents U and H as Zmats.
     * 
     * @version Pre-alpha
     * @author G. W. Stewart
     */
    static final class Zhess {

        /** The upper Hessenberg matrix */
        final Zmat H;

        /** The unitary matrix */
        final Zmat U;

        /**
         * Creates a Zhess from a square Zmat. Throws a ZException for
         * non-square matrix.
         * 
         * @param A
         *            A Zmat
         * @return The Hessenberg form of A
         * @throws ZException
         *                Thrown if A is not square.
         */
        Zhess(Zmat A) throws ZException {
            if (A.nr != A.nc) {
                throw new ZException("Matrix not square");
            }

            H = new Zmat(A);
            U = Eye.o(H.nr);

            Z1 work = new Z1(H.nr);

            for (int k = 1; k <= H.nc - 2; k++) {
                Z1 u = House.genc(H, k + 1, H.nr, k);
                House.ua(u, H, k + 1, H.nr, k + 1, H.nc, work);
                House.au(H, u, 1, H.nr, k + 1, H.nc, work);
                House.au(U, u, 1, U.nr, k + 1, U.nc, work);
            }
        }
    }
}
