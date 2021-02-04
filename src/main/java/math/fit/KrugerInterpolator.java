/*
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
package math.fit;

import java.util.Arrays;

/**
 * Kruger's constrained cubic spline interpolation
 * (http://www.korf.co.uk/spline.pdf).
 * <p>
 * Written by Anton Danshin,
 * https://jetcracker.wordpress.com/2014/12/26/constrained-cubic-spline-java/
 */
public final class KrugerInterpolator {

    private final double[] knots;
    private final double[][] polynomials;
    private final double min;
    private final double max;

    public KrugerInterpolator(double[] points, double values[]) {
        knots = points;
        min = knots[0];
        max = knots[knots.length - 1];
        polynomials = polynomials(points, values);
    }

    public double value(double point) {
        if (point < min || point > max) {
            throw new IllegalArgumentException("point out of range [" + min + ", " + max + "]");
        }
        int i = Arrays.binarySearch(knots, point);
        if (i < 0) {
            i = -i - 2;
        }
        // This will handle the case where point is the last knot value
        // There are only n-1 polynomials, so if point is the last knot
        // then we use the last polynomial to calculate the value
        if (i >= polynomials.length) {
            i--;
        }
        double[] coeffs = polynomials[i];
        return horner(coeffs[0], coeffs[1], coeffs[2], coeffs[3], point);
    }

    private static double horner(double a, double b, double c, double d, double x) {
        return x * (x * (x * d + c) + b) + a;
    }

    private static double[][] polynomials(double points[], double values[]) {
        // Number of intervals. The number of data points is n + 1
        final int n = points.length - 1;
        // Differences between knot points
        double dx[] = new double[n];
        double dy[] = new double[n];
        for (int i = 0; i < n; i++) {
            dx[i] = points[i + 1] - points[i];
            dy[i] = values[i + 1] - values[i];
        }

        double f1[] = new double[n + 1]; // F'(x[i])
        for (int i = 1; i < n; i++) {
            double slope = dy[i - 1] * dy[i];
            if (slope > 0.0) {
                // doesn't change sign
                f1[i] = 2.0 / (dx[i] / dy[i] + dx[i - 1] / dy[i - 1]);
            } else if (slope <= 0.0) {
                // changes sign
                f1[i] = 0.0;
            }
        }
        f1[0] = 3.0 * dy[0] / (2.0 * dx[0]) - f1[1] / 2.0;
        f1[n] = 3.0 * dy[n - 1] / (2.0 * dx[n - 1]) - f1[n - 1] / 2.0;

        // cubic spline coefficients -- a contains constants, b is linear, c
        // quadratic, d is cubic
        double a[] = new double[n + 1];
        double b[] = new double[n + 1];
        double c[] = new double[n + 1];
        double d[] = new double[n + 1];

        //@formatter:off
        for (int i = 1; i <= n; i++) {
            double f2a = -2.0 * (f1[i] + 2.0 * f1[i-1]) / dx[i-1] + 6.0 * dy[i-1] / (dx[i-1] * dx[i-1]);
            double f2b = 2.0 * (2.0 * f1[i] + f1[i-1]) / dx[i-1] - 6.0 * dy[i-1] / (dx[i-1] * dx[i-1]);
            d[i] = (f2b - f2a) / (6.0 * dx[i-1]);
            c[i] = (points[i] * f2a - points[i-1] * f2b) / (2.0 * dx[i-1]);
            b[i] = (dy[i-1] -
                    c[i] * (points[i] * points[i] - points[i-1] * points[i-1]) -
                    d[i] * (points[i] * points[i] * points[i] - points[i-1] * points[i-1] * points[i-1])
            ) / dx[i-1];
            a[i] = values[i-1] - b[i] * points[i-1] - c[i] * points[i-1] * points[i-1] - d[i] * points[i-1] * points[i-1] * points[i-1];
        }
        //@formatter:on

        double[][] polynomials = new double[n][];
        for (int i = 1; i <= n; i++) {
            double coefficients[] = new double[4];
            coefficients[0] = a[i];
            coefficients[1] = b[i];
            coefficients[2] = c[i];
            coefficients[3] = d[i];
            polynomials[i - 1] = coefficients;
        }

        return polynomials;
    }
}
