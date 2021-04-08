/*
 * Copyright 2015 Stefan Zobel
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

import math.cern.ProbabilityFuncs;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * TODO
 * <p>
 * https://en.wikipedia.org/wiki/Log-normal_distribution
 */
public class LogNormal extends AbstractContinuousDistribution {

    private final double mu;
    private final double sigma;

    public LogNormal(PseudoRandom prng, double mu, double sigma) {
        super(prng);
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("sigma <= 0.0 : " + sigma);
        }
        this.mu = mu;
        this.sigma = sigma;
    }

    public LogNormal(double mu, double sigma) {
        this(DefaultRng.newPseudoRandom(), mu, sigma);
    }

    @Override
    public double pdf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        double d = Math.log(x) - mu;
        return Math.exp((-d * d) / (2.0 * (sigma * sigma))) / (Math.sqrt(2.0 * Math.PI) * sigma * x);
    }

    @Override
    public double cdf(double x) {
        if (x <= 0.0) {
            return 0.0;
        }
        return ProbabilityFuncs.normal((Math.log(x) - mu) / sigma);
    }

    @Override
    public double mean() {
        return (Math.exp(mu + (sigma * sigma) / 2.0));
    }

    @Override
    public double variance() {
        double sigsig = sigma * sigma;
        return ((Math.exp(2.0 * mu + sigsig) * (Math.exp(sigsig) - 1.0)));
    }

    @Override
    public double sample() {
        double stdNormal = prng.nextGaussian();
        return Math.exp(mu + sigma * stdNormal);
    }

    @Override
    public String toString() {
        return getSimpleName(mu, sigma);
    }
}
