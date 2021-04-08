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
package math.dmd;

import math.coord.LinSpace;
import math.fun.DIndexIterator;
import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.EvdComplexD;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;
import net.jamu.matrix.SvdEconD;

/**
 * Partially refactored version of example 3.
 */
public class Example4 {

    static final double x_start = -10.0;
    static final double x_end = 10.0;
    static final int x_num = 400;

    static final double t_start = 0.0;
    static final double t_end = 4.0 * Math.PI;
    static final int t_num = 200;

    // space (TODO)
    static final LinSpace xi = LinSpace.linspace(x_start, x_end, x_num);
    // time (TODO)
    static final LinSpace ti = LinSpace.linspace(t_start, t_end, t_num);

    public static void main(String[] args) {

        // build data 'measurements' matrix
        MatrixD data = setupMeasurementsMatrix();

        // step 1 of exact DMD algorithm
        SvdEconD svd = computeSvd(data);

        // estimate rank truncation from SVD
        final int rank = estimateRank(svd);

        // step 4: get back into high-dimensional space
        // Modes.Phi contains the modes of the fitted linear system and
        // Modes.eigs contains the eigenvalues in the subspace
        Modes modes = computeModesAndEigenvalues(data, svd, rank);

        // omega contains the eigenvalues of the fitted linear system
        ComplexMatrixD omega = computeOmega(modes.eigs, getDeltaT());

        // b = initial condition at time 0
        ComplexMatrixD b = computeInitialCondition(data, rank, modes.Phi);

        // time dynamics matrix (e^omega*t)
        ComplexMatrixD time_dynamics = createTimeDynamicsMatrix(omega, rank, b);

        // spatio-temporal reconstruction
        ComplexMatrixD X_dmd = modes.Phi.times(time_dynamics);
        // convert measurements data to complex matrix for better comparison
        ComplexMatrixD X = Matrices.convertToComplex(data);

        System.out.println("reconstructed:" + X_dmd);
        System.out.println("original     :" + X);

        // compare Frobenius norms for approximate equality
        double normDmd = X_dmd.normF();
        double normData = X.normF();
        System.out.println("reconstructed: " + normDmd);
        System.out.println("original     : " + normData);
    }

    private static int estimateRank(SvdEconD svd) {
        // TODO
        return 2;
    }

    private static double getDeltaT() {
        // TODO
        return ti.spacing();
    }

    private static ComplexMatrixD createTimeDynamicsMatrix(ComplexMatrixD omega, int rank, ComplexMatrixD time0) {
        ComplexMatrixD time_dynamics = Matrices.createComplexD(rank, ti.size());
        for (DIndexIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();

            for (int k = 0; k < rank; ++k) {
                Zd omg = omega.get(k, k);
                Zd expOmg_k = expOmegaT(omg.re(), omg.im(), t);
                Zd time0_k = time0.get(k, 0);
                Zd timeComponent = time0_k.mul(expOmg_k);
                time_dynamics.set(k, colIdx, timeComponent.re(), timeComponent.im());
            }
        }
        return time_dynamics;
    }

    private static ComplexMatrixD computeInitialCondition(MatrixD data, int rank, ComplexMatrixD modes) {
        // compute initial condition at time 0
        MatrixD firstCol = data.selectConsecutiveColumns(data.startCol(), data.startCol());
        ComplexMatrixD x1 = firstCol.toComplexMatrix();
        return modes.solve(x1, Matrices.createComplexD(rank, x1.numColumns()));
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

    private static Modes computeModesAndEigenvalues(MatrixD data, SvdEconD svd, int rank) {
        MatrixD U = svd.getU();
        MatrixD Vt = svd.getVt();
        // pull out U / V for low-dimensional subspace
        MatrixD Ur = U.selectConsecutiveColumns(U.startCol(), rank - 1);
        MatrixD Vr = Vt.transpose().selectConsecutiveColumns(Vt.startCol(), rank - 1);
        // create the second time-shifted snapshot
        MatrixD snapshot = getSecondSnapshot(data);
        // pull out Sigma for low-dimensional subspace
        MatrixD Sr = getSigmaTruncated(svd, rank);
        // step 2: similarity-transform in the low-rank subspace
        // (ATilde takes us from one snapshot to the next in the low-rank
        // subspace)
        MatrixD ATilde_ = Ur.transpose().times(snapshot).times(Vr).times(Sr.inverse());
        // copy the real ATilde_ into the complex matrix ATilde
        // (from here on everything is done in the complex domain)
        ComplexMatrixD ATilde = ATilde_.toComplexMatrix();
        // step 3: compute the 'rank' eigenvalues / eigenvectors in the subspace
        EvdComplexD evd = ATilde.evd(true);
        ComplexMatrixD W = evd.getEigenvectors();
        Zd[] D = evd.getEigenvalues();
        // create Modes
        Modes modes = new Modes();
        modes.eigs = D;
        // copy the second snapshot into complex matrix
        ComplexMatrixD X2 = Matrices.convertToComplex(snapshot);
        // step 4: get back into high-dimensional space
        // Phi contains the modes of the fitted linear system
        ComplexMatrixD Phi = X2.times(Vr).times(Sr.inverse()).times(W);
        modes.Phi = Phi;
        return modes;
    }

    private static MatrixD getSigmaTruncated(SvdEconD svd, int rank) {
        double[] S = svd.getS();
        // pull out low-dimensional subspace
        MatrixD Sr = Matrices.createD(rank, rank);
        for (int i = Sr.startRow(); i <= Sr.endRow(); ++i) {
            Sr.set(i, i, S[i]);
        }
        return Sr;
    }

    private static MatrixD getSecondSnapshot(MatrixD data) {
        // create the second time-shifted snapshot
        return data.selectConsecutiveColumns(data.startCol() + 1, data.endCol());
    }

    private static SvdEconD computeSvd(MatrixD data) {
        // create first snapshot from measurements matrix
        MatrixD X1_ = data.selectConsecutiveColumns(data.startCol(), data.endCol() - 1);
        // step 1 of exact DMD algorithm
        return X1_.svdEcon();
    }

    private static MatrixD setupMeasurementsMatrix() {
        // build data 'measurements' matrix
        MatrixD X_ = Matrices.createD(xi.size(), ti.size());

        for (DIndexIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();
            for (DIndexIterator xIt = xi.iterator(); xIt.hasNext(); /**/) {
                int rowIdx = xIt.nextIndex() - 1;
                Zd z = f(xIt.next(), t);
                // copy only the real part
                X_.set(rowIdx, colIdx, z.re());
            }
        }

        return X_;
    }

    // merged spatio-temporal signal
    private static Zd f(double x, double t) {
        return f1a(x, t).add(f2a(x, t));
    }

    // first spatio-temporal pattern
    private static Zd f1a(double x, double t) {
        Zd zt = new ZdImpl(0.1, 2.2 * t).exp();
        Zd zx = new ZdImpl(sech(x + 3.0), Math.tanh(x));
        return zt.mul(zx);
    }

    // second spatio-temporal pattern
    private static Zd f2a(double x, double t) {
        Zd zt = new ZdImpl(0.1, -2.2 * t).exp();
        Zd zx = new ZdImpl(sech(x - 3.0), -Math.tanh(x));
        return zt.mul(zx);
    }

    private static Zd expOmegaT(double omegaR, double omegaI, double t) {
        Zd z = new ZdImpl(omegaR, omegaI);
        return z.scale(t).exp();
    }

    private static double sech(double y) {
        return 1.0 / Math.cosh(y);
    }

    @SuppressWarnings("unused")
    private static double cosh(double y) {
        return Math.cosh(y);
    }
}
