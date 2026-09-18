package misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The three-sweep scheme of HeatEquation3D is a locally one-dimensional splitting of one implicit
 * backward-Euler step. These tests pin that down against a dense unsplit solve, so that nobody
 * "fixes" the full time step per sweep into dt/3.
 */
public class HeatEquation3DTest {

    private static int idx(int n, int x, int y, int z) {
        return z * n * n + y * n + x;
    }

    private static boolean isBoundary(int n, int x, int y, int z) {
        return x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1;
    }

    /** Dense Gaussian elimination with partial pivoting. */
    private static double[] denseSolve(double[][] matrix, double[] rhs) {
        int m = rhs.length;
        double[][] a = new double[m][];
        for (int i = 0; i < m; i++) {
            a[i] = matrix[i].clone();
        }
        double[] x = rhs.clone();
        for (int k = 0; k < m; k++) {
            int pivot = k;
            for (int i = k + 1; i < m; i++) {
                if (Math.abs(a[i][k]) > Math.abs(a[pivot][k])) {
                    pivot = i;
                }
            }
            double[] rowTmp = a[k];
            a[k] = a[pivot];
            a[pivot] = rowTmp;
            double valTmp = x[k];
            x[k] = x[pivot];
            x[pivot] = valTmp;
            for (int i = k + 1; i < m; i++) {
                double factor = a[i][k] / a[k][k];
                if (factor == 0.0) {
                    continue;
                }
                for (int j = k; j < m; j++) {
                    a[i][j] -= factor * a[k][j];
                }
                x[i] -= factor * x[k];
            }
        }
        for (int i = m - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < m; j++) {
                sum -= a[i][j] * x[j];
            }
            x[i] = sum / a[i][i];
        }
        return x;
    }

    /** One unsplit implicit step: (I - r * Laplacian_h) v_new = v_old, Dirichlet zero. */
    private static double[] unsplitBackwardEuler(double[] v0, int n, double r) {
        int m = n * n * n;
        double[][] a = new double[m][m];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int i = idx(n, x, y, z);
                    if (isBoundary(n, x, y, z)) {
                        a[i][i] = 1.0;
                        continue;
                    }
                    a[i][i] = 1.0 + 6.0 * r;
                    a[i][idx(n, x - 1, y, z)] = -r;
                    a[i][idx(n, x + 1, y, z)] = -r;
                    a[i][idx(n, x, y - 1, z)] = -r;
                    a[i][idx(n, x, y + 1, z)] = -r;
                    a[i][idx(n, x, y, z - 1)] = -r;
                    a[i][idx(n, x, y, z + 1)] = -r;
                }
            }
        }
        return denseSolve(a, v0);
    }

    private static double maxDifference(double[] p, double[] q) {
        double worst = 0.0;
        for (int i = 0; i < p.length; i++) {
            worst = Math.max(worst, Math.abs(p[i] - q[i]));
        }
        return worst;
    }

    /**
     * alpha, dt and dx are chosen so that r = alpha * dt / dx^2 takes the wanted value; with dx = 1
     * and dt = 1 that is just alpha.
     */
    private static double splittingErrorAt(double r) {
        int n = 8;
        HeatEquation3D simulation = new HeatEquation3D(n, r, 1.0, 1.0);
        double[] initial = simulation.grid().clone();

        simulation.step();
        double[] reference = unsplitBackwardEuler(initial, n, r);
        return maxDifference(simulation.grid(), reference);
    }

    @Test
    public void splittingMatchesUnsplitBackwardEulerAtTheDefaultRate() {
        // r = 0.001 is what the default parameters give: 0.01 * 0.1 / 1.0
        assertTrue("splitting error at r=0.001 must stay tiny", splittingErrorAt(0.001) < 1.0e-3);
    }

    @Test
    public void splittingErrorGrowsQuadraticallyWithTheStep() {
        double small = splittingErrorAt(0.005);
        double large = splittingErrorAt(0.05);
        assertTrue("error at r=0.05 must stay bounded", large < 0.3);
        // Ten times the step must cost far more than ten times the error, but clearly less than
        // a thousand times - that is the signature of an O(r^2) splitting error.
        double ratio = large / small;
        assertTrue("expected roughly quadratic growth, got a ratio of " + ratio,
                ratio > 30.0 && ratio < 300.0);
    }

    @Test
    public void boundaryStaysZero() {
        int n = 10;
        HeatEquation3D simulation = new HeatEquation3D(n, 0.01, 0.1, 1.0);
        for (int step = 0; step < 10; step++) {
            simulation.step();
        }
        double[] grid = simulation.grid();
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (isBoundary(n, x, y, z)) {
                        assertEquals("boundary at " + x + "," + y + "," + z, 0.0,
                                grid[idx(n, x, y, z)], 0.0);
                    }
                }
            }
        }
    }

    /**
     * Regression guard for the data race: the parallel sweeps must produce the same grid every
     * time. A shared TriDiagInPlace instance makes this fail.
     */
    @Test
    public void repeatedRunsAreIdentical() {
        double[] first = null;
        for (int trial = 0; trial < 10; trial++) {
            HeatEquation3D simulation = new HeatEquation3D(24, 0.05, 1.0, 1.0);
            for (int step = 0; step < 3; step++) {
                simulation.step();
            }
            if (first == null) {
                first = simulation.grid().clone();
            } else {
                assertEquals("trial " + trial + " differs from the first run", 0.0,
                        maxDifference(first, simulation.grid()), 0.0);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDegenerateSize() {
        new HeatEquation3D(2, 0.01, 0.1, 1.0);
    }
}
