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
package math.stats.distribution.mle;

import math.stats.ValidatedValue;
import math.stats.distribution.StudentT;

/**
 * MLE for the parameter of a {@link StudentT} distribution. Note that we allow
 * for double-valued degrees of freedom. However, {@link #df} must be
 * {@code > 0.0} to be considered valid.
 */
public final class ParStudentT implements ValidatedValue {

    /** degrees of freedom (&nu;) */
    public double df = Double.NaN;

    /**
     * A {@link StudentT} &nu; (degrees of freedom) parameter is considered
     * valid in this implementation if &nu; {@code > 0.0}. You'll have to
     * {@link Math#round(double)} and ensure that {@code df >= 1} yourself if
     * you need an integer-valued estimator.
     */
    @Override
    public boolean isValid() {
        return df > 0.0;
    }
}
