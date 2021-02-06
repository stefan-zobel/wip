/*
 * Copyright 2012-2013 Jeff Hain
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
/*
 * =============================================================================
 * Notice of fdlibm package this program is partially derived from:
 *
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * =============================================================================
 */
package math.fast;

/**
 * Class providing math treatments that:
 * - are meant to be faster than java.lang.Math class equivalents,
 * - are still somehow accurate and robust (handling of NaN and such),
 * - do not (or not directly) generate objects at run time (no "new").
 *
 * Use of look-up tables: around 1 MiB total, and initialized on class load.
 *
 * Depending on JVM, or JVM options, these treatments can actually be slower
 * than Math ones.
 * In particular, they can be slower if not optimized by the JIT, which you
 * can see with -Xint JVM option.
 * Another cause of slowness can be cache-misses on look-up tables.
 * Also, look-up tables initialization, done on class load, typically
 * takes multiple hundreds of milliseconds (and is about twice slower
 * in J6 than in J5, and in J7 than in J6, possibly due to intrinsifications
 * preventing optimizations such as use of hardware sqrt, and Math delegating
 * to StrictMath with JIT optimizations not yet up during class load).
 *
 * These treatments are not strictfp: if you want identical results
 * across various architectures, you must roll your own implementation
 * by adding strictfp and using StrictMath instead of Math.
 *
 * Methods with same signature than Math ones, are meant to return
 * "good" approximations on all range.
 *
 * --- words, words, words ---
 *
 * "0x42BE0000 percents of the folks out there
 * are completely clueless about floating-point."
 *
 * The difference between precision and accuracy:
 * "3.177777777777777 is a precise (16 digits)
 * but inaccurate (only correct up to the second digit)
 * approximation of PI=3.141592653589793(etc.)."
 */
final class JafamaFastMath {

    /*
     * For trigonometric functions, use of look-up tables and Taylor-Lagrange formula
     * with 4 derivatives (more take longer to compute and don't add much accuracy,
     * less require larger tables (which use more memory, take more time to initialize,
     * and are slower to access (at least on the machine they were developed on))).
     *
     * For angles reduction of cos/sin/tan functions:
     * - for small values, instead of reducing angles, and then computing the best index
     *   for look-up tables, we compute this index right away, and use it for reduction,
     * - for large values, treatments derived from fdlibm package are used, as done in
     *   java.lang.Math. They are faster but still "slow", so if you work with
     *   large numbers and need speed over accuracy for them, you might want to use
     *   normalizeXXXFast treatments before your function, or modify cos/sin/tan
     *   so that they call the fast normalization treatments instead of the accurate ones.
     *   NB: If an angle is huge (like PI*1e20), in double precision format its last digits
     *       are zeros, which most likely is not the case for the intended value, and doing
     *       an accurate reduction on a very inaccurate value is most likely pointless.
     *       But it gives some sort of coherence that could be needed in some cases.
     *
     * Multiplication on double appears to be about as fast (or not much slower) than call
     * to <double_array>[<index>], and regrouping some doubles in a private class, to use
     * index only once, does not seem to speed things up, so:
     * - for uniformly tabulated values, to retrieve the parameter corresponding to
     *   an index, we recompute it rather than using an array to store it,
     * - for cos/sin, we recompute derivatives divided by (multiplied by inverse of)
     *   factorial each time, rather than storing them in arrays.
     *
     * Lengths of look-up tables are usually of the form 2^n+1, for their values to be
     * of the form (<a_constant> * k/2^n, k in 0 .. 2^n), so that particular values
     * (PI/2, etc.) are "exactly" computed, as well as for other reasons.
     *
     * Most math treatments I could find on the web, including "fast" ones,
     * usually take care of special cases (NaN, etc.) at the beginning, and
     * then deal with the general case, which adds a useless overhead for the
     * general (and common) case. In this class, special cases are only dealt
     * with when needed, and if the general case does not already handle them.
     */

    //--------------------------------------------------------------------------
    // PUBLIC TREATMENTS
    //--------------------------------------------------------------------------

    /*
     * logarithms
     */

    /**
     * Much more accurate than log(1+value),
     * for arguments (and results) close to zero.
     *
     * @param value A double value.
     * @return Logarithm (base e) of (1+value).
     */
    static double log1p(double value) {
        if (value > -1.0) {
            if (value == Double.POSITIVE_INFINITY) {
                return Double.POSITIVE_INFINITY;
            }

            // ln'(x) = 1/x
            // so
            // log(x+epsilon) ~= log(x) + epsilon/x
            //
            // Let u be 1+value rounded:
            // 1+value = u+epsilon
            //
            // log(1+value)
            // = log(u+epsilon)
            // ~= log(u) + epsilon/value
            // We compute log(u) as done in log(double), and then add the corrective term.

            double valuePlusOne = 1.0 + value;
            if (valuePlusOne == 1.0) {
                return value;
            } else if (Math.abs(value) < 0.15) {
                double z = value/(value + 2.0);
                double z2 = z*z;
                return z*(2+z2*((2.0/3)+z2*((2.0/5)+z2*((2.0/7)+z2*((2.0/9)+z2*((2.0/11)))))));
            }

            return Math.log1p(value);
        } else if (value == -1.0) {
            return Double.NEGATIVE_INFINITY;
        } else { // value < -1.0, or value is NaN
            return Double.NaN;
        }
    }

    //--------------------------------------------------------------------------
    //  PRIVATE TREATMENTS
    //--------------------------------------------------------------------------

    /**
     * JafamaFastMath is non-instantiable.
     */
    private JafamaFastMath() {
    }
}
