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
 * An immutable double-precision complex number.
 * <p>
 * Infinity keeps its direction (C99 Annex G): only a result that has none
 * collapses to {@link #Inf()} - the inverse of zero, a quotient by zero, a
 * divergent {@link #pow(double)}. {@code 0 * inf} and {@code 0 / 0} are
 * {@link #NaN()}. Sum, difference, negation, conjugation and {@link #ln()} are
 * componentwise, while {@link #exp()} keeps an exact zero, so a real argument
 * stays real even where the modulus is not a number. A NaN spreads
 * componentwise too; only against an infinite operand does a NaN component
 * count as zero, so that the direction survives. {@code equals} sees one value
 * in every NaN but tells {@code +0.0} from {@code -0.0}, because the branch
 * cuts do; {@code hashCode} follows.
 */
public final class ComplexD {

    private static final ComplexD NAN = new ComplexD(Double.NaN, Double.NaN);
    private static final ComplexD INF = new ComplexD(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    private static final ComplexD ZERO = new ComplexD(0.0, 0.0);
    private static final ComplexD ONE = new ComplexD(1.0, 0.0);
    private static final ComplexD I = new ComplexD(0.0, 1.0);

    private final double re;
    private final double im;

    public ComplexD(double re) {
        this(re, 0.0);
    }

    public ComplexD(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public double re() {
        return re;
    }

    public double im() {
        return im;
    }

    public static ComplexD fromPolar(double radius, double phi) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be positive : " + radius);
        }
        double c = Math.cos(phi);
        double s = Math.sin(phi);
        // an exact zero stays zero even for an infinite radius
        return new ComplexD(c == 0.0 ? c : radius * c, s == 0.0 ? s : radius * s);
    }

    public static double abs(double re, double im) {
        // sqrt(a^2 + b^2) without under/overflow
        if (im == 0.0) {
            return Math.abs(re);
        } else if (Math.abs(re) > Math.abs(im)) {
            double abs = im / re;
            return Math.abs(re) * Math.sqrt(1.0 + abs * abs);
        } else {
            double abs = re / im;
            return Math.abs(im) * Math.sqrt(1.0 + abs * abs);
        }
    }

    /**
     * This number itself, since it cannot change.
     *
     * @return {@code this}
     */
    public ComplexD copy() {
        return this;
    }

    public ComplexD add(ComplexD that) {
        return new ComplexD(re + that.re, im + that.im);
    }

    public ComplexD sub(ComplexD that) {
        return new ComplexD(re - that.re, im - that.im);
    }

    public ComplexD mul(ComplexD that) {
        double a = re;
        double b = im;
        double c = that.re;
        double d = that.im;
        if (isInfinite() || that.isInfinite()) {
            // C99 Annex G: an infinite operand still fixes the direction
            if (Double.isInfinite(a) || Double.isInfinite(b)) {
                a = Math.copySign(Double.isInfinite(a) ? 1.0 : 0.0, a);
                b = Math.copySign(Double.isInfinite(b) ? 1.0 : 0.0, b);
            }
            if (Double.isInfinite(c) || Double.isInfinite(d)) {
                c = Math.copySign(Double.isInfinite(c) ? 1.0 : 0.0, c);
                d = Math.copySign(Double.isInfinite(d) ? 1.0 : 0.0, d);
            }
            a = zeroIfNan(a);
            b = zeroIfNan(b);
            c = zeroIfNan(c);
            d = zeroIfNan(d);
            if ((a == 0.0 && b == 0.0) || (c == 0.0 && d == 0.0)) {
                // zero times infinity has no direction and no modulus
                return NAN;
            }
            return new ComplexD(unbounded(a * c - b * d), unbounded(a * d + b * c));
        }
        return new ComplexD(a * c - b * d, a * d + b * c);
    }

    private static double zeroIfNan(double x) {
        return Double.isNaN(x) ? Math.copySign(0.0, x) : x;
    }

    // C99 scales by infinity here; an exact zero keeps its sign instead
    private static double unbounded(double x) {
        if (x == 0.0 || Double.isNaN(x)) {
            return x;
        }
        return (x > 0.0) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
    }

    public ComplexD div(ComplexD that) {
        double c = that.re;
        double d = that.im;
        if (c == 0.0 && d == 0.0) {
            // zero over zero has no value, anything else over zero is inv(0)
            if (re == 0.0 && im == 0.0) {
                return NAN;
            }
            return INF;
        }
        boolean thisInfinite = isInfinite();
        if (that.isInfinite() && !thisInfinite) {
            return ZERO;
        }
        if (thisInfinite && Double.isFinite(c) && Double.isFinite(d)) {
            // C99 Annex G: an infinite numerator still fixes the direction
            double a = Math.copySign(Double.isInfinite(re) ? 1.0 : 0.0, re);
            double b = Math.copySign(Double.isInfinite(im) ? 1.0 : 0.0, im);
            return new ComplexD(unbounded(a * c + b * d), unbounded(b * c - a * d));
        }
        // limit overflow/underflow
        if (Math.abs(c) < Math.abs(d)) {
            double q = c / d;
            double denom = c * q + d;
            double real = re;
            return new ComplexD((real * q + im) / denom, (im * q - real) / denom);
        }
        double q = d / c;
        double denom = d * q + c;
        double real = re;
        return new ComplexD((im * q + real) / denom, (im - real * q) / denom);
    }

    public ComplexD inv() {
        if (re == 0.0 && im == 0.0) {
            return INF;
        }
        if (isInfinite()) {
            return ZERO;
        }
        // the scaling from div(), with a numerator of (1, 0)
        double c = re;
        double d = im;
        if (Math.abs(c) < Math.abs(d)) {
            double q = c / d;
            double denom = c * q + d;
            return new ComplexD(q / denom, -1.0 / denom);
        }
        double q = d / c;
        double denom = d * q + c;
        return new ComplexD(1.0 / denom, -q / denom);
    }

    public ComplexD ln() {
        double abs = abs();
        double phi = arg();
        return new ComplexD(Math.log(abs), phi);
    }

    public ComplexD exp() {
        double c = Math.cos(im);
        double s = Math.sin(im);
        double h = Math.exp(re);
        if (Double.isInfinite(h)) {
            // e^re overflows although the product with cos or sin need not
            h = Math.exp(re / 2.0);
            return new ComplexD((c == 0.0) ? c : h * c * h, (s == 0.0) ? s : h * s * h);
        }
        return new ComplexD((c == 0.0) ? c : h * c, (s == 0.0) ? s : h * s);
    }

    /**
     * Principal square root: the root whose real part is not negative.
     *
     * @return the principal square root of this complex number
     */
    public ComplexD sqrt() {
        if (Double.isInfinite(im)) {
            // C99: an infinite imaginary part decides, whatever the real part is
            return new ComplexD(Double.POSITIVE_INFINITY, Math.copySign(Double.POSITIVE_INFINITY, im));
        }
        if (re == 0.0 && im == 0.0) {
            return new ComplexD(0.0, im);
        }
        double a = re;
        double b = im;
        double out = 1.0;
        // an exact power of four in and a power of two out, so that nothing in
        // between moves: the sum below overflows above a modulus of 9e307 and
        // goes subnormal below 2e-308
        if (Math.abs(a) > 0x1p1000 || Math.abs(b) > 0x1p1000) {
            a *= 0.25;
            b *= 0.25;
            out = 2.0;
        } else if (Math.abs(a) < 0x1p-500 && Math.abs(b) < 0x1p-500) {
            a *= 0x1p100;
            b *= 0x1p100;
            out = 0x1p-50;
        }
        // Kahan: t is built from |re| so that nothing cancels
        double t = Math.sqrt((Math.abs(a) + modulus(a, b)) / 2.0);
        if (a >= 0.0) {
            return new ComplexD(t * out, (b / (2.0 * t)) * out);
        }
        return new ComplexD((Math.abs(b) / (2.0 * t)) * out, Math.copySign(t, b) * out);
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

    private static ComplexD sinhOf(double x, double y) {
        if (Double.isInfinite(x) && !Double.isFinite(y)) {
            // the modulus survives, the direction does not
            return new ComplexD(Double.POSITIVE_INFINITY, Double.NaN);
        }
        return new ComplexD(product(Math.sinh(x), Math.cos(y)), product(Math.cosh(x), Math.sin(y)));
    }

    private static ComplexD coshOf(double x, double y) {
        if (Double.isInfinite(x) && !Double.isFinite(y)) {
            return new ComplexD(Double.POSITIVE_INFINITY, Double.NaN);
        }
        return new ComplexD(product(Math.cosh(x), Math.cos(y)), product(Math.sinh(x), Math.sin(y)));
    }

    /**
     * Hyperbolic sine.
     *
     * @return the hyperbolic sine of this complex number
     */
    public ComplexD sinh() {
        return sinhOf(re, im);
    }

    /**
     * Hyperbolic cosine.
     *
     * @return the hyperbolic cosine of this complex number
     */
    public ComplexD cosh() {
        return coshOf(re, im);
    }

    /**
     * Sine.
     *
     * @return the sine of this complex number
     */
    public ComplexD sin() {
        // sin(z) = -i * sinh(i * z)
        ComplexD s = sinhOf(-im, re);
        return new ComplexD(s.im, -s.re);
    }

    /**
     * Cosine.
     *
     * @return the cosine of this complex number
     */
    public ComplexD cos() {
        // cos(z) = cosh(i * z)
        return coshOf(-im, re);
    }

    private static ComplexD tanhOf(double x, double y) {
        if (Double.isInfinite(x)) {
            // the real part saturates, the imaginary part dies away
            return new ComplexD(Math.copySign(1.0, x),
                    Math.copySign(0.0, Double.isFinite(y) ? Math.sin(2.0 * y) : y));
        }
        if (Double.isNaN(x)) {
            return new ComplexD(Double.NaN, y == 0.0 ? y : Double.NaN);
        }
        if (!Double.isFinite(y)) {
            return new ComplexD(Double.NaN, Double.NaN);
        }
        double t = Math.tan(y);
        if (t == 0.0) {
            // on the real axis this is Math.tanh itself
            return new ComplexD(Math.tanh(x), t);
        }
        // sinh and cosh in the denominator would overflow near |x| = 355, so
        // the quotient is divided through by cos(y) first
        double s = Math.sinh(x);
        double b = 1.0 + t * t;
        double d = 1.0 + b * s * s;
        if (!Double.isFinite(d)) {
            // beyond the range only the limit is left
            return new ComplexD(Math.copySign(1.0, x), Math.copySign(0.0, t));
        }
        return new ComplexD(b * s * Math.cosh(x) / d, t / d);
    }

    /**
     * Hyperbolic tangent.
     *
     * @return the hyperbolic tangent of this complex number
     */
    public ComplexD tanh() {
        return tanhOf(re, im);
    }

    /**
     * Tangent.
     *
     * @return the tangent of this complex number
     */
    public ComplexD tan() {
        // tan(z) = -i * tanh(i * z)
        ComplexD u = tanhOf(-im, re);
        return new ComplexD(u.im, -u.re);
    }

    /**
     * Principal n-th root, the one with the smallest argument.
     *
     * @param n the degree of the root, positive
     * @return the principal n-th root of this complex number
     */
    public ComplexD nthRoot(int n) {
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
        return fromPolar(Math.pow(abs(), 1.0 / n), arg() / n);
    }

    /**
     * All n roots of degree n, the principal one first. The array is fresh on
     * every call and belongs to the caller.
     *
     * @param n the degree of the roots, positive
     * @return the n roots of this complex number
     */
    public ComplexD[] nthRoots(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive : " + n);
        }
        if (n == 2) {
            // the rotation by cis(pi) is an exact negation
            ComplexD w = sqrt();
            return new ComplexD[] { w, w.neg() };
        }
        ComplexD[] roots = new ComplexD[n];
        // taking the first one from nthRoot keeps the two methods in step
        roots[0] = nthRoot(n);
        double rho = Math.pow(abs(), 1.0 / n);
        double phi = arg() / n;
        for (int k = 1; k < n; ++k) {
            roots[k] = fromPolar(rho, phi + 2.0 * Math.PI * k / n);
        }
        return roots;
    }

    private static ComplexD atanhOf(double x, double y) {
        if (Double.isInfinite(x) || Double.isInfinite(y)) {
            // the modulus dies away, the angle saturates
            return new ComplexD(Math.copySign(0.0, x),
                    Double.isNaN(y) ? Double.NaN : Math.copySign(Math.PI / 2.0, y));
        }
        if (Double.isNaN(x)) {
            return new ComplexD(Double.NaN, Double.NaN);
        }
        if (Double.isNaN(y)) {
            return new ComplexD(x == 0.0 ? x : Double.NaN, Double.NaN);
        }
        if (x < 0.0) {
            // atanh is odd, and 1 + 4x/d is (1+x)^2/(1-x)^2, which
            // cancels for a negative x
            ComplexD w = atanhOf(-x, -y);
            return new ComplexD(-w.re, -w.im);
        }
        double d = (1.0 - x) * (1.0 - x) + y * y;
        if (!Double.isFinite(d)) {
            // far out atanh(z) is 1/z + i*pi/2, and 1/z through the scaling inv uses
            double re;
            if (Math.abs(x) >= Math.abs(y)) {
                double q = y / x;
                re = 1.0 / (x + y * q);
            } else {
                double q = x / y;
                re = q / (x * q + y);
            }
            return new ComplexD(re, Math.copySign(Math.PI / 2.0, y));
        }
        // log1p, because 1 + z rounds back to 1 for a small z
        return new ComplexD(0.25 * Math.log1p(4.0 * x / d),
                0.5 * Math.atan2(2.0 * y, (1.0 - x) * (1.0 + x) - y * y));
    }

    /**
     * Inverse hyperbolic tangent.
     *
     * @return the inverse hyperbolic tangent of this complex number
     */
    public ComplexD atanh() {
        return atanhOf(re, im);
    }

    /**
     * Inverse tangent, with a real part between -PI/2 and PI/2.
     *
     * @return the inverse tangent of this complex number
     */
    public ComplexD atan() {
        // atan(z) = -i * atanh(i * z)
        ComplexD u = atanhOf(-im, re);
        return new ComplexD(u.im, -u.re);
    }

    /** ln(2), which the inverse hyperbolic sine needs far from the origin */
    private static final double LN2 = 0.6931471805599453094172321;

    // the inverse hyperbolic sine of a real number, which Math does not have
    private static double realAsinh(double t) {
        double a = Math.abs(t);
        // beyond this a * a would overflow, and asinh(a) is ln(2a) there anyway
        double r = (a > 1.0e150) ? Math.log(a) + LN2
                : Math.log1p(a + a * a / (1.0 + Math.sqrt(1.0 + a * a)));
        return Math.copySign(r, t);
    }

    private static ComplexD asinhOf(double x, double y) {
        if (Double.isInfinite(y)) {
            // the modulus grows without bound, the angle saturates
            double angle = Double.isNaN(x) ? Double.NaN
                    : Math.copySign(Double.isInfinite(x) ? Math.PI / 4.0 : Math.PI / 2.0, y);
            return new ComplexD(Math.copySign(Double.POSITIVE_INFINITY, x), angle);
        }
        if (Double.isInfinite(x)) {
            return new ComplexD(x, Double.isNaN(y) ? Double.NaN : Math.copySign(0.0, y));
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            // asinh is real on the real axis, so an exact zero survives there
            return new ComplexD(Double.NaN, (Double.isNaN(x) && y == 0.0) ? y : Double.NaN);
        }
        if (x < 0.0) {
            // asinh is odd; the far field below needs an angle in [-PI/2, PI/2]
            ComplexD w = asinhOf(-x, -y);
            return new ComplexD(-w.re, -w.im);
        }
        if (x > 1.0e307 || Math.abs(y) > 1.0e307) {
            // the two roots have modulus sqrt(|z|), so their product would overflow
            return new ComplexD(LN2 + Math.log(new ComplexD(x, y).abs()), Math.atan2(y, x));
        }
        // 1 + z^2 is (1 + i*z)(1 - i*z); the roots taken apart, so nothing cancels
        ComplexD p = new ComplexD(1.0 - y, x).sqrt();
        ComplexD q = new ComplexD(1.0 + y, -x).sqrt();
        return new ComplexD(realAsinh(q.re * p.im - q.im * p.re),
                Math.atan2(y, p.re * q.re - p.im * q.im));
    }

    /**
     * Inverse hyperbolic sine.
     *
     * @return the inverse hyperbolic sine of this complex number
     */
    public ComplexD asinh() {
        return asinhOf(re, im);
    }

    /**
     * Inverse sine, with a real part between -PI/2 and PI/2.
     *
     * @return the inverse sine of this complex number
     */
    public ComplexD asin() {
        // asin(z) = -i * asinh(i * z)
        ComplexD u = asinhOf(-im, re);
        return new ComplexD(u.im, -u.re);
    }

    private static ComplexD acosOf(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            if (Double.isInfinite(y)) {
                return new ComplexD(Double.NaN, -Math.copySign(Double.POSITIVE_INFINITY, y));
            }
            if (Double.isInfinite(x)) {
                return new ComplexD(Double.NaN, Double.NEGATIVE_INFINITY);
            }
            // acos carries the imaginary axis onto itself, so PI/2 survives there
            return new ComplexD(x == 0.0 ? Math.PI / 2.0 : Double.NaN, Double.NaN);
        }
        if (Double.isInfinite(x) || Double.isInfinite(y)) {
            // the angle saturates, the modulus grows without bound
            double re;
            if (Double.isInfinite(x)) {
                re = Double.isInfinite(y) ? (x > 0.0 ? Math.PI / 4.0 : 3.0 * Math.PI / 4.0)
                        : (x > 0.0 ? 0.0 : Math.PI);
            } else {
                re = Math.PI / 2.0;
            }
            return new ComplexD(re, -Math.copySign(Double.POSITIVE_INFINITY, y));
        }
        if (Math.abs(x) > 1.0e307 || Math.abs(y) > 1.0e307) {
            // sqrt itself gives out above a modulus of 9e307, and the product
            // of the two roots would overflow anyway
            return new ComplexD(Math.atan2(Math.abs(y), x),
                    -Math.copySign(LN2 + Math.log(new ComplexD(x, y).abs()), y));
        }
        // 1 - z^2 is (1 - z)(1 + z); the roots taken apart, so nothing cancels
        ComplexD p = new ComplexD(1.0 - x, -y).sqrt();
        ComplexD q = new ComplexD(1.0 + x, y).sqrt();
        return new ComplexD(2.0 * Math.atan2(p.re, q.re), realAsinh(q.re * p.im - q.im * p.re));
    }

    private static ComplexD acoshOf(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            if (Double.isInfinite(x) || Double.isInfinite(y)) {
                return new ComplexD(Double.POSITIVE_INFINITY, Double.NaN);
            }
            return new ComplexD(Double.NaN, Double.NaN);
        }
        // a quarter turn of acos, to the side that keeps the real part positive
        ComplexD w = acosOf(x, y);
        return new ComplexD(Math.abs(w.im), Math.copySign(w.re, -w.im));
    }

    /**
     * Inverse cosine, with a real part between 0 and PI.
     *
     * @return the inverse cosine of this complex number
     */
    public ComplexD acos() {
        return acosOf(re, im);
    }

    /**
     * Inverse hyperbolic cosine, with a real part that is not negative.
     *
     * @return the inverse hyperbolic cosine of this complex number
     */
    public ComplexD acosh() {
        return acoshOf(re, im);
    }

    public ComplexD pow(double exponent) {
        if (isDegenerate()) {
            return degeneratePow(exponent);
        }
        if (Double.isInfinite(exponent)) {
            return infinitePow(exponent);
        }
        return ln().scale(exponent).exp();
    }

    public ComplexD pow(ComplexD exponent) {
        if (isDegenerate()) {
            return degeneratePow(exponent);
        }
        if (exponent.isInfinite()) {
            if (exponent.im == 0.0) {
                return infinitePow(exponent.re);
            }
            return NAN;
        }
        return ln().mul(exponent).exp();
    }

    // the values of Math.pow, with the modulus in place of |x|
    private ComplexD infinitePow(double exponent) {
        double r = abs();
        if (Double.isNaN(r) || r == 1.0) {
            return NAN;
        } else if ((r > 1.0) == (exponent > 0.0)) {
            return INF;
        } else {
            return ZERO;
        }
    }

    // a base that ln() cannot carry
    private boolean isDegenerate() {
        return (re == 0.0 && im == 0.0) || isInfinite();
    }

    // the values of Math.pow, mirrored for an infinite base
    private ComplexD degeneratePow(double exponent) {
        boolean zeroBase = (re == 0.0 && im == 0.0);
        if (isNan() || Double.isNaN(exponent)) {
            return NAN;
        } else if (exponent == 0.0) {
            return ONE;
        } else if (zeroBase == (exponent > 0.0)) {
            return ZERO;
        } else {
            return INF;
        }
    }

    private ComplexD degeneratePow(ComplexD exponent) {
        double a = exponent.re;
        double b = exponent.im;
        if (a == 0.0 && b == 0.0) {
            return ONE;
        }
        if (a == 0.0 || Double.isNaN(b) || Double.isInfinite(b)) {
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
    public ComplexD scale(double alpha) {
        double a = re;
        double b = im;
        double c = alpha;
        if (isInfinite() || Double.isInfinite(alpha)) {
            if (Double.isInfinite(a) || Double.isInfinite(b)) {
                a = Math.copySign(Double.isInfinite(a) ? 1.0 : 0.0, a);
                b = Math.copySign(Double.isInfinite(b) ? 1.0 : 0.0, b);
            }
            if (Double.isInfinite(c)) {
                c = Math.copySign(1.0, c);
            }
            a = zeroIfNan(a);
            b = zeroIfNan(b);
            c = zeroIfNan(c);
            if ((a == 0.0 && b == 0.0) || c == 0.0) {
                return NAN;
            }
            return new ComplexD(unbounded(a * c), unbounded(b * c));
        }
        return new ComplexD(alpha * a, alpha * b);
    }

    public ComplexD conj() {
        return new ComplexD(re, -im);
    }

    public ComplexD neg() {
        return new ComplexD(-re, -im);
    }

    public boolean isReal() {
        return im == 0.0 && !Double.isNaN(re);
    }

    /**
     * Compute the argument of this complex number, between -PI (not inclusive)
     * and PI (inclusive). Also known as {@code phase} or {@code angle}.
     *
     * @return the angle of this complex number
     */
    public double arg() {
        return Math.atan2(im, re);
    }

    /**
     * Also known as {@code magnitude} or {@code modulus}.
     *
     * @return the absolute value of this complex number
     */
    public double abs() {
        return modulus(re, im);
    }

    // sqrt(a^2 + b^2) without under/overflow, for a pair that need not be this one
    private static double modulus(double re, double im) {
        if (Double.isInfinite(re) || Double.isInfinite(im)) {
            return Double.POSITIVE_INFINITY;
        }
        if (im == 0.0) {
            return Math.abs(re);
        } else if (Math.abs(re) > Math.abs(im)) {
            double abs = im / re;
            return Math.abs(re) * Math.sqrt(1.0 + abs * abs);
        } else {
            double abs = re / im;
            return Math.abs(im) * Math.sqrt(1.0 + abs * abs);
        }
    }

    public boolean isNan() {
        return Double.isNaN(re) || Double.isNaN(im);
    }

    public boolean isInfinite() {
        return Double.isInfinite(re) || Double.isInfinite(im);
    }

    @Override
    public String toString() {
        return toString("%.10E");
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
        double re_ = re;
        double im_ = im;
        // fix negative zero
        if (re_ == 0.0) {
            re_ = 0.0;
        }
        if (im_ == 0.0) {
            im_ = 0.0;
        }
        StringBuilder buf = new StringBuilder(40);
        if (re_ >= 0.0) {
            buf.append("+");
        }
        buf.append(String.format(format, re_)).append("  ");
        if (im_ >= 0.0) {
            buf.append("+");
        }
        buf.append(String.format(format, im_)).append("i");
        return buf.toString();
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that instanceof ComplexD) {
            ComplexD other = (ComplexD) that;
            if (other.isNan()) {
                return this.isNan();
            }
            // the two zeros stay apart: every function here reads the sign of a
            // zero off the branch cut
            return Double.doubleToLongBits(re) == Double.doubleToLongBits(other.re)
                    && Double.doubleToLongBits(im) == Double.doubleToLongBits(other.im);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // equals() sees one value in every NaN but tells the two zeros
        // apart, and the sign of a zero sits in the top bit alone, so the
        // mixing has to spread it before the second component arrives
        boolean nan = isNan();
        long h = 0xCBF29CE484222325L;
        h = (h ^ Double.doubleToLongBits(nan ? Double.NaN : re)) * 0x100000001B3L;
        h ^= h >>> 29;
        h = (h ^ Double.doubleToLongBits(nan ? Double.NaN : im)) * 0x100000001B3L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    public static ComplexD NaN() {
        return NAN;
    }

    public static ComplexD Inf() {
        return INF;
    }

    public static ComplexD Zero() {
        return ZERO;
    }

    public static ComplexD One() {
        return ONE;
    }

    public static ComplexD I() {
        return I;
    }
}
