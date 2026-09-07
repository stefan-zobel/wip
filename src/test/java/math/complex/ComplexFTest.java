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
 * Tests for {@link ComplexF}, built like {@link ComplexDTest}: exact arithmetic
 * as the oracle, the infinity convention as literals, and a digest over the
 * full matrix of special values. Needs nothing but JUnit.
 */
public final class ComplexFTest {

    private static final float INF = Float.POSITIVE_INFINITY;
    private static final float NAN = Float.NaN;

    /** the degenerate values, and a few ordinary ones */
    private static final float[][] SPECIAL = { { 0.0f, 0.0f }, { -0.0f, 0.0f }, { 0.0f, -0.0f }, { -0.0f, -0.0f },
            { 1.0f, 0.0f }, { -1.0f, 0.0f }, { 0.0f, 1.0f }, { 0.0f, -1.0f }, { 3.0f, 4.0f }, { -3.0f, -4.0f },
            { 0.5f, 0.25f }, { 1.0e38f, 1.0e38f }, { 1.0e-38f, 1.0e-38f }, { INF, 0.0f }, { -INF, 0.0f },
            { 0.0f, INF }, { INF, INF }, { -INF, INF }, { INF, -INF }, { -INF, -INF }, { NAN, 0.0f }, { 0.0f, NAN },
            { NAN, NAN }, { 1.0f, NAN }, { INF, NAN }, { NAN, INF } };

    /** the real scalars used for scale and pow */
    private static final float[] SCALARS = { 0.0f, -0.0f, 1.0f, -1.0f, 2.0f, 0.5f, -2.5f, INF, -INF, NAN };

    /** the first exponent, the last one and the step of the test ensemble */
    private static final int MIN_EXP = -30;
    private static final int MAX_EXP = 30;
    private static final int ANGLES = 8;

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MAX = new BigDecimal(Float.MAX_VALUE);
    /** below this modulus a float turns subnormal and loses digits */
    private static final BigDecimal FLOOR = new BigDecimal("1e-70");

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

    private static ComplexF z(int i) {
        return new ComplexF(SPECIAL[i][0], SPECIAL[i][1]);
    }

    private static String at(float a, float b) {
        return " at (" + a + ", " + b + ")";
    }

    private static BigDecimal big(float x) {
        return new BigDecimal(x);
    }

    // ---------- bit exact comparison ----------

    private static void same(String what, float want, float got) {
        assertEquals(what + ": want " + want + ", got " + got, Float.floatToIntBits(want),
                Float.floatToIntBits(got));
    }

    private static void same(String what, float wantRe, float wantIm, ComplexF got) {
        same(what + " re", wantRe, got.re());
        same(what + " im", wantIm, got.im());
    }

    // ---------- exact arithmetic as the oracle ----------

    private void component(String what, BigDecimal want, BigDecimal mod2, float got, double tol) {
        if (want.abs().compareTo(MAX) > 0) {
            // out of range; mul can leave NaN behind, see the test below
            assertTrue(what + " must leave the finite range, got " + got, !isFinite(got));
            return;
        }
        if (!isFinite(got)) {
            fail(what + " is " + got + " but the exact value is " + want.round(MC));
        }
        BigDecimal err = big(got).subtract(want, MC);
        BigDecimal bound = mod2.multiply(new BigDecimal(tol).multiply(new BigDecimal(tol), MC), MC);
        assertTrue(what + ": want " + want.round(MC) + ", got " + got,
                err.multiply(err, MC).compareTo(bound) <= 0);
    }

    /** the computed value against the exact one, relative to the exact modulus */
    private void exact(String what, BigDecimal wantRe, BigDecimal wantIm, ComplexF got, double tol) {
        BigDecimal mod2 = wantRe.multiply(wantRe, MC).add(wantIm.multiply(wantIm, MC), MC);
        if (mod2.signum() == 0 || mod2.compareTo(FLOOR) < 0) {
            // gradual underflow, nothing relative to say
            return;
        }
        component(what + " re", wantRe, mod2, got.re(), tol);
        component(what + " im", wantIm, mod2, got.im(), tol);
        ++compared;
    }

    private static boolean isFinite(float x) {
        return !Float.isNaN(x) && !Float.isInfinite(x);
    }

    /** the computed value against a complex one, relative to its modulus */
    private static void close(String what, ComplexF want, ComplexF got, double tol) {
        double scale = want.abs();
        double err = Math.hypot((double) got.re() - want.re(), (double) got.im() - want.im());
        assertTrue(what + ": want " + want + ", got " + got + ", relative error " + (err / scale),
                err <= tol * scale);
    }

    // ================= 1. exact arithmetic as the oracle =================

    @Test
    public void testAbsAgreesWithHypot() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                float want = (float) Math.hypot(a, b);
                float got = new ComplexF(a, b).abs();
                assertTrue("abs" + at(a, b) + ": want " + want + ", got " + got,
                        Math.abs(got - want) <= 2.0f * Math.ulp(want));
            }
        }
        for (float[] v : SPECIAL) {
            // by value, since abs keeps the sign of a negative zero
            float want = (float) Math.hypot(v[0], v[1]);
            assertEquals("abs" + at(v[0], v[1]), want, new ComplexF(v[0], v[1]).abs(), 2.0f * Math.ulp(want));
        }
    }

    @Test
    public void testAbsIsNeverNegative() {
        // a modulus carries no sign, not even on a zero
        same("abs(-0.0, 0.0)", 0.0f, new ComplexF(-0.0f, 0.0f).abs());
        same("abs(-0.0, -0.0)", 0.0f, new ComplexF(-0.0f, -0.0f).abs());
        same("abs(0.0, -0.0)", 0.0f, new ComplexF(0.0f, -0.0f).abs());
        same("abs(0.0, 0.0)", 0.0f, ComplexF.Zero().abs());
        same("static abs(-0.0, 0.0)", 0.0f, ComplexF.abs(-0.0f, 0.0f));
        same("static abs(-0.0, -0.0)", 0.0f, ComplexF.abs(-0.0f, -0.0f));
    }

    @Test
    public void testMultiplicationOverflowsToNan() {
        // the plain formula is not rescaled, so a product that leaves the range
        // can cancel to NaN instead of reaching infinity
        same("(1e30,1e30)^2", NAN, INF, new ComplexF(1.0e30f, 1.0e30f).mul(new ComplexF(1.0e30f, 1.0e30f)));
        // where nothing cancels it does reach infinity
        same("(1e30,0)*(1e30,0)", INF, 0.0f, new ComplexF(1.0e30f, 0.0f).mul(new ComplexF(1.0e30f, 0.0f)));
    }

    @Test
    public void testMultiplicationAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 2) {
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (Math.pow(10.0, e) * Math.cos(angle(k)));
                float b = (float) (Math.pow(10.0, e) * Math.sin(angle(k)));
                for (int f = -20; f <= 20; f += 10) {
                    float c = (float) (Math.pow(10.0, f) * Math.cos(angle(k) + 1.1));
                    float d = (float) (Math.pow(10.0, f) * Math.sin(angle(k) + 1.1));
                    BigDecimal wantRe = big(a).multiply(big(c), MC).subtract(big(b).multiply(big(d), MC), MC);
                    BigDecimal wantIm = big(a).multiply(big(d), MC).add(big(b).multiply(big(c), MC), MC);
                    exact("mul" + at(a, b), wantRe, wantIm, new ComplexF(a, b).mul(new ComplexF(c, d)), 1.0e-6);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testDivisionAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 2) {
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (Math.pow(10.0, e) * Math.cos(angle(k)));
                float b = (float) (Math.pow(10.0, e) * Math.sin(angle(k)));
                for (int f = -20; f <= 20; f += 10) {
                    float c = (float) (Math.pow(10.0, f) * Math.cos(angle(k) + 1.1));
                    float d = (float) (Math.pow(10.0, f) * Math.sin(angle(k) + 1.1));
                    BigDecimal den = big(c).multiply(big(c), MC).add(big(d).multiply(big(d), MC), MC);
                    BigDecimal wantRe = big(a).multiply(big(c), MC).add(big(b).multiply(big(d), MC), MC)
                            .divide(den, MC);
                    BigDecimal wantIm = big(b).multiply(big(c), MC).subtract(big(a).multiply(big(d), MC), MC)
                            .divide(den, MC);
                    exact("div" + at(a, b), wantRe, wantIm, new ComplexF(a, b).div(new ComplexF(c, d)), 1.0e-6);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testSumAndDifferenceAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; e += 2) {
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (Math.pow(10.0, e) * Math.cos(angle(k)));
                float b = (float) (Math.pow(10.0, e) * Math.sin(angle(k)));
                for (int f = -20; f <= 20; f += 10) {
                    float c = (float) (Math.pow(10.0, f) * Math.cos(angle(k) + 1.1));
                    float d = (float) (Math.pow(10.0, f) * Math.sin(angle(k) + 1.1));
                    // a single rounding costs at most half an ulp
                    exact("add" + at(a, b), big(a).add(big(c), MC), big(b).add(big(d), MC),
                            new ComplexF(a, b).add(new ComplexF(c, d)), Math.ulp(1.0f));
                    exact("sub" + at(a, b), big(a).subtract(big(c), MC), big(b).subtract(big(d), MC),
                            new ComplexF(a, b).sub(new ComplexF(c, d)), Math.ulp(1.0f));
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testInverseAgainstExactArithmetic() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                BigDecimal den = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                exact("inv" + at(a, b), big(a).divide(den, MC), big(b).negate().divide(den, MC),
                        new ComplexF(a, b).inv(), 1.0e-6);
            }
        }
        assertTrue("too few points compared: " + compared, compared > 300);
    }

    @Test
    public void testSquareRootSquaredIsTheOriginal() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                ComplexF root = new ComplexF(a, b).sqrt();
                BigDecimal x = big(root.re());
                BigDecimal y = big(root.im());
                // square the root exactly, without going through mul
                BigDecimal gotRe = x.multiply(x, MC).subtract(y.multiply(y, MC), MC);
                BigDecimal gotIm = x.multiply(y, MC).multiply(new BigDecimal(2), MC);
                BigDecimal mod2 = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                BigDecimal errRe = gotRe.subtract(big(a), MC);
                BigDecimal errIm = gotIm.subtract(big(b), MC);
                BigDecimal err2 = errRe.multiply(errRe, MC).add(errIm.multiply(errIm, MC), MC);
                BigDecimal tol = new BigDecimal(1.0e-6);
                assertTrue("sqrt" + at(a, b) + " squared is " + gotRe.round(MC) + ", " + gotIm.round(MC),
                        err2.compareTo(mod2.multiply(tol.multiply(tol, MC), MC)) <= 0);
            }
        }
    }

    @Test
    public void testSquareRootIsThePrincipalValue() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                ComplexF root = new ComplexF(a, b).sqrt();
                assertTrue("sqrt" + at(a, b) + " has a negative real part", root.re() >= 0.0f);
                assertEquals("sqrt" + at(a, b) + " flipped the imaginary sign", Math.signum(b),
                        Math.signum(root.im()), 0.0f);
            }
        }
    }

    @Test
    public void testExpAndLnAreInverse() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF want = new ComplexF((float) (r * Math.cos(angle(k))), (float) (r * Math.sin(angle(k))));
                // ln of a large modulus is large, and exp turns that back into a
                // relative error, so 1e-4 is as tight as single precision gets
                close("exp(ln z)", want, want.ln().exp(), 1.0e-4);
            }
        }
    }

    @Test
    public void testExpHasTheModulusAndAngleItShould() {
        for (float re = -80.0f; re <= 80.0f; re += 10.0f) {
            for (int k = 0; k < ANGLES; ++k) {
                float im = (float) angle(k);
                ComplexF got = new ComplexF(re, im).exp();
                double wantAbs = Math.exp(re);
                assertTrue("|exp| at re=" + re + ": want " + wantAbs + ", got " + got.abs(),
                        Math.abs(got.abs() - wantAbs) <= 1.0e-5 * wantAbs);
                // sin of the difference closes the angle over the branch cut
                assertTrue("arg(exp) at im=" + im + ", got " + got.arg(),
                        Math.abs(Math.sin(got.arg() - im)) <= 1.0e-5);
            }
        }
    }

    @Test
    public void testExpGoesThroughDoublePrecision() {
        // no halving here, but the intermediate is a double, so the product
        // survives well past the point where a float e^re would have overflown
        ComplexF got = new ComplexF(100.0f, (float) (Math.PI / 2.0)).exp();
        assertTrue("the real part collapsed to " + got.re(), isFinite(got.re()) && got.re() != 0.0f);
        assertTrue("the imaginary part should overflow", Float.isInfinite(got.im()));
        // an exact zero is kept, not turned into inf * 0
        same("exp(710)", INF, 0.0f, new ComplexF(710.0f, 0.0f).exp());
        // once the double overflows nothing is finite any more, and unlike
        // ComplexD there is no halving to fall back on
        got = new ComplexF(710.0f, (float) (Math.PI / 2.0)).exp();
        assertTrue("the real part should overflow", Float.isInfinite(got.re()));
    }

    @Test
    public void testArgIsConsistentWithTheComponents() {
        for (int e = -15; e <= 15; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                ComplexF v = new ComplexF(a, b);
                double abs = v.abs();
                double phi = v.arg();
                assertTrue("abs*cos(arg)" + at(a, b), Math.abs(abs * Math.cos(phi) - a) <= 1.0e-6 * abs);
                assertTrue("abs*sin(arg)" + at(a, b), Math.abs(abs * Math.sin(phi) - b) <= 1.0e-6 * abs);
            }
        }
    }

    @Test
    public void testIntegerPowAgainstRepeatedMultiplication() {
        for (int e = -5; e <= 5; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF base = new ComplexF((float) (r * Math.cos(angle(k))), (float) (r * Math.sin(angle(k))));
                for (int n = 1; n <= 3; ++n) {
                    BigDecimal wantRe = big(base.re());
                    BigDecimal wantIm = big(base.im());
                    for (int i = 1; i < n; ++i) {
                        BigDecimal nextRe = wantRe.multiply(big(base.re()), MC)
                                .subtract(wantIm.multiply(big(base.im()), MC), MC);
                        wantIm = wantRe.multiply(big(base.im()), MC).add(wantIm.multiply(big(base.re()), MC), MC);
                        wantRe = nextRe;
                    }
                    // the path through ln and exp costs most of the digits
                    exact("pow " + n, wantRe, wantIm, base.pow((float) n), 1.0e-4);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    // ================= 2. the infinity convention, as literals =================

    @Test
    public void testMultiplicationWithInfinity() {
        same("(2,3)*(4,5)", -7.0f, 22.0f, new ComplexF(2.0f, 3.0f).mul(new ComplexF(4.0f, 5.0f)));
        same("(inf,0)*(2,0)", INF, 0.0f, new ComplexF(INF, 0.0f).mul(new ComplexF(2.0f, 0.0f)));
        same("(inf,0)*(0,2)", 0.0f, INF, new ComplexF(INF, 0.0f).mul(new ComplexF(0.0f, 2.0f)));
        same("(1,0)*(inf,inf)", INF, INF, ComplexF.One().mul(ComplexF.Inf()));
        same("(-inf,0)*(2,0)", -INF, 0.0f, new ComplexF(-INF, 0.0f).mul(new ComplexF(2.0f, 0.0f)));
        // zero times infinity has neither direction nor modulus
        same("(inf,0)*(0,0)", NAN, NAN, new ComplexF(INF, 0.0f).mul(ComplexF.Zero()));
        same("(-0.0,0)*(inf,0)", NAN, NAN, new ComplexF(-0.0f, 0.0f).mul(new ComplexF(INF, 0.0f)));
        // against an infinite operand a NaN component counts as zero
        same("(inf,NaN)*(2,0)", INF, 0.0f, new ComplexF(INF, NAN).mul(new ComplexF(2.0f, 0.0f)));
        // but (NaN,NaN) then has no direction left at all
        same("(inf,inf)*(NaN,NaN)", NAN, NAN, ComplexF.Inf().mul(ComplexF.NaN()));
        same("(NaN,NaN)*(2,0)", NAN, NAN, ComplexF.NaN().mul(new ComplexF(2.0f, 0.0f)));
    }

    @Test
    public void testDivisionWithZeroAndInfinity() {
        same("(1,0)/(0,0)", INF, INF, ComplexF.One().div(ComplexF.Zero()));
        same("(0,0)/(0,0)", NAN, NAN, ComplexF.Zero().div(ComplexF.Zero()));
        same("(2,0)/(inf,inf)", 0.0f, 0.0f, new ComplexF(2.0f, 0.0f).div(ComplexF.Inf()));
        same("(inf,0)/(2,0)", INF, 0.0f, new ComplexF(INF, 0.0f).div(new ComplexF(2.0f, 0.0f)));
        same("(inf,0)/(0,2)", 0.0f, -INF, new ComplexF(INF, 0.0f).div(new ComplexF(0.0f, 2.0f)));
        // infinity over infinity has no direction
        same("(inf,0)/(inf,0)", NAN, NAN, new ComplexF(INF, 0.0f).div(new ComplexF(INF, 0.0f)));
        same("(0,0)/(2,0)", 0.0f, 0.0f, ComplexF.Zero().div(new ComplexF(2.0f, 0.0f)));
        same("(NaN,NaN)/(2,0)", NAN, NAN, ComplexF.NaN().div(new ComplexF(2.0f, 0.0f)));
    }

    @Test
    public void testScaleWithInfinity() {
        same("(2,3)*1", 2.0f, 3.0f, new ComplexF(2.0f, 3.0f).scale(1.0f));
        same("(2,3)*inf", INF, INF, new ComplexF(2.0f, 3.0f).scale(INF));
        same("(2,-3)*inf", INF, -INF, new ComplexF(2.0f, -3.0f).scale(INF));
        same("(inf,0)*2", INF, 0.0f, new ComplexF(INF, 0.0f).scale(2.0f));
        same("(inf,0)*0", NAN, NAN, new ComplexF(INF, 0.0f).scale(0.0f));
        same("(0,0)*inf", NAN, NAN, ComplexF.Zero().scale(INF));
    }

    @Test
    public void testInverseAtTheEdges() {
        same("1/(0,0)", INF, INF, ComplexF.Zero().inv());
        same("1/(inf,inf)", 0.0f, 0.0f, ComplexF.Inf().inv());
        same("1/(inf,0)", 0.0f, 0.0f, new ComplexF(INF, 0.0f).inv());
        same("1/(2,0)", 0.5f, -0.0f, new ComplexF(2.0f, 0.0f).inv());
        same("1/(0,2)", 0.0f, -0.5f, new ComplexF(0.0f, 2.0f).inv());
        same("1/(NaN,NaN)", NAN, NAN, ComplexF.NaN().inv());
    }

    @Test
    public void testSquareRootAtTheEdges() {
        same("sqrt(4)", 2.0f, 0.0f, new ComplexF(4.0f, 0.0f).sqrt());
        same("sqrt(-4)", 0.0f, 2.0f, new ComplexF(-4.0f, 0.0f).sqrt());
        // copySign carries the branch cut
        same("sqrt(-4-0i)", 0.0f, -2.0f, new ComplexF(-4.0f, -0.0f).sqrt());
        same("sqrt(0)", 0.0f, 0.0f, ComplexF.Zero().sqrt());
        same("sqrt(-0-0i)", 0.0f, -0.0f, new ComplexF(-0.0f, -0.0f).sqrt());
        same("sqrt(inf,1)", INF, 0.0f, new ComplexF(INF, 1.0f).sqrt());
        same("sqrt(-inf,1)", 0.0f, INF, new ComplexF(-INF, 1.0f).sqrt());
        // an infinite imaginary part decides, whatever the real part is
        same("sqrt(1,inf)", INF, INF, new ComplexF(1.0f, INF).sqrt());
        same("sqrt(1,-inf)", INF, -INF, new ComplexF(1.0f, -INF).sqrt());
        same("sqrt(-inf,-inf)", INF, -INF, new ComplexF(-INF, -INF).sqrt());
        same("sqrt(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).sqrt());
        same("sqrt(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).sqrt());
    }

    @Test
    public void testExpAndLnAtTheEdges() {
        same("exp(0)", 1.0f, 0.0f, ComplexF.Zero().exp());
        same("exp(-inf)", 0.0f, 0.0f, new ComplexF(-INF, 0.0f).exp());
        same("exp(inf)", INF, 0.0f, new ComplexF(INF, 0.0f).exp());
        // C99 Annex G: exp of NaN + i0 stays on the real axis
        same("exp(NaN)", NAN, 0.0f, new ComplexF(NAN, 0.0f).exp());
        same("exp(NaN - 0i)", NAN, -0.0f, new ComplexF(NAN, -0.0f).exp());
        // a nonzero imaginary part lets the NaN through
        same("exp(NaN + i)", NAN, NAN, new ComplexF(NAN, 1.0f).exp());
        same("exp(0,inf)", NAN, NAN, new ComplexF(0.0f, INF).exp());
        same("ln(0)", -INF, 0.0f, ComplexF.Zero().ln());
        same("ln(-1)", 0.0f, (float) Math.PI, new ComplexF(-1.0f, 0.0f).ln());
        same("ln(inf,0)", INF, 0.0f, new ComplexF(INF, 0.0f).ln());
        same("ln(NaN,0)", NAN, NAN, new ComplexF(NAN, 0.0f).ln());
    }

    @Test
    public void testPowWithARealExponentAtTheEdges() {
        same("0^0", 1.0f, 0.0f, ComplexF.Zero().pow(0.0f));
        same("0^2", 0.0f, 0.0f, ComplexF.Zero().pow(2.0f));
        same("0^-2", INF, INF, ComplexF.Zero().pow(-2.0f));
        same("inf^2", INF, INF, ComplexF.Inf().pow(2.0f));
        same("inf^-2", 0.0f, 0.0f, ComplexF.Inf().pow(-2.0f));
        same("0^NaN", NAN, NAN, ComplexF.Zero().pow(NAN));
        same("NaN^2", NAN, NAN, ComplexF.NaN().pow(2.0f));
        // an infinite exponent takes the values of Math.pow, with the modulus
        same("2^inf", INF, INF, new ComplexF(2.0f, 0.0f).pow(INF));
        same("2^-inf", 0.0f, 0.0f, new ComplexF(2.0f, 0.0f).pow(-INF));
        same("0.5^inf", 0.0f, 0.0f, new ComplexF(0.5f, 0.0f).pow(INF));
        same("0.5^-inf", INF, INF, new ComplexF(0.5f, 0.0f).pow(-INF));
        // modulus one has no limit to go to
        same("i^inf", NAN, NAN, ComplexF.I().pow(INF));
    }

    @Test
    public void testPowWithAComplexExponentAtTheEdges() {
        same("0^0", 1.0f, 0.0f, ComplexF.Zero().pow(ComplexF.Zero()));
        same("0^2", 0.0f, 0.0f, ComplexF.Zero().pow(new ComplexF(2.0f, 0.0f)));
        // a purely imaginary exponent is not defined for a degenerate base
        same("0^2i", NAN, NAN, ComplexF.Zero().pow(new ComplexF(0.0f, 2.0f)));
        same("0^(2+3i)", 0.0f, 0.0f, ComplexF.Zero().pow(new ComplexF(2.0f, 3.0f)));
        same("2^(inf,0)", INF, INF, new ComplexF(2.0f, 0.0f).pow(new ComplexF(INF, 0.0f)));
        // an infinite exponent that is not real has no direction
        same("2^(inf,1)", NAN, NAN, new ComplexF(2.0f, 0.0f).pow(new ComplexF(INF, 1.0f)));
    }

    @Test
    public void testFromPolar() {
        same("fromPolar(0,1)", 0.0f, 0.0f, ComplexF.fromPolar(0.0f, 1.0f));
        // an exact zero stays zero even for an infinite radius
        same("fromPolar(inf,0)", INF, 0.0f, ComplexF.fromPolar(INF, 0.0f));
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF got = ComplexF.fromPolar(r, (float) angle(k));
                assertTrue("fromPolar radius at 1e" + e, Math.abs(got.abs() - r) <= 1.0e-6 * r);
            }
        }
    }

    @Test
    public void testFromPolarRejectsANegativeRadius() {
        try {
            ComplexF.fromPolar(-1.0f, 0.0f);
            fail("a negative radius should be rejected");
        } catch (IllegalArgumentException expected) {
            // that is the contract
        }
    }

    @Test
    public void testTheStaticAbsMatchesTheInstanceOne() {
        for (float[] v : SPECIAL) {
            // the static form has no isInfinite() guard, so it only has to agree
            // where nothing is infinite
            if (!new ComplexF(v[0], v[1]).isInfinite()) {
                same("abs" + at(v[0], v[1]), new ComplexF(v[0], v[1]).abs(), ComplexF.abs(v[0], v[1]));
            }
        }
    }

    // ---------- the hyperbolic and trigonometric pair ----------

    @Test
    public void testSinhAndCoshAgainstExp() {
        // sinh(z) = (exp(z) - exp(-z))/2 and cosh(z) = (exp(z) + exp(-z))/2,
        // through the exp that the tests above have already pinned down
        for (float x = -20.0f; x <= 20.0f; x += 2.5f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) (3.0 * angle(k)));
                ComplexF ez = v.exp();
                ComplexF em = v.neg().exp();
                close("sinh" + at(x, v.im()), ez.sub(em).scale(0.5f), v.sinh(), 1.0e-5);
                close("cosh" + at(x, v.im()), ez.add(em).scale(0.5f), v.cosh(), 1.0e-5);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testThePythagoreanIdentities() {
        // cosh^2 - sinh^2 == 1 and sin^2 + cos^2 == 1, through mul and sub
        for (float x = -6.0f; x <= 6.0f; x += 0.75f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) angle(k));
                ComplexF ch = v.cosh();
                ComplexF sh = v.sinh();
                // the squares cancel down to 1, so the bound goes with them
                unit("cosh^2 - sinh^2" + at(x, v.im()), ch.mul(ch).sub(sh.mul(sh)), ch.abs() * ch.abs());
                ComplexF s = v.sin();
                ComplexF c = v.cos();
                unit("sin^2 + cos^2" + at(x, v.im()), s.mul(s).add(c.mul(c)), c.abs() * c.abs());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    /** the value should be one, to within the size of the terms that cancelled */
    private static void unit(String what, ComplexF got, double terms) {
        double err = Math.hypot((double) got.re() - 1.0, got.im());
        double bound = 1.0e-6 * Math.max(1.0, terms);
        assertTrue(what + ": got " + got + ", off by " + err + ", bound " + bound, err <= bound);
    }

    @Test
    public void testOnTheRealAndImaginaryAxes() {
        for (float x = -6.0f; x <= 6.0f; x += 0.25f) {
            // a real argument gives a real sine and cosine, bit for bit
            same("sin re" + at(x, 0.0f), (float) Math.sin(x), new ComplexF(x, 0.0f).sin().re());
            same("cos re" + at(x, 0.0f), (float) Math.cos(x), new ComplexF(x, 0.0f).cos().re());
            same("sinh re" + at(x, 0.0f), (float) Math.sinh(x), new ComplexF(x, 0.0f).sinh().re());
            same("cosh re" + at(x, 0.0f), (float) Math.cosh(x), new ComplexF(x, 0.0f).cosh().re());
            assertEquals("sin im" + at(x, 0.0f), 0.0f, new ComplexF(x, 0.0f).sin().im(), 0.0f);
            assertEquals("cosh im" + at(x, 0.0f), 0.0f, new ComplexF(x, 0.0f).cosh().im(), 0.0f);
            // an imaginary argument turns sine into sinh and cosine into cosh
            same("sin(iy) im" + at(0.0f, x), (float) Math.sinh(x), new ComplexF(0.0f, x).sin().im());
            same("cos(iy) re" + at(0.0f, x), (float) Math.cosh(x), new ComplexF(0.0f, x).cos().re());
        }
    }

    @Test
    public void testParity() {
        // sinh and sin are odd, cosh and cos are even
        for (float x = -6.0f; x <= 6.0f; x += 0.75f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) angle(k));
                ComplexF n = v.neg();
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
        for (float x = -3.0f; x <= 3.0f; x += 1.5f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF a = new ComplexF(x, (float) angle(k));
                ComplexF b = new ComplexF(0.5f - x, (float) (angle(k) / 2.0));
                ComplexF want = a.sinh().mul(b.cosh()).add(a.cosh().mul(b.sinh()));
                ComplexF got = a.add(b).sinh();
                // the two terms are far larger than their sum, so the bound
                // goes with the terms and not with the result
                double terms = (double) a.sinh().abs() * b.cosh().abs()
                        + (double) a.cosh().abs() * b.sinh().abs();
                double err = Math.hypot((double) got.re() - want.re(), (double) got.im() - want.im());
                assertTrue("sinh(a+b)" + at(x, a.im()) + ": off by " + err + ", terms " + terms,
                        err <= 1.0e-6 * terms);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 30);
    }

    @Test
    public void testTheOverflowBandWhereCoshOutlivesExp() {
        // the float range ends near 88, and the double intermediate carries the
        // product right up to it
        ComplexF v = new ComplexF(89.0f, 0.0f).sinh();
        assertTrue("sinh(89) collapsed to " + v.re(), isFinite(v.re()));
        same("sinh(89) re", (float) Math.sinh(89.0), v.re());
        assertEquals("sinh(89) im", 0.0f, v.im(), 0.0f);
        // sin(1 + 88i) keeps both components
        ComplexF w = new ComplexF(1.0f, 88.0f).sin();
        same("sin(1+88i) re", 6.9490197E37f, w.re());
        same("sin(1+88i) im", 4.4619146E37f, w.im());
        // one step further nothing is finite any more
        assertTrue("sinh(90)", Float.isInfinite(new ComplexF(90.0f, 0.0f).sinh().re()));
    }

    @Test
    public void testTheHyperbolicPairAtTheEdges() {
        same("sinh(0)", 0.0f, 0.0f, ComplexF.Zero().sinh());
        same("cosh(0)", 1.0f, 0.0f, ComplexF.Zero().cosh());
        same("sin(0)", 0.0f, 0.0f, ComplexF.Zero().sin());
        same("cos(0)", 1.0f, -0.0f, ComplexF.Zero().cos());
        // an exact zero survives a value that has none, as in exp
        same("sinh(NaN)", NAN, 0.0f, new ComplexF(NAN, 0.0f).sinh());
        same("cosh(NaN)", NAN, 0.0f, new ComplexF(NAN, 0.0f).cosh());
        same("cosh(0,inf)", NAN, 0.0f, new ComplexF(0.0f, INF).cosh());
        same("sinh(0,inf)", 0.0f, NAN, new ComplexF(0.0f, INF).sinh());
        // an infinite real part still takes its direction from cos and sin
        same("cosh(inf,0)", INF, 0.0f, new ComplexF(INF, 0.0f).cosh());
        same("cosh(-inf,0)", INF, -0.0f, new ComplexF(-INF, 0.0f).cosh());
        same("sinh(inf,2)", -INF, INF, new ComplexF(INF, 2.0f).sinh());
        same("sinh(-inf,0)", -INF, 0.0f, new ComplexF(-INF, 0.0f).sinh());
        // both parts unbounded: the modulus survives, the direction does not
        same("sinh(inf,inf)", INF, NAN, ComplexF.Inf().sinh());
        same("cosh(inf,inf)", INF, NAN, ComplexF.Inf().cosh());
        same("sin(inf,inf)", NAN, -INF, ComplexF.Inf().sin());
        same("cos(inf,inf)", INF, NAN, ComplexF.Inf().cos());
        same("cosh(inf,NaN)", INF, NAN, new ComplexF(INF, NAN).cosh());
        // a finite real part against a value that has none
        same("cosh(1,inf)", NAN, NAN, new ComplexF(1.0f, INF).cosh());
        same("sin(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).sin());
        same("cosh(NaN,NaN)", NAN, NAN, ComplexF.NaN().cosh());
    }

    @Test
    public void testWhereTheSignOfAZeroIsLeftOpen() {
        // C99 does not fix the sign of this zero; the C library answers +0.0,
        // the formula here yields -0.0. Pinned so a change is noticed.
        same("cos(inf,0) im", -0.0f, new ComplexF(INF, 0.0f).cos().im());
        same("cos(-inf,0) im", -0.0f, new ComplexF(-INF, 0.0f).cos().im());
        same("cos(NaN,0) im", -0.0f, new ComplexF(NAN, 0.0f).cos().im());
    }

    // ---------- the tangent pair ----------

    @Test
    public void testTanhAgainstTheQuotient() {
        // tanh = sinh/cosh, through the three that are already pinned down,
        // wherever sinh and cosh still fit into a float
        for (float x = -80.0f; x <= 80.0f; x += 0.8f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) angle(k));
                ComplexF sh = v.sinh();
                ComplexF ch = v.cosh();
                if (!isFinite(ch.re()) || !isFinite(ch.im()) || !isFinite(sh.re()) || !isFinite(sh.im())) {
                    continue;
                }
                ComplexF want = sh.div(ch);
                if (!isFinite(want.re()) || !isFinite(want.im())) {
                    continue;
                }
                close("tanh" + at(x, v.im()), want, v.tanh(), 1.0e-6);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 500);
    }

    @Test
    public void testTanhWhereTheQuotientBreaks() {
        // from 90 on the quotient has nothing left in single precision;
        // tanh saturates instead. That is the point of the whole method.
        for (float x = 100.0f; x <= 400.0f; x += 50.0f) {
            for (int s = -1; s <= 1; s += 2) {
                ComplexF v = new ComplexF(s * x, 1.0f);
                ComplexF got = v.tanh();
                same("tanh re at " + (s * x), (float) s, got.re());
                assertEquals("tanh im at " + (s * x), 0.0f, got.im(), 0.0f);
                assertTrue("the quotient should have failed at " + (s * x),
                        Float.isNaN(v.sinh().div(v.cosh()).re()));
            }
        }
    }

    @Test
    public void testTanhOnTheRealAndImaginaryAxes() {
        for (float x = -25.0f; x <= 25.0f; x += 0.25f) {
            // the real axis is Math.tanh itself, bit for bit
            same("tanh re" + at(x, 0.0f), (float) Math.tanh(x), new ComplexF(x, 0.0f).tanh().re());
            assertEquals("tanh im" + at(x, 0.0f), 0.0f, new ComplexF(x, 0.0f).tanh().im(), 0.0f);
            // and the imaginary axis turns tan into tanh
            same("tan(iy) im" + at(0.0f, x), (float) Math.tanh(x), new ComplexF(0.0f, x).tan().im());
            assertEquals("tan(iy) re" + at(0.0f, x), 0.0f, new ComplexF(0.0f, x).tan().re(), 0.0f);
        }
    }

    @Test
    public void testTanhAndTanAreOdd() {
        for (float x = -6.0f; x <= 6.0f; x += 0.75f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) angle(k));
                ComplexF n = v.neg();
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
        for (float x = -4.0f; x <= 4.0f; x += 0.5f) {
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF(x, (float) (angle(k) / 2.0));
                ComplexF th = v.tanh();
                ComplexF want = th.scale(2.0f).div(ComplexF.One().add(th.mul(th)));
                ComplexF got = v.scale(2.0f).tanh();
                // the denominator can come close to zero, so the bound goes
                // with the terms and not with the result
                double terms = 2.0 * th.abs() + (double) th.abs() * th.abs();
                double err = Math.hypot((double) got.re() - want.re(), (double) got.im() - want.im());
                assertTrue("tanh(2z)" + at(x, v.im()) + ": off by " + err + ", terms " + terms,
                        err <= 1.0e-5 * Math.max(1.0, terms));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testTheTangentPairAtTheEdges() {
        same("tanh(0)", 0.0f, 0.0f, ComplexF.Zero().tanh());
        same("tanh(-0,-0)", -0.0f, -0.0f, new ComplexF(-0.0f, -0.0f).tanh());
        same("tan(0)", 0.0f, 0.0f, ComplexF.Zero().tan());
        // an infinite real part saturates, the imaginary part dies away
        same("tanh(inf,1)", 1.0f, 0.0f, new ComplexF(INF, 1.0f).tanh());
        same("tanh(-inf,1)", -1.0f, 0.0f, new ComplexF(-INF, 1.0f).tanh());
        same("tanh(inf,inf)", 1.0f, 0.0f, ComplexF.Inf().tanh());
        same("tanh(inf,-inf)", 1.0f, -0.0f, new ComplexF(INF, -INF).tanh());
        same("tanh(inf,NaN)", 1.0f, 0.0f, new ComplexF(INF, NAN).tanh());
        // a NaN real part keeps a value on the real axis real
        same("tanh(NaN,0)", NAN, 0.0f, new ComplexF(NAN, 0.0f).tanh());
        same("tanh(NaN,-0.0)", NAN, -0.0f, new ComplexF(NAN, -0.0f).tanh());
        same("tanh(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).tanh());
        // an unbounded imaginary part against a finite real part has no value
        same("tanh(1,inf)", NAN, NAN, new ComplexF(1.0f, INF).tanh());
        same("tanh(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).tanh());
        same("tan(inf,0)", NAN, NAN, new ComplexF(INF, 0.0f).tan());
        // but the tangent does have a limit along the imaginary axis
        same("tan(1,inf)", 0.0f, 1.0f, new ComplexF(1.0f, INF).tan());
        same("tan(0,inf)", 0.0f, 1.0f, new ComplexF(0.0f, INF).tan());
    }

    // ---------- the n-th roots ----------

    /** w raised to the n-th power, exactly, against z */
    private void power(String what, ComplexF w, int n, ComplexF z, double tol) {
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
            for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
                float r = (float) Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                            (float) (r * Math.sin(angle(k))));
                    // the path through pow, cos and sin costs about 8e-7 here
                    power("nthRoot " + n + at(v.re(), v.im()), v.nthRoot(n), n, v, 1.0e-5);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 2000);
    }

    @Test
    public void testNthRootsAreTheCompleteSet() {
        for (int n = 1; n <= 8; ++n) {
            for (int e = -6; e <= 6; ++e) {
                float r = (float) Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                            (float) (r * Math.sin(angle(k))));
                    ComplexF[] roots = v.nthRoots(n);
                    assertEquals("nthRoots(" + n + ") length", n, roots.length);
                    for (int i = 0; i < n; ++i) {
                        // every one of them is a root
                        power("root " + i + " of " + n, roots[i], n, v, 1.0e-5);
                        // and they all sit on the same circle
                        assertTrue("modulus of root " + i + " of " + n,
                                Math.abs(roots[i].abs() - roots[0].abs()) <= 1.0e-6 * roots[0].abs());
                        // and no two of them are the same point
                        for (int j = 0; j < i; ++j) {
                            double gap = Math.hypot((double) roots[i].re() - roots[j].re(),
                                    (double) roots[i].im() - roots[j].im());
                            assertTrue("roots " + i + " and " + j + " of " + n + " coincide",
                                    gap > 1.0e-5 * roots[0].abs());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testTheTwoRootMethodsAgree() {
        // nthRoots(n)[0] is nthRoot(n), bit for bit, signed zeros included
        float[] tricky = { 0.0f, -0.0f, 1.0f, -1.0f, 3.0f, -3.0f, INF, NAN };
        for (int n = 1; n <= 8; ++n) {
            for (int e = MIN_EXP; e <= MAX_EXP; e += 3) {
                float r = (float) Math.pow(10.0, e);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                            (float) (r * Math.sin(angle(k))));
                    same("degree " + n + at(v.re(), v.im()), v.nthRoot(n).re(), v.nthRoots(n)[0].re());
                    same("degree " + n + at(v.re(), v.im()), v.nthRoot(n).im(), v.nthRoots(n)[0].im());
                }
            }
            for (float x : tricky) {
                for (float y : new float[] { 0.0f, -0.0f, 1.0f }) {
                    ComplexF v = new ComplexF(x, y);
                    same("degree " + n + at(x, y), v.nthRoot(n).re(), v.nthRoots(n)[0].re());
                    same("degree " + n + at(x, y), v.nthRoot(n).im(), v.nthRoots(n)[0].im());
                }
            }
        }
    }

    @Test
    public void testTheTwoSpecialDegrees() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                // degree one is the value itself, not a trip through cos and sin
                assertSame("nthRoot(1)" + at(v.re(), v.im()), v, v.nthRoot(1));
                assertSame("nthRoots(1)" + at(v.re(), v.im()), v, v.nthRoots(1)[0]);
                // degree two is sqrt, so the two never disagree
                ComplexF w = v.sqrt();
                same("nthRoot(2)" + at(v.re(), v.im()), w.re(), w.im(), v.nthRoot(2));
                ComplexF[] pair = v.nthRoots(2);
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
                new ComplexF(3.0f, 4.0f).nthRoot(n);
                fail("nthRoot(" + n + ") should be rejected");
            } catch (IllegalArgumentException expected) {
                // that is the contract
            }
            try {
                new ComplexF(3.0f, 4.0f).nthRoots(n);
                fail("nthRoots(" + n + ") should be rejected");
            } catch (IllegalArgumentException expected) {
                // that is the contract
            }
        }
    }

    @Test
    public void testNthRootAtTheEdges() {
        same("cbrt(0)", 0.0f, 0.0f, ComplexF.Zero().nthRoot(3));
        same("cbrt(-0,-0)", 0.0f, -0.0f, new ComplexF(-0.0f, -0.0f).nthRoot(3));
        same("cbrt(inf,0)", INF, 0.0f, new ComplexF(INF, 0.0f).nthRoot(3));
        // inf * cis(PI/3) has both parts unbounded
        same("cbrt(-inf,0)", INF, INF, new ComplexF(-INF, 0.0f).nthRoot(3));
        same("cbrt(0,inf)", INF, INF, new ComplexF(0.0f, INF).nthRoot(3));
        same("cbrt(NaN,0)", NAN, NAN, new ComplexF(NAN, 0.0f).nthRoot(3));
        same("cbrt(NaN,NaN)", NAN, NAN, ComplexF.NaN().nthRoot(3));
        // arg carries the branch cut, so the sign of the zero decides
        ComplexF up = new ComplexF(-4.0f, 0.0f).nthRoot(3);
        ComplexF down = new ComplexF(-4.0f, -0.0f).nthRoot(3);
        same("cbrt(-4+0i) re", 0.79370046f, up.re());
        same("cbrt(-4+0i) im", 1.3747296f, up.im());
        same("cbrt(-4-0i) re", 0.79370046f, down.re());
        same("cbrt(-4-0i) im", -1.3747296f, down.im());
        // a modulus is never negative any more, so fromPolar cannot refuse
        same("cbrt(-0.0, 0.0) re", 0.0f, new ComplexF(-0.0f, 0.0f).nthRoot(3).re());
    }

    // ---------- the inverse tangent pair ----------

    @Test
    public void testAtanhRoundTripThroughTanh() {
        // only up to modulus one: atanh folds the whole far field into a thin
        // strip around +-i*PI/2, and tanh magnifies any error there
        for (int e = MIN_EXP; e <= 0; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                close("tanh(atanh(z))" + at(v.re(), v.im()), v, v.atanh().tanh(), 1.0e-6);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testAtanhAgainstTheLogIdentity() {
        // 0.5*(ln(1+z) - ln(1-z)) through the ln that is already pinned down.
        // Only from modulus one up: below it 1 +- z rounds back to 1 and the
        // identity loses everything, which is why the code uses log1p.
        for (int e = 0; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF one = ComplexF.One();
                ComplexF want = one.add(v).ln().sub(one.sub(v).ln()).scale(0.5f);
                if (!isFinite(want.re()) || !isFinite(want.im()) || want.abs() == 0.0f) {
                    continue;
                }
                close("atanh" + at(v.re(), v.im()), want, v.atanh(), 1.0e-6);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testAtanhFarOut() {
        // far out atanh(z) is 1/z + i*PI/2, and inv is already pinned down
        for (int e = 10; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF got = v.atanh();
                float want = v.inv().re();
                assertTrue("real part at 1e" + e + ": want " + want + ", got " + got.re(),
                        Math.abs(got.re() - want) <= 1.0e-6 * Math.abs(want));
                same("imaginary part at 1e" + e,
                        Math.copySign((float) (Math.PI / 2.0), v.im()), got.im());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testAtanhOnTheRealAxis() {
        for (float x = -0.95f; x <= 0.95f; x += 0.05f) {
            ComplexF got = new ComplexF(x, 0.0f).atanh();
            // the stable real form; the plain log of the quotient loses digits
            // near zero
            double want = 0.5 * Math.log1p(2.0 * x / (1.0 - x));
            assertTrue("atanh(" + x + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-6 * Math.max(1.0, Math.abs(want)));
            assertEquals("atanh(" + x + ") stays real", 0.0f, got.im(), 0.0f);
        }
        // the denominator is exactly zero there and log1p carries it
        same("atanh(1)", INF, 0.0f, new ComplexF(1.0f, 0.0f).atanh());
        same("atanh(-1)", -INF, 0.0f, new ComplexF(-1.0f, 0.0f).atanh());
        // past one the value leaves the real axis by PI/2
        ComplexF two = new ComplexF(2.0f, 0.0f).atanh();
        assertTrue("atanh(2) real part", Math.abs(two.re() - 0.5 * Math.log(3.0)) <= 1.0e-6);
        same("atanh(2) imaginary part", (float) (Math.PI / 2.0), two.im());
    }

    @Test
    public void testAtanhAndAtanAreOdd() {
        for (int e = -6; e <= 6; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF n = v.neg();
                same("atanh odd re", v.atanh().neg().re(), n.atanh().re());
                same("atanh odd im", v.atanh().neg().im(), n.atanh().im());
                same("atan odd re", v.atan().neg().re(), n.atan().re());
                same("atan odd im", v.atan().neg().im(), n.atan().im());
            }
        }
    }

    @Test
    public void testTheRangeOfTheInverseTangent() {
        float half = (float) (Math.PI / 2.0);
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                float re = v.atan().re();
                assertTrue("Re(atan) out of range: " + re, re >= -half && re <= half);
                float im = v.atanh().im();
                assertTrue("Im(atanh) out of range: " + im, im >= -half && im <= half);
            }
        }
    }

    @Test
    public void testTheInverseTangentPairAtTheEdges() {
        float half = (float) (Math.PI / 2.0);
        same("atanh(0)", 0.0f, 0.0f, ComplexF.Zero().atanh());
        same("atanh(-0,-0)", -0.0f, -0.0f, new ComplexF(-0.0f, -0.0f).atanh());
        same("atan(0)", 0.0f, 0.0f, ComplexF.Zero().atan());
        // the modulus dies away, the angle saturates
        same("atanh(inf,0)", 0.0f, half, new ComplexF(INF, 0.0f).atanh());
        same("atanh(-inf,0)", -0.0f, half, new ComplexF(-INF, 0.0f).atanh());
        same("atanh(0,inf)", 0.0f, half, new ComplexF(0.0f, INF).atanh());
        same("atanh(inf,inf)", 0.0f, half, ComplexF.Inf().atanh());
        same("atanh(inf,-inf)", 0.0f, -half, new ComplexF(INF, -INF).atanh());
        // an unbounded imaginary part decides even against a NaN real part
        same("atanh(NaN,inf)", 0.0f, half, new ComplexF(NAN, INF).atanh());
        same("atanh(inf,NaN)", 0.0f, NAN, new ComplexF(INF, NAN).atanh());
        // a NaN real part has no value to give
        same("atanh(NaN,0)", NAN, NAN, new ComplexF(NAN, 0.0f).atanh());
        same("atanh(NaN,NaN)", NAN, NAN, ComplexF.NaN().atanh());
        // but an exact zero survives a NaN imaginary part
        same("atanh(0,NaN)", 0.0f, NAN, new ComplexF(0.0f, NAN).atanh());
        same("atanh(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).atanh());
        // the poles of atan sit on the imaginary axis at +-i
        same("atan(0,1)", 0.0f, INF, new ComplexF(0.0f, 1.0f).atan());
        same("atan(0,-1)", 0.0f, -INF, new ComplexF(0.0f, -1.0f).atan());
        same("atan(inf,0)", half, 0.0f, new ComplexF(INF, 0.0f).atan());
    }

    // ---------- the inverse sine pair ----------

    @Test
    public void testAsinhRoundTripThroughSinh() {
        // usable over the whole range, unlike tanh(atanh(z)): measured 0 below
        // modulus one, 6.0e-8 at one, 1.5e-6 at 1e30
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            double tol = 2.0e-7 * Math.max(1.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                close("sinh(asinh(z))" + at(v.re(), v.im()), v, v.asinh().sinh(), tol);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testAsinAgainstSin() {
        for (int e = -8; e <= 8; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                close("sin(asin(z))" + at(v.re(), v.im()), v, v.asin().sin(), 5.0e-6);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testAsinhFarOut() {
        // far out asinh(z) is ln(2z), and asinh is odd, so the angle is taken
        // against |x|. From 1e8 up the difference is at the float noise floor.
        for (int e = 8; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float x = (float) (r * Math.cos(angle(k)));
                float y = (float) (r * Math.sin(angle(k)));
                ComplexF got = new ComplexF(x, y).asinh();
                double wantRe = Math.copySign(0.6931471805599453 + Math.log(new ComplexF(x, y).abs()), x);
                double wantIm = Math.atan2(y, Math.abs(x));
                assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                        Math.abs(got.re() - wantRe) <= 1.0e-6 * Math.abs(wantRe));
                assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                        Math.abs(got.im() - wantIm) <= 1.0e-6);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testAsinhAtTheTopOfTheRange() {
        // ComplexD needs a far field branch above 1e307; here the roots are
        // taken in double, the products stay below 1e38, and the top of the
        // float range comes out of the ordinary formula
        float big = 1.0e38f;
        float[][] points = { { big, big }, { -big, big }, { big, -big }, { -big, -big },
                { Float.MAX_VALUE, 0.0f }, { 0.0f, big }, { big, 1.0f }, { 1.0f, big } };
        for (int i = 0; i < points.length; ++i) {
            float x = points[i][0];
            float y = points[i][1];
            ComplexF got = new ComplexF(x, y).asinh();
            double wantRe = Math.copySign(0.6931471805599453 + Math.log(new ComplexF(x, y).abs()), x);
            double wantIm = Math.atan2(y, Math.abs(x));
            assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                    Math.abs(got.re() - wantRe) <= 1.0e-6 * Math.abs(wantRe));
            assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                    Math.abs(got.im() - wantIm) <= 1.0e-6);
            assertTrue("angle out of range" + at(x, y) + ": " + got.im(),
                    Math.abs(got.im()) <= (float) (Math.PI / 2.0));
        }
    }

    @Test
    public void testAsinhOnTheRealAxis() {
        for (int k = -400; k <= 400; ++k) {
            float x = k / 100.0f;
            ComplexF got = new ComplexF(x, 0.0f).asinh();
            // the stable real form; ln(x + sqrt(x*x+1)) loses everything near zero
            double a = Math.abs(x);
            double want = Math.copySign(Math.log1p(a + a * a / (1.0 + Math.sqrt(1.0 + a * a))), x);
            assertTrue("asinh(" + x + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-6 * Math.max(1.0, Math.abs(want)));
            same("asinh(" + x + ") stays real", 0.0f, got.im());
        }
        // the signed zeros are carried through untouched
        same("asinh(0)", 0.0f, 0.0f, ComplexF.Zero().asinh());
        same("asinh(-0,-0)", -0.0f, -0.0f, new ComplexF(-0.0f, -0.0f).asinh());
    }

    @Test
    public void testAsinhOnTheImaginaryAxis() {
        // asinh(i*y) is i*asin(y) for |y| <= 1, and Math.asin has nothing in
        // common with the code under test
        for (int k = -100; k <= 100; ++k) {
            float y = k / 100.0f;
            ComplexF got = new ComplexF(0.0f, y).asinh();
            same("asinh(i*" + y + ") stays imaginary", 0.0f, got.re());
            assertTrue("asinh(i*" + y + "): want " + Math.asin(y) + ", got " + got.im(),
                    Math.abs(got.im() - Math.asin(y)) <= 1.0e-6);
        }
        // past i the value leaves the imaginary axis by the real asinh of the cut
        for (int k = 11; k <= 400; ++k) {
            float y = k / 10.0f;
            ComplexF got = new ComplexF(0.0f, y).asinh();
            double want = Math.log(y + Math.sqrt((double) y * y - 1.0));
            assertTrue("asinh(i*" + y + "): want " + want + ", got " + got.re(),
                    Math.abs(got.re() - want) <= 1.0e-6 * want);
            same("asinh(i*" + y + ") imaginary part", (float) (Math.PI / 2.0), got.im());
        }
    }

    @Test
    public void testAsinhAtTheBranchPoint() {
        // the expected values come from 120 digit arithmetic and NOT from the C
        // library, which is 2.3e7 ulp out at (1e-8, 1)
        branchPoint(1.0e-4f, 0.010000083331458277771, 1.5607964101301048965);
        branchPoint(1.0e-8f, 1.0000000008333333419e-4, 1.5706963267949799526);
        branchPoint(1.0e-16f, 9.9999999999999999788e-9, 1.5707963167948966192);
    }

    private void branchPoint(float d, double wantRe, double wantIm) {
        ComplexF got = new ComplexF(d, 1.0f).asinh();
        assertTrue("asinh(" + d + " + i) re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-6 * wantRe);
        assertTrue("asinh(" + d + " + i) im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-6 * wantIm);
    }

    @Test
    public void testAsinhAndAsinAreOddAndConjugateSymmetric() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF n = v.neg();
                ComplexF c = v.conj();
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
        float half = (float) (Math.PI / 2.0);
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                float im = v.asinh().im();
                assertTrue("Im(asinh) out of range: " + im, im >= -half && im <= half);
                float re = v.asin().re();
                assertTrue("Re(asin) out of range: " + re, re >= -half && re <= half);
            }
        }
    }

    @Test
    public void testTheInverseSinePairAtTheEdges() {
        float half = (float) (Math.PI / 2.0);
        float quarter = (float) (Math.PI / 4.0);
        same("asinh(0,1)", 0.0f, half, new ComplexF(0.0f, 1.0f).asinh());
        same("asinh(-0,1)", -0.0f, half, new ComplexF(-0.0f, 1.0f).asinh());
        same("asinh(0,-1)", 0.0f, -half, new ComplexF(0.0f, -1.0f).asinh());
        // an unbounded imaginary part carries the real part with it
        same("asinh(0,inf)", INF, half, new ComplexF(0.0f, INF).asinh());
        same("asinh(-0,inf)", -INF, half, new ComplexF(-0.0f, INF).asinh());
        same("asinh(1,-inf)", INF, -half, new ComplexF(1.0f, -INF).asinh());
        same("asinh(inf,inf)", INF, quarter, ComplexF.Inf().asinh());
        same("asinh(inf,-inf)", INF, -quarter, new ComplexF(INF, -INF).asinh());
        // an unbounded real part swallows the angle
        same("asinh(inf,0)", INF, 0.0f, new ComplexF(INF, 0.0f).asinh());
        same("asinh(inf,-0)", INF, -0.0f, new ComplexF(INF, -0.0f).asinh());
        same("asinh(inf,1)", INF, 0.0f, new ComplexF(INF, 1.0f).asinh());
        same("asinh(-inf,-1)", -INF, -0.0f, new ComplexF(-INF, -1.0f).asinh());
        same("asinh(inf,NaN)", INF, NAN, new ComplexF(INF, NAN).asinh());
        // an exact zero in the imaginary part survives a NaN real part, because
        // asinh is real on the real axis; the other way round it does not
        same("asinh(NaN,0)", NAN, 0.0f, new ComplexF(NAN, 0.0f).asinh());
        same("asinh(NaN,-0)", NAN, -0.0f, new ComplexF(NAN, -0.0f).asinh());
        same("asinh(0,NaN)", NAN, NAN, new ComplexF(0.0f, NAN).asinh());
        same("asinh(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).asinh());
        same("asinh(NaN,inf)", INF, NAN, new ComplexF(NAN, INF).asinh());
        same("asinh(NaN,NaN)", NAN, NAN, ComplexF.NaN().asinh());
        // asin on its cut, where the sign of the zero decides the side
        same("asin(1,0)", half, 0.0f, ComplexF.One().asin());
        same("asin(-1,0)", -half, 0.0f, new ComplexF(-1.0f, 0.0f).asin());
        same("asin(2,0)", half, 1.3169579f, new ComplexF(2.0f, 0.0f).asin());
        same("asin(2,-0)", half, -1.3169579f, new ComplexF(2.0f, -0.0f).asin());
        same("asin(0,1)", 0.0f, 0.8813736f, ComplexF.I().asin());
        same("asin(inf,0)", half, INF, new ComplexF(INF, 0.0f).asin());
        same("asin(inf,inf)", quarter, INF, ComplexF.Inf().asin());
        same("asin(0,NaN)", 0.0f, NAN, new ComplexF(0.0f, NAN).asin());
    }

    // ---------- the inverse cosine pair ----------

    @Test
    public void testAcosRoundTripThroughCos() {
        // only from modulus one up: acos runs into PI/2 near the origin and cos
        // is flat there, so the way back loses everything
        for (int e = 0; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            double tol = 3.0e-7 * Math.max(1.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                close("cos(acos(z))" + at(v.re(), v.im()), v, v.acos().cos(), tol);
                close("cosh(acosh(z))" + at(v.re(), v.im()), v, v.acosh().cosh(), tol);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testAcosAndAsinAddToHalfPi() {
        // the near field oracle, where the way back through cos is useless.
        // asin is pinned down by the tests above.
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF s = v.acos().add(v.asin());
                double err = Math.hypot(s.re() - Math.PI / 2.0, s.im());
                assertTrue("acos + asin" + at(v.re(), v.im()) + " is " + s, err <= 5.0e-7);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testAcosFarOut() {
        // far out acos(z) is atan2(|y|, x) - i*ln(2|z|); the real part is even
        // in y because acos of a conjugate is the conjugate of acos
        for (int e = 8; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float x = (float) (r * Math.cos(angle(k)));
                float y = (float) (r * Math.sin(angle(k)));
                ComplexF got = new ComplexF(x, y).acos();
                double wantRe = Math.atan2(Math.abs(y), x);
                double wantIm = -Math.copySign(0.6931471805599453
                        + Math.log(new ComplexF(x, y).abs()), y);
                assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                        Math.abs(got.re() - wantRe) <= 1.0e-6);
                assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                        Math.abs(got.im() - wantIm) <= 1.0e-6 * Math.abs(wantIm));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 100);
    }

    @Test
    public void testAcosAtTheTopOfTheRange() {
        // ComplexD needs a far field branch above 1e307, where sqrt itself
        // gives out; here the roots are taken in double and the top of the
        // float range comes out of the ordinary formula
        float big = 1.0e38f;
        float[][] points = { { big, big }, { -big, big }, { big, -big }, { -big, -big },
                { Float.MAX_VALUE, 0.0f }, { 0.0f, big }, { big, 1.0f }, { 1.0f, big } };
        for (int i = 0; i < points.length; ++i) {
            float x = points[i][0];
            float y = points[i][1];
            ComplexF got = new ComplexF(x, y).acos();
            double wantRe = Math.atan2(Math.abs(y), x);
            double wantIm = -Math.copySign(0.6931471805599453
                    + Math.log(new ComplexF(x, y).abs()), y);
            assertTrue("real part" + at(x, y) + ": want " + wantRe + ", got " + got.re(),
                    Math.abs(got.re() - wantRe) <= 1.0e-6);
            assertTrue("imaginary part" + at(x, y) + ": want " + wantIm + ", got " + got.im(),
                    Math.abs(got.im() - wantIm) <= 1.0e-6 * Math.abs(wantIm));
            ComplexF h = new ComplexF(x, y).acosh();
            assertTrue("Re(acosh) must not be negative" + at(x, y) + ": " + h.re(), h.re() >= 0.0f);
        }
    }

    @Test
    public void testAcosOnTheRealAxis() {
        // Math.acos has nothing in common with the code under test
        for (int k = -100; k <= 100; ++k) {
            float x = k / 100.0f;
            ComplexF got = new ComplexF(x, 0.0f).acos();
            assertTrue("acos(" + x + "): want " + Math.acos(x) + ", got " + got.re(),
                    Math.abs(got.re() - Math.acos(x)) <= 1.0e-6);
            same("acos(" + x + ") stays real", -0.0f, got.im());
        }
        // past one the value leaves the real axis, and acosh is the real one
        for (int k = 11; k <= 400; ++k) {
            float x = k / 10.0f;
            double want = Math.log(x + Math.sqrt((double) x * x - 1.0));
            ComplexF got = new ComplexF(x, 0.0f).acos();
            assertTrue("Im(acos(" + x + ")): want " + (-want) + ", got " + got.im(),
                    Math.abs(got.im() + want) <= 1.0e-6 * want);
            same("acos(" + x + ") real part", 0.0f, got.re());
            ComplexF h = new ComplexF(x, 0.0f).acosh();
            assertTrue("acosh(" + x + "): want " + want + ", got " + h.re(),
                    Math.abs(h.re() - want) <= 1.0e-6 * want);
            same("acosh(" + x + ") stays real", 0.0f, h.im());
        }
    }

    @Test
    public void testAcosAtTheBranchPoint() {
        // the expected values come from 120 digit arithmetic; pi/2 - asin(z) is
        // 2.3e6 ulp out at 1 - 1e-13 and the plain logarithm is worse still
        acosBranchPointReal(1.0e-4f, 0.014143426826438996709);
        acosBranchPointReal(1.0e-6f, 0.0014235723601588100464);
        acosBranchPoint(1.0e-4f, 0.0099999165384842553762, -0.010000083205144494905);
        acosBranchPoint(1.0e-6f, 9.9999991540426958541e-4, -1.0000000820709356209e-3);
    }

    private void acosBranchPointReal(float d, double wantRe) {
        double got = new ComplexF(1.0f - d, 0.0f).acos().re();
        assertTrue("acos(1 - " + d + "): want " + wantRe + ", got " + got,
                Math.abs(got - wantRe) <= 1.0e-6 * wantRe);
    }

    private void acosBranchPoint(float d, double wantRe, double wantIm) {
        ComplexF got = new ComplexF(1.0f, d).acos();
        assertTrue("acos(1 + " + d + "i) re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-6 * wantRe);
        assertTrue("acos(1 + " + d + "i) im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-6 * Math.abs(wantIm));
    }

    @Test
    public void testAcosAndAcoshAreConjugateSymmetric() {
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                ComplexF c = v.conj();
                same("acos conj re", v.acos().conj().re(), c.acos().re());
                same("acos conj im", v.acos().conj().im(), c.acos().im());
                same("acosh conj re", v.acosh().conj().re(), c.acosh().re());
                same("acosh conj im", v.acosh().conj().im(), c.acosh().im());
            }
        }
    }

    @Test
    public void testTheRangeOfTheInverseCosine() {
        float pi = (float) Math.PI;
        for (int e = MIN_EXP; e <= MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF v = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                float re = v.acos().re();
                assertTrue("Re(acos) out of range: " + re, re >= 0.0f && re <= pi);
                float h = v.acosh().re();
                assertTrue("Re(acosh) must not be negative: " + h, h >= 0.0f);
            }
        }
    }

    @Test
    public void testTheInverseCosinePairAtTheEdges() {
        float half = (float) (Math.PI / 2.0);
        float quarter = (float) (Math.PI / 4.0);
        float three = (float) (3.0 * Math.PI / 4.0);
        float pi = (float) Math.PI;
        // the sign of the zero decides the side of the cut
        same("acos(0,0)", half, -0.0f, ComplexF.Zero().acos());
        same("acos(0,-0)", half, 0.0f, new ComplexF(0.0f, -0.0f).acos());
        same("acos(-0,0)", half, -0.0f, new ComplexF(-0.0f, 0.0f).acos());
        same("acos(1,0)", 0.0f, -0.0f, ComplexF.One().acos());
        same("acos(1,-0)", 0.0f, 0.0f, new ComplexF(1.0f, -0.0f).acos());
        same("acos(-1,0)", pi, -0.0f, new ComplexF(-1.0f, 0.0f).acos());
        same("acos(2,0)", 0.0f, -1.3169579f, new ComplexF(2.0f, 0.0f).acos());
        same("acos(2,-0)", 0.0f, 1.3169579f, new ComplexF(2.0f, -0.0f).acos());
        same("acos(-2,0)", pi, -1.3169579f, new ComplexF(-2.0f, 0.0f).acos());
        same("acos(0,1)", half, -0.8813736f, ComplexF.I().acos());
        // the angle saturates, the modulus grows without bound
        same("acos(0,inf)", half, -INF, new ComplexF(0.0f, INF).acos());
        same("acos(0,-inf)", half, INF, new ComplexF(0.0f, -INF).acos());
        same("acos(inf,0)", 0.0f, -INF, new ComplexF(INF, 0.0f).acos());
        same("acos(inf,-0)", 0.0f, INF, new ComplexF(INF, -0.0f).acos());
        same("acos(-inf,0)", pi, -INF, new ComplexF(-INF, 0.0f).acos());
        same("acos(inf,inf)", quarter, -INF, ComplexF.Inf().acos());
        same("acos(-inf,inf)", three, -INF, new ComplexF(-INF, INF).acos());
        same("acos(inf,-inf)", quarter, INF, new ComplexF(INF, -INF).acos());
        same("acos(inf,NaN)", NAN, -INF, new ComplexF(INF, NAN).acos());
        same("acos(NaN,inf)", NAN, -INF, new ComplexF(NAN, INF).acos());
        same("acos(NaN,-inf)", NAN, INF, new ComplexF(NAN, -INF).acos());
        // acos carries the imaginary axis onto itself, so PI/2 survives a NaN there
        same("acos(0,NaN)", half, NAN, new ComplexF(0.0f, NAN).acos());
        same("acos(-0,NaN)", half, NAN, new ComplexF(-0.0f, NAN).acos());
        same("acos(NaN,0)", NAN, NAN, new ComplexF(NAN, 0.0f).acos());
        same("acos(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).acos());
        same("acos(NaN,NaN)", NAN, NAN, ComplexF.NaN().acos());
        // and the quarter turn of all of that
        same("acosh(0,0)", 0.0f, half, ComplexF.Zero().acosh());
        same("acosh(0,-0)", 0.0f, -half, new ComplexF(0.0f, -0.0f).acosh());
        same("acosh(1,0)", 0.0f, 0.0f, ComplexF.One().acosh());
        same("acosh(1,-0)", 0.0f, -0.0f, new ComplexF(1.0f, -0.0f).acosh());
        same("acosh(-1,0)", 0.0f, pi, new ComplexF(-1.0f, 0.0f).acosh());
        same("acosh(-1,-0)", 0.0f, -pi, new ComplexF(-1.0f, -0.0f).acosh());
        same("acosh(2,0)", 1.3169579f, 0.0f, new ComplexF(2.0f, 0.0f).acosh());
        same("acosh(2,-0)", 1.3169579f, -0.0f, new ComplexF(2.0f, -0.0f).acosh());
        same("acosh(-2,0)", 1.3169579f, pi, new ComplexF(-2.0f, 0.0f).acosh());
        same("acosh(0,inf)", INF, half, new ComplexF(0.0f, INF).acosh());
        same("acosh(0,-inf)", INF, -half, new ComplexF(0.0f, -INF).acosh());
        same("acosh(inf,0)", INF, 0.0f, new ComplexF(INF, 0.0f).acosh());
        same("acosh(inf,-0)", INF, -0.0f, new ComplexF(INF, -0.0f).acosh());
        same("acosh(-inf,0)", INF, pi, new ComplexF(-INF, 0.0f).acosh());
        same("acosh(inf,inf)", INF, quarter, ComplexF.Inf().acosh());
        same("acosh(-inf,inf)", INF, three, new ComplexF(-INF, INF).acosh());
        same("acosh(inf,NaN)", INF, NAN, new ComplexF(INF, NAN).acosh());
        same("acosh(NaN,inf)", INF, NAN, new ComplexF(NAN, INF).acosh());
        // here the two part company: acos keeps PI/2, acosh does not
        same("acosh(0,NaN)", NAN, NAN, new ComplexF(0.0f, NAN).acosh());
        same("acosh(NaN,0)", NAN, NAN, new ComplexF(NAN, 0.0f).acosh());
        same("acosh(NaN,NaN)", NAN, NAN, ComplexF.NaN().acosh());
    }

    // ---------- the two ends of the range ----------

    /** the sqrt tests reach further out than the shared ensemble, which stops at 1e30 */
    private static final int SQRT_MIN_EXP = -45;
    private static final int SQRT_MAX_EXP = 38;

    @Test
    public void testSqrtOnTheRealAxisIsMathSqrt() {
        // on the axis the modulus is |x|, so t is Math.sqrt(x) and must agree
        // to the last bit; Math.sqrt has nothing in common with the code
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            for (double m : new double[] { 1.0, 2.5, 7.3 }) {
                float x = (float) (m * Math.pow(10.0, e));
                if (!isFinite(x) || x == 0.0f) {
                    continue;
                }
                same("sqrt(" + x + ")", (float) Math.sqrt(x), new ComplexF(x, 0.0f).sqrt().re());
                same("sqrt(-" + x + ")", (float) Math.sqrt(x), new ComplexF(-x, 0.0f).sqrt().im());
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testSqrtStaysFiniteAtBothEnds() {
        // the sum |re| + |z| overflows above a modulus of 1.7e38 and turns
        // subnormal below 1.2e-38; both ends must still come out of the range
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float x = (float) (r * Math.cos(angle(k)));
                float y = (float) (r * Math.sin(angle(k)));
                if (!isFinite(x) || !isFinite(y) || (x == 0.0f && y == 0.0f)) {
                    continue;
                }
                ComplexF s = new ComplexF(x, y).sqrt();
                assertTrue("sqrt" + at(x, y) + " is " + s, isFinite(s.re()) && isFinite(s.im()));
                assertTrue("real part" + at(x, y) + " must not be negative", s.re() >= 0.0f);
                assertTrue("the root of " + at(x, y) + " must not be zero",
                        s.re() != 0.0f || s.im() != 0.0f);
            }
        }
        same("sqrt(3.4e38)", (float) Math.sqrt(3.4e38f), new ComplexF(3.4e38f, 0.0f).sqrt().re());
        same("sqrt(MAX_VALUE)", (float) Math.sqrt(Float.MAX_VALUE),
                new ComplexF(Float.MAX_VALUE, 0.0f).sqrt().re());
    }

    @Test
    public void testSqrtInTheSubnormalBand() {
        // the expected values come from 120 digit arithmetic, because squaring
        // the root back underflows down here
        subnormalRoot(1.4e-45f, 1.4e-45f, 4.1128054643427787981E-23, 1.7035798027329537504E-23);
        subnormalRoot(1.0e-42f, -3.0e-43f, 1.0111942368118900672E-21, -1.4827906471805593517E-22);
        subnormalRoot(-1.0e-40f, 2.0e-41f, 9.9505519252915438348E-22, 1.0049357981847737337E-20);
        // three points pinned to the bit, where taking the quotient of the
        // modulus in float instead of double moves the answer away from the
        // 120 digit value by a fraction of an ulp
        same("sqrt(-6.1964989e9, -1.12513096e10)", 57655.39f, -97573.78f,
                new ComplexF(-6.1964989E9f, -1.12513096E10f).sqrt());
        same("sqrt(2.10404086e10, 3.30625167e10)", 173536.83f, 95260.805f,
                new ComplexF(2.10404086E10f, 3.30625167E10f).sqrt());
        same("sqrt(-43.956238, 68.04957)", 4.3043847f, 7.90468f,
                new ComplexF(-43.956238f, 68.04957f).sqrt());
    }

    private void subnormalRoot(float x, float y, double wantRe, double wantIm) {
        ComplexF got = new ComplexF(x, y).sqrt();
        assertTrue("sqrt" + at(x, y) + " re: want " + wantRe + ", got " + got.re(),
                Math.abs(got.re() - wantRe) <= 1.0e-6 * Math.abs(wantRe));
        assertTrue("sqrt" + at(x, y) + " im: want " + wantIm + ", got " + got.im(),
                Math.abs(got.im() - wantIm) <= 1.0e-6 * Math.abs(wantIm));
    }

    @Test
    public void testSquareRootSquaredIsTheOriginalAtBothEnds() {
        // the same exact squaring as above, but out to where the sum |re| + |z|
        // used to overflow and to where it used to turn subnormal
        for (int e = SQRT_MIN_EXP; e <= SQRT_MAX_EXP; ++e) {
            float r = (float) Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float a = (float) (r * Math.cos(angle(k)));
                float b = (float) (r * Math.sin(angle(k)));
                if (!isFinite(a) || !isFinite(b) || (a == 0.0f && b == 0.0f)) {
                    continue;
                }
                ComplexF root = new ComplexF(a, b).sqrt();
                BigDecimal x = big(root.re());
                BigDecimal y = big(root.im());
                BigDecimal gotRe = x.multiply(x, MC).subtract(y.multiply(y, MC), MC);
                BigDecimal gotIm = x.multiply(y, MC).multiply(big(2.0f), MC);
                BigDecimal mod2 = big(a).multiply(big(a), MC).add(big(b).multiply(big(b), MC), MC);
                BigDecimal errRe = gotRe.subtract(big(a), MC);
                BigDecimal errIm = gotIm.subtract(big(b), MC);
                BigDecimal err2 = errRe.multiply(errRe, MC).add(errIm.multiply(errIm, MC), MC);
                BigDecimal bound = mod2.multiply(big(5.0e-7f).multiply(big(5.0e-7f), MC), MC);
                assertTrue("sqrt" + at(a, b) + " squared is " + gotRe.round(MC) + ", " + gotIm.round(MC),
                        err2.compareTo(bound) <= 0);
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testSqrtWithVeryUnequalComponents() {
        // where |b| is far below a > 0 the root is (sqrt(a), b/(2*sqrt(a))).
        // Only where that imaginary part is still a normal float; below it the
        // value itself carries too few bits to compare against.
        for (int ea = -38; ea <= SQRT_MAX_EXP; ++ea) {
            float a = (float) (1.7 * Math.pow(10.0, ea));
            if (!isFinite(a) || a == 0.0f) {
                continue;
            }
            for (int d = 4; d <= 40; d += 2) {
                float b = (float) (3.1 * Math.pow(10.0, ea - d));
                double wantIm = b / (2.0 * Math.sqrt(a));
                if (b == 0.0f || Math.abs(wantIm) < Float.MIN_NORMAL) {
                    continue;
                }
                ComplexF s = new ComplexF(a, b).sqrt();
                assertTrue("sqrt" + at(a, b) + " re is " + s.re(),
                        Math.abs(s.re() - Math.sqrt(a)) <= 1.0e-7 * Math.sqrt(a));
                assertTrue("sqrt" + at(a, b) + " im is " + s.im(),
                        Math.abs(s.im() - wantIm) <= 1.0e-7 * Math.abs(wantIm));
                ++compared;
            }
        }
        assertTrue("too few points compared: " + compared, compared > 800);
    }

    // ================= 3. the full matrix, as digests =================

    private static long fold(long h, long bits) {
        h = (h ^ bits) * 0x100000001B3L;
        return h ^ (h >>> 29);
    }

    private static long fold(long h, double x) {
        return fold(h, Double.doubleToLongBits(x));
    }

    private static long fold(long h, ComplexF v) {
        return fold(fold(h, (double) v.re()), (double) v.im());
    }

    private static final long SEED = 0xCBF29CE484222325L;

    private static ComplexF apply(String op, ComplexF a, ComplexF b) {
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
            ComplexF r = binary ? apply(op, z(i), z(j))
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
            for (float x : SCALARS) {
                h = fold(h, op.equals("scale") ? z(i).scale(x) : z(i).pow(x));
            }
            out[i] = h;
        }
        return out;
    }

    private long unary(String op) {
        long h = SEED;
        for (int i = 0; i < SPECIAL.length; ++i) {
            ComplexF v = z(i);
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
            } else if (op.equals("neg")) {
                h = fold(h, v.neg());
            } else if (op.equals("abs")) {
                h = fold(h, (double) v.abs());
            } else if (op.equals("arg")) {
                h = fold(h, (double) v.arg());
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
                    ComplexF[] roots = z(i).nthRoots(n);
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
        ComplexF a = new ComplexF(1.0f, 2.0f);
        assertTrue("reflexive", a.equals(a));
        assertTrue("equal values", a.equals(new ComplexF(1.0f, 2.0f)));
        assertTrue("symmetric", new ComplexF(1.0f, 2.0f).equals(a));
        assertFalse("different real part", a.equals(new ComplexF(1.5f, 2.0f)));
        assertFalse("different imaginary part", a.equals(new ComplexF(1.0f, 2.5f)));
        assertFalse("null", a.equals(null));
        assertFalse("a foreign class", a.equals("1+2i"));
        // a NaN component says nothing about the other one
        assertFalse("NaN class", new ComplexF(NAN, 1.0f).equals(ComplexF.NaN()));
        assertFalse("NaN class", ComplexF.NaN().equals(new ComplexF(1.0f, NAN)));
        assertFalse("NaN against a number", a.equals(ComplexF.NaN()));
        // the two zeros are told apart, because the branch cuts tell them apart
        assertFalse("signed zero", ComplexF.Zero().equals(new ComplexF(-0.0f, -0.0f)));
    }

    @Test
    public void testHashCodeFollowsEquals() {
        assertTrue("the two zeros", ComplexF.Zero().hashCode() != new ComplexF(-0.0f, -0.0f).hashCode());
        assertTrue("the two zeros", new ComplexF(0.0f, -0.0f).hashCode() != new ComplexF(-0.0f, 0.0f).hashCode());
        assertTrue("every NaN", ComplexF.NaN().hashCode() != new ComplexF(NAN, 1.0f).hashCode());
        assertTrue("every NaN", new ComplexF(1.0f, NAN).hashCode() != new ComplexF(NAN, 0.0f).hashCode());
        ComplexF a = new ComplexF(3.0f, 4.0f);
        assertEquals("stable", a.hashCode(), a.hashCode());
        assertEquals("equal values", a.hashCode(), new ComplexF(3.0f, 4.0f).hashCode());
        // and it still separates ordinary values
        assertTrue("(3,4) against (4,3)", a.hashCode() != new ComplexF(4.0f, 3.0f).hashCode());
        assertTrue("(3,4) against (3,5)", a.hashCode() != new ComplexF(3.0f, 5.0f).hashCode());
    }

    @Test
    public void testTheFourZerosAreFourValues() {
        // the branch cuts read the sign of a zero, so equals does too
        ComplexF[] zeros = { new ComplexF(0.0f, 0.0f), new ComplexF(-0.0f, -0.0f),
                new ComplexF(0.0f, -0.0f), new ComplexF(-0.0f, 0.0f) };
        for (int i = 0; i < zeros.length; ++i) {
            for (int j = i + 1; j < zeros.length; ++j) {
                assertFalse("zero " + i + " equals zero " + j, zeros[i].equals(zeros[j]));
                assertTrue("zero " + i + " and " + j + " hashed alike",
                        zeros[i].hashCode() != zeros[j].hashCode());
            }
        }
        // and sqrt is one of the five that tell them apart
        same("sqrt(-4,+0)", 0.0f, 2.0f, new ComplexF(-4.0f, 0.0f).sqrt());
        same("sqrt(-4,-0)", 0.0f, -2.0f, new ComplexF(-4.0f, -0.0f).sqrt());
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
                seen.add(new ComplexF(i * 0.25f, j * 0.25f).hashCode());
            }
        }
        assertTrue(seen.size() + " distinct hashes for " + n + " values", seen.size() > 0.99 * n);
    }

    @Test
    public void testTheEqualsContract() {
        float[] vals = { 0.0f, -0.0f, 1.0f, -1.0f, 2.5f, INF, -INF, NAN, 1.4e-45f, 1.0e38f };
        ComplexF[] zs = new ComplexF[vals.length * vals.length];
        for (int i = 0; i < vals.length; ++i) {
            for (int j = 0; j < vals.length; ++j) {
                zs[i * vals.length + j] = new ComplexF(vals[i], vals[j]);
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
        // doubleToLongBits canonicalises them, and that part of the old
        // convention stays; doubleToRawLongBits would break it quietly
        ComplexF odd = new ComplexF(Float.intBitsToFloat(0x7fc00001), 1.0f);
        ComplexF plain = new ComplexF(NAN, 1.0f);
        assertTrue("two NaN bit patterns", odd.equals(plain));
        assertEquals("two NaN bit patterns hashed apart", odd.hashCode(), plain.hashCode());
        ComplexF odd2 = new ComplexF(2.0f, Float.intBitsToFloat(0xffc00003));
        ComplexF plain2 = new ComplexF(2.0f, NAN);
        assertTrue("a NaN with the sign bit set", odd2.equals(plain2));
        assertEquals("hashed apart", odd2.hashCode(), plain2.hashCode());
    }

    @Test
    public void testEqualsIsACongruence() {
        // equal now means the same bits, so no operation can tell two equal
        // values apart. The NaN class this replaced had 128 pairs that could.
        float[][] pts = { { NAN, 0.0f }, { NAN, -0.0f }, { 0.0f, NAN }, { -0.0f, NAN }, { NAN, -5.0f },
                { -5.0f, NAN }, { INF, NAN }, { NAN, INF }, { 1.0f, NAN }, { NAN, 1.0f }, { NAN, NAN },
                { -INF, NAN }, { NAN, -INF }, { 0.0f, 0.0f }, { -0.0f, -0.0f }, { 0.0f, -0.0f } };
        String[] ops = { "exp", "ln", "sqrt", "sinh", "cosh", "tanh", "asinh", "asin", "acos",
                "acosh", "atanh", "atan", "inv", "conj", "neg" };
        for (int o = 0; o < ops.length; ++o) {
            for (int i = 0; i < pts.length; ++i) {
                for (int j = 0; j < pts.length; ++j) {
                    ComplexF u = new ComplexF(pts[i][0], pts[i][1]);
                    ComplexF v = new ComplexF(pts[j][0], pts[j][1]);
                    if (!u.equals(v)) {
                        continue;
                    }
                    assertTrue(ops[o] + " of two equal values differs: " + u + " / " + v,
                            unary(u, ops[o]).equals(unary(v, ops[o])));
                }
            }
        }
    }

    private static ComplexF unary(ComplexF z, String op) {
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
        assertSame("NaN", ComplexF.NaN(), ComplexF.NaN());
        assertSame("Inf", ComplexF.Inf(), ComplexF.Inf());
        assertSame("Zero", ComplexF.Zero(), ComplexF.Zero());
        assertSame("One", ComplexF.One(), ComplexF.One());
        assertSame("I", ComplexF.I(), ComplexF.I());
        assertSame("copy", ComplexF.One(), ComplexF.One().copy());
        same("NaN", NAN, NAN, ComplexF.NaN());
        same("Inf", INF, INF, ComplexF.Inf());
        same("Zero", 0.0f, 0.0f, ComplexF.Zero());
        same("One", 1.0f, 0.0f, ComplexF.One());
        same("I", 0.0f, 1.0f, ComplexF.I());
    }

    @Test
    public void testNothingCanChangeAValue() {
        ComplexF v = new ComplexF(3.0f, 4.0f);
        v.add(ComplexF.One());
        v.sub(ComplexF.One());
        v.mul(ComplexF.I());
        v.div(ComplexF.I());
        v.inv();
        v.ln();
        v.exp();
        v.sqrt();
        v.pow(2.0f);
        v.pow(ComplexF.I());
        v.scale(7.0f);
        v.conj();
        v.neg();
        same("untouched", 3.0f, 4.0f, v);
    }

    @Test
    public void testToString() {
        // six digits here, ten in ComplexD
        assertEquals("+1.500000E+00  -2.500000E+00i", new ComplexF(1.5f, -2.5f).toString());
        // both zeros print as positive
        assertEquals("+0.000000E+00  +0.000000E+00i", new ComplexF(-0.0f, -0.0f).toString());
        assertEquals("+1.50  -2.50i", new ComplexF(1.5f, -2.5f).toString("%.2f"));
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

    // ================= the digests =================
    // one per left operand, folded over all 26 right operands

    private static final long[] MUL_DIGEST = {
            0x8C36AC0F97F502BDL, 0xF7EC8309EB2BBCDDL, 0x95143493C8653A54L,
            0x99118EF76A0E9459L, 0x11F7B24BDF3356AFL, 0xC64F8CDBF4C19B1CL,
            0x66BA00EDED90BDD3L, 0xFC7F66067488DB4DL, 0x91599143C88D82E9L,
            0xB4D8CE3C2A9EFC1FL, 0x301489EE7972B74EL, 0xDA392541ACDFC9B7L,
            0x8FFD6B63318F05B0L, 0x13C80708362FBB3FL, 0x48B315A6DB3C1B38L,
            0xAE457393BB7142B4L, 0xE336082AD0C1073BL, 0x696A8ABE057431C2L,
            0xA41F97EE0D769DB0L, 0x8D9789A1E9231EF3L, 0x204EB64D1052D7F8L,
            0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L, 0xC9443BBDF728642DL,
            0x13C80708362FBB3FL, 0xAE457393BB7142B4L };
    private static final long[] DIV_DIGEST = {
            0x465BB4704EBEE6E1L, 0x2A4AA4C89E610073L, 0xE5DDD92486DE4782L,
            0x301412524F1C2F77L, 0x1CE7C3D4BC32467CL, 0x75C196DCCD464D9AL,
            0x040F48929D3BEBA4L, 0xAE0B40E3B76B9892L, 0x2056EEC186F00467L,
            0x54C3EBF7467E28A5L, 0x5FE54145B06C86F8L, 0x06118043DB1CA6CDL,
            0x4C32C695325EDEF2L, 0x7320802C26EAE749L, 0x363F496977E15BB2L,
            0x2281048D55494F4AL, 0x90A1A302D57B02BEL, 0x4FB1BCAD4BFA533DL,
            0x2AE287CBF2FABA3FL, 0x55A75FFE8A522676L, 0x19F64499015D71ADL,
            0x19F64499015D71ADL, 0x19F64499015D71ADL, 0x19F64499015D71ADL,
            0x7320802C26EAE749L, 0x2281048D55494F4AL };
    private static final long[] POW_DIGEST = {
            0x5FC227D263AA91AFL, 0x5FC227D263AA91AFL, 0x5FC227D263AA91AFL,
            0x5FC227D263AA91AFL, 0xA71242CEB023E841L, 0xF01E6DE6F7D441C6L,
            0x2F73D878C17D8EACL, 0xC33FC3BE7902F5B8L, 0xCD92E4D08027AFC4L,
            0x6B3A6407701B487AL, 0x8901582B2180597CL, 0xD3402CE726BE2337L,
            0x6F150A0984BF0E18L, 0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L,
            0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L,
            0x52165B55EE7DEF10L, 0x52165B55EE7DEF10L, 0x204EB64D1052D7F8L,
            0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L, 0x204EB64D1052D7F8L,
            0xE5E5C7B72A3E5381L, 0xE5E5C7B72A3E5381L };
    private static final long[] ADD_DIGEST = {
            0x5E3945A15C33E5E4L, 0x9BB905E6D7BFD67EL, 0x848BBC940C030606L,
            0x63DAA1ADB7D261FEL, 0xB5E4B0D193C27C33L, 0x4C10FD3BD76716EFL,
            0xC4FE96771E479753L, 0xECC5D82FBCB460ACL, 0xC00A1EFE3EFE9C1EL,
            0x5327BAD3CC3BD597L, 0x439BAF8132DC1766L, 0x25418A69FF1AF7B3L,
            0x1EFA5107A7C766C1L, 0x6160D43E92150B62L, 0x3C075FF23E764474L,
            0x9F462952603E13C5L, 0x7BE08C4DC52D2A77L, 0xAA911F33F0101647L,
            0xA3D4A1E26D72C66BL, 0x1A3A1CFE812D18D2L, 0xE1EFC119A905943EL,
            0x7435A9E36082B45FL, 0x204EB64D1052D7F8L, 0x7ADF3C27E67F99D1L,
            0x39865C58C45AE528L, 0xF5C6013D287973C2L };
    private static final long[] SUB_DIGEST = {
            0xE1C9BA7000CCB152L, 0xCA588D54B1E421B5L, 0x96578BDA990404DCL,
            0x241D88E6D9266867L, 0xAC90777A507068D6L, 0x3A8B8D14B699DD42L,
            0xBD24300C63E56697L, 0xEC31EE143E27766EL, 0xE96833EF23CBB349L,
            0x2C6160316537F2D0L, 0x37D414F5246ECF21L, 0x91F00DC1D532D0B3L,
            0x19796B5A0860E429L, 0x807CDA5CABB355B4L, 0xE693B0FFB5ECDAF8L,
            0x5B6D18603E05045CL, 0xDF333E92457C7D7FL, 0x1EAA84DDBFF4F921L,
            0x59E5F4066274A3E6L, 0xA57BD965CDC2B3B2L, 0xB5C8F5301C9AA95BL,
            0xA77CFE39E04BA1FCL, 0x204EB64D1052D7F8L, 0x7ADAEDEBCA607DC0L,
            0x7ABB2D53D43F8B96L, 0x14200D4175B564D6L };
    private static final long[] SCALE_DIGEST = {
            0x6D3EE73E937BD60EL, 0x56496E4DA9F9EA45L, 0x2EF7F7C94423066CL,
            0x367FF12A5D71ADA9L, 0x56E46FC625844C38L, 0x669F2E664743811BL,
            0x91F6D11DFC27BEC7L, 0x711AB9E0C9006208L, 0x7EDE5F016BEFA5ECL,
            0x49809193F30D96F9L, 0x348425A355222D87L, 0x8105259F01A00C8CL,
            0x3A6D1661BD04E863L, 0x9464A4979EE6AB75L, 0x1497EAE04496BB7FL,
            0x07DC93A9767C4F4BL, 0xF00464EB72FFD456L, 0x05020ED10E1B81B0L,
            0xC2CCF98C474632F4L, 0xCBE9CCF5F26D03D9L, 0x2E840BD58D4AE086L,
            0x32473C25BEB25AD4L, 0x6FE25EB7DBF1810FL, 0x7AD9F7F42E081D92L,
            0x9464A4979EE6AB75L, 0x07DC93A9767C4F4BL };
    private static final long[] POWR_DIGEST = {
            0x73960B54D06804D7L, 0x73960B54D06804D7L, 0x73960B54D06804D7L,
            0x73960B54D06804D7L, 0xC5DAFE644FBCD402L, 0x9CB49B70F3C516ADL,
            0xDA0C39C44CF12911L, 0xBF641A6725D3282BL, 0x3804E0C5266AC5CAL,
            0x687156DDB777036FL, 0x46164DB4BF04F044L, 0xBFB02E035F39A0DFL,
            0xC5083D5E75234B3CL, 0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL,
            0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL,
            0x90F8BE288D73EA1BL, 0x90F8BE288D73EA1BL, 0x6FE25EB7DBF1810FL,
            0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL,
            0x6FE25EB7DBF1810FL, 0x6FE25EB7DBF1810FL };

    private static final long INV_DIGEST = 0x3D22CC683F8BAF7CL;
    private static final long LN_DIGEST = 0xC38BE3DB20B18ACBL;
    private static final long EXP_DIGEST = 0xA0D29E88E82B03CCL;
    private static final long SQRT_DIGEST = 0x75BFE2772EA9984FL;
    private static final long SINH_DIGEST = 0xBDEEE7DA07351290L;
    private static final long COSH_DIGEST = 0x6B21A8243A1BC0B3L;
    private static final long SIN_DIGEST = 0xEDD201EBB6C904C2L;
    private static final long COS_DIGEST = 0x0EC4CDF56327FB3AL;
    private static final long TANH_DIGEST = 0xA4D6068ACE8F71B8L;
    private static final long TAN_DIGEST = 0xB045BB16F89DE38EL;
    private static final long ATANH_DIGEST = 0x8FF221827B338A66L;
    private static final long ATAN_DIGEST = 0x894A0238ACF3AE46L;
    private static final long ASINH_DIGEST = 0xD61F21DDE5070E2CL;
    private static final long ASIN_DIGEST = 0xAB0ADA9B66942105L;
    private static final long ACOS_DIGEST = 0x5B0067B2833E075BL;
    private static final long ACOSH_DIGEST = 0xF1C12B9C7AA71583L;
    private static final long NTHROOT_DIGEST = 0x6A6E5C0A4B750BE8L;
    private static final long NTHROOTS_DIGEST = 0x6113FB7F79BEF113L;
    private static final long CONJ_DIGEST = 0x7D33B1ACC637F174L;
    private static final long NEG_DIGEST = 0x241D88E6D9266867L;
    private static final long ABS_DIGEST = 0x33688208A5C817EFL;
    private static final long ARG_DIGEST = 0xE488086C72363CF4L;
    private static final long HASH_DIGEST = 0xD54F0484F04C49ECL;
    private static final long STRING_DIGEST = 0xF2471CAD00267BD5L;
}
