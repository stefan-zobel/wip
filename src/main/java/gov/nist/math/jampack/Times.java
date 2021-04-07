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
 * Times provides static methods to compute matrix products.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Times {

    /**
     * Computes the product of a Z and a Zmat.
     * 
     * @param z
     *            The complex scalar
     * @param A
     *            The Zmat
     * @return zA
     */
    public static Zmat o(Z z, Zmat A) {
        Zmat B = new Zmat(A.nr, A.nc);
        for (int i = 0; i < A.nr; i++)
            for (int j = 0; j < A.nc; j++) {
                double reij = A.re(i, j);
                double imij = A.im(i, j);
                B.setRe(i, j, z.re * reij - z.im * imij);
                B.setIm(i, j, z.im * reij + z.re * imij);
            }
        return B;
    }

    /**
     * Computes the product of two Zmats.
     * 
     * @param A
     *            The first Zmat
     * @param B
     *            The second Zmat
     * @return AB
     * @throws    ZException
     *                for unconformity
     */
    public static Zmat o(Zmat A, Zmat B) throws ZException {
        if (A.nc != B.nr) {
            throw new ZException("Unconformity in product");
        }
        Zmat C = new Zmat(A.nr, B.nc);
        for (int i = 0; i < A.nr; i++) {
            for (int k = 0; k < A.nc; k++) {
                for (int j = 0; j < B.nc; j++) {
                    double imkj = B.im(k, j);
                    double rekj = B.re(k, j);
                    double imik = A.im(i, k);
                    double reik = A.re(i, k);
                    C.addRe(i, j, reik * rekj - imik * imkj);
                    C.addIm(i, j, imik * rekj + reik * imkj);
                }
            }
        }
        return C;
    }

    /**
     * Computes A<sup>H</sup>A, where A is a Zmat.
     * 
     * @param A
     *            The Zmat
     * @return A<sup>H</sup>A
     */
    public static Zmat aha(Zmat A) {
        Zmat C = new Zmat(A.nc, A.nc, true);
        for (int k = 0; k < A.nr; k++) {
            for (int i = 0; i < A.nc; i++) {
                double reki = A.re(k, i);
                double imki = A.im(k, i);
                C.addRe(i, i, reki * reki + imki * imki);
                C.setIm(i, i, 0.0);
                for (int j = i + 1; j < A.nc; j++) {
                    double reki_ = A.re(k, i);
                    double rekj_ = A.re(k, j);
                    double imki_ = A.im(k, i);
                    double imkj_ = A.im(k, j);
                    C.addRe(i, j, reki_ * rekj_ + imki_ * imkj_);
                    C.addIm(i, j, reki_ * imkj_ - imki_ * rekj_);
                }
            }
        }
        for (int i = 0; i < A.nc; i++) {
            for (int j = i + 1; j < A.nc; j++) {
                C.setRe(j, i, C.re(i, j));
                C.setIm(j, i, -C.im(i, j));
            }
        }
        return C;
    }

    /**
     * Computes AA<sup>H</sup>, where A is a Zmat.
     * 
     * @param A
     *            The Zmat
     * @return AA<sup>H</sup>
     */
    public static Zmat aah(Zmat A) {
        Zmat C = new Zmat(A.nr, A.nr, true);
        for (int i = 0; i < A.nr; i++) {
            for (int k = 0; k < A.nc; k++) {
                double reik = A.re(i, k);
                double imik = A.im(i, k);
                C.addRe(i, i, reik * reik + imik * imik);
            }
            C.setIm(i, i, 0.0);
            for (int j = i + 1; j < A.nr; j++) {
                for (int k = 0; k < A.nc; k++) {
                    double reik = A.re(i, k);
                    double rejk = A.re(j, k);
                    double imik = A.im(i, k);
                    double imjk = A.im(j, k);
                    C.addRe(i, j, reik * rejk + imik * imjk);
                    C.addIm(i, j, -reik * imjk + imik * rejk);
                }
                C.setRe(j, i, C.re(i, j));
                C.setIm(j, i, -C.im(i, j));
            }
        }
        return C;
    }

    /**
     * Computes the product of a Z and a Zdiagmat.
     * 
     * @param z
     *            The complex scalar
     * @param D
     *            The Zdiagmat
     * @return zD
     */
    public static Zdiagmat o(Z z, Zdiagmat D) {
        Zdiagmat B = new Zdiagmat(D);
        for (int i = 0; i < D.order; i++) {
            double rei = D.re(i);
            double imi = D.im(i);
            B.setRe(i, z.re * rei - z.im * imi);
            B.setIm(i, z.im * rei + z.re * imi);
        }
        return B;
    }

    /**
     * Computes the product of two Zdiagmats.
     * 
     * @param D1
     *            The first Zdiagmat
     * @param D2
     *            The second Zdiagmat
     * @return D1*D2
     * @throws    ZException
     *                for unconformity
     */
    public static Zdiagmat o(Zdiagmat D1, Zdiagmat D2) throws ZException {
        if (D1.order != D2.order) {
            throw new ZException("Unconformity in product");
        }
        Zdiagmat D3 = new Zdiagmat(D1.order);
        for (int i = 0; i < D3.order; i++) {
            double d1rei = D1.re(i);
            double d1imi = D1.im(i);
            double d2rei = D2.re(i);
            double d2imi = D2.im(i);
            D3.setRe(i, d1rei * d2rei - d1imi * d2imi);
            D3.setIm(i, d1rei * d2imi + d1imi * d2rei);
        }
        return D3;
    }

    /**
     * Computes the product of a Zdiagmat and a Zmat.
     * 
     * @param D
     *            The Zdiagmat
     * @param A
     *            The Zmat
     * @return DA
     * @throws    ZException
     *                for unconformity
     */
    public static Zmat o(Zdiagmat D, Zmat A) throws ZException {
        if (D.order != A.nr) {
            throw new ZException("Unconformity in product.");
        }
        Zmat B = new Zmat(A.nr, A.nc);
        for (int i = 0; i < A.nr; i++) {
            for (int j = 0; j < A.nc; j++) {
                double rei = D.re(i);
                double imi = D.im(i);
                double reij = A.re(i, j);
                double imij = A.im(i, j);
                B.setRe(i, j, rei * reij - imi * imij);
                B.setIm(i, j, rei * imij + imi * reij);
            }
        }
        return B;
    }

    /**
     * Computes the product of a Zmat and a Zdiagmat.
     * 
     * @param A
     *            The Zgmat
     * @param D
     *            The Zdiagmat
     * @return AD
     * @throws    ZException
     *                for unconformity
     */
    public static Zmat o(Zmat A, Zdiagmat D) throws ZException {
        if (D.order != A.nc) {
            throw new ZException("Unconformity in product.");
        }
        Zmat B = new Zmat(A.nr, A.nc);
        for (int i = 0; i < A.nr; i++) {
            for (int j = 0; j < A.nc; j++) {
                double rej = D.re(j);
                double imj = D.im(j);
                double reij = A.re(i, j);
                double imij = A.im(i, j);
                B.setRe(i, j, rej * reij - imj * imij);
                B.setIm(i, j, rej * imij + imj * reij);
            }
        }
        return B;
    }
}
