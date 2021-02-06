/*
 * Copyright 2013 Stefan Zobel
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
package math.fast;

public final class FastMath {

    /*
     * Don't let anyone instantiate this class.
     */
    private FastMath() {
        throw new AssertionError();
    }

    /**
     * Returns the natural logarithm of the sum of the argument and 1. Note that
     * for small values {@code x}, the result of {@code log1p(x)} is much closer
     * to the true result of ln(1 + {@code x}) than the floating-point
     * evaluation of {@code log(1.0+x)}.
     * 
     * <p>
     * Special cases:
     * 
     * <ul>
     * 
     * <li>If the argument is NaN or less than -1, then the result is NaN.
     * 
     * <li>If the argument is positive infinity, then the result is positive
     * infinity.
     * 
     * <li>If the argument is negative one, then the result is negative
     * infinity.
     * 
     * <li>If the argument is zero, then the result is a zero with the same sign
     * as the argument.
     * 
     * </ul>
     * 
     * <p>
     * The computed result must be within 1 ulp of the exact result. Results
     * must be semi-monotonic.
     * 
     * @param x
     *            a value
     * @return the value ln({@code x}&nbsp;+&nbsp;1), the natural log of
     *         {@code x}&nbsp;+&nbsp;1
     * @since 1.5
     */
    public static double log1p(double x) {
        return JafamaFastMath.log1p(x);
    }
}
