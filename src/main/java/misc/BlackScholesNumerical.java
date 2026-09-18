package misc;

/**
 * Prices a European call by solving the Black-Scholes PDE backwards in time with a Crank-Nicolson
 * scheme on a uniform grid in the underlying price S.
 * <p>
 * With tau = T - t the equation is
 *
 * <pre>
 *     dV/dtau = 0.5 * sigma^2 * S^2 * V_SS + r * S * V_S - r * V
 * </pre>
 *
 * Discretized on S_i = i * dS, Crank-Nicolson solves {@code A v_new = (2I - A) v_old} with
 * {@code A = I - 0.5 * dt * L}. The closed-form price is available as {@link #blackScholesCall} and
 * the two agree to about 6e-4 at the default grid resolution; the remaining error comes from
 * truncating the domain at S_MAX = 3 * K.
 */
public class BlackScholesNumerical {
    // Option parameters
    private static final double K = 100.0;    // strike price
    private static final double T = 1.0;      // time to maturity in years
    private static final double R = 0.05;     // risk-free rate (5%)
    private static final double SIGMA = 0.2;  // volatility (20%)

    // Grid parameters
    private static final int N_S = 600;       // steps in the underlying price S
    private static final int N_T = 500;       // time steps
    private static final double S_MAX = 300.0;// largest underlying price on the grid

    private BlackScholesNumerical() {
        throw new AssertionError();
    }

    /**
     * Crank-Nicolson price of the European call at {@code S = spot}, using the grid parameters
     * above. The spot has to lie on a grid node.
     */
    public static double price(double spot) {
        double ds = S_MAX / N_S;
        double dt = T / N_T;
        double[] v = new double[N_S + 1];

        // Terminal condition: the payoff at expiry
        for (int i = 0; i <= N_S; i++) {
            v[i] = Math.max(i * ds - K, 0.0); // call payoff max(S - K, 0)
        }

        double[] a = new double[N_S + 1];
        double[] b = new double[N_S + 1];
        double[] c = new double[N_S + 1];
        double[] rhs = new double[N_S + 1];

        TriDiagInPlace solver = new TriDiagInPlace(N_S + 1);

        // March backwards from T to 0
        for (int t = 0; t < N_T; t++) {
            for (int i = 1; i < N_S; i++) {
                double i2 = (double) i * i;

                double sigmaTerm = 0.5 * dt * SIGMA * SIGMA * i2;
                double driftTerm = 0.5 * dt * R * i;

                // Implicit matrix A = I - 0.5 * dt * L
                a[i] = 0.5 * (driftTerm - sigmaTerm);
                b[i] = 1.0 + sigmaTerm + 0.5 * dt * R;
                c[i] = 0.5 * (-driftTerm - sigmaTerm);

                // Explicit side (2I - A) v_old. The signs are the counterpart of the matrix above.
                rhs[i] = -a[i] * v[i - 1] + (2.0 - b[i]) * v[i] - c[i] * v[i + 1];
            }

            // Boundary conditions
            rhs[0] = 0.0; // S = 0: a call is worthless
            b[0] = 1.0;
            c[0] = 0.0;

            // S = S_MAX: the call approaches S - K * exp(-r * time to expiry)
            double tau = (t + 1) * dt;
            rhs[N_S] = S_MAX - K * Math.exp(-R * tau);
            b[N_S] = 1.0;
            a[N_S] = 0.0;

            solver.solveInPlace(rhs, 0, 1, a, b, c);
            System.arraycopy(rhs, 0, v, 0, v.length);
        }

        int index = (int) Math.round(spot / ds);
        return v[index];
    }

    /** Closed-form Black-Scholes price of a European call, the reference for {@link #price}. */
    public static double blackScholesCall(double spot, double strike, double timeToMaturity,
            double rate, double volatility) {
        double sqrtT = Math.sqrt(timeToMaturity);
        double d1 = (Math.log(spot / strike) + (rate + 0.5 * volatility * volatility) * timeToMaturity)
                / (volatility * sqrtT);
        double d2 = d1 - volatility * sqrtT;
        return spot * normalCdf(d1) - strike * Math.exp(-rate * timeToMaturity) * normalCdf(d2);
    }

    /**
     * Standard normal cumulative distribution, Abramowitz and Stegun 26.2.17. Absolute error below
     * 7.5e-8, which is two orders of magnitude finer than the discretization error it is used to
     * measure.
     */
    private static double normalCdf(double x) {
        double absX = Math.abs(x);
        double t = 1.0 / (1.0 + 0.2316419 * absX);
        double poly = t * (0.319381530
                + t * (-0.356563782
                + t * (1.781477937
                + t * (-1.821255978
                + t * 1.330274429))));
        double density = Math.exp(-0.5 * absX * absX) / Math.sqrt(2.0 * Math.PI);
        double upperTail = density * poly;
        return x >= 0.0 ? 1.0 - upperTail : upperTail;
    }

    public static void main(String[] args) {
        double numeric = price(100.0);
        double exact = blackScholesCall(100.0, K, T, R, SIGMA);
        System.out.printf("Option price at S=100: %.4f%n", numeric);
        System.out.printf("Closed-form reference: %.4f   (absolute error %.2e)%n",
                exact, Math.abs(numeric - exact));
    }
}
