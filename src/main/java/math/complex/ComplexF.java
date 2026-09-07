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

/**
 * An immutable single-precision complex number.
 * <p>
 * Infinity keeps its direction (C99 Annex G): only a result that has none
 * collapses to {@link #Inf()} - the inverse of zero, a quotient by zero, a
 * divergent {@link #pow(float)}. {@code 0 * inf} and {@code 0 / 0} are
 * {@link #NaN()}. Sum, difference, negation, conjugation and {@link #ln()} are
 * componentwise, while {@link #exp()} keeps an exact zero, so a real argument
 * stays real even where the modulus is not a number. A NaN spreads
 * componentwise too; only against an infinite operand does a NaN component
 * count as zero, so that the direction survives. {@code equals} compares the
 * two components bit for bit, as {@code Arrays.equals} does for a
 * {@code float[]}; {@code hashCode} follows.
 */
public final class ComplexF {

    private static final ComplexF NAN = new ComplexF(Float.NaN, Float.NaN);
    private static final ComplexF INF = new ComplexF(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    private static final ComplexF ZERO = new ComplexF(0.0f, 0.0f);
    private static final ComplexF ONE = new ComplexF(1.0f, 0.0f);
    private static final ComplexF I = new ComplexF(0.0f, 1.0f);

    /** ln(2), which asin's asymptotic needs */
    private static final double LN2 = 0.6931471805599453094172321;

    /** below this modulus the float reciprocal overflows */
    private static final double TINY = 0x1p-120;

    /** how close to +-1 the identity acsc = PI/2 - asec is the better form */
    private static final double NEAR = 0.3;

    private final float re;
    private final float im;

    public ComplexF(float re) {
        this(re, 0.0f);
    }

    public ComplexF(float re, float im) {
        this.re = re;
        this.im = im;
    }

    public float re() {
        return re;
    }

    public float im() {
        return im;
    }

    public static ComplexF fromPolar(float radius, float phi) {
        if (radius < 0.0f) {
            throw new IllegalArgumentException("radius must be positive : " + radius);
        }
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        // an exact zero stays zero even for an infinite radius
        return new ComplexF((float) (c == 0.0 ? c : radius * c), (float) (s == 0.0 ? s : radius * s));
    }

    public static float abs(float re, float im) {
        // sqrt(a^2 + b^2) without under/overflow
        if (im == 0.0f) {
            return Math.abs(re);
        } else if (Math.abs(re) > Math.abs(im)) {
            double abs = im / re;
            return (float) (Math.abs(re) * Math.sqrt(1.0 + abs * abs));
        } else {
            double abs = re / im;
            return (float) (Math.abs(im) * Math.sqrt(1.0 + abs * abs));
        }
    }

    /**
     * This number itself, since it cannot change.
     *
     * @return {@code this}
     */
    public ComplexF copy() {
        return this;
    }

    public ComplexF add(ComplexF that) {
        return new ComplexF(re + that.re, im + that.im);
    }

    public ComplexF sub(ComplexF that) {
        return new ComplexF(re - that.re, im - that.im);
    }

    public ComplexF mul(ComplexF that) {
        float a = re;
        float b = im;
        float c = that.re;
        float d = that.im;
        if (isInfinite() || that.isInfinite()) {
            // C99 Annex G: an infinite operand still fixes the direction
            if (Float.isInfinite(a) || Float.isInfinite(b)) {
                a = Math.copySign(Float.isInfinite(a) ? 1.0f : 0.0f, a);
                b = Math.copySign(Float.isInfinite(b) ? 1.0f : 0.0f, b);
            }
            if (Float.isInfinite(c) || Float.isInfinite(d)) {
                c = Math.copySign(Float.isInfinite(c) ? 1.0f : 0.0f, c);
                d = Math.copySign(Float.isInfinite(d) ? 1.0f : 0.0f, d);
            }
            a = zeroIfNan(a);
            b = zeroIfNan(b);
            c = zeroIfNan(c);
            d = zeroIfNan(d);
            if ((a == 0.0f && b == 0.0f) || (c == 0.0f && d == 0.0f)) {
                // zero times infinity has no direction and no modulus
                return NAN;
            }
            return new ComplexF(unbounded(a * c - b * d), unbounded(a * d + b * c));
        }
        return new ComplexF(a * c - b * d, a * d + b * c);
    }

    private static float zeroIfNan(float x) {
        return Float.isNaN(x) ? Math.copySign(0.0f, x) : x;
    }

    // C99 scales by infinity here; an exact zero keeps its sign instead
    private static float unbounded(float x) {
        if (x == 0.0f || Float.isNaN(x)) {
            return x;
        }
        return (x > 0.0f) ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
    }

    public ComplexF div(ComplexF that) {
        float c = that.re;
        float d = that.im;
        if (c == 0.0f && d == 0.0f) {
            // zero over zero has no value, anything else over zero is inv(0)
            if (re == 0.0f && im == 0.0f) {
                return NAN;
            }
            return INF;
        }
        boolean thisInfinite = isInfinite();
        if (that.isInfinite() && !thisInfinite) {
            return ZERO;
        }
        if (thisInfinite && Float.isFinite(c) && Float.isFinite(d)) {
            // C99 Annex G: an infinite numerator still fixes the direction
            float a = Math.copySign(Float.isInfinite(re) ? 1.0f : 0.0f, re);
            float b = Math.copySign(Float.isInfinite(im) ? 1.0f : 0.0f, im);
            return new ComplexF(unbounded(a * c + b * d), unbounded(b * c - a * d));
        }
        // limit overflow/underflow
        if (Math.abs(c) < Math.abs(d)) {
            float q = c / d;
            float denom = c * q + d;
            float real = re;
            return new ComplexF((real * q + im) / denom, (im * q - real) / denom);
        }
        float q = d / c;
        float denom = d * q + c;
        float real = re;
        return new ComplexF((im * q + real) / denom, (im - real * q) / denom);
    }

    public ComplexF inv() {
        if (re == 0.0f && im == 0.0f) {
            return INF;
        }
        if (isInfinite()) {
            return ZERO;
        }
        // the scaling from div(), with a numerator of (1, 0)
        float c = re;
        float d = im;
        if (Math.abs(c) < Math.abs(d)) {
            float q = c / d;
            float denom = c * q + d;
            return new ComplexF(q / denom, -1.0f / denom);
        }
        float q = d / c;
        float denom = d * q + c;
        return new ComplexF(1.0f / denom, -q / denom);
    }

    public ComplexF ln() {
        float abs = abs();
        float phi = arg();
        return new ComplexF((float) Math.log(abs), phi);
    }

    public ComplexF exp() {
        double expRe = Math.exp(re);
        double c = Math.cos(im);
        double s = Math.sin(im);
        // an exact zero stays zero even when expRe has overflown
        return new ComplexF((c == 0.0) ? (float) c : (float) (expRe * c),
                (s == 0.0) ? (float) s : (float) (expRe * s));
    }

    /**
     * Principal square root: the root whose real part is not negative.
     *
     * @return the principal square root of this complex number
     */
    public ComplexF sqrt() {
        if (Float.isInfinite(im)) {
            // C99: an infinite imaginary part decides, whatever the real part is
            return new ComplexF(Float.POSITIVE_INFINITY, Math.copySign(Float.POSITIVE_INFINITY, im));
        }
        if (re == 0.0f && im == 0.0f) {
            return new ComplexF(0.0f, im);
        }
        // Kahan: t is built from |re| so that nothing cancels, and the sum is
        // taken in double, where no float can overflow or turn subnormal
        double t = Math.sqrt((Math.abs((double) re) + wideModulus(re, im)) / 2.0);
        if (re >= 0.0f) {
            return new ComplexF((float) t, (float) (im / (2.0 * t)));
        }
        return new ComplexF((float) (Math.abs((double) im) / (2.0 * t)), Math.copySign((float) t, im));
    }

    // the modulus in double, quotient included; abs() takes that quotient in
    // float, and rounding it early costs the subnormals their digits
    private static double wideModulus(float re, float im) {
        if (Float.isInfinite(re) || Float.isInfinite(im)) {
            return Double.POSITIVE_INFINITY;
        }
        if (im == 0.0f) {
            return Math.abs((double) re);
        } else if (Math.abs(re) > Math.abs(im)) {
            double abs = (double) im / re;
            return Math.abs((double) re) * Math.sqrt(1.0 + abs * abs);
        } else {
            double abs = (double) re / im;
            return Math.abs((double) im) * Math.sqrt(1.0 + abs * abs);
        }
    }

    // a NaN has no sign to contribute
    private static double sign(double x) {
        return Double.isNaN(x) ? 1.0 : Math.copySign(1.0, x);
    }

    // an exact zero survives a factor that has no value, keeping the product sign
    private static double product(double a, double b) {
        if (a == 0.0 && !Double.isFinite(b)) {
            return Math.copySign(a, sign(a) * sign(b));
        }
        if (b == 0.0 && !Double.isFinite(a)) {
            return Math.copySign(b, sign(a) * sign(b));
        }
        return a * b;
    }

    private static ComplexF sinhOf(float x, float y) {
        if (Float.isInfinite(x) && !Float.isFinite(y)) {
            // the modulus survives, the direction does not
            return new ComplexF(Float.POSITIVE_INFINITY, Float.NaN);
        }
        return new ComplexF((float) product(Math.sinh(x), Math.cos(y)),
                (float) product(Math.cosh(x), Math.sin(y)));
    }

    private static ComplexF coshOf(float x, float y) {
        if (Float.isInfinite(x) && !Float.isFinite(y)) {
            return new ComplexF(Float.POSITIVE_INFINITY, Float.NaN);
        }
        return new ComplexF((float) product(Math.cosh(x), Math.cos(y)),
                (float) product(Math.sinh(x), Math.sin(y)));
    }

    /**
     * Hyperbolic sine.
     *
     * @return the hyperbolic sine of this complex number
     */
    public ComplexF sinh() {
        return sinhOf(re, im);
    }

    /**
     * Hyperbolic cosine.
     *
     * @return the hyperbolic cosine of this complex number
     */
    public ComplexF cosh() {
        return coshOf(re, im);
    }

    /**
     * Sine.
     *
     * @return the sine of this complex number
     */
    public ComplexF sin() {
        // sin(z) = -i * sinh(i * z)
        ComplexF s = sinhOf(-im, re);
        return new ComplexF(s.im, -s.re);
    }

    /**
     * Cosine.
     *
     * @return the cosine of this complex number
     */
    public ComplexF cos() {
        // cos(z) = cosh(i * z)
        return coshOf(-im, re);
    }

    private static ComplexF tanhOf(float x, float y) {
        if (Float.isInfinite(x)) {
            // the real part saturates, the imaginary part dies away
            return new ComplexF(Math.copySign(1.0f, x),
                    Math.copySign(0.0f, Float.isFinite(y) ? (float) Math.sin(2.0 * y) : y));
        }
        if (Float.isNaN(x)) {
            return new ComplexF(Float.NaN, y == 0.0f ? y : Float.NaN);
        }
        if (!Float.isFinite(y)) {
            return new ComplexF(Float.NaN, Float.NaN);
        }
        double t = Math.tan(y);
        if (t == 0.0) {
            // on the real axis this is Math.tanh itself
            return new ComplexF((float) Math.tanh(x), (float) t);
        }
        // sinh and cosh in the denominator would overflow near |x| = 355, so
        // the quotient is divided through by cos(y) first
        double s = Math.sinh(x);
        double b = 1.0 + t * t;
        double d = 1.0 + b * s * s;
        if (!Double.isFinite(d)) {
            // beyond the range only the limit is left
            return new ComplexF(Math.copySign(1.0f, x), (float) Math.copySign(0.0, t));
        }
        return new ComplexF((float) (b * s * Math.cosh(x) / d), (float) (t / d));
    }

    /**
     * Hyperbolic tangent.
     *
     * @return the hyperbolic tangent of this complex number
     */
    public ComplexF tanh() {
        return tanhOf(re, im);
    }

    /**
     * Tangent.
     *
     * @return the tangent of this complex number
     */
    public ComplexF tan() {
        // tan(z) = -i * tanh(i * z)
        ComplexF u = tanhOf(-im, re);
        return new ComplexF(u.im, -u.re);
    }

    /**
     * Principal n-th root, the one with the smallest argument.
     *
     * @param n the degree of the root, positive
     * @return the principal n-th root of this complex number
     */
    public ComplexF nthRoot(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive : " + n);
        }
        if (n == 1) {
            // the general formula goes through cos and sin and would not
            // reproduce this value exactly
            return this;
        }
        if (n == 2) {
            return sqrt();
        }
        return fromPolar((float) Math.pow(abs(), 1.0 / n), (float) (arg() / (double) n));
    }

    /**
     * All n roots of degree n, the principal one first. The array is fresh on
     * every call and belongs to the caller.
     *
     * @param n the degree of the roots, positive
     * @return the n roots of this complex number
     */
    public ComplexF[] nthRoots(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive : " + n);
        }
        if (n == 2) {
            // the rotation by cis(pi) is an exact negation
            ComplexF w = sqrt();
            return new ComplexF[] { w, w.neg() };
        }
        ComplexF[] roots = new ComplexF[n];
        // taking the first one from nthRoot keeps the two methods in step
        roots[0] = nthRoot(n);
        float rho = (float) Math.pow(abs(), 1.0 / n);
        double phi = arg() / (double) n;
        for (int k = 1; k < n; ++k) {
            roots[k] = fromPolar(rho, (float) (phi + 2.0 * Math.PI * k / n));
        }
        return roots;
    }

    private static ComplexF atanhOf(float x, float y) {
        if (Float.isInfinite(x) || Float.isInfinite(y)) {
            // the modulus dies away, the angle saturates
            return new ComplexF(Math.copySign(0.0f, x),
                    Float.isNaN(y) ? Float.NaN : Math.copySign((float) (Math.PI / 2.0), y));
        }
        if (Float.isNaN(x)) {
            return new ComplexF(Float.NaN, Float.NaN);
        }
        if (Float.isNaN(y)) {
            return new ComplexF(x == 0.0f ? x : Float.NaN, Float.NaN);
        }
        if (x < 0.0) {
            // atanh is odd, and 1 + 4x/d is (1+x)^2/(1-x)^2, which
            // cancels for a negative x
            ComplexF w = atanhOf(-x, -y);
            return new ComplexF(-w.re, -w.im);
        }
        double d = (1.0 - x) * (1.0 - x) + (double) y * y;
        // no far field branch here: d is computed in double, and over the
        // whole float range it stays below 2.4e77, so it cannot overflow
        // log1p, because 1 + z rounds back to 1 for a small z
        return new ComplexF((float) (0.25 * Math.log1p(4.0 * x / d)),
                (float) (0.5 * Math.atan2(2.0 * y, (1.0 - x) * (1.0 + x) - (double) y * y)));
    }

    /**
     * Inverse hyperbolic tangent.
     *
     * @return the inverse hyperbolic tangent of this complex number
     */
    public ComplexF atanh() {
        return atanhOf(re, im);
    }

    /**
     * Inverse tangent, with a real part between -PI/2 and PI/2.
     *
     * @return the inverse tangent of this complex number
     */
    public ComplexF atan() {
        // atan(z) = -i * atanh(i * z)
        ComplexF u = atanhOf(-im, re);
        return new ComplexF(u.im, -u.re);
    }

    // the inverse hyperbolic sine of a real number, which Math does not have.
    // No overflow guard: over the whole float range a stays below 1e38 here,
    // so a * a cannot leave the double range.
    private static double realAsinh(double t) {
        double a = Math.abs(t);
        return Math.copySign(Math.log1p(a + a * a / (1.0 + Math.sqrt(1.0 + a * a))), t);
    }

    // the principal square root of a + i*b in double, into r[k] and r[k+1]
    private static void sqrtInto(double a, double b, double[] r, int k) {
        if (a == 0.0 && b == 0.0) {
            r[k] = 0.0;
            r[k + 1] = b;
            return;
        }
        // Kahan: t is built from |a| so that nothing cancels
        double t = Math.sqrt((Math.abs(a) + Math.hypot(a, b)) / 2.0);
        if (a >= 0.0) {
            r[k] = t;
            r[k + 1] = b / (2.0 * t);
        } else {
            r[k] = Math.abs(b) / (2.0 * t);
            r[k + 1] = Math.copySign(t, b);
        }
    }

    private static ComplexF asinhOf(float x, float y) {
        if (Float.isInfinite(y)) {
            // the modulus grows without bound, the angle saturates
            float angle = Float.isNaN(x) ? Float.NaN
                    : Math.copySign((float) (Float.isInfinite(x) ? Math.PI / 4.0 : Math.PI / 2.0), y);
            return new ComplexF(Math.copySign(Float.POSITIVE_INFINITY, x), angle);
        }
        if (Float.isInfinite(x)) {
            return new ComplexF(x, Float.isNaN(y) ? Float.NaN : Math.copySign(0.0f, y));
        }
        if (Float.isNaN(x) || Float.isNaN(y)) {
            // asinh is real on the real axis, so an exact zero survives there
            return new ComplexF(Float.NaN, (Float.isNaN(x) && y == 0.0f) ? y : Float.NaN);
        }
        if (x < 0.0f) {
            // asinh is odd, and this keeps the two halves of the plane in step
            ComplexF w = asinhOf(-x, -y);
            return new ComplexF(-w.re, -w.im);
        }
        // 1 + z^2 is (1 + i*z)(1 - i*z); the roots taken apart, so nothing
        // cancels, and taken in double, which is worth two bits of the result.
        // No far field branch either: the products stay below 1e38.
        double[] r = new double[4];
        sqrtInto(1.0 - y, x, r, 0);
        sqrtInto(1.0 + y, -x, r, 2);
        return new ComplexF((float) realAsinh(r[2] * r[1] - r[3] * r[0]),
                (float) Math.atan2(y, r[0] * r[2] - r[1] * r[3]));
    }

    /**
     * Inverse hyperbolic sine.
     *
     * @return the inverse hyperbolic sine of this complex number
     */
    public ComplexF asinh() {
        return asinhOf(re, im);
    }

    /**
     * Inverse sine, with a real part between -PI/2 and PI/2.
     *
     * @return the inverse sine of this complex number
     */
    public ComplexF asin() {
        // asin(z) = -i * asinh(i * z)
        ComplexF u = asinhOf(-im, re);
        return new ComplexF(u.im, -u.re);
    }

    private static ComplexF acosOf(float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) {
            if (Float.isInfinite(y)) {
                return new ComplexF(Float.NaN, -Math.copySign(Float.POSITIVE_INFINITY, y));
            }
            if (Float.isInfinite(x)) {
                return new ComplexF(Float.NaN, Float.NEGATIVE_INFINITY);
            }
            // acos carries the imaginary axis onto itself, so PI/2 survives there
            return new ComplexF(x == 0.0f ? (float) (Math.PI / 2.0) : Float.NaN, Float.NaN);
        }
        if (Float.isInfinite(x) || Float.isInfinite(y)) {
            // the angle saturates, the modulus grows without bound
            double re;
            if (Float.isInfinite(x)) {
                re = Float.isInfinite(y) ? (x > 0.0f ? Math.PI / 4.0 : 3.0 * Math.PI / 4.0)
                        : (x > 0.0f ? 0.0 : Math.PI);
            } else {
                re = Math.PI / 2.0;
            }
            return new ComplexF((float) re, -Math.copySign(Float.POSITIVE_INFINITY, y));
        }
        // 1 - z^2 is (1 - z)(1 + z); the roots taken apart, so nothing cancels,
        // and taken in double. No far field branch: the products stay below 1e38.
        double[] r = new double[4];
        sqrtInto(1.0 - x, -y, r, 0);
        sqrtInto(1.0 + x, y, r, 2);
        return acosFromRoots(r);
    }

    // Kahan's ending, shared with asec, which supplies its own roots
    private static ComplexF acosFromRoots(double[] r) {
        return new ComplexF((float) (2.0 * Math.atan2(r[0], r[2])),
                (float) realAsinh(r[2] * r[1] - r[3] * r[0]));
    }

    // 1 / (x + i y) in double, in inv()'s shape, which differs from div()'s by
    // exactly the sign of a zero
    private static void invInto(double x, double y, double[] r, int k) {
        if (Math.abs(x) < Math.abs(y)) {
            double q = x / y;
            double denom = x * q + y;
            r[k] = q / denom;
            r[k + 1] = -1.0 / denom;
            return;
        }
        double q = y / x;
        double denom = y * q + x;
        r[k] = 1.0 / denom;
        r[k + 1] = -q / denom;
    }

    // (u + i v) / (x + i y) in double, in div()'s shape, which is what keeps
    // the sign of a zero; a float squared cannot overflow a double
    private static void divInto(double u, double v, double x, double y, double[] r, int k) {
        if (Math.abs(x) < Math.abs(y)) {
            double q = x / y;
            double denom = x * q + y;
            r[k] = (u * q + v) / denom;
            r[k + 1] = (v * q - u) / denom;
            return;
        }
        double q = y / x;
        double denom = y * q + x;
        r[k] = (v * q + u) / denom;
        r[k + 1] = (v - u * q) / denom;
    }
    private static ComplexF asecOf(float x, float y) {
        if (x == 0.0f && y == 0.0f) {
            // the modulus is unbounded and the real part is whatever direction
            // one came from, so there is no value with a direction here
            return INF;
        }
        if (Float.isInfinite(x) || Float.isInfinite(y)) {
            // the reciprocal of an infinity is the zero without a direction
            return acosOf(0.0f, 0.0f);
        }
        double[] r = new double[4];
        double[] t = new double[2];
        if (wideModulus(x - Math.copySign(1.0f, x), y) < 1.0) {
            // next to +-1 the reciprocal throws the distance to the branch point
            // away; 1 -+ 1/z written as (z -+ 1)/z keeps it, because the
            // numerator is exact there
            divInto((double) x - 1.0, y, x, y, t, 0);
            sqrtInto(t[0], t[1], r, 0);
            divInto((double) x + 1.0, y, x, y, t, 0);
            sqrtInto(t[0], t[1], r, 2);
            return acosFromRoots(r);
        }
        // away from them that quotient is the one that cancels; the reciprocal
        // is taken in double, where a float cannot overflow it, and in inv's
        // shape, which is what keeps the sign of the zero
        invInto(x, y, t, 0);
        sqrtInto(1.0 - t[0], -t[1], r, 0);
        sqrtInto(1.0 + t[0], t[1], r, 2);
        return acosFromRoots(r);
    }

    private static ComplexF asechOf(float x, float y) {
        if (x == 0.0f && y == 0.0f) {
            return INF;
        }
        // the same quarter turn acosh is
        ComplexF w = asecOf(x, y);
        return new ComplexF(Math.abs(w.im), Math.copySign(w.re, -w.im));
    }

    private static ComplexF acoshOf(float x, float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) {
            if (Float.isInfinite(x) || Float.isInfinite(y)) {
                return new ComplexF(Float.POSITIVE_INFINITY, Float.NaN);
            }
            return new ComplexF(Float.NaN, Float.NaN);
        }
        // a quarter turn of acos, to the side that keeps the real part positive
        ComplexF w = acosOf(x, y);
        return new ComplexF(Math.abs(w.im), Math.copySign(w.re, -w.im));
    }

    /**
     * Inverse cosine, with a real part between 0 and PI.
     *
     * @return the inverse cosine of this complex number
     */
    public ComplexF acos() {
        return acosOf(re, im);
    }

    /**
     * Inverse hyperbolic cosine, with a real part that is not negative.
     *
     * @return the inverse hyperbolic cosine of this complex number
     */
    public ComplexF acosh() {
        return acoshOf(re, im);
    }

    /**
     * Inverse secant, with its cut on the segment {@code (-1, 1)}.
     *
     * @return the inverse secant of this complex number
     */
    public ComplexF asec() {
        return asecOf(re, im);
    }

    /**
     * Inverse hyperbolic secant, with its cuts on the real rays
     * {@code (-inf, 0]} and {@code (1, inf)}.
     *
     * @return the inverse hyperbolic secant of this complex number
     */
    public ComplexF asech() {
        return asechOf(re, im);
    }

    private static ComplexF acscOf(float x, float y) {
        if (x == 0.0f && y == 0.0f) {
            // the modulus is unbounded and the real part is whatever direction
            // one came from, so there is no value with a direction here
            return INF;
        }
        double h = wideModulus(x, y);
        if (h < TINY) {
            // the reciprocal would overflow in float. This is asin's asymptotic,
            // with PI/2 - atan2(|y|, x) written as atan2(x, |y|) so that nothing
            // cancels; widening to double is what lifts the subnormals here
            return new ComplexF((float) Math.atan2(x, Math.abs((double) y)),
                    (float) Math.copySign(LN2 - Math.log(h), -y));
        }
        if (wideModulus(x - Math.copySign(1.0f, x), y) < NEAR) {
            // asin + acos = PI/2 exactly, and this close to +-1 asec is nowhere
            // near PI/2, so the subtraction costs nothing and asec's accuracy
            // at its branch points carries over
            ComplexF w = asecOf(x, y);
            return new ComplexF((float) (Math.PI / 2.0 - w.re()), -w.im());
        }
        // further out that subtraction is the one that cancels, while 1/z is
        // harmless there
        return new ComplexF(x, y).inv().asin();
    }

    private static ComplexF acschOf(float x, float y) {
        if (x == 0.0f && y == 0.0f) {
            return INF;
        }
        // asinh(u) = i * asin(-i * u), so acsch(z) = i * acsc(i * z)
        ComplexF u = acscOf(-y, x);
        return new ComplexF(-u.im, u.re);
    }

    /**
     * Inverse cosecant, with its cut on the segment {@code (-1, 1)}.
     *
     * @return the inverse cosecant of this complex number
     */
    public ComplexF acsc() {
        return acscOf(re, im);
    }

    /**
     * Inverse hyperbolic cosecant, with its cut on the imaginary segment
     * {@code i*(-1, 1)}.
     *
     * @return the inverse hyperbolic cosecant of this complex number
     */
    public ComplexF acsch() {
        return acschOf(re, im);
    }

    /**
     * Cotangent.
     *
     * @return the cotangent of this complex number
     */
    public ComplexF cot() {
        return tan().inv();
    }

    /**
     * Hyperbolic cotangent.
     *
     * @return the hyperbolic cotangent of this complex number
     */
    public ComplexF coth() {
        return tanh().inv();
    }

    private static ComplexF sechOf(float x, float y) {
        ComplexF w = coshOf(x, y).inv();
        if (w.re() == 0.0f && w.im() == 0.0f && Float.isFinite(x)) {
            // the quotient underflowed on the way: cosh overflows at 89.42 and
            // inv a little before that, while 1/cosh is representable to
            // 104.67. That far out cosh and sinh are equal to the last bit, so
            // what is left is a magnitude times a unit vector
            double e = 2.0 * Math.exp(-Math.abs((double) x));
            return new ComplexF((float) (e * Math.cos(y)),
                    (float) (-Math.copySign(e, x) * Math.sin(y)));
        }
        return w;
    }

    private static ComplexF cschOf(float x, float y) {
        ComplexF w = sinhOf(x, y).inv();
        if (w.re() == 0.0f && w.im() == 0.0f && Float.isFinite(x)) {
            // the same band, except that sinh carries the sign of x through
            double e = 2.0 * Math.exp(-Math.abs((double) x));
            return new ComplexF((float) (Math.copySign(e, x) * Math.cos(y)),
                    (float) (-e * Math.sin(y)));
        }
        return w;
    }

    /**
     * Secant.
     *
     * @return the secant of this complex number
     */
    public ComplexF sec() {
        // sec(z) = sech(i * z)
        return sechOf(-im, re);
    }

    /**
     * Cosecant.
     *
     * @return the cosecant of this complex number
     */
    public ComplexF csc() {
        // csc(z) = i * csch(i * z)
        ComplexF u = cschOf(-im, re);
        if (u.isInfinite()) {
            // the pole, and this library has one infinity without a direction
            return INF;
        }
        if (isInfinite()) {
            // and one zero without one either, which the turn would undo
            return u;
        }
        return new ComplexF(-u.im, u.re);
    }

    /**
     * Hyperbolic secant.
     *
     * @return the hyperbolic secant of this complex number
     */
    public ComplexF sech() {
        return sechOf(re, im);
    }

    /**
     * Hyperbolic cosecant.
     *
     * @return the hyperbolic cosecant of this complex number
     */
    public ComplexF csch() {
        return cschOf(re, im);
    }

    private static ComplexF sincOf(float x, float y) {
        if (x == 0.0f && y == 0.0f) {
            // one one, and no direction
            return ONE;
        }
        if (Float.isInfinite(y)) {
            // the modulus is unbounded, the direction is not settled
            return INF;
        }
        if (Float.isInfinite(x)) {
            // the sine stays bounded along the real axis, the quotient dies
            return ZERO;
        }
        if (Float.isNaN(x) || Float.isNaN(y)) {
            return NAN;
        }
        double a = x;
        double b = y;
        if (Math.abs(b) <= 700.0) {
            // sin(z) in double, where it cannot overflow before the quotient
            // does, so this rounds only once
            double[] r = new double[2];
            divInto(product(Math.sin(a), Math.cosh(b)), product(Math.cos(a), Math.sinh(b)), a, b,
                    r, 0);
            return new ComplexF((float) r[0], (float) r[1]);
        }
        // beyond that no float can hold the value, but the direction still can
        double c = (b < 0.0) ? -Math.cos(a) : Math.cos(a);
        double[] r = new double[2];
        divInto(Math.sin(a), c, a, b, r, 0);
        double h = 0.5 * Math.exp(Math.abs(b));
        return new ComplexF((float) product(r[0], h), (float) product(r[1], h));
    }

    /**
     * Cardinal sine, sin(z) / z, which is 1 at the origin.
     *
     * @return the cardinal sine of this complex number
     */
    public ComplexF sinc() {
        return sincOf(re, im);
    }

    /**
     * Cardinal hyperbolic sine, sinh(z) / z, which is 1 at the origin.
     *
     * @return the cardinal hyperbolic sine of this complex number
     */
    public ComplexF sinhc() {
        // sinc(i * z) = sinh(z) / z
        return sincOf(-im, re);
    }

    private static ComplexF acotOf(float x, float y) {
        double h = wideModulus(x, y);
        if (h <= 1.0) {
            // inside the unit disc atan stays well clear of PI/2, so nothing
            // cancels here - and the subtraction keeps the distance to the
            // branch points +-i, which taking the reciprocal would throw away
            ComplexF t = new ComplexF(x, y).atan();
            return new ComplexF((float) (Math.PI / 2.0 - t.re()), -t.im());
        }
        if (h <= 4.0) {
            // just outside, the reciprocal loses the distance to +-i; one
            // factor of (y-1)*(y+1) is exact there, so this form keeps it.
            // log1p takes the ratio that stays positive, the other cancels
            double dx = x;
            double dy = y;
            double re = Math.atan2(2.0 * dx, dx * dx + (dy - 1.0) * (dy + 1.0)) / 2.0;
            double im = (dy >= 0.0)
                    ? -Math.log1p(4.0 * dy / (dx * dx + (dy - 1.0) * (dy - 1.0))) / 4.0
                    : Math.log1p(-4.0 * dy / (dx * dx + (dy + 1.0) * (dy + 1.0))) / 4.0;
            return lift(re, im, x);
        }
        // far out atan(z) runs into PI/2 and that subtraction loses every
        // digit; atan(1/z) does not, and no branch point is near
        ComplexF t = new ComplexF(x, y).inv().atan();
        return lift(t.re(), t.im(), x);
    }

    // both forms above cut the segment (-i, i); lifting the left half plane by
    // PI moves the cut onto the rays. copySign, because -0.0f < 0.0f is false
    private static ComplexF lift(double re, double im, float x) {
        if (Math.copySign(1.0f, x) < 0.0f) {
            return new ComplexF((float) (re + Math.PI), (float) im);
        }
        return new ComplexF((float) re, (float) im);
    }

    /**
     * Inverse cotangent, continuous at the origin, with its cuts on the rays
     * {@code |Im z| >= 1}. Note that this is not {@code atan(1/z)}, which
     * differs by PI in the left half plane.
     *
     * @return the inverse cotangent of this complex number
     */
    public ComplexF acot() {
        return acotOf(re, im);
    }

    /**
     * Inverse hyperbolic cotangent, continuous at the origin, with its cuts on
     * the rays {@code |Re z| >= 1}.
     *
     * @return the inverse hyperbolic cotangent of this complex number
     */
    public ComplexF acoth() {
        // coth(w) = i * cot(i * w), so acoth(z) = -i * acot(-i * z)
        ComplexF u = acotOf(im, -re);
        return new ComplexF(u.im, -u.re);
    }

    public ComplexF pow(float exponent) {
        if (isDegenerate()) {
            return degeneratePow(exponent);
        }
        if (Float.isInfinite(exponent)) {
            return infinitePow(exponent);
        }
        return ln().scale(exponent).exp();
    }

    public ComplexF pow(ComplexF exponent) {
        if (isDegenerate()) {
            return degeneratePow(exponent);
        }
        if (exponent.isInfinite()) {
            if (exponent.im == 0.0f) {
                return infinitePow(exponent.re);
            }
            return NAN;
        }
        return ln().mul(exponent).exp();
    }

    // the values of Math.pow, with the modulus in place of |x|
    private ComplexF infinitePow(float exponent) {
        float r = abs();
        if (Float.isNaN(r) || r == 1.0f) {
            return NAN;
        } else if ((r > 1.0f) == (exponent > 0.0f)) {
            return INF;
        } else {
            return ZERO;
        }
    }

    // a base that ln() cannot carry
    private boolean isDegenerate() {
        return (re == 0.0f && im == 0.0f) || isInfinite();
    }

    // the values of Math.pow, mirrored for an infinite base
    private ComplexF degeneratePow(float exponent) {
        boolean zeroBase = (re == 0.0f && im == 0.0f);
        if (isNan() || Float.isNaN(exponent)) {
            return NAN;
        } else if (exponent == 0.0f) {
            return ONE;
        } else if (zeroBase == (exponent > 0.0f)) {
            return ZERO;
        } else {
            return INF;
        }
    }

    private ComplexF degeneratePow(ComplexF exponent) {
        float a = exponent.re;
        float b = exponent.im;
        if (a == 0.0f && b == 0.0f) {
            return ONE;
        }
        if (a == 0.0f || Float.isNaN(b) || Float.isInfinite(b)) {
            // only a real exponent is defined here, and 0^(bi) is not
            return NAN;
        }
        return degeneratePow(a);
    }

    /**
     * Multiplication with a real number.
     *
     * @param alpha the multiplicand
     * @return the result of the multiplication
     */
    public ComplexF scale(float alpha) {
        float a = re;
        float b = im;
        float c = alpha;
        if (isInfinite() || Float.isInfinite(alpha)) {
            if (Float.isInfinite(a) || Float.isInfinite(b)) {
                a = Math.copySign(Float.isInfinite(a) ? 1.0f : 0.0f, a);
                b = Math.copySign(Float.isInfinite(b) ? 1.0f : 0.0f, b);
            }
            if (Float.isInfinite(c)) {
                c = Math.copySign(1.0f, c);
            }
            a = zeroIfNan(a);
            b = zeroIfNan(b);
            c = zeroIfNan(c);
            if ((a == 0.0f && b == 0.0f) || c == 0.0f) {
                return NAN;
            }
            return new ComplexF(unbounded(a * c), unbounded(b * c));
        }
        return new ComplexF(alpha * a, alpha * b);
    }

    public ComplexF conj() {
        return new ComplexF(re, -im);
    }

    public ComplexF neg() {
        return new ComplexF(-re, -im);
    }

    public boolean isReal() {
        return im == 0.0f && !Float.isNaN(re);
    }

    /**
     * Compute the argument of this complex number, between -PI (not inclusive)
     * and PI (inclusive). Also known as {@code phase} or {@code angle}.
     *
     * @return the angle of this complex number
     */
    public float arg() {
        return (float) Math.atan2(im, re);
    }

    /**
     * Also known as {@code magnitude} or {@code modulus}.
     *
     * @return the absolute value of this complex number
     */
    public float abs() {
        if (isInfinite()) {
            return Float.POSITIVE_INFINITY;
        }
        // sqrt(a^2 + b^2) without under/overflow
        float re = this.re;
        float im = this.im;
        if (im == 0.0f) {
            return Math.abs(re);
        } else if (Math.abs(re) > Math.abs(im)) {
            double abs = im / re;
            return (float) (Math.abs(re) * Math.sqrt(1.0 + abs * abs));
        } else {
            double abs = re / im;
            return (float) (Math.abs(im) * Math.sqrt(1.0 + abs * abs));
        }
    }

    public boolean isNan() {
        return Float.isNaN(re) || Float.isNaN(im);
    }

    public boolean isInfinite() {
        return Float.isInfinite(re) || Float.isInfinite(im);
    }

    @Override
    public String toString() {
        return toString("%.6E");
    }

    /**
     * Get this complex number as a formatted string.
     *
     * @param format
     *            a format string in
     *            {@link java.util.Formatter#format(String, Object...)} format
     *            string syntax
     * @return this complex number formatted as specified in the {@code format}
     *         syntax
     */
    public String toString(String format) {
        StringBuilder buf = new StringBuilder(40);
        if (needsPlus(re)) {
            buf.append("+");
        }
        buf.append(String.format(format, re)).append("  ");
        if (needsPlus(im)) {
            buf.append("+");
        }
        buf.append(String.format(format, im)).append("i");
        return buf.toString();
    }

    // format writes the sign itself, so prepend one only for a positive value;
    // a negative zero keeps its sign, the branch cuts read it
    private static boolean needsPlus(float x) {
        return !Float.isNaN(x) && Math.copySign(1.0f, x) > 0.0f;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that instanceof ComplexF) {
            ComplexF other = (ComplexF) that;
            // bit for bit, as Arrays.equals does for a double[]: the branch cuts
            // read the sign of a zero, and a NaN component says nothing about
            // the other one
            return Float.floatToIntBits(re) == Float.floatToIntBits(other.re)
                    && Float.floatToIntBits(im) == Float.floatToIntBits(other.im);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // equals compares the components bit for bit, and the sign of a zero
        // sits in the top bit alone, so the mixing has to spread it before the
        // second component arrives
        long h = 0xCBF29CE484222325L;
        h = (h ^ (Float.floatToIntBits(re) & 0xFFFFFFFFL)) * 0x100000001B3L;
        h ^= h >>> 29;
        h = (h ^ (Float.floatToIntBits(im) & 0xFFFFFFFFL)) * 0x100000001B3L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    public static ComplexF NaN() {
        return NAN;
    }

    public static ComplexF Inf() {
        return INF;
    }

    public static ComplexF Zero() {
        return ZERO;
    }

    public static ComplexF One() {
        return ONE;
    }

    public static ComplexF I() {
        return I;
    }
}
