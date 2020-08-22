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
 * A complex number.
 */
public interface IComplex {

    double re();

    double im();

    IComplex add(IComplex that);

    IComplex sub(IComplex that);

    IComplex conj();

    IComplex neg();

    /**
     * Compute the argument of this complex number. The argument is the angle
     * phi between the positive real axis and the point representing this number
     * in the complex plane. The value returned is between -PI (not inclusive)
     * and PI (inclusive), with negative values returned for numbers with
     * negative imaginary parts. A.k.a {@code phase} or {@code angle}.
     * 
     * @return the angle of this complex number
     */
    double arg();

    /**
     * A.k.a {@code magnitude} or {@code modulus}.
     * 
     * @return the absolute value of this complex number
     */
    double abs();

    /**
     * Multiplicative inverse: {@code 1.0 / this}.
     * 
     * @return the multiplicative inverse of this complex number
     */
    IComplex inv();

    IComplex mul(IComplex that);

    IComplex div(IComplex that);

    /**
     * Multiplication with a real number.
     * 
     * @param alpha
     * @return the result of the multiplication
     */
    IComplex scale(double alpha);

    /**
     * Natural logarithm: {@code ln(this)}.
     * 
     * @return the natural log of this complex number
     */
    IComplex ln();

    /**
     * Exponential function: {@code e}<sup>{@code this}</sup>.
     * 
     * @return {@code e} to the power of this complex number
     */
    IComplex exp();

    /**
     * Power function of this complex base with a real exponent.
     * <p>
     * Computes {@code this}<sup>{@code exponent}</sup>
     * 
     * @param exponent
     *            real exponent
     * @return this complex number to the power of the real exponent
     */
    IComplex pow(double exponent);

    /**
     * Power function of this complex base with a complex exponent.
     * <p>
     * Computes {@code this}<sup>{@code exponent}</sup>
     * 
     * @param exponent
     *            complex exponent
     * @return this complex number to the power of the complex exponent
     */
    IComplex pow(IComplex exponent);

    boolean isMutable();

    boolean isImmutable();

    IComplex toImmutable();

    boolean isReal();

    boolean isNan();

    boolean isInfinite();

    String toString();

    static final IComplex NaN = new Complex(Double.NaN, Double.NaN);
    static final IComplex Inf = new Complex(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    static final IComplex Zero = new Complex(0.0, 0.0);
    static final IComplex One = new Complex(1.0, 0.0);
    static final IComplex I = new Complex(0.0, 1.0);
}
