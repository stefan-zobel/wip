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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link ComplexD} that carry their own expectations: exact
 * arithmetic as the oracle, the infinity convention as literals, and a digest
 * over the full matrix of special values. Needs nothing but JUnit.
 */
public final class ComplexDTest {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NAN = Double.NaN;

    /** the degenerate values, and a few ordinary ones */
    private static final double[][] SPECIAL = { { 0.0, 0.0 }, { -0.0, 0.0 }, { 0.0, -0.0 }, { -0.0, -0.0 },
            { 1.0, 0.0 }, { -1.0, 0.0 }, { 0.0, 1.0 }, { 0.0, -1.0 }, { 3.0, 4.0 }, { -3.0, -4.0 }, { 0.5, 0.25 },
            { 1.0e300, 1.0e300 }, { 1.0e-300, 1.0e-300 }, { INF, 0.0 }, { -INF, 0.0 }, { 0.0, INF }, { INF, INF },
            { -INF, INF }, { INF, -INF }, { -INF, -INF }, { NAN, 0.0 }, { 0.0, NAN }, { NAN, NAN }, { 1.0, NAN },
            { INF, NAN }, { NAN, INF } };

    /** the real scalars used for scale and pow */
    private static final double[] SCALARS = { 0.0, -0.0, 1.0, -1.0, 2.0, 0.5, -2.5, INF, -INF, NAN };

    /** the first exponent, the last one and the step of the test ensemble */
    private static final int MIN_EXP = -300;
    private static final int MAX_EXP = 300;
    private static final int ANGLES = 8;

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MAX = new BigDecimal(Double.MAX_VALUE);
    /** below this modulus a relative statement is meaningless */
    private static final BigDecimal FLOOR = new BigDecimal("1e-580");

    /** how many ensemble points an exact comparison actually looked at */
    private int compared;

    /** toString goes through String.format, which follows the default locale */
    private static Locale saved;

    @BeforeClass
    public static void fixTheLocale() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterClass
    public static void restoreTheLocale() {
        Locale.setDefault(saved);
    }

    private static double angle(int k) {
        // 0.3 keeps the components off the axes
        return k * Math.PI / 4.0 + 0.3;
    }

    private static ComplexD z(int i) {
        return new ComplexD(SPECIAL[i][0], SPECIAL[i][1]);
    }

    private static String at(double a, double b) {
        return " at (" + a + ", " + b + ")";
    }

    private static BigDecimal big(double x) {
        return new BigDecimal(x);
    }

    // ---------- bit exact comparison ----------

    private static void same(String what, double want, double got) {
        assertEquals(what + ": want " + want + ", got " + got, Double.doubleToLongBits(want),
                Double.doubleToLongBits(got));
    }

    private static void same(String what, double wantRe, double wantIm, ComplexD got) {
        same(what + " re", wantRe, got.re());
        same(what + " im", wantIm, got.im());
    }

    private static void near(String what, double wantRe, double wantIm, ComplexD got) {
        assertEquals(what + " re", wantRe, got.re(), 4.0 * Math.ulp(wantRe));
        assertEquals(what + " im", wantIm, got.im(), 4.0 * Math.ulp(wantIm));
    }

    // ---------- exact arithmetic as the oracle ----------

    private void component(String what, BigDecimal want, BigDecimal mod2, double got, double tol) {
        if (want.abs().compareTo(MAX) > 0) {
            // out of range; mul can leave NaN behind, see the test below
            assertTrue(what + " must leave the finite range, got " + got, !isFinite(got));
            return;
        }
        if (!isFinite(got)) {
            fail(what + " is " + got + " but the exact value is " + want.round(MC));
        }
        BigDecimal err = big(got).subtract(want, MC);
        BigDecimal bound = mod2.multiply(big(tol).multiply(big(tol), MC), MC);
        assertTrue(what + ": want " + want.round(MC) + ", got " + got,
                err.multiply(err, MC).compareTo(bound) <= 0);
    }

    /** the computed value against the exact one, relative to the exact modulus */
    private void exact(String what, BigDecimal wantRe, BigDecimal wantIm, ComplexD got, double tol) {
        BigDecimal mod2 = wantRe.multiply(wantRe, MC).add(wantIm.multiply(wantIm, MC), MC);
        if (mod2.signum() == 0 || mod2.compareTo(FLOOR) < 0) {
            // gradual underflow, nothing relative to say
            return;
        }
        component(what + " re", wantRe, mod2, got.re(), tol);
        component(what + " im", wantIm, mod2, got.im(), tol);
        ++compared;
    }

    private static boolean isFinite(double x) {
        return !Double.isNaN(x) && !Double.isInfinite(x);
    }

    /** the computed value against a complex one, relative to its modulus */
    private static void close(String what, ComplexD want, ComplexD got, double tol) {
        double scale = want.abs();
        double err = Math.hypot(got.re() - want.re(), got.im() - want.im());
        assertTrue(what + ": want " + want + ", got " + got + ", relative error " + (err / scale),
                err <= tol * scale);
    }

    // ================= 1. exact arithmetic as the oracle =================

    @Test
    public void testAbsAgreesWithHypot() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                double want = Math.hypot(a, b);
                double got = new ComplexD(a, b).abs();
                assertTrue("abs" + at(a, b) + ": want " + want + ", got " + got,
                        Math.abs(got - want) <= 2.0 * Math.ulp(want));
            }
        }
        for (double[] v : SPECIAL) {
            // by value, since abs keeps the sign of a negative zero
            double want = Math.hypot(v[0], v[1]);
            assertEquals("abs" + at(v[0], v[1]), want, new ComplexD(v[0], v[1]).abs(), 2.0 * Math.ulp(want));
        }
    }

    @Test
    public void testAbsIsNeverNegative() {
        // a modulus carries no sign, not even on a zero
        same("abs(-0.0, 0.0)", 0.0, new ComplexD(-0.0, 0.0).abs());
        same("abs(-0.0, -0.0)", 0.0, new ComplexD(-0.0, -0.0).abs());
        same("abs(0.0, -0.0)", 0.0, new ComplexD(0.0, -0.0).abs());
        same("abs(0.0, 0.0)", 0.0, ComplexD.Zero().abs());
        same("static abs(-0.0, 0.0)", 0.0, ComplexD.abs(-0.0, 0.0));
        same("static abs(-0.0, -0.0)", 0.0, ComplexD.abs(-0.0, -0.0));
    }

    @Test
    public void testMultiplicationOverflowsToNan() {
        // the plain formula is not rescaled, so a product that leaves the range
        // can cancel to NaN instead of reaching infinity
        ComplexD got = new ComplexD(1.0e200, 1.0e200).mul(new ComplexD(1.0e200, 1.0e200));
        same("(1e200,1e200)^2", NAN, INF, got);
        // where nothing cancels it does reach infinity
        same("(1e200,0)*(1e200,0)", INF, 0.0, new ComplexD(1.0e200, 0.0).mul(new ComplexD(1.0e200, 0.0)));
    }

    @Test
    public void testMultiplicationAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 20) {
            for (int k = 0; k < ANGLES; ++k) {
                double a = Math.pow(10.0, e) * Math.cos(angle(k));
                double b = Math.pow(10.0, e) * Math.sin(angle(k));
                for (int f = -200; f <= 200; f += 100) {
                    double c = Math.pow(10.0, f) * Math.cos(angle(k) + 1.1);
                    double d = Math.pow(10.0, f) * Math.sin(angle(k) + 1.1);
                    BigDecimal wantRe = big(a).multiply(big(c), MC).subtract(big(b).multiply(big(d), MC), MC);
                    BigDecimal wantIm = big(a).multiply(big(d), MC).add(big(b).multiply(big(c), MC), MC);
                    exact("mul" + at(a, b), wantRe, wantIm, new ComplexD(a, b).mul(new ComplexD(c, d)), 1.0e-15);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testDivisionAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 20) {
            for (int k = 0; k < ANGLES; ++k) {
                double a = Math.pow(10.0, e) * Math.cos(angle(k));
                double b = Math.pow(10.0, e) * Math.sin(angle(k));
                for (int f = -200; f <= 200; f += 100) {
                    double c = Math.pow(10.0, f) * Math.cos(angle(k) + 1.1);
                    double d = Math.pow(10.0, f) * Math.sin(angle(k) + 1.1);
                    BigDecimal den = big(c).multiply(big(c), MC).add(big(d).multiply(big(d), MC), MC);
                    BigDecimal wantRe = big(a).multiply(big(c), MC).add(big(b).multiply(big(d), MC), MC)
                            .divide(den, MC);
                    BigDecimal wantIm = big(b).multiply(big(c), MC).subtract(big(a).multiply(big(d), MC), MC)
                            .divide(den, MC);
                    exact("div" + at(a, b), wantRe, wantIm, new ComplexD(a, b).div(new ComplexD(c, d)), 1.0e-15);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testSumAndDifferenceAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 20) {
            for (int k = 0; k < ANGLES; ++k) {
                double a = Math.pow(10.0, e) * Math.cos(angle(k));
                double b = Math.pow(10.0, e) * Math.sin(angle(k));
                for (int f = -200; f <= 200; f += 100) {
                    double c = Math.pow(10.0, f) * Math.cos(angle(k) + 1.1);
                    double d = Math.pow(10.0, f) * Math.sin(angle(k) + 1.1);
                    // a single rounding costs at most half an ulp
                    exact("add" + at(a, b), big(a).add(big(c), MC), big(b).add(big(d), MC),
                            new ComplexD(a, b).add(new ComplexD(c, d)), Math.ulp(1.0));
                    exact("sub" + at(a, b), big(a).subtract(big(c), MC), big(b).subtract(big(d), MC),
                            new ComplexD(a, b).sub(new ComplexD(c, d)), Math.ulp(1.0));
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 800);
    }

    @Test
    public void testInverseAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                BigDecimal den = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                exact("inv" + at(a, b), big(a).divide(den, MC), big(b).negate().divide(den, MC),
                        new ComplexD(a, b).inv(), 1.0e-15);
            }
        }
        assertTrue("too few points compared: " + compared, compared > 300);
    }

    @Test
    public void testSquareRootSquaredIsTheOriginal() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                ComplexD root = new ComplexD(a, b).sqrt();
                BigDecimal x = big(root.re());
                BigDecimal y = big(root.im());
                // square the root exactly, without going through mul
                BigDecimal gotRe = x.multiply(x, MC).subtract(y.multiply(y, MC), MC);
                BigDecimal gotIm = x.multiply(y, MC).multiply(big(2.0), MC);
                BigDecimal mod2 = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                BigDecimal errRe = gotRe.subtract(big(a), MC);
                BigDecimal errIm = gotIm.subtract(big(b), MC);
                BigDecimal err2 = errRe.multiply(errRe, MC).add(errIm.multiply(errIm, MC), MC);
                BigDecimal bound = mod2.multiply(big(1.0e-15).multiply(big(1.0e-15), MC), MC);
                assertTrue("sqrt" + at(a, b) + " squared is " + gotRe.round(MC) + ", " + gotIm.round(MC),
                        err2.compareTo(bound) <= 0);
            }
        }
    }

    @Test
    public void testSquareRootIsThePrincipalValue() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                ComplexD root = new ComplexD(a, b).sqrt();
                assertTrue("sqrt" + at(a, b) + " has a negative real part", root.re() >= 0.0);
                assertEquals("sqrt" + at(a, b) + " flipped the imaginary sign", Math.signum(b),
                        Math.signum(root.im()), 0.0);
            }
        }
    }

    @Test
    public void testExpAndLnAreInverse() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD want = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                // ln of a large modulus is large, and exp turns that back into a
                // relative error, so 1e-12 is as tight as this can be
                close("exp(ln z)", want, want.ln().exp(), 1.0e-12);
            }
        }
    }

    @Test
    public void testExpHasTheModulusAndAngleItShould() {
        for (double re = -700.0; re <= 700.0; re += 50.0) {
            for (int k = 0; k < ANGLES; ++k) {
                double im = angle(k);
                ComplexD got = new ComplexD(re, im).exp();
                double wantAbs = Math.exp(re);
                assertTrue("|exp| at re=" + re + ": want " + wantAbs + ", got " + got.abs(),
                        Math.abs(got.abs() - wantAbs) <= 1.0e-14 * wantAbs);
                // sin of the difference closes the angle over the branch cut
                assertTrue("arg(exp) at im=" + im + ", got " + got.arg(),
                        Math.abs(Math.sin(got.arg() - im)) <= 1.0e-13);
            }
        }
    }

    @Test
    public void testExpStaysFiniteWhereItCan() {
        // e^710 overflows, the product with cos(PI/2) does not
        ComplexD got = new ComplexD(710.0, Math.PI / 2.0).exp();
        assertTrue("the real part collapsed to " + got.re(), isFinite(got.re()) && got.re() > 0.0);
        assertTrue("the imaginary part should overflow", Double.isInfinite(got.im()));
        // an exact zero is kept, not turned into inf * 0
        same("exp(710)", INF, 0.0, new ComplexD(710.0, 0.0).exp());
        // far enough out nothing is finite any more
        got = new ComplexD(1500.0, Math.PI / 2.0).exp();
        assertTrue("the real part should overflow", Double.isInfinite(got.re()));
    }

    @Test
    public void testArgIsConsistentWithTheComponents() {
        for (int e = -150; e <= 150; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                ComplexD zz = new ComplexD(a, b);
                double abs = zz.abs();
                double phi = zz.arg();
                assertTrue("abs*cos(arg)" + at(a, b), Math.abs(abs * Math.cos(phi) - a) <= 1.0e-15 * abs);
                assertTrue("abs*sin(arg)" + at(a, b), Math.abs(abs * Math.sin(phi) - b) <= 1.0e-15 * abs);
            }
        }
    }

    @Test
    public void testIntegerPowAgainstRepeatedMultiplication() {
        for (int e = -30; e <= 30; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD base = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                for (int n = 1; n <= 4; ++n) {
                    BigDecimal wantRe = big(base.re());
                    BigDecimal wantIm = big(base.im());
                    for (int i = 1; i < n; ++i) {
                        BigDecimal nextRe = wantRe.multiply(big(base.re()), MC)
                                .subtract(wantIm.multiply(big(base.im()), MC), MC);
                        wantIm = wantRe.multiply(big(base.im()), MC).add(wantIm.multiply(big(base.re()), MC), MC);
                        wantRe = nextRe;
                    }
                    // the path through ln and exp costs a few digits
                    exact("pow " + n, wantRe, wantIm, base.pow((double) n), 1.0e-13);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 300);
    }

    // ================= 2. the infinity convention, as literals =================

    @Test
    public void testMultiplicationWithInfinity() {
        same("(2,3)*(4,5)", -7.0, 22.0, new ComplexD(2.0, 3.0).mul(new ComplexD(4.0, 5.0)));
        same("(inf,0)*(2,0)", INF, 0.0, new ComplexD(INF, 0.0).mul(new ComplexD(2.0, 0.0)));
        same("(inf,0)*(0,2)", 0.0, INF, new ComplexD(INF, 0.0).mul(new ComplexD(0.0, 2.0)));
        same("(1,0)*(inf,inf)", INF, INF, ComplexD.One().mul(ComplexD.Inf()));
        same("(-inf,0)*(2,0)", -INF, 0.0, new ComplexD(-INF, 0.0).mul(new ComplexD(2.0, 0.0)));
        // zero times infinity has neither direction nor modulus
        same("(inf,0)*(0,0)", NAN, NAN, new ComplexD(INF, 0.0).mul(ComplexD.Zero()));
        same("(-0.0,0)*(inf,0)", NAN, NAN, new ComplexD(-0.0, 0.0).mul(new ComplexD(INF, 0.0)));
        // against an infinite operand a NaN component counts as zero
        same("(inf,NaN)*(2,0)", INF, 0.0, new ComplexD(INF, NAN).mul(new ComplexD(2.0, 0.0)));
        // but (NaN,NaN) then has no direction left at all
        same("(inf,inf)*(NaN,NaN)", NAN, NAN, ComplexD.Inf().mul(ComplexD.NaN()));
        same("(NaN,NaN)*(2,0)", NAN, NAN, ComplexD.NaN().mul(new ComplexD(2.0, 0.0)));
    }

    @Test
    public void testDivisionWithZeroAndInfinity() {
        same("(1,0)/(0,0)", INF, INF, ComplexD.One().div(ComplexD.Zero()));
        same("(0,0)/(0,0)", NAN, NAN, ComplexD.Zero().div(ComplexD.Zero()));
        same("(2,0)/(inf,inf)", 0.0, 0.0, new ComplexD(2.0, 0.0).div(ComplexD.Inf()));
        same("(inf,0)/(2,0)", INF, 0.0, new ComplexD(INF, 0.0).div(new ComplexD(2.0, 0.0)));
        same("(inf,0)/(0,2)", 0.0, -INF, new ComplexD(INF, 0.0).div(new ComplexD(0.0, 2.0)));
        // infinity over infinity has no direction
        same("(inf,0)/(inf,0)", NAN, NAN, new ComplexD(INF, 0.0).div(new ComplexD(INF, 0.0)));
        same("(0,0)/(2,0)", 0.0, 0.0, ComplexD.Zero().div(new ComplexD(2.0, 0.0)));
        same("(NaN,NaN)/(2,0)", NAN, NAN, ComplexD.NaN().div(new ComplexD(2.0, 0.0)));
    }

    @Test
    public void testScaleWithInfinity() {
        same("(2,3)*1", 2.0, 3.0, new ComplexD(2.0, 3.0).scale(1.0));
        same("(2,3)*inf", INF, INF, new ComplexD(2.0, 3.0).scale(INF));
        same("(2,-3)*inf", INF, -INF, new ComplexD(2.0, -3.0).scale(INF));
        same("(inf,0)*2", INF, 0.0, new ComplexD(INF, 0.0).scale(2.0));
        same("(inf,0)*0", NAN, NAN, new ComplexD(INF, 0.0).scale(0.0));
        same("(0,0)*inf", NAN, NAN, ComplexD.Zero().scale(INF));
    }

    @Test
    public void testInverseAtTheEdges() {
        same("1/(0,0)", INF, INF, ComplexD.Zero().inv());
        same("1/(inf,inf)", 0.0, 0.0, ComplexD.Inf().inv());
        same("1/(inf,0)", 0.0, 0.0, new ComplexD(INF, 0.0).inv());
        same("1/(2,0)", 0.5, -0.0, new ComplexD(2.0, 0.0).inv());
        same("1/(0,2)", 0.0, -0.5, new ComplexD(0.0, 2.0).inv());
        same("1/(NaN,NaN)", NAN, NAN, ComplexD.NaN().inv());
    }

    @Test
    public void testSquareRootAtTheEdges() {
        same("sqrt(4)", 2.0, 0.0, new ComplexD(4.0, 0.0).sqrt());
        same("sqrt(-4)", 0.0, 2.0, new ComplexD(-4.0, 0.0).sqrt());
        // copySign carries the branch cut
        same("sqrt(-4-0i)", 0.0, -2.0, new ComplexD(-4.0, -0.0).sqrt());
        same("sqrt(0)", 0.0, 0.0, ComplexD.Zero().sqrt());
        same("sqrt(-0-0i)", 0.0, -0.0, new ComplexD(-0.0, -0.0).sqrt());
        same("sqrt(inf,1)", INF, 0.0, new ComplexD(INF, 1.0).sqrt());
        same("sqrt(-inf,1)", 0.0, INF, new ComplexD(-INF, 1.0).sqrt());
        // an infinite imaginary part decides, whatever the real part is
        same("sqrt(1,inf)", INF, INF, new ComplexD(1.0, INF).sqrt());
        same("sqrt(1,-inf)", INF, -INF, new ComplexD(1.0, -INF).sqrt());
        same("sqrt(-inf,-inf)", INF, -INF, new ComplexD(-INF, -INF).sqrt());
        same("sqrt(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).sqrt());
        same("sqrt(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).sqrt());
    }

    @Test
    public void testExpAndLnAtTheEdges() {
        same("exp(0)", 1.0, 0.0, ComplexD.Zero().exp());
        same("exp(-inf)", 0.0, 0.0, new ComplexD(-INF, 0.0).exp());
        same("exp(inf)", INF, 0.0, new ComplexD(INF, 0.0).exp());
        // C99 Annex G: exp of NaN + i0 stays on the real axis
        same("exp(NaN)", NAN, 0.0, new ComplexD(NAN, 0.0).exp());
        same("exp(NaN - 0i)", NAN, -0.0, new ComplexD(NAN, -0.0).exp());
        // a nonzero imaginary part lets the NaN through
        same("exp(NaN + i)", NAN, NAN, new ComplexD(NAN, 1.0).exp());
        same("exp(0,inf)", NAN, NAN, new ComplexD(0.0, INF).exp());
        same("ln(0)", -INF, 0.0, ComplexD.Zero().ln());
        same("ln(-1)", 0.0, Math.PI, new ComplexD(-1.0, 0.0).ln());
        same("ln(inf,0)", INF, 0.0, new ComplexD(INF, 0.0).ln());
        same("ln(NaN,0)", NAN, NAN, new ComplexD(NAN, 0.0).ln());
    }

    @Test
    public void testPowWithARealExponentAtTheEdges() {
        same("0^0", 1.0, 0.0, ComplexD.Zero().pow(0.0));
        same("0^2", 0.0, 0.0, ComplexD.Zero().pow(2.0));
        same("0^-2", INF, INF, ComplexD.Zero().pow(-2.0));
        same("inf^2", INF, INF, ComplexD.Inf().pow(2.0));
        same("inf^-2", 0.0, 0.0, ComplexD.Inf().pow(-2.0));
        same("0^NaN", NAN, NAN, ComplexD.Zero().pow(NAN));
        same("NaN^2", NAN, NAN, ComplexD.NaN().pow(2.0));
        // an infinite exponent takes the values of Math.pow, with the modulus
        same("2^inf", INF, INF, new ComplexD(2.0, 0.0).pow(INF));
        same("2^-inf", 0.0, 0.0, new ComplexD(2.0, 0.0).pow(-INF));
        same("0.5^inf", 0.0, 0.0, new ComplexD(0.5, 0.0).pow(INF));
        same("0.5^-inf", INF, INF, new ComplexD(0.5, 0.0).pow(-INF));
        // modulus one has no limit to go to
        same("i^inf", NAN, NAN, ComplexD.I().pow(INF));
    }

    @Test
    public void testPowWithAComplexExponentAtTheEdges() {
        same("0^0", 1.0, 0.0, ComplexD.Zero().pow(ComplexD.Zero()));
        same("0^2", 0.0, 0.0, ComplexD.Zero().pow(new ComplexD(2.0, 0.0)));
        // a purely imaginary exponent is not defined for a degenerate base
        same("0^2i", NAN, NAN, ComplexD.Zero().pow(new ComplexD(0.0, 2.0)));
        same("0^(2+3i)", 0.0, 0.0, ComplexD.Zero().pow(new ComplexD(2.0, 3.0)));
        same("2^(inf,0)", INF, INF, new ComplexD(2.0, 0.0).pow(new ComplexD(INF, 0.0)));
        // an infinite exponent that is not real has no direction
        same("2^(inf,1)", NAN, NAN, new ComplexD(2.0, 0.0).pow(new ComplexD(INF, 1.0)));
    }

    @Test
    public void testFromPolar() {
        same("fromPolar(0,1)", 0.0, 0.0, ComplexD.fromPolar(0.0, 1.0));
        // an exact zero stays zero even for an infinite radius
        same("fromPolar(inf,0)", INF, 0.0, ComplexD.fromPolar(INF, 0.0));
        same("fromPolar(2,PI/2)", 2.0 * Math.cos(Math.PI / 2.0), 2.0, ComplexD.fromPolar(2.0, Math.PI / 2.0));
        for (int e = -300; e <= 300; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD got = ComplexD.fromPolar(r, angle(k));
                assertTrue("fromPolar radius at 1e" + e, Math.abs(got.abs() - r) <= 1.0e-15 * r);
            }
        }
    }

    @Test
    public void testFromPolarRejectsANegativeRadius() {
        try {
            ComplexD.fromPolar(-1.0, 0.0);
            fail("a negative radius should be rejected");
        } catch (IllegalArgumentException expected) {
            // that is the contract
        }
    }

    @Test
    public void testTheStaticAbsMatchesTheInstanceOne() {
        for (double[] v : SPECIAL) {
            // the static form has no isInfinite() guard, so it only has to agree
            // where nothing is infinite
            if (!new ComplexD(v[0], v[1]).isInfinite()) {
                same("abs" + at(v[0], v[1]), new ComplexD(v[0], v[1]).abs(), ComplexD.abs(v[0], v[1]));
            }
        }
    }

    // ---------- the hyperbolic and trigonometric pair ----------

    @Test
    public void testSinhAndCoshAgainstExp() {
        // sinh(z) = (exp(z) - exp(-z))/2 and cosh(z) = (exp(z) + exp(-z))/2,
        // through the exp that the tests above have already pinned down
        for (double x = -20.0; x <= 20.0; x += 2.5) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, 3.0 * angle(k));
                ComplexD ez = v.exp();
                ComplexD em = v.neg().exp();
                close("sinh" + at(x, v.im()), ez.sub(em).scale(0.5), v.sinh(), 1.0e-13);
                close("cosh" + at(x, v.im()), ez.add(em).scale(0.5), v.cosh(), 1.0e-13);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testThePythagoreanIdentities() {
        // cosh^2 - sinh^2 == 1 and sin^2 + cos^2 == 1, through mul and sub
        for (double x = -6.0; x <= 6.0; x += 0.75) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, angle(k));
                ComplexD ch = v.cosh();
                ComplexD sh = v.sinh();
                // the squares cancel down to 1, so the bound goes with them
                unit("cosh^2 - sinh^2" + at(x, v.im()), ch.mul(ch).sub(sh.mul(sh)), ch.abs() * ch.abs());
                ComplexD s = v.sin();
                ComplexD c = v.cos();
                unit("sin^2 + cos^2" + at(x, v.im()), s.mul(s).add(c.mul(c)), c.abs() * c.abs());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    /** the value should be one, to within the size of the terms that cancelled */
    private static void unit(String what, ComplexD got, double terms) {
        double err = Math.hypot(got.re() - 1.0, got.im());
        double bound = 1.0e-14 * Math.max(1.0, terms);
        assertTrue(what + ": got " + got + ", off by " + err + ", bound " + bound, err <= bound);
    }

    @Test
    public void testOnTheRealAndImaginaryAxes() {
        for (double x = -6.0; x <= 6.0; x += 0.25) {
            // a real argument gives a real sine and cosine, bit for bit
            same("sin re" + at(x, 0.0), Math.sin(x), new ComplexD(x, 0.0).sin().re());
            same("cos re" + at(x, 0.0), Math.cos(x), new ComplexD(x, 0.0).cos().re());
            same("sinh re" + at(x, 0.0), Math.sinh(x), new ComplexD(x, 0.0).sinh().re());
            same("cosh re" + at(x, 0.0), Math.cosh(x), new ComplexD(x, 0.0).cosh().re());
            assertEquals("sin im" + at(x, 0.0), 0.0, new ComplexD(x, 0.0).sin().im(), 0.0);
            assertEquals("cosh im" + at(x, 0.0), 0.0, new ComplexD(x, 0.0).cosh().im(), 0.0);
            // an imaginary argument turns sine into sinh and cosine into cosh
            same("sin(iy) im" + at(0.0, x), Math.sinh(x), new ComplexD(0.0, x).sin().im());
            same("cos(iy) re" + at(0.0, x), Math.cosh(x), new ComplexD(0.0, x).cos().re());
        }
    }

    @Test
    public void testParity() {
        // sinh and sin are odd, cosh and cos are even
        for (double x = -6.0; x <= 6.0; x += 0.75) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, angle(k));
                ComplexD n = v.neg();
                same("sinh odd", v.sinh().neg().re(), n.sinh().re());
                same("sinh odd", v.sinh().neg().im(), n.sinh().im());
                same("cosh even", v.cosh().re(), n.cosh().re());
                same("cosh even", v.cosh().im(), n.cosh().im());
                same("sin odd", v.sin().neg().re(), n.sin().re());
                same("cos even", v.cos().re(), n.cos().re());
            }
        }
    }

    @Test
    public void testTheAdditionTheorem() {
        // sinh(a+b) = sinh(a)cosh(b) + cosh(a)sinh(b)
        for (double x = -3.0; x <= 3.0; x += 1.5) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD a = new ComplexD(x, angle(k));
                ComplexD b = new ComplexD(0.5 - x, angle(k) / 2.0);
                ComplexD want = a.sinh().mul(b.cosh()).add(a.cosh().mul(b.sinh()));
                ComplexD got = a.add(b).sinh();
                // the two terms are far larger than their sum, so the bound
                // goes with the terms and not with the result
                double terms = a.sinh().abs() * b.cosh().abs() + a.cosh().abs() * b.sinh().abs();
                double err = Math.hypot(got.re() - want.re(), got.im() - want.im());
                assertTrue("sinh(a+b)" + at(x, a.im()) + ": off by " + err + ", terms " + terms,
                        err <= 1.0e-14 * terms);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 30);
    }

    @Test
    public void testTheOverflowBandWhereCoshOutlivesExp() {
        // e^710 overflows, cosh(710) does not
        ComplexD v = new ComplexD(710.0, 0.0).sinh();
        assertTrue("sinh(710) collapsed to " + v.re(), isFinite(v.re()));
        same("sinh(710) re", Math.sinh(710.0), v.re());
        assertEquals("sinh(710) im", 0.0, v.im(), 0.0);
        // sin(1 + 710i) keeps both components, as the C library does
        ComplexD w = new ComplexD(1.0, 710.0).sin();
        same("sin(1+710i) re", 9.399208879688907E307, w.re());
        same("sin(1+710i) im", 6.035162617272642E307, w.im());
        // one step further nothing is finite any more
        assertTrue("sinh(711)", Double.isInfinite(new ComplexD(711.0, 0.0).sinh().re()));
    }

    @Test
    public void testTheHyperbolicPairAtTheEdges() {
        same("sinh(0)", 0.0, 0.0, ComplexD.Zero().sinh());
        same("cosh(0)", 1.0, 0.0, ComplexD.Zero().cosh());
        same("sin(0)", 0.0, 0.0, ComplexD.Zero().sin());
        same("cos(0)", 1.0, -0.0, ComplexD.Zero().cos());
        // an exact zero survives a value that has none, as in exp
        same("sinh(NaN)", NAN, 0.0, new ComplexD(NAN, 0.0).sinh());
        same("cosh(NaN)", NAN, 0.0, new ComplexD(NAN, 0.0).cosh());
        same("cosh(0,inf)", NAN, 0.0, new ComplexD(0.0, INF).cosh());
        same("sinh(0,inf)", 0.0, NAN, new ComplexD(0.0, INF).sinh());
        // an infinite real part still takes its direction from cos and sin
        same("cosh(inf,0)", INF, 0.0, new ComplexD(INF, 0.0).cosh());
        same("cosh(-inf,0)", INF, -0.0, new ComplexD(-INF, 0.0).cosh());
        same("sinh(inf,2)", -INF, INF, new ComplexD(INF, 2.0).sinh());
        same("sinh(-inf,0)", -INF, 0.0, new ComplexD(-INF, 0.0).sinh());
        // both parts unbounded: the modulus survives, the direction does not
        same("sinh(inf,inf)", INF, NAN, ComplexD.Inf().sinh());
        same("cosh(inf,inf)", INF, NAN, ComplexD.Inf().cosh());
        same("sin(inf,inf)", NAN, -INF, ComplexD.Inf().sin());
        same("cos(inf,inf)", INF, NAN, ComplexD.Inf().cos());
        same("cosh(inf,NaN)", INF, NAN, new ComplexD(INF, NAN).cosh());
        // a finite real part against a value that has none
        same("cosh(1,inf)", NAN, NAN, new ComplexD(1.0, INF).cosh());
        same("sin(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).sin());
        same("cosh(NaN,NaN)", NAN, NAN, ComplexD.NaN().cosh());
    }

    @Test
    public void testWhereTheSignOfAZeroIsLeftOpen() {
        // C99 does not fix the sign of this zero; the C library answers +0.0,
        // the formula here yields -0.0. Pinned so a change is noticed.
        same("cos(inf,0) im", -0.0, new ComplexD(INF, 0.0).cos().im());
        same("cos(-inf,0) im", -0.0, new ComplexD(-INF, 0.0).cos().im());
        same("cos(NaN,0) im", -0.0, new ComplexD(NAN, 0.0).cos().im());
    }

    // ---------- the tangent pair ----------

    @Test
    public void testTanhAgainstTheQuotient() {
        // tanh = sinh/cosh, through the three that are already pinned down,
        // wherever sinh and cosh still fit into a double
        for (double x = -300.0; x <= 300.0; x += 3.0) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, angle(k));
                ComplexD sh = v.sinh();
                ComplexD ch = v.cosh();
                if (!isFinite(ch.re()) || !isFinite(ch.im()) || !isFinite(sh.re()) || !isFinite(sh.im())) {
                    continue;
                }
                close("tanh" + at(x, v.im()), sh.div(ch), v.tanh(), 1.0e-14);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 500);
    }

    @Test
    public void testTanhWhereTheQuotientBreaks() {
        // past 710 sinh itself is unbounded, so sinh/cosh has nothing left;
        // tanh saturates instead. That is the point of the whole method.
        for (double x = 720.0; x <= 900.0; x += 20.0) {
            for (int s = -1; s <= 1; s += 2) {
                ComplexD v = new ComplexD(s * x, 1.0);
                ComplexD got = v.tanh();
                same("tanh re at " + (s * x), (double) s, got.re());
                assertEquals("tanh im at " + (s * x), 0.0, got.im(), 0.0);
                assertTrue("the quotient should have failed at " + (s * x),
                        Double.isNaN(v.sinh().div(v.cosh()).re()));
            }
        }
        // the naive form, which the C library uses, gives up far earlier
        ComplexD v = new ComplexD(400.0, 1.0);
        ComplexD sh = v.sinh();
        ComplexD ch = v.cosh();
        double naive = (sh.re() * ch.re() + sh.im() * ch.im())
                / (ch.re() * ch.re() + ch.im() * ch.im());
        assertTrue("the naive quotient should fail at 400", Double.isNaN(naive));
        same("tanh(400,1) re", 1.0, v.tanh().re());
    }

    @Test
    public void testTanhOnTheRealAndImaginaryAxes() {
        for (double x = -25.0; x <= 25.0; x += 0.25) {
            // the real axis is Math.tanh itself, bit for bit
            same("tanh re" + at(x, 0.0), Math.tanh(x), new ComplexD(x, 0.0).tanh().re());
            assertEquals("tanh im" + at(x, 0.0), 0.0, new ComplexD(x, 0.0).tanh().im(), 0.0);
            // and the imaginary axis turns tan into tanh
            same("tan(iy) im" + at(0.0, x), Math.tanh(x), new ComplexD(0.0, x).tan().im());
            assertEquals("tan(iy) re" + at(0.0, x), 0.0, new ComplexD(0.0, x).tan().re(), 0.0);
        }
    }

    @Test
    public void testTanhAndTanAreOdd() {
        for (double x = -6.0; x <= 6.0; x += 0.75) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, angle(k));
                ComplexD n = v.neg();
                same("tanh odd re", v.tanh().neg().re(), n.tanh().re());
                same("tanh odd im", v.tanh().neg().im(), n.tanh().im());
                same("tan odd re", v.tan().neg().re(), n.tan().re());
                same("tan odd im", v.tan().neg().im(), n.tan().im());
            }
        }
    }

    @Test
    public void testTheDoubleAngleFormula() {
        // tanh(2z) = 2 tanh(z) / (1 + tanh(z)^2)
        for (double x = -4.0; x <= 4.0; x += 0.5) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(x, angle(k) / 2.0);
                ComplexD th = v.tanh();
                ComplexD want = th.scale(2.0).div(ComplexD.One().add(th.mul(th)));
                ComplexD got = v.scale(2.0).tanh();
                // the denominator can come close to zero, so the bound goes
                // with the terms and not with the result
                double terms = 2.0 * th.abs() + th.abs() * th.abs();
                double err = Math.hypot(got.re() - want.re(), got.im() - want.im());
                assertTrue("tanh(2z)" + at(x, v.im()) + ": off by " + err + ", terms " + terms,
                        err <= 1.0e-13 * Math.max(1.0, terms));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testTheTangentPairAtTheEdges() {
        same("tanh(0)", 0.0, 0.0, ComplexD.Zero().tanh());
        same("tanh(-0,-0)", -0.0, -0.0, new ComplexD(-0.0, -0.0).tanh());
        same("tan(0)", 0.0, 0.0, ComplexD.Zero().tan());
        // an infinite real part saturates, the imaginary part dies away
        same("tanh(inf,1)", 1.0, 0.0, new ComplexD(INF, 1.0).tanh());
        same("tanh(-inf,1)", -1.0, 0.0, new ComplexD(-INF, 1.0).tanh());
        same("tanh(inf,inf)", 1.0, 0.0, ComplexD.Inf().tanh());
        same("tanh(inf,-inf)", 1.0, -0.0, new ComplexD(INF, -INF).tanh());
        same("tanh(inf,NaN)", 1.0, 0.0, new ComplexD(INF, NAN).tanh());
        // a NaN real part keeps a value on the real axis real
        same("tanh(NaN,0)", NAN, 0.0, new ComplexD(NAN, 0.0).tanh());
        same("tanh(NaN,-0.0)", NAN, -0.0, new ComplexD(NAN, -0.0).tanh());
        same("tanh(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).tanh());
        // an unbounded imaginary part against a finite real part has no value
        same("tanh(1,inf)", NAN, NAN, new ComplexD(1.0, INF).tanh());
        same("tanh(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).tanh());
        same("tan(inf,0)", NAN, NAN, new ComplexD(INF, 0.0).tan());
        // but the tangent does have a limit along the imaginary axis
        same("tan(1,inf)", 0.0, 1.0, new ComplexD(1.0, INF).tan());
        same("tan(0,inf)", 0.0, 1.0, new ComplexD(0.0, INF).tan());
        // where the C library answers NaN, because it divides naively
        same("tanh(1e300,1e300)", 1.0, 0.0, new ComplexD(1.0e300, 1.0e300).tanh());
    }

    // ---------- the n-th roots ----------

    /** w raised to the n-th power, exactly, against z */
    private void power(String what, ComplexD w, int n, ComplexD z, double tol) {
        BigDecimal pr = big(w.re());
        BigDecimal pi = big(w.im());
        BigDecimal wr = pr;
        BigDecimal wi = pi;
        for (int i = 1; i < n; ++i) {
            BigDecimal nr = pr.multiply(wr, MC).subtract(pi.multiply(wi, MC), MC);
            pi = pr.multiply(wi, MC).add(pi.multiply(wr, MC), MC);
            pr = nr;
        }
        exact(what, pr, pi, z, tol);
    }

    @Test
    public void testNthRootRaisedToTheNthPower() {
        // the root taken back up, in exact arithmetic
        for (int n = 1; n <= 8; ++n) {
            for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
                double r = Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                    // the path through pow, cos and sin costs about 4e-14
                    power("nthRoot " + n + at(v.re(), v.im()), v.nthRoot(n), n, v, 1.0e-13);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 3000);
    }

    @Test
    public void testNthRootsAreTheCompleteSet() {
        for (int n = 1; n <= 8; ++n) {
            for (int e = -60; e <= 60; e += 10) {
                double r = Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                    ComplexD[] roots = v.nthRoots(n);
                    assertEquals("nthRoots(" + n + ") length", n, roots.length);
                    for (int i = 0; i < n; ++i) {
                        // every one of them is a root
                        power("root " + i + " of " + n, roots[i], n, v, 1.0e-13);
                        // and they all sit on the same circle
                        assertTrue("modulus of root " + i + " of " + n,
                                Math.abs(roots[i].abs() - roots[0].abs()) <= 1.0e-15 * roots[0].abs());
                        // and no two of them are the same point
                        for (int j = 0; j < i; ++j) {
                            double gap = Math.hypot(roots[i].re() - roots[j].re(),
                                    roots[i].im() - roots[j].im());
                            assertTrue("roots " + i + " and " + j + " of " + n + " coincide",
                                    gap > 1.0e-9 * roots[0].abs());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testTheTwoRootMethodsAgree() {
        // nthRoots(n)[0] is nthRoot(n), bit for bit, signed zeros included
        double[] tricky = { 0.0, -0.0, 1.0, -1.0, 3.0, -3.0, INF, NAN };
        for (int n = 1; n <= 8; ++n) {
            for (int e = MIN_EXP; e <= MAX_EXP; e += 30) {
                double r = Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                    same("degree " + n + at(v.re(), v.im()), v.nthRoot(n).re(), v.nthRoots(n)[0].re());
                    same("degree " + n + at(v.re(), v.im()), v.nthRoot(n).im(), v.nthRoots(n)[0].im());
                }
            }
            for (double x : tricky) {
                for (double y : new double[] { 0.0, -0.0, 1.0 }) {
                    ComplexD v = new ComplexD(x, y);
                    same("degree " + n + at(x, y), v.nthRoot(n).re(), v.nthRoots(n)[0].re());
                    same("degree " + n + at(x, y), v.nthRoot(n).im(), v.nthRoots(n)[0].im());
                }
            }
        }
    }

    @Test
    public void testTheTwoSpecialDegrees() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 10) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                // degree one is the value itself, not a trip through cos and sin
                assertSame("nthRoot(1)" + at(v.re(), v.im()), v, v.nthRoot(1));
                assertSame("nthRoots(1)" + at(v.re(), v.im()), v, v.nthRoots(1)[0]);
                // degree two is sqrt, so the two never disagree
                ComplexD w = v.sqrt();
                same("nthRoot(2)" + at(v.re(), v.im()), w.re(), w.im(), v.nthRoot(2));
                ComplexD[] pair = v.nthRoots(2);
                assertEquals("nthRoots(2) length", 2, pair.length);
                same("nthRoots(2)[0]", w.re(), w.im(), pair[0]);
                // and the second one is the exact negation
                same("nthRoots(2)[1]", -w.re(), -w.im(), pair[1]);
            }
        }
    }

    @Test
    public void testNthRootRejectsADegreeThatIsNotPositive() {
        for (int n : new int[] { 0, -1, -7, Integer.MIN_VALUE }) {
            try {
                new ComplexD(3.0, 4.0).nthRoot(n);
                fail("nthRoot(" + n + ") should be rejected");
            } catch (IllegalArgumentException expected) {
                // that is the contract
            }
            try {
                new ComplexD(3.0, 4.0).nthRoots(n);
                fail("nthRoots(" + n + ") should be rejected");
            } catch (IllegalArgumentException expected) {
                // that is the contract
            }
        }
    }

    @Test
    public void testNthRootAtTheEdges() {
        same("cbrt(0)", 0.0, 0.0, ComplexD.Zero().nthRoot(3));
        same("cbrt(-0,-0)", 0.0, -0.0, new ComplexD(-0.0, -0.0).nthRoot(3));
        same("cbrt(inf,0)", INF, 0.0, new ComplexD(INF, 0.0).nthRoot(3));
        // inf * cis(PI/3) has both parts unbounded
        same("cbrt(-inf,0)", INF, INF, new ComplexD(-INF, 0.0).nthRoot(3));
        same("cbrt(0,inf)", INF, INF, new ComplexD(0.0, INF).nthRoot(3));
        same("cbrt(NaN,0)", NAN, NAN, new ComplexD(NAN, 0.0).nthRoot(3));
        same("cbrt(NaN,NaN)", NAN, NAN, ComplexD.NaN().nthRoot(3));
        // arg carries the branch cut, so the sign of the zero decides
        ComplexD up = new ComplexD(-4.0, 0.0).nthRoot(3);
        ComplexD down = new ComplexD(-4.0, -0.0).nthRoot(3);
        same("cbrt(-4+0i) re", 0.7937005259840999, up.re());
        same("cbrt(-4+0i) im", 1.3747296369986024, up.im());
        same("cbrt(-4-0i) re", 0.7937005259840999, down.re());
        same("cbrt(-4-0i) im", -1.3747296369986024, down.im());
        // a modulus is never negative any more, so fromPolar cannot refuse
        same("cbrt(-0.0, 0.0) re", 0.0, new ComplexD(-0.0, 0.0).nthRoot(3).re());
    }

    // ---------- the inverse tangent pair ----------

    @Test
    public void testAtanhRoundTripThroughTanh() {
        // only up to modulus one: atanh folds the whole far field into a thin
        // strip around +-i*PI/2, and tanh magnifies any error there. Measured:
        // 2.5e-16 at modulus one, 1.1e-12 at 1e4, useless at 1e16.
        for (int e = MIN_EXP; e <= 0; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                close("tanh(atanh(z))" + at(v.re(), v.im()), v, v.atanh().tanh(), 1.0e-15);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testAtanhAgainstTheLogIdentity() {
        // 0.5*(ln(1+z) - ln(1-z)) through the ln that is already pinned down.
        // Only from modulus one up: below it 1 +- z rounds back to 1 and the
        // identity loses everything, which is why the code uses log1p.
        for (int e = 0; e <= MAX_EXP; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD one = ComplexD.One();
                ComplexD want = one.add(v).ln().sub(one.sub(v).ln()).scale(0.5);
                if (!isFinite(want.re()) || !isFinite(want.im()) || want.abs() == 0.0) {
                    continue;
                }
                close("atanh" + at(v.re(), v.im()), want, v.atanh(), 1.0e-15);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testAtanhFarOut() {
        // far out atanh(z) is 1/z + i*PI/2, and inv is already pinned down
        for (int e = 20; e <= MAX_EXP; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD got = v.atanh();
                double want = v.inv().re();
                assertTrue("real part at 1e" + e + ": want " + want + ", got " + got.re(),
                        Math.abs(got.re() - want) <= 1.0e-15 * Math.abs(want));
                same("imaginary part at 1e" + e, Math.copySign(Math.PI / 2.0, v.im()), got.im());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 300);
    }

    @Test
    public void testAtanhOnTheRealAxis() {
        for (double x = -0.95; x <= 0.95; x += 0.05) {
            ComplexD got = new ComplexD(x, 0.0).atanh();
            // the stable real form; the plain log of the quotient loses digits
            // near zero, measured 4.5e-15 relative at x = 0.01
            double want = 0.5 * Math.log1p(2.0 * x / (1.0 - x));
            assertTrue("atanh(" + x + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-15 * Math.max(1.0, Math.abs(want)));
            assertEquals("atanh(" + x + ") stays real", 0.0, got.im(), 0.0);
        }
        // the denominator is exactly zero there and log1p carries it
        same("atanh(1)", INF, 0.0, new ComplexD(1.0, 0.0).atanh());
        same("atanh(-1)", -INF, 0.0, new ComplexD(-1.0, 0.0).atanh());
        // past one the value leaves the real axis by PI/2
        ComplexD two = new ComplexD(2.0, 0.0).atanh();
        assertTrue("atanh(2) real part", Math.abs(two.re() - 0.5 * Math.log(3.0)) <= 1.0e-15);
        same("atanh(2) imaginary part", Math.PI / 2.0, two.im());
    }

    @Test
    public void testAtanhAndAtanAreOdd() {
        for (int e = -6; e <= 6; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD n = v.neg();
                same("atanh odd re", v.atanh().neg().re(), n.atanh().re());
                same("atanh odd im", v.atanh().neg().im(), n.atanh().im());
                same("atan odd re", v.atan().neg().re(), n.atan().re());
                same("atan odd im", v.atan().neg().im(), n.atan().im());
            }
        }
    }

    @Test
    public void testTheRangeOfTheInverseTangent() {
        double half = Math.PI / 2.0;
        for (int e = MIN_EXP; e <= MAX_EXP; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                double re = v.atan().re();
                assertTrue("Re(atan) out of range: " + re, re >= -half && re <= half);
                double im = v.atanh().im();
                assertTrue("Im(atanh) out of range: " + im, im >= -half && im <= half);
            }
        }
    }

    @Test
    public void testTheInverseTangentPairAtTheEdges() {
        same("atanh(0)", 0.0, 0.0, ComplexD.Zero().atanh());
        same("atanh(-0,-0)", -0.0, -0.0, new ComplexD(-0.0, -0.0).atanh());
        same("atan(0)", 0.0, 0.0, ComplexD.Zero().atan());
        // the modulus dies away, the angle saturates
        same("atanh(inf,0)", 0.0, Math.PI / 2.0, new ComplexD(INF, 0.0).atanh());
        same("atanh(-inf,0)", -0.0, Math.PI / 2.0, new ComplexD(-INF, 0.0).atanh());
        same("atanh(0,inf)", 0.0, Math.PI / 2.0, new ComplexD(0.0, INF).atanh());
        same("atanh(inf,inf)", 0.0, Math.PI / 2.0, ComplexD.Inf().atanh());
        same("atanh(inf,-inf)", 0.0, -Math.PI / 2.0, new ComplexD(INF, -INF).atanh());
        // an unbounded imaginary part decides even against a NaN real part
        same("atanh(NaN,inf)", 0.0, Math.PI / 2.0, new ComplexD(NAN, INF).atanh());
        same("atanh(inf,NaN)", 0.0, NAN, new ComplexD(INF, NAN).atanh());
        // a NaN real part has no value to give
        same("atanh(NaN,0)", NAN, NAN, new ComplexD(NAN, 0.0).atanh());
        same("atanh(NaN,NaN)", NAN, NAN, ComplexD.NaN().atanh());
        // but an exact zero survives a NaN imaginary part
        same("atanh(0,NaN)", 0.0, NAN, new ComplexD(0.0, NAN).atanh());
        same("atanh(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).atanh());
        // the poles of atan sit on the imaginary axis at +-i
        same("atan(0,1)", 0.0, INF, new ComplexD(0.0, 1.0).atan());
        same("atan(0,-1)", 0.0, -INF, new ComplexD(0.0, -1.0).atan());
        same("atan(inf,0)", Math.PI / 2.0, 0.0, new ComplexD(INF, 0.0).atan());
        // where the C library answers NaN, because its denominator overflows
        same("atanh(1e300,1e300)", 5.0E-301, Math.PI / 2.0, new ComplexD(1.0e300, 1.0e300).atanh());
    }

    // ---------- the inverse sine pair ----------

    @Test
    public void testAsinhRoundTripThroughSinh() {
        // usable over the whole range, unlike tanh(atanh(z)): measured 0 below
        // modulus one, 1.2e-16 at one, 7.9e-14 at 1e300
        for (int e = MIN_EXP; e <= MAX_EXP; e += 5) {
            double r = Math.pow(10.0, e);
            double tol = 1.0e-15 * Math.max(1.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                close("sinh(asinh(z))" + at(v.re(), v.im()), v, v.asinh().sinh(), tol);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 900);
    }

    @Test
    public void testAsinAgainstSin() {
        for (int e = -8; e <= 8; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                close("sin(asin(z))" + at(v.re(), v.im()), v, v.asin().sin(), 2.0e-15);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testAsinhFarOut() {
        // far out asinh(z) is ln(2z), and asinh is odd, so the angle is taken
        // against |x|. From 1e20 up the two agree to the last bit or two.
        for (int e = 20; e <= MAX_EXP; e += 5) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double x = r * Math.cos(angle(k));
                double y = r * Math.sin(angle(k));
                ComplexD got = new ComplexD(x, y).asinh();
                double wantRe = Math.copySign(0.6931471805599453 + Math.log(new ComplexD(x, y).abs()), x);
                double wantIm = Math.atan2(y, Math.abs(x));
                assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                        Math.abs(got.re() - wantRe) <= 1.0e-15 * Math.abs(wantRe));
                assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                        Math.abs(got.im() - wantIm) <= 1.0e-15);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testAsinhAtTheTopOfTheRange() {
        // above 1e307 the product of the two roots would overflow, so only the
        // far field branch can answer here; and it must respect the oddness,
        // or the angle leaves [-PI/2, PI/2]
        double big = 1.0e308;
        double[][] points = { { big, big }, { -big, big }, { big, -big }, { -big, -big },
                { Double.MAX_VALUE, 0.0 }, { 0.0, big }, { big, 1.0 }, { 1.0, big } };
        for (int i = 0; i < points.length; ++i) {
            double x = points[i][0];
            double y = points[i][1];
            ComplexD got = new ComplexD(x, y).asinh();
            double wantRe = Math.copySign(0.6931471805599453 + Math.log(new ComplexD(x, y).abs()), x);
            double wantIm = Math.atan2(y, Math.abs(x));
            assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                    Math.abs(got.re() - wantRe) <= 1.0e-15 * Math.abs(wantRe));
            assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                    Math.abs(got.im() - wantIm) <= 1.0e-15);
            assertTrue("angle out of range" + at(x, y) + ": " + got.im(),
                    Math.abs(got.im()) <= Math.PI / 2.0);
        }
    }

    @Test
    public void testAsinhOnTheRealAxis() {
        for (int k = -400; k <= 400; ++k) {
            double x = k / 100.0;
            ComplexD got = new ComplexD(x, 0.0).asinh();
            // the stable real form; ln(x + sqrt(x*x+1)) loses everything near zero
            double a = Math.abs(x);
            double want = Math.copySign(Math.log1p(a + a * a / (1.0 + Math.sqrt(1.0 + a * a))), x);
            assertTrue("asinh(" + x + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-15 * Math.max(1.0, Math.abs(want)));
            same("asinh(" + x + ") stays real", 0.0, got.im());
        }
        // the signed zeros are carried through untouched
        same("asinh(0)", 0.0, 0.0, ComplexD.Zero().asinh());
        same("asinh(-0,-0)", -0.0, -0.0, new ComplexD(-0.0, -0.0).asinh());
    }

    @Test
    public void testAsinhOnTheImaginaryAxis() {
        // asinh(i*y) is i*asin(y) for |y| <= 1, and Math.asin has nothing in
        // common with the code under test
        for (int k = -100; k <= 100; ++k) {
            double y = k / 100.0;
            ComplexD got = new ComplexD(0.0, y).asinh();
            same("asinh(i*" + y + ") stays imaginary", 0.0, got.re());
            assertTrue("asinh(i*" + y + "): want " + Math.asin(y) + ", got " + got.im(),
                    Math.abs(got.im() - Math.asin(y)) <= 1.0e-15);
        }
        // past i the value leaves the imaginary axis by the real asinh of the cut
        for (int k = 11; k <= 400; ++k) {
            double y = k / 10.0;
            ComplexD got = new ComplexD(0.0, y).asinh();
            double want = Math.log(y + Math.sqrt(y * y - 1.0));
            assertTrue("asinh(i*" + y + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-15 * want);
            same("asinh(i*" + y + ") imaginary part", Math.PI / 2.0, got.im());
        }
    }

    @Test
    public void testAsinhAtTheBranchPoint() {
        // the expected values come from 120 digit arithmetic and NOT from the C
        // library, which is 2.3e7 ulp out at (1e-8, 1)
        branchPoint(1.0e-4, 0.010000083331458277771, 1.5607964101301048965);
        branchPoint(1.0e-8, 1.0000000008333333419e-4, 1.5706963267949799526);
        branchPoint(1.0e-16, 9.9999999999999999788e-9, 1.5707963167948966192);
    }

    private void branchPoint(double d, double wantRe, double wantIm) {
        ComplexD got = new ComplexD(d, 1.0).asinh();
        assertTrue("asinh(" + d + " + i) re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-15 * wantRe);
        assertTrue("asinh(" + d + " + i) im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-15 * wantIm);
    }

    @Test
    public void testAsinhAndAsinAreOddAndConjugateSymmetric() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 3) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD n = v.neg();
                ComplexD c = v.conj();
                same("asinh odd re", v.asinh().neg().re(), n.asinh().re());
                same("asinh odd im", v.asinh().neg().im(), n.asinh().im());
                same("asin odd re", v.asin().neg().re(), n.asin().re());
                same("asin odd im", v.asin().neg().im(), n.asin().im());
                same("asinh conj re", v.asinh().conj().re(), c.asinh().re());
                same("asinh conj im", v.asinh().conj().im(), c.asinh().im());
            }
        }
    }

    @Test
    public void testTheRangeOfTheInverseSine() {
        double half = Math.PI / 2.0;
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                double im = v.asinh().im();
                assertTrue("Im(asinh) out of range: " + im, im >= -half && im <= half);
                double re = v.asin().re();
                assertTrue("Re(asin) out of range: " + re, re >= -half && re <= half);
            }
        }
    }

    @Test
    public void testTheInverseSinePairAtTheEdges() {
        double half = Math.PI / 2.0;
        double quarter = Math.PI / 4.0;
        same("asinh(0,1)", 0.0, half, new ComplexD(0.0, 1.0).asinh());
        same("asinh(-0,1)", -0.0, half, new ComplexD(-0.0, 1.0).asinh());
        same("asinh(0,-1)", 0.0, -half, new ComplexD(0.0, -1.0).asinh());
        // an unbounded imaginary part carries the real part with it
        same("asinh(0,inf)", INF, half, new ComplexD(0.0, INF).asinh());
        same("asinh(-0,inf)", -INF, half, new ComplexD(-0.0, INF).asinh());
        same("asinh(1,-inf)", INF, -half, new ComplexD(1.0, -INF).asinh());
        same("asinh(inf,inf)", INF, quarter, ComplexD.Inf().asinh());
        same("asinh(inf,-inf)", INF, -quarter, new ComplexD(INF, -INF).asinh());
        // an unbounded real part swallows the angle
        same("asinh(inf,0)", INF, 0.0, new ComplexD(INF, 0.0).asinh());
        same("asinh(inf,-0)", INF, -0.0, new ComplexD(INF, -0.0).asinh());
        same("asinh(inf,1)", INF, 0.0, new ComplexD(INF, 1.0).asinh());
        same("asinh(-inf,-1)", -INF, -0.0, new ComplexD(-INF, -1.0).asinh());
        same("asinh(inf,NaN)", INF, NAN, new ComplexD(INF, NAN).asinh());
        // an exact zero in the imaginary part survives a NaN real part, because
        // asinh is real on the real axis; the other way round it does not
        same("asinh(NaN,0)", NAN, 0.0, new ComplexD(NAN, 0.0).asinh());
        same("asinh(NaN,-0)", NAN, -0.0, new ComplexD(NAN, -0.0).asinh());
        same("asinh(0,NaN)", NAN, NAN, new ComplexD(0.0, NAN).asinh());
        same("asinh(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).asinh());
        same("asinh(NaN,inf)", INF, NAN, new ComplexD(NAN, INF).asinh());
        same("asinh(NaN,NaN)", NAN, NAN, ComplexD.NaN().asinh());
        // asin on its cut, where the sign of the zero decides the side
        same("asin(1,0)", half, 0.0, ComplexD.One().asin());
        same("asin(-1,0)", -half, 0.0, new ComplexD(-1.0, 0.0).asin());
        same("asin(2,0)", half, 1.3169578969248166, new ComplexD(2.0, 0.0).asin());
        same("asin(2,-0)", half, -1.3169578969248166, new ComplexD(2.0, -0.0).asin());
        same("asin(0,1)", 0.0, 0.881373587019543, ComplexD.I().asin());
        same("asin(inf,0)", half, INF, new ComplexD(INF, 0.0).asin());
        same("asin(inf,inf)", quarter, INF, ComplexD.Inf().asin());
        same("asin(0,NaN)", 0.0, NAN, new ComplexD(0.0, NAN).asin());
    }

    // ---------- the inverse cosine pair ----------

    @Test
    public void testAcosRoundTripThroughCos() {
        // only from modulus one up: acos runs into PI/2 near the origin and cos
        // is flat there, so the way back loses everything - measured 1.4e-8 at
        // 1e-8. Above one it is 2.3e-16 at one and 7.9e-14 at 1e300.
        for (int e = 0; e <= MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            double tol = 1.0e-15 * Math.max(1.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                close("cos(acos(z))" + at(v.re(), v.im()), v, v.acos().cos(), tol);
                close("cosh(acosh(z))" + at(v.re(), v.im()), v, v.acosh().cosh(), tol);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 2000);
    }

    @Test
    public void testAcosAndAsinAddToHalfPi() {
        // the near field oracle, where the way back through cos is useless.
        // asin is pinned down by the tests above.
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD s = v.acos().add(v.asin());
                double err = Math.hypot(s.re() - Math.PI / 2.0, s.im());
                assertTrue("acos + asin" + at(v.re(), v.im()) + " is " + s, err <= 1.0e-15);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 4000);
    }

    @Test
    public void testAcosFarOut() {
        // far out acos(z) is atan2(|y|, x) - i*ln(2|z|); the real part is even
        // in y because acos of a conjugate is the conjugate of acos
        for (int e = 20; e <= MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double x = r * Math.cos(angle(k));
                double y = r * Math.sin(angle(k));
                ComplexD got = new ComplexD(x, y).acos();
                double wantRe = Math.atan2(Math.abs(y), x);
                double wantIm = -Math.copySign(0.6931471805599453
                        + Math.log(new ComplexD(x, y).abs()), y);
                assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                        Math.abs(got.re() - wantRe) <= 1.0e-15);
                assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                        Math.abs(got.im() - wantIm) <= 1.0e-15 * Math.abs(wantIm));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 2000);
    }

    @Test
    public void testAcosAtTheTopOfTheRange() {
        // above 1e307 sqrt itself gives out - Math.abs(re) + abs() overflows
        // from a modulus of 9e307 - so only the far field branch can answer
        double big = 1.0e308;
        double[][] points = { { big, big }, { -big, big }, { big, -big }, { -big, -big },
                { Double.MAX_VALUE, 0.0 }, { 0.0, big }, { big, 1.0 }, { 1.0, big } };
        for (int i = 0; i < points.length; ++i) {
            double x = points[i][0];
            double y = points[i][1];
            ComplexD got = new ComplexD(x, y).acos();
            double wantRe = Math.atan2(Math.abs(y), x);
            double wantIm = -Math.copySign(0.6931471805599453
                    + Math.log(new ComplexD(x, y).abs()), y);
            assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                    Math.abs(got.re() - wantRe) <= 1.0e-15);
            assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                    Math.abs(got.im() - wantIm) <= 1.0e-15 * Math.abs(wantIm));
            ComplexD h = new ComplexD(x, y).acosh();
            assertTrue("Re(acosh) must not be negative" + at(x, y) + ": " + h.re(), h.re() >= 0.0);
        }
    }

    @Test
    public void testAcosOnTheRealAxis() {
        // Math.acos has nothing in common with the code under test
        for (int k = -100; k <= 100; ++k) {
            double x = k / 100.0;
            ComplexD got = new ComplexD(x, 0.0).acos();
            assertTrue("acos(" + x + "): want " + Math.acos(x) + ", got " + got.re(),
                    Math.abs(got.re() - Math.acos(x)) <= 1.0e-15);
            same("acos(" + x + ") stays real", -0.0, got.im());
        }
        // past one the value leaves the real axis, and acosh is the real one
        for (int k = 11; k <= 400; ++k) {
            double x = k / 10.0;
            double want = Math.log(x + Math.sqrt(x * x - 1.0));
            ComplexD got = new ComplexD(x, 0.0).acos();
            assertTrue("Im(acos(" + x + ")): want " + (-want) + ", got " + got.im(),
                    Math.abs(got.im() + want) <= 1.0e-15 * want);
            same("acos(" + x + ") real part", 0.0, got.re());
            ComplexD h = new ComplexD(x, 0.0).acosh();
            assertTrue("acosh(" + x + "): want " + want + ", got " + h.re(),
                    Math.abs(h.re() - want) <= 1.0e-15 * want);
            same("acosh(" + x + ") stays real", 0.0, h.im());
        }
    }

    @Test
    public void testAcosAtTheBranchPoint() {
        // the expected values come from 120 digit arithmetic; pi/2 - asin(z) is
        // 2.3e6 ulp out at 1 - 1e-13 and the plain logarithm is worse still
        acosBranchPointReal(1.0e-4, 0.014142253477512098811);
        acosBranchPointReal(1.0e-8, 1.4142135671046477153e-4);
        acosBranchPointReal(1.0e-14, 1.4136482746161737882e-7);
        acosBranchPoint(1.0e-4, 0.0099999166647917227117, -0.010000083331458277771);
        acosBranchPoint(1.0e-8, 9.9999999916666667525e-5, -1.0000000008333333419e-4);
        acosBranchPoint(1.0e-14, 9.9999999999999916608e-8, -1.0000000000000008327e-7);
    }

    private void acosBranchPointReal(double d, double wantRe) {
        double got = new ComplexD(1.0 - d, 0.0).acos().re();
        assertTrue("acos(1 - " + d + "): want " + wantRe + ", got " + got,
                Math.abs(got - wantRe) <= 1.0e-15 * wantRe);
    }

    private void acosBranchPoint(double d, double wantRe, double wantIm) {
        ComplexD got = new ComplexD(1.0, d).acos();
        assertTrue("acos(1 + " + d + "i) re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-15 * wantRe);
        assertTrue("acos(1 + " + d + "i) im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-15 * Math.abs(wantIm));
    }

    @Test
    public void testAcosAndAcoshAreConjugateSymmetric() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 3) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                ComplexD c = v.conj();
                same("acos conj re", v.acos().conj().re(), c.acos().re());
                same("acos conj im", v.acos().conj().im(), c.acos().im());
                same("acosh conj re", v.acosh().conj().re(), c.acosh().re());
                same("acosh conj im", v.acosh().conj().im(), c.acosh().im());
            }
        }
    }

    @Test
    public void testTheRangeOfTheInverseCosine() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexD v = new ComplexD(r * Math.cos(angle(k)), r * Math.sin(angle(k)));
                double re = v.acos().re();
                assertTrue("Re(acos) out of range: " + re, re >= 0.0 && re <= Math.PI);
                double h = v.acosh().re();
                assertTrue("Re(acosh) must not be negative: " + h, h >= 0.0);
            }
        }
    }

    @Test
    public void testTheInverseCosinePairAtTheEdges() {
        double half = Math.PI / 2.0;
        double quarter = Math.PI / 4.0;
        double three = 3.0 * Math.PI / 4.0;
        // the sign of the zero decides the side of the cut
        same("acos(0,0)", half, -0.0, ComplexD.Zero().acos());
        same("acos(0,-0)", half, 0.0, new ComplexD(0.0, -0.0).acos());
        same("acos(-0,0)", half, -0.0, new ComplexD(-0.0, 0.0).acos());
        same("acos(1,0)", 0.0, -0.0, ComplexD.One().acos());
        same("acos(1,-0)", 0.0, 0.0, new ComplexD(1.0, -0.0).acos());
        same("acos(-1,0)", Math.PI, -0.0, new ComplexD(-1.0, 0.0).acos());
        same("acos(2,0)", 0.0, -1.3169578969248166, new ComplexD(2.0, 0.0).acos());
        same("acos(2,-0)", 0.0, 1.3169578969248166, new ComplexD(2.0, -0.0).acos());
        same("acos(-2,0)", Math.PI, -1.3169578969248166, new ComplexD(-2.0, 0.0).acos());
        same("acos(0,1)", half, -0.881373587019543, ComplexD.I().acos());
        // the angle saturates, the modulus grows without bound
        same("acos(0,inf)", half, -INF, new ComplexD(0.0, INF).acos());
        same("acos(0,-inf)", half, INF, new ComplexD(0.0, -INF).acos());
        same("acos(inf,0)", 0.0, -INF, new ComplexD(INF, 0.0).acos());
        same("acos(inf,-0)", 0.0, INF, new ComplexD(INF, -0.0).acos());
        same("acos(-inf,0)", Math.PI, -INF, new ComplexD(-INF, 0.0).acos());
        same("acos(inf,inf)", quarter, -INF, ComplexD.Inf().acos());
        same("acos(-inf,inf)", three, -INF, new ComplexD(-INF, INF).acos());
        same("acos(inf,-inf)", quarter, INF, new ComplexD(INF, -INF).acos());
        same("acos(inf,NaN)", NAN, -INF, new ComplexD(INF, NAN).acos());
        same("acos(NaN,inf)", NAN, -INF, new ComplexD(NAN, INF).acos());
        same("acos(NaN,-inf)", NAN, INF, new ComplexD(NAN, -INF).acos());
        // acos carries the imaginary axis onto itself, so PI/2 survives a NaN there
        same("acos(0,NaN)", half, NAN, new ComplexD(0.0, NAN).acos());
        same("acos(-0,NaN)", half, NAN, new ComplexD(-0.0, NAN).acos());
        same("acos(NaN,0)", NAN, NAN, new ComplexD(NAN, 0.0).acos());
        same("acos(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).acos());
        same("acos(NaN,NaN)", NAN, NAN, ComplexD.NaN().acos());
        // and the quarter turn of all of that
        same("acosh(0,0)", 0.0, half, ComplexD.Zero().acosh());
        same("acosh(0,-0)", 0.0, -half, new ComplexD(0.0, -0.0).acosh());
        same("acosh(1,0)", 0.0, 0.0, ComplexD.One().acosh());
        same("acosh(1,-0)", 0.0, -0.0, new ComplexD(1.0, -0.0).acosh());
        same("acosh(-1,0)", 0.0, Math.PI, new ComplexD(-1.0, 0.0).acosh());
        same("acosh(-1,-0)", 0.0, -Math.PI, new ComplexD(-1.0, -0.0).acosh());
        same("acosh(2,0)", 1.3169578969248166, 0.0, new ComplexD(2.0, 0.0).acosh());
        same("acosh(2,-0)", 1.3169578969248166, -0.0, new ComplexD(2.0, -0.0).acosh());
        same("acosh(-2,0)", 1.3169578969248166, Math.PI, new ComplexD(-2.0, 0.0).acosh());
        same("acosh(0,inf)", INF, half, new ComplexD(0.0, INF).acosh());
        same("acosh(0,-inf)", INF, -half, new ComplexD(0.0, -INF).acosh());
        same("acosh(inf,0)", INF, 0.0, new ComplexD(INF, 0.0).acosh());
        same("acosh(inf,-0)", INF, -0.0, new ComplexD(INF, -0.0).acosh());
        same("acosh(-inf,0)", INF, Math.PI, new ComplexD(-INF, 0.0).acosh());
        same("acosh(inf,inf)", INF, quarter, ComplexD.Inf().acosh());
        same("acosh(-inf,inf)", INF, three, new ComplexD(-INF, INF).acosh());
        same("acosh(inf,NaN)", INF, NAN, new ComplexD(INF, NAN).acosh());
        same("acosh(NaN,inf)", INF, NAN, new ComplexD(NAN, INF).acosh());
        // here the two part company: acos keeps PI/2, acosh does not
        same("acosh(0,NaN)", NAN, NAN, new ComplexD(0.0, NAN).acosh());
        same("acosh(NaN,0)", NAN, NAN, new ComplexD(NAN, 0.0).acosh());
        same("acosh(NaN,NaN)", NAN, NAN, ComplexD.NaN().acosh());
    }

    // ---------- the two ends of the range ----------

    /** the sqrt tests reach further out than the shared ensemble, which stops at 1e300 */
    private static final int SQRT_MIN_EXP = -323;
    private static final int SQRT_MAX_EXP = 308;

    @Test
    public void testSqrtOnTheRealAxisIsMathSqrt() {
        // on the axis the modulus is |x|, so t is Math.sqrt(x) and must agree
        // to the last bit; Math.sqrt has nothing in common with the code
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            for (double m : new double[] { 1.0, 2.5, 7.3 }) {
                double x = m * Math.pow(10.0, e);
                if (!isFinite(x) || x == 0.0) {
                    continue;
                }
                same("sqrt(" + x + ")", Math.sqrt(x), new ComplexD(x, 0.0).sqrt().re());
                same("sqrt(-" + x + ")", Math.sqrt(x), new ComplexD(-x, 0.0).sqrt().im());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 1800);
    }

    @Test
    public void testSqrtStaysFiniteAtBothEnds() {
        // the sum |re| + |z| overflows above a modulus of 9e307 and turns
        // subnormal below 2e-308; both ends must still come out of the range
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double x = r * Math.cos(angle(k));
                double y = r * Math.sin(angle(k));
                if (!isFinite(x) || !isFinite(y) || (x == 0.0 && y == 0.0)) {
                    continue;
                }
                ComplexD s = new ComplexD(x, y).sqrt();
                assertTrue("sqrt" + at(x, y) + " is " + s, isFinite(s.re()) && isFinite(s.im()));
                assertTrue("real part" + at(x, y) + " must not be negative", s.re() >= 0.0);
                assertTrue("the root of " + at(x, y) + " must not be zero",
                        s.re() != 0.0 || s.im() != 0.0);
            }
        }
        same("sqrt(9e307)", 9.486832980505138E153, new ComplexD(9.0e307, 0.0).sqrt().re());
        same("sqrt(MAX_VALUE)", Math.sqrt(Double.MAX_VALUE),
                new ComplexD(Double.MAX_VALUE, 0.0).sqrt().re());
    }

    @Test
    public void testSqrtInTheSubnormalBand() {
        // the expected values come from 120 digit arithmetic, because squaring
        // the root back underflows down here
        subnormalRoot(1.0e-320, 1.0e-320, 1.0986779977260263990E-160, 4.5508732733903661602E-161);
        subnormalRoot(1.0e-315, -2.0e-316, 3.1778954514264884631E-158, -3.1467366489798094928E-159);
        subnormalRoot(-1.0e-310, 3.0e-311, 1.4837562281427940884E-156, 1.0109477362581718829E-155);
    }

    private void subnormalRoot(double x, double y, double wantRe, double wantIm) {
        ComplexD got = new ComplexD(x, y).sqrt();
        assertTrue("sqrt" + at(x, y) + " re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-15 * Math.abs(wantRe));
        assertTrue("sqrt" + at(x, y) + " im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-15 * Math.abs(wantIm));
    }

    @Test
    public void testSquareRootSquaredIsTheOriginalAtBothEnds() {
        // the same exact squaring as above, but out to where the sum |re| + |z|
        // used to overflow and to where it used to turn subnormal
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                double a = r * Math.cos(angle(k));
                double b = r * Math.sin(angle(k));
                if (!isFinite(a) || !isFinite(b) || (a == 0.0 && b == 0.0)) {
                    continue;
                }
                ComplexD root = new ComplexD(a, b).sqrt();
                BigDecimal x = big(root.re());
                BigDecimal y = big(root.im());
                BigDecimal gotRe = x.multiply(x, MC).subtract(y.multiply(y, MC), MC);
                BigDecimal gotIm = x.multiply(y, MC).multiply(big(2.0), MC);
                BigDecimal mod2 = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                BigDecimal errRe = gotRe.subtract(big(a), MC);
                BigDecimal errIm = gotIm.subtract(big(b), MC);
                BigDecimal err2 = errRe.multiply(errRe, MC).add(errIm.multiply(errIm, MC), MC);
                BigDecimal bound = mod2.multiply(big(1.0e-15).multiply(big(1.0e-15), MC), MC);
                assertTrue("sqrt" + at(a, b) + " squared is " + gotRe.round(MC) + ", " + gotIm.round(MC),
                        err2.compareTo(bound) <= 0);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 4000);
    }

    @Test
    public void testSqrtWithVeryUnequalComponents() {
        // where |b| is far below a > 0 the root is (sqrt(a), b/(2*sqrt(a))),
        // and Math.sqrt says so to the last bit. Scaling the argument by more
        // than a factor of four would push b/(2t) into the subnormal range and
        // lose it here, which the ensemble above never reaches.
        for (int ea = -300; ea <= SQRT_MAX_EXP; ++ea) {
            double a = 1.7 * Math.pow(10.0, ea);
            if (!isFinite(a)) {
                continue;
            }
            for (int d = 20; d <= 620; d += 20) {
                double b = 3.1 * Math.pow(10.0, ea - d);
                double wantIm = b / (2.0 * Math.sqrt(a));
                if (b == 0.0 || wantIm == 0.0) {
                    continue;
                }
                same("sqrt" + at(a, b) + " re", Math.sqrt(a), new ComplexD(a, b).sqrt().re());
                double gotIm = new ComplexD(a, b).sqrt().im();
                // one ulp of a subnormal is all the factor of four may cost here
                assertTrue("sqrt" + at(a, b) + " im: want " + wantIm + ", got " + gotIm,
                        Math.abs(gotIm - wantIm) <= 1.0e-13 * Math.abs(wantIm));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 8000);
    }

    // ================= 3. the full matrix, as digests =================

    private static long fold(long h, long bits) {
        h = (h ^ bits) * 0x100000001B3L;
        return h ^ (h >>> 29);
    }

    private static long fold(long h, double x) {
        return fold(h, Double.doubleToLongBits(x));
    }

    private static long fold(long h, ComplexD v) {
        return fold(fold(h, v.re()), v.im());
    }

    private static final long SEED = 0xCBF29CE484222325L;

    private static ComplexD apply(String op, ComplexD a, ComplexD b) {
        if (op.equals("mul")) {
            return a.mul(b);
        } else if (op.equals("div")) {
            return a.div(b);
        } else if (op.equals("pow")) {
            return a.pow(b);
        } else if (op.equals("add")) {
            return a.add(b);
        }
        return a.sub(b);
    }

    /** the offending row, spelled out, so the change can be seen */
    private void report(String op, int i, boolean binary) {
        StringBuilder b = new StringBuilder();
        b.append(op).append(" changed for").append(at(SPECIAL[i][0], SPECIAL[i][1])).append(":");
        int n = binary ? SPECIAL.length : SCALARS.length;
        for (int j = 0; j < n; ++j) {
            ComplexD r = binary ? apply(op, z(i), z(j))
                    : (op.equals("scale") ? z(i).scale(SCALARS[j]) : z(i).pow(SCALARS[j]));
            b.append("\n  with ").append(binary ? at(SPECIAL[j][0], SPECIAL[j][1]) : " " + SCALARS[j])
                    .append(" -> (").append(r.re()).append(", ").append(r.im()).append(")");
        }
        fail(b.toString());
    }

    private void digest(String op, long[] want, long[] got, boolean binary) {
        for (int i = 0; i < want.length; ++i) {
            if (want[i] != got[i]) {
                report(op, i, binary);
            }
        }
    }

    private long[] binary(String op) {
        long[] out = new long[SPECIAL.length];
        for (int i = 0; i < SPECIAL.length; ++i) {
            long h = SEED;
            for (int j = 0; j < SPECIAL.length; ++j) {
                h = fold(h, apply(op, z(i), z(j)));
            }
            out[i] = h;
        }
        return out;
    }

    private long[] scalar(String op) {
        long[] out = new long[SPECIAL.length];
        for (int i = 0; i < SPECIAL.length; ++i) {
            long h = SEED;
            for (double x : SCALARS) {
                h = fold(h, op.equals("scale") ? z(i).scale(x) : z(i).pow(x));
            }
            out[i] = h;
        }
        return out;
    }

    private long unary(String op) {
        long h = SEED;
        for (int i = 0; i < SPECIAL.length; ++i) {
            ComplexD v = z(i);
            if (op.equals("inv")) {
                h = fold(h, v.inv());
            } else if (op.equals("ln")) {
                h = fold(h, v.ln());
            } else if (op.equals("exp")) {
                h = fold(h, v.exp());
            } else if (op.equals("sqrt")) {
                h = fold(h, v.sqrt());
            } else if (op.equals("conj")) {
                h = fold(h, v.conj());
            } else if (op.equals("sinh")) {
                h = fold(h, v.sinh());
            } else if (op.equals("cosh")) {
                h = fold(h, v.cosh());
            } else if (op.equals("sin")) {
                h = fold(h, v.sin());
            } else if (op.equals("cos")) {
                h = fold(h, v.cos());
            } else if (op.equals("tanh")) {
                h = fold(h, v.tanh());
            } else if (op.equals("tan")) {
                h = fold(h, v.tan());
            } else if (op.equals("atanh")) {
                h = fold(h, v.atanh());
            } else if (op.equals("atan")) {
                h = fold(h, v.atan());
            } else if (op.equals("asinh")) {
                h = fold(h, v.asinh());
            } else if (op.equals("asin")) {
                h = fold(h, v.asin());
            } else if (op.equals("acos")) {
                h = fold(h, v.acos());
            } else if (op.equals("acosh")) {
                h = fold(h, v.acosh());
            } else if (op.equals("cot")) {
                h = fold(h, v.cot());
            } else if (op.equals("coth")) {
                h = fold(h, v.coth());
            } else if (op.equals("acot")) {
                h = fold(h, v.acot());
            } else if (op.equals("acoth")) {
                h = fold(h, v.acoth());
            } else if (op.equals("sec")) {
                h = fold(h, v.sec());
            } else if (op.equals("csc")) {
                h = fold(h, v.csc());
            } else if (op.equals("sech")) {
                h = fold(h, v.sech());
            } else if (op.equals("csch")) {
                h = fold(h, v.csch());
            } else if (op.equals("asec")) {
                h = fold(h, v.asec());
            } else if (op.equals("asech")) {
                h = fold(h, v.asech());
            } else if (op.equals("acsc")) {
                h = fold(h, v.acsc());
            } else if (op.equals("acsch")) {
                h = fold(h, v.acsch());
            } else if (op.equals("sinc")) {
                h = fold(h, v.sinc());
            } else if (op.equals("sinhc")) {
                h = fold(h, v.sinhc());
            } else if (op.equals("neg")) {
                h = fold(h, v.neg());
            } else if (op.equals("abs")) {
                h = fold(h, v.abs());
            } else if (op.equals("arg")) {
                h = fold(h, v.arg());
            } else if (op.equals("hash")) {
                h = fold(h, (long) v.hashCode());
            } else {
                h = fold(h, (long) v.toString().hashCode());
            }
        }
        return h;
    }

    /** the roots over the special values, for degrees one through five */
    private long rootDigest(boolean all) {
        long h = SEED;
        for (int i = 0; i < SPECIAL.length; ++i) {
            for (int n = 1; n <= 5; ++n) {
                if (all) {
                    ComplexD[] roots = z(i).nthRoots(n);
                    for (int j = 0; j < roots.length; ++j) {
                        h = fold(h, roots[j]);
                    }
                } else {
                    h = fold(h, z(i).nthRoot(n));
                }
            }
        }
        return h;
    }

    @Test
    public void testTheFullMatrixOfRoots() {
        assertEquals("nthRoot changed", NTHROOT_DIGEST, rootDigest(false));
        assertEquals("nthRoots changed", NTHROOTS_DIGEST, rootDigest(true));
    }

    @Test
    public void testTheFullMatrixOfBinaryResults() {
        digest("mul", MUL_DIGEST, binary("mul"), true);
        digest("div", DIV_DIGEST, binary("div"), true);
        digest("pow", POW_DIGEST, binary("pow"), true);
        digest("add", ADD_DIGEST, binary("add"), true);
        digest("sub", SUB_DIGEST, binary("sub"), true);
    }

    @Test
    public void testTheFullMatrixOfScalarResults() {
        digest("scale", SCALE_DIGEST, scalar("scale"), false);
        digest("powr", POWR_DIGEST, scalar("pow"), false);
    }

    @Test
    public void testTheFullMatrixOfUnaryResults() {
        assertEquals("inv changed", INV_DIGEST, unary("inv"));
        assertEquals("ln changed", LN_DIGEST, unary("ln"));
        assertEquals("exp changed", EXP_DIGEST, unary("exp"));
        assertEquals("sqrt changed", SQRT_DIGEST, unary("sqrt"));
        assertEquals("sinh changed", SINH_DIGEST, unary("sinh"));
        assertEquals("cosh changed", COSH_DIGEST, unary("cosh"));
        assertEquals("sin changed", SIN_DIGEST, unary("sin"));
        assertEquals("cos changed", COS_DIGEST, unary("cos"));
        assertEquals("tanh changed", TANH_DIGEST, unary("tanh"));
        assertEquals("tan changed", TAN_DIGEST, unary("tan"));
        assertEquals("atanh changed", ATANH_DIGEST, unary("atanh"));
        assertEquals("atan changed", ATAN_DIGEST, unary("atan"));
        assertEquals("asinh changed", ASINH_DIGEST, unary("asinh"));
        assertEquals("asin changed", ASIN_DIGEST, unary("asin"));
        assertEquals("acos changed", ACOS_DIGEST, unary("acos"));
        assertEquals("acosh changed", ACOSH_DIGEST, unary("acosh"));
        assertEquals("cot changed", COT_DIGEST, unary("cot"));
        assertEquals("coth changed", COTH_DIGEST, unary("coth"));
        assertEquals("acot changed", ACOT_DIGEST, unary("acot"));
        assertEquals("acoth changed", ACOTH_DIGEST, unary("acoth"));
        assertEquals("sec changed", SEC_DIGEST, unary("sec"));
        assertEquals("csc changed", CSC_DIGEST, unary("csc"));
        assertEquals("sech changed", SECH_DIGEST, unary("sech"));
        assertEquals("csch changed", CSCH_DIGEST, unary("csch"));
        assertEquals("asec changed", ASEC_DIGEST, unary("asec"));
        assertEquals("asech changed", ASECH_DIGEST, unary("asech"));
        assertEquals("acsc changed", ACSC_DIGEST, unary("acsc"));
        assertEquals("acsch changed", ACSCH_DIGEST, unary("acsch"));
        assertEquals("sinc changed", SINC_DIGEST, unary("sinc"));
        assertEquals("sinhc changed", SINHC_DIGEST, unary("sinhc"));
        assertEquals("conj changed", CONJ_DIGEST, unary("conj"));
        assertEquals("neg changed", NEG_DIGEST, unary("neg"));
        assertEquals("abs changed", ABS_DIGEST, unary("abs"));
        assertEquals("arg changed", ARG_DIGEST, unary("arg"));
        assertEquals("hashCode changed", HASH_DIGEST, unary("hash"));
        assertEquals("toString changed", STRING_DIGEST, unary("string"));
    }

    // ================= 4. the object contract =================

    @Test
    public void testEquals() {
        ComplexD a = new ComplexD(1.0, 2.0);
        assertTrue("reflexive", a.equals(a));
        assertTrue("equal values", a.equals(new ComplexD(1.0, 2.0)));
        assertTrue("symmetric", new ComplexD(1.0, 2.0).equals(a));
        assertFalse("different real part", a.equals(new ComplexD(1.5, 2.0)));
        assertFalse("different imaginary part", a.equals(new ComplexD(1.0, 2.5)));
        assertFalse("null", a.equals(null));
        assertFalse("a foreign class", a.equals("1+2i"));
        // a NaN component says nothing about the other one
        assertFalse("NaN class", new ComplexD(NAN, 1.0).equals(ComplexD.NaN()));
        assertFalse("NaN class", ComplexD.NaN().equals(new ComplexD(1.0, NAN)));
        assertFalse("NaN against a number", a.equals(ComplexD.NaN()));
        // the two zeros are told apart, because the branch cuts tell them apart
        assertFalse("signed zero", ComplexD.Zero().equals(new ComplexD(-0.0, -0.0)));
    }

    @Test
    public void testHashCodeFollowsEquals() {
        assertTrue("the two zeros", ComplexD.Zero().hashCode() != new ComplexD(-0.0, -0.0).hashCode());
        assertTrue("the two zeros", new ComplexD(0.0, -0.0).hashCode() != new ComplexD(-0.0, 0.0).hashCode());
        assertTrue("every NaN", ComplexD.NaN().hashCode() != new ComplexD(NAN, 1.0).hashCode());
        assertTrue("every NaN", new ComplexD(1.0, NAN).hashCode() != new ComplexD(NAN, 0.0).hashCode());
        ComplexD a = new ComplexD(3.0, 4.0);
        assertEquals("stable", a.hashCode(), a.hashCode());
        assertEquals("equal values", a.hashCode(), new ComplexD(3.0, 4.0).hashCode());
        // and it still separates ordinary values
        assertTrue("(3,4) against (4,3)", a.hashCode() != new ComplexD(4.0, 3.0).hashCode());
        assertTrue("(3,4) against (3,5)", a.hashCode() != new ComplexD(3.0, 5.0).hashCode());
    }

    @Test
    public void testTheFourZerosAreFourValues() {
        // the branch cuts read the sign of a zero, so equals does too
        ComplexD[] zeros = { new ComplexD(0.0, 0.0), new ComplexD(-0.0, -0.0),
                new ComplexD(0.0, -0.0), new ComplexD(-0.0, 0.0) };
        for (int i = 0; i < zeros.length; ++i) {
            for (int j = i + 1; j < zeros.length; ++j) {
                assertFalse("zero " + i + " equals zero " + j, zeros[i].equals(zeros[j]));
                assertTrue("zero " + i + " and " + j + " hashed alike",
                        zeros[i].hashCode() != zeros[j].hashCode());
            }
        }
        // and sqrt is one of the five that tell them apart
        same("sqrt(-4,+0)", 0.0, 2.0, new ComplexD(-4.0, 0.0).sqrt());
        same("sqrt(-4,-0)", 0.0, -2.0, new ComplexD(-4.0, -0.0).sqrt());
    }

    @Test
    public void testHashCodeSpreadsARegularGrid() {
        // the mixing this replaced folded a regular grid onto 3.9 percent of
        // its values, and a regular grid is what numerical code produces
        java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
        int n = 0;
        for (int i = -160; i <= 160; ++i) {
            for (int j = -160; j <= 160; ++j) {
                ++n;
                seen.add(new ComplexD(i * 0.25, j * 0.25).hashCode());
            }
        }
        assertTrue(seen.size() + " distinct hashes for " + n + " values", seen.size() > 0.99 * n);
    }

    @Test
    public void testTheEqualsContract() {
        double[] vals = { 0.0, -0.0, 1.0, -1.0, 2.5, INF, -INF, NAN, 4.9e-324, 1.0e300 };
        ComplexD[] zs = new ComplexD[vals.length * vals.length];
        for (int i = 0; i < vals.length; ++i) {
            for (int j = 0; j < vals.length; ++j) {
                zs[i * vals.length + j] = new ComplexD(vals[i], vals[j]);
            }
        }
        for (int i = 0; i < zs.length; ++i) {
            assertTrue("reflexive: " + zs[i], zs[i].equals(zs[i]));
            assertFalse("null: " + zs[i], zs[i].equals(null));
            assertFalse("a foreign class: " + zs[i], zs[i].equals("z"));
            for (int j = 0; j < zs.length; ++j) {
                assertEquals("symmetric: " + zs[i] + " / " + zs[j],
                        zs[i].equals(zs[j]), zs[j].equals(zs[i]));
                if (zs[i].equals(zs[j])) {
                    assertEquals("hashCode: " + zs[i] + " / " + zs[j],
                            zs[i].hashCode(), zs[j].hashCode());
                }
            }
        }
        for (int i = 0; i < zs.length; ++i) {
            for (int j = 0; j < zs.length; ++j) {
                if (!zs[i].equals(zs[j])) {
                    continue;
                }
                for (int k = 0; k < zs.length; ++k) {
                    if (zs[j].equals(zs[k])) {
                        assertTrue("transitive: " + zs[i] + " / " + zs[j] + " / " + zs[k],
                                zs[i].equals(zs[k]));
                    }
                }
            }
        }
    }

    @Test
    public void testEveryNanBitPatternIsOneValue() {
        // doubleToLongBits canonicalizes them, and that part of the old
        // convention stays; doubleToRawLongBits would break it quietly
        ComplexD odd = new ComplexD(Double.longBitsToDouble(0x7ff8000000000001L), 1.0);
        ComplexD plain = new ComplexD(NAN, 1.0);
        assertTrue("two NaN bit patterns", odd.equals(plain));
        assertEquals("two NaN bit patterns hashed apart", odd.hashCode(), plain.hashCode());
        ComplexD odd2 = new ComplexD(2.0, Double.longBitsToDouble(0xfff8000000000003L));
        ComplexD plain2 = new ComplexD(2.0, NAN);
        assertTrue("a NaN with the sign bit set", odd2.equals(plain2));
        assertEquals("hashed apart", odd2.hashCode(), plain2.hashCode());
    }

    @Test
    public void testEqualsIsACongruence() {
        // equal now means the same bits, so no operation can tell two equal
        // values apart. The NaN class this replaced had 128 pairs that could.
        double[][] pts = { { NAN, 0.0 }, { NAN, -0.0 }, { 0.0, NAN }, { -0.0, NAN }, { NAN, -5.0 },
                { -5.0, NAN }, { INF, NAN }, { NAN, INF }, { 1.0, NAN }, { NAN, 1.0 }, { NAN, NAN },
                { -INF, NAN }, { NAN, -INF }, { 0.0, 0.0 }, { -0.0, -0.0 }, { 0.0, -0.0 } };
        String[] ops = { "exp", "ln", "sqrt", "sinh", "cosh", "tanh", "asinh", "asin", "acos",
                "acosh", "atanh", "atan", "cot", "coth", "acot", "acoth", "sec", "csc", "sech", "csch", "asec", "asech", "acsc", "acsch", "sinc", "sinhc", "inv", "conj",
                "neg" };
        for (int o = 0; o < ops.length; ++o) {
            for (int i = 0; i < pts.length; ++i) {
                for (int j = 0; j < pts.length; ++j) {
                    ComplexD u = new ComplexD(pts[i][0], pts[i][1]);
                    ComplexD v = new ComplexD(pts[j][0], pts[j][1]);
                    if (!u.equals(v)) {
                        continue;
                    }
                    assertTrue(ops[o] + " of two equal values differs: " + u + " / " + v,
                            unary(u, ops[o]).equals(unary(v, ops[o])));
                }
            }
        }
    }

    private static ComplexD unary(ComplexD z, String op) {
        if (op.equals("exp")) {
            return z.exp();
        } else if (op.equals("ln")) {
            return z.ln();
        } else if (op.equals("sqrt")) {
            return z.sqrt();
        } else if (op.equals("sinh")) {
            return z.sinh();
        } else if (op.equals("cosh")) {
            return z.cosh();
        } else if (op.equals("tanh")) {
            return z.tanh();
        } else if (op.equals("asinh")) {
            return z.asinh();
        } else if (op.equals("asin")) {
            return z.asin();
        } else if (op.equals("acos")) {
            return z.acos();
        } else if (op.equals("acosh")) {
            return z.acosh();
        } else if (op.equals("atanh")) {
            return z.atanh();
        } else if (op.equals("cot")) {
            return z.cot();
        } else if (op.equals("coth")) {
            return z.coth();
        } else if (op.equals("acot")) {
            return z.acot();
        } else if (op.equals("acoth")) {
            return z.acoth();
        } else if (op.equals("sec")) {
            return z.sec();
        } else if (op.equals("csc")) {
            return z.csc();
        } else if (op.equals("sech")) {
            return z.sech();
        } else if (op.equals("csch")) {
            return z.csch();
        } else if (op.equals("asec")) {
            return z.asec();
        } else if (op.equals("asech")) {
            return z.asech();
        } else if (op.equals("acsc")) {
            return z.acsc();
        } else if (op.equals("acsch")) {
            return z.acsch();
        } else if (op.equals("sinc")) {
            return z.sinc();
        } else if (op.equals("sinhc")) {
            return z.sinhc();
        } else if (op.equals("atan")) {
            return z.atan();
        } else if (op.equals("inv")) {
            return z.inv();
        } else if (op.equals("conj")) {
            return z.conj();
        }
        return z.neg();
    }

    @Test
    public void testTheFactoriesShareTheirInstances() {
        assertSame("NaN", ComplexD.NaN(), ComplexD.NaN());
        assertSame("Inf", ComplexD.Inf(), ComplexD.Inf());
        assertSame("Zero", ComplexD.Zero(), ComplexD.Zero());
        assertSame("One", ComplexD.One(), ComplexD.One());
        assertSame("I", ComplexD.I(), ComplexD.I());
        assertSame("copy", ComplexD.One(), ComplexD.One().copy());
        same("NaN", NAN, NAN, ComplexD.NaN());
        same("Inf", INF, INF, ComplexD.Inf());
        same("Zero", 0.0, 0.0, ComplexD.Zero());
        same("One", 1.0, 0.0, ComplexD.One());
        same("I", 0.0, 1.0, ComplexD.I());
    }

    @Test
    public void testNothingCanChangeAValue() {
        ComplexD v = new ComplexD(3.0, 4.0);
        v.add(ComplexD.One());
        v.sub(ComplexD.One());
        v.mul(ComplexD.I());
        v.div(ComplexD.I());
        v.inv();
        v.ln();
        v.exp();
        v.sqrt();
        v.pow(2.0);
        v.pow(ComplexD.I());
        v.scale(7.0);
        v.conj();
        v.neg();
        same("untouched", 3.0, 4.0, v);
    }

    @Test
    public void testToString() {
        assertEquals("+1.5000000000E+00  -2.5000000000E+00i", new ComplexD(1.5, -2.5).toString());
        // the branch cuts read the sign of a zero, so the printout keeps it
        assertEquals("-0.0000000000E+00  -0.0000000000E+00i", new ComplexD(-0.0, -0.0).toString());
        assertEquals("+1.50  -2.50i", new ComplexD(1.5, -2.5).toString("%.2f"));
        assertEquals("-0.00  +0.00i", new ComplexD(-0.0, 0.0).toString("%.2f"));
    }

    @Test
    public void testToStringTellsTheFourZerosApart() {
        // printing both zeros alike had hidden three wrong expectations
        ComplexD[] zeros = { new ComplexD(0.0, 0.0), new ComplexD(-0.0, -0.0),
                new ComplexD(0.0, -0.0), new ComplexD(-0.0, 0.0) };
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < zeros.length; ++i) {
            seen.add(zeros[i].toString());
        }
        assertEquals("four zeros, four printouts", 4, seen.size());
        assertEquals("+0.0000000000E+00  -0.0000000000E+00i", new ComplexD(0.0, -0.0).toString());
        assertEquals("-0.0000000000E+00  +0.0000000000E+00i", new ComplexD(-0.0, 0.0).toString());
        // and sqrt is one of the operations that tell them apart
        assertEquals("+0.0000000000E+00  +2.0000000000E+00i", new ComplexD(-4.0, 0.0).sqrt().toString());
        assertEquals("+0.0000000000E+00  -2.0000000000E+00i", new ComplexD(-4.0, -0.0).sqrt().toString());
    }

    @Test
    public void testToStringLeavesNanAndInfinityAlone() {
        // only the zeros moved; format writes no sign of its own for a NaN
        assertEquals("NAN  NANi", new ComplexD(NAN, NAN).toString());
        assertEquals("a NaN carrying the sign bit", "NAN  NANi",
                new ComplexD(Double.longBitsToDouble(0xfff8000000000000L), NAN).toString());
        assertEquals("+INFINITY  -INFINITYi", new ComplexD(INF, -INF).toString());
        assertEquals("-INFINITY  +INFINITYi", new ComplexD(-INF, INF).toString());
    }

    @Test
    public void testTheClassificationPredicates() {
        StringBuilder real = new StringBuilder();
        StringBuilder nan = new StringBuilder();
        StringBuilder infinite = new StringBuilder();
        for (int i = 0; i < SPECIAL.length; ++i) {
            real.append(z(i).isReal() ? '1' : '0');
            nan.append(z(i).isNan() ? '1' : '0');
            infinite.append(z(i).isInfinite() ? '1' : '0');
        }
        assertEquals("isReal", "11111100000001100000000000", real.toString());
        assertEquals("isNan", "00000000000000000000111111", nan.toString());
        assertEquals("isInfinite", "00000000000001111111000011", infinite.toString());
    }


    @Test
    public void testCotIsTheReciprocalOfTan() {
        // the only thing cot has to be, and inv is exact enough that it is
        for (int i = 0; i < SPECIAL.length; ++i) {
            ComplexD z = z(i);
            if (z.isNan() || z.cot().isNan()) {
                continue;
            }
            ComplexD one = z.cot().mul(z.tan());
            if (one.isNan() || one.isInfinite()) {
                continue;
            }
            assertTrue("cot * tan at " + z + " gives " + one,
                    Math.abs(one.re() - 1.0) <= 2.0 * Math.ulp(1.0)
                            && Math.abs(one.im()) <= 2.0 * Math.ulp(1.0));
        }
        same("cot(1)", 0.6420926159343306, -0.0, new ComplexD(1.0, 0.0).cot());
        same("coth(1)", 1.3130352854993315, -0.0, new ComplexD(1.0, 0.0).coth());
        same("coth(i)", 0.0, -0.6420926159343306, new ComplexD(0.0, 1.0).coth());
    }

    @Test
    public void testCotInTheFarFieldAndAtThePole() {
        // tanh runs into 1 long before the range ends, so cot runs into -i
        same("cot(1+700i)", 0.0, -1.0, new ComplexD(1.0, 700.0).cot());
        same("coth(700+i)", 1.0, -0.0, new ComplexD(700.0, 1.0).coth());
        // the pole is inv(0), the one infinity without a direction
        same("cot(0)", INF, INF, new ComplexD(0.0, 0.0).cot());
        same("coth(0)", INF, INF, new ComplexD(0.0, 0.0).coth());
        same("coth(inf)", 1.0, -0.0, new ComplexD(INF, 0.0).coth());
    }

    @Test
    public void testAcotIsAnalyticAtTheOrigin() {
        // the cut runs along the rays |Im z| >= 1, not through the origin, so
        // all four zeros answer PI/2 and only the zero of the result turns
        same("acot(+0,+0)", Math.PI / 2.0, -0.0, new ComplexD(0.0, 0.0).acot());
        same("acot(-0,+0)", Math.PI / 2.0, -0.0, new ComplexD(-0.0, 0.0).acot());
        same("acot(+0,-0)", Math.PI / 2.0, 0.0, new ComplexD(0.0, -0.0).acot());
        same("acot(-0,-0)", Math.PI / 2.0, 0.0, new ComplexD(-0.0, -0.0).acot());
        // and the neighborhood agrees from every direction
        assertTrue("from +x", Math.abs(new ComplexD(1.0e-9, 0.0).acot().re() - Math.PI / 2.0) < 2.0e-9);
        assertTrue("from -x", Math.abs(new ComplexD(-1.0e-9, 0.0).acot().re() - Math.PI / 2.0) < 2.0e-9);
        assertTrue("from +y", Math.abs(new ComplexD(0.0, 1.0e-9).acot().re() - Math.PI / 2.0) < 2.0e-9);
        assertTrue("from -y", Math.abs(new ComplexD(0.0, -1.0e-9).acot().re() - Math.PI / 2.0) < 2.0e-9);
        // acoth is the same statement turned by i
        same("acoth(+0,+0)", 0.0, -Math.PI / 2.0, new ComplexD(0.0, 0.0).acoth());
        same("acoth(-0,+0)", -0.0, -Math.PI / 2.0, new ComplexD(-0.0, 0.0).acoth());
    }

    @Test
    public void testAcotPutsItsCutOnTheRays() {
        // on the cut the sign of the zero picks the side, a difference of PI
        same("acot(+0+2i)", 0.0, -0.5493061443340549, new ComplexD(0.0, 2.0).acot());
        same("acot(-0+2i)", Math.PI, -0.5493061443340549, new ComplexD(-0.0, 2.0).acot());
        same("acot(+0-2i)", 0.0, 0.5493061443340549, new ComplexD(0.0, -2.0).acot());
        same("acot(-0-2i)", Math.PI, 0.5493061443340549, new ComplexD(-0.0, -2.0).acot());
        // the branch points themselves
        same("acot(i)", Math.PI / 2.0, -INF, new ComplexD(0.0, 1.0).acot());
        same("acot(-i)", Math.PI / 2.0, INF, new ComplexD(0.0, -1.0).acot());
        // inside the unit disc there is no cut at all: walk a circle and the
        // largest step is the sampling
        double worst = 0.0;
        ComplexD prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexD w = new ComplexD(0.9 * Math.cos(ang), 0.9 * Math.sin(ang)).acot();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " inside the unit disc", worst < 0.01);
        // and outside it there is one, of exactly PI
        double up = new ComplexD(1.0e-13, 2.0).acot().re();
        double dn = new ComplexD(-1.0e-13, 2.0).acot().re();
        assertTrue("the ray does not jump by PI: " + (dn - up), Math.abs(dn - up - Math.PI) < 1.0e-12);
    }

    @Test
    public void testAcotIsNotAtanOfTheReciprocal() {
        // the price of the continuous branch: they differ by exactly PI in the
        // left half plane, and agree in the right one
        for (double x : new double[] { -0.5, -1.0, -2.0, -100.0 }) {
            ComplexD z = new ComplexD(x, 0.0);
            double diff = z.acot().re() - z.inv().atan().re();
            assertTrue("at " + x + " the difference is " + diff,
                    Math.abs(diff - Math.PI) < 1.0e-14);
        }
        for (double x : new double[] { 0.5, 1.0, 2.0, 100.0 }) {
            ComplexD z = new ComplexD(x, 0.0);
            assertEquals("at " + x, z.inv().atan().re(), z.acot().re(), 1.0e-15);
        }
    }

    @Test
    public void testAcotFarOutWhereTheSubtractionWouldDie() {
        // PI/2 - atan(z) loses every digit here, which is why the reciprocal
        // form carries the far field; the truth is 1/x - 1/(3x^3)
        for (double x : new double[] { 1.0e5, 1.0e8, 1.0e12, 1.0e15, 1.0e100 }) {
            double want = 1.0 / x - 1.0 / (3.0 * x * x * x);
            double got = new ComplexD(x, 0.0).acot().re();
            assertTrue("acot(" + x + ") = " + got + ", want " + want,
                    Math.abs(got - want) <= 4.0 * Math.ulp(want));
        }
        // and the far end stays finite and on the right branch
        same("acot(inf)", 0.0, 0.0, new ComplexD(INF, 0.0).acot());
        same("acot(-inf)", Math.PI, 0.0, new ComplexD(-INF, 0.0).acot());
        // below 1/MAX_VALUE the reciprocal overflows, and PI/2 is still right
        assertEquals("acot of a subnormal", Math.PI / 2.0, new ComplexD(1.0e-320, 0.0).acot().re(), 0.0);
    }

    @Test
    public void testAcothIsAcotTurnedByI() {
        // coth(w) = i * cot(i * w), so acoth(z) = -i * acot(-i * z)
        same("acoth(2)", 0.5493061443340549, -0.0, new ComplexD(2.0, 0.0).acoth());
        same("acoth(-2)", -0.5493061443340549, -0.0, new ComplexD(-2.0, 0.0).acoth());
        same("acoth(2i)", 0.0, -0.4636476090008061, new ComplexD(0.0, 2.0).acoth());
        // its cut is the mirror image, on the real rays |Re z| >= 1
        same("acoth(2+0i)", 0.5493061443340549, -0.0, new ComplexD(2.0, 0.0).acoth());
        double up = new ComplexD(2.0, 1.0e-13).acoth().im();
        double dn = new ComplexD(2.0, -1.0e-13).acoth().im();
        assertTrue("the real ray does not jump by PI: " + (up - dn),
                Math.abs(Math.abs(up - dn) - Math.PI) < 1.0e-12);
        // the branch points
        same("acoth(1)", INF, -Math.PI / 2.0, new ComplexD(1.0, 0.0).acoth());
        same("acoth(-1)", -INF, -Math.PI / 2.0, new ComplexD(-1.0, 0.0).acoth());
    }

    @Test
    public void testAcotAndAcothSpreadNan() {
        same("acot(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).acot());
        same("acot(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).acot());
        same("acoth(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).acoth());
        same("acoth(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).acoth());
        same("cot(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).cot());
        same("coth(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).coth());
    }

    @Test
    public void testAcotIsSharpNextToTheBranchPoints() {
        // the ring just outside the unit circle, where the reciprocal form
        // threw the distance to +-i away; the values are the 130 digit oracle
        near("acot(0.0553,-1.03645)", 0.480439877367561, 1.7130832680220007,
                new ComplexD(0.0553, -1.03645).acot());
        near("acot(0.01,1)", 0.7828981842304692, -2.6491649331958946,
                new ComplexD(0.01, 1.0).acot());
        near("acot(-0.01,1)", 2.358694469359324, -2.6491649331958946,
                new ComplexD(-0.01, 1.0).acot());
        near("acot(0.001,-0.9995)", 1.0169719054030495, 3.744540388846149,
                new ComplexD(0.001, -0.9995).acot());
        near("acot(1e-6,1.01)", 4.975124361442784e-5, -2.6516524515295994,
                new ComplexD(1.0e-6, 1.01).acot());
        near("acot(-1e-4,-1.0001)", 2.748918570641083, 4.778481981128063,
                new ComplexD(-1.0e-4, -1.0001).acot());
        near("acot(0.25,1.05)", 0.626024383187126, -1.0459525475075355,
                new ComplexD(0.25, 1.05).acot());
        near("acot(3.9,0.5)", 0.24734169601672873, -0.03041412681304506,
                new ComplexD(3.9, 0.5).acot());
        // and acoth is the same statement turned by i
        near("acoth(1.03645,0.0553)", 1.7130832680220007, -0.480439877367561,
                new ComplexD(1.03645, 0.0553).acoth());
    }

    @Test
    public void testAcotHasNoSeamWhereTheFormChanges() {
        // the form changes at |z| = 1 and at |z| = 4, and crossing must not step
        for (double seam : new double[] { 1.0, 4.0 }) {
            for (int k = 0; k < 720; ++k) {
                double ang = 2.0 * Math.PI * k / 720.0;
                double c = Math.cos(ang);
                double s = Math.sin(ang);
                if (seam == 1.0 && Math.abs(c) < 1.0e-3) {
                    continue; // the branch points, where acot is unbounded
                }
                double in = seam * (1.0 - 1.0e-15);
                double out = seam * (1.0 + 1.0e-15);
                ComplexD lo = new ComplexD(in * c, in * s).acot();
                ComplexD hi = new ComplexD(out * c, out * s).acot();
                double step = Math.max(Math.abs(hi.re() - lo.re()), Math.abs(hi.im() - lo.im()));
                assertTrue("a step of " + step + " across |z| = " + seam, step < 1.0e-11);
            }
        }
    }

    @Test
    public void testAcotStopsTheDirectFormBeforeTheSquareOverflows() {
        // x*x overflows above 1.3e154, so the direct form must end well below it
        same("acot(1e200)", 1.0e-200, -0.0, new ComplexD(1.0e200, 0.0).acot());
        same("acot(-1e200)", Math.PI, -0.0, new ComplexD(-1.0e200, 0.0).acot());
        same("acot(1e200+1e200i)", 5.0e-201, -5.0e-201, new ComplexD(1.0e200, 1.0e200).acot());
        same("acot(-1e200+1e200i)", Math.PI, -5.0e-201, new ComplexD(-1.0e200, 1.0e200).acot());
    }

    /** how far f * parent is from one, in ulp, or zero where it says nothing */
    private static double off(ComplexD f, ComplexD parent) {
        if (f.isNan() || f.isInfinite() || parent.isNan() || parent.isInfinite()) {
            return 0.0;
        }
        ComplexD p = f.mul(parent);
        if (p.isNan() || p.isInfinite()) {
            return 0.0;
        }
        return Math.max(Math.abs(p.re() - 1.0), Math.abs(p.im())) / Math.ulp(1.0);
    }

    @Test
    public void testTheReciprocalsAreTheReciprocals() {
        // the only thing the four have to be, and inv is exact enough that
        // they are - so this identity is the whole accuracy test outside the
        // overflow band
        double worst = 0.0;
        for (int e = -20; e <= 20; ++e) {
            double m = Math.pow(10.0, e);
            for (int k = 0; k < 16; ++k) {
                double ang = 2.0 * Math.PI * k / 16.0;
                ComplexD z = new ComplexD(m * Math.cos(ang), m * Math.sin(ang));
                worst = Math.max(worst, off(z.sec(), z.cos()));
                worst = Math.max(worst, off(z.csc(), z.sin()));
                worst = Math.max(worst, off(z.sech(), z.cosh()));
                worst = Math.max(worst, off(z.csch(), z.sinh()));
            }
        }
        for (int i = 0; i < SPECIAL.length; ++i) {
            ComplexD z = z(i);
            worst = Math.max(worst, off(z.sec(), z.cos()));
            worst = Math.max(worst, off(z.csc(), z.sin()));
            worst = Math.max(worst, off(z.sech(), z.cosh()));
            worst = Math.max(worst, off(z.csch(), z.sinh()));
        }
        assertTrue("the reciprocal identity is off by " + worst + " ulp", worst <= 2.0);
        same("sec(1)", 1.8508157176809255, 0.0, new ComplexD(1.0, 0.0).sec());
        same("csc(1)", 1.1883951057781212, -0.0, new ComplexD(1.0, 0.0).csc());
        same("sech(1)", 0.6480542736638853, -0.0, new ComplexD(1.0, 0.0).sech());
        same("csch(1)", 0.8509181282393216, -0.0, new ComplexD(1.0, 0.0).csch());
    }

    @Test
    public void testSechAndCschSurviveTheOverflowBand() {
        // cosh overflows at 710.48 and inv a little earlier, but 1/cosh is
        // representable to 745.13 - that whole band came back as a flat zero
        for (double x : new double[] { 710.5, 711.0, 715.0, 720.0, 730.0, 740.0, 744.0 }) {
            ComplexD z = new ComplexD(x, 1.0);
            ComplexD h = new ComplexD(x / 2.0, 0.5).exp();
            for (ComplexD f : new ComplexD[] { z.sech(), z.csch() }) {
                assertTrue("a flat zero at x = " + x, f.re() != 0.0 || f.im() != 0.0);
                // exp is verified, and f * e^z is two out here; the tolerance is
                // the subnormal's own resolution, nothing else
                ComplexD two = f.mul(h).mul(h);
                double tol = 8.0 * Math.ulp(f.re()) / Math.abs(f.re());
                assertEquals("f * e^z at x = " + x, 2.0, two.re(), tol);
                assertEquals("and its imaginary part at x = " + x, 0.0, two.im(), tol);
            }
        }
        // past the band the value is gone for good, and only the sign is left
        same("sech(746,1)", 0.0, -0.0, new ComplexD(746.0, 1.0).sech());
        same("csch(746,1)", 0.0, -0.0, new ComplexD(746.0, 1.0).csch());
    }

    @Test
    public void testSechIsEvenAndCschIsOddInTheBand() {
        // the direction is the second thing the flat zero used to destroy
        for (double y : new double[] { 1.0, -1.0, 2.5, -2.5 }) {
            ComplexD p = new ComplexD(720.0, y);
            ComplexD m = new ComplexD(-720.0, -y);
            same("sech even at " + y, p.sech().re(), p.sech().im(), m.sech());
            same("csch odd at " + y, -p.csch().re(), -p.csch().im(), m.csch());
        }
    }

    @Test
    public void testTheOverflowBandHasNoSeam() {
        // walking into the band, every step must be the ordinary e^0.01
        double prev = Double.NaN;
        for (double x = 709.0; x < 712.0; x += 0.01) {
            double v = new ComplexD(x, 1.0).sech().re();
            assertTrue("a flat zero at x = " + x, v != 0.0);
            if (!Double.isNaN(prev)) {
                double q = prev / v;
                assertTrue("a step of " + q + " at x = " + x, q > 1.0 && q < 1.02);
            }
            prev = v;
        }
    }

    @Test
    public void testTheReciprocalsAtThePolesAndAtInfinity() {
        // csc and csch have their pole at the origin, and this library has one
        // infinity without a direction; the turn in csc must not give it one
        for (int i = 0; i < 4; ++i) {
            same("csc at zero " + i, INF, INF, z(i).csc());
            same("csch at zero " + i, INF, INF, z(i).csch());
        }
        same("sec(+0,+0)", 1.0, 0.0, new ComplexD(0.0, 0.0).sec());
        same("sec(-0,+0)", 1.0, -0.0, new ComplexD(-0.0, 0.0).sec());
        same("sech(+0,+0)", 1.0, -0.0, new ComplexD(0.0, 0.0).sech());
        same("sech(-0,+0)", 1.0, 0.0, new ComplexD(-0.0, 0.0).sech());
        // at infinity the reciprocal is a zero, and that has no direction either
        same("sech(inf)", 0.0, 0.0, new ComplexD(INF, 0.0).sech());
        same("csch(-inf)", 0.0, 0.0, new ComplexD(-INF, 0.0).csch());
        same("sec(0,inf)", 0.0, 0.0, new ComplexD(0.0, INF).sec());
        same("csc(0,inf)", 0.0, 0.0, new ComplexD(0.0, INF).csc());
        same("csc(0,-inf)", 0.0, 0.0, new ComplexD(0.0, -INF).csc());
        // cos of an infinite real is NaN, so its reciprocal is one too
        same("sec(inf)", NAN, NAN, new ComplexD(INF, 0.0).sec());
        same("csc(inf)", NAN, NAN, new ComplexD(INF, 0.0).csc());
        same("sech(1,inf)", NAN, NAN, new ComplexD(1.0, INF).sech());
        same("csc(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).csc());
        same("sech(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).sech());
        same("csch(NaN,NaN)", NAN, NAN, new ComplexD(NAN, NAN).csch());
    }

    @Test
    public void testAsecIsSharpNextToTheBranchPoints() {
        // taking the reciprocal first throws the distance to +-1 away, which
        // costs up to 4.8e11 ulp; these are the 130 digit oracle values
        near("asec(1.01,1e-4)", 0.1408376626744981, 6.983531194957062e-4,
                new ComplexD(1.01, 1.0e-4).asec());
        near("asec(0.999,-5e-4)", 0.010878474157442801, -0.04603902209003508,
                new ComplexD(0.999, -5.0e-4).asec());
        near("asec(-1.02,0.003)", 2.942666374836713, 0.014590751694938963,
                new ComplexD(-1.02, 0.003).asec());
        near("asec(-0.9995,-2e-4)", 3.13538250831896, -0.032232219854273825,
                new ComplexD(-0.9995, -2.0e-4).asec());
        near("asec(1,1e-8)", 1.0000000041666667e-4, 9.999999958333333e-5,
                new ComplexD(1.0, 1.0e-8).asec());
        near("asec(-1,1e-6)", 3.140592653173127, 9.999995833330645e-4,
                new ComplexD(-1.0, 1.0e-6).asec());
        near("asec(0.5,0.25)", 0.5352384153948206, 1.2321615351709958,
                new ComplexD(0.5, 0.25).asec());
        // and asech is the same statement turned by i
        near("asech(1.01,1e-4)", 6.983531194957062e-4, -0.1408376626744981,
                new ComplexD(1.01, 1.0e-4).asech());
        near("asech(-0.9995,-2e-4)", 0.032232219854273825, 3.13538250831896,
                new ComplexD(-0.9995, -2.0e-4).asech());
    }

    @Test
    public void testAsecPutsItsCutOnTheSegment() {
        // on (-1, 1) the sign of the zero picks the side, and +0 is the limit
        // from above
        double v = 1.3169578969248166;
        same("asec(0.5+0i)", 0.0, v, new ComplexD(0.5, 0.0).asec());
        same("asec(0.5-0i)", 0.0, -v, new ComplexD(0.5, -0.0).asec());
        same("asec(-0.5+0i)", Math.PI, v, new ComplexD(-0.5, 0.0).asec());
        same("asec(-0.5-0i)", Math.PI, -v, new ComplexD(-0.5, -0.0).asec());
        assertEquals("the +0 side is not the limit from above", v,
                new ComplexD(0.5, 1.0e-170).asec().im(), 0.0);
        // the branch points, and outside the segment there is no cut at all
        same("asec(1)", 0.0, 0.0, new ComplexD(1.0, 0.0).asec());
        same("asec(-1)", Math.PI, 0.0, new ComplexD(-1.0, 0.0).asec());
        same("asec(2+0i)", 1.0471975511965979, 0.0, new ComplexD(2.0, 0.0).asec());
        same("asec(2-0i)", 1.0471975511965979, -0.0, new ComplexD(2.0, -0.0).asec());
        // a circle of radius 2 never meets the segment, so nothing jumps on it
        double worst = 0.0;
        ComplexD prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexD w = new ComplexD(2.0 * Math.cos(ang), 2.0 * Math.sin(ang)).asec();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " outside the segment", worst < 0.01);
    }

    @Test
    public void testAsechPutsItsCutsOnTheRealRays() {
        // asech's cuts are the mirror image, (-inf, 0] and (1, inf)
        same("asech(2+0i)", 0.0, -1.0471975511965979, new ComplexD(2.0, 0.0).asech());
        same("asech(2-0i)", 0.0, 1.0471975511965979, new ComplexD(2.0, -0.0).asech());
        same("asech(-2+0i)", 0.0, -2.0943951023931953, new ComplexD(-2.0, 0.0).asech());
        same("asech(-0.5+0i)", 1.3169578969248166, -Math.PI, new ComplexD(-0.5, 0.0).asech());
        // and between 0 and 1 there is no cut
        same("asech(0.5+0i)", 1.3169578969248166, -0.0, new ComplexD(0.5, 0.0).asech());
        same("asech(0.5-0i)", 1.3169578969248166, 0.0, new ComplexD(0.5, -0.0).asech());
        same("asech(1)", 0.0, -0.0, new ComplexD(1.0, 0.0).asech());
        same("asech(-1)", 0.0, -Math.PI, new ComplexD(-1.0, 0.0).asech());
    }

    @Test
    public void testAsecSurvivesWhereTheReciprocalOverflows() {
        // below 1/MAX_VALUE the reciprocal is Infinity and the whole value was
        // lost; the asymptotic keeps it, subnormal components and all
        assertTrue("the reciprocal does not overflow here after all",
                new ComplexD(6.0e-321, 8.0e-321).inv().isInfinite());
        near("asec(6e-311,8e-311)", 0.9272952180016023, 714.494526008714,
                new ComplexD(6.0e-311, 8.0e-311).asec());
        near("asec(6e-321,8e-321)", 0.9273940517629033, 737.5205857146394,
                new ComplexD(6.0e-321, 8.0e-321).asec());
        near("asec(MIN,MIN)", 0.7853981633974483, 744.7866455116613,
                new ComplexD(Double.MIN_VALUE, Double.MIN_VALUE).asec());
    }

    @Test
    public void testAsecAtThePolesAndAtInfinity() {
        // at the origin the modulus is unbounded and the real part is whatever
        // direction one came from, so the one infinity stands there
        for (int i = 0; i < 4; ++i) {
            same("asec at zero " + i, INF, INF, z(i).asec());
            same("asech at zero " + i, INF, INF, z(i).asech());
        }
        // an infinite modulus makes the reciprocal zero, whatever the direction
        same("asec(inf)", Math.PI / 2.0, -0.0, new ComplexD(INF, 0.0).asec());
        same("asec(-inf)", Math.PI / 2.0, -0.0, new ComplexD(-INF, 0.0).asec());
        same("asec(0,inf)", Math.PI / 2.0, -0.0, new ComplexD(0.0, INF).asec());
        same("asec(NaN,inf)", Math.PI / 2.0, -0.0, new ComplexD(NAN, INF).asec());
        same("asech(inf)", 0.0, Math.PI / 2.0, new ComplexD(INF, 0.0).asech());
        same("asec(i)", Math.PI / 2.0, 0.881373587019543, new ComplexD(0.0, 1.0).asec());
        same("asech(i)", 0.881373587019543, -Math.PI / 2.0, new ComplexD(0.0, 1.0).asech());
        same("asec(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).asec());
        same("asec(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).asec());
        same("asech(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).asech());
        same("asech(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).asech());
    }

    @Test
    public void testAcscIsSharpNextToTheBranchPoints() {
        // asin(inv(z)) throws the distance to +-1 away and costs up to 3.6e11
        // ulp here; these are the 130 digit oracle values
        near("acsc(1.01,1e-4)", 1.4299586641203985, -6.983531194957062e-4,
                new ComplexD(1.01, 1.0e-4).acsc());
        near("acsc(0.999,-5e-4)", 1.5599178526374538, 0.04603902209003508,
                new ComplexD(0.999, -5.0e-4).acsc());
        near("acsc(-1.02,0.003)", -1.371870048041816, -0.014590751694938963,
                new ComplexD(-1.02, 0.003).acsc());
        near("acsc(-0.9995,-2e-4)", -1.5645861815240636, 0.032232219854273825,
                new ComplexD(-0.9995, -2.0e-4).acsc());
        near("acsc(1,1e-8)", 1.57069632679448, -9.999999958333333e-5,
                new ComplexD(1.0, 1.0e-8).acsc());
        near("acsc(-1,1e-6)", -1.5697963263782302, -9.999995833330645e-4,
                new ComplexD(-1.0, 1.0e-6).acsc());
        // and just outside the switch, where the identity would cancel instead
        near("acsc(1.2,0.1)", 0.9642047381542691, -0.12068401873708458,
                new ComplexD(1.2, 0.1).acsc());
        near("acsc(3,-4)", 0.11875073130741176, 0.16044553377450493,
                new ComplexD(3.0, -4.0).acsc());
        // acsch has its branch points at +-i, and gets there by the turn
        near("acsch(1e-4,1.01)", 6.983531194957062e-4, -1.4299586641203985,
                new ComplexD(1.0e-4, 1.01).acsch());
        near("acsch(-2e-4,-0.9995)", -0.032232219854273825, 1.5645861815240636,
                new ComplexD(-2.0e-4, -0.9995).acsch());
    }

    @Test
    public void testAcscPutsItsCutOnTheSegment() {
        // on (-1, 1) the sign of the zero picks the side, and +0 is the limit
        // from above
        double v = 1.3169578969248166;
        double h = Math.PI / 2.0;
        same("acsc(0.5+0i)", h, -v, new ComplexD(0.5, 0.0).acsc());
        same("acsc(0.5-0i)", h, v, new ComplexD(0.5, -0.0).acsc());
        same("acsc(-0.5+0i)", -h, -v, new ComplexD(-0.5, 0.0).acsc());
        same("acsc(-0.5-0i)", -h, v, new ComplexD(-0.5, -0.0).acsc());
        assertEquals("the +0 side is not the limit from above", -v,
                new ComplexD(0.5, 1.0e-170).acsc().im(), 0.0);
        // the branch points, and outside the segment there is no cut at all
        same("acsc(1)", h, -0.0, new ComplexD(1.0, 0.0).acsc());
        same("acsc(-1)", -h, -0.0, new ComplexD(-1.0, 0.0).acsc());
        same("acsc(2+0i)", 0.5235987755982989, -0.0, new ComplexD(2.0, 0.0).acsc());
        same("acsc(2-0i)", 0.5235987755982989, 0.0, new ComplexD(2.0, -0.0).acsc());
        // a circle of radius 2 never meets the segment, so nothing jumps on it
        double worst = 0.0;
        ComplexD prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexD w = new ComplexD(2.0 * Math.cos(ang), 2.0 * Math.sin(ang)).acsc();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " outside the segment", worst < 0.01);
    }

    @Test
    public void testAcschPutsItsCutOnTheImaginarySegment() {
        // the turn by i carries the cut onto i*(-1, 1)
        double v = 1.3169578969248166;
        double h = Math.PI / 2.0;
        same("acsch(+0+0.5i)", v, -h, new ComplexD(0.0, 0.5).acsch());
        same("acsch(-0+0.5i)", -v, -h, new ComplexD(-0.0, 0.5).acsch());
        same("acsch(+0-0.5i)", v, h, new ComplexD(0.0, -0.5).acsch());
        same("acsch(-0-0.5i)", -v, h, new ComplexD(-0.0, -0.5).acsch());
        // outside it there is none
        same("acsch(+0+2i)", 0.0, -0.5235987755982989, new ComplexD(0.0, 2.0).acsch());
        same("acsch(-0+2i)", -0.0, -0.5235987755982989, new ComplexD(-0.0, 2.0).acsch());
        // the branch point, and the real axis where acsch is real
        same("acsch(i)", 0.0, -h, new ComplexD(0.0, 1.0).acsch());
        same("acsch(1)", 0.881373587019543, -0.0, new ComplexD(1.0, 0.0).acsch());
        same("acsch(2)", 0.48121182505960347, -0.0, new ComplexD(2.0, 0.0).acsch());
    }

    @Test
    public void testAcscSurvivesWhereTheReciprocalOverflows() {
        // below 1/MAX_VALUE the reciprocal is Infinity and the value was lost;
        // PI/2 - atan2(|y|, x) written as atan2(x, |y|) cancels nowhere
        assertTrue("the reciprocal does not overflow here after all",
                new ComplexD(6.0e-321, 8.0e-321).inv().isInfinite());
        near("acsc(6e-311,8e-311)", 0.6435011087932943, -714.494526008714,
                new ComplexD(6.0e-311, 8.0e-311).acsc());
        near("acsc(6e-321,8e-321)", 0.6434022750319932, -737.5205857146394,
                new ComplexD(6.0e-321, 8.0e-321).acsc());
        near("acsc(MIN,MIN)", 0.7853981633974483, -744.7866455116613,
                new ComplexD(Double.MIN_VALUE, Double.MIN_VALUE).acsc());
        // and where the real part itself is tiny, which is where writing it
        // as PI/2 - atan2 would have thrown it away
        near("acsc(1e-320,1e-310)", 9.99988867182686e-11, -714.4945260087142,
                new ComplexD(1.0e-320, 1.0e-310).acsc());
        near("acsc(-1e-320,1e-310)", -9.99988867182686e-11, -714.4945260087142,
                new ComplexD(-1.0e-320, 1.0e-310).acsc());
    }

    @Test
    public void testAcscAtThePolesAndAtInfinity() {
        // at the origin the modulus is unbounded and the real part is whatever
        // direction one came from, so the one infinity stands there
        for (int i = 0; i < 4; ++i) {
            same("acsc at zero " + i, INF, INF, z(i).acsc());
            same("acsch at zero " + i, INF, INF, z(i).acsch());
        }
        // an infinite modulus makes the reciprocal zero, whatever the direction
        same("acsc(inf)", 0.0, 0.0, new ComplexD(INF, 0.0).acsc());
        same("acsc(-inf)", 0.0, 0.0, new ComplexD(-INF, 0.0).acsc());
        same("acsc(0,inf)", 0.0, 0.0, new ComplexD(0.0, INF).acsc());
        same("acsc(NaN,inf)", 0.0, 0.0, new ComplexD(NAN, INF).acsc());
        same("acsch(inf)", -0.0, 0.0, new ComplexD(INF, 0.0).acsch());
        same("acsc(i)", 0.0, -0.881373587019543, new ComplexD(0.0, 1.0).acsc());
        same("acsc(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).acsc());
        same("acsc(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).acsc());
        same("acsch(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).acsch());
        same("acsch(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).acsch());
    }

    @Test
    public void testSincSurvivesTheOverflowBand() {
        // sin overflows at |Im z| = 710.4758, sin(z)/z only ln|z| later, so
        // there is a wedge where the plain quotient is already Infinity
        assertTrue("sin does not overflow here after all",
                new ComplexD(1.0, 712.0).sin().isInfinite());
        near("sinc(1,712)", 6.276911646249123E305, -9.745577609708701E305,
                new ComplexD(1.0, 712.0).sinc());
        // cos(2.5) is negative, which copying the sign of y onto it would lose
        near("sinc(2.5,713)", -2.5142709150625284E306, -1.8919879218025157E306,
                new ComplexD(2.5, 713.0).sinc());
        near("sinc(0,716)", 6.293699812721396E307, 0.0, new ComplexD(0.0, 716.0).sinc());
        near("sinc(-3,-714.5)", -1.3923208533539997E307, -2.044388782063196E306,
                new ComplexD(-3.0, -714.5).sinc());
        // the wedge is ln|z| wide, so a large real part carries it a long way
        near("sinc(1e5,720)", 7.024772971865934E305, -2.4592836110691795E307,
                new ComplexD(1.0e5, 720.0).sinc());
        near("sinc(1e300,1400)", -4.2066392775375587E307, -2.959402552439904E307,
                new ComplexD(1.0e300, 1400.0).sinc());
        near("sinhc(713,2.5)", -2.5142709150625284E306, 1.8919879218025157E306,
                new ComplexD(713.0, 2.5).sinhc());
    }

    @Test
    public void testSincLetsEachComponentOverflowOnItsOwn() {
        // scaling the pair at once would erase the second component the moment
        // the first went infinite
        same("sinc(1e-20,900)", INF, -INF, new ComplexD(1.0e-20, 900.0).sinc());
        same("sinc(-1e-20,900)", INF, INF, new ComplexD(-1.0e-20, 900.0).sinc());
        same("sinc(1e-30,1000)", INF, -INF, new ComplexD(1.0e-30, 1000.0).sinc());
        // and a zero component has to stay a zero, not turn into a NaN
        same("sinc(0,800)", INF, 0.0, new ComplexD(0.0, 800.0).sinc());
    }

    @Test
    public void testTheCardinalSinesAtTheOriginAndNextToIt() {
        // one one, and no direction: all four zeros give the same
        for (int i = 0; i < 4; ++i) {
            same("sinc at zero " + i, 1.0, 0.0, z(i).sinc());
            same("sinhc at zero " + i, 1.0, 0.0, z(i).sinhc());
        }
        // the singularity is removable, so nothing is lost next to it either
        near("sinc(1e-160,0)", 1.0, 0.0, new ComplexD(1.0e-160, 0.0).sinc());
        near("sinc(0,1e-160)", 1.0, 0.0, new ComplexD(0.0, 1.0e-160).sinc());
        ComplexD t = new ComplexD(1.0e-8, 1.0e-8).sinc();
        assertEquals("sinc(1e-8,1e-8) re", 1.0, t.re(), 4.0 * Math.ulp(1.0));
        assertEquals("sinc(1e-8,1e-8) im", 0.0, t.im(), 4.0 * Math.ulp(1.0));
        near("sinc(0.5)", 0.958851077208406, 0.0, new ComplexD(0.5, 0.0).sinc());
        near("sinhc(0.5)", 1.0421906109874948, 0.0, new ComplexD(0.5, 0.0).sinhc());
        near("sinc(2)", 0.45464871341284085, 0.0, new ComplexD(2.0, 0.0).sinc());
    }

    @Test
    public void testTheCardinalSinesAtThePolesAndAtInfinity() {
        // an unbounded imaginary part leaves the modulus unbounded and the
        // direction unsettled, so the one infinity stands there
        same("sinc(0,inf)", INF, INF, new ComplexD(0.0, INF).sinc());
        same("sinc(0,-inf)", INF, INF, new ComplexD(0.0, -INF).sinc());
        same("sinc(inf,inf)", INF, INF, new ComplexD(INF, INF).sinc());
        same("sinc(NaN,inf)", INF, INF, new ComplexD(NAN, INF).sinc());
        same("sinhc(inf,0)", INF, INF, new ComplexD(INF, 0.0).sinhc());
        // along the real axis the sine stays bounded, so the quotient dies away
        same("sinc(inf)", 0.0, 0.0, new ComplexD(INF, 0.0).sinc());
        same("sinc(-inf)", 0.0, 0.0, new ComplexD(-INF, 0.0).sinc());
        same("sinc(inf,1)", 0.0, 0.0, new ComplexD(INF, 1.0).sinc());
        same("sinc(inf,NaN)", 0.0, 0.0, new ComplexD(INF, NAN).sinc());
        same("sinhc(0,inf)", 0.0, 0.0, new ComplexD(0.0, INF).sinhc());
        same("sinc(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).sinc());
        same("sinc(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).sinc());
        same("sinhc(NaN,1)", NAN, NAN, new ComplexD(NAN, 1.0).sinhc());
        same("sinhc(1,NaN)", NAN, NAN, new ComplexD(1.0, NAN).sinhc());
    }

    @Test
    public void testTheCardinalSinesAreTheQuotients() {
        double[][] ps = { { 1.0, 1.0 }, { 2.5, -0.75 }, { -4.0, 3.0 }, { 20.0, 3.0 },
                { 0.25, 0.0 }, { 0.0, 0.25 }, { 1.0e-8, 1.0e-8 }, { 700.0, 0.5 } };
        for (double[] p : ps) {
            ComplexD z = new ComplexD(p[0], p[1]);
            ComplexD a = z.sinc().mul(z);
            ComplexD b = z.sinhc().mul(z);
            assertEquals("sinc times z re at " + z, z.sin().re(), a.re(),
                    8.0 * Math.ulp(z.sin().abs()));
            assertEquals("sinc times z im at " + z, z.sin().im(), a.im(),
                    8.0 * Math.ulp(z.sin().abs()));
            assertEquals("sinhc times z re at " + z, z.sinh().re(), b.re(),
                    8.0 * Math.ulp(z.sinh().abs()));
            assertEquals("sinhc times z im at " + z, z.sinh().im(), b.im(),
                    8.0 * Math.ulp(z.sinh().abs()));
        }
    }

    @Test
    public void testSinhcIsSincTurnedByI() {
        double[][] ps = { { 1.0, 1.0 }, { 2.5, -0.75 }, { -4.0, 700.0 }, { 1.0e-300, 3.0 },
                { 0.0, 0.5 }, { 713.0, 2.5 }, { 1.0e300, 1400.0 } };
        for (double[] p : ps) {
            ComplexD z = new ComplexD(p[0], p[1]);
            ComplexD t = new ComplexD(-p[1], p[0]).sinc();
            same("sinhc is the turn at " + z, t.re(), t.im(), z.sinhc());
            // and the cardinal sine is even, down to the sign of a zero
            near("sinc is even at " + z, z.sinc().re(), z.sinc().im(), z.neg().sinc());
        }
    }

    // ================= the digests =================
    // one per left operand, folded over all 26 right operands

    private static final long[] MUL_DIGEST = {
            0x8C36AC0F97F502BDL, 0xF7EC8309EB2BBCDDL, 0x95143493C8653A54L,
            0x99118EF76A0E9459L, 0xAE9CC967A5EDBBEFL, 0x3C63C7F835A5055AL,
            0xBEAB71DE8064A245L, 0x1AAED008F4947CD8L, 0x960A2532CE3CE648L,
            0xECAEEBE0370F947DL, 0xD4A784869CC2F590L, 0xEB7C458E10046C84L,
            0x514374A9371D7174L, 0x13C80708362FBB3FL, 0x48B315A6DB3C1B38L,
            0xAE457393BB7142B4L, 0xE336082AD0C1073BL, 0x696A8ABE057431C2L,
            0xA41F97EE0D769DB0L, 0x8D9789A1E9231EF3L, 0x204EB64D1052D7F8L,
            0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L, 0xC9443BBDF728642DL,
            0x13C80708362FBB3FL, 0xAE457393BB7142B4L };
    private static final long[] DIV_DIGEST = {
            0x465BB4704EBEE6E1L, 0x2A4AA4C89E610073L, 0xE5DDD92486DE4782L,
            0x301412524F1C2F77L, 0x48F249AE88F35BFDL, 0xCF2A85CDC417A325L,
            0x7EA2BA0CD79EFA6AL, 0x6F4DA42F987E3BF4L, 0x2517E6914C3CA522L,
            0x4E0344DD359A4504L, 0x1B251B6CE0525588L, 0xF673AAE6AF443AF2L,
            0x0BA1D5FB44216653L, 0x7320802C26EAE749L, 0x363F496977E15BB2L,
            0x2281048D55494F4AL, 0x90A1A302D57B02BEL, 0x4FB1BCAD4BFA533DL,
            0x2AE287CBF2FABA3FL, 0x55A75FFE8A522676L, 0x19F64499015D71ADL,
            0x19F64499015D71ADL, 0x19F64499015D71ADL, 0x19F64499015D71ADL,
            0x7320802C26EAE749L, 0x2281048D55494F4AL };
    private static final long[] POW_DIGEST = {
            0x5FC227D263AA91AFL, 0x5FC227D263AA91AFL, 0x5FC227D263AA91AFL,
            0x5FC227D263AA91AFL, 0xA71242CEB023E841L, 0x4596741EFF242C46L,
            0xCC45B460DD4E25D5L, 0xBC6A2348400880C6L, 0xCD9ECC2CBF0D4DCBL,
            0x4DA764C6D6DBCA38L, 0x5289133D583E07BFL, 0xE3A3B905D26A2EFAL,
            0x23C254B0438BA8C4L, 0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L,
            0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L,
            0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L, 0x204EB64D1052D7F8L,
            0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L,
            0xE5E5C7B72A3E5381L, 0xE5E5C7B72A3E5381L };
    private static final long[] ADD_DIGEST = {
            0xD939946838EC8CFBL, 0xAB0E59327BA62EEAL, 0x9FADB16A2FCAC990L,
            0xFD4C87FD7B37A63EL, 0x2AA85ADD4F613441L, 0xCBC8E2D9F5967892L,
            0x9AFCAB79976BD4CAL, 0x9FBC92BEF441ED48L, 0xA2AEEC8D117DC9B7L,
            0xF1724FF9B7A4C1E3L, 0x09C4518F4440E61CL, 0x3552597FB7A0A6D3L,
            0x3221A80136566357L, 0x235523E0F7D19A53L, 0xD56AEC545BDA685BL,
            0x6DDF7E006A84D5B3L, 0x7BE08C4DC52D2A77L, 0xAA911F33F0101647L,
            0xA3D4A1E26D72C66BL, 0x1A3A1CFE812D18D2L, 0x2747C3D51BD15705L,
            0xC94D0767519DFBC3L, 0x204EB64D1052D7F8L, 0xCCF3142C330B2F36L,
            0x39865C58C45AE528L, 0xF5C6013D287973C2L };
    private static final long[] SUB_DIGEST = {
            0x63F935D6B59C81A8L, 0x8B7DF90D08DA957EL, 0x65431A58CB009E50L,
            0xCCDE060AE8009104L, 0x2F5BD8C0D6619351L, 0x596A72B85AAC46CFL,
            0x5AE305CC8D03FD37L, 0x101328481E93ADA2L, 0x5F93C746B7BA3A73L,
            0xA5DF6DBA94060A7CL, 0x2369FED424A01B93L, 0x4F03C1CCD180B94CL,
            0x91AB7E9DCCCEDE6EL, 0xC0884BC91964D355L, 0x76B183AEFAF609B4L,
            0x803DEA91831D0617L, 0xDF333E92457C7D7FL, 0x1EAA84DDBFF4F921L,
            0x59E5F4066274A3E6L, 0xA57BD965CDC2B3B2L, 0xC31D156386A753F7L,
            0xB44BC9BB987E4EF2L, 0x204EB64D1052D7F8L, 0x19FBED607561D17CL,
            0x7ABB2D53D43F8B96L, 0x14200D4175B564D6L };
    private static final long[] SCALE_DIGEST = {
            0x6D3EE73E937BD60EL, 0x56496E4DA9F9EA45L, 0x2EF7F7C94423066CL,
            0x367FF12A5D71ADA9L, 0x56E46FC625844C38L, 0x669F2E664743811BL,
            0x91F6D11DFC27BEC7L, 0x711AB9E0C9006208L, 0x7EDE5F016BEFA5ECL,
            0x49809193F30D96F9L, 0x348425A355222D87L, 0x2FCCBC9F17954E9EL,
            0xFB6062A75CF03067L, 0x9464A4979EE6AB75L, 0x1497EAE04496BB7FL,
            0x07DC93A9767C4F4BL, 0xF00464EB72FFD456L, 0x05020ED10E1B81B0L,
            0xC2CCF98C474632F4L, 0xCBE9CCF5F26D03D9L, 0x2E840BD58D4AE086L,
            0x32473C25BEB25AD4L, 0x6FE25EB7DBF1810FL, 0x7AD9F7F42E081D92L,
            0x9464A4979EE6AB75L, 0x07DC93A9767C4F4BL };
    private static final long[] POWR_DIGEST = {
            0x73960B54D06804D7L, 0x73960B54D06804D7L, 0x73960B54D06804D7L,
            0x73960B54D06804D7L, 0xC5DAFE644FBCD402L, 0xD57C98B6B6EAA7E0L,
            0x276209A09DBC9273L, 0xA8B58B4D217E1CF2L, 0x0620FA21B177CF4EL,
            0xD50BB363F78E7E3CL, 0x137F5A3CEC530C3FL, 0xA068718E3CAE51EFL,
            0xF13B57F104B743A9L, 0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL,
            0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL,
            0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL, 0x6FE25EB7DBF1810FL,
            0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL,
            0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL };

    private static final long INV_DIGEST = 0xAE700A5169C3FE03L;
    private static final long LN_DIGEST = 0xD96027ED7896B147L;
    private static final long EXP_DIGEST = 0xB5FA337F1D07EA36L;
    private static final long SQRT_DIGEST = 0x750654B648A46A2CL;
    private static final long SINH_DIGEST = 0xEDAAA09F662768FEL;
    private static final long COSH_DIGEST = 0xE80D9C777E389AA7L;
    private static final long SIN_DIGEST = 0x15829D37E8EEFE4BL;
    private static final long COS_DIGEST = 0xF942D3E01B0FD5ABL;
    private static final long TANH_DIGEST = 0x9818B4ABA5D72939L;
    private static final long TAN_DIGEST = 0x237C15734C6FF0DAL;
    private static final long ATANH_DIGEST = 0x859F6C5293A66D06L;
    private static final long ATAN_DIGEST = 0xB957AAF9B42FFDC8L;
    private static final long ASINH_DIGEST = 0x140EDCB145E2F48EL;
    private static final long ASIN_DIGEST = 0xDEF56D62C58A1C55L;
    private static final long ACOS_DIGEST = 0x7A0B0634C62275C7L;
    private static final long ACOSH_DIGEST = 0xB4E0E132DDB074F9L;
    private static final long COT_DIGEST = 0x8B01F173D12D36D4L;
    private static final long COTH_DIGEST = 0xEBD81FF6C15594F1L;
    private static final long ACOT_DIGEST = 0x45A866F18AA22CF0L;
    private static final long ACOTH_DIGEST = 0x863DD2D67E15B7D6L;
    private static final long SEC_DIGEST   = 0xB0EE8F70D1398B7FL;
    private static final long CSC_DIGEST   = 0x6B22DD7DDF1375B0L;
    private static final long SECH_DIGEST  = 0xBA128FFD3E364383L;
    private static final long CSCH_DIGEST  = 0x8ECC24B861069AF7L;
    private static final long ASEC_DIGEST  = 0x73F37ACE85F6057EL;
    private static final long ASECH_DIGEST = 0xFD834A155CCAC102L;
    private static final long ACSC_DIGEST  = 0xA9B268B8875902CEL;
    private static final long ACSCH_DIGEST = 0x924A74011865124CL;
    private static final long SINC_DIGEST = 0x07B0D7079F713734L;
    private static final long SINHC_DIGEST = 0xA179567DB1BB4D6BL;
    private static final long NTHROOT_DIGEST = 0x13D7EFEE24C3D43FL;
    private static final long NTHROOTS_DIGEST = 0x34C1B57CE5FD60C1L;
    private static final long CONJ_DIGEST = 0x7177CD7F280B4735L;
    private static final long NEG_DIGEST = 0xCCDE060AE8009104L;
    private static final long ABS_DIGEST = 0x1EB710824629C9F6L;
    private static final long ARG_DIGEST = 0x07645CBA6F24888EL;
    private static final long HASH_DIGEST = 0xEB1D52173FAB84AAL;
    private static final long STRING_DIGEST = 0xC06FFF11282FD340L;
}
