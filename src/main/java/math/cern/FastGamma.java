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
 * Contains fast approximations for the &Gamma; (Gamma) family of functions.
 * <p>
 * <b>Implementation:</b> High performance implementation.
 * </p>
 * This is a port of <tt>gen_fun.cpp</tt> from the <A
 * HREF="http://www.cis.tu-graz.ac.at/stat/stadl/random.html">C-RAND /
 * WIN-RAND</A> library.
 * 
 * @author wolfgang.hoschek@cern.ch
 */
public final class FastGamma {

    private static final double c0 = 9.1893853320467274e-01;
    private static final double c1 = 8.3333333333333333e-02;
    private static final double c2 = -2.7777777777777777e-03;
    private static final double c3 = 7.9365079365079365e-04;
    private static final double c4 = -5.9523809523809524e-04;
    private static final double c5 = 8.4175084175084175e-04;
    private static final double c6 = -1.9175269175269175e-03;

    /**
     * Returns a quick approximation of the gamma function <tt>gamma(x)</tt>.
     * 
     * @param x the value
     * @return gamma(x)
     */
    public static double gamma(final double x) {
        return Math.exp(logGamma(x));
    }

    /**
     * Returns a quick approximation of <tt>log(gamma(x))</tt>.
     * 
     * @param x the value
     * @return log(gamma(x))
     */
    public static double logGamma(double x) {
        if (x <= 0.0 /* || x > 1.3e19 */) {
            return -999;
        }

        double z;
        for (z = 1.0; x < 11.0; x++) {
            z *= x;
        }

        final double r = 1.0 / (x * x);
        double g = c1 + r * (c2 + r * (c3 + r * (c4 + r * (c5 + r + c6))));
        g = (x - 0.5) * Math.log(x) - x + c0 + g / x;
        if (z == 1.0) {
            return g;
        }
        return g - Math.log(z);
    }

    private FastGamma() {
    }
}
