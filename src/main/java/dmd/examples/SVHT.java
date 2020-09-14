package dmd.examples;

import net.jamu.matrix.MatrixD;

/**
 * Approximately optimal Singular Value truncation after Gavish and Donoho
 * (2014).
 * 
 * @see https://arxiv.org/pdf/1305.5870.pdf
 */
class SVHT {

    /** The IEEE 754 machine epsilon from Cephes: (2^-53) */
    static final double MACH_EPS_DBL = 1.11022302462515654042e-16;
    static final double TOL = 5.0 * MACH_EPS_DBL;
    static final double FAIR_SHARE = 1.0 - 1e-4;

    static int threshold(MatrixD data, double[] singularValues) {
        double omega = computeOmega(data);
        double median = median(singularValues);
        double cutoff = omega * median;
        return threshold(singularValues, cutoff);
    }

    private static double median(double[] singularValues) {
        int len = singularValues.length;
        int endIdx = len - 1;
        for (int i = endIdx; i >= 0; --i) {
            if (singularValues[i] > TOL) {
                endIdx = i;
                break;
            }
        }
        if (endIdx < len - 1) {
            len = endIdx + 1;
        }
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
        if (idx > 0) {
            double cap = FAIR_SHARE * sum(singularValues);
            double sum = 0.0;
            int lastIdx = 0;
            for (int i = 0; i <= idx && sum < cap; ++i) {
                sum += singularValues[i];
                lastIdx = i;
            }
            idx = Math.min(idx, lastIdx);
        }
        // estimated rank
        idx = (idx < 0) ? 0 : idx;
        return idx + 1;
    }

    private static double sum(double[] singularValues) {
        double sum = 0.0;
        for (int i = 0; i < singularValues.length; ++i) {
            double sv = singularValues[i];
            if (sv <= TOL) {
                break;
            }
            sum += sv;
        }
        return sum;
    }

    private SVHT() {
        throw new AssertionError();
    }
}
