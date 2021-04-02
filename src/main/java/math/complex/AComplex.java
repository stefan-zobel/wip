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

abstract class AComplex implements IComplex {

    public abstract double re();

    public abstract double im();

    public abstract boolean isMutable();

    public final boolean isImmutable() {
        return !isMutable();
    }

    public final IComplex toImmutable() {
        if (isImmutable()) {
            return this;
        }
        return new Complex(re(), im());
    }

    public final boolean isReal() {
        return im() == 0.0;
    }

    public final double arg() {
        return Math.atan2(im(), re());
    }

    public final double abs() {
        if (isInfinite()) {
            return Double.POSITIVE_INFINITY;
        }
        // sqrt(a^2 + b^2) without under/overflow
        double re = re();
        double im = im();
        if (Math.abs(re) > Math.abs(im)) {
            double abs = im / re;
            return Math.abs(re) * Math.sqrt(1.0 + abs * abs);
        } else if (im != 0.0) {
            double abs = re / im;
            return Math.abs(im) * Math.sqrt(1.0 + abs * abs);
        }
        return 0.0;
    }

    public final boolean isNan() {
        return Double.isNaN(re()) || Double.isNaN(im());
    }

    public final boolean isInfinite() {
        return Double.isInfinite(re()) || Double.isInfinite(im());
    }

    public final String toString() {
        return re() + "  " + im() + "i";
    }

    public final boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that instanceof IComplex) {
            IComplex other = (IComplex) that;
            if (other.isNan()) {
                return this.isNan();
            }
            return re() == other.re() && im() == other.im();
        }
        return false;
    }

    public final int hashCode() {
        int h = 0x7FFFF + Double.hashCode(re());
        h = ((h << 19) - h) + Double.hashCode(im());
        return (h << 19) - h;
    }
}
