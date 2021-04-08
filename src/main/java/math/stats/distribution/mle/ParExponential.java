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
import math.stats.distribution.Exponential;

/**
 * MLE for the parameter of the {@link Exponential} distribution.
 */
public final class ParExponential implements ValidatedValue {
    /** &lambda; */
    public double lambda = Double.NaN;

    @Override
    public boolean isValid() {
        return lambda > 0.0;
    }
}
