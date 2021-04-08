/*
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
/*
 * Copyright © 1999 CERN - European Organization for Nuclear Research.
 * Permission to use, copy, modify, distribute and sell this software and
 * its documentation for any purpose is hereby granted without fee, provided
 * that the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation. CERN makes no representations about the suitability of this
 * software for any purpose. It is provided "as is" without expressed or
 * implied warranty.
 */
package math.density;

import math.cern.FastGamma;
import math.cern.ProbabilityFuncs;
import math.rng.DefaultRng;
import math.rng.PseudoRandom;

/**
 * StudentT distribution (a.k.a "T distribution").
 * <p>
 * <tt>p(x) = const  *  (1 + x^2/&nu;) ^ -(&nu;+1)/2</tt> where
 * <tt>const = &Gamma;((&nu;+1)/2) / (&radic;(&Pi;*&nu;) * &Gamma;(&nu;/2))</tt>
 * and <tt>&Gamma;(a)</tt> being the Gamma function and <tt>&nu;</tt> being the
 * degrees of freedom.
 * </p>
 * <b>Implementation:</b> This is a port of <A HREF=
 * "http://wwwinfo.cern.ch/asd/lhc++/clhep/manual/RefGuide/Random/RandStudentT.html"
 * >RandStudentT</A> used in <A
 * HREF="http://wwwinfo.cern.ch/asd/lhc++/clhep">CLHEP 1.4.0</A> (C++). CLHEP's
 * implementation, in turn, is based on <tt>tpol.c</tt> from the <A
 * HREF="http://www.cis.tu-graz.ac.at/stat/stadl/random.html">C-RAND /
 * WIN-RAND</A> library. C-RAND's implementation, in turn, is based upon
 * <p>
 * R.W. Bailey (1994): Polar generation of random variates with the
 * t-distribution, Mathematics of Computation 62, 779-781.
 * <p>
 * See <a href="https://en.wikipedia.org/wiki/Student%27s_t-distribution">Wikipedia Student's t-distribution</a>.
 *  
 * @author wolfgang.hoschek@cern.ch
 * @version 1.0, 09/24/99
 */
public class StudentT extends AbstractContinuousDistribution {

    private final double df;
    private final double pdfConst;

    public StudentT(double df) {
        this(DefaultRng.newPseudoRandom(), df);
    }

    public StudentT(PseudoRandom prng, double df) {
        super(prng);
        if (df <= 0.0) {
            throw new IllegalArgumentException("df <= 0.0 : " + df);
        }
        final double tmp = FastGamma.logGamma((df + 1.0) / 2.0)
                - FastGamma.logGamma(df / 2.0);
        this.pdfConst = Math.exp(tmp) / Math.sqrt(Math.PI * df);
        this.df = df;
    }

    @Override
    public double pdf(double x) {
        return pdfConst * Math.pow((1.0 + x * x / df), -(df + 1.0) * 0.5);
    }

    @Override
    public double cdf(double x) {
        return ProbabilityFuncs.studentT(df, x);
    }

    @Override
    public double sample() {
        /*
         * Marsaglia's formulation of the Box/Muller polar method for generating
         * Normal variates is adapted to the Student-t distribution. The two
         * generated variates are not independent and the expected number of
         * uniforms per variate is 2.5464.
         * 
         * Reference:
         * 
         * R.W. Bailey (1994): Polar generation of random variates with the
         * t-distribution, Mathematics of Computation 62, 779-781.
         */
        double u1, u2, q;
        do {
            u1 = 2.0 * prng.nextDouble() - 1.0; // between -1 and 1
            u2 = 2.0 * prng.nextDouble() - 1.0; // between -1 and 1
            q = u1 * u1 + u2 * u2;
        } while (q > 1.0);
        return u1
                * Math.sqrt(df * (Math.exp(-2.0 / df * Math.log(q)) - 1.0)
                        / q);
    }

    @Override
    public double mean() {
        if (df <= 1.0) {
            return Double.NaN;
        }
        return 0.0;
    }

    @Override
    public double variance() {
        if (df > 2.0) {
            return df / ((double) df - 2.0);
        }
        if (df == 2.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Double.NaN;
    }

    public double getDegreesOfFreedom() {
        return df;
    }

    @Override
    public String toString() {
        return getSimpleName(df);
    }
}
