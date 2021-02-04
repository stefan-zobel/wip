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
 * Finite difference numerical gradient calculation using the forward difference
 * approximation {@code f'(x) = (f(x + h) - f(x)) / h}.
 * <p>
 * Scaling of {@code h} is taken into account by each individual {@code h} being
 * based upon the absolute magnitude of the corresponding element in the vector
 * {@code x}.
 */
public abstract class NumericalDiffDMultiFunction implements
        DiffDMultiFunction {

    /** The IEEE 754 machine epsilon from Cephes: (2^-53) */
    private static final double MACH_EPS_DBL = 1.11022302462515654042e-16;

    protected final double diffScale;

    public NumericalDiffDMultiFunction() {
        this(1.5 * Math.sqrt(MACH_EPS_DBL));
    }

    public NumericalDiffDMultiFunction(double diffScale) {
        this.diffScale = diffScale;
    }

    @Override
    public final void derivativeAt(double[] x, double[] grad) {
        double fx = this.apply(x);

        for (int i = 0; i < x.length; ++i) {
            double xi = x[i];
            double hi = (xi != 0.0) ? diffScale * Math.abs(xi) : diffScale;

            double xi_plus_hi = xi + hi;

            // account for potential round-off errors
            hi = xi_plus_hi - xi;

            x[i] = xi_plus_hi;
            // new function value for advance in variable i
            double fx_plus_hi = this.apply(x);
            // estimated gradient component for variable i
            grad[i] = (fx_plus_hi - fx) / hi;

            // restore the old value for variable i
            x[i] = xi;
        }
    }
}
