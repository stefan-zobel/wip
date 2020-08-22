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
 * Immutable {@link IComplex} implementation.
 */
public final class Complex extends AComplex implements IComplex {

    private final double re;
    private final double im;

    public double re() {
        return re;
    }

    public double im() {
        return im;
    }

    public Complex(double re) {
        this(re, 0.0);
    }

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public boolean isMutable() {
        return false;
    }

    public IComplex add(IComplex that) {
        return new Complex(re + that.re(), im + that.im());
    }

    public IComplex sub(IComplex that) {
        return new Complex(re - that.re(), im - that.im());
    }

    public IComplex mul(IComplex that) {
        if (isInfinite() || that.isInfinite()) {
            return IComplex.Inf;
        }
        return new Complex(re * that.re() - im * that.im(), im * that.re() + re * that.im());
    }

    public IComplex div(IComplex that) {
        double c = that.re();
        double d = that.im();
        if (c == 0.0 && d == 0.0) {
            return IComplex.NaN;
        }
        if (that.isInfinite() && !this.isInfinite()) {
            return IComplex.Zero;
        }
        // limit overflow/underflow
        if (Math.abs(c) < Math.abs(d)) {
            double q = c / d;
            double denom = c * q + d;
            return new Complex((re * q + im) / denom, (im * q - re) / denom);
        } else {
            double q = d / c;
            double denom = d * q + c;
            return new Complex((im * q + re) / denom, (im - re * q) / denom);
        }
    }

    public IComplex inv() {
        if (re == 0.0 && im == 0.0) {
            return IComplex.Inf;
        }
        if (isInfinite()) {
            return IComplex.Zero;
        }
        double scale = re * re + im * im;
        return new Complex(re / scale, -im / scale);
    }

    public IComplex ln() {
        return ComplexFun.ln(this);
    }

    public IComplex exp() {
        return ComplexFun.exp(this);
    }

    public IComplex pow(double exponent) {
        return ComplexFun.pow(this, exponent, false);
    }

    public IComplex pow(IComplex exponent) {
        return ComplexFun.pow(this, exponent, false);
    }

    public IComplex scale(double alpha) {
        if (isInfinite() || Double.isInfinite(alpha)) {
            return IComplex.Inf;
        }
        return new Complex(alpha * re, alpha * im);
    }

    public IComplex conj() {
        return new Complex(re, -im);
    }

    public IComplex neg() {
        return new Complex(-re, -im);
    }
}
