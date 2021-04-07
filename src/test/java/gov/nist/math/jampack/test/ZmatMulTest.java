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
package gov.nist.math.jampack.test;

import gov.nist.math.jampack.Times;
import gov.nist.math.jampack.Z;
import gov.nist.math.jampack.Zmat;

public class ZmatMulTest {

    public ZmatMulTest() {
        // expected result
        // [6.575, 3.45, 28.225, 10.35, 8.5375, -0.1499999999999999, 34.9875,
        // 4.550000000000001, 8.375, 4.75, 36.375, 20.25]
    }

    public static void main(String[] args) {
        Zmat A = new Zmat(2, 2);
        Zmat B = new Zmat(2, 3);

        // 2 x 2 matrix A
        // 0.0 + 0.0i, 1.0 + 0.25i
        // 2.0 + 0.5i, 3.0 + 0.75i
        A.put(1, 1, new Z(0.0, 0.0));
        A.put(1, 2, new Z(1.0, 0.25));
        A.put(2, 1, new Z(2.0, 0.5));
        A.put(2, 2, new Z(3.0, 0.75));

        // 2 x 3 matrix B
        // 4.0 - 1.0i, 5.0 + 1.25i, 6.0 + 1.5i
        // 7.0 + 1.7i, 8.0 - 2.15i, 9.0 + 2.5i
        B.put(1, 1, new Z(4.0, -1.0));
        B.put(1, 2, new Z(5.0, 1.25));
        B.put(1, 3, new Z(6.0, 1.5));
        B.put(2, 1, new Z(7.0, 1.7));
        B.put(2, 2, new Z(8.0, -2.15));
        B.put(2, 3, new Z(9.0, 2.5));

        Zmat C = Times.o(A, B);
        Z[][] array = C.getZ();

        // [6.575, 3.45, 28.225, 10.35, 8.5375, -0.1499999999999999, 34.9875,
        // 4.550000000000001, 8.375, 4.75, 36.375, 20.25]
        for (int i = 0; i < array.length; ++i) {
            Z[] row = array[i];
            for (int j = 0; j < row.length; ++j) {
                Z z = row[j];
                System.out.print("(" + i + ", " + j + "): " + z.re + " " + z.im + "\n");
            }
        }
    }
}
