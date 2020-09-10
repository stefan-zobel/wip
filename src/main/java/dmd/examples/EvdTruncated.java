package dmd.examples;

import net.jamu.complex.Zd;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.MatrixD;

/* package */ class EvdTruncated {

    // truncated singular vectors V
    MatrixD Vr;

    // truncated eigenvectors
    ComplexMatrixD eigenvecs;

    // truncated eigenvalues
    Zd[] eigs;
}
