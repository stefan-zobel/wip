package dmd.examples;

import java.util.Objects;

import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.EvdComplexD;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;
import net.jamu.matrix.SvdEconD;

public class ExactDMD {

    private final MatrixD data;
    private final double deltaT;

    // TODO
    private int rank = 0;
    // eigenvalues in the subspace
    private Zd[] eigenValues;
    // modes of the fitted linear system in the high-dimensional space
    private ComplexMatrixD phi;
    // omega contains the eigenvalues of the fitted linear system
    private ComplexMatrixD omega;
    // initial condition at time 0
    private ComplexMatrixD time0;

    public ExactDMD(MatrixD data, double deltaT) {
        if (deltaT <= 0.0) {
            throw new IllegalArgumentException("deltaT: " + deltaT);
        }
        this.data = Objects.requireNonNull(data);
        this.deltaT = deltaT;
    }

    public ExactDMD(MatrixD data, double deltaT, int rank) {
        this(data, deltaT);
        if (rank < 1) {
            throw new IllegalArgumentException("rank: " + rank);
        }
        this.rank = rank;
    }

    public ExactDMD compute() {
        computeDMD();
        return this;
    }

    public int getRank() {
        return rank;
    }

    public Zd[] getEigenValues() {
        return eigenValues;
    }

    public ComplexMatrixD getPhi() {
        return phi;
    }

    public ComplexMatrixD getOmega() {
        return omega;
    }

    public ComplexMatrixD getTime0() {
        return time0;
    }

    public MatrixD getData() {
        return data;
    }

    public double getDeltaT() {
        return deltaT;
    }

    private void computeDMD() {
        // step 1 of exact DMD algorithm
        SvdEconD svd = computeSvd(data);

        // estimate rank truncation from SVD
        if (rank == 0) {
            rank = estimateRank(svd);
        }

        // step 4: get back into high-dimensional space
        // Modes.Phi contains the modes of the fitted linear system and
        // Modes.eigs contains the eigenvalues in the subspace
        Modes modes = computeModesAndEigenvalues(data, svd, rank);
        eigenValues = modes.eigs;
        phi = modes.Phi;

        // omega contains the eigenvalues of the fitted linear system
        omega = computeOmega(eigenValues, deltaT);

        // initial condition (vector b) at time 0
        time0 = computeInitialCondition(data, rank, phi);
    }

    public MatrixD predict(double timeFrom, double timeTo, int numberOfPredictions) {
        if (timeTo < timeFrom) {
            throw new IllegalArgumentException("timeTo < timeFrom: " + timeTo + " < " + timeFrom);
        }
        if (numberOfPredictions < 1) {
            throw new IllegalArgumentException("numberOfPredictions < 1: " + numberOfPredictions);
        }
        if (timeTo == timeFrom && numberOfPredictions != 1) {
            throw new IllegalArgumentException(
                    "timeTo == timeFrom but numberOfPredictions != 1: " + numberOfPredictions);
        }
        double dt = (numberOfPredictions == 1) ? 0.0 : (timeTo - timeFrom) / (numberOfPredictions - 1);
        ComplexMatrixD newOmega = omega;
        // recompute omega if necessary
        if (dt != deltaT && dt != 0.0) {
            newOmega = recomputeOmega(newOmega, deltaT, dt);
        }
        // create time dynamics matrix (b * e^omega*t)
        ComplexMatrixD timeDynamics = createTimeDynamicsMatrix(newOmega, rank, time0, timeFrom, timeTo,
                numberOfPredictions);
        // spatio-temporal prediction
        return phi.times(timeDynamics).toRealMatrix();
    }

    private static ComplexMatrixD recomputeOmega(ComplexMatrixD analysisOmega, double analysisDeltaT,
            double newDeltaT) {
        ComplexMatrixD newOmega = analysisOmega.copy();
        newOmega.scaleInplace(analysisDeltaT, 0.0);
        if (newDeltaT != 1.0) {
            newOmega.scaleInplace(1.0 / newDeltaT, 0.0);
        }
        return newOmega;
    }

    private static int estimateRank(SvdEconD svd) {
        // TODO
        return 2;
    }

    private static SvdEconD computeSvd(MatrixD data) {
        // create first snapshot from measurements matrix
        MatrixD X1_ = data.selectConsecutiveColumns(data.startCol(), data.endCol() - 1);
        // step 1 of exact DMD algorithm
        return X1_.svdEcon();
    }

    private static Modes computeModesAndEigenvalues(MatrixD data, SvdEconD svd, int rank) {
        // create the second time-shifted snapshot
        MatrixD snapshot = getSecondSnapshot(data);
        // pull out Sigma inverse for low-dimensional subspace
        MatrixD sigmaTruncInverse = getSigmaTruncatedInverse(svd, rank);
        EvdTruncated decomposed = decompose(snapshot, svd, rank, sigmaTruncInverse);
        // create Modes
        Modes modes = new Modes();
        modes.eigs = decomposed.eigs;
        // copy the second snapshot into complex matrix
        ComplexMatrixD X2 = Matrices.convertToComplex(snapshot);
        // step 4: get back into high-dimensional space
        // Phi contains the modes of the fitted linear system
        modes.Phi = X2.times(decomposed.Vr).times(sigmaTruncInverse).times(decomposed.eigenvecs);
        return modes;
    }

    private static EvdTruncated decompose(MatrixD snapshot, SvdEconD svd, int rank, MatrixD sigmaTruncInverse) {
        MatrixD U = svd.getU();
        MatrixD Vt = svd.getVt();
        // pull out U / V for low-dimensional subspace
        MatrixD Ur = U.selectConsecutiveColumns(U.startCol(), rank - 1);
        MatrixD Vr = Vt.transpose().selectConsecutiveColumns(Vt.startCol(), rank - 1);
        EvdTruncated summary = new EvdTruncated();
        summary.Vr = Vr;
        // step 2: similarity-transform in the low-rank subspace
        // ATilde takes us from one snapshot to the next in the low-rank
        // subspace (from here on everything is done in the complex domain)
        ComplexMatrixD ATilde = Ur.transpose().times(snapshot).times(Vr).times(sigmaTruncInverse).toComplexMatrix();
        // step 3: compute the 'rank' eigenvalues / eigenvectors in the subspace
        EvdComplexD evd = ATilde.evd(true);
        summary.eigenvecs = evd.getEigenvectors();
        summary.eigs = evd.getEigenvalues();
        return summary;
    }

    private static ComplexMatrixD computeOmega(Zd[] eigs, double dt) {
        // omega contains the eigenvalues of the fitted linear system
        ComplexMatrixD omega = Matrices.createComplexD(eigs.length, eigs.length);
        for (int i = 0; i < eigs.length; ++i) {
            Zd z = eigs[i].copy();
            z.ln().scale(1.0 / dt);
            omega.set(i, i, z.re(), z.im());
        }
        return omega;
    }

    private static ComplexMatrixD computeInitialCondition(MatrixD data, int rank, ComplexMatrixD modes) {
        // compute initial condition at time 0
        MatrixD firstCol = data.selectConsecutiveColumns(data.startCol(), data.startCol());
        ComplexMatrixD x1 = firstCol.toComplexMatrix();
        return modes.solve(x1, Matrices.createComplexD(rank, x1.numColumns()));
    }

    private static MatrixD getSecondSnapshot(MatrixD data) {
        // create the second time-shifted snapshot
        return data.selectConsecutiveColumns(data.startCol() + 1, data.endCol());
    }

    private static MatrixD getSigmaTruncatedInverse(SvdEconD svd, int rank) {
        double[] S = svd.getS();
        // pull out low-dimensional subspace
        MatrixD Sr = Matrices.createD(rank, rank);
        for (int i = Sr.startRow(); i <= Sr.endRow(); ++i) {
            Sr.set(i, i, S[i]);
        }
        return Sr.inverse();
    }

    private static ComplexMatrixD createTimeDynamicsMatrix(ComplexMatrixD omega, int rank, ComplexMatrixD time0,
            double tStart, double tEnd, int tNum) {
        // time dynamics matrix (b * e^omega*t)
        ComplexMatrixD timeDynamics = Matrices.createComplexD(rank, tNum);
        double dt = (tNum == 1) ? 0.0 : (tEnd - tStart) / (tNum - 1);
        ZdImpl omg = new ZdImpl(0.0);
        ZdImpl expOmg_k = new ZdImpl(0.0);
        ZdImpl time0_k = new ZdImpl(0.0);
        for (int i = 1; i <= tNum; ++i) {
            int colIdx = i - 1;
            double t = (i == tNum) ? tEnd : tStart + colIdx * dt;
            for (int k = 0; k < rank; ++k) {
                omega.get(k, k, omg);
                expOmegaT(omg.re(), omg.im(), t, expOmg_k);
                time0.get(k, 0, time0_k);
                double time0_k_re = time0_k.re();
                double time0_k_im = time0_k.im();
                double expOmg_k_re = expOmg_k.re();
                double expOmg_k_im = expOmg_k.im();
                double re = time0_k_re * expOmg_k_re - time0_k_im * expOmg_k_im;
                double im = time0_k_im * expOmg_k_re + time0_k_re * expOmg_k_im;
                timeDynamics.set(k, colIdx, re, im);
            }
        }
        return timeDynamics;
    }

    private static void expOmegaT(double omegaR, double omegaI, double t, ZdImpl out) {
        // e^omega*t
        omegaR = t * omegaR;
        omegaI = t * omegaI;
        double expRe = Math.exp(omegaR);
        omegaR = expRe * Math.cos(omegaI);
        omegaI = expRe * Math.sin(omegaI);
        out.setRe(omegaR);
        out.setIm(omegaI);
    }
}
