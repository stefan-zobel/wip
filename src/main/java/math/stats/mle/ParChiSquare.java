/*
 * Copyright 2018 Stefan Zobel
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
package math.stats.mle;

import math.density.ChiSquare;
import math.stats.Validity;

/**
 * MLE for the parameter of the {@link ChiSquare} distribution.
 */
public final class ParChiSquare implements Validity {

    /** {@code k} (degrees of freedom) */
    public double degreesOfFreedom = Double.NaN;

    /**
     * A {@link ChiSquare} {@code k} (degrees of freedom) parameter is
     * considered valid in this implementation if {@code k > 0.0}. You'll have
     * to {@link Math#round(double)} and ensure that {@code k >= 1} yourself if
     * you need an integer-valued estimator.
     */
    @Override
    public boolean isValid() {
        return degreesOfFreedom > 0.0;
    }
}
