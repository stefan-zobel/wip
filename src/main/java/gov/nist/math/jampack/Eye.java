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
 * Eye generates a matrix whose diagonal elements are one and whose off diagonal
 * elements are zero.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Eye {

    /**
     * Generates an identity matrix of order <tt>n</tt>.
     * 
     * @param n the order of the matrix
     * @return an identity matrix
     */
    public static Zmat o(int n) {
        return o(n, n);
    }

    /**
     * Generates an <tt>m x n</tt> matrix whose diagonal elements are one and
     * whose off diagonal elements are zero.
     * 
     * @param m the number of rows in the matrix
     * @param n the number of columns in the matrix
     * @return diagonal matrix whose diagonal elements are 1
     */
    public static Zmat o(int m, int n) {

        Zmat I = new Zmat(m, n);

        for (int i = 0; i < Math.min(m, n); i++) {
            I.setRe(i, i, 1.0);
            I.setIm(i, i, 0.0);
        }

        return I;
    }
}
