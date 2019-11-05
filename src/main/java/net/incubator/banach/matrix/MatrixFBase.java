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

import java.util.Arrays;

/**
 * A {@code MatrixFBase} is a partial implementation of a dense matrix of
 * primitive floats with column-major storage layout. The addressing is zero
 * based. All operations throw a {@code NullPointerException} if any of the
 * method arguments is {@code null}.
 * <p>
 * <b>Note: this is experimental, unfinished and completely untested code!</b>
 */
public abstract class MatrixFBase extends DimensionsBase implements MatrixF {

    protected final float[] a;

    public MatrixFBase(int rows, int cols, float[] array, boolean doArrayCopy) {
        super(rows, cols);
        checkArrayLength(array, rows, cols);
        if (doArrayCopy) {
            float[] copy = new float[array.length];
            System.arraycopy(array, 0, copy, 0, copy.length);
            a = copy;
        } else {
            a = array;
        }
    }

    @Override
    public float toScalar() {
        if (!isScalar()) {
            throw new IllegalStateException("(" + rows + " x " + cols + ") matrix is not a scalar");
        }
        return a[0];
    }

    /**
     * {@code A = alpha * A}
     * 
     * @param alpha
     * @return {@code A}
     */
    @Override
    public MatrixF scaleInplace(float alpha) {
        if (alpha == 0.0f) {
            return this.zeroInplace();
        }
        if (alpha == 1.0f) {
            return this;
        }
        float[] _a = a;
        for (int i = 0; i < _a.length; ++i) {
            _a[i] *= alpha;
        }
        return this;
    }

    /**
     * {@code B = alpha * A}
     * 
     * @param alpha
     * @param B
     * @return {@code B}
     */
    @Override
    public MatrixF scale(float alpha, MatrixF B) {
        Checks.checkEqualDimension(this, B);
        if (alpha == 0.0f) {
            Arrays.fill(B.getArrayUnsafe(), 0.0f);
            return B;
        }
        float[] _a = a;
        float[] _b = B.getArrayUnsafe();
        for (int i = 0; i < _b.length; ++i) {
            _b[i] = alpha * _a[i];
        }
        return B;
    }

    /**
     * <code>AT = A<sup>T</sup></code>
     * 
     * @param AT
     * @return {@code AT}
     */
    @Override
    public MatrixF trans(MatrixF AT) {
        Checks.checkTrans(this, AT);
        int cols_ = cols;
        int rows_ = rows;
        for (int col = 0; col < cols_; ++col) {
            for (int row = 0; row < rows_; ++row) {
                AT.setUnsafe(col, row, getUnsafe(row, col));
            }
        }
        return AT;
    }

    /**
     * {@code A = A + B}
     * 
     * @param B
     * @return {@code A}
     */
    @Override
    public MatrixF addInplace(MatrixF B) {
        return addInplace(1.0f, B);
    }

    /**
     * {@code A = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @return {@code A}
     */
    @Override
    public MatrixF addInplace(float alpha, MatrixF B) {
        Checks.checkEqualDimension(this, B);
        if (alpha != 0.0f) {
            float[] _a = a;
            float[] _b = B.getArrayUnsafe();
            for (int i = 0; i < _b.length; ++i) {
                _a[i] += alpha * _b[i];
            }
        }
        return this;
    }

    /**
     * {@code C = A + B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF add(MatrixF B, MatrixF C) {
        return add(1.0f, B, C);
    }

    /**
     * {@code C = A + alpha * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF add(float alpha, MatrixF B, MatrixF C) {
        Checks.checkAdd(this, B, C);
        if (alpha == 0.0f) {
            System.arraycopy(a, 0, C.getArrayUnsafe(), 0, a.length);
        } else {
            float[] _a = a;
            float[] _b = B.getArrayUnsafe();
            float[] _c = C.getArrayUnsafe();
            for (int i = 0; i < _a.length; ++i) {
                _c[i] = _a[i] + alpha * _b[i];
            }
        }
        return C;
    }

    /**
     * {@code C = A * B}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF mult(MatrixF B, MatrixF C) {
        return mult(1.0f, B, C);
    }

    /**
     * {@code C = alpha * A * B}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF mult(float alpha, MatrixF B, MatrixF C) {
        return multAdd(alpha, B, C.zeroInplace());
    }

    /**
     * {@code C = A * B + C}
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF multAdd(MatrixF B, MatrixF C) {
        return multAdd(1.0f, B, C);
    }

    /**
     * {@code C = alpha * A * B + C}
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public abstract MatrixF multAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transABmult(MatrixF B, MatrixF C) {
        return transABmult(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transABmult(float alpha, MatrixF B, MatrixF C) {
        return transABmultAdd(alpha, B, C.zeroInplace());
    }

    /**
     * <code>C = A<sup>T</sup> * B</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transAmult(MatrixF B, MatrixF C) {
        return transAmult(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A<sup>T</sup> * B</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transAmult(float alpha, MatrixF B, MatrixF C) {
        return transAmultAdd(alpha, B, C.zeroInplace());
    }

    /**
     * <code>C = A * B<sup>T</sup></code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transBmult(MatrixF B, MatrixF C) {
        return transBmult(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A * B<sup>T</sup></code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transBmult(float alpha, MatrixF B, MatrixF C) {
        return transBmultAdd(alpha, B, C.zeroInplace());
    }

    /**
     * <code>C = A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transABmultAdd(MatrixF B, MatrixF C) {
        return transABmultAdd(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A<sup>T</sup> * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public abstract MatrixF transABmultAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A<sup>T</sup> * B + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transAmultAdd(MatrixF B, MatrixF C) {
        return transAmultAdd(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A<sup>T</sup> * B + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public abstract MatrixF transAmultAdd(float alpha, MatrixF B, MatrixF C);

    /**
     * <code>C = A * B<sup>T</sup> + C</code>
     * 
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public MatrixF transBmultAdd(MatrixF B, MatrixF C) {
        return transBmultAdd(1.0f, B, C);
    }

    /**
     * <code>C = alpha * A * B<sup>T</sup> + C</code>
     * 
     * @param alpha
     * @param B
     * @param C
     * @return {@code C}
     */
    @Override
    public abstract MatrixF transBmultAdd(float alpha, MatrixF B, MatrixF C);

    @Override
    public MatrixF zeroInplace() {
        Arrays.fill(a, 0.0f);
        return this;
    }

    @Override
    public MatrixF setInplace(MatrixF other) {
        return setInplace(1.0f, other);
    }

    @Override
    public MatrixF setInplace(float alpha, MatrixF other) {
        Checks.checkEqualDimension(this, other);
        if (alpha == 0.0f) {
            return zeroInplace();
        }
        if (other == this) {
            return scaleInplace(alpha);
        }
        float[] _a = a;
        float[] _b = other.getArrayUnsafe();
        for (int i = 0; i < _b.length; ++i) {
            _a[i] = alpha * _b[i];
        }
        return this;
    }

    @Override
    public MatrixF submatrix(int r0, int c0, int r1, int c1, MatrixF B, int rb, int cb) {
        checkSubmatrixIndexes(r0, c0, r1, c1);
        B.checkIndex(rb, cb);
        B.checkIndex(rb + r1 - r0, cb + c1 - c0);
        int rbStart = rb;
        for (int col = c0; col <= c1; ++col) {
            for (int row = r0; row <= r1; ++row) {
                B.setUnsafe(rb++, cb, this.getUnsafe(row, col));
            }
            rb = rbStart;
            cb++;
        }
        return B;
    }

    @Override
    public MatrixF setSubmatrixInplace(int r0, int c0, MatrixF B, int rb0, int cb0, int rb1, int cb1) {
        B.checkSubmatrixIndexes(rb0, cb0, rb1, cb1);
        checkIndex(r0, c0);
        checkIndex(r0 + rb1 - rb0, c0 + cb1 - cb0);
        int r0Start = r0;
        for (int col = cb0; col <= cb1; ++col) {
            for (int row = rb0; row <= rb1; ++row) {
                this.setUnsafe(r0++, c0, B.getUnsafe(row, col));
            }
            r0 = r0Start;
            c0++;
        }
        return this;
    }

    @Override
    public float get(int row, int col) {
        checkIndex(row, col);
        return a[idx(row, col)];
    }

    public float getUnsafe(int row, int col) {
        return a[idx(row, col)];
    }

    @Override
    public MatrixF set(int row, int col, float val) {
        checkIndex(row, col);
        a[idx(row, col)] = val;
        return this;
    }

    public void setUnsafe(int row, int col, float val) {
        a[idx(row, col)] = val;
    }

    @Override
    public MatrixF add(int row, int col, float val) {
        checkIndex(row, col);
        a[idx(row, col)] += val;
        return this;
    }

    protected void addUnsafe(int row, int col, float val) {
        a[idx(row, col)] += val;
    }

    @Override
    public float[] getArrayUnsafe() {
        return a;
    }

    @Override
    public float[][] toJaggedArray() {
        int _rows = rows;
        int _cols = cols;
        float[] _a = a;
        float[][] copy = new float[_rows][_cols];
        for (int row = 0; row < _rows; ++row) {
            float[] row_i = copy[row];
            for (int col = 0; col < row_i.length; ++col) {
                row_i[col] = _a[col * _rows + row];
            }
        }
        return copy;
    }

    @Override
    public float normF() {
        // overflow resistant implementation
        double scale = 0.0;
        double sumsquared = 1.0;
        float[] _a = a;
        for (int i = 0; i < _a.length; ++i) {
            double xi = _a[i];
            if (xi != 0.0) {
                double absxi = Math.abs(xi);
                if (scale < absxi) {
                    double unsquared = scale / absxi;
                    sumsquared = 1.0 + sumsquared * (unsquared * unsquared);
                    scale = absxi;
                } else {
                    double unsquared = absxi / scale;
                    sumsquared = sumsquared + (unsquared * unsquared);
                }
            }
        }
        return (float) (scale * Math.sqrt(sumsquared));
    }

    protected abstract MatrixF create(int rows, int cols, float[] data);

    protected static void checkArrayLength(float[] array, int rows, int cols) {
        if (array.length != rows * cols) {
            throw new IllegalArgumentException(
                    "data array has wrong length. Needed : " + rows * cols + " , Is : " + array.length);
        }
    }
}
