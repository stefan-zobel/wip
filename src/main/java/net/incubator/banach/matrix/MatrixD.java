/*
 * Copyright 2019, 2020 Stefan Zobel
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

import net.frobenius.ComputationTruncatedException;
import net.frobenius.NotConvergedException;

/**
 * A {@code MatrixD} is a dense matrix of primitive doubles with column-major
 * storage layout. The addressing is zero based. All operations throw a
 * {@code NullPointerException} if any of the method arguments is {@code null}.
 */
public interface MatrixD extends Dimensions, DMatrixBasicOps {

    /**
     * Get the single element as a scalar if this matrix is 1-by-1.
     * 
     * @return the single element as a scalar if this matrix is 1-by-1
     * @throws IllegalStateException
     *             if this matrix is not 1-by-1
     */
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

    /**
     * Get a newly created copy of this matrix.
     * 
     * @return fresh copy of this matrix
     */
    MatrixD copy();

    /**
     * Set all elements of this matrix to {@code 0.0} mutating this matrix.
     * 
     * @return this matrix (mutated)
     */
    MatrixD zeroInplace();

    /**
     * Copy the {@code other} matrix into this matrix (mutating this matrix)
     * where the dimensions of {@code other} and {@code this} must be the same.
     * 
     * @param other
     *            matrix whose elements should be copied into this matrix
     * @return this matrix (mutated)
     */
    MatrixD setInplace(MatrixD other);

    /**
     * {@code A = alpha * B}
     * 
     * @param alpha
     * @param other
     * @return {@code A}
     */
    MatrixD setInplace(double alpha, MatrixD other);

    /**
     * Get the matrix element at {@code (row, col)}.
     * 
     * @param row
     *            row index, zero-based
     * @param col
     *            column index, zero-based
     * @return the matrix element at {@code (row, col)}
     */
    double get(int row, int col);

    /**
     * Set the matrix element at {@code (row, col)} to {@code val} mutating this
     * matrix.
     * 
     * @param row
     *            row index, zero-based
     * @param col
     *            column index, zero-based
     * @param val
     *            new value
     * @return this matrix (mutated)
     */
    MatrixD set(int row, int col, double val);

    /**
     * Add {@code val} to the matrix element at {@code (row, col)} mutating this
     * matrix.
     * 
     * @param row
     *            row index, zero-based
     * @param col
     *            column index, zero-based
     * @param val
     *            the value to add to the element at {@code (row, col)}
     * @return this matrix (mutated)
     */
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
     * Computes the solution ({@code X}) to a real system of linear equations
     * {@code A * X = B}, where {@code A} is either a {@code n x n} matrix and
     * {@code X} and {@code B} are {@code n x r} matrices, or where {@code A} is
     * a {@code n x m} and matrix {@code X} is a {@code m x r} matrix and
     * {@code B} is a {@code n x r} matrix.
     * 
     * @param B
     *            matrix with the same number of rows as this matrix {@code A},
     *            and the same number of columns as {@code X}
     * @param X
     *            matrix with number of rows equal to the number of columns of
     *            this matrix {@code A}, and the same number of columns as
     *            {@code B}
     * @return {@code X}, the solution of dimension either {@code n x r} (in the
     *         {@code n x n} case) or {@code m x r} (in the {@code m x n} case).
     * @throws ComputationTruncatedException
     *             for exactly singular factors in the LU decomposition of a
     *             quadratic matrix or for a non-quadratic matrix that doesn't
     *             have full rank
     */
    MatrixD solve(MatrixD B, MatrixD X);

    /**
     * Matrix inverse for quadratic matrices.
     * 
     * @param inverse
     *            matrix where the inverse is stored. Must have the same
     *            dimension as this matrix
     * @return the inverse matrix (i.e. the argument {@code inverse})
     * @throws IllegalArgumentException if this matrix is not quadratic or if
     *        {@code inverse} has the wrong dimension
     * @throws ComputationTruncatedException
     *             for exactly singular factors in the LU decomposition of
     *             this matrix
     */
    MatrixD inv(MatrixD inverse);

    /**
     * Compute the Moore-Penrose pseudoinverse.
     * 
     * @return the Moore-Penrose Pseudo-Inverse
     * @throws NotConvergedException
     *             if the singular value decomposition did not converge
     */
    MatrixD pseudoInv();

    /**
     * Computes the singular value decomposition of this matrix.
     * 
     * @param full
     *            controls whether the full decomposition should be computed (if
     *            {@code true}) or the singular values only (if {@code false})
     * @return the {@link SvdD} of this matrix, either full or the singular
     *         values only (if {@code full} is set to {@code false})
     * @throws NotConvergedException
     *             if the singular value decomposition did not converge
     */
    SvdD svd(boolean full);

    /**
     * Computes the eigenvalue decomposition of this matrix if it is quadratic.
     * 
     * @param full
     *            controls whether the (right) eigenvectors should be computed
     *            in addition (if {@code true}) or the eigenvalues only (if
     *            {@code false})
     * @return the {@link EvdD} of this matrix, either full or the eigenvalues
     *         only (if {@code full} is set to {@code false})
     * @throws IllegalArgumentException
     *             if this matrix is not quadratic
     * @throws ComputationTruncatedException
     *             if the QR decomposition failed to compute all eigenvalues
     */
    EvdD evd(boolean full);

    /**
     * Copy into a jagged array.
     * 
     * @return this matrix converted to a jagged array
     */
    double[][] toJaggedArray();

    /**
     * Frobenius norm
     * 
     * @return sqrt of sum of squares of all elements
     */
    double normF();

    /**
     * Two norm
     * 
     * @return maximum singular value
     * @throws NotConvergedException
     *             if the singular value decomposition did not converge
     */
    double norm2();

    /**
     * Matrix trace
     * 
     * @return sum of the diagonal elements
     */
    double trace();

    /**
     * Get the reference to the internal backing array without copying.
     * 
     * @return the reference to the internal backing array
     */
    double[] getArrayUnsafe();

    /**
     * Get the matrix element {@code (row, col)} without bounds checking.
     * 
     * @param row
     *            row index, zero-based
     * @param col
     *            column index, zero-based
     * @return the matrix element at {@code (row, col)}
     */
    double getUnsafe(int row, int col);

    /**
     * Set the matrix element at {@code (row, col)} to {@code val} without
     * bounds checking.
     * 
     * @param row
     *            row index, zero-based
     * @param col
     *            column index, zero-based
     * @param val
     *            new value
     */
    void setUnsafe(int row, int col, double val);
}
