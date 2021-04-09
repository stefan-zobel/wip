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

/**
 * A <a
 * href="https://en.wikipedia.org/wiki/Probability_distribution#Continuous_probability_distribution">continuous
 * probability distribution</a>.
 */
public interface ContinuousDistribution {

    /**
     * Returns the probability density function (PDF) of this distribution
     * evaluated at the specified point {@code x}. In general, the PDF is the
     * derivative of the {@link #cdf(double) CDF}. If the derivative does not
     * exist at {@code x}, then an appropriate replacement should be returned,
     * e.g. {@code Double.POSITIVE_INFINITY}, {@code Double.NaN}, or the limit
     * inferior or limit superior of the difference quotient.
     * 
     * @param x
     *            the point at which the PDF is evaluated
     * @return the value of the probability density function at point {@code x}
     */
    double pdf(double x);

    /**
     * For a random variable {@code X} whose values are distributed according to
     * this distribution, this method returns {@code P(X <= x)}. In other words,
     * this method represents the (cumulative) distribution function (CDF) for
     * this distribution.
     * 
     * @param x
     *            the point at which the CDF is evaluated
     * @return the probability that a random variable with this distribution
     *         takes a value less than or equal to {@code x}
     */
    double cdf(double x);

    /**
     * Generate a random value sampled from this distribution.
     * 
     * @return a random value.
     */
    double sample();

    /**
     * Generate a random sample from the distribution.
     * 
     * @param sampleSize
     *            the number of random values to generate.
     * @return an array representing the random sample
     */
    double[] sample(int sampleSize);

    /**
     * Use this method to get the the mean of this distribution.
     * 
     * @return the mean or {@code Double.NaN} if it is not defined
     */
    double mean();

    /**
     * Use this method to get the variance of this distribution.
     * 
     * @return the variance (possibly {@code Double.POSITIVE_INFINITY} as for
     *         certain cases in {@link StudentT}) or {@code Double.NaN} if it is
     *         not defined
     */
    double variance();

    /**
     * For a random variable {@code X} whose values are distributed according to
     * this distribution, this method returns {@code P(x0 < X <= x1)}.
     * 
     * @param x0
     *            Lower bound (excluded).
     * @param x1
     *            Upper bound (included).
     * @return the probability that a random variable with this distribution
     *         takes a value between {@code x0} and {@code x1}, excluding the
     *         lower and including the upper endpoint.
     * @throws IllegalArgumentException
     *             if {@code x0 > x1}.
     * 
     *             The default implementation uses the identity
     *             {@code P(x0 < X <= x1) = P(X <= x1) - P(X <= x0)}
     */
    double probability(double x0, double x1);
}
