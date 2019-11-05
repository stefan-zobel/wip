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
 * A {@code MatrixF} is a dense matrix of primitive floats with column-major
 * storage layout. The addressing is zero based. All operations throw a
 * {@code NullPointerException} if any of the method arguments is {@code null}.
 */
public interface MatrixF extends Dimensions {

    float toScalar();

    /**
     * {@code A = alpha * A}
     * 
     * @param alpha
     * @return {@code A}
     */
    MatrixF scaleInplace(float alpha);

    /**
     * {@code B = alpha * A}
     * 
     * @param alpha
     * @param B
     * @return {@code B}
     */
    MatrixF scale(float alpha, MatrixF B);

    /**
     * <code>AT = A<sup>T</sup></code>
     * 
     * @param AT
     * @return {@code AT}
     */
    MatrixF trans(MatrixF AT);

    /**
     * {@code A = A + B}
     * 
     * @param B
     * @return {@code A}
     */
    MatrixF addInplace(MatrixF B);

    /**
     * {@code A = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @return {@code A}
     */
    MatrixF addInplace(float alpha, MatrixF B);

    /**
     * {@code C = A + B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF add(MatrixF B, MatrixF C);

    /**
     * {@code C = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF add(float alpha, MatrixF B, MatrixF C);

    /**
     * {@code C = A * B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF mult(MatrixF B, MatrixF C);

    /**
     * {@code C = alpha * A * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF mult(float alpha, MatrixF B, MatrixF C);

    /**
     * {@code C = A * B + C}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF multAdd(MatrixF B, MatrixF C);

    /**
     * {@code C = alpha * A * B + C}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF multAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transABmult(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transABmult(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transAmult(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transAmult(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transBmult(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transBmult(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transABmultAdd(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transABmultAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transAmultAdd(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A<sup>T</sup> * B + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transAmultAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transBmultAdd(MatrixF B, MatrixF C);

    /**
     * <code>C = alpha * A * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    MatrixF transBmultAdd(float alpha, MatrixF B, MatrixF C);

    MatrixF copy();

    MatrixF zeroInplace();

    MatrixF setInplace(MatrixF other);

    /**
     * {@code A = alpha * B}
     * @param alpha
     * @param B
     * @return {@code A}
     */
    MatrixF setInplace(float alpha, MatrixF B);

    float get(int row, int col);

    MatrixF set(int row, int col, float val);

    MatrixF add(int row, int col, float val);

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
    MatrixF submatrix(int r0, int c0, int r1, int c1, MatrixF B, int rb, int cb);

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
    MatrixF setSubmatrixInplace(int r0, int c0, MatrixF B, int rb0, int cb0, int rb1, int cb1);

    /**
     * Copy into a jagged array. 
     * @return this matrix converted to a jagged array
     */
    float[][] toJaggedArray();

    /**
     * Frobenius norm
     * 
     * @return sqrt of sum of squares of all elements
     */
    float normF();

    float[] getArrayUnsafe();

    float getUnsafe(int row, int col);

    void setUnsafe(int row, int col, float val);
}
