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
 * Natural cubic spline interpolation from commons-math3
 */
public final class SplineInterpolator {

    private final double[] knots;
    private final double[][] polynomials;
    private final double min;
    private final double max;

    public SplineInterpolator(double points[], double values[]) {
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
        return horner(coeffs[0], coeffs[1], coeffs[2], coeffs[3], point - knots[i]);
    }

    private static double horner(double a, double b, double c, double d, double x) {
        return x * (x * (x * d + c) + b) + a;
    }

    private static double[][] polynomials(double points[], double values[]) {
        // Number of intervals. The number of data points is n + 1
        final int n = points.length - 1;

        // Differences between knot points
        double h[] = new double[n];
        for (int i = 0; i < n; i++) {
            h[i] = points[i + 1] - points[i];
        }

        double mu[] = new double[n];
        double z[] = new double[n + 1];
        mu[0] = 0.0;
        z[0] = 0.0;
        double g = 0.0;
        //@formatter:off
        for (int i = 1; i < n; i++) {
            g = 2.0 * (points[i + 1]  - points[i - 1]) - h[i - 1] * mu[i - 1];
            mu[i] = h[i] / g;
            z[i] = (3.0 * (values[i + 1] * h[i - 1] - values[i] * (points[i + 1] - points[i - 1]) + values[i - 1] * h[i]) /
                    (h[i - 1] * h[i]) - h[i - 1] * z[i - 1]) / g;
        }
        //@formatter:on

        // cubic spline coefficients -- b is linear, c quadratic, d is cubic
        // (original values are the constants)
        double b[] = new double[n];
        double c[] = new double[n + 1];
        double d[] = new double[n];

        z[n] = 0.0;
        c[n] = 0.0;

        //@formatter:off
        for (int j = n - 1; j >= 0; j--) {
            c[j] = z[j] - mu[j] * c[j + 1];
            b[j] = (values[j + 1] - values[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0;
            d[j] = (c[j + 1] - c[j]) / (3.0 * h[j]);
        }
        //@formatter:on

        double[][] polynomials = new double[n][];
        for (int i = 0; i < n; i++) {
            double coefficients[] = new double[4];
            coefficients[0] = values[i];
            coefficients[1] = b[i];
            coefficients[2] = c[i];
            coefficients[3] = d[i];
            polynomials[i] = coefficients;
        }

        return polynomials;
    }
}
