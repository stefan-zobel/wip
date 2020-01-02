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
     *         {@link #hasEigenVectors()} returns {@code false}
     */
    public SimpleMatrixF getEigenVectors() {
        return eigenVectors;
    }

    /**
     * {@code true} if eigenvectors have been computed, {@code false} otherwise.
     * 
     * @return whether eigenvectors have been computed or not
     */
    public boolean hasEigenVectors() {
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
                eigValImagParts, new float[0], ld, hasEigenVectors() ? eigenVectors.getArrayUnsafe() : new float[0],
                ld);
    }
}
