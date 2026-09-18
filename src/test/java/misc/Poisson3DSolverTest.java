package misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The acceptance test for the line relaxation: its fixed point has to be the discrete Poisson
 * solution, compared against a dense solve of the very same linear system.
 */
public class Poisson3DSolverTest {

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

    /** Dense solution of 6u - (six neighbours) = h^2 * f with Dirichlet zero. */
    private static double[] denseDiscretePoisson(double[] f, int n, double h2) {
        int m = n * n * n;
        double[][] a = new double[m][m];
        double[] rhs = new double[m];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int i = idx(n, x, y, z);
                    if (isBoundary(n, x, y, z)) {
                        a[i][i] = 1.0;
                        continue;
                    }
                    a[i][i] = 6.0;
                    a[i][idx(n, x - 1, y, z)] = -1.0;
                    a[i][idx(n, x + 1, y, z)] = -1.0;
                    a[i][idx(n, x, y - 1, z)] = -1.0;
                    a[i][idx(n, x, y + 1, z)] = -1.0;
                    a[i][idx(n, x, y, z - 1)] = -1.0;
                    a[i][idx(n, x, y, z + 1)] = -1.0;
                    rhs[i] = h2 * f[i];
                }
            }
        }
        return denseSolve(a, rhs);
    }

    private static double maxDifference(double[] p, double[] q) {
        double worst = 0.0;
        for (int i = 0; i < p.length; i++) {
            worst = Math.max(worst, Math.abs(p[i] - q[i]));
        }
        return worst;
    }

    @Test
    public void convergesToTheDenseSolution() {
        int n = 8;
        Poisson3DSolver solver = new Poisson3DSolver(n);
        solver.setCenterPointSource(100.0);

        int iterations = solver.iterate(1.0e-11, 20000);
        assertTrue("did not reach the tolerance", iterations > 0);

        double[] expected = denseDiscretePoisson(solver.source(), n, 1.0);
        assertEquals("iterated solution must match the dense solve", 0.0,
                maxDifference(expected, solver.solution()), 1.0e-9);
    }

    /** With a non-unit spacing the h^2 on the right-hand side has to be honored. */
    @Test
    public void honoursTheGridSpacing() {
        int n = 8;
        double h = 0.25;
        Poisson3DSolver solver = new Poisson3DSolver(n, h);
        solver.setCenterPointSource(100.0);
        assertTrue(solver.iterate(1.0e-11, 20000) > 0);

        double[] expected = denseDiscretePoisson(solver.source(), n, h * h);
        assertEquals(0.0, maxDifference(expected, solver.solution()), 1.0e-9);
    }

    @Test
    public void residualDropsBelowTolerance() {
        Poisson3DSolver solver = new Poisson3DSolver(10);
        solver.setCenterPointSource(50.0);
        int iterations = solver.iterate(1.0e-10, 20000);
        assertTrue("did not converge", iterations > 0);
        assertTrue("residual " + solver.residualInfinityNorm() + " above the tolerance",
                solver.residualInfinityNorm() <= 1.0e-10);
    }

    /** An unreachable tolerance must be reported, not silently accepted. */
    @Test
    public void reportsFailureToConverge() {
        Poisson3DSolver solver = new Poisson3DSolver(8);
        solver.setCenterPointSource(100.0);
        assertEquals(-1, solver.iterate(1.0e-300, 5));
    }

    /**
     * Regression guard for the data race, and for the Jacobi-within-a-sweep property: repeated
     * runs must agree bit for bit despite the parallel sweeps.
     */
    @Test
    public void repeatedRunsAreIdentical() {
        double[] first = null;
        for (int trial = 0; trial < 10; trial++) {
            Poisson3DSolver solver = new Poisson3DSolver(12);
            solver.setCenterPointSource(100.0);
            solver.iterate(1.0e-9, 60);
            if (first == null) {
                first = solver.solution().clone();
            } else {
                assertEquals("trial " + trial + " differs from the first run", 0.0,
                        maxDifference(first, solver.solution()), 0.0);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDegenerateSize() {
        new Poisson3DSolver(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveSpacing() {
        new Poisson3DSolver(8, 0.0);
    }
}
