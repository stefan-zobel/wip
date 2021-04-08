/*
 * Class:        GofStat
 * Description:  Goodness-of-fit test statistics
 * Environment:  Java
 * Software:     SSJ
 * Copyright (C) 2001  Pierre L'Ecuyer and Universite de Montreal
 * Organization: DIRO, Universite de Montreal
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
package math.stats.distribution.fit;

import math.stats.distribution.ContinuousDistribution;

/**
 * Transformation of empirical observations to {@code (0, 1)} interval.
 */
final class Transformer {

    /**
     * Apply the {@link ContinuousDistribution#cdf(double)} method of the given
     * {@code dist} to the data in {@code observations}. This transforms the
     * data to a {@code (0, 1)} interval. If the hypothesized {@code dist} fits
     * the data in {@code observations} then the resulting transformed data
     * should be roughly uniformly distributed on {@code (0, 1)}.
     * 
     * @param observations
     *            the empirical data to transform
     * @param dist
     *            the distribution to use for the transformation
     * @return data transformed to the {@code (0, 1)} interval. If the
     *         hypothesized distribution describes the observations then the
     *         transformed data is approximately uniformly distributed
     */
    static double[] uniform(double[] observations, ContinuousDistribution dist) {
        if (dist == null) {
            throw new IllegalArgumentException("dist == null");
        }
        if (observations == null) {
            throw new IllegalArgumentException("observations == null");
        }
        double[] transformed = new double[observations.length];
        for (int i = 0; i < transformed.length; ++i) {
            transformed[i] = dist.cdf(observations[i]);
        }
        return transformed;
    }

    private Transformer() {
        throw new AssertionError();
    }
}
