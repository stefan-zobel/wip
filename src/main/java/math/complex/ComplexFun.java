/*
 * Copyright 2018 Stefan Zobel
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
 * Helper functions for complex algebra.
 */
public final class ComplexFun {

    /**
     * <code>
     *   exp(a + bi) = exp(a)*(cos(b) + i*sin(b))
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param z
     *            complex argument {@code = (a + bi)}
     * @return {@code exp(a + bi)}
     */
    public static IComplex exp(IComplex z) {
        return exp(z, false);
    }

    /**
     * <code>
     *   ln(a + bi) = ln(|a + bi|) + arg(a + bi)i
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param z
     *            complex argument
     * @return {@code ln(a + bi)}
     */
    public static IComplex ln(IComplex z) {
        return ln(z, false);
    }

    /**
     * <code>
     *   ln(a + bi) = ln(|a + bi|) + arg(a + bi)i
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param z
     *            complex argument
     * @param mutable
     *            creates a mutable return value if {@code true}
     * @return {@code ln(a + bi)}
     */
    public static IComplex ln(IComplex z, boolean mutable) {
        double abs = z.abs();
        double phi = z.arg();
        if (mutable) {
            return new MComplex(Math.log(abs), phi);
        } else {
            return new Complex(Math.log(abs), phi);
        }
    }

    /**
     * <code>
     *   base<sup>exponent</sup> = exp(exponent * ln(base))
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param base
     *            complex argument {@code base = (a + bi)}
     * @param exponent
     *            real exponent
     * @return {@code base}<sup>{@code exponent}</sup>
     */
    public static IComplex pow(IComplex base, double exponent) {
        return pow(base, exponent, false);
    }

    /**
     * <code>
     *   base<sup>exponent</sup> = exp(exponent * ln(base))
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param base
     *            complex argument {@code base = (a + bi)}
     * @param exponent
     *            real exponent
     * @param mutable
     *            creates a mutable return value if {@code true}
     * @return {@code base}<sup>{@code exponent}</sup>
     */
    public static IComplex pow(IComplex base, double exponent, boolean mutable) {
        MComplex log = (MComplex) ln(base, true);
        log.scale(exponent);
        IComplex exp = exp(log, true, true);
        if (mutable) {
            return exp;
        } else {
            return new Complex(exp.re(), exp.im());
        }
    }

    /**
     * <code>
     *   base<sup>exponent</sup> = exp(exponent * ln(base))
     * </code>
     * <p>
     * None of the arguments get mutated.
     * 
     * @param base
     *            complex argument {@code base = (a + bi)}
     * @param exponent
     *            complex argument {@code exponent = (c + di)}
     * @return {@code base}<sup>{@code exponent}</sup>
     */
    public static IComplex pow(IComplex base, IComplex exponent) {
        return pow(base, exponent, false);
    }

    /**
     * <code>
     *   base<sup>exponent</sup> = exp(exponent * ln(base))
     * </code>
     * <p>
     * None of the arguments get mutated.
     * 
     * @param base
     *            complex argument {@code base = (a + bi)}
     * @param exponent
     *            complex argument {@code exponent = (c + di)}
     * @param mutable
     *            creates a mutable return value if {@code true}
     * @return {@code base}<sup>{@code exponent}</sup>
     */
    public static IComplex pow(IComplex base, IComplex exponent, boolean mutable) {
        MComplex log = (MComplex) ln(base, true);
        log.mul(exponent);
        IComplex exp = exp(log, true, true);
        if (mutable) {
            return exp;
        } else {
            return new Complex(exp.re(), exp.im());
        }
    }

    /**
     * <code>
     *   exp(a + bi) = exp(a)*(cos(b) + i*sin(b))
     * </code>
     * <p>
     * The argument doesn't get mutated.
     * 
     * @param z
     *            complex argument {@code = (a + bi)}
     * @param mutable
     *            creates a mutable return value if {@code true}
     * @return {@code exp(a + bi)}
     */
    public static IComplex exp(IComplex z, boolean mutable) {
        return exp(z, mutable, false);
    }

    static IComplex exp(IComplex z, boolean mutable, boolean reuseArg) {
        double expRe = Math.exp(z.re());
        double im = z.im();
        double cos = Math.cos(im);
        double sin = Math.sin(im);
        if (reuseArg) {
            MComplex arg = (MComplex) z;
            arg.setRe(expRe * cos);
            arg.setIm(expRe * sin);
            return arg;
        }
        if (mutable) {
            return new MComplex(expRe * cos, expRe * sin);
        } else {
            return new Complex(expRe * cos, expRe * sin);
        }
    }

    public static IComplex fromPolar(double radius, double phi) {
        return fromPolar(radius, phi, false);
    }

    public static IComplex fromPolar(double radius, double phi, boolean mutable) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("radius must be positive : " + radius);
        }
        if (mutable) {
            return new MComplex(radius * Math.cos(phi), radius * Math.sin(phi));
        } else {
            return new Complex(radius * Math.cos(phi), radius * Math.sin(phi));
        }
    }

    private ComplexFun() {
        throw new AssertionError();
    }
}
