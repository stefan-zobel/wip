package misc;

import java.util.stream.IntStream;

/**
 * Implicit 3D heat equation on a cube with homogeneous Dirichlet boundaries.
 * <p>
 * Each time step is split into three implicit sweeps, one per coordinate direction:
 *
 * <pre>
 *     (I - r * d2_x) v1 = v0,   (I - r * d2_y) v2 = v1,   (I - r * d2_z) v3 = v2
 * </pre>
 *
 * with {@code r = alpha * dt / dx^2}. This is a locally one-dimensional (LOD) fractional-step
 * scheme, <b>not</b> Peaceman-Rachford ADI. The distinction matters when reading the code: LOD uses
 * the <i>full</i> time step in every sweep on purpose, because the product of the three operators
 * approximates the unsplit backward-Euler step
 * {@code (I - r * Laplacian) v_new = v_old} to within O(r^2). Using dt/3 per sweep, as ADI would
 * suggest, is what would make the scheme wrong. At the default parameters r = 0.001 and the
 * splitting error is about 3e-6 relative; {@code HeatEquation3DTest} measures it.
 * <p>
 * The scheme is unconditionally stable and first order in time.
 */
public class HeatEquation3D {
    public static final int DEFAULT_SIZE = 50;        // grid points per dimension
    public static final double DEFAULT_ALPHA = 0.01;  // thermal diffusivity
    public static final double DEFAULT_DT = 0.1;      // time step
    public static final double DEFAULT_DX = 1.0;      // grid spacing

    private final int n;
    private final double[] grid;

    // One solver per thread: TriDiagInPlace keeps its forward sweep in instance fields, so a single
    // shared instance would be corrupted by the parallel sweeps below.
    private final ThreadLocal<TriDiagInPlace> solver;

    // Tridiagonal coefficients, identical for all three directions
    private final double[] a;
    private final double[] b;
    private final double[] c;

    public HeatEquation3D() {
        this(DEFAULT_SIZE, DEFAULT_ALPHA, DEFAULT_DT, DEFAULT_DX);
    }

    public HeatEquation3D(int n, double alpha, double dt, double dx) {
        if (n <= 2) {
            throw new IllegalArgumentException("n must be greater than 2, but was " + n);
        }
        if (dx <= 0.0) {
            throw new IllegalArgumentException("dx must be positive, but was " + dx);
        }
        this.n = n;
        this.grid = new double[n * n * n];
        this.solver = ThreadLocal.withInitial(() -> new TriDiagInPlace(n));

        this.a = new double[n];
        this.b = new double[n];
        this.c = new double[n];

        setupCoefficients(alpha, dt, dx);
        setupInitialConditions();
    }

    private void setupCoefficients(double alpha, double dt, double dx) {
        double r = (alpha * dt) / (dx * dx);
        for (int i = 0; i < n; i++) {
            a[i] = -r;            // lower diagonal
            b[i] = 1.0 + 2.0 * r; // main diagonal
            c[i] = -r;            // upper diagonal
        }
        // Boundary rows are the identity, so the edge values are carried over unchanged. Starting
        // from zero there, this is a homogeneous Dirichlet condition.
        b[0] = 1.0;
        c[0] = 0.0;
        b[n - 1] = 1.0;
        a[n - 1] = 0.0;
    }

    /** A hot cube in the middle, the rest at zero. */
    private void setupInitialConditions() {
        int center = n / 2;
        // Half width 5 for the original 50^3 grid, clamped so that small grids keep a zero boundary.
        int half = Math.min(5, (n - 1) / 2);
        for (int z = center - half; z < center + half; z++) {
            for (int y = center - half; y < center + half; y++) {
                for (int x = center - half; x < center + half; x++) {
                    grid[z * n * n + y * n + x] = 50.0;
                }
            }
        }
    }

    /** The live grid, flattened as {@code z * n * n + y * n + x}. Modifying it changes the state. */
    public double[] grid() {
        return grid;
    }

    public int size() {
        return n;
    }

    public void step() {
        // LOD sub-step 1: x direction
        IntStream.range(0, n).parallel().forEach(z -> {
            for (int y = 0; y < n; y++) {
                solver.get().solveInPlace(grid, (z * n * n + y * n), 1, a, b, c);
            }
        });

        // LOD sub-step 2: y direction
        IntStream.range(0, n).parallel().forEach(z -> {
            for (int x = 0; x < n; x++) {
                solver.get().solveInPlace(grid, (z * n * n + x), n, a, b, c);
            }
        });

        // LOD sub-step 3: z direction
        IntStream.range(0, n).parallel().forEach(y -> {
            for (int x = 0; x < n; x++) {
                solver.get().solveInPlace(grid, (y * n + x), n * n, a, b, c);
            }
        });
    }

    public static void main(String[] args) {
        HeatEquation3D simulation = new HeatEquation3D();

        System.out.println("Starting simulation...");
        long start = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            simulation.step();
            if (i % 10 == 0) {
                System.out.println("Step " + i + " done.");
            }
        }

        long end = System.currentTimeMillis();
        double checksum = 0.0;
        for (double value : simulation.grid()) {
            checksum += value;
        }
        System.out.println("100 steps in " + (end - start) + "ms.");
        System.out.printf("Grid sum: %.12f%n", checksum);
    }
}
