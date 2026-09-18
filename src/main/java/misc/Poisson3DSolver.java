package misc;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Solves the 3D Poisson equation {@code -Laplacian u = f} on a cube with homogeneous Dirichlet
 * boundaries, by alternating line relaxation.
 * <p>
 * One sweep in direction d solves the tridiagonal system along every interior line of that
 * direction implicitly and takes the other two directions from the current iterate:
 *
 * <pre>
 *     6*u_new[i] - u_new[i-1] - u_new[i+1]
 *         = h^2 * f + u_old[y-1] + u_old[y+1] + u_old[z-1] + u_old[z+1]      (x sweep)
 * </pre>
 *
 * At the fixed point {@code u_new == u_old}, so this collapses to
 * {@code 6u - (all six neighbours) = h^2 * f}, which is exactly the discrete Poisson equation.
 * <p>
 * The right-hand side of a sweep is computed for the whole grid before any line is solved, so the
 * relaxation is of Jacobi type within a sweep and the result does not depend on the order in which
 * the lines are processed - that is what makes the parallel sweeps reproducible. The three
 * directional sweeps are applied one after another.
 * <p>
 * Each line matrix is strictly diagonally dominant, so the pivot-free Thomas algorithm in
 * {@link TriDiagInPlace} is stable. Convergence is linear and slows down as the grid is refined;
 * for large grids a multigrid method would be the appropriate answer.
 */
public class Poisson3DSolver {
    private final int n;
    private final double h2; // squared grid spacing
    private double[] u;      // the unknown potential
    private double[] work;   // right-hand side of the current sweep, becomes the next iterate
    private final double[] f;

    // One solver per thread: TriDiagInPlace keeps its forward sweep in instance fields, so a single
    // shared instance would be corrupted by the parallel sweeps below.
    private final ThreadLocal<TriDiagInPlace> solver;

    // Line matrix: 6 on the diagonal, -1 off it, identity rows at the two ends.
    private final double[] a;
    private final double[] b;
    private final double[] c;

    public Poisson3DSolver(int n) {
        this(n, 1.0);
    }

    public Poisson3DSolver(int n, double h) {
        if (n <= 2) {
            throw new IllegalArgumentException("n must be greater than 2, but was " + n);
        }
        if (h <= 0.0) {
            throw new IllegalArgumentException("h must be positive, but was " + h);
        }
        this.n = n;
        this.h2 = h * h;
        this.u = new double[n * n * n];
        this.work = new double[n * n * n];
        this.f = new double[n * n * n];
        this.solver = ThreadLocal.withInitial(() -> new TriDiagInPlace(n));

        this.a = new double[n];
        this.b = new double[n];
        this.c = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = -1.0;
            b[i] = 6.0;
            c[i] = -1.0;
        }
        b[0] = 1.0;
        c[0] = 0.0;
        b[n - 1] = 1.0;
        a[n - 1] = 0.0;
    }

    public int size() {
        return n;
    }

    /** The source term, flattened as {@code z*n*n + y*n + x}. Modifying it changes the problem. */
    public double[] source() {
        return f;
    }

    /** The current iterate, same layout as {@link #source()}. */
    public double[] solution() {
        return u;
    }

    public double at(int x, int y, int z) {
        return u[z * n * n + y * n + x];
    }

    /** A point source of the given strength in the middle of the grid. */
    public void setCenterPointSource(double strength) {
        int mid = n / 2;
        f[mid * n * n + mid * n + mid] = strength;
    }

    /**
     * Relaxes until the residual is at or below {@code tolerance}, or until {@code maxIterations}
     * iterations have been done. One iteration is a sweep in each of the three directions.
     *
     * @return the number of iterations performed, or -1 if the tolerance was not reached
     */
    public int iterate(double tolerance, int maxIterations) {
        for (int it = 1; it <= maxIterations; it++) {
            sweep(1, n, n * n);  // x lines
            sweep(n, 1, n * n);  // y lines
            sweep(n * n, 1, n);  // z lines
            if (residualInfinityNorm() <= tolerance) {
                return it;
            }
        }
        return -1;
    }

    /**
     * One directional sweep. {@code stride} steps along the line; {@code other1} and {@code other2}
     * are the strides of the two remaining directions, whose neighbours move to the right-hand side.
     */
    private void sweep(int stride, int other1, int other2) {
        final double[] current = u;
        final double[] next = work;

        IntStream.range(0, n).parallel().forEach(z -> {
            for (int y = 0; y < n; y++) {
                int base = z * n * n + y * n;
                for (int x = 0; x < n; x++) {
                    int i = base + x;
                    if (isBoundary(x, y, z)) {
                        next[i] = 0.0;
                    } else {
                        next[i] = h2 * f[i]
                                + current[i - other1] + current[i + other1]
                                + current[i - other2] + current[i + other2];
                    }
                }
            }
        });

        forEachInteriorLine(stride, offset -> solver.get().solveInPlace(next, offset, stride, a, b, c));

        u = next;
        work = current;
    }

    /** Runs the action on the start offset of every line whose two other coordinates are interior. */
    private void forEachInteriorLine(int stride, IntConsumer action) {
        if (stride == 1) { // x lines, indexed by (y, z)
            IntStream.range(1, n - 1).parallel().forEach(z -> {
                for (int y = 1; y < n - 1; y++) {
                    action.accept(z * n * n + y * n);
                }
            });
        } else if (stride == n) { // y lines, indexed by (x, z)
            IntStream.range(1, n - 1).parallel().forEach(z -> {
                for (int x = 1; x < n - 1; x++) {
                    action.accept(z * n * n + x);
                }
            });
        } else { // z lines, indexed by (x, y)
            IntStream.range(1, n - 1).parallel().forEach(y -> {
                for (int x = 1; x < n - 1; x++) {
                    action.accept(y * n + x);
                }
            });
        }
    }

    private boolean isBoundary(int x, int y, int z) {
        return x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1;
    }

    /** Largest absolute value of {@code -Laplacian u - f} over the interior. */
    public double residualInfinityNorm() {
        double worst = 0.0;
        for (int z = 1; z < n - 1; z++) {
            for (int y = 1; y < n - 1; y++) {
                for (int x = 1; x < n - 1; x++) {
                    int i = z * n * n + y * n + x;
                    double laplacian = 6.0 * u[i]
                            - u[i - 1] - u[i + 1]
                            - u[i - n] - u[i + n]
                            - u[i - n * n] - u[i + n * n];
                    worst = Math.max(worst, Math.abs(laplacian / h2 - f[i]));
                }
            }
        }
        return worst;
    }

    public static void main(String[] args) {
        int size = 40;
        Poisson3DSolver solver = new Poisson3DSolver(size);
        solver.setCenterPointSource(100.0);

        System.out.println("Solving the 3D Poisson equation...");
        int iterations = solver.iterate(1.0e-9, 20000);
        if (iterations < 0) {
            System.out.println("Did not reach the tolerance within the iteration limit.");
        } else {
            System.out.println("Converged after " + iterations + " iterations.");
        }
        int m = size / 2;
        System.out.printf("Potential at the centre: %.9f%n", solver.at(m, m, m));
        System.out.printf("Residual: %.3e%n", solver.residualInfinityNorm());
    }
}
