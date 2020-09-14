package dmd.examples;

import net.jamu.matrix.MatrixD;

/**
 * Approximately optimal Singular Value truncation after Gavish and Donoho
 * (2014).
 */
class SVHT {

    static int threshold(MatrixD data, double[] singularValues) {
        double omega = computeOmega(data);
        double median = median(singularValues);
        double cutoff = omega * median;
        return threshold(singularValues, cutoff);
    }

    private static double median(double[] singularValues) {
        int len = singularValues.length;
        if (len % 2 != 0) {
            return singularValues[(len - 1) / 2];
        } else {
            int mid = len / 2;
            return (singularValues[mid - 1] + singularValues[mid]) / 2.0;
        }
    }

    private static double computeOmega(MatrixD data) {
        int rows = data.numRows();
        int cols = data.numColumns();
        int m = Math.min(rows, cols);
        int n = Math.max(rows, cols);
        double beta = m / (double) n;
        double betaSqr = beta * beta;
        double betaCub = betaSqr * beta;
        return 0.56 * betaCub - 0.95 * betaSqr + 1.82 * beta + 1.43;
    }

    private static int threshold(double[] singularValues, double cutoff) {
        int idx = 0;
        for (int i = 0; i < singularValues.length; ++i) {
            if (singularValues[i] <= cutoff) {
                // idx of last sv > cutoff
                idx = i - 1;
                break;
            }
        }
        // estimated rank
        idx = (idx < 0) ? 0 : idx;
        return idx + 1;
    }

    private SVHT() {
        throw new AssertionError();
    }
}
