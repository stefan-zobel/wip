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

import static math.cern.FastGamma.logGamma;

import math.cern.ProbabilityFuncs;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * TODO
 * <p>
 * <b>Warning:</b> Do not choose a too low value for &alpha; and/or &beta;
 * (<b>especially</b> not for &beta;!). Otherwise the sampled distribution may
 * get very inaccurate. An &alpha; and/or &beta; of {@code 0.125} ({@code 1/8})
 * should be ok, values below are not. If you need to have a <b>&beta;</b> in
 * the range {@code 1/8 <= } &beta; {@code < 1} then <b>&alpha;</b> must not be
 * too large. A ratio of &beta; : &alpha; of {@code 1 : 1800} should be ok (e.g.
 * &alpha; {@code = 225} for &beta; {@code = 0.125}). Don't go above that. If
 * only &alpha; is small (but not less than {@code 1/8}) there seems to exist no
 * practically relevant limit for the magnitude of &beta; (other than the lower
 * bound of {@code 1/8}).
 * <p>
 * <b>See</b> <a href="https://en.wikipedia.org/wiki/Beta_distribution">Wikipedia Beta distribution</a>.
 */
public class Beta extends AbstractContinuousDistribution {

    private final double alpha;
    private final double beta;
    private final double pdfNormFactor;
    private final Gamma gamma_U;
    private final Gamma gamma_V;

    public Beta(final double alpha, final double beta) {
        this(DefaultRng.newPseudoRandom(), alpha, beta);
    }

    public Beta(final PseudoRandom prng, final double alpha, final double beta) {
        super(prng);
        if (alpha <= 0.0) {
            throw new IllegalArgumentException("alpha <= 0.0");
        }
        if (beta <= 0.0) {
            throw new IllegalArgumentException("beta <= 0.0");
        }
        this.alpha = alpha;
        this.beta = beta;
        this.pdfNormFactor = Math.exp(logGamma(alpha + beta)
                - (logGamma(alpha) + logGamma(beta)));

        gamma_U = new Gamma(this.prng, alpha);
        gamma_V = new Gamma(DefaultRng.newIndepPseudoRandom(this.prng), beta);
    }

    @Override
    public double pdf(final double x) {
        if (x < 0.0 || x > 1.0) {
            return 0.0;
        }
        return pdfNormFactor * Math.pow(x, alpha - 1)
                * Math.pow(1 - x, beta - 1);
    }

    @Override
    public double cdf(final double x) {
        if (x <= 0.0) {
            return 0.0;
        } else if (x >= 1.0) {
            return 1.0;
        }
        return ProbabilityFuncs.beta(alpha, beta, x);
    }

    @Override
    public double sample() {
        // This may not be the most efficient solution,
        // but it doesn't get any simpler. The problem is
        // alpha and beta must not be too small, especially
        // a beta < 1 paired with a very large alpha is numerically
        // inaccurate. But this seems to be true for all algorithms
        // (commons.math appears to be even more inaccurate than this
        // simple implementation - not to mention that it is much slower).

        final double u = gamma_U.sample();
        return u / (u + gamma_V.sample());
    }

    @Override
    public double mean() {
        return alpha / (alpha + beta);
    }

    @Override
    public double variance() {
        final double alphaPlusBeta = alpha + beta;
        return (alpha * beta)
                / (alphaPlusBeta * alphaPlusBeta * (alphaPlusBeta + 1));
    }

    public double getAlpha() {
        return alpha;
    }

    public double getBeta() {
        return beta;
    }

    @Override
    public String toString() {
        return getSimpleName(alpha, beta);
    }
}
