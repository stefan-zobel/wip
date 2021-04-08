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
 * TODO
 * <p>
 * <b>See</b> <a href="https://en.wikipedia.org/wiki/F-distribution">Wikipedia F-distribution</a>.
 */
public class FisherF extends AbstractContinuousDistribution {

    private final int d1; // numerator DF
    private final int d2; // denominator DF
    private final Beta beta;

    public FisherF(final int numeratorDF, final int denominatorDF) {
        this(DefaultRng.newPseudoRandom(), numeratorDF, denominatorDF);
    }

    public FisherF(final PseudoRandom prng, final int numeratorDF,
            final int denominatorDF) {
        super(prng);
        if (numeratorDF < 1) {
            throw new IllegalArgumentException("numeratorDF < 1 : "
                    + numeratorDF);
        }
        if (denominatorDF < 1) {
            throw new IllegalArgumentException("denominatorDF < 1 : "
                    + denominatorDF);
        }
        this.d1 = numeratorDF;
        this.d2 = denominatorDF;
        this.beta = new Beta(this.prng, (d1 / 2.0), (d2 / 2.0));
    }

    @Override
    public double pdf(final double x) {
        if (x < 0.0) {
            return 0.0;
        }
        if (x == 0.0) {
            if (d1 == 1) {
                return Double.POSITIVE_INFINITY;
            }
            if (d1 == 2) {
                return 1.0;
            }
            return 0.0;
        }
        // A quite clever variable substitution approach:
        final double w = d2 / (d2 + (d1 * x));
        // Fact: if X ~ F(d1, d1) then (1 - W) ~ Beta(d1/2, d2/2).
        //
        // Further note that (1): (1 - w) = (d1 / d2) * x * w
        // and (2): (1 / w) = 1 + (d1 / d2) * x
        //
        // First write out the density of the Beta((1-w); d1/2, d2/2)
        // and then substitute (1) into the resulting (1 - w) term.
        //
        // Then multiply the density by (w * w * (d1/d2)); finally
        // replace the remaining "w" term with the inverse of (2).
        //
        // Compare your result with the density of the F(x; d1, d2)
        // - both are identical! This proves that the following is
        // the correct transformation:
        return (((w * d1) * w) * beta.pdf(1.0 - w)) / d2;
    }

    @Override
    public double cdf(final double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        final double z = d1 * x;
        final double y = z / (d2 + z);
        return beta.cdf(y);
    }

    @Override
    public double sample() {
        final double y = beta.sample();
        return (y * d2) / (d1 - d1 * y);
    }

    @Override
    public double mean() {
        if (d2 <= 2) {
            return Double.NaN;
        }
        return d2 / ((double) d2 - 2.0);
    }

    @Override
    public double variance() {
        if (d2 <= 4) {
            return Double.NaN;
        }
        final double z = d2 - 2.0;
        return 2.0 * d2 * d2 * (d1 + z) / (d1 * z * z * (d2 - 4.0));
    }

    public int getNumeratorDegreesOfFreedom() {
        return d1;
    }

    public int getDenominatorDegreesOfFreedom() {
        return d2;
    }

    @Override
    public String toString() {
        return getSimpleName(d1, d2);
    }
}
