package misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.stream.IntStream;

import org.junit.Test;

/**
 * TriDiagInPlace keeps the forward sweep in instance fields and is therefore not thread-safe.
 * The parallel test below is the regression guard for that: it reproduces the access pattern of
 * HeatEquation3D and Poisson3DSolver and fails as soon as one instance is shared across threads.
 */
public class TriDiagInPlaceTest {

    private static final double TOLERANCE = 1.0e-12;

    /** Dense reference solve with partial pivoting. */
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

    @Test
    public void solvesADiagonallyDominantSystem() {
        int n = 9;
        double[] lower = new double[n];
        double[] diag = new double[n];
        double[] upper = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = -1.0;
            diag[i] = 4.0 + 0.1 * i;
            upper[i] = -1.0;
        }

        double[] rhs = new double[n];
        for (int i = 0; i < n; i++) {
            rhs[i] = Math.sin(0.7 * i) + 2.0;
        }

        double[][] dense = new double[n][n];
        for (int i = 0; i < n; i++) {
            dense[i][i] = diag[i];
            if (i > 0) {
                dense[i][i - 1] = lower[i];
            }
            if (i < n - 1) {
                dense[i][i + 1] = upper[i];
            }
        }
        double[] expected = denseSolve(dense, rhs);

        double[] actual = rhs.clone();
        new TriDiagInPlace(n).solveInPlace(actual, 0, 1, lower, diag, upper);

        for (int i = 0; i < n; i++) {
            assertEquals("component " + i, expected[i], actual[i], TOLERANCE);
        }
    }

    /** A stride greater than one addresses a column of a flattened grid. */
    @Test
    public void solvesAStridedSlice() {
        int n = 6;
        int stride = 4;
        int offset = 2;
        double[] lower = new double[n];
        double[] diag = new double[n];
        double[] upper = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = -1.0;
            diag[i] = 3.0;
            upper[i] = -1.0;
        }

        double[] compact = new double[n];
        double[] grid = new double[offset + n * stride + 3];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = -7.0; // sentinel outside the slice
        }
        for (int i = 0; i < n; i++) {
            double value = 1.0 + i;
            compact[i] = value;
            grid[offset + i * stride] = value;
        }

        new TriDiagInPlace(n).solveInPlace(compact, 0, 1, lower, diag, upper);
        new TriDiagInPlace(n).solveInPlace(grid, offset, stride, lower, diag, upper);

        for (int i = 0; i < n; i++) {
            assertEquals("component " + i, compact[i], grid[offset + i * stride], 0.0);
        }
        for (int i = 0; i < grid.length; i++) {
            boolean inSlice = i >= offset && i <= offset + (n - 1) * stride && (i - offset) % stride == 0;
            if (!inSlice) {
                assertEquals("wrote outside the slice at " + i, -7.0, grid[i], 0.0);
            }
        }
    }

    @Test
    public void rejectsDegenerateSize() {
        for (int n : new int[] { -1, 0, 1 }) {
            try {
                new TriDiagInPlace(n);
                fail("n = " + n + " must be rejected");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    /**
     * The access pattern of the PDE solvers: many independent lines of one grid, solved from a
     * parallel stream. With one solver per thread the result must be bit-identical to the serial
     * one, every time. Sharing a single TriDiagInPlace across the threads makes this fail.
     */
    @Test
    public void parallelLineSolvesMatchSerial() {
        final int n = 16;
        final double r = 0.25;
        final double[] lower = new double[n];
        final double[] diag = new double[n];
        final double[] upper = new double[n];
        for (int i = 0; i < n; i++) {
            lower[i] = -r;
            diag[i] = 1.0 + 2.0 * r;
            upper[i] = -r;
        }
        diag[0] = 1.0;
        upper[0] = 0.0;
        diag[n - 1] = 1.0;
        lower[n - 1] = 0.0;

        double[] initial = new double[n * n * n];
        for (int i = 0; i < initial.length; i++) {
            initial[i] = (i * 37 % 101) / 101.0;
        }

        double[] serial = initial.clone();
        TriDiagInPlace serialSolver = new TriDiagInPlace(n);
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                serialSolver.solveInPlace(serial, z * n * n + y * n, 1, lower, diag, upper);
            }
        }

        for (int trial = 0; trial < 20; trial++) {
            final double[] parallel = initial.clone();
            final ThreadLocal<TriDiagInPlace> solver = ThreadLocal.withInitial(() -> new TriDiagInPlace(n));
            IntStream.range(0, n).parallel().forEach(z -> {
                for (int y = 0; y < n; y++) {
                    solver.get().solveInPlace(parallel, z * n * n + y * n, 1, lower, diag, upper);
                }
            });
            assertArrayEquals("trial " + trial + " differs from the serial result", serial, parallel, 0.0);
        }
    }
}
