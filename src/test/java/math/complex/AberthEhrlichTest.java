package math.complex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;

import org.junit.Test;

public class AberthEhrlichTest {

    private static final MathContext MC = new MathContext(80);

    // ================= the residual, where double Horner is only noise =================

    /**
     * |p(z)| / sum |a_k| |z|^k at 80 digits, in the max norm, so within a
     * factor of two of the relative backward error. In double the numerator is
     * pure cancellation and says nothing.
     */
    private static double backward(ComplexD[] a, ComplexD z) {
        BigDecimal zr = new BigDecimal(z.re());
        BigDecimal zi = new BigDecimal(z.im());
        BigDecimal pr = new BigDecimal(a[a.length - 1].re());
        BigDecimal pi = new BigDecimal(a[a.length - 1].im());
        for (int k = a.length - 2; k >= 0; --k) {
            BigDecimal nr = pr.multiply(zr, MC).subtract(pi.multiply(zi, MC), MC)
                    .add(new BigDecimal(a[k].re()), MC);
            BigDecimal ni = pr.multiply(zi, MC).add(pi.multiply(zr, MC), MC)
                    .add(new BigDecimal(a[k].im()), MC);
            pr = nr;
            pi = ni;
        }
        BigDecimal az = zr.abs().max(zi.abs());
        BigDecimal scale = BigDecimal.ZERO;
        BigDecimal pw = BigDecimal.ONE;
        for (int k = 0; k < a.length; ++k) {
            BigDecimal m = new BigDecimal(a[k].re()).abs().max(new BigDecimal(a[k].im()).abs());
            scale = scale.add(m.multiply(pw, MC), MC);
            pw = pw.multiply(az, MC);
        }
        if (scale.signum() == 0) {
            return 0.0;
        }
        return pr.abs().max(pi.abs()).divide(scale, MC).doubleValue();
    }

    /** every root has to leave a residual at the level of the roundoff */
    private static void backwardStable(String what, ComplexD[] a, double tol) {
        ComplexD[] z = AberthEhrlich.roots(a);
        for (ComplexD w : z) {
            assertTrue(what + ": " + w + " is not a root, residual " + backward(a, w),
                    backward(a, w) <= tol);
        }
    }

    /** the monic polynomial with the given roots, expanded exactly enough */
    private static ComplexD[] fromRoots(double... r) {
        ComplexD[] a = new ComplexD[r.length + 1];
        Arrays.fill(a, ComplexD.Zero());
        a[0] = ComplexD.One();
        for (int i = 0; i < r.length; ++i) {
            for (int k = i + 1; k >= 1; --k) {
                a[k] = a[k - 1].sub(a[k].scale(r[i]));
            }
            a[0] = a[0].scale(-r[i]);
        }
        return a;
    }

    /** the computed root closest to x */
    private static ComplexD nearest(ComplexD[] z, double x, double y) {
        ComplexD want = new ComplexD(x, y);
        ComplexD best = z[0];
        double bd = Double.MAX_VALUE;
        for (ComplexD w : z) {
            double d = w.sub(want).abs();
            if (d < bd) {
                bd = d;
                best = w;
            }
        }
        return best;
    }

    // ================= what it has to get right =================

    @Test
    public void testTheRootsOfPolynomialsWithKnownRoots() {
        ComplexD[] a = fromRoots(1, 2, 3, 4, 5, 6, 7, 8);
        ComplexD[] z = AberthEhrlich.roots(a);
        assertEquals("one root per degree", 8, z.length);
        for (int i = 1; i <= 8; ++i) {
            ComplexD w = nearest(z, i, 0.0);
            assertEquals("root " + i + " re", i, w.re(), 1.0e-10);
            assertEquals("root " + i + " im", 0.0, w.im(), 1.0e-10);
        }
        backwardStable("1..8", a, 1.0e-14);
        // z^n - 1 is well conditioned, so here the roots themselves have to be
        // right, not only the residual
        int n = 24;
        ComplexD[] u = new ComplexD[n + 1];
        Arrays.fill(u, ComplexD.Zero());
        u[0] = ComplexD.One().neg();
        u[n] = ComplexD.One();
        ComplexD[] w = AberthEhrlich.roots(u);
        assertEquals("one root per degree", n, w.length);
        for (int k = 0; k < n; ++k) {
            ComplexD want = ComplexD.fromPolar(1.0, 2.0 * Math.PI * k / n);
            double best = Double.MAX_VALUE;
            for (ComplexD v : w) {
                best = Math.min(best, v.sub(want).abs());
            }
            assertTrue("the root at angle " + k + "/24 is off by " + best, best < 4.0e-15);
        }
        backwardStable("z^24 - 1", u, 1.0e-14);
    }

    @Test
    public void testTheStartFindsRootsWhoseModuliAreFarApart() {
        // one circle for all of them cannot straddle 300 orders of magnitude;
        // the upper convex hull of the coefficient logarithms can
        ComplexD[] a = fromRoots(1e-150, 1e-100, 1e-50, 1, 1e50, 1e100, 1e150);
        ComplexD[] z = AberthEhrlich.roots(a);
        assertEquals("one root per degree", 7, z.length);
        for (double r : new double[] { 1e-150, 1e-100, 1e-50, 1, 1e50, 1e100, 1e150 }) {
            ComplexD w = nearest(z, r, 0.0);
            assertEquals("the root near " + r, 1.0, w.re() / r, 1.0e-12);
        }
        backwardStable("moduli 1e-150 to 1e150", a, 1.0e-14);
    }

    @Test
    public void testEvaluationSurvivesOutsideTheUnitCircle() {
        // |a_n z^n| overflows long before such a root is wrong, so above the
        // unit circle the reversed polynomial at 1/z has to carry it
        ComplexD[] a = fromRoots(1e60, 2e60, 3e60, 4e60, 5e60);
        ComplexD[] z = AberthEhrlich.roots(a);
        assertEquals("one root per degree", 5, z.length);
        for (int i = 1; i <= 5; ++i) {
            ComplexD w = nearest(z, i * 1e60, 0.0);
            assertEquals("the root near " + i + "e60", 1.0, w.re() / (i * 1e60), 1.0e-10);
        }
        backwardStable("five roots at 1e60", a, 1.0e-14);
    }

    @Test
    public void testTheCorrectionKeepsTheRootsApart() {
        // without the sum over the other roots this is plain Newton, and
        // several of the approximations run into the same root
        ComplexD[] a = fromRoots(1e-150, 1e-100, 1e-50, 1, 1e50, 1e100, 1e150);
        ComplexD[] z = AberthEhrlich.roots(a);
        for (int i = 0; i < z.length; ++i) {
            for (int j = i + 1; j < z.length; ++j) {
                assertTrue("two approximations collapsed onto " + z[i],
                        z[i].sub(z[j]).abs() > 0.0);
            }
        }
        backwardStable("distinct", a, 1.0e-14);
    }

    @Test
    public void testTheStoppingRuleFollowsTheScaleOfThePolynomial() {
        // an absolute residual threshold is meaningless: these coefficients run
        // from 1 to 1e300, and a threshold that does not scale with them either
        // stops at once or never
        ComplexD[] a = fromRoots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19, 20);
        backwardStable("Wilkinson 20", a, 1.0e-14);
        ComplexD[] b = fromRoots(1e-150, 1e-100, 1e-50, 1, 1e50, 1e100, 1e150);
        backwardStable("moduli far apart", b, 1.0e-14);
    }

    @Test
    public void testWilkinsonIsBackwardStableAndNothingMore() {
        // the roots of this one are hopelessly ill conditioned, and the honest
        // claim is the residual, not the roots. The forward error is allowed to
        // be enormous, and is
        ComplexD[] a = fromRoots(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
                19, 20);
        backwardStable("Wilkinson 20", a, 1.0e-14);
        ComplexD[] z = AberthEhrlich.roots(a);
        assertEquals("one root per degree", 20, z.length);
        double worst = 0.0;
        for (int i = 1; i <= 20; ++i) {
            worst = Math.max(worst, Math.abs(nearest(z, i, 0.0).re() - i) / i);
        }
        assertTrue("even for Wilkinson this is too far off: " + worst, worst < 1.0e-1);
    }

    @Test
    public void testAMultipleRootLosesTheDigitsItHasTo() {
        // a root of multiplicity m can only be found to u^(1/m); anything
        // better would mean the residual test is lying
        double[][] cases = { { 2, 1.0e-9, 1.0e-6 }, { 3, 1.0e-7, 1.0e-3 }, { 4, 1.0e-6, 1.0e-2 } };
        for (double[] cs : cases) {
            int m = (int) cs[0];
            double[] r = new double[m + 2];
            for (int i = 0; i < m; ++i) {
                r[i] = 1.0;
            }
            r[m] = 2.0;
            r[m + 1] = 3.0;
            ComplexD[] a = fromRoots(r);
            ComplexD[] z = AberthEhrlich.roots(a);
            double off = nearest(z, 1.0, 0.0).sub(ComplexD.One()).abs();
            assertTrue("multiplicity " + m + " came out too sharp: " + off, off > cs[1]);
            assertTrue("multiplicity " + m + " came out too blunt: " + off, off < cs[2]);
            backwardStable("multiplicity " + m, a, 1.0e-14);
        }
    }

    @Test
    public void testTheDegenerateCoefficients() {
        // a constant has no roots, and the zero polynomial has every root
        assertEquals("a constant", 0, AberthEhrlich.roots(new double[] { 3.0 }).length);
        try {
            AberthEhrlich.roots(new double[] { 0.0, 0.0 });
            fail("the zero polynomial should not answer");
        } catch (IllegalArgumentException expected) {
            // that is the point
        }
        // one division is exact for the linear one
        ComplexD[] lin = AberthEhrlich.roots(new double[] { -6.0, 3.0 });
        assertEquals("linear", 1, lin.length);
        assertEquals("2 exactly", 2.0, lin[0].re(), 0.0);
        assertEquals("real", 0.0, lin[0].im(), 0.0);
        // trailing zeros are roots at the origin, and leading ones are not roots
        ComplexD[] tz = AberthEhrlich.roots(new double[] { 0.0, 0.0, 0.0, -6.0, 3.0, 0.0, 0.0 });
        assertEquals("three at the origin and one more", 4, tz.length);
        int zeros = 0;
        for (ComplexD w : tz) {
            if (w.re() == 0.0 && w.im() == 0.0) {
                ++zeros;
            }
        }
        assertEquals("roots at the origin", 3, zeros);
        assertEquals("and the linear one", 2.0, nearest(tz, 2.0, 0.0).re(), 1.0e-14);
        // the quadratic, against the stable formula
        ComplexD[] q = AberthEhrlich.roots(new double[] { 1.0e-8, -1.0, 1.0 });
        double big = 0.5 * (1.0 + Math.sqrt(1.0 - 4.0e-8));
        assertEquals("the large root", big, nearest(q, big, 0.0).re(), 1.0e-15);
        assertEquals("the small one, which subtraction would ruin", 1.0e-8 / big,
                nearest(q, 1.0e-8, 0.0).re(), 1.0e-22);
    }

    @Test
    public void testComplexCoefficients() {
        // i, -i and 2i, so nothing is real anywhere
        // (z - i)(z + i)(z + 2i) = z^3 + 2i z^2 + z + 2i
        ComplexD[] a = new ComplexD[] { new ComplexD(0.0, 2.0), ComplexD.One(),
                new ComplexD(0.0, 2.0), ComplexD.One() };
        ComplexD[] z = AberthEhrlich.roots(a);
        assertEquals("one root per degree", 3, z.length);
        assertEquals("i", 1.0, nearest(z, 0.0, 1.0).im(), 1.0e-14);
        assertEquals("-i", -1.0, nearest(z, 0.0, -1.0).im(), 1.0e-14);
        assertEquals("-2i", -2.0, nearest(z, 0.0, -2.0).im(), 1.0e-14);
        backwardStable("purely imaginary roots", a, 1.0e-14);
    }
}
