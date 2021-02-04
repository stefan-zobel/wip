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
package math.fun;

/**
 * Interface for the gradient of once-differentiable double-valued functions
 * over double[] arrays.
 */
public interface Gradient {
    /**
     * The first-derivative vector (a.k.a. gradient) of a double-valued function
     * over a double[] array evaluated at the input location {@code x} gets
     * stored into the output vector {@code grad}.
     * 
     * @param x
     *            a <code>double[]</code> input vector (not modified)
     * @param grad
     *            a <code>double[]</code> output vector containing the gradient
     *            at location {@code x} (modified)
     */
    void derivativeAt(double[] x, double[] grad);
}
