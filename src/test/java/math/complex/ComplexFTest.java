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

    /** the integer exponents, on both sides of the threshold in pow(int) */
    private static final int[] EXPONENTS = { 0, 1, -1, 2, -2, 3, -3, 8, 256, 257, -257, Integer.MAX_VALUE,
            Integer.MIN_VALUE };

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

    /** the power series of ln(1+z) or of exp(z)-1 at 34 digits, |z| under one */
    private static BigDecimal[] series(float x, float y, boolean log) {
        BigDecimal a = big(x);
        BigDecimal b = big(y);
        BigDecimal pr = a;
        BigDecimal pi = b;
        BigDecimal sr = a;
        BigDecimal si = b;
        for (int n = 2; n <= 60; ++n) {
            BigDecimal t = pr.multiply(a, MC).subtract(pi.multiply(b, MC), MC);
            pi = pr.multiply(b, MC).add(pi.multiply(a, MC), MC);
            pr = t;
            BigDecimal d = new BigDecimal(n);
            if (!log) {
                // z^n / n!, the factorial carried in the power itself
                pr = pr.divide(d, MC);
                pi = pi.divide(d, MC);
                sr = sr.add(pr, MC);
                si = si.add(pi, MC);
            } else if ((n & 1) == 0) {
                sr = sr.subtract(pr.divide(d, MC), MC);
                si = si.subtract(pi.divide(d, MC), MC);
            } else {
                sr = sr.add(pr.divide(d, MC), MC);
                si = si.add(pi.divide(d, MC), MC);
            }
        }
        return new BigDecimal[] { sr, si };
    }

    @Test
    public void testLog1pAndExpm1AgainstTheirSeries() {
        for (int e = -1; e >= -34; --e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float x = (float) (r * Math.cos(angle(k)));
                float y = (float) (r * Math.sin(angle(k)));
                BigDecimal[] l = series(x, y, true);
                // one float ulp is 1.2e-7, so this says correctly rounded
                exact("log1p" + at(x, y), l[0], l[1], new ComplexF(x, y).log1p(), 1.0e-7);
                BigDecimal[] p = series(x, y, false);
                exact("expm1" + at(x, y), p[0], p[1], new ComplexF(x, y).expm1(), 1.0e-7);
            }
        }
        assertTrue("too few points compared: " + compared, compared > 400);
    }

    @Test
    public void testLog1pAndExpm1KeepWhatTheDifferenceLoses() {
        ComplexF one = new ComplexF(1.0f, 0.0f);
        // a hundredth of an ulp of one, so 1 + z rounds straight back to one
        float x = 1.0e-9f;
        float y = 2.0e-9f;
        ComplexF z = new ComplexF(x, y);
        BigDecimal[] l = series(x, y, true);
        exact("log1p at 2e-9", l[0], l[1], z.log1p(), 1.0e-7);
        BigDecimal[] p = series(x, y, false);
        exact("expm1 at 2e-9", p[0], p[1], z.expm1(), 1.0e-7);
        assertTrue("add(1).ln() has a real part left", z.add(one).ln().re() == 0.0f);
        assertTrue("exp().sub(1) has a real part left", z.exp().sub(one).re() == 0.0f);
        // exp is one on all of z = 2 k pi i, and the difference dies there too,
        // six units away from the origin and further
        for (int k = 1; k <= 4; ++k) {
            float w = (float) (2.0 * Math.PI * k);
            double s = Math.sin(w);
            // cos(w) - 1 = -sin(w)^2 / (1 + cos(w)), which does not cancel
            float want = (float) (-s * s / (1.0 + Math.cos(w)));
            ComplexF got = new ComplexF(0.0f, w).expm1();
            assertEquals("expm1 at 2*" + k + "*pi*i re", want, got.re(), 4.0f * Math.ulp(want));
            same("expm1 at 2*" + k + "*pi*i im", (float) s, got.im());
            assertTrue("exp().sub(1) has a real part left at 2*" + k + "*pi*i",
                    new ComplexF(0.0f, w).exp().sub(one).re() == 0.0f);
        }
    }

    @Test
    public void testLog1pAndExpm1OnTheRealAxis() {
        for (int e = -34; e <= 34; ++e) {
            float m = (float) Math.pow(10.0, e);
            for (int s = 0; s < 2; ++s) {
                float x = (s == 0) ? m : -m;
                if (x > -1.0f) {
                    same("log1p" + at(x, 0.0f), (float) Math.log1p(x), 0.0f,
                            new ComplexF(x, 0.0f).log1p());
                }
                same("expm1" + at(x, 0.0f), (float) Math.expm1(x), 0.0f,
                        new ComplexF(x, 0.0f).expm1());
            }
        }
        // the sign of the zero rides through, as it does in Math
        same("log1p(-0,+0)", -0.0f, 0.0f, new ComplexF(-0.0f, 0.0f).log1p());
        same("log1p(+0,-0)", 0.0f, -0.0f, new ComplexF(0.0f, -0.0f).log1p());
        same("expm1(-0,+0)", -0.0f, 0.0f, new ComplexF(-0.0f, 0.0f).expm1());
        same("expm1(+0,-0)", 0.0f, -0.0f, new ComplexF(0.0f, -0.0f).expm1());
        // below -1 the logarithm leaves the axis and the zero picks the side
        same("log1p(-3,+0)", (float) Math.log(2.0), (float) Math.PI,
                new ComplexF(-3.0f, 0.0f).log1p());
        same("log1p(-3,-0)", (float) Math.log(2.0), (float) -Math.PI,
                new ComplexF(-3.0f, -0.0f).log1p());
        same("log1p(-1)", -INF, 0.0f, new ComplexF(-1.0f, 0.0f).log1p());
        same("log1p(-1,-0)", -INF, -0.0f, new ComplexF(-1.0f, -0.0f).log1p());
    }

    @Test
    public void testLog1pStaysSharpNextToMinusOne() {
        // there |1+z|^2 - 1 runs into -1 and cancels against the 1, so the form
        // that carries it answers -Infinity where the value is merely large
        for (int e = -2; e >= -38; e -= 4) {
            double d = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                float x = (float) (-1.0 + d * Math.cos(angle(k)));
                float y = (float) (d * Math.sin(angle(k)));
                ComplexF got = new ComplexF(x, y).log1p();
                float want = (float) Math.log(Math.hypot(1.0 + (double) x, (double) y));
                assertTrue("log1p next to -1" + at(x, y) + ": want " + want + ", got " + got.re(),
                        Math.abs(got.re() - want) <= 4.0f * Math.ulp(want));
                same("the angle next to -1" + at(x, y),
                        (float) Math.atan2((double) y, 1.0 + (double) x), got.im());
            }
        }
    }

    @Test
    public void testLog1pAndExpm1AreInverse() {
        for (int e = -1; e >= -34; --e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                ComplexF want = new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k))));
                // the same round trip through exp and ln keeps nothing at all
                close("log1p(expm1 z)", want, want.expm1().log1p(), 1.0e-6);
                close("expm1(log1p z)", want, want.log1p().expm1(), 1.0e-6);
            }
        }
    }

    @Test
    public void testLog1pAndExpm1FollowTheNaiveRouteWhereItIsDegenerate() {
        ComplexF one = new ComplexF(1.0f, 0.0f);
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            if (!z.isInfinite() && !z.isNan()) {
                // the finite rows are where the two are allowed to part company
                continue;
            }
            ComplexF w = z.add(one).ln();
            same("log1p" + at(v[0], v[1]), w.re(), w.im(), z.log1p());
            ComplexF u = z.exp().sub(one);
            same("expm1" + at(v[0], v[1]), u.re(), u.im(), z.expm1());
        }
        // and where they do part company the new route is the right one
        same("log1p(-0,+0)", -0.0f, 0.0f, new ComplexF(-0.0f, 0.0f).log1p());
        same("add(1).ln() there", 0.0f, 0.0f, new ComplexF(-0.0f, 0.0f).add(one).ln());
        same("log1p(1e-38)", 1.0e-38f, 1.0e-38f, new ComplexF(1.0e-38f, 1.0e-38f).log1p());
        same("add(1).ln() there", 0.0f, 1.0e-38f, new ComplexF(1.0e-38f, 1.0e-38f).add(one).ln());
        same("expm1(1e-38)", 1.0e-38f, 1.0e-38f, new ComplexF(1.0e-38f, 1.0e-38f).expm1());
        same("exp().sub(1) there", 0.0f, 1.0e-38f, new ComplexF(1.0e-38f, 1.0e-38f).exp().sub(one));
    }

    @Test
    public void testLog1pAndExpm1AtTheEdges() {
        // far out the 1 is beneath notice, and the widened square still fits
        ComplexF far = new ComplexF(1.0e30f, 1.0e30f);
        close("log1p far out", far.ln(), far.log1p(), 1.0e-7);
        // the twin hands nothing back to exp(): the product runs in double, so
        // a float answer overflows 200 orders before the product does
        same("expm1(90,3) re", -INF, new ComplexF(90.0f, 3.0f).expm1().re());
        assertTrue("expm1(90,3) im", isFinite(new ComplexF(90.0f, 3.0f).expm1().im()));
        same("expm1(800,3)", -INF, INF, new ComplexF(800.0f, 3.0f).expm1());
        // an exact zero is kept there too, not turned into inf - 1
        same("expm1(800,0)", INF, 0.0f, new ComplexF(800.0f, 0.0f).expm1());
        same("expm1(-inf,2)", -1.0f, 0.0f, new ComplexF(-INF, 2.0f).expm1());
    }

    @Test
    public void testTheFormsChangeWithoutASeam() {
        ComplexF one = new ComplexF(1.0f, 0.0f);
        // log1p hands the work over where |1+z|^2 - 1 reaches -1/2
        for (double t : new double[] { 0.70712, 0.7071068, 0.707106, 0.7071 }) {
            float x = (float) (-1.0 + t * Math.cos(1.0));
            float y = (float) (t * Math.sin(1.0));
            ComplexF z = new ComplexF(x, y);
            double u = (double) x * (2.0 + (double) x) + (double) y * (double) y;
            float a = (float) (0.5 * Math.log1p(u));
            float b = z.add(one).ln().re();
            assertEquals("the two forms at the seam" + at(x, y), a, b, 4.0f * Math.ulp(a));
            assertEquals("the log1p form" + at(x, y), a, z.log1p().re(), 4.0f * Math.ulp(a));
        }
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
                    // the product runs in double and rounds once, so it costs none
                    exact("powi " + n, wantRe, wantIm, base.pow(n), 1.0e-7);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 200);
    }

    @Test
    public void testIntegerPowStaysCloseAtTheHigherDegrees() {
        for (int n : new int[] { 8, 20, 40, 100, 200, 256 }) {
            for (int e = -8; e <= 8; ++e) {
                double r = Math.pow(10.0, (double) e / n);
                for (int k = 0; k < ANGLES; ++k) {
                    ComplexF base = new ComplexF((float) (r * Math.cos(angle(k))), (float) (r * Math.sin(angle(k))));
                    BigDecimal wantRe = big(base.re());
                    BigDecimal wantIm = big(base.im());
                    for (int i = 1; i < n; ++i) {
                        BigDecimal nextRe = wantRe.multiply(big(base.re()), MC)
                                .subtract(wantIm.multiply(big(base.im()), MC), MC);
                        wantIm = wantRe.multiply(big(base.im()), MC).add(wantIm.multiply(big(base.re()), MC), MC);
                        wantRe = nextRe;
                    }
                    // one float ulp is 1.2e-7, so this says correctly rounded; the
                    // same product carried out in float costs ten times as much
                    exact("powi " + n, wantRe, wantIm, base.pow(n), 1.0e-7);
                }
            }
        }
        assertTrue("too few points compared: " + compared, compared > 800);
    }

    @Test
    public void testIntegerPowIsExactWhereTheAnswerIsWhole() {
        same("(3,4)^2", -7.0f, 24.0f, new ComplexF(3.0f, 4.0f).pow(2));
        same("(1,1)^8", 16.0f, 0.0f, new ComplexF(1.0f, 1.0f).pow(8));
        // the product runs i, -1, -i, 1, and the vanishing part keeps its sign
        same("i^4", 1.0f, -0.0f, ComplexF.I().pow(4));
        same("(-2)^3", -8.0f, 0.0f, new ComplexF(-2.0f, 0.0f).pow(3));
        same("(3i)^2", -9.0f, 0.0f, new ComplexF(0.0f, 3.0f).pow(2));
        same("2^-2", 0.25f, 0.0f, new ComplexF(2.0f, 0.0f).pow(-2));
        // the route through ln and exp can say none of this
        assertTrue("(-2)^3 stays on the real axis", new ComplexF(-2.0f, 0.0f).pow(3).isReal());
        assertFalse("(-2)^3.0 does not", new ComplexF(-2.0f, 0.0f).pow(3.0f).isReal());
    }

    @Test
    public void testIntegerPowHasTheHeadroomOfADouble() {
        // in float both terms overflow and cancel into a NaN; carried out in
        // double the real part is exactly zero and the answer survives
        ComplexF big = new ComplexF(1.0e30f, 1.0e30f);
        same("(1e30,1e30)^2", 0.0f, INF, big.pow(2));
        same("(1e30,1e30) squared by mul", NAN, INF, big.mul(big));
        // and the whole float range of a negative exponent stays reachable
        same("2^-149", Float.MIN_VALUE, 0.0f, new ComplexF(2.0f, 0.0f).pow(-149));
        same("2^-150", 0.0f, 0.0f, new ComplexF(2.0f, 0.0f).pow(-150));
        // past the double range as well the direction survives: (1+i)^16 is 256,
        // so this is real and positive however large the modulus has grown
        same("(1e38,1e38)^16", INF, 0.0f, new ComplexF(1.0e38f, 1.0e38f).pow(16));
        // and the reciprocal of a product that overflowed is a zero, not a NaN
        same("(1e38,1e38)^-257", 0.0f, 0.0f, new ComplexF(1.0e38f, 1.0e38f).pow(-257));
    }

    @Test
    public void testIntegerPowKeepsTheAxisItStartedOn() {
        for (int e = -8; e <= 8; e += 2) {
            float x = (float) (1.7 * Math.pow(10.0, e));
            for (int n = -6; n <= 12; ++n) {
                ComplexF a = new ComplexF(x, 0.0f).pow(n);
                assertTrue("real^" + n + " left the real axis: " + a, a.isReal());
                ComplexF b = new ComplexF(0.0f, x).pow(n);
                if (Math.abs(n) % 2 == 0) {
                    assertTrue("imaginary^" + n + " is not real: " + b, b.isReal());
                } else {
                    // an odd power of an imaginary number is imaginary
                    same("imaginary^" + n + " re", 0.0f, Math.abs(b.re()));
                }
            }
        }
    }

    @Test
    public void testIntegerPowObeysTheLawsOfAProduct() {
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            if ((v[0] == 0.0f && v[1] == 0.0f) || z.isInfinite() || z.isNan()) {
                // those follow the real overload instead, see the next test
                continue;
            }
            String w = at(v[0], v[1]);
            same("z^0" + w, 1.0f, 0.0f, z.pow(0));
            same("z^1" + w, v[0], v[1], z.pow(1));
            // the reciprocal comes out of the double product, so it can differ
            // from inv() in the sign of a vanishing part, never in value
            close("z^-1" + w, z.inv(), z.pow(-1), 1.0e-7);
        }
    }

    @Test
    public void testIntegerPowFollowsTheRealOverloadWhereItIsDegenerate() {
        int[] ns = { 0, 1, 2, 3, -1, -2, Integer.MAX_VALUE, Integer.MIN_VALUE };
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            if (!((v[0] == 0.0f && v[1] == 0.0f) || z.isInfinite() || z.isNan())) {
                continue;
            }
            for (int n : ns) {
                ComplexF want = z.pow((float) n);
                same("z^" + n + at(v[0], v[1]), want.re(), want.im(), z.pow(n));
            }
        }
    }

    @Test
    public void testIntegerPowAtTheEdgesOfTheRange() {
        ComplexF two = new ComplexF(2.0f, 0.0f);
        // the last power of two that fits, and the first that does not
        same("2^127", (float) Math.pow(2.0, 127.0), 0.0f, two.pow(127));
        same("2^128", INF, 0.0f, two.pow(128));
        // negating the exponent is what overflows an int, so it happens in long
        ComplexF i = ComplexF.I();
        same("i^MIN_VALUE", 1.0f, 0.0f, i.pow(Integer.MIN_VALUE));
        same("i^MAX_VALUE", -0.0f, -1.0f, i.pow(Integer.MAX_VALUE));
        // on either side of the threshold between the two loops
        same("i^256", 1.0f, -0.0f, i.pow(256));
        same("i^257", 0.0f, 1.0f, i.pow(257));
        same("i^-257", 0.0f, -1.0f, i.pow(-257));
    }

    /** the exact |z|^2; a float widens exactly, so this has no rounding at all */
    private static BigDecimal square(float x, float y) {
        BigDecimal a = new BigDecimal((double) x);
        BigDecimal b = new BigDecimal((double) y);
        return a.multiply(a).add(b.multiply(b));
    }

    /** how far a double is from an exactly known value, in ulps of that value */
    private static double ulpsOf(double got, BigDecimal want) {
        return new BigDecimal(got).subtract(want, MC).abs().doubleValue()
                / Math.ulp(want.doubleValue());
    }

    /** a finer fan than the ensemble uses, the axes still avoided */
    private static double spoke(int k) {
        return k * Math.PI / 16.0 + 0.3;
    }

    @Test
    public void testAbs2IsCorrectlyRounded() {
        // the twin widens its components and rounds once, as sincOf does
        double worst = 0.0;
        for (int e = -38; e <= 38; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < 32; ++k) {
                float x = (float) (r * Math.cos(spoke(k)));
                float y = (float) (r * Math.sin(spoke(k)));
                worst = Math.max(worst, ulpsOf(new ComplexF(x, y).abs2(), square(x, y)));
                ++compared;
            }
        }
        assertTrue("abs2 is off by " + worst + " ulp", worst <= 1.0);
        assertTrue("too few points compared: " + compared, compared > 2000);
    }

    @Test
    public void testAbs2KeepsTheWholeFloatRange() {
        // |z|^2 needs twice the exponent that z does, which is why the answer
        // is a double: over the whole float range not one point is lost
        int live = 0;
        for (int e = -46; e <= 38; ++e) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < 32; ++k) {
                float x = (float) (r * Math.cos(spoke(k)));
                float y = (float) (r * Math.sin(spoke(k)));
                if (x == 0.0f && y == 0.0f) {
                    continue;
                }
                double got = new ComplexF(x, y).abs2();
                assertTrue("abs2 died" + at(x, y) + ", got " + got,
                        got > 0.0 && !Double.isInfinite(got));
                ++live;
            }
        }
        assertTrue("too few live points: " + live, live > 2000);
        // a float squares to at most 1.2e77, so the two ends of the grid, where
        // a float answer would be Infinity and zero, both come back
        assertEquals("abs2(1e38,1e38)", 1.999999872114279E76,
                new ComplexF(1.0e38f, 1.0e38f).abs2(), 0.0);
        assertEquals("abs2(1e-38,1e-38)", 1.99999974018257E-76,
                new ComplexF(1.0e-38f, 1.0e-38f).abs2(), 0.0);
        assertEquals("abs2 of the largest float", 2.3119999349163358E77,
                new ComplexF(3.4e38f, 3.4e38f).abs2(), 0.0);
        assertEquals("abs2 of the smallest subnormal", 1.9636373861190906E-90,
                new ComplexF(1.4e-45f, 0.0f).abs2(), 0.0);
    }

    @Test
    public void testAbs2FollowsAbsWhereTheModulusHasNoDigits() {
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            if (z.isInfinite()) {
                assertTrue("abs2" + at(v[0], v[1]), z.abs2() == Double.POSITIVE_INFINITY);
                same("abs" + at(v[0], v[1]), INF, z.abs());
            } else if (z.isNan()) {
                assertTrue("abs2" + at(v[0], v[1]) + " should be NaN", Double.isNaN(z.abs2()));
            }
        }
        // an infinite component fixes the modulus even against a NaN one, so
        // the square has to say so too - the plain product does not
        assertTrue("the plain product at (inf,NaN)",
                Double.isNaN((double) INF * INF + (double) NAN * NAN));
        assertTrue("abs2(inf,NaN)", new ComplexF(INF, NAN).abs2() == Double.POSITIVE_INFINITY);
        assertTrue("abs2(NaN,inf)", new ComplexF(NAN, INF).abs2() == Double.POSITIVE_INFINITY);
        // and where the modulus has digits it is exactly the square of them
        assertEquals("abs2(3,4)", 25.0, new ComplexF(3.0f, 4.0f).abs2(), 0.0);
        assertEquals("abs2(-0,-0)", 0.0, new ComplexF(-0.0f, -0.0f).abs2(), 0.0);
        assertEquals("abs2(1,0)", 1.0, new ComplexF(1.0f, 0.0f).abs2(), 0.0);
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

    @Test
    public void testProjSendsEveryInfinityToTheOnePoint() {
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int infinite = 0;
        int left = 0;
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            ComplexF p = z.proj();
            if (z.isInfinite()) {
                ++infinite;
                seen.add(Float.floatToIntBits(p.re()) + "/" + Float.floatToIntBits(p.im()));
                same("proj" + at(v[0], v[1]), INF, INF, p);
            } else {
                // everything else comes back bit for bit, both zeros included
                same("proj" + at(v[0], v[1]), v[0], v[1], p);
                ++left;
            }
            same("proj is idempotent" + at(v[0], v[1]), p.re(), p.im(), p.proj());
        }
        assertEquals("infinite rows in the grid", 9, infinite);
        assertEquals("finite rows left alone", 17, left);
        assertEquals("the infinities are one point", 1, seen.size());
        // and it is the point the library already had
        assertTrue("proj(inf,1)", new ComplexF(INF, 1.0f).proj().equals(ComplexF.Inf()));
        assertTrue("the inverse of zero", ComplexF.Zero().inv().equals(ComplexF.Inf()));
        assertTrue("the pole of csc", ComplexF.Zero().csc().equals(ComplexF.Inf()));
        // the C99 form would leave two of them, because equals reads the sign
        // of a zero and (inf,+0) is not (inf,-0)
        assertFalse("(inf,+0) against (inf,-0)",
                new ComplexF(INF, 0.0f).equals(new ComplexF(INF, -0.0f)));
        // neg and conj do not normalize an infinity, and proj is the one that
        // does - that is the whole of its job
        same("neg keeps the direction", -INF, -1.0e10f, new ComplexF(INF, 1.0e10f).neg());
        assertTrue("proj does not", new ComplexF(INF, 1.0e10f).proj().equals(ComplexF.Inf()));
    }

    @Test
    public void testIsFiniteAnswersWhatTheOtherTwoDoNot() {
        int both = 0;
        for (float[] v : SPECIAL) {
            ComplexF z = new ComplexF(v[0], v[1]);
            assertEquals("isFinite" + at(v[0], v[1]), !z.isNan() && !z.isInfinite(), z.isFinite());
            if (z.isNan() && z.isInfinite()) {
                ++both;
            }
        }
        // the predicates overlap, which is why the question needed a name of
        // its own: two rows are a NaN and an infinity at once
        assertEquals("rows that are NaN and infinite together", 2, both);
        assertTrue("(inf,NaN) is a NaN", new ComplexF(INF, NAN).isNan());
        assertTrue("(inf,NaN) is infinite", new ComplexF(INF, NAN).isInfinite());
        assertFalse("(inf,NaN) is not finite", new ComplexF(INF, NAN).isFinite());
        assertFalse("(NaN,inf) is not finite", new ComplexF(NAN, INF).isFinite());
        // one bad component is enough, either way round
        assertTrue("an ordinary value", new ComplexF(3.0f, 4.0f).isFinite());
        assertTrue("the largest one", new ComplexF(Float.MAX_VALUE, -Float.MAX_VALUE).isFinite());
        assertFalse("a NaN imaginary part", new ComplexF(1.0f, NAN).isFinite());
        assertFalse("a NaN real part", new ComplexF(NAN, 1.0f).isFinite());
        assertFalse("an infinite imaginary part", new ComplexF(1.0f, INF).isFinite());
        assertFalse("an infinite real part", new ComplexF(-INF, 1.0f).isFinite());
        for (int e = MIN_EXP; e <= MAX_EXP; e += 2) {
            double r = Math.pow(10.0, e);
            for (int k = 0; k < ANGLES; ++k) {
                assertTrue("the ensemble is finite", new ComplexF((float) (r * Math.cos(angle(k))),
                        (float) (r * Math.sin(angle(k)))).isFinite());
            }
        }
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
        int n = binary ? SPECIAL.length : grid(op);
        for (int j = 0; j < n; ++j) {
            ComplexF r = binary ? apply(op, z(i), z(j)) : applyScalar(op, z(i), j);
            b.append("\n  with ").append(binary ? at(SPECIAL[j][0], SPECIAL[j][1]) : label(op, j))
                    .append(" -> (").append(r.re()).append(", ").append(r.im()).append(")");
        }
        fail(b.toString());
    }

    /** how many right operands a scalar op has */
    private static int grid(String op) {
        return op.equals("powi") ? EXPONENTS.length : SCALARS.length;
    }

    private static ComplexF applyScalar(String op, ComplexF z, int j) {
        if (op.equals("scale")) {
            return z.scale(SCALARS[j]);
        } else if (op.equals("powi")) {
            return z.pow(EXPONENTS[j]);
        }
        return z.pow(SCALARS[j]);
    }

    private static String label(String op, int j) {
        return op.equals("powi") ? " " + EXPONENTS[j] : " " + SCALARS[j];
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
        int n = grid(op);
        for (int i = 0; i < SPECIAL.length; ++i) {
            long h = SEED;
            for (int j = 0; j < n; ++j) {
                h = fold(h, applyScalar(op, z(i), j));
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
            } else if (op.equals("log1p")) {
                h = fold(h, v.log1p());
            } else if (op.equals("expm1")) {
                h = fold(h, v.expm1());
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
                h = fold(h, (double) v.abs());
            } else if (op.equals("arg")) {
                h = fold(h, (double) v.arg());
            } else if (op.equals("abs2")) {
                h = fold(h, v.abs2());
            } else if (op.equals("proj")) {
                h = fold(h, v.proj());
            } else if (op.equals("finite")) {
                h = fold(h, v.isFinite() ? 1L : 0L);
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
        digest("powi", POWI_DIGEST, scalar("powi"), false);
    }

    @Test
    public void testTheFullMatrixOfUnaryResults() {
        assertEquals("inv changed", INV_DIGEST, unary("inv"));
        assertEquals("ln changed", LN_DIGEST, unary("ln"));
        assertEquals("exp changed", EXP_DIGEST, unary("exp"));
        assertEquals("log1p changed", LOG1P_DIGEST, unary("log1p"));
        assertEquals("expm1 changed", EXPM1_DIGEST, unary("expm1"));
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
        assertEquals("abs2 changed", ABS2_DIGEST, unary("abs2"));
        assertEquals("proj changed", PROJ_DIGEST, unary("proj"));
        assertEquals("isFinite changed", FINITE_DIGEST, unary("finite"));
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
        // doubleToLongBits canonicalizes them, and that part of the old
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
                "acosh", "atanh", "atan", "cot", "coth", "acot", "acoth", "sec", "csc", "sech", "csch", "asec", "asech", "acsc", "acsch", "sinc", "sinhc", "inv", "conj",
                "neg" };
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
        v.log1p();
        v.expm1();
        v.sqrt();
        v.pow(2.0f);
        v.pow(2);
        v.pow(ComplexF.I());
        v.scale(7.0f);
        v.conj();
        v.neg();
        v.abs2();
        v.proj();
        v.isFinite();
        same("untouched", 3.0f, 4.0f, v);
    }

    @Test
    public void testToString() {
        // six digits here, ten in ComplexD
        assertEquals("+1.500000E+00  -2.500000E+00i", new ComplexF(1.5f, -2.5f).toString());
        // the branch cuts read the sign of a zero, so the printout keeps it
        assertEquals("-0.000000E+00  -0.000000E+00i", new ComplexF(-0.0f, -0.0f).toString());
        assertEquals("+1.50  -2.50i", new ComplexF(1.5f, -2.5f).toString("%.2f"));
        assertEquals("-0.00  +0.00i", new ComplexF(-0.0f, 0.0f).toString("%.2f"));
    }

    @Test
    public void testToStringTellsTheFourZerosApart() {
        // printing both zeros alike had hidden three wrong expectations
        ComplexF[] zeros = { new ComplexF(0.0f, 0.0f), new ComplexF(-0.0f, -0.0f),
                new ComplexF(0.0f, -0.0f), new ComplexF(-0.0f, 0.0f) };
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        for (int i = 0; i < zeros.length; ++i) {
            seen.add(zeros[i].toString());
        }
        assertEquals("four zeros, four printouts", 4, seen.size());
        assertEquals("+0.000000E+00  -0.000000E+00i", new ComplexF(0.0f, -0.0f).toString());
        assertEquals("-0.000000E+00  +0.000000E+00i", new ComplexF(-0.0f, 0.0f).toString());
        // and sqrt is one of the operations that tell them apart
        assertEquals("+0.000000E+00  +2.000000E+00i", new ComplexF(-4.0f, 0.0f).sqrt().toString());
        assertEquals("+0.000000E+00  -2.000000E+00i", new ComplexF(-4.0f, -0.0f).sqrt().toString());
    }

    @Test
    public void testToStringLeavesNanAndInfinityAlone() {
        // only the zeros moved; format writes no sign of its own for a NaN
        assertEquals("NAN  NANi", new ComplexF(NAN, NAN).toString());
        assertEquals("a NaN carrying the sign bit", "NAN  NANi",
                new ComplexF(Float.intBitsToFloat(0xffc00000), NAN).toString());
        assertEquals("+INFINITY  -INFINITYi", new ComplexF(INF, -INF).toString());
        assertEquals("-INFINITY  +INFINITYi", new ComplexF(-INF, INF).toString());
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
            ComplexF z = z(i);
            if (z.isNan() || z.cot().isNan()) {
                continue;
            }
            ComplexF one = z.cot().mul(z.tan());
            if (one.isNan() || one.isInfinite()) {
                continue;
            }
            assertTrue("cot * tan at " + z + " gives " + one,
                    Math.abs(one.re() - 1.0f) <= 2.0f * Math.ulp(1.0f)
                            && Math.abs(one.im()) <= 2.0f * Math.ulp(1.0f));
        }
        // the float chain is a ulp off correctly rounded, which is what it is
        assertEquals("cot(1)", (float) 0.6420926159343306,
                new ComplexF(1.0f, 0.0f).cot().re(), 2.0f * Math.ulp(0.64f));
        assertEquals("coth(1)", (float) 1.3130352854993315,
                new ComplexF(1.0f, 0.0f).coth().re(), 2.0f * Math.ulp(1.31f));
    }

    @Test
    public void testCotInTheFarFieldAndAtThePole() {
        // tanh runs into 1 long before the range ends, so cot runs into -i
        same("cot(1+90i)", 0.0f, -1.0f, new ComplexF(1.0f, 90.0f).cot());
        same("coth(90+i)", 1.0f, -0.0f, new ComplexF(90.0f, 1.0f).coth());
        // the pole is inv(0), the one infinity without a direction
        same("cot(0)", INF, INF, new ComplexF(0.0f, 0.0f).cot());
        same("coth(0)", INF, INF, new ComplexF(0.0f, 0.0f).coth());
        same("coth(inf)", 1.0f, -0.0f, new ComplexF(INF, 0.0f).coth());
    }

    @Test
    public void testAcotIsAnalyticAtTheOrigin() {
        // the cut runs along the rays |Im z| >= 1, not through the origin, so
        // all four zeros answer PI/2 and only the zero of the result turns
        float h = (float) (Math.PI / 2.0);
        same("acot(+0,+0)", h, -0.0f, new ComplexF(0.0f, 0.0f).acot());
        same("acot(-0,+0)", h, -0.0f, new ComplexF(-0.0f, 0.0f).acot());
        same("acot(+0,-0)", h, 0.0f, new ComplexF(0.0f, -0.0f).acot());
        same("acot(-0,-0)", h, 0.0f, new ComplexF(-0.0f, -0.0f).acot());
        // and the neighborhood agrees from every direction
        assertTrue("from +x", Math.abs(new ComplexF(1.0e-5f, 0.0f).acot().re() - h) < 2.0e-5);
        assertTrue("from -x", Math.abs(new ComplexF(-1.0e-5f, 0.0f).acot().re() - h) < 2.0e-5);
        assertTrue("from +y", Math.abs(new ComplexF(0.0f, 1.0e-5f).acot().re() - h) < 2.0e-5);
        assertTrue("from -y", Math.abs(new ComplexF(0.0f, -1.0e-5f).acot().re() - h) < 2.0e-5);
        // acoth is the same statement turned by i
        same("acoth(+0,+0)", 0.0f, -h, new ComplexF(0.0f, 0.0f).acoth());
        same("acoth(-0,+0)", -0.0f, -h, new ComplexF(-0.0f, 0.0f).acoth());
    }

    @Test
    public void testAcotPutsItsCutOnTheRays() {
        // on the cut the sign of the zero picks the side, a difference of PI
        float v = (float) 0.5493061443340549;
        same("acot(+0+2i)", 0.0f, -v, new ComplexF(0.0f, 2.0f).acot());
        same("acot(-0+2i)", (float) Math.PI, -v, new ComplexF(-0.0f, 2.0f).acot());
        same("acot(+0-2i)", 0.0f, v, new ComplexF(0.0f, -2.0f).acot());
        same("acot(-0-2i)", (float) Math.PI, v, new ComplexF(-0.0f, -2.0f).acot());
        // the branch points themselves
        float h = (float) (Math.PI / 2.0);
        same("acot(i)", h, -INF, new ComplexF(0.0f, 1.0f).acot());
        same("acot(-i)", h, INF, new ComplexF(0.0f, -1.0f).acot());
        // inside the unit disc there is no cut at all
        float worst = 0.0f;
        ComplexF prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexF w = new ComplexF((float) (0.9 * Math.cos(ang)),
                    (float) (0.9 * Math.sin(ang))).acot();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " inside the unit disc", worst < 0.01f);
        // and outside it there is one, of exactly PI
        float up = new ComplexF(1.0e-7f, 2.0f).acot().re();
        float dn = new ComplexF(-1.0e-7f, 2.0f).acot().re();
        assertTrue("the ray does not jump by PI: " + (dn - up),
                Math.abs(dn - up - (float) Math.PI) < 1.0e-5f);
    }

    @Test
    public void testAcotIsNotAtanOfTheReciprocal() {
        // the price of the continuous branch: they differ by exactly PI in the
        // left half plane, and agree in the right one
        for (float x : new float[] { -0.5f, -1.0f, -2.0f, -100.0f }) {
            ComplexF z = new ComplexF(x, 0.0f);
            float diff = z.acot().re() - z.inv().atan().re();
            assertTrue("at " + x + " the difference is " + diff,
                    Math.abs(diff - (float) Math.PI) < 1.0e-6f);
        }
        for (float x : new float[] { 0.5f, 1.0f, 2.0f, 100.0f }) {
            ComplexF z = new ComplexF(x, 0.0f);
            assertEquals("at " + x, z.inv().atan().re(), z.acot().re(), 1.0e-7f);
        }
    }

    @Test
    public void testAcotFarOutWhereTheSubtractionWouldDie() {
        // PI/2 - atan(z) loses every digit here, which is why the reciprocal
        // form carries the far field
        for (float x : new float[] { 1.0e4f, 1.0e8f, 1.0e15f, 1.0e30f }) {
            float want = 1.0f / x;
            float got = new ComplexF(x, 0.0f).acot().re();
            assertTrue("acot(" + x + ") = " + got + ", want " + want,
                    Math.abs(got - want) <= 4.0f * Math.ulp(want));
        }
        same("acot(inf)", 0.0f, 0.0f, new ComplexF(INF, 0.0f).acot());
        same("acot(-inf)", (float) Math.PI, 0.0f, new ComplexF(-INF, 0.0f).acot());
        // below 1/MAX_VALUE the reciprocal overflows, and PI/2 is still right
        assertEquals("acot of a subnormal", (float) (Math.PI / 2.0),
                new ComplexF(1.0e-42f, 0.0f).acot().re(), 0.0f);
    }

    @Test
    public void testAcothIsAcotTurnedByI() {
        // coth(w) = i * cot(i * w), so acoth(z) = -i * acot(-i * z)
        float v = (float) 0.5493061443340549;
        same("acoth(2)", v, -0.0f, new ComplexF(2.0f, 0.0f).acoth());
        same("acoth(-2)", -v, -0.0f, new ComplexF(-2.0f, 0.0f).acoth());
        same("acoth(2i)", 0.0f, (float) -0.4636476090008061, new ComplexF(0.0f, 2.0f).acoth());
        // its cut is the mirror image, on the real rays |Re z| >= 1
        float up = new ComplexF(2.0f, 1.0e-7f).acoth().im();
        float dn = new ComplexF(2.0f, -1.0e-7f).acoth().im();
        assertTrue("the real ray does not jump by PI: " + (up - dn),
                Math.abs(Math.abs(up - dn) - (float) Math.PI) < 1.0e-5f);
        // the branch points
        float h = (float) (Math.PI / 2.0);
        same("acoth(1)", INF, -h, new ComplexF(1.0f, 0.0f).acoth());
        same("acoth(-1)", -INF, -h, new ComplexF(-1.0f, 0.0f).acoth());
    }

    @Test
    public void testAcotAndAcothSpreadNan() {
        same("acot(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).acot());
        same("acot(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).acot());
        same("acoth(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).acoth());
        same("acoth(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).acoth());
        same("cot(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).cot());
        same("coth(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).coth());
    }

    @Test
    public void testAcotIsSharpNextToTheBranchPoints() {
        // the ring just outside the unit circle, where the reciprocal form
        // threw the distance to +-i away; the double intermediates make every
        // one of these correctly rounded, so the comparison is bitwise
        same("acot(0.0553,-1.03645)", 0.4804397f, 1.7130831f,
                new ComplexF(0.0553f, -1.03645f).acot());
        same("acot(0.01,1)", 0.7828982f, -2.649165f, new ComplexF(0.01f, 1.0f).acot());
        same("acot(-0.01,1)", 2.3586946f, -2.649165f, new ComplexF(-0.01f, 1.0f).acot());
        same("acot(0.001,-0.9995)", 1.0169812f, 3.7445357f,
                new ComplexF(0.001f, -0.9995f).acot());
        same("acot(1e-4,1.01)", 0.004974962f, -2.651628f,
                new ComplexF(1.0e-4f, 1.01f).acot());
        same("acot(-1e-4,-1.0001)", 2.74896f, 4.7784405f,
                new ComplexF(-1.0e-4f, -1.0001f).acot());
        same("acot(0.25,1.05)", 0.6260245f, -1.0459526f, new ComplexF(0.25f, 1.05f).acot());
        same("acot(3.9,0.5)", 0.24734169f, -0.030414125f, new ComplexF(3.9f, 0.5f).acot());
        // and acoth is the same statement turned by i
        same("acoth(1.03645,0.0553)", 1.7130831f, -0.4804397f,
                new ComplexF(1.03645f, 0.0553f).acoth());
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
                double in = seam * (1.0 - 1.0e-7);
                double out = seam * (1.0 + 1.0e-7);
                ComplexF lo = new ComplexF((float) (in * c), (float) (in * s)).acot();
                ComplexF hi = new ComplexF((float) (out * c), (float) (out * s)).acot();
                float step = Math.max(Math.abs(hi.re() - lo.re()), Math.abs(hi.im() - lo.im()));
                assertTrue("a step of " + step + " across |z| = " + seam, step < 1.0e-3f);
            }
        }
    }

    /** how far f * parent is from one, in ulp, or zero where it says nothing */
    private static double off(ComplexF f, ComplexF parent) {
        if (f.isNan() || f.isInfinite() || parent.isNan() || parent.isInfinite()) {
            return 0.0;
        }
        ComplexF p = f.mul(parent);
        if (p.isNan() || p.isInfinite()) {
            return 0.0;
        }
        return Math.max(Math.abs(p.re() - 1.0f), Math.abs(p.im())) / Math.ulp(1.0f);
    }

    @Test
    public void testTheReciprocalsAreTheReciprocals() {
        // the only thing the four have to be, and inv is exact enough that
        // they are - so this identity is the whole accuracy test outside the
        // overflow band
        double worst = 0.0;
        for (int e = -12; e <= 12; ++e) {
            double m = Math.pow(10.0, e);
            for (int k = 0; k < 16; ++k) {
                double ang = 2.0 * Math.PI * k / 16.0;
                ComplexF z = new ComplexF((float) (m * Math.cos(ang)), (float) (m * Math.sin(ang)));
                worst = Math.max(worst, off(z.sec(), z.cos()));
                worst = Math.max(worst, off(z.csc(), z.sin()));
                worst = Math.max(worst, off(z.sech(), z.cosh()));
                worst = Math.max(worst, off(z.csch(), z.sinh()));
            }
        }
        for (int i = 0; i < SPECIAL.length; ++i) {
            ComplexF z = z(i);
            worst = Math.max(worst, off(z.sec(), z.cos()));
            worst = Math.max(worst, off(z.csc(), z.sin()));
            worst = Math.max(worst, off(z.sech(), z.cosh()));
            worst = Math.max(worst, off(z.csch(), z.sinh()));
        }
        assertTrue("the reciprocal identity is off by " + worst + " ulp", worst <= 2.0);
        same("sec(1)", 1.8508158f, 0.0f, new ComplexF(1.0f, 0.0f).sec());
        same("csc(1)", 1.1883951f, -0.0f, new ComplexF(1.0f, 0.0f).csc());
        same("sech(1)", 0.64805424f, -0.0f, new ComplexF(1.0f, 0.0f).sech());
        same("csch(1)", 0.8509181f, -0.0f, new ComplexF(1.0f, 0.0f).csch());
    }

    @Test
    public void testSechAndCschSurviveTheOverflowBand() {
        // cosh overflows at 89.42 and inv a little earlier, but 1/cosh is
        // representable to 104.67 - that whole band came back as a flat zero
        for (float x : new float[] { 89.5f, 90.0f, 95.0f, 100.0f, 103.0f }) {
            ComplexF z = new ComplexF(x, 1.0f);
            ComplexF h = new ComplexF(x / 2.0f, 0.5f).exp();
            for (ComplexF f : new ComplexF[] { z.sech(), z.csch() }) {
                assertTrue("a flat zero at x = " + x, f.re() != 0.0f || f.im() != 0.0f);
                // exp is verified, and f * e^z is two out here; the tolerance is
                // the subnormal's own resolution, nothing else
                ComplexF two = f.mul(h).mul(h);
                float tol = 8.0f * Math.ulp(f.re()) / Math.abs(f.re());
                assertEquals("f * e^z at x = " + x, 2.0f, two.re(), tol);
                assertEquals("and its imaginary part at x = " + x, 0.0f, two.im(), tol);
            }
        }
        // past the band the value is gone for good, and only the sign is left
        same("sech(105,1)", 0.0f, -0.0f, new ComplexF(105.0f, 1.0f).sech());
        same("csch(105,1)", 0.0f, -0.0f, new ComplexF(105.0f, 1.0f).csch());
    }

    @Test
    public void testSechIsEvenAndCschIsOddInTheBand() {
        // the direction is the second thing the flat zero used to destroy
        for (float y : new float[] { 1.0f, -1.0f, 2.5f, -2.5f }) {
            ComplexF p = new ComplexF(95.0f, y);
            ComplexF m = new ComplexF(-95.0f, -y);
            same("sech even at " + y, p.sech().re(), p.sech().im(), m.sech());
            same("csch odd at " + y, -p.csch().re(), -p.csch().im(), m.csch());
        }
    }

    @Test
    public void testTheOverflowBandHasNoSeam() {
        // walking into the band, every step must be the ordinary e^0.01
        float prev = Float.NaN;
        for (float x = 88.0f; x < 91.0f; x += 0.01f) {
            float v = new ComplexF(x, 1.0f).sech().re();
            assertTrue("a flat zero at x = " + x, v != 0.0f);
            if (!Float.isNaN(prev)) {
                float q = prev / v;
                assertTrue("a step of " + q + " at x = " + x, q > 1.0f && q < 1.02f);
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
        same("sec(+0,+0)", 1.0f, 0.0f, new ComplexF(0.0f, 0.0f).sec());
        same("sec(-0,+0)", 1.0f, -0.0f, new ComplexF(-0.0f, 0.0f).sec());
        same("sech(+0,+0)", 1.0f, -0.0f, new ComplexF(0.0f, 0.0f).sech());
        same("sech(-0,+0)", 1.0f, 0.0f, new ComplexF(-0.0f, 0.0f).sech());
        // at infinity the reciprocal is a zero, and that has no direction either
        same("sech(inf)", 0.0f, 0.0f, new ComplexF(INF, 0.0f).sech());
        same("csch(-inf)", 0.0f, 0.0f, new ComplexF(-INF, 0.0f).csch());
        same("sec(0,inf)", 0.0f, 0.0f, new ComplexF(0.0f, INF).sec());
        same("csc(0,inf)", 0.0f, 0.0f, new ComplexF(0.0f, INF).csc());
        same("csc(0,-inf)", 0.0f, 0.0f, new ComplexF(0.0f, -INF).csc());
        // cos of an infinite real is NaN, so its reciprocal is one too
        same("sec(inf)", NAN, NAN, new ComplexF(INF, 0.0f).sec());
        same("csc(inf)", NAN, NAN, new ComplexF(INF, 0.0f).csc());
        same("sech(1,inf)", NAN, NAN, new ComplexF(1.0f, INF).sech());
        same("csc(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).csc());
        same("sech(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).sech());
        same("csch(NaN,NaN)", NAN, NAN, new ComplexF(NAN, NAN).csch());
    }

    @Test
    public void testAsecIsSharpNextToTheBranchPoints() {
        // taking the reciprocal first throws the distance to +-1 away, which
        // costs up to 4.8e11 ulp; the double intermediates make every one of
        // these correctly rounded, so the comparison is bitwise
        same("asec(1.01,1e-4)", 0.1408376f, 6.9835345e-4f,
                new ComplexF(1.01f, 1.0e-4f).asec());
        same("asec(0.999,-5e-4)", 0.010878537f, -0.046038758f,
                new ComplexF(0.999f, -5.0e-4f).asec());
        same("asec(-1.02,0.003)", 2.9426665f, 0.014590759f,
                new ComplexF(-1.02f, 0.003f).asec());
        same("asec(-0.9995,-2e-4)", 3.1353827f, -0.032232918f,
                new ComplexF(-0.9995f, -2.0e-4f).asec());
        same("asec(1,1e-8)", 1.0e-4f, 1.0e-4f, new ComplexF(1.0f, 1.0e-8f).asec());
        same("asec(-1,1e-6)", 3.1405926f, 9.999996e-4f,
                new ComplexF(-1.0f, 1.0e-6f).asec());
        same("asec(0.5,0.25)", 0.53523844f, 1.2321615f,
                new ComplexF(0.5f, 0.25f).asec());
        // and asech is the same statement turned by i
        same("asech(1.01,1e-4)", 6.9835345e-4f, -0.1408376f,
                new ComplexF(1.01f, 1.0e-4f).asech());
    }

    @Test
    public void testAsecPutsItsCutOnTheSegment() {
        // on (-1, 1) the sign of the zero picks the side, and +0 is the limit
        // from above
        float v = (float) 1.3169578969248166;
        same("asec(0.5+0i)", 0.0f, v, new ComplexF(0.5f, 0.0f).asec());
        same("asec(0.5-0i)", 0.0f, -v, new ComplexF(0.5f, -0.0f).asec());
        same("asec(-0.5+0i)", (float) Math.PI, v, new ComplexF(-0.5f, 0.0f).asec());
        same("asec(-0.5-0i)", (float) Math.PI, -v, new ComplexF(-0.5f, -0.0f).asec());
        assertEquals("the +0 side is not the limit from above", v,
                new ComplexF(0.5f, 1.0e-30f).asec().im(), 0.0f);
        // the branch points, and outside the segment there is no cut at all
        same("asec(1)", 0.0f, 0.0f, new ComplexF(1.0f, 0.0f).asec());
        same("asec(-1)", (float) Math.PI, 0.0f, new ComplexF(-1.0f, 0.0f).asec());
        same("asec(2+0i)", (float) 1.0471975511965979, 0.0f, new ComplexF(2.0f, 0.0f).asec());
        same("asec(2-0i)", (float) 1.0471975511965979, -0.0f, new ComplexF(2.0f, -0.0f).asec());
        // a circle of radius 2 never meets the segment, so nothing jumps on it
        float worst = 0.0f;
        ComplexF prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexF w = new ComplexF((float) (2.0 * Math.cos(ang)),
                    (float) (2.0 * Math.sin(ang))).asec();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " outside the segment", worst < 0.01f);
    }

    @Test
    public void testAsechPutsItsCutsOnTheRealRays() {
        // asech's cuts are the mirror image, (-inf, 0] and (1, inf)
        float v = (float) 1.0471975511965979;
        same("asech(2+0i)", 0.0f, -v, new ComplexF(2.0f, 0.0f).asech());
        same("asech(2-0i)", 0.0f, v, new ComplexF(2.0f, -0.0f).asech());
        same("asech(-2+0i)", 0.0f, (float) -2.0943951023931953,
                new ComplexF(-2.0f, 0.0f).asech());
        same("asech(-0.5+0i)", (float) 1.3169578969248166, (float) -Math.PI,
                new ComplexF(-0.5f, 0.0f).asech());
        // and between 0 and 1 there is no cut
        same("asech(0.5+0i)", (float) 1.3169578969248166, -0.0f,
                new ComplexF(0.5f, 0.0f).asech());
        same("asech(0.5-0i)", (float) 1.3169578969248166, 0.0f,
                new ComplexF(0.5f, -0.0f).asech());
        same("asech(1)", 0.0f, -0.0f, new ComplexF(1.0f, 0.0f).asech());
        same("asech(-1)", 0.0f, (float) -Math.PI, new ComplexF(-1.0f, 0.0f).asech());
    }

    @Test
    public void testAsecSurvivesWhereTheReciprocalOverflows() {
        // inv works in float and overflows at the bottom of the range; asec
        // takes its reciprocal in double, so the value is still there
        assertTrue("the reciprocal does not overflow here after all",
                new ComplexF(Float.MIN_VALUE, Float.MIN_VALUE).inv().isInfinite());
        same("asec(MIN,MIN)", 0.7853982f, 103.6255f,
                new ComplexF(Float.MIN_VALUE, Float.MIN_VALUE).asec());
        same("asec(1e-38,2e-38)", 1.1071488f, 87.386665f,
                new ComplexF(1.0e-38f, 2.0e-38f).asec());
        same("asec(1e30)", 1.5707964f, 0.0f, new ComplexF(1.0e30f, 0.0f).asec());
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
        float h = (float) (Math.PI / 2.0);
        same("asec(inf)", h, -0.0f, new ComplexF(INF, 0.0f).asec());
        same("asec(-inf)", h, -0.0f, new ComplexF(-INF, 0.0f).asec());
        same("asec(0,inf)", h, -0.0f, new ComplexF(0.0f, INF).asec());
        same("asec(NaN,inf)", h, -0.0f, new ComplexF(NAN, INF).asec());
        same("asech(inf)", 0.0f, h, new ComplexF(INF, 0.0f).asech());
        same("asec(i)", h, (float) 0.881373587019543, new ComplexF(0.0f, 1.0f).asec());
        same("asech(i)", (float) 0.881373587019543, -h, new ComplexF(0.0f, 1.0f).asech());
        same("asec(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).asec());
        same("asec(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).asec());
        same("asech(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).asech());
        same("asech(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).asech());
    }

    @Test
    public void testAcscIsSharpNextToTheBranchPoints() {
        // asin(inv(z)) throws the distance to +-1 away and costs up to 3.6e11
        // ulp here; these are the correctly rounded values
        same("acsc(1.01,1e-4)", 1.4299587f, -6.9835345e-4f,
                new ComplexF(1.01f, 1.0e-4f).acsc());
        same("acsc(0.999,-5e-4)", 1.5599178f, 0.046038758f,
                new ComplexF(0.999f, -5.0e-4f).acsc());
        same("acsc(-1.02,0.003)", -1.3718702f, -0.014590759f,
                new ComplexF(-1.02f, 0.003f).acsc());
        same("acsc(-0.9995,-2e-4)", -1.5645863f, 0.032232918f,
                new ComplexF(-0.9995f, -2.0e-4f).acsc());
        same("acsc(1,1e-8)", 1.5706964f, -1.0e-4f, new ComplexF(1.0f, 1.0e-8f).acsc());
        same("acsc(-1,1e-6)", -1.5697962f, -9.999996e-4f,
                new ComplexF(-1.0f, 1.0e-6f).acsc());
        // and just outside the switch, where the identity would cancel instead
        same("acsc(1.2,0.1)", 0.96420467f, -0.120684005f,
                new ComplexF(1.2f, 0.1f).acsc());
        same("acsc(3,-4)", 0.11875073f, 0.16044553f, new ComplexF(3.0f, -4.0f).acsc());
        // acsch has its branch points at +-i, and gets there by the turn
        same("acsch(1e-4,1.01)", 6.9835345e-4f, -1.4299587f,
                new ComplexF(1.0e-4f, 1.01f).acsch());
    }

    @Test
    public void testAcscPutsItsCutOnTheSegment() {
        // on (-1, 1) the sign of the zero picks the side, and +0 is the limit
        // from above
        float v = (float) 1.3169578969248166;
        float h = (float) (Math.PI / 2.0);
        same("acsc(0.5+0i)", h, -v, new ComplexF(0.5f, 0.0f).acsc());
        same("acsc(0.5-0i)", h, v, new ComplexF(0.5f, -0.0f).acsc());
        same("acsc(-0.5+0i)", -h, -v, new ComplexF(-0.5f, 0.0f).acsc());
        same("acsc(-0.5-0i)", -h, v, new ComplexF(-0.5f, -0.0f).acsc());
        assertEquals("the +0 side is not the limit from above", -v,
                new ComplexF(0.5f, 1.0e-30f).acsc().im(), 0.0f);
        // the branch points, and outside the segment there is no cut at all
        same("acsc(1)", h, -0.0f, new ComplexF(1.0f, 0.0f).acsc());
        same("acsc(-1)", -h, -0.0f, new ComplexF(-1.0f, 0.0f).acsc());
        same("acsc(2+0i)", (float) 0.5235987755982989, -0.0f, new ComplexF(2.0f, 0.0f).acsc());
        same("acsc(2-0i)", (float) 0.5235987755982989, 0.0f, new ComplexF(2.0f, -0.0f).acsc());
        // a circle of radius 2 never meets the segment, so nothing jumps on it
        float worst = 0.0f;
        ComplexF prev = null;
        for (int k = 0; k <= 3600; ++k) {
            double ang = 2.0 * Math.PI * k / 3600.0;
            ComplexF w = new ComplexF((float) (2.0 * Math.cos(ang)),
                    (float) (2.0 * Math.sin(ang))).acsc();
            if (prev != null) {
                worst = Math.max(worst,
                        Math.max(Math.abs(w.re() - prev.re()), Math.abs(w.im() - prev.im())));
            }
            prev = w;
        }
        assertTrue("a jump of " + worst + " outside the segment", worst < 0.01f);
    }

    @Test
    public void testAcschPutsItsCutOnTheImaginarySegment() {
        // the turn by i carries the cut onto i*(-1, 1)
        float v = (float) 1.3169578969248166;
        float h = (float) (Math.PI / 2.0);
        same("acsch(+0+0.5i)", v, -h, new ComplexF(0.0f, 0.5f).acsch());
        same("acsch(-0+0.5i)", -v, -h, new ComplexF(-0.0f, 0.5f).acsch());
        same("acsch(+0-0.5i)", v, h, new ComplexF(0.0f, -0.5f).acsch());
        same("acsch(-0-0.5i)", -v, h, new ComplexF(-0.0f, -0.5f).acsch());
        // outside it there is none
        same("acsch(+0+2i)", 0.0f, (float) -0.5235987755982989, new ComplexF(0.0f, 2.0f).acsch());
        same("acsch(-0+2i)", -0.0f, (float) -0.5235987755982989, new ComplexF(-0.0f, 2.0f).acsch());
        // the branch point, and the real axis where acsch is real
        same("acsch(i)", 0.0f, -h, new ComplexF(0.0f, 1.0f).acsch());
        same("acsch(1)", (float) 0.881373587019543, -0.0f, new ComplexF(1.0f, 0.0f).acsch());
        same("acsch(2)", (float) 0.48121182505960347, -0.0f, new ComplexF(2.0f, 0.0f).acsch());
    }

    @Test
    public void testAcscSurvivesWhereTheReciprocalOverflows() {
        // inv works in float and overflows at the bottom of the range; acsc
        // switches to asin's asymptotic before that
        assertTrue("the reciprocal does not overflow here after all",
                new ComplexF(Float.MIN_VALUE, Float.MIN_VALUE).inv().isInfinite());
        same("acsc(MIN,MIN)", 0.7853982f, -103.6255f,
                new ComplexF(Float.MIN_VALUE, Float.MIN_VALUE).acsc());
        same("acsc(1e-38,2e-38)", 0.46364757f, -87.386665f,
                new ComplexF(1.0e-38f, 2.0e-38f).acsc());
        // and where the real part itself is tiny, which is where writing it
        // as PI/2 - atan2 would have thrown it away
        same("acsc(MIN,1e-38)", 1.4012986e-7f, -88.19138f,
                new ComplexF(Float.MIN_VALUE, 1.0e-38f).acsc());
        same("acsc(-MIN,1e-38)", -1.4012986e-7f, -88.19138f,
                new ComplexF(-Float.MIN_VALUE, 1.0e-38f).acsc());
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
        same("acsc(inf)", 0.0f, 0.0f, new ComplexF(INF, 0.0f).acsc());
        same("acsc(-inf)", 0.0f, 0.0f, new ComplexF(-INF, 0.0f).acsc());
        same("acsc(0,inf)", 0.0f, 0.0f, new ComplexF(0.0f, INF).acsc());
        same("acsc(NaN,inf)", 0.0f, 0.0f, new ComplexF(NAN, INF).acsc());
        same("acsch(inf)", -0.0f, 0.0f, new ComplexF(INF, 0.0f).acsch());
        same("acsc(i)", 0.0f, (float) -0.881373587019543, new ComplexF(0.0f, 1.0f).acsc());
        same("acsc(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).acsc());
        same("acsc(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).acsc());
        same("acsch(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).acsch());
        same("acsch(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).acsch());
    }

    @Test
    public void testSincSurvivesTheOverflowBand() {
        // sin overflows a float at |Im z| = 88.72, sin(z)/z only ln|z| later,
        // so there is a wedge where the plain quotient is already Infinity
        assertTrue("sin does not overflow here after all",
                new ComplexF(1.0e30f, 120.0f).sin().isInfinite());
        same("sinc(1e30,120)", -5.159101E21f, -3.9882164E21f,
                new ComplexF(1.0e30f, 120.0f).sinc());
        same("sinc(1e20,100)", 8.824775E22f, 1.01376865E23f,
                new ComplexF(1.0e20f, 100.0f).sinc());
        // cos(2.5) is negative, which copying the sign of y onto it would lose
        same("sinc(2.5,-94)", -2.7810672E38f, 2.195095E38f,
                new ComplexF(2.5f, -94.0f).sinc());
        same("sinc(0,94)", INF, 0.0f, new ComplexF(0.0f, 94.0f).sinc());
        same("sinhc(120,1e30)", -5.159101E21f, 3.9882164E21f,
                new ComplexF(120.0f, 1.0e30f).sinhc());
        // past 700 no float can hold the value, but the direction still stands
        same("sinc(2.5,800)", -INF, -INF, new ComplexF(2.5f, 800.0f).sinc());
        same("sinc(2.5,-800)", -INF, INF, new ComplexF(2.5f, -800.0f).sinc());
        same("sinhc(800,2.5)", -INF, INF, new ComplexF(800.0f, 2.5f).sinhc());
    }

    @Test
    public void testSincLetsEachComponentOverflowOnItsOwn() {
        // one component going infinite must not erase the other
        same("sinc(1e-20,120)", INF, -5.3888027E29f, new ComplexF(1.0e-20f, 120.0f).sinc());
        same("sinc(-3,120)", -INF, INF, new ComplexF(-3.0f, 120.0f).sinc());
        // and a zero component has to stay a zero, not turn into a NaN
        same("sinc(0,800)", INF, 0.0f, new ComplexF(0.0f, 800.0f).sinc());
    }

    @Test
    public void testTheCardinalSinesAtTheOriginAndNextToIt() {
        // one one, and no direction: all four zeros give the same
        for (int i = 0; i < 4; ++i) {
            same("sinc at zero " + i, 1.0f, 0.0f, z(i).sinc());
            same("sinhc at zero " + i, 1.0f, 0.0f, z(i).sinhc());
        }
        // the singularity is removable, so nothing is lost next to it either
        same("sinc(1e-20,0)", 1.0f, 0.0f, new ComplexF(1.0e-20f, 0.0f).sinc());
        same("sinc(0,1e-20)", 1.0f, 0.0f, new ComplexF(0.0f, 1.0e-20f).sinc());
        same("sinc(0.5)", 0.9588511f, 0.0f, new ComplexF(0.5f, 0.0f).sinc());
        same("sinhc(0.5)", 1.0421906f, 0.0f, new ComplexF(0.5f, 0.0f).sinhc());
        same("sinc(1,1)", 0.96671075f, -0.33174685f, new ComplexF(1.0f, 1.0f).sinc());
        same("sinc(2.5,-0.75)", 0.21181421f, 0.32706177f,
                new ComplexF(2.5f, -0.75f).sinc());
        same("sinc(20,3)", 0.4794348f, 0.13249053f, new ComplexF(20.0f, 3.0f).sinc());
    }

    @Test
    public void testTheCardinalSinesAtThePolesAndAtInfinity() {
        // an unbounded imaginary part leaves the modulus unbounded and the
        // direction unsettled, so the one infinity stands there
        same("sinc(0,inf)", INF, INF, new ComplexF(0.0f, INF).sinc());
        same("sinc(0,-inf)", INF, INF, new ComplexF(0.0f, -INF).sinc());
        same("sinc(inf,inf)", INF, INF, new ComplexF(INF, INF).sinc());
        same("sinc(NaN,inf)", INF, INF, new ComplexF(NAN, INF).sinc());
        same("sinhc(inf,0)", INF, INF, new ComplexF(INF, 0.0f).sinhc());
        // along the real axis the sine stays bounded, so the quotient dies away
        same("sinc(inf)", 0.0f, 0.0f, new ComplexF(INF, 0.0f).sinc());
        same("sinc(-inf)", 0.0f, 0.0f, new ComplexF(-INF, 0.0f).sinc());
        same("sinc(inf,1)", 0.0f, 0.0f, new ComplexF(INF, 1.0f).sinc());
        same("sinc(inf,NaN)", 0.0f, 0.0f, new ComplexF(INF, NAN).sinc());
        same("sinhc(0,inf)", 0.0f, 0.0f, new ComplexF(0.0f, INF).sinhc());
        same("sinc(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).sinc());
        same("sinc(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).sinc());
        same("sinhc(NaN,1)", NAN, NAN, new ComplexF(NAN, 1.0f).sinhc());
        same("sinhc(1,NaN)", NAN, NAN, new ComplexF(1.0f, NAN).sinhc());
    }

    @Test
    public void testTheCardinalSinesAreTheQuotients() {
        float[][] ps = { { 1.0f, 1.0f }, { 2.5f, -0.75f }, { -4.0f, 3.0f }, { 20.0f, 3.0f },
                { 0.25f, 0.0f }, { 0.0f, 0.25f }, { 1.0e-8f, 1.0e-8f }, { 80.0f, 0.5f } };
        for (float[] p : ps) {
            ComplexF z = new ComplexF(p[0], p[1]);
            ComplexF a = z.sinc().mul(z);
            ComplexF b = z.sinhc().mul(z);
            float ts = 8.0f * Math.ulp(z.sin().abs());
            float th = 8.0f * Math.ulp(z.sinh().abs());
            assertEquals("sinc times z re at " + z, z.sin().re(), a.re(), ts);
            assertEquals("sinc times z im at " + z, z.sin().im(), a.im(), ts);
            assertEquals("sinhc times z re at " + z, z.sinh().re(), b.re(), th);
            assertEquals("sinhc times z im at " + z, z.sinh().im(), b.im(), th);
        }
    }

    @Test
    public void testSinhcIsSincTurnedByI() {
        float[][] ps = { { 1.0f, 1.0f }, { 2.5f, -0.75f }, { -4.0f, 80.0f }, { 1.0e-30f, 3.0f },
                { 0.0f, 0.5f }, { 1.0e30f, 120.0f }, { 2.5f, 800.0f } };
        for (float[] p : ps) {
            ComplexF z = new ComplexF(p[0], p[1]);
            ComplexF t = new ComplexF(-p[1], p[0]).sinc();
            same("sinhc is the turn at " + z, t.re(), t.im(), z.sinhc());
            // and the cardinal sine is even, down to the sign of a zero
            ComplexF e = z.sinc();
            ComplexF o = z.neg().sinc();
            assertEquals("sinc is even re at " + z, e.re(), o.re(), 0.0f);
            assertEquals("sinc is even im at " + z, e.im(), o.im(), 0.0f);
        }
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

    private static final long[] POWI_DIGEST = {
            0x2D3BB2871A4117E7L, 0x2D3BB2871A4117E7L, 0x2D3BB2871A4117E7L,
            0x2D3BB2871A4117E7L, 0xB9FDD54791D53489L, 0xD3D7C21F0E7CC682L,
            0xD51898B9FCD19572L, 0x1E35D2F4C13370D5L, 0x9F939320B9F7B27CL,
            0xCFABF18E6236B7C7L, 0x38611A57A4821877L, 0x8DC705BC55C320B6L,
            0x86A15D6D3C0C47FAL, 0xA86E89714D962B4CL, 0xA86E89714D962B4CL,
            0xA86E89714D962B4CL, 0xA86E89714D962B4CL, 0xA86E89714D962B4CL,
            0xA86E89714D962B4CL, 0xA86E89714D962B4CL, 0x5A31FDAA4059AD15L,
            0x5A31FDAA4059AD15L, 0x5A31FDAA4059AD15L, 0x5A31FDAA4059AD15L,
            0x5A31FDAA4059AD15L, 0x5A31FDAA4059AD15L };

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
    private static final long COT_DIGEST = 0xFC14B7F3F1FFA30FL;
    private static final long COTH_DIGEST = 0xD05EE99586A2F5B8L;
    private static final long ACOT_DIGEST = 0x8F7D465C6EAE6A5EL;
    private static final long ACOTH_DIGEST = 0xA8104C63905DF746L;
    private static final long SEC_DIGEST   = 0x133821E603BDF070L;
    private static final long CSC_DIGEST   = 0x3595471864174910L;
    private static final long SECH_DIGEST  = 0x315E7CC44085A0CAL;
    private static final long CSCH_DIGEST  = 0xCBE17C45117CDF51L;
    private static final long ASEC_DIGEST  = 0x05C30102A02206DBL;
    private static final long ASECH_DIGEST = 0xC94155D56C57DE50L;
    private static final long ACSC_DIGEST  = 0x7829B6B340D8F7AAL;
    private static final long ACSCH_DIGEST = 0x9B71DB0E5FC8AAC6L;
    private static final long SINC_DIGEST = 0xBDF5F8533B117201L;
    private static final long SINHC_DIGEST = 0x801989F8B1714C38L;
    private static final long LOG1P_DIGEST = 0x3AC8F186565A4301L;
    private static final long EXPM1_DIGEST = 0xCFDCC7B4D0B720C4L;
    private static final long NTHROOT_DIGEST = 0x6A6E5C0A4B750BE8L;
    private static final long NTHROOTS_DIGEST = 0x6113FB7F79BEF113L;
    private static final long CONJ_DIGEST = 0x7D33B1ACC637F174L;
    private static final long NEG_DIGEST = 0x241D88E6D9266867L;
    private static final long ABS_DIGEST = 0x33688208A5C817EFL;
    private static final long ARG_DIGEST = 0xE488086C72363CF4L;
    private static final long ABS2_DIGEST = 0xD1317C100558D83CL;
    private static final long PROJ_DIGEST = 0xECF9BAD15C2473ACL;
    private static final long FINITE_DIGEST = 0x2E351FC69C8B1BACL;
    private static final long HASH_DIGEST = 0xD54F0484F04C49ECL;
    private static final long STRING_DIGEST = 0x4C3BABE2655B0E22L;
}
