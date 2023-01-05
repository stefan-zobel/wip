package math.regression;

import math.probe.DoubleStatisticsNoSync;
import math.rng.PseudoRandom;
import math.rng.Stc64;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;

public class OLSTest {

    private static final PseudoRandom rnd = Stc64.getDefault();
    private static final DoubleStatisticsNoSync stats = new DoubleStatisticsNoSync();

    private static final int ROWS = 4000;

    private static class Data {
        final double y;
        final double x1;
        final double x2;
        final double x3;

        Data(double y, double x1, double x2, double x3) {
            this.y = y;
            this.x1 = x1;
            this.x2 = x2;
            this.x3 = x3;
        }

        @Override
        public String toString() {
            return "[y=" + y + ", x1=" + x1 + ", x2=" + x2 + "]";
        }
    }

    public static void main(String[] args) {
        double alpha = 0.05;
        MatrixD X = Matrices.createD(ROWS, 4);
        MatrixD y = Matrices.createD(ROWS, 1);
        for (int i = 0; i < ROWS; ++i) {
            Data row = next();
            stats.accept(row.y);
            y.set(i, 0, row.y);
            X.set(i, 0, 1.0);
            X.set(i, 1, row.x1);
            X.set(i, 2, row.x2);
            X.set(i, 3, row.x3);
        }

        System.out.println(stats);

        LSSummary smmry = OLS.estimate(alpha, X, y);

        System.out.println(smmry);
    }

    static Data next() {
        double beta0 = 150.0;
        double beta1 = 4.75;
        double beta2 = -2.33;
        double x1 = rnd.nextDouble(0.5, 99.5);
        double x2 = rnd.nextDouble(5.0, 75.0);
        double unused = rnd.nextDouble(-10, 110.0);
        double y = beta0 + beta1 * x1 + beta2 * x2 + rnd.nextGaussian(0, 100);
        Data next = new Data(y, x1, x2, unused);
        return next;
    }
}
