package net.incubator.banach.matrix;

import net.dedekind.lapack.Lapack;
import net.frobenius.TEigJob;
import net.frobenius.lapack.PlainLapack;

public class EvdD {

    // never compute left eigenvectors
    private static final TEigJob leftEVec = TEigJob.VALUES_ONLY;
    // caller decides whether right eigenvectors should be computed
    private final TEigJob rightEVec;
    // (right) eigenvectors if full == true or null
    private final SimpleMatrixD eigenVectors;
    // eigenvalue real parts
    private final double[] eigValRealParts;
    // eigenvalue imaginary parts
    private final double[] eigValImagParts;

    public double[] getRealEigenvalues() {
        return eigValRealParts;
    }

    public double[] getImaginaryEigenvalues() {
        return eigValImagParts;
    }

    public SimpleMatrixD getEigenVectors() {
        return eigenVectors;
    }

    public boolean hasEigenVectors() {
        return rightEVec == TEigJob.ALL;
    }

    /* package */ EvdD(SimpleMatrixD A, boolean full) {
        if (!A.isSquareMatrix()) {
            throw new IllegalArgumentException("EVD only works for square matrices");
        }
        int n = A.numRows();
        rightEVec = full ? TEigJob.ALL : TEigJob.VALUES_ONLY;
        eigenVectors = full ? new SimpleMatrixD(n, n) : null;
        eigValRealParts = new double[n];
        eigValImagParts = new double[n];
        computeEvdInplace(A);
    }

    private void computeEvdInplace(SimpleMatrixD A) {
        int n = A.numRows();
        int ld = Math.max(1, n);
        PlainLapack.dgeev(Lapack.getInstance(), leftEVec, rightEVec, n, A.getArrayUnsafe(), ld, eigValRealParts,
                eigValImagParts, new double[0], ld, hasEigenVectors() ? eigenVectors.getArrayUnsafe() : new double[0],
                ld);
    }
}
