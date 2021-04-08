/*
 * Copyright 2017 Stefan Zobel
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
 * https://en.wikipedia.org/wiki/Uniform_distribution_(continuous)
 */
public class Uniform extends AbstractContinuousDistribution {

    private final double a;
    private final double b;

    public Uniform(PseudoRandom prng, double a, double b) {
        super(prng);
        if (b <= a) {
            throw new IllegalArgumentException("b <= a");
        }
        this.a = a;
        this.b = b;
    }

    public Uniform(double a, double b) {
        this(DefaultRng.newPseudoRandom(), a, b);
    }

    public Uniform() {
        this(DefaultRng.newPseudoRandom());
    }

    public Uniform(PseudoRandom prng) {
        this(prng, 0.0, 1.0);
    }

    @Override
    public double pdf(double x) {
        if (x < a || x > b) {
            return 0.0;
        }
        return 1.0 / (b - a);
    }

    @Override
    public double cdf(double x) {
        if (x <= a) {
            return 0.0;
        }
        if (x >= b) {
            return 1.0;
        }
        return (x - a) / (b - a);
    }

    @Override
    public double sample() {
        return a + ((b - a) * prng.nextDouble());
    }

    @Override
    public double mean() {
        return (a + b) / 2.0;
    }

    @Override
    public double variance() {
        return ((b - a) * (b - a)) / 12.0;
    }

    @Override
    public String toString() {
        return getSimpleName(a, b);
    }
}
