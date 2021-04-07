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
 * Eig implements the eigenvalue-vector decomposition of a square matrix.
 * Specifically given a diagonalizable matrix A, there is a non-singular matrix
 * X such that
 * <p>
 * <code>
 *      D = X<sup>-1</sup> AX
 * </code>
 * <p>
 * is diagonal. The columns of X are eigenvectors of A corresponding to the
 * diagonal elements of D. Eig implements X as a Zmat and D as a Zdiagmat.
 * <p>
 * Warning: if A is defective rounding error will allow Eig to compute a set of
 * eigenvectors. However, the matrix X will be ill conditioned.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Eig {

    /** The matrix of eigenvectors */
    public Zmat X;

    /** The diagonal matrix of eigenvalues */
    public Zdiagmat D;

    /**
     * Creates an eigenvalue-vector decomposition of a square matrix A.
     * 
     * @param A
     *            The matrix whose decomposition is to be computed
     * @throws    ZException
     *                Thrown if A is not square.
     */
    public Eig(Zmat A) throws ZException {
        int i, j, k;
        double norm, scale;
        Z z, d;

        if (A.nr != A.nc) {
            throw new ZException("Matrix not square.");
        }

        int n = A.nr;

        /* Compute the Schur decomposition of $A$ and set up T and D. */
        Schur S = new Schur(A);

        Zmat T = S.T;

        D = new Zdiagmat(T);

        norm = Norm.fro(A);

        X = new Zmat(n, n);

        /* Compute the eigenvectors of T */
        for (k = n - 1; k >= 0; k--) {

            d = T.get0(k, k);

            X.setRe(k, k, 1.0);
            X.setIm(k, k, 0.0);

            for (i = k - 1; i >= 0; i--) {

                X.scaleRe(i, k, -1.0);
                X.scaleIm(i, k, -1.0);

                for (j = i + 1; j < k; j++) {
                    double rejk = X.re(j, k);
                    double imjk = X.im(j, k);
                    double reij = T.re(i, j);
                    double imij = T.im(i, j);
                    X.addRe(i, k, -reij * rejk + imij * imjk);
                    X.addIm(i, k, -reij * imjk - imij * rejk);
                }

                z = T.get0(i, i);
                z.minus(z, d);
                if (z.re == 0.0 && z.im == 0.0) { // perturb zero diagonal
                    z.re = 1.0e-16 * norm; // to avoid division by zero
                }
                z.div(X.get0(i, k), z);
                X.put0(i, k, z);
            }

            /* Scale the vector so its norm is one. */
            scale = 1.0 / Norm.fro(X, 1, X.nr, 1 + k, 1 + k);
            for (i = 0; i < X.nr; i++) {
                X.scaleRe(i, k, scale);
                X.scaleIm(i, k, scale);
            }
        }
        X = Times.o(S.U, X);
        S = null;
        T = null;
    }
}
