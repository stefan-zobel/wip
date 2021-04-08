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
package math.density;

import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * The &Chi;<sup>2</sup>(k) distribution for x &gt;= 0 with PDF:
 * <p>
 * <tt>f(x; k) = (1 / (2<sup>k/2</sup> * &Gamma;(k/2))) * x<sup>(k/2) - 1 </sup> * e<sup>-x/2</sup></tt>
 * where <tt>&Gamma;()</tt> is the Gamma function.
 * <p>
 * Valid parameter ranges: <tt>k &gt; 0</tt>; <tt>x &gt; 0</tt> (if
 * <tt>k = 1</tt>, otherwise <tt>x &gt;= 0</tt>).
 * <p>
 * https://en.wikipedia.org/wiki/Chi-squared_distribution
 */
public class ChiSquare extends AbstractContinuousDistribution {

    private static final double BIG = 100.0;

    private final double degreesOfFreedom;
    private final Gamma gamma;

    public ChiSquare(double degreesOfFreedom) {
        this(DefaultRng.newPseudoRandom(), degreesOfFreedom);
    }

    public ChiSquare(PseudoRandom prng, double degreesOfFreedom) {
        super(prng);
        if (degreesOfFreedom <= 0.0) {
            throw new IllegalArgumentException("degreesOfFreedom <= 0.0 : "
                    + degreesOfFreedom);
        }
        this.degreesOfFreedom = degreesOfFreedom;
        this.gamma = new Gamma(this.prng, (this.degreesOfFreedom / 2.0), 2.0);
    }

    @Override
    public double pdf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        return gamma.pdf(x);
    }

    @Override
    public double cdf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        if (x >= BIG * degreesOfFreedom) {
            return 1.0;
        }
        return gamma.cdf(x);
    }

    @Override
    public double sample() {
        return gamma.sample();
    }

    /**
     * Inverse of the Chi-squared cumulative distribution function.
     * 
     * @return the value X for which P(x&lt;=X).
     */
    public double inverse(double probability) {
        if (probability <= 0.0) {
            return 0.0; // < 0 is not entirely correct (TODO)
        }
        if (probability >= 1.0) {
            return Double.MAX_VALUE; // > 1 is not entirely correct (TODO)
        }
        return gamma.inverse(probability);
    }

    @Override
    public double mean() {
        return degreesOfFreedom;
    }

    @Override
    public double variance() {
        return 2.0 * degreesOfFreedom;
    }

    /**
     * @return the degreesOfFreedom
     */
    public double getDegreesOfFreedom() {
        return degreesOfFreedom;
    }

    @Override
    public String toString() {
        return getSimpleName(degreesOfFreedom);
    }
}
