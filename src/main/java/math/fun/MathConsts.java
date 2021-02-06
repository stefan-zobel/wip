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
package math.fun;

/**
 * Some numerical constants from the Cephes library.
 */
public final class MathConsts {

    /** The IEEE 754 machine epsilon from Cephes: {@code (2^-53)} */
    public static final double MACH_EPS_DBL = 1.11022302462515654042e-16;

    /** 7.09782712893383996732e2 */
    public static final double MAX_LOG = 7.09782712893383996732e2;

    /** -7.451332191019412076235e2 */
    public static final double MIN_LOG = -7.451332191019412076235e2;

    /** 171.624376956302725 */
    public static final double MAX_GAMMA = 171.624376956302725;

    /** The value of {@code sqrt(2)} */
    public static final double SQRT_TWO = 1.41421356237309504880e0;

    /** The value of {@code sqrt(2*PI)} */
    public static final double SQRT_TWO_PI = 2.50662827463100050242e0;

    /** The value of {@code sqrt(PI/2)} */
    public static final double SQRT_PI_HALF = 1.2533141373155001e0;

    /** The value of {@code sqrt(2)/2} */
    public static final double SQRT_TWO_HALF = 7.07106781186547524401e-1;

    /** The value of {@code Math.PI * Math.PI} (9.869604401089358) */
    public static final double PI_SQUARED = Math.PI * Math.PI;

    /** The value of {@code ln(PI)} */
    public static final double LN_PI = 1.14472988584940017414; /* ln(PI) */

    /** The value of {@code ln2} */
    public static final double LN_2 = 0.69314718055994530941; /* ln(2) */

    /** The value of {@code ln(10)} */
    public static final double LN_10 = 2.302585092994046; /* ln(10) */

    /** 4.503599627370496e15 */
    public static final double BIG = 4.503599627370496e15;

    /** 2.22044604925031308085e-16 */
    public static final double BIG_INV = 2.22044604925031308085e-16;

    /** 4.450147717014403e-308 (equals 2 x {@link Double#MIN_NORMAL}) */
    public static final double MIN_VAL = 2.0 * Double.MIN_NORMAL;

    /** Natural logarithm of {@link Double#MIN_NORMAL} (-708.3964185322641) */
    public static final double LN_MIN_NORMAL = -708.3964185322641;

    /**
     * 5.218048215738236e-15 (equals (45 x {@link #MACH_EPS_DBL}) +
     * {@link #BIG_INV})
     */
    public static final double MIN_TOL = (45.0 * MACH_EPS_DBL) + BIG_INV;

    /**
     * Largest int x such that 10^x is representable (approximately) as double
     */
    public static final int MAX_X_FOR_10_EXP_X_AS_DOUBLE = 308;

    private MathConsts() {
    }
}
