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

import math.cern.FastGamma;
import math.cern.ProbabilityFuncs;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * The &Gamma;(x; k, &theta;) distribution for x &gt;= 0 with PDF:
 * <p>
 * <tt>f(x; k, &theta;) = (x^(k-1) * e^(-x/&theta;)) / (&theta;^k * &Gamma;(k)) </tt>
 * where <tt>&Gamma;()</tt> is the Gamma function.
 * <p>
 * Valid parameter ranges: <tt>k &gt; 0</tt>, <tt>&theta;  &gt; 0</tt>,
 * <tt>x &gt;= 0</tt>.
 * <p>
 * Note: For a Gamma distribution to have the mean <tt>E(X)</tt> and variance
 * <tt>Var(X)</tt>, set the parameters as follows:
 * 
 * <pre>
 * k = E(X) * E(X) / Var(X)
 * &theta; = Var(X) / E(X)
 * </pre>
 */
public class Gamma extends AbstractContinuousDistribution {

    private final double shape_k;
    private final double scale_theta;
    private final double rate_beta;

    public Gamma(final double shape /* k */) {
        this(shape, 1.0 /* scale */);
    }

    public Gamma(final PseudoRandom prng, final double shape /* k */) {
        this(prng, shape, 1.0 /* scale */);
    }

    public Gamma(final double shape /* k */, final double scale /* theta */) {
        this(DefaultRng.newPseudoRandom(), shape, scale);
    }

    public Gamma(final PseudoRandom prng, final double shape /* k */,
            final double scale /* theta */) {
        super(prng);
        if (shape <= 0.0) {
            throw new IllegalArgumentException("shape <= 0.0");
        }
        if (scale <= 0.0) {
            throw new IllegalArgumentException("scale <= 0.0");
        }
        this.shape_k = shape;
        this.scale_theta = scale;
        this.rate_beta = (1.0 / this.scale_theta);
    }

    /**
     * Returns the probability distribution function.
     * 
     * @param x
     *            Where to compute the density function.
     * 
     * @return The value of the gamma density at x.
     */
    @Override
    public double pdf(final double x) {
        if (x < 0.0) {
            throw new IllegalArgumentException("x < 0.0");
        }
        if (x == 0.0) {
            if (shape_k == 1.0) {
                return rate_beta;
            } else if (shape_k < 1.0) {
                return Double.POSITIVE_INFINITY;
            } else {
                return 0.0;
            }
        }
        if (shape_k == 1.0) {
            return rate_beta * Math.exp(-rate_beta * x);
        }

        return rate_beta
                * Math.exp((shape_k - 1.0) * Math.log(rate_beta * x)
                        - (rate_beta * x) - FastGamma.logGamma(shape_k));
    }

    @Override
    public double cdf(final double x) {
        return ProbabilityFuncs.gamma(shape_k, rate_beta, x);
    }

    /**
     * This implementation uses the following algorithms:
     * <p>
     * For 0 < k < 1: <br/>
     * Ahrens, J. H. and Dieter, U., <i>Computer methods for sampling from
     * gamma, beta, Poisson and binomial distributions.</i> Computing, 12,
     * 223-246, 1974.
     * </p>
     * <p>
     * For k >= 1: <br/>
     * Marsaglia and Tsang, <i>A Simple Method for Generating Gamma
     * Variables.</i> ACM Transactions on Mathematical Software, Volume 26 Issue
     * 3, September, 2000.
     * </p>
     * 
     * @return random value sampled from the &Gamma;(k, &theta;) distribution
     */
    @Override
    public double sample() {
        if (shape_k < 1.0) {
            // [1]: p. 228, Algorithm GS
            final double bGS = 1.0 + shape_k / Math.E;

            while (true) {
                // Step 1:
                double u = prng.nextDouble();
                double p = bGS * u;

                if (p <= 1.0) {
                    // Step 2:

                    double x = Math.pow(p, 1.0 / shape_k);
                    double u2 = prng.nextDouble();

                    if (u2 > Math.exp(-x)) {
                        // reject
                        continue;
                    } else {
                        return scale_theta * x;
                    }
                } else {
                    // Step 3:

                    double x = -1 * Math.log((bGS - p) / shape_k);
                    double u2 = prng.nextDouble();

                    if (u2 > Math.pow(x, shape_k - 1)) {
                        // reject
                        continue;
                    } else {
                        return scale_theta * x;
                    }
                }
            }
        }

        // shape >= 1
        final double d = shape_k - 0.333333333333333333;
        final double c = 1.0 / (3.0 * Math.sqrt(d));

        while (true) {
            double x = prng.nextGaussian();
            double cx = 1.0 + c * x;
            double v = cx * cx * cx;

            if (v <= 0.0) {
                continue;
            }

            double x2 = x * x;
            double u = prng.nextDouble();

            // squeeze
            if (u < 1.0 - 0.0331 * x2 * x2) {
                return scale_theta * d * v;
            }

            if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) {
                return scale_theta * d * v;
            }
        }
    }

    @Override
    public double mean() {
        return shape_k * scale_theta; // k * theta
    }

    @Override
    public double variance() {
        return shape_k * scale_theta * scale_theta; // k * (theta^2)
    }

    /**
     * Inverse of the Gamma cumulative distribution function.
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
        return findRoot(probability, mean(), 0.0, Double.MAX_VALUE);
    }

    /**
     * Returns the shape parameter of this distribution.
     * 
     * @return the shape parameter.
     */
    public double getShape() {
        return shape_k;
    }

    /**
     * Returns the scale parameter of this distribution.
     * 
     * @return the scale parameter.
     */
    public double getScale() {
        return scale_theta;
    }

    @Override
    public String toString() {
        return getSimpleName(shape_k, scale_theta);
    }
}
