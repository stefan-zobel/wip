/*
 * Copyright 2020 Stefan Zobel
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
package net.incubator.banach.matrix;

import net.dedekind.lapack.Lapack;
import net.frobenius.TEigJob;
import net.frobenius.lapack.PlainLapack;

/**
 * Eigenvalues and eigenvectors of a general n-by-n float matrix.
 */
public class EvdF {

    // never compute left eigenvectors
    private static final TEigJob leftEVec = TEigJob.VALUES_ONLY;
    // caller decides whether right eigenvectors should be computed
    private final TEigJob rightEVec;
    // (right) eigenvectors if full == true or null
    private final SimpleMatrixF eigenVectors;
    // eigenvalue real parts
    private final float[] eigValRealParts;
    // eigenvalue imaginary parts
    private final float[] eigValImagParts;

    /**
     * Returns the real parts of the eigenvalues.
     * 
     * @return array containing the real parts of the eigenvalues
     */
    public float[] getRealEigenvalues() {
        return eigValRealParts;
    }

    /**
     * Returns the imaginary parts of the eigenvalues.
     * 
     * @return array containing the imaginary parts of the eigenvalues
     */
    public float[] getImaginaryEigenvalues() {
        return eigValImagParts;
    }

    /**
     * The (right) eigenvectors or {@code null} if the eigenvectors haven't been
     * computed.
     * 
     * @return n-by-n eigenvector matrix or {@code null} if
     *         {@link #hasEigenvectors()} returns {@code false}
     */
    public SimpleMatrixF getEigenvectors() {
        return eigenVectors;
    }

    /**
     * {@code true} if eigenvectors have been computed, {@code false} otherwise.
     * 
     * @return whether eigenvectors have been computed or not
     */
    public boolean hasEigenvectors() {
        return rightEVec == TEigJob.ALL;
    }

    /* package */ EvdF(SimpleMatrixF A, boolean full) {
        if (!A.isSquareMatrix()) {
            throw new IllegalArgumentException("EVD only works for square matrices");
        }
        int n = A.numRows();
        rightEVec = full ? TEigJob.ALL : TEigJob.VALUES_ONLY;
        eigenVectors = full ? new SimpleMatrixF(n, n) : null;
        eigValRealParts = new float[n];
        eigValImagParts = new float[n];
        computeEvdInplace(A);
    }

    private void computeEvdInplace(SimpleMatrixF A) {
        MatrixF AA = A.copy();
        int n = AA.numRows();
        int ld = Math.max(1, n);
        PlainLapack.sgeev(Lapack.getInstance(), leftEVec, rightEVec, n, AA.getArrayUnsafe(), ld, eigValRealParts,
                eigValImagParts, new float[0], ld, hasEigenvectors() ? eigenVectors.getArrayUnsafe() : new float[0],
                ld);
    }
}
