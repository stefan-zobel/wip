package misc;

public class TriDiagInPlace {
    private final int n;
    private final double[] cp; // auxiliary memory
    private final double[] dp;

    public TriDiagInPlace(int n) {
        this.n = n;
        this.cp = new double[n];
        this.dp = new double[n];
    }

    /**
     * Solves the system directly on the large grid array.
     * @param grid The flat 1D total grid (is modified directly)
     * @param offset Start index of the current rod
     * @param stride Step size to the next element of the rod
     * @param a, b, c The tridiagonal coefficients (1D arrays of length n)
     */
    public void solveInPlace(double[] grid, int offset, int stride, double[] a, double[] b, double[] c) {
        // Forward Sweep
        cp[0] = c[0] / b[0];
        dp[0] = grid[offset] / b[0];

        for (int i = 1; i < n; i++) {
            int currentIdx = offset + i * stride;
            int prevIdx = offset + (i - 1) * stride;

            double m = 1.0 / (b[i] - a[i] * cp[i - 1]);
            if (i < n - 1) {
                cp[i] = c[i] * m;
            }
            dp[i] = (grid[currentIdx] - a[i] * dp[i - 1]) * m;
        }

        // Back Substitution directly into the grid array
        grid[offset + (n - 1) * stride] = dp[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            int currentIdx = offset + i * stride;
            int nextIdx = offset + (i + 1) * stride;
            grid[currentIdx] = dp[i] - cp[i] * grid[nextIdx];
        }
    }
}
