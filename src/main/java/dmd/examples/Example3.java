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
package dmd.examples;

import math.coord.LinSpace;
import math.fun.DIterator;
import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.EvdComplexD;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;
import net.jamu.matrix.SvdEconD;

/**
 * Equivalent to example 2 but with the switch to the complex domain occurring
 * later.
 */
public class Example3 {

    static final double x_start = -10.0;
    static final double x_end = 10.0;
    static final int x_num = 400;

    static final double t_start = 0.0;
    static final double t_end = 4.0 * Math.PI;
    static final int t_num = 200;

    // assume rank=2 truncation
    static final int rank = 2;

    public static void main(String[] args) {
        // space
        LinSpace xi = LinSpace.linspace(x_start, x_end, x_num);
        // time
        LinSpace ti = LinSpace.linspace(t_start, t_end, t_num);

        // build data 'measurements' matrix
        MatrixD X_ = Matrices.createD(xi.size(), ti.size());

        for (DIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();
            for (DIterator xIt = xi.iterator(); xIt.hasNext(); /**/) {
                int rowIdx = xIt.nextIndex() - 1;
                Zd z = f(xIt.next(), t);
                // copy only the real part
                X_.set(rowIdx, colIdx, z.re());
            }
        }

        // create first snapshot from measurements matrix
        MatrixD X1_ = X_.selectConsecutiveColumns(X_.startCol(), X_.endCol() - 1);

        // step 1 of exact DMD algorithm
        SvdEconD svd = X1_.svdEcon();

        MatrixD U = svd.getU();
        MatrixD Vt = svd.getVt();
        double[] S = svd.getS();

        // pull out low-dimensional subspace
        MatrixD Sr = Matrices.createD(rank, rank);
        for (int i = Sr.startRow(); i <= Sr.endRow(); ++i) {
            Sr.set(i, i, S[i]);
        }

        MatrixD Ur = U.selectConsecutiveColumns(U.startCol(), rank - 1);
        MatrixD Vr = Vt.transpose().selectConsecutiveColumns(Vt.startCol(), rank - 1);

        // create the second time-shifted snapshot
        MatrixD X2_ = X_.selectConsecutiveColumns(X_.startCol() + 1, X_.endCol());

        // step 2: similarity-transform in the low-rank subspace
        // (ATilde takes us from one snapshot to the next in the low-rank
        // subspace)
        MatrixD ATilde_ = Ur.transpose().times(X2_).times(Vr).times(Sr.inverse());

        // copy the real ATilde_ into the complex matrix ATilde
        // (from here on everything is done in the complex domain)
        ComplexMatrixD ATilde = ATilde_.toComplexMatrix();

        // step 3: compute the 'rank' eigenvalues / eigenvectors in the subspace
        EvdComplexD evd = ATilde.evd(true);
        ComplexMatrixD W = evd.getEigenvectors();
        Zd[] D = evd.getEigenvalues();

        // copy the second snapshot into complex matrix
        ComplexMatrixD X2 = Matrices.convertToComplex(X2_);

        // step 4: get back into high-dimensional space
        // Phi contains the modes of the fitted linear system
        ComplexMatrixD Phi = X2.times(Vr).times(Sr.inverse()).times(W);

        double dt = ti.spacing();
        // omega contains the eigenvalues of the fitted linear system
        ComplexMatrixD omega = Matrices.createComplexD(D.length, D.length);
        for (int i = 0; i < D.length; ++i) {
            Zd z = D[i].copy();
            z.ln().scale(1.0 / dt);
            omega.set(i, i, z.re(), z.im());
        }

        // convert measurements data to complex matrix
        ComplexMatrixD X = Matrices.convertToComplex(X_);

        // b = initial condition at time 0
        ComplexMatrixD x1 = X.selectConsecutiveColumns(X.startCol(), X.startCol());
        ComplexMatrixD b = Phi.solve(x1, Matrices.createComplexD(rank, x1.numColumns()));

        // time dynamics matrix (e^omega*t)
        ComplexMatrixD time_dynamics = Matrices.createComplexD(rank, ti.size());

        for (DIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();

            for (int k = 0; k < rank; ++k) {
                Zd omg = omega.get(k, k);
                Zd expOmg_k = expOmegaT(omg.re(), omg.im(), t);
                Zd b_k = b.get(k, 0);
                Zd timeComponent = b_k.mul(expOmg_k);
                time_dynamics.set(k, colIdx, timeComponent.re(), timeComponent.im());
            }
        }

        // spatio-temporal reconstruction
        ComplexMatrixD X_dmd = Phi.times(time_dynamics);

        System.out.println("reconstructed:" + X_dmd);
        System.out.println("original     :" + X);

        // compare Frobenius norms for approximate equality
        double normDmd = X_dmd.normF();
        double normData = X.normF();
        System.out.println("reconstructed: " + normDmd);
        System.out.println("original     : " + normData);
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
