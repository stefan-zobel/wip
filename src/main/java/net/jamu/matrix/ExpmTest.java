/*
 * Copyright 2020 Stefan Zobel
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
package net.jamu.matrix;

/**
 * Test for Expm
 */
public final class ExpmTest {

    public static void main(String[] args) {

        // https://de.mathworks.com/help/matlab/ref/expm.html
        MatrixD A = Matrices.createD(3, 3);
        A.set(0, 0, 1);
        A.set(0, 1, 1);
        A.set(0, 2, 0);
        A.set(1, 0, 0);
        A.set(1, 1, 0);
        A.set(1, 2, 2);
        A.set(2, 0, 0);
        A.set(2, 1, 0);
        A.set(2, 2, -1);
        System.out.println(A);
        MatrixD eA = Expm.expmD(A, A.normMaxAbs());
        System.out.println(eA);

        // A4: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A2 = Matrices.createD(2, 2);
        A2.set(0, 0, 0.25);
        A2.set(0, 1, 0.25);
        A2.set(1, 0, 0);
        A2.set(1, 1, 0);
        System.out.println(A2);
        MatrixD eA2 = Expm.expmD(A2, A2.normMaxAbs());
        System.out.println(eA2);

        // A5: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A3 = Matrices.createD(2, 2);
        A3.set(0, 0, 0);
        A3.set(0, 1, 0.02);
        A3.set(1, 0, 0);
        A3.set(1, 1, 0);
        System.out.println(A3);
        MatrixD eA3 = Expm.expmD(A3, A3.normMaxAbs());
        System.out.println(eA3);

        // A3: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A4 = Matrices.createD(3, 3);
        A4.set(0, 0, -131);
        A4.set(0, 1, 19);
        A4.set(0, 2, 18);
        A4.set(1, 0, -390);
        A4.set(1, 1, 56);
        A4.set(1, 2, 54);
        A4.set(2, 0, -387);
        A4.set(2, 1, 57);
        A4.set(2, 2, 52);
        A4 = A4.transpose();
        System.out.println(A4);
        MatrixD eA4 = Expm.expmD(A4, A4.normMaxAbs());
        System.out.println(eA4);

        // A1: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A5 = Matrices.createD(3, 3);
        A5.set(0, 0, 4);
        A5.set(0, 1, 2);
        A5.set(0, 2, 0);
        A5.set(1, 0, 1);
        A5.set(1, 1, 4);
        A5.set(1, 2, 1);
        A5.set(2, 0, 1);
        A5.set(2, 1, 1);
        A5.set(2, 2, 4);
        A5 = A5.transpose();
        System.out.println(A5);
        MatrixD eA5 = Expm.expmD(A5, A5.normMaxAbs());
        System.out.println(eA5);
    }
}
