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
 * Mutable {@link IComplex} implementation.
 */
public final class MComplex extends AComplex implements IComplex {

    private double re;
    private double im;

    public double re() {
        return re;
    }

    public double im() {
        return im;
    }

    public MComplex(double re) {
        this(re, 0.0);
    }

    public MComplex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    public MComplex(IComplex that) {
        this.re = that.re();
        this.im = that.im();
    }

    public void setRe(double re) {
        this.re = re;
    }

    public void setIm(double im) {
        this.im = im;
    }

    public MComplex copy() {
        return new MComplex(re, im);
    }

    public boolean isMutable() {
        return true;
    }

    public IComplex add(IComplex that) {
        re += that.re();
        im += that.im();
        return this;
    }

    public IComplex sub(IComplex that) {
        re -= that.re();
        im -= that.im();
        return this;
    }

    public IComplex mul(IComplex that) {
        if (isInfinite() || that.isInfinite()) {
            re = Double.POSITIVE_INFINITY;
            im = Double.POSITIVE_INFINITY;
            return this;
        }
        double this_re = re;
        double that_re = that.re();
        re = this_re * that_re - im * that.im();
        im = im * that_re + this_re * that.im();
        return this;
    }

    public IComplex div(IComplex that) {
        double c = that.re();
        double d = that.im();
        if (c == 0.0 && d == 0.0) {
            re = Double.NaN;
            im = Double.NaN;
            return this;
        }
        if (that.isInfinite() && !this.isInfinite()) {
            re = 0.0;
            im = 0.0;
            return this;
        }
        // limit overflow/underflow
        if (Math.abs(c) < Math.abs(d)) {
            double q = c / d;
            double denom = c * q + d;
            double real = re;
            re = (real * q + im) / denom;
            im = (im * q - real) / denom;
        } else {
            double q = d / c;
            double denom = d * q + c;
            double real = re;
            re = (im * q + real) / denom;
            im = (im - real * q) / denom;
        }
        return this;
    }

    public IComplex inv() {
        if (re == 0.0 && im == 0.0) {
            re = Double.POSITIVE_INFINITY;
            im = Double.POSITIVE_INFINITY;
            return this;
        }
        if (isInfinite()) {
            re = 0.0;
            im = 0.0;
            return this;
        }
        double scale = re * re + im * im;
        re = re / scale;
        im = -im / scale;
        return this;
    }

    public IComplex ln() {
        double abs = abs();
        double phi = arg();
        re = Math.log(abs);
        im = phi;
        return this;
    }

    public IComplex exp() {
        ComplexFun.exp(this, true, true);
        return this;
    }

    public IComplex pow(double exponent) {
        return ln().scale(exponent).exp();
    }

    public IComplex pow(IComplex exponent) {
        return ln().mul(exponent).exp();
    }

    public IComplex scale(double alpha) {
        if (isInfinite() || Double.isInfinite(alpha)) {
            re = Double.POSITIVE_INFINITY;
            im = Double.POSITIVE_INFINITY;
            return this;
        }
        re = alpha * re;
        im = alpha * im;
        return this;
    }

    public IComplex conj() {
        im = -im;
        return this;
    }

    public IComplex neg() {
        re = -re;
        im = -im;
        return this;
    }
}
