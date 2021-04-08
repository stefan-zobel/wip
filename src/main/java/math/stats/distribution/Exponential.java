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
package math.stats.distribution;

import math.fast.SpeedyMath;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * The Exponential(&lambda;) distribution for x &gt;= 0 with PDF:
 * <p>
 * <tt>f(x; &lambda;) = &lambda; * e<sup>-&lambda; * x</sup></tt> where &lambda;
 * &gt; 0.
 * <p>
 * Valid parameter ranges: <tt>x &gt;= 0</tt>; &lambda; &gt; 0.
 * <p>
 * https://en.wikipedia.org/wiki/Exponential_distribution
 */
public class Exponential extends AbstractContinuousDistribution {

    private static final double BIG = 100.0;

    private final double lambda;

    public Exponential(double lambda) {
        this(DefaultRng.newPseudoRandom(), lambda);
    }

    public Exponential(PseudoRandom prng, double lambda) {
        super(prng);
        if (lambda <= 0.0) {
            throw new IllegalArgumentException("lambda <= 0.0 : " + lambda);
        }
        this.lambda = lambda;
    }

    @Override
    public double pdf(double x) {
        return x < 0.0 ? 0.0 : lambda * Math.exp(-lambda * x);
    }

    @Override
    public double cdf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        double y = lambda * x;
        if (y >= BIG) {
            return 1.0;
        }
        return -SpeedyMath.expm1(-y);
    }

    @Override
    public double sample() {
        double u;
        do {
            u = prng.nextDouble();
        } while (u == 0.0 || u == 1.0);
        return -Math.log(u) / lambda;
    }

    @Override
    public double mean() {
        return (1.0 / lambda);
    }

    @Override
    public double variance() {
        return (1.0 / (lambda * lambda));
    }

    @Override
    public String toString() {
        return getSimpleName(lambda);
    }
}
