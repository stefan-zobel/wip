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
package math.density;

import static math.MathConsts.SQRT_TWO_PI;

import math.cern.ProbabilityFuncs;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * Normal (a.k.a Gaussian) distribution.
 * 
 * <pre>
 *                 1                       2
 *    pdf(x) = -----------   exp( - (x-mean) / 2v ) 
 *             sqrt(2pi*v)
 * 
 *                            x
 *                            -
 *                 1         | |                 2
 *    cdf(x) = -----------   |    exp( - (t-mean) / 2v ) dt
 *             sqrt(2pi*v) | |
 *                          -
 *                         -inf.
 * </pre>
 * 
 * where <tt>v = variance = standardDeviation^2</tt>.
 * <p>
 * See <a href="https://en.wikipedia.org/wiki/Normal_distribution">Wikipedia Normal distribution</a>.
 * 
 * @author wolfgang.hoschek@cern.ch
 * @version 1.0, 09/24/99
 */
public class Normal extends AbstractContinuousDistribution {

    /** Mean of this distribution */
    private final double mean;

    /** Standard deviation of this distribution */
    private final double stdDev;

    /** Variance of this distribution */
    private final double variance;

    /** 1.0 / (stdDev * sqrt(2 * PI)) */
    private final double factor;

    public Normal() {
        this(0.0, 1.0);
    }

    public Normal(final PseudoRandom prng) {
        this(prng, 0.0, 1.0);
    }

    public Normal(final double mean, final double stdDev) {
        this(DefaultRng.newPseudoRandom(), mean, stdDev);
    }

    public Normal(final PseudoRandom prng, final double mean,
            final double stdDev) {
        super(prng);
        if (stdDev <= 0.0) {
            throw new IllegalArgumentException(
                    "Standard deviation must be positive (" + stdDev + ")");
        }
        this.mean = mean;
        this.stdDev = stdDev;
        this.variance = stdDev * stdDev;
        this.factor = (1.0 / (this.variance * SQRT_TWO_PI));
    }

    @Override
    public double pdf(final double x) {
        double xMinusMu = (x - mean);
        return factor * Math.exp(-(xMinusMu * xMinusMu) / (2.0 * variance));
    }

    @Override
    public double cdf(final double x) {
        return ProbabilityFuncs.normal(mean, variance, x);
    }

    @Override
    public double sample() {
        return mean + prng.nextGaussian() * stdDev;
    }

    @Override
    public double mean() {
        return mean;
    }

    @Override
    public double variance() {
        return variance;
    }

    @Override
    public String toString() {
        return getSimpleName(mean, stdDev);
    }
}
