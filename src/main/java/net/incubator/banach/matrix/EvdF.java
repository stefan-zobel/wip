package net.incubator.banach.matrix;

import net.dedekind.lapack.Lapack;
import net.frobenius.TEigJob;
import net.frobenius.lapack.PlainLapack;

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

    public float[] getRealEigenvalues() {
        return eigValRealParts;
    }

    public float[] getImaginaryEigenvalues() {
        return eigValImagParts;
    }

    public SimpleMatrixF getEigenVectors() {
        return eigenVectors;
    }

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
        int n = A.numRows();
        int ld = Math.max(1, n);
        PlainLapack.sgeev(Lapack.getInstance(), leftEVec, rightEVec, n, A.getArrayUnsafe(), ld, eigValRealParts,
                eigValImagParts, new float[0], ld, hasEigenVectors() ? eigenVectors.getArrayUnsafe() : new float[0],
                ld);
    }
}
