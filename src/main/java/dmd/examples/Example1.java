package dmd.examples;

import math.coord.LinSpace;
import math.fun.DIterator;
import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.ComplexMatrixD;
import net.jamu.matrix.EvdComplexD;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.SvdEconComplexD;

/**
 * Dynamic mode decomposition MATLAB code example presented on YouTube by
 * {@code Nathan J. Kutz} rewritten in Java.
 * 
 * @see https://www.youtube.com/watch?v=KAau5TBU0Sc
 */
public class Example1 {

    static final double x_start = -10.0;
    static final double x_end = 10.0;
    static final int x_num = 400;

    static final double t_start = 0.0;
    static final double t_end = 4.0 * Math.PI;
    static final int t_num = 200;

    static final int rank = 2;

    public static void main(String[] args) {
        LinSpace X = LinSpace.linspace(x_start, x_end, x_num);
        LinSpace T = LinSpace.linspace(t_start, t_end, t_num);

        ComplexMatrixD data = Matrices.createComplexD(X.size(), T.size());

        for (DIterator tIt = T.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();
            for (DIterator xIt = X.iterator(); xIt.hasNext(); /**/) {
                int rowIdx = xIt.nextIndex() - 1;
                Zd z = f(xIt.next(), t);
                data.set(rowIdx, colIdx, z.re(), z.im());
            }
        }

        ComplexMatrixD X1 = data.selectConsecutiveColumns(data.startCol(), data.endCol() - 1);
        ComplexMatrixD X2 = data.selectConsecutiveColumns(data.startCol() + 1, data.endCol());

        SvdEconComplexD svd = X1.svdEcon();

        ComplexMatrixD U = svd.getU();
        ComplexMatrixD Vh = svd.getVh();
        double[] S = svd.getS();

        ComplexMatrixD Sr = Matrices.createComplexD(rank, rank);
        for (int i = Sr.startRow(); i <= Sr.endRow(); ++i) {
            Sr.set(i, i, S[i], 0.0);
        }

        ComplexMatrixD Ur = U.selectConsecutiveColumns(U.startCol(), rank - 1);
        ComplexMatrixD Vr = Vh.conjugateTranspose().selectConsecutiveColumns(Vh.startCol(), rank - 1);

        ComplexMatrixD ATilde = Ur.conjugateTranspose().times(X2).times(Vr).times(Sr.inverse());

        EvdComplexD evd = ATilde.evd(true);
        ComplexMatrixD W = evd.getEigenvectors();
        Zd[] D = evd.getEigenvalues();

        ComplexMatrixD Phi = X2.times(Vr).times(Sr.inverse()).times(W);

        double dt = T.spacing();
        ComplexMatrixD omega = Matrices.createComplexD(D.length, D.length);
        for (int i = 0; i < D.length; ++i) {
            Zd z = D[i].copy();
            z.ln().scale(1.0 / dt);
            omega.set(i, i, z.re(), z.im());
        }

        // initial condition at time 0
        ComplexMatrixD x1 = data.selectConsecutiveColumns(data.startCol(), data.startCol());
        ComplexMatrixD B = Phi.solve(x1, Matrices.createComplexD(rank, x1.numColumns()));

        // time dynamics matrix
        ComplexMatrixD time_dynamics = Matrices.createComplexD(rank, T.size());

        for (DIterator tIt = T.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();

            for (int k = 0; k < rank; ++k) {
                Zd omg = omega.get(k, k);
                Zd expOmg_k = expOmegaT(omg.re(), omg.im(), t);
                Zd b_k = B.get(k, 0);
                Zd timeComponent = b_k.mul(expOmg_k);
                time_dynamics.set(k, colIdx, timeComponent.re(), timeComponent.im());
            }
        }

        // reconstruction
        ComplexMatrixD X_dmd = Phi.times(time_dynamics);

        System.out.println("reconstructed:" + X_dmd);
        System.out.println("original     :" + data);

        // compare Frobenius norms for approximate equality
        double normDmd = X_dmd.normF();
        double normData = data.normF();
        System.out.println("reconstructed: " + normDmd);
        System.out.println("original     : " + normData);
    }

    private static Zd f(double x, double t) {
        return f1(x, t).add(f2(x, t));
    }

    private static Zd f1(double x, double t) {
        Zd z = new ZdImpl(0.0, 2.3 * t);
        return z.exp().scale(sech(x + 3.0));
    }

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
