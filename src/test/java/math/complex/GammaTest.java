/*
 * Copyright 2026 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package math.complex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import org.junit.Test;

/**
 * Gamma against an oracle that does not take its route: Stirling at 60 digits
 * with a much longer shift, plus identities that need no series at all.
 */
public class GammaTest {

    private static final double U = 0x1p-52;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NAN = Double.NaN;

    private static final MathContext MC = new MathContext(60);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal EPS = new BigDecimal("1e-70");
    private static final BigDecimal PI = new BigDecimal(
            "3.14159265358979323846264338327950288419716939937510582097494459230781640628620899");

    /** the oracle shifts this far out, where twenty terms leave 50 digits */
    private static final int R = 40;
    private static final int TERMS = 20;

    private static final BigDecimal[] BERN = bernoulliTable(TERMS);

    // ---------- exact rational Bernoulli numbers ----------

    /** B_2n / (2n(2n-1)) from sum C(m+1,j) B_j = 0, in exact fractions */
    private static BigDecimal[] bernoulliTable(int terms) {
        int m = 2 * terms;
        BigInteger[] n = new BigInteger[m + 1];
        BigInteger[] d = new BigInteger[m + 1];
        n[0] = BigInteger.ONE;
        d[0] = BigInteger.ONE;
        for (int i = 1; i <= m; ++i) {
            BigInteger sn = BigInteger.ZERO;
            BigInteger sd = BigInteger.ONE;
            for (int j = 0; j < i; ++j) {
                BigInteger t = binomial(i + 1, j).multiply(n[j]);
                sn = sn.multiply(d[j]).add(t.multiply(sd));
                sd = sd.multiply(d[j]);
                BigInteger h = sn.gcd(sd);
                if (h.signum() != 0) {
                    sn = sn.divide(h);
                    sd = sd.divide(h);
                }
            }
            BigInteger rn = sn.negate();
            BigInteger rd = sd.multiply(BigInteger.valueOf(i + 1));
            BigInteger g = rn.gcd(rd);
            if (g.signum() != 0) {
                rn = rn.divide(g);
                rd = rd.divide(g);
            }
            if (rd.signum() < 0) {
                rn = rn.negate();
                rd = rd.negate();
            }
            n[i] = rn;
            d[i] = rd;
        }
        BigDecimal[] out = new BigDecimal[terms + 1];
        out[0] = BigDecimal.ZERO;
        for (int k = 1; k <= terms; ++k) {
            long den = 2L * k * (2L * k - 1L);
            out[k] = new BigDecimal(n[2 * k]).divide(
                    new BigDecimal(d[2 * k]).multiply(BigDecimal.valueOf(den)), MC);
        }
        return out;
    }

    private static BigInteger binomial(int a, int b) {
        BigInteger r = BigInteger.ONE;
        for (int i = 0; i < b; ++i) {
            r = r.multiply(BigInteger.valueOf(a - i)).divide(BigInteger.valueOf(i + 1));
        }
        return r;
    }

    // ---------- the transcendentals the oracle needs ----------

    private static BigDecimal sqrt(BigDecimal x) {
        if (x.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal g = new BigDecimal(Math.sqrt(x.doubleValue()));
        if (g.signum() == 0) {
            g = new BigDecimal("1e-80");
        }
        for (int i = 0; i < 12; ++i) {
            g = g.add(x.divide(g, MC), MC).multiply(HALF, MC);
        }
        return g;
    }

    /** ln by 2 atanh((x-1)/(x+1)), the argument first scaled into [1, 2) */
    private static BigDecimal ln(BigDecimal x) {
        int e = 0;
        BigDecimal v = x;
        while (v.compareTo(TWO) >= 0) {
            v = v.divide(TWO, MC);
            ++e;
        }
        while (v.compareTo(BigDecimal.ONE) < 0) {
            v = v.multiply(TWO, MC);
            --e;
        }
        BigDecimal t = v.subtract(BigDecimal.ONE, MC).divide(v.add(BigDecimal.ONE, MC), MC);
        BigDecimal t2 = t.multiply(t, MC);
        BigDecimal p = t;
        BigDecimal s = t;
        for (int k = 1; k < 400; ++k) {
            p = p.multiply(t2, MC);
            BigDecimal q = p.divide(BigDecimal.valueOf(2L * k + 1L), MC);
            s = s.add(q, MC);
            if (q.abs().compareTo(EPS) < 0) {
                break;
            }
        }
        s = s.multiply(TWO, MC);
        return (e == 0) ? s : s.add(LN2.multiply(BigDecimal.valueOf(e), MC), MC);
    }

    private static final BigDecimal LN2 = new BigDecimal(
            "0.693147180559945309417232121458176568075500134360255254120680009493393621969694716");

    /** atan by halving down to a small argument and doubling back up */
    private static BigDecimal atan(BigDecimal x) {
        int n = 0;
        BigDecimal v = x;
        while (v.abs().compareTo(new BigDecimal("0.05")) > 0) {
            // atan v = 2 atan(v / (1 + sqrt(1 + v^2)))
            BigDecimal r = sqrt(BigDecimal.ONE.add(v.multiply(v, MC), MC));
            v = v.divide(BigDecimal.ONE.add(r, MC), MC);
            ++n;
        }
        BigDecimal v2 = v.multiply(v, MC).negate();
        BigDecimal p = v;
        BigDecimal s = v;
        for (int k = 1; k < 400; ++k) {
            p = p.multiply(v2, MC);
            BigDecimal q = p.divide(BigDecimal.valueOf(2L * k + 1L), MC);
            s = s.add(q, MC);
            if (q.abs().compareTo(EPS) < 0) {
                break;
            }
        }
        for (int i = 0; i < n; ++i) {
            s = s.multiply(TWO, MC);
        }
        return s;
    }

    private static BigDecimal arg(BigDecimal x, BigDecimal y) {
        if (x.signum() > 0) {
            return atan(y.divide(x, MC));
        }
        if (x.signum() < 0) {
            BigDecimal t = atan(y.divide(x, MC));
            return y.signum() >= 0 ? t.add(PI, MC) : t.subtract(PI, MC);
        }
        return y.signum() >= 0 ? PI.divide(TWO, MC) : PI.divide(TWO, MC).negate();
    }

    // ---------- the oracle ----------

    private static BigDecimal[] pair(BigDecimal a, BigDecimal b) {
        return new BigDecimal[] { a, b };
    }

    private static BigDecimal[] clog(BigDecimal[] a) {
        BigDecimal m2 = a[0].multiply(a[0], MC).add(a[1].multiply(a[1], MC), MC);
        return pair(ln(m2).divide(TWO, MC), arg(a[0], a[1]));
    }

    private static BigDecimal[] cmul(BigDecimal[] a, BigDecimal[] b) {
        return pair(a[0].multiply(b[0], MC).subtract(a[1].multiply(b[1], MC), MC),
                a[0].multiply(b[1], MC).add(a[1].multiply(b[0], MC), MC));
    }

    private static BigDecimal[] cdiv(BigDecimal[] a, BigDecimal[] b) {
        BigDecimal d = b[0].multiply(b[0], MC).add(b[1].multiply(b[1], MC), MC);
        return pair(a[0].multiply(b[0], MC).add(a[1].multiply(b[1], MC), MC).divide(d, MC),
                a[1].multiply(b[0], MC).subtract(a[0].multiply(b[1], MC), MC).divide(d, MC));
    }

    /**
     * ln(gamma(z)) at 60 digits. The route is Stirling with a much longer
     * shift and no reflection at all, so it judges the reflection in the code.
     * lgamma(w+1) = lgamma(w) + Log(w) holds with the principal log on the
     * half planes every argument here lives in.
     */
    private static BigDecimal[] oracle(double x, double y) {
        BigDecimal[] z = pair(new BigDecimal(x), new BigDecimal(y));
        BigDecimal[] w = z;
        BigDecimal[] one = pair(BigDecimal.ONE, BigDecimal.ZERO);
        int n = 0;
        while (Math.hypot(w[0].doubleValue(), w[1].doubleValue()) < R) {
            w = pair(w[0].add(BigDecimal.ONE, MC), w[1]);
            ++n;
        }
        BigDecimal[] lw = clog(w);
        BigDecimal[] s = cmul(pair(w[0].subtract(HALF, MC), w[1]), lw);
        s = pair(s[0].subtract(w[0], MC), s[1].subtract(w[1], MC));
        BigDecimal lnTwoPi = ln(PI.multiply(TWO, MC)).divide(TWO, MC);
        s = pair(s[0].add(lnTwoPi, MC), s[1]);
        BigDecimal[] w2 = cmul(w, w);
        BigDecimal[] p = cdiv(one, w);
        for (int k = 1; k <= TERMS; ++k) {
            if (k > 1) {
                p = cdiv(p, w2);
            }
            s = pair(s[0].add(p[0].multiply(BERN[k], MC), MC),
                    s[1].add(p[1].multiply(BERN[k], MC), MC));
        }
        BigDecimal[] t = z;
        for (int k = 0; k < n; ++k) {
            BigDecimal[] l = clog(t);
            s = pair(s[0].subtract(l[0], MC), s[1].subtract(l[1], MC));
            t = pair(t[0].add(BigDecimal.ONE, MC), t[1]);
        }
        return s;
    }

    /** the absolute miss of a computed pair, in units of the roundoff */
    private static double absMiss(ComplexD got, BigDecimal[] want) {
        double dr = new BigDecimal(got.re()).subtract(want[0], MC).abs().doubleValue();
        double di = new BigDecimal(got.im()).subtract(want[1], MC).abs().doubleValue();
        return Math.hypot(dr, di) / U;
    }

    /** the relative miss, scaled so that nothing overflows on the way */
    private static double relMiss(ComplexD got, BigDecimal[] want) {
        BigDecimal s = want[0].abs().max(want[1].abs());
        if (s.signum() == 0) {
            return 0.0;
        }
        double dr = new BigDecimal(got.re()).subtract(want[0], MC).divide(s, MC).doubleValue();
        double di = new BigDecimal(got.im()).subtract(want[1], MC).divide(s, MC).doubleValue();
        double m = Math.hypot(want[0].divide(s, MC).doubleValue(),
                want[1].divide(s, MC).doubleValue());
        return Math.hypot(dr, di) / m / U;
    }

    private static double spoke(int k, int of) {
        // 0.37 keeps the points off the axes and off the cut lines
        return 0.37 + k * 2.0 * Math.PI / of;
    }

    private static String at(double a, double b) {
        return " at (" + a + ", " + b + ")";
    }

    private static void same(String what, double want, double got) {
        assertEquals(what + ": want " + want + ", got " + got, Double.doubleToLongBits(want),
                Double.doubleToLongBits(got));
    }

    // ================= 1. the table and the exact values =================

    @Test
    public void testTheCoefficientsAreWhatTheRecurrenceGives() {
        // nothing in the code is quoted: every coefficient comes out of
        // sum C(m+1,j) B_j = 0, in exact fractions
        assertEquals("how many terms", 12, Gamma.terms());
        for (int n = 1; n <= Gamma.terms(); ++n) {
            same("B_" + (2 * n) + " / (" + (2 * n) + " * " + (2 * n - 1) + ")",
                    BERN[n].doubleValue(), Gamma.bernoulli(n));
        }
        // and the first few are the Bernoulli numbers everyone knows
        assertEquals("B_2 / 2", 1.0 / 12.0, Gamma.bernoulli(1), 0.0);
        assertEquals("B_4 / 12", -1.0 / 360.0, Gamma.bernoulli(2), 0.0);
        assertEquals("B_12 / 132", -691.0 / 2730.0 / 132.0, Gamma.bernoulli(6), 1.0e-18);
    }

    @Test
    public void testGammaIsTheFactorialOnThePositiveIntegers() {
        BigInteger f = BigInteger.ONE;
        double worst = 0.0;
        for (int n = 1; n <= 23; ++n) {
            double want = f.doubleValue();
            ComplexD got = Gamma.gamma(new ComplexD(n, 0.0));
            same("the imaginary part of gamma(" + n + ")", 0.0, got.im());
            double miss = Math.abs(got.re() - want) / Math.ulp(want);
            assertTrue("gamma(" + n + ") = " + got.re() + ", (n-1)! = " + want + ", " + miss
                    + " ulp", miss <= 100.0);
            worst = Math.max(worst, miss);
            f = f.multiply(BigInteger.valueOf(n));
        }
        // the exponential is what costs this: the factorials are exact doubles
        // up to 18!, and gamma still misses them by tens of ulp
        assertTrue("the factorials came out too well: " + worst, worst > 1.0);
    }

    // ================= 2. against the oracle =================

    @Test
    public void testLgammaAgainstTheOracleOnTheRight() {
        double worstRel = 0.0;
        double worstAbs = 0.0;
        int n = 0;
        for (int e = -4; e <= 12; ++e) {
            double rad = Math.pow(10.0, e * 0.5);
            for (int k = 0; k < 12; ++k) {
                double x = 0.5 + rad * Math.cos(spoke(k, 12));
                double y = rad * Math.sin(spoke(k, 12));
                if (x < 0.5) {
                    continue;
                }
                BigDecimal[] want = oracle(x, y);
                ComplexD got = Gamma.lgamma(new ComplexD(x, y));
                if (rad <= 3.2) {
                    // where the shift runs, the absolute miss of the exponent
                    // is what it costs, and gamma inherits exactly that
                    worstAbs = Math.max(worstAbs, absMiss(got, want));
                }
                if (Math.hypot(want[0].doubleValue(), want[1].doubleValue()) > 0.05) {
                    worstRel = Math.max(worstRel, relMiss(got, want));
                }
                ++n;
            }
        }
        assertTrue("too few points: " + n, n > 100);
        assertTrue("the exponent is off by " + worstAbs + " u", worstAbs <= 28.0);
        assertTrue("lgamma is off by " + worstRel + " ulp", worstRel <= 150.0);
    }

    @Test
    public void testLgammaIsSharpWhereItIsLarge() {
        // away from its two zeros there is nothing to cancel and the value is
        // good to a few ulp
        double worst = 0.0;
        for (int e = 1; e <= 12; ++e) {
            double rad = Math.pow(10.0, e * 0.5);
            for (int k = 0; k < 12; ++k) {
                double x = 0.5 + rad * Math.cos(spoke(k, 12));
                double y = rad * Math.sin(spoke(k, 12));
                if (x < 0.5) {
                    continue;
                }
                worst = Math.max(worst, relMiss(Gamma.lgamma(new ComplexD(x, y)), oracle(x, y)));
            }
        }
        assertTrue("lgamma is off by " + worst + " ulp", worst <= 10.0);
    }

    @Test
    public void testLgammaOnTheLeftAndItsTurns() {
        // the reflection is the code's route and not the oracle's, so this is
        // the turn correction under test
        double worst = 0.0;
        int n = 0;
        for (double x = -0.2; x > -20.0; x -= 0.31) {
            for (double y : new double[] { 0.05, 0.4, 2.0, 17.0, -0.4, -2.0 }) {
                BigDecimal[] want = oracle(x, y);
                ComplexD got = Gamma.lgamma(new ComplexD(x, y));
                if (Math.hypot(want[0].doubleValue(), want[1].doubleValue()) > 0.05) {
                    worst = Math.max(worst, relMiss(got, want));
                }
                ++n;
            }
        }
        assertTrue("too few points: " + n, n > 300);
        assertTrue("lgamma on the left is off by " + worst + " ulp", worst <= 30.0);
    }

    @Test
    public void testTheReducedSineLeavesNoSeam() {
        // the reduction moves the only discontinuity of the formula onto the
        // integers, where m steps and the sign of sin(pi r) turns over - and
        // there the two must cancel, so lgamma stays continuous
        for (double x : new double[] { -1.0, -2.0, -3.0, -8.0, -17.0 }) {
            for (double y : new double[] { 0.3, -0.3, 2.5 }) {
                ComplexD a = Gamma.lgamma(new ComplexD(x - 1.0e-9, y));
                ComplexD b = Gamma.lgamma(new ComplexD(x + 1.0e-9, y));
                assertEquals("across the integer, re" + at(x, y), a.re(), b.re(), 1.0e-7);
                assertEquals("across the integer, im" + at(x, y), a.im(), b.im(), 1.0e-7);
            }
        }
        // where the unreduced sine used to sit on its cut - x = 1.5 mod 2 -
        // nothing happens at all any more, and the value there was the one a
        // rounding residue used to decide
        for (double x : new double[] { -0.5, -2.5, -4.5, -8.5 }) {
            for (double y : new double[] { 0.3, -0.3 }) {
                ComplexD on = Gamma.lgamma(new ComplexD(x, y));
                ComplexD left = Gamma.lgamma(new ComplexD(x - 1.0e-9, y));
                ComplexD right = Gamma.lgamma(new ComplexD(x + 1.0e-9, y));
                assertEquals("the line and its left neighbour" + at(x, y), on.im(), left.im(),
                        1.0e-7);
                assertEquals("the line and its right neighbour" + at(x, y), on.im(), right.im(),
                        1.0e-7);
                double worst = Math.max(relMiss(on, oracle(x, y)),
                        relMiss(right, oracle(x + 1.0e-9, y)));
                assertTrue("on the old cut line" + at(x, y) + ": " + worst + " ulp", worst <= 30.0);
            }
        }
    }

    // ================= 3. identities that need no series =================

    @Test
    public void testTheFunctionalEquation() {
        ComplexD one = new ComplexD(1.0, 0.0);
        double worst = 0.0;
        for (double x = -6.3; x < 12.0; x += 0.7) {
            for (double y : new double[] { 0.3, 1.5, 6.0 }) {
                ComplexD z = new ComplexD(x, y);
                ComplexD a = Gamma.gamma(z.add(one));
                ComplexD b = z.mul(Gamma.gamma(z));
                worst = Math.max(worst, Math.hypot(a.re() - b.re(), a.im() - b.im()) / a.abs());
            }
        }
        assertTrue("gamma(z+1) against z gamma(z): " + worst, worst <= 1.0e-13);
    }

    @Test
    public void testTheDuplicationFormula() {
        // gamma(z) gamma(z+1/2) = 2^(1-2z) sqrt(pi) gamma(2z), which neither
        // the code nor the oracle uses anywhere
        ComplexD half = new ComplexD(0.5, 0.0);
        double worst = 0.0;
        for (double x = -3.0; x < 6.0; x += 0.5) {
            for (double y : new double[] { 0.25, 1.0, 3.0 }) {
                ComplexD z = new ComplexD(x, y);
                ComplexD lhs = Gamma.lgamma(z).add(Gamma.lgamma(z.add(half)));
                ComplexD two = new ComplexD(2.0, 0.0);
                ComplexD rhs = Gamma.lgamma(z.scale(2.0))
                        .add(two.ln().scale(1.0 - 2.0 * x))
                        .add(new ComplexD(0.5 * Math.log(Math.PI), -2.0 * y * Math.log(2.0)));
                double d = Math.abs(lhs.re() - rhs.re());
                // the identity holds up to a whole turn in the imaginary part
                double t = (lhs.im() - rhs.im()) / (2.0 * Math.PI);
                assertEquals("the turn is whole" + at(x, y), Math.rint(t), t, 1.0e-9);
                worst = Math.max(worst, d / Math.max(1.0, Math.abs(rhs.re())));
            }
        }
        assertTrue("the duplication formula is off by " + worst, worst <= 1.0e-13);
    }

    @Test
    public void testGammaIsTheExponentialOfLgamma() {
        for (double x = -5.3; x < 9.0; x += 0.61) {
            for (double y : new double[] { 0.0, 0.4, 3.0, -2.0 }) {
                ComplexD z = new ComplexD(x, y);
                ComplexD a = Gamma.gamma(z);
                ComplexD b = Gamma.lgamma(z).exp();
                same("gamma against exp(lgamma) re" + at(x, y), a.re(), b.re());
                same("gamma against exp(lgamma) im" + at(x, y), a.im(), b.im());
            }
        }
    }

    @Test
    public void testTheMirror() {
        // gamma(conj z) = conj(gamma z), bit for bit, because the route is
        // odd in y throughout
        for (double x = -4.3; x < 8.0; x += 0.53) {
            for (double y : new double[] { 0.2, 1.3, 5.0 }) {
                ComplexD z = new ComplexD(x, y);
                ComplexD a = Gamma.gamma(z.conj());
                ComplexD b = Gamma.gamma(z).conj();
                same("the mirror re" + at(x, y), a.re(), b.re());
                same("the mirror im" + at(x, y), a.im(), b.im());
            }
        }
    }

    // ================= 4. the axis, the poles and the edges =================

    @Test
    public void testTheRealAxisStaysReal() {
        for (double x : new double[] { 0.5, 1.0, 1.5, 2.0, 3.0, 7.5, 40.0, 1.0e10 }) {
            same("lgamma" + at(x, 0.0), 0.0, Gamma.lgamma(new ComplexD(x, 0.0)).im());
            // the sign of the zero rides through, as it does everywhere else
            same("lgamma" + at(x, -0.0), -0.0, Gamma.lgamma(new ComplexD(x, -0.0)).im());
            same("gamma" + at(x, 0.0), 0.0, Gamma.gamma(new ComplexD(x, 0.0)).im());
        }
        // below one half the value is still real, and the zero picks the side
        for (double x : new double[] { -0.5, -1.5, -2.5, -3.5 }) {
            ComplexD up = Gamma.lgamma(new ComplexD(x, 0.0));
            ComplexD dn = Gamma.lgamma(new ComplexD(x, -0.0));
            assertEquals("the two sides of" + at(x, 0.0), up.re(), dn.re(), 0.0);
            assertEquals("the angle flips with the zero" + at(x, 0.0), up.im(), -dn.im(), 0.0);
            // and it is the limit from the side the zero names
            BigDecimal[] want = oracle(x, 1.0e-30);
            assertEquals("the limit from above" + at(x, 0.0), want[1].doubleValue(), up.im(),
                    1.0e-12);
            assertTrue("gamma is real there" + at(x, 0.0),
                    Math.abs(Gamma.gamma(new ComplexD(x, 0.0)).im()) < 1.0e-14);
        }
    }

    @Test
    public void testThePolesAndTheDegenerateRows() {
        for (double x : new double[] { 0.0, -0.0, -1.0, -2.0, -17.0 }) {
            for (double y : new double[] { 0.0, -0.0 }) {
                ComplexD z = new ComplexD(x, y);
                same("gamma at the pole" + at(x, y), INF, Gamma.gamma(z).re());
                same("gamma at the pole" + at(x, y), INF, Gamma.gamma(z).im());
                same("lgamma at the pole" + at(x, y), INF, Gamma.lgamma(z).re());
                assertTrue("the angle has no value" + at(x, y),
                        Double.isNaN(Gamma.lgamma(z).im()));
            }
        }
        // a pole is a pole only on the axis
        assertTrue("not a pole off the axis", !Double.isInfinite(
                Gamma.gamma(new ComplexD(-2.0, 1.0e-30)).re()));
        // gamma grows without bound along the positive axis and settles on no
        // direction anywhere else out there
        same("lgamma(inf, 0)", INF, Gamma.lgamma(new ComplexD(INF, 0.0)).re());
        same("lgamma(inf, 0)", 0.0, Gamma.lgamma(new ComplexD(INF, 0.0)).im());
        same("gamma(inf, 0)", INF, Gamma.gamma(new ComplexD(INF, 0.0)).re());
        for (double[] v : new double[][] { { 0.0, INF }, { INF, INF }, { NAN, 0.0 }, { 0.0, NAN },
                { NAN, NAN }, { INF, NAN }, { NAN, INF }, { 1.0, INF } }) {
            ComplexD z = new ComplexD(v[0], v[1]);
            assertTrue("lgamma" + at(v[0], v[1]), Gamma.lgamma(z).isNan());
            assertTrue("gamma" + at(v[0], v[1]), Gamma.gamma(z).isNan());
        }
    }

    @Test
    public void testTheEdgesOfTheRange() {
        // gamma leaves the double range on the real axis where lgamma passes
        // ln(MAX_VALUE), and lgamma lives on - which is why both exist
        assertTrue("gamma(171.6)", Gamma.gamma(new ComplexD(171.6, 0.0)).re() < INF);
        same("gamma(171.7)", INF, Gamma.gamma(new ComplexD(171.7, 0.0)).re());
        assertEquals("lgamma(171.7)", 710.1716, Gamma.lgamma(new ComplexD(171.7, 0.0)).re(),
                1.0e-3);
        // and it underflows up the imaginary axis, where lgamma also lives on
        assertTrue("gamma(0.5+450i)", Gamma.gamma(new ComplexD(0.5, 450.0)).abs() > 0.0);
        same("gamma(0.5+500i)", 0.0, Gamma.gamma(new ComplexD(0.5, 500.0)).abs());
        assertEquals("lgamma(0.5+500i)", -784.479, Gamma.lgamma(new ComplexD(0.5, 500.0)).re(),
                1.0e-2);
    }

    @Test
    public void testGammaLosesWhatTheExponentialAmplifies() {
        // gamma is exp of the logarithm, so the ABSOLUTE error of the exponent
        // is the RELATIVE error of gamma. That one law governs the accuracy,
        // and it is the exponent that is worth measuring
        double worst = 0.0;
        for (double[] p : new double[][] { { 1.5, 0.0 }, { 5.0, 0.0 }, { 20.0, 0.0 },
                { 80.0, 0.0 }, { 150.0, 0.0 }, { 2.0, 3.0 }, { 10.0, 30.0 }, { 0.5, 60.0 },
                { 40.0, 40.0 }, { -3.7, 0.2 }, { 0.1, 0.0 } }) {
            BigDecimal[] lt = oracle(p[0], p[1]);
            double mag = Math.hypot(lt[0].doubleValue(), lt[1].doubleValue());
            double absU = absMiss(Gamma.lgamma(new ComplexD(p[0], p[1])), lt);
            assertTrue("the exponent" + at(p[0], p[1]) + " is off by " + absU + " u, and |lgamma|"
                    + " is " + mag, absU <= 5.0 * Math.max(mag, 20.0));
            worst = Math.max(worst, absU / Math.max(mag, 20.0));
        }
        // and the law is not slack: somewhere the exponent really does lose
        // that much, so a caller cannot expect a fixed number of ulp
        assertTrue("the exponent came out too well: " + worst, worst > 0.1);
    }
}
