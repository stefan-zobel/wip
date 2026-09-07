package math.complex;

/**
 * All roots of a complex polynomial at once, by the Aberth-Ehrlich iteration.
 * Nothing is deflated, so no root inherits another one's error.
 */
public final class AberthEhrlich {

    /** the unit roundoff */
    private static final double U = 0x1p-53;

    /** how much of it one complex Horner step costs, measured */
    private static final double HORNER = 1.0;

    /** enough for the cubic convergence, and for the linear one at a cluster */
    private static final int SWEEPS = 400;

    /** an angle no symmetric pattern of roots shares */
    private static final double TILT = 0.7;

    private static final double LN2 = 0.6931471805599453094172321;

    private AberthEhrlich() {
        throw new AssertionError("no instances");
    }

    /**
     * The roots of the polynomial whose coefficient of z^k is a[k].
     *
     * @param a the coefficients, lowest order first
     * @return the roots, as many as the degree, in no particular order
     */
    public static ComplexD[] roots(double[] a) {
        ComplexD[] c = new ComplexD[a.length];
        for (int k = 0; k < a.length; ++k) {
            c[k] = new ComplexD(a[k], 0.0);
        }
        return roots(c);
    }

    /**
     * The roots of the polynomial whose coefficient of z^k is a[k].
     *
     * @param a the coefficients, lowest order first
     * @return the roots, as many as the degree, in no particular order
     */
    public static ComplexD[] roots(ComplexD[] a) {
        int n = degree(a);
        if (n < 0) {
            throw new IllegalArgumentException("every number is a root of the zero polynomial");
        }
        if (n == 0) {
            return new ComplexD[0];
        }
        // z^m divides out, and puts m of the roots at the origin
        int m = 0;
        while (isZero(a[m])) {
            ++m;
        }
        ComplexD[] out = new ComplexD[n];
        for (int i = 0; i < m; ++i) {
            out[i] = ComplexD.Zero();
        }
        ComplexD[] c = tail(a, m, n);
        int d = n - m;
        if (d == 1) {
            out[m] = c[0].div(c[1]).neg();
            return out;
        }
        ComplexD[] z = start(c);
        solve(c, z);
        System.arraycopy(z, 0, out, m, d);
        return out;
    }

    private static boolean isZero(ComplexD w) {
        return w.re() == 0.0 && w.im() == 0.0;
    }

    private static int degree(ComplexD[] a) {
        for (int k = a.length - 1; k >= 0; --k) {
            if (!isZero(a[k])) {
                return k;
            }
        }
        return -1;
    }

    // a[m..n], the polynomial with its roots at the origin divided out
    private static ComplexD[] tail(ComplexD[] a, int m, int n) {
        ComplexD[] c = new ComplexD[n - m + 1];
        for (int k = 0; k < c.length; ++k) {
            c[k] = a[k + m];
        }
        return c;
    }

    // Bini's start: the upper convex hull of (k, log2|c_k|) predicts how many
    // roots have which modulus, far better than one circle for all of them
    private static ComplexD[] start(ComplexD[] c) {
        int n = c.length - 1;
        double[] l = new double[n + 1];
        for (int k = 0; k <= n; ++k) {
            double t = c[k].abs();
            l[k] = (t == 0.0) ? Double.NEGATIVE_INFINITY : Math.log(t) / LN2;
        }
        int[] hull = new int[n + 1];
        int h = 0;
        for (int k = 0; k <= n; ++k) {
            if (l[k] == Double.NEGATIVE_INFINITY) {
                continue;
            }
            while (h >= 2 && slope(l, hull[h - 2], hull[h - 1]) <= slope(l, hull[h - 1], k)) {
                --h;
            }
            hull[h++] = k;
        }
        ComplexD[] z = new ComplexD[n];
        int at = 0;
        for (int t = 0; t + 1 < h; ++t) {
            int k0 = hull[t];
            int k1 = hull[t + 1];
            int cnt = k1 - k0;
            double r = Math.pow(2.0, (l[k0] - l[k1]) / cnt);
            for (int j = 0; j < cnt; ++j) {
                z[at++] = ComplexD.fromPolar(r,
                        TILT + 2.0 * Math.PI * j / cnt + 2.0 * Math.PI * t / n);
            }
        }
        while (at < n) {
            z[at] = ComplexD.fromPolar(1.0, TILT + 2.0 * Math.PI * at / n);
            ++at;
        }
        return z;
    }

    private static double slope(double[] l, int i, int j) {
        return (l[j] - l[i]) / (j - i);
    }

    // the sweeps, each root frozen once its residual sinks into the roundoff
    private static void solve(ComplexD[] c, ComplexD[] z) {
        int n = z.length;
        boolean[] done = new boolean[n];
        ComplexD[] out = new ComplexD[1];
        for (int sweep = 0; sweep < SWEEPS; ++sweep) {
            int moved = 0;
            for (int i = 0; i < n; ++i) {
                if (done[i]) {
                    continue;
                }
                if (correction(c, z[i], out)) {
                    done[i] = true;
                    continue;
                }
                ComplexD w = out[0];
                if (w.isNan()) {
                    // a stationary point carries no direction, so step aside
                    z[i] = z[i].add(ComplexD.fromPolar(
                            Math.max(z[i].abs(), 1.0) * 0x1p-20, TILT + i));
                    ++moved;
                    continue;
                }
                ComplexD s = ComplexD.Zero();
                for (int j = 0; j < n; ++j) {
                    if (j == i) {
                        continue;
                    }
                    ComplexD gap = z[i].sub(z[j]);
                    if (!isZero(gap)) {
                        s = s.add(gap.inv());
                    }
                }
                ComplexD den = ComplexD.One().sub(w.mul(s));
                ComplexD step = isZero(den) ? w : w.div(den);
                if (step.isNan()) {
                    done[i] = true;
                    continue;
                }
                z[i] = z[i].sub(step);
                ++moved;
            }
            if (moved == 0) {
                return;
            }
        }
    }

    // The Newton correction p/p' at z, into out[0], NaN where p' vanishes.
    // Returns true once the residual has sunk into the roundoff of the
    // evaluation itself, below which it is only the noise of Horner
    private static boolean correction(ComplexD[] c, ComplexD z, ComplexD[] out) {
        int n = c.length - 1;
        double az = z.abs();
        if (az <= 1.0) {
            ComplexD p = c[n];
            ComplexD dp = ComplexD.Zero();
            double e = 0.5 * p.abs();
            for (int k = n - 1; k >= 0; --k) {
                dp = dp.mul(z).add(p);
                p = p.mul(z).add(c[k]);
                e = e * az + p.abs();
            }
            out[0] = isZero(dp) ? ComplexD.NaN() : p.div(dp);
            return p.abs() <= HORNER * U * (2.0 * e - p.abs());
        }
        // Outside the unit circle |a_n z^n| overflows long before the root is
        // wrong. The reversed polynomial q(w) = sum a_(n-j) w^j at w = 1/z is
        // the same value divided by z^n, and nothing in it can overflow.
        ComplexD w = z.inv();
        double aw = w.abs();
        ComplexD q = c[0];
        ComplexD dq = ComplexD.Zero();
        double e = 0.5 * q.abs();
        for (int k = 1; k <= n; ++k) {
            dq = dq.mul(w).add(q);
            q = q.mul(w).add(c[k]);
            e = e * aw + q.abs();
        }
        // p/p' = z q / (n q - w q'). Written with the w outside, the
        // denominator underflows to zero for a root of a large modulus
        ComplexD den = q.scale(n).sub(w.mul(dq));
        out[0] = isZero(den) ? ComplexD.NaN() : z.mul(q).div(den);
        return q.abs() <= HORNER * U * (2.0 * e - q.abs());
    }
}
