/*
 * Class:        GofFormat
 * Description:
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

import java.util.Arrays;

import math.cern.Arithmetic;
import math.stats.distribution.ContinuousDistribution;

/**
 * Provides methods for the computation of goodness of fit tests
 * (Anderson-Darling and Kolomogorov-Smirnov).
 */
public final class GoodnessOfFit {

    /**
     * Computes a goodness of fit test {@link UniformTestStatistics.Result} for
     * the given data and the postulated model distribution.
     * 
     * @param observations
     *            the list of observations to explore
     * @param distribution
     *            the hypothesized distribution of the empirical data
     * @return the {@link UniformTestStatistics.Result} for the given
     *         observations and distribution
     */
    public static UniformTestStatistics.Result computeStatistics(double[] observations,
            ContinuousDistribution distribution) {
        double[] transformed = Transformer.uniform(observations, distribution);
        Arrays.sort(transformed);
        UniformTestStatistics.Result statistics = UniformTestStatistics.compareEmpiricalToUniform(transformed);
        if (observations.length == 1) {
            // one wants obs[0], not u[0]
            statistics.MEAN = observations[0];
        }
        return statistics;
    }

    /**
     * Computes the Anderson-Darling and Kolomogorov-Smirnov tests' p-values for
     * the given test statistics.
     * 
     * @param testStatistics
     *            the the {@link UniformTestStatistics.Result} to examine
     * @return the {@link UniformTestStatistics.PValue} for the given test
     *         statistics
     */
    public static UniformTestStatistics.PValue computePValues(UniformTestStatistics.Result testStatistics) {
        if (testStatistics == null) {
            throw new IllegalArgumentException("testStatistics == null");
        }
        if (testStatistics.N < 1) {
            throw new IllegalArgumentException(
                    "testStatistics doesn't contain any observations (N = " + testStatistics.N + ")");
        }
        UniformTestStatistics.PValue pval = new UniformTestStatistics.PValue();
        pval.N = testStatistics.N;
        if (testStatistics.N == 1) {
            pval.KSP_PVAL = testStatistics.KSP;
            return pval;
        }
        if (!Arithmetic.isBadNum(testStatistics.KSP)) {
            // Kolmogorov-Smirnov+
            // double p = KolmogorovSmirnovP.barF(testStatistics.N, testStatistics.KSP);
            // pval.KSP_PVAL = p;
        }
        if (!Arithmetic.isBadNum(testStatistics.KSM)) {
            // Kolmogorov-Smirnov-
            // double p = KolmogorovSmirnovP.barF(testStatistics.N, testStatistics.KSM);
            // pval.KSM_PVAL = p;
        }
        if (!Arithmetic.isBadNum(testStatistics.KS)) {
            // Kolmogorov-Smirnov
            double p = FastKolmogorovSmirnov.barF(testStatistics.N, testStatistics.KS);
            pval.KS_PVAL = p;
        }
        if (!Arithmetic.isBadNum(testStatistics.AD)) {
            // Anderson-Darling
            double p = AndersonDarling.barF(testStatistics.N, testStatistics.AD);
            pval.AD_PVAL = p;
        }
        return pval;
    }

    private GoodnessOfFit() {
        throw new AssertionError();
    }
}
