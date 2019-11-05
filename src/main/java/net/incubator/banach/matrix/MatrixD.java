/*
 * Copyright 2019 Stefan Zobel
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
package net.incubator.banach.matrix;

/**
 * A {@code MatrixD} is a dense matrix of primitive doubles with column-major
 * storage layout. The addressing is zero based. All operations throw a
 * {@code NullPointerException} if any of the method arguments is {@code null}.
 */
public interface MatrixD extends Dimensions {

    double toScalar();

    /**
     * {@code A = alpha * A}
     * 
     * @param alpha
     * @return {@code A}
     */
    MatrixD scaleInplace(double alpha);

    /**
     * {@code B = alpha * A}
     * 
     * @param alpha
     * @param B
     * @return {@code B}
     */
    MatrixD scale(double alpha, MatrixD B);

    /**
     * <code>AT = A<sup>T</sup></code>
     * 
     * @param AT
     * @return {@code AT}
     */
    MatrixD trans(MatrixD AT);

    /**
     * {@code A = A + B}
     * 
     * @param B
     * @return {@code A}
     */
    MatrixD addInplace(MatrixD B);

    /**
     * {@code A = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @return {@code A}
     */
    MatrixD addInplace(double alpha, MatrixD B);

    /**
     * {@code C = A + B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD add(MatrixD B, MatrixD C);

    /**
     * {@code C = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD add(double alpha, MatrixD B, MatrixD C);

    /**
     * {@code C = A * B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD mult(MatrixD B, MatrixD C);

    /**
     * {@code C = alpha * A * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD mult(double alpha, MatrixD B, MatrixD C);

    /**
     * {@code C = A * B + C}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD multAdd(MatrixD B, MatrixD C);

    /**
     * {@code C = alpha * A * B + C}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD multAdd(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transABmult(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transABmult(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A<sup>T</sup> * B</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transAmult(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transAmult(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transBmult(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transBmult(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transABmultAdd(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transABmultAdd(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A<sup>T</sup> * B + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transAmultAdd(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transAmultAdd(double alpha, MatrixD B, MatrixD C);

    /**
     * <code>C = A * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transBmultAdd(MatrixD B, MatrixD C);

    /**
     * <code>C = alpha * A * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixD transBmultAdd(double alpha, MatrixD B, MatrixD C);

    MatrixD copy();

    MatrixD zeroInplace();

    MatrixD setInplace(MatrixD other);

    /**
     * {@code A = alpha * B}
     * @param alpha
     * @param B
     * @return {@code A}
     */
    MatrixD setInplace(double alpha, MatrixD other);

    double get(int row, int col);

    MatrixD set(int row, int col, double val);

    MatrixD add(int row, int col, double val);

    /**
     * Copy a submatrix of this matrix into {@code B}.
     * 
     * @param r0
     *            initial row index (left upper corner) in this matrix
     * @param c0
     *            initial col index (left upper corner) in this matrix
     * @param r1
     *            last row index (right lower corner) in this matrix
     * @param c1
     *            last col index (right lower corner) in this matrix
     * @param B
     *            matrix of dimension at least
     *            {@code (r1 - r0 + 1) x (c1 - c0 + 1)}
     * @param rb
     *            initial row index (left upper corner) in the matrix {@code B}
     * @param cb
     *            initial col index (left upper corner) in the matrix {@code B}
     * @return the submatrix {@code B}
     */
    MatrixD submatrix(int r0, int c0, int r1, int c1, MatrixD B, int rb, int cb);

    /**
     * Set a submatrix from the values of matrix {@code B} extending from
     * {@code (rb0, cb0)} to {@code (rb1, cb1)} (the upper left and lower right
     * corner in {@code B} respectively) at position {@code (r0, c0)} in this
     * matrix.
     * 
     * @param r0
     *            initial row index (left upper corner) in this matrix
     * @param c0
     *            initial col index (left upper corner) in this matrix
     * @param rb0
     *            initial row index (left upper corner) in the matrix {@code B}
     * @param cb0
     *            initial col index (left upper corner) in the matrix {@code B}
     * @param rb1
     *            last row index (right lower corner) in the matrix {@code B}
     * @param cb1
     *            last col index (right lower corner) in the matrix {@code B}
     * @param B
     *            the matrix that holds the values to set in this matrix
     * @return this matrix {@code A}
     */
    MatrixD setSubmatrixInplace(int r0, int c0, MatrixD B, int rb0, int cb0, int rb1, int cb1);

    /**
     * Copy into a jagged array. 
     * @return this matrix converted to a jagged array
     */
    double[][] toJaggedArray();

    /**
     * Frobenius norm
     * 
     * @return sqrt of sum of squares of all elements
     */
    double normF();

    double[] getArrayUnsafe();

    double getUnsafe(int row, int col);

    void setUnsafe(int row, int col, double val);
}
