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
package math.dmd.examples;

import math.coord.LinSpace;
import math.fun.DIndexIterator;
import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.EvdComplexD;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.SvdEconComplexD;

/**
 * Dynamic mode decomposition MATLAB code example presented on YouTube by
 * {@code Nathan Kutz} rewritten in Java.
 * <p>
 * See <a href="https://www.youtube.com/watch?v=KAau5TBU0Sc">YouTube video</a>.
 */
public class Example1 {

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
        ComplexMatrixD X = Matrices.createComplexD(xi.size(), ti.size());

        for (DIndexIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();
            for (DIndexIterator xIt = xi.iterator(); xIt.hasNext(); /**/) {
                int rowIdx = xIt.nextIndex() - 1;
                Zd z = f(xIt.next(), t);
                X.set(rowIdx, colIdx, z.re(), z.im());
            }
        }

        // create time snapshots from measurements matrix
        ComplexMatrixD X1 = X.selectConsecutiveColumns(X.startCol(), X.endCol() - 1);
        ComplexMatrixD X2 = X.selectConsecutiveColumns(X.startCol() + 1, X.endCol());

        // step 1 of exact DMD algorithm
        SvdEconComplexD svd = X1.svdEcon();

        ComplexMatrixD U = svd.getU();
        ComplexMatrixD Vh = svd.getVh();
        double[] S = svd.getS();

        // pull out low-dimensional subspace
        ComplexMatrixD Sr = Matrices.createComplexD(rank, rank);
        for (int i = Sr.startRow(); i <= Sr.endRow(); ++i) {
            Sr.set(i, i, S[i], 0.0);
        }

        ComplexMatrixD Ur = U.selectConsecutiveColumns(U.startCol(), rank - 1);
        ComplexMatrixD Vr = Vh.conjugateTranspose().selectConsecutiveColumns(Vh.startCol(), rank - 1);

        // step 2: similarity-transform in the low-rank subspace
        // (ATilde takes us from one snapshot to the next in the low-rank
        // subspace)
        ComplexMatrixD ATilde = Ur.conjugateTranspose().times(X2).times(Vr).times(Sr.inverse());

        // step 3: compute the 'rank' eigenvalues / eigenvectors in the subspace
        EvdComplexD evd = ATilde.evd(true);
        ComplexMatrixD W = evd.getEigenvectors();
        Zd[] D = evd.getEigenvalues();

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

        // b = initial condition at time 0
        ComplexMatrixD x1 = X.selectConsecutiveColumns(X.startCol(), X.startCol());
        ComplexMatrixD b = Phi.solve(x1, Matrices.createComplexD(rank, x1.numColumns()));

        // time dynamics matrix (e^omega*t)
        ComplexMatrixD time_dynamics = Matrices.createComplexD(rank, ti.size());

        for (DIndexIterator tIt = ti.iterator(); tIt.hasNext(); /**/) {
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
        return f1(x, t).add(f2(x, t));
    }

    // first spatio-temporal pattern
    private static Zd f1(double x, double t) {
        Zd z = new ZdImpl(0.0, 2.3 * t);
        return z.exp().scale(sech(x + 3.0));
    }

    // second spatio-temporal pattern
    private static Zd f2(double x, double t) {
        Zd z = new ZdImpl(0.0, 2.8 * t);
        return z.exp().scale(2.0).scale(sech(x) * Math.tanh(x));
    }

    private static Zd expOmegaT(double omegaR, double omegaI, double t) {
        Zd z = new ZdImpl(omegaR, omegaI);
        return z.scale(t).exp();
    }

    private static double sech(double y) {
        return 1.0 / Math.cosh(y);
    }
}
