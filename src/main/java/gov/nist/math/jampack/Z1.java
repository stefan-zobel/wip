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
package gov.nist.math.jampack;

/**
 * Z1 implements a one-dimensional array of complex numbers as a two arrays of
 * type double. The addressing is zero based. It is necessary to provide
 * one-dimensional complex arrays whose real and imaginary parts are contiguous
 * in storage.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Z1 {

    final int n;
    final double re[];
    final double im[];

    /**
     * Creates a Z1 initialized to zero.
     * 
     * @param n
     *            a positive integer
     * @throws    ZException
     *                Thrown if n &lt;= 0.
     */
    public Z1(int n) throws ZException {
        if (n <= 0) {
            throw new ZException("Nonpositive dimension.");
        }
        this.n = n;
        re = new double[n];
        im = new double[n];
    }

    /**
     * Returns the ith element of a Z1 as a Z.
     * 
     * @param i
     *            an integer
     * @return The ith element of this Z1
     */
    public Z get(int i) {
        return new Z(re[i], im[i]);
    }

    /**
     * Sets the ith element of a Z1 to a Z.
     * 
     * @param i an integer
     * @param z a Z
     */
    public void put(int i, Z z) {
        re[i] = z.re;
        im[i] = z.im;
    }

    /**
     * Sets the real and imaginary parts of the ith element of a Z1.
     * 
     * @param i an integer
     * @param real a double
     * @param imag a double
     */
    public void put(int i, double real, double imag) {
        re[i] = real;
        im[i] = imag;
    }

    /**
     * Multiplies the ith element of a Z1 by a Z.
     * 
     * @param i an integer
     * @param z a Z
     */
    public void times(int i, Z z) {
        double t = re[i] * z.re - im[i] * z.im;
        im[i] = re[i] * z.im + im[i] * z.re;
        re[i] = t;
    }
}
