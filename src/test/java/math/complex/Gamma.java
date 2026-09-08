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
 * The gamma function and the logarithm of its analytic continuation, by
 * Stirling with an argument shift and the reflection formula. {@code lgamma}
 * is continued rather than cut, so its imaginary part is unbounded and
 * {@code exp} of it is {@code gamma}. The exponential turns the absolute error
 * of the logarithm into a relative one, so {@code gamma} loses accuracy as
 * {@code |lgamma|} grows.
 */
public final class Gamma {

    /** shift the argument until its modulus reaches this, then the series */
    private static final double R = 5.0;

    /** B_2n / (2n(2n-1)), which the test recomputes from the recurrence */
    private static final double[] B = { 0.0, 0.08333333333333333, -0.002777777777777778,
            7.936507936507937E-4, -5.952380952380953E-4, 8.417508417508417E-4,
            -0.0019175269175269176, 0.00641025641025641, -0.029550653594771242,
            0.17964437236883057, -1.3924322169059011, 13.402864044168393, -156.84828462600203 };

    private static final double LN_SQRT_2PI = 0.918938533204672741780329736406;
    private static final double LN_PI = 1.144729885849400174143427351353;

    // the n-th coefficient, so the test can recompute it from the recurrence
    static double bernoulli(int n) {
        return B[n];
    }

    // how many coefficients there are
    static int terms() {
        return B.length - 1;
    }

    private Gamma() {
        throw new AssertionError("no instances");
    }

    /**
     * The logarithm of gamma, continued analytically: the imaginary part is
     * unbounded and the exponential of the result is {@link #gamma(ComplexD)}.
     *
     * @param z the argument
     * @return ln(gamma(z)), or (+inf, NaN) at a pole
     */
    public static ComplexD lgamma(ComplexD z) {
        double x = z.re();
        double y = z.im();
        if (y == 0.0 && x <= 0.0 && x == Math.rint(x)) {
            // a pole: the modulus is unbounded, the angle has no value
            return new ComplexD(Double.POSITIVE_INFINITY, Double.NaN);
        }
        if (!z.isFinite()) {
            // gamma grows without bound along the positive axis and settles
            // on no direction anywhere else out there
            return (x > 0.0 && y == 0.0) ? new ComplexD(Double.POSITIVE_INFINITY, y)
                    : ComplexD.NaN();
        }
        if (x >= 0.5) {
            // on the axis the value is real, and the zero keeps its sign
            return (y == 0.0) ? new ComplexD(right(x, 0.0).re(), y) : right(x, y);
        }
        // ln(pi) - ln(sin(pi z)) - lgamma(1 - z). The sine is reduced first,
        // sin(pi z) = (-1)^m sin(pi (r + i y)) with r in [0, 1), which keeps
        // the logarithm away from its cut altogether; that factor then enters
        // as exp(i sign(y) pi m), which is the branch each half plane needs
        double m = Math.floor(x);
        ComplexD l = new ComplexD(Math.PI * (x - m), Math.PI * y).sin().ln();
        ComplexD w = right(1.0 - x, -y);
        double t = Math.PI * m * Math.copySign(1.0, y);
        return new ComplexD(LN_PI - l.re() - w.re(), t - l.im() - w.im());
    }

    /**
     * The gamma function.
     *
     * @param z the argument
     * @return gamma(z), or {@link ComplexD#Inf()} at a pole
     */
    public static ComplexD gamma(ComplexD z) {
        double x = z.re();
        if (z.im() == 0.0 && x <= 0.0 && x == Math.rint(x)) {
            // the poles, and this library has one infinity without a direction
            return ComplexD.Inf();
        }
        return lgamma(z).exp();
    }

    // ln(gamma(z)) for re >= 0.5, where every logarithm here is principal
    private static ComplexD right(double x, double y) {
        double sr = 0.0;
        double si = 0.0;
        double px = x;
        double py = y;
        // gamma(w+1) = w gamma(w), which holds with the principal log on this
        // side, so the shift costs one logarithm per step
        while (px * px + py * py < R * R) {
            sr -= 0.5 * Math.log(px * px + py * py);
            si -= Math.atan2(py, px);
            px += 1.0;
        }
        double lr = 0.5 * Math.log(px * px + py * py);
        double li = Math.atan2(py, px);
        double ar = px - 0.5;
        double tr = ar * lr - py * li - px + LN_SQRT_2PI;
        double ti = ar * li + py * lr - py;
        // the series in 1/w, stepped by 1/w^2
        double d = px * px + py * py;
        double ir = px / d;
        double ii = -py / d;
        double qr = ir * ir - ii * ii;
        double qi = 2.0 * ir * ii;
        double pr = ir;
        double pi = ii;
        for (int n = 1; n < B.length; ++n) {
            if (n > 1) {
                double nr = pr * qr - pi * qi;
                pi = pr * qi + pi * qr;
                pr = nr;
            }
            tr += B[n] * pr;
            ti += B[n] * pi;
        }
        return new ComplexD(tr + sr, ti + si);
    }
}
