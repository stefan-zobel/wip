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
/*
 * Copyright © 1999 CERN - European Organization for Nuclear Research.
 * Permission to use, copy, modify, distribute and sell this software and
 * its documentation for any purpose is hereby granted without fee, provided
 * that the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation. CERN makes no representations about the suitability of this
 * software for any purpose. It is provided "as is" without expressed or
 * implied warranty.
 */
package math.cern;

/**
 * Polynomial functions.
 */
public final class Polynomial {

    private Polynomial() {
    }

    /**
     * Evaluates the given polynomial of degree <tt>N</tt> at <tt>x</tt>,
     * assuming coefficient of N is 1.0. Otherwise same as <tt>polevl()</tt>.
     * 
     * <pre>
     *                     2          N
     * y  =  C  + C x + C x  +...+ C x
     *        0    1     2          N
     * 
     * where C  = 1 and hence is omitted from the array.
     *        N
     * 
     * Coefficients are stored in reverse order:
     * 
     * coef[0] = C  , ..., coef[N-1] = C  .
     *            N-1                   0
     * 
     * Calling arguments are otherwise the same as polevl().
     * </pre>
     * 
     * In the interest of speed, there are no checks for out of bounds
     * arithmetic.
     * 
     * @param x
     *            argument to the polynomial
     * @param coef
     *            the coefficients of the polynomial
     * @param N
     *            the degree of the polynomial
     * @return the value of the polynomial for x
     */
    public static double p1evl(final double x, final double coef[], final int N) {

        double ans = x + coef[0];

        for (int i = 1; i < N; i++) {
            ans = ans * x + coef[i];
        }

        return ans;
    }

    /**
     * Evaluates the given polynomial of degree <tt>N</tt> at <tt>x</tt>.
     * 
     * <pre>
     *                     2          N
     * y  =  C  + C x + C x  +...+ C x
     *        0    1     2          N
     * 
     * Coefficients are stored in reverse order:
     * 
     * coef[0] = C  , ..., coef[N] = C  .
     *            N                   0
     * </pre>
     * 
     * In the interest of speed, there are no checks for out of bounds
     * arithmetic.
     * 
     * @param x
     *            argument to the polynomial
     * @param coef
     *            the coefficients of the polynomial
     * @param N
     *            the degree of the polynomial
     * @return the value of the polynomial for x
     */
    public static double polevl(final double x, final double coef[], final int N) {

        double ans = coef[0];

        for (int i = 1; i <= N; i++) {
            ans = ans * x + coef[i];
        }

        return ans;
    }

    /**
     * Evaluates the series of Chebyshev polynomials T<sub>i</sub> at argument
     * x/2. The series is given by
     * 
     * <pre>
     *        N-1
     *         - '
     *  y  =   &gt;   coef[i] T (x/2)
     *         -            i
     *        i=0
     * </pre>
     * 
     * Coefficients are stored in reverse order, i.e. the zero order term is
     * last in the array. Note: N is the number of coefficients, not the order.
     * <p>
     * If coefficients are for the interval a to b, x must have been transformed
     * to {@code x -> 2(2x - b - a)/(b-a)} before entering the routine. This maps x from
     * (a, b) to (-1, 1), over which the Chebyshev polynomials are defined.
     * <p>
     * If the coefficients are for the inverted interval, in which (a, b) is
     * mapped to (1/b, 1/a), the transformation required is {@code x -> 2(2ab/x - b -
     * a)/(b-a)}. If b is infinity, this becomes {@code x -> 4a/x - 1}.
     * <p>
     * SPEED:
     * <p>
     * Taking advantage of the recurrence properties of the Chebyshev
     * polynomials, the routine requires one more addition per loop than
     * evaluating a nested polynomial of the same degree.
     * 
     * @param x
     *            argument to the polynomial
     * @param coef
     *            the coefficients of the polynomial
     * @param N
     *            the number of coefficients
     * @return the value of the series for x
     */
    public static double chbevl(final double x, final double coef[], final int N) {

        int p = 0;
        double b0 = coef[p++];
        double b1 = 0.0;
        double b2;
        int i = N - 1;

        do {
            b2 = b1;
            b1 = b0;
            b0 = x * b1 - b2 + coef[p++];
        } while (--i > 0);

        return 0.5 * (b0 - b2);
    }

    /**
     * Evaluates a series of shifted Chebyshev polynomials {@code T_j^*} at
     * {@code x} over the basic interval {@code [0, 1]} using the method of
     * Clenshaw.
     * 
     * @param coef
     *            coefficients of the polynomials
     * 
     * @param N
     *            largest degree of polynomials
     * @param x
     *            the parameter of the {@code T_j^*} functions
     * @return the value of a series of Chebyshev polynomials {@code T_j^*}
     */
    public static double evalChebyStar(double coef[], int N, double x) {

        double b0 = 0.0;
        double b1 = 0.0;
        double b2 = 0.0;
        double xx = 2.0 * (2.0 * x - 1.0);

        for (int i = N; i >= 0; i--) {
            b2 = b1;
            b1 = b0;
            b0 = xx * b1 - b2 + coef[i];
        }

        return 0.5 * (b0 - b2);
    }
}
