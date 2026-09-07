package math.complex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class LambertWTest {

    private static final double INV_E = 0.36787944117144232159552377016146;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NAN = Double.NaN;

    private static void same(String what, double want, double got) {
        assertEquals(what + ": want " + want + ", got " + got, Double.doubleToLongBits(want),
                Double.doubleToLongBits(got));
    }

    private static void same(String what, double wantRe, double wantIm, ComplexD got) {
        same(what + " re", wantRe, got.re());
        same(what + " im", wantIm, got.im());
    }

    private static void near(String what, double wantRe, double wantIm, ComplexD got) {
        double t = 4.0 * Math.ulp(Math.max(Math.abs(wantRe), Math.abs(wantIm)));
        assertEquals(what + " re", wantRe, got.re(), t);
        assertEquals(what + " im", wantIm, got.im(), t);
    }

    /** |W exp(W) - z| relative to |z|, which is what the function actually promises */
    private static double residual(int k, ComplexD z) {
        ComplexD w = LambertW.w(k, z);
        return w.mul(w.exp()).sub(z).abs() / z.abs();
    }

    private static void solves(String what, int k, ComplexD z) {
        double r = residual(k, z);
        // exp turns a relative error in W into |W| times as much, so that is
        // the scale the residual has to be read against
        double tol = 16.0 * Math.max(1.0, LambertW.w(k, z).abs()) * 0x1p-52;
        assertTrue(what + ": W exp(W) is off z by " + r + ", allowed " + tol, r < tol);
    }

    // ================= what it has to get right =================

    @Test
    public void testTheDefiningIdentity() {
        double[][] zs = { { 1.0, 0.0 }, { Math.E, 0.0 }, { -0.3, 0.0 }, { -1.0, 0.0 },
                { -5.0, 0.0 }, { 100.0, 0.0 }, { 1.0e300, 0.0 }, { 1.0e-300, 0.0 },
                { 1.0, 1.0 }, { -2.0, 7.0 }, { 0.5, -0.5 }, { -0.36, 0.05 },
                { 1.0e-8, 1.0e-8 }, { -1.0e100, 1.0e100 }, { 3.0, -1.0e-14 } };
        for (double[] p : zs) {
            ComplexD z = new ComplexD(p[0], p[1]);
            solves("W_0 at " + z, 0, z);
            solves("W_-1 at " + z, -1, z);
        }
    }

    @Test
    public void testTheKnownValues() {
        near("W(1) is Omega", 0.5671432904097838, 0.0, LambertW.w(new ComplexD(1.0, 0.0)));
        near("W(e) = 1", 1.0, 0.0, LambertW.w(new ComplexD(Math.E, 0.0)));
        near("W(-ln2/2) = -ln2", -0.6931471805599453, 0.0,
                LambertW.w(new ComplexD(-0.34657359027997264, 0.0)));
        near("W(1e-100) = 1e-100", 1.0e-100, 0.0, LambertW.w(new ComplexD(1.0e-100, 0.0)));
        near("W(100)", 3.38563014029005, 0.0, LambertW.w(new ComplexD(100.0, 0.0)));
        near("W(1e300)", 684.2472086297608, 0.0, LambertW.w(new ComplexD(1.0e300, 0.0)));
        near("W(1+i)", 0.6569660692304364, 0.32545033941341506,
                LambertW.w(new ComplexD(1.0, 1.0)));
        near("W(-1) on the cut from above", -0.31813150520476413, 1.3372357014306895,
                LambertW.w(new ComplexD(-1.0, 0.0)));
        near("W_-1(-0.1)", -3.577152063957297, 0.0, LambertW.w(-1, new ComplexD(-0.1, 0.0)));
        near("W_-1(-1e-100)", -235.72115887568532, 0.0,
                LambertW.w(-1, new ComplexD(-1.0e-100, 0.0)));
        near("W_-1(1)", -1.5339133197935746, -4.375185153061898,
                LambertW.w(-1, new ComplexD(1.0, 0.0)));
        near("W_-1(e)", -0.53209212198638, -4.597158013302574,
                LambertW.w(-1, new ComplexD(Math.E, 0.0)));
    }

    @Test
    public void testBothBranchesAreRealOnTheSegment() {
        // on (-1/e, 0) the equation has two real solutions, one on each branch,
        // and W_0 is the one above -1
        for (int i = 1; i <= 200; ++i) {
            double x = -INV_E * i / 201.0;
            ComplexD z = new ComplexD(x, 0.0);
            ComplexD a = LambertW.w(0, z);
            ComplexD b = LambertW.w(-1, z);
            same("W_0 is real at " + x, 0.0, a.im());
            same("W_-1 is real at " + x, 0.0, b.im());
            assertTrue("W_0 must sit above -1 at " + x, a.re() >= -1.0);
            assertTrue("W_-1 must sit below -1 at " + x, b.re() <= -1.0);
            assertTrue("and the two must differ at " + x, a.re() > b.re());
        }
    }

    @Test
    public void testTheCutsFollowTheSignOfTheZero() {
        // the principal branch is cut along (-inf, -1/e], and +0 is the limit
        // from above, which is what the whole package does
        for (double x : new double[] { -0.5, -1.0, -5.0, -1.0e10 }) {
            ComplexD up = LambertW.w(0, new ComplexD(x, 0.0));
            ComplexD dn = LambertW.w(0, new ComplexD(x, -0.0));
            ComplexD ue = LambertW.w(0, new ComplexD(x, 1.0e-15 * Math.abs(x)));
            // the nudge is a relative 1e-15, so the values may differ by that
            assertTrue("+0 is not the limit from above at " + x,
                    ue.sub(up).abs() < 1.0e-12 * up.abs());
            assertTrue("and the upper side is the one with a positive part at " + x,
                    up.im() > 0.0);
            assertEquals("and -0 is its mirror re at " + x, up.re(), dn.re(), 0.0);
            assertEquals("and -0 is its mirror im at " + x, -up.im(), dn.im(), 0.0);
            assertTrue("the two sides must differ at " + x, up.im() != dn.im());
        }
        // the other branch is cut along the whole negative axis, and on
        // (-1/e, 0) it is the upper side that carries the real value
        for (double x : new double[] { -0.2, -0.1, -0.01 }) {
            ComplexD up = LambertW.w(-1, new ComplexD(x, 0.0));
            ComplexD dn = LambertW.w(-1, new ComplexD(x, -0.0));
            same("W_-1 is real above the cut at " + x, 0.0, up.im());
            assertTrue("and complex below it at " + x, dn.im() < -1.0);
        }
        // off the cut the sign of a zero changes nothing
        for (double x : new double[] { 0.5, 2.0, 1.0e6 }) {
            ComplexD up = LambertW.w(-1, new ComplexD(x, 0.0));
            ComplexD dn = LambertW.w(-1, new ComplexD(x, -0.0));
            same("no cut at " + x, up.re(), up.im(), dn);
        }
    }

    @Test
    public void testThePrincipalBranchStaysInItsStrip() {
        // the range of W_0 lies between the curves for -pi < eta < pi, so its
        // imaginary part can never leave that strip
        for (int i = -40; i <= 40; ++i) {
            for (int j = -40; j <= 40; ++j) {
                if (i == 0 && j == 0) {
                    continue;
                }
                double x = Math.signum(i) * Math.pow(10.0, Math.abs(i) / 6.0 - 5.0);
                double y = Math.signum(j) * Math.pow(10.0, Math.abs(j) / 6.0 - 5.0);
                ComplexD w = LambertW.w(0, new ComplexD(x, y));
                assertTrue("Im W_0 left (-pi, pi] at (" + x + ", " + y + "): " + w.im(),
                        w.im() > -Math.PI && w.im() <= Math.PI);
            }
        }
    }

    @Test
    public void testTheBranchesCarryTheLabelsTheyClaim() {
        // far out W_k(z) is ln z + 2 pi i k - ln(ln z + 2 pi i k), and that is
        // what tells the branches apart in the first place
        for (int k : new int[] { 0, -1 }) {
            for (double r : new double[] { 1.0e3, 1.0e20, 1.0e200 }) {
                for (int i = 0; i < 12; ++i) {
                    double th = -Math.PI + 2.0 * Math.PI * (i + 0.5) / 12.0;
                    ComplexD z = ComplexD.fromPolar(r, th);
                    ComplexD l1 = z.ln().add(new ComplexD(0.0, 2.0 * Math.PI * k));
                    ComplexD want = l1.sub(l1.ln());
                    ComplexD got = LambertW.w(k, z);
                    assertTrue("branch " + k + " is not the asymptotic one at " + z
                            + ": got " + got + ", the leading terms give " + want,
                            got.sub(want).abs() < 0.6 * Math.max(1.0, want.abs()));
                }
            }
        }
    }

    @Test
    public void testTheBranchPointIsWhereTheTwoBranchesMeet() {
        ComplexD bp = new ComplexD(-INV_E, 0.0);
        near("W_0 at the branch point", -1.0, 0.0, LambertW.w(0, bp));
        near("W_-1 at the branch point", -1.0, 0.0, LambertW.w(-1, bp));
        // beside it the two part like the square root, and the residual stays
        // small even where the value itself can no longer be resolved
        for (double d : new double[] { 1.0e-2, 1.0e-4, 1.0e-6 }) {
            ComplexD z = new ComplexD(-INV_E + d, 0.0);
            ComplexD a = LambertW.w(0, z);
            ComplexD b = LambertW.w(-1, z);
            double want = 2.0 * Math.sqrt(2.0 * Math.E * d);
            assertEquals("the branches part like 2 sqrt(2 e d) at d = " + d, want,
                    a.sub(b).abs(), 0.2 * want);
            solves("W_0 beside the branch point", 0, z);
            solves("W_-1 beside the branch point", -1, z);
        }
    }

    @Test
    public void testTheConjugateSymmetryOfThePrincipalBranch() {
        double[][] zs = { { 1.0, 1.0 }, { -2.0, 7.0 }, { 0.5, -0.5 }, { -0.36, 0.05 },
                { 1.0e100, 1.0 }, { 1.0e-100, 1.0e-100 } };
        for (double[] p : zs) {
            ComplexD z = new ComplexD(p[0], p[1]);
            ComplexD a = LambertW.w(0, z).conj();
            ComplexD b = LambertW.w(0, z.conj());
            near("conj W_0 at " + z, a.re(), a.im(), b);
        }
    }

    @Test
    public void testItIsContinuousOffItsCut() {
        // a circle that stops short of the negative axis must show no jump
        for (int k : new int[] { 0, -1 }) {
            for (double r : new double[] { 0.1, 1.0, 30.0 }) {
                ComplexD prev = null;
                double worst = 0.0;
                int n = 4000;
                for (int i = 0; i <= n; ++i) {
                    double th = -Math.PI + 0.02 + (2.0 * Math.PI - 0.04) * i / n;
                    ComplexD w = LambertW.w(k, ComplexD.fromPolar(r, th));
                    if (prev != null) {
                        worst = Math.max(worst, w.sub(prev).abs());
                    }
                    prev = w;
                }
                assertTrue("branch " + k + " jumps by " + worst + " on the circle of radius " + r,
                        worst < 0.01);
            }
        }
    }

    @Test
    public void testTheSpecialValues() {
        // W ~ z at the origin, so the principal branch keeps the zero it is
        // given, and the other one runs off without a direction
        for (double[] p : new double[][] { { 0.0, 0.0 }, { -0.0, 0.0 }, { 0.0, -0.0 },
                { -0.0, -0.0 } }) {
            ComplexD z = new ComplexD(p[0], p[1]);
            same("W_0 at a zero", p[0], p[1], LambertW.w(0, z));
            same("W_-1 at a zero", INF, INF, LambertW.w(-1, z));
        }
        // an infinite modulus, and no direction that survives
        for (double[] p : new double[][] { { INF, 0.0 }, { -INF, 0.0 }, { 0.0, INF },
                { INF, INF }, { NAN, INF } }) {
            ComplexD z = new ComplexD(p[0], p[1]);
            same("W_0 at infinity", INF, INF, LambertW.w(0, z));
            same("W_-1 at infinity", INF, INF, LambertW.w(-1, z));
        }
        same("W_0(NaN)", NAN, NAN, LambertW.w(new ComplexD(NAN, 1.0)));
        same("W_0(NaN)", NAN, NAN, LambertW.w(new ComplexD(1.0, NAN)));
        same("W_-1(NaN)", NAN, NAN, LambertW.w(-1, new ComplexD(NAN, NAN)));
        // and there are only two branches here
        for (int k : new int[] { 1, -2, 5 }) {
            try {
                LambertW.w(k, new ComplexD(1.0, 0.0));
                fail("branch " + k + " should not answer");
            } catch (IllegalArgumentException expected) {
                // that is the point
            }
        }
    }
}
