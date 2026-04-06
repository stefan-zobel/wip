/*
 * Copyright 2024 - 2026 Stefan Zobel
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
package blis.gemm;

import java.util.Arrays;

/**
 * A minimal matrix of doubles that can't do much more than addition,
 * subtraction and multiplication.
 */
public class DMatrix {

    protected final int rows;
    protected final int cols;
    protected final double[] a;

    public static DMatrix identity(int dim) {
        DMatrix I = new DMatrix(dim, dim);
        for (int i = 0; i < dim; ++i) {
            I.setUnsafe(i, i, 1.0);
        }
        return I;
    }

    public static DMatrix diag(int dim, double value) {
        return identity(dim).scaleInplace(value);
    }

    public DMatrix(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.a = new double[checkArrayLength(rows, cols)];
    }

    public DMatrix(DMatrix other) {
        this(other.rows, other.cols, Arrays.copyOf(other.a, other.a.length));
    }

    protected DMatrix(int rows, int cols, double[] a) {
        this.rows = rows;
        this.cols = cols;
        this.a = a;
    }

    public DMatrix copy() {
        return new DMatrix(this);
    }

    public int numColumns() {
        return cols;
    }

    public int numRows() {
        return rows;
    }

    public boolean isSquareMatrix() {
        return rows == cols;
    }

    public double get(int row, int col) {
        checkIndex(row, col);
        return a[idx(row, col)];
    }

    public double getUnsafe(int row, int col) {
        return a[idx(row, col)];
    }

    public DMatrix set(int row, int col, double val) {
        checkIndex(row, col);
        a[idx(row, col)] = val;
        return this;
    }

    public void setUnsafe(int row, int col, double val) {
        a[idx(row, col)] = val;
    }

    public double[] getArrayUnsafe() {
        return a;
    }

    public DMatrix scale(double alpha) {
        return scale(alpha, new DMatrix(rows, cols));
    }

    public DMatrix scaleInplace(double alpha) {
        return scale(alpha, this);
    }

    private DMatrix scale(double alpha, DMatrix target) {
        double[] _a = a;
        double[] _b = target.a;
        for (int i = 0; i < _b.length; ++i) {
            _b[i] = alpha * _a[i];
        }
        return target;
    }

    public DMatrix abs() {
        return abs(new DMatrix(rows, cols));
    }

    public DMatrix absInplace() {
        return abs(this);
    }

    private DMatrix abs(DMatrix target) {
        double[] _a = a;
        double[] _b = target.a;
        for (int i = 0; i < _b.length; ++i) {
            _b[i] = Math.abs(_a[i]);
        }
        return target;
    }

    public DMatrix transpose() {
        if (rows == 1 || cols == 1) {
            return new DMatrix(cols, rows, Arrays.copyOf(a, a.length));
        }
        DMatrix AT = new DMatrix(cols, rows);
        int cols_ = cols;
        int rows_ = rows;
        for (int col = 0; col < cols_; ++col) {
            for (int row = 0; row < rows_; ++row) {
                AT.setUnsafe(col, row, getUnsafe(row, col));
            }
        }
        return AT;
    }

    public DMatrix add(DMatrix B) {
        checkEqualDimension(this, B);
        return add(B, new DMatrix(rows, cols));
    }

    public DMatrix addInplace(DMatrix B) {
        checkEqualDimension(this, B);
        return add(B, this);
    }

    private DMatrix add(DMatrix B, DMatrix target) {
        double[] _a = a;
        double[] _b = B.a;
        double[] _c = target.a;
        for (int i = 0; i < _a.length; ++i) {
            _c[i] = _a[i] + _b[i];
        }
        return target;
    }

    public DMatrix addBroadcastedVector(DMatrix B) {
        checkSameRows(this, B);
        return addBroadcastedVector(B, new DMatrix(rows, cols));
    }

    public DMatrix addBroadcastedVectorInplace(DMatrix B) {
        checkSameRows(this, B);
        return addBroadcastedVector(B, this);
    }

    private DMatrix addBroadcastedVector(DMatrix B, DMatrix target) {
        if (this.cols == B.cols) {
            return add(B, target);
        }
        if (B.numColumns() == 1) {
            double[] _a = a;
            double[] _b = B.a;
            double[] _c = target.a;
            int cols_ = cols;
            int rows_ = rows;
            for (int col = 0; col < cols_; ++col) {
                for (int row = 0; row < rows_; ++row) {
                    _c[idx(row, col)] = _a[idx(row, col)] + _b[row];
                }
            }
            return target;
        }
        // incompatible dimensions
        throw getSameColsException(this, B);
    }

    public DMatrix minus(DMatrix B) {
        checkEqualDimension(this, B);
        DMatrix C = new DMatrix(rows, cols);
        double[] _a = a;
        double[] _b = B.a;
        double[] _c = C.a;
        for (int i = 0; i < _a.length; ++i) {
            _c[i] = _a[i] - _b[i];
        }
        return C;
    }

    public DMatrix mul(DMatrix B) {
        checkMul(this, B);
        DMatrix C = new DMatrix(this.rows, B.cols);
        Dgemm.dgemm(true, true, C.rows, C.cols, cols, 1.0, a, 0, rows, B.a, 0, B.rows, 0.0, C.a, 0,
                C.rows);
        return C;
    }

    public DMatrix mulBLIS(DMatrix B) {
        checkMul(this, B);
        DMatrix C = new DMatrix(this.rows, B.cols);
        Dgemm.dgemmBLIS(true, true, C.rows, C.cols, cols, 1.0, a, 0, rows, B.a, 0, B.rows, 0.0, C.a, 0,
                C.rows);
        return C;
    }

    public DMatrix mulBTrans(DMatrix B) {
        checkMulBTrans(this, B);
        DMatrix C = new DMatrix(this.rows, B.rows);
        Dgemm.dgemm(true, false, C.rows, C.cols, cols, 1.0, a, 0, rows, B.a, 0, B.rows, 0.0, C.a, 0,
                C.rows);
        return C;
    }

    public DMatrix mulBTransBLIS(DMatrix B) {
        checkMulBTrans(this, B);
        DMatrix C = new DMatrix(this.rows, B.rows);
        Dgemm.dgemmBLIS(true, false, C.rows, C.cols, cols, 1.0, a, 0, rows, B.a, 0, B.rows, 0.0, C.a, 0,
                C.rows);
        return C;
    }

    @Override
    public String toString() {
        return toString(this);
    }

    public static boolean approximatelyEquals(DMatrix A, DMatrix B, double absTol) {
        return approximatelyEquals(A, B, 1.0e-8, absTol);
    }

    private static boolean approximatelyEquals(DMatrix A, DMatrix B, double relTol, double absTol) {
        if (A.numRows() != B.numRows() || A.numColumns() != B.numColumns()) {
            return false;
        }
        if (absTol < 0.0 || Double.isNaN(absTol) || Double.isInfinite(absTol)) {
            throw new IllegalArgumentException("illegal absTol : " + absTol);
        }
        if (A == B) {
            return true;
        }
        double[] _a = A.getArrayUnsafe();
        double[] _b = B.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            double a = _a[i];
            double b = _b[i];
            if (a != b) {
                double diff = Math.abs(a - b);
                if (!((diff <= relTol * Math.max(Math.abs(a), Math.abs(b))) || (diff <= absTol))) {
                    return false;
                }
            }
        }
        return true;
    }

    protected final int idx(int row, int col) {
        return col * rows + row;
    }

    protected void checkIndex(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IllegalArgumentException("Illegal row index " + row + " in (" + rows + " x " + cols + ") matrix");
        }
        if (col < 0 || col >= cols) {
            throw new IllegalArgumentException(
                    "Illegal column index " + col + " in (" + rows + " x " + cols + ") matrix");
        }
    }

    protected static String toString(DMatrix mat) {
        StringBuilder buf = new StringBuilder();
        buf.append("(").append(mat.rows).append(" x ").append(mat.cols).append(")").append(System.lineSeparator());
        int _cols = mat.numColumns() <= 6 ? mat.numColumns() : 5;
        int _rows = mat.numRows() <= 6 ? mat.numRows() : 5;
        int row;
        for (row = 0; row < _rows; ++row) {
            printRowD(row, _cols, mat, buf);
        }
        if (row == 5 && _rows < mat.numRows()) {
            int empty = _cols < mat.numColumns() ? 6 : mat.numColumns();
            for (int i = 0; i < empty; ++i) {
                buf.append("......");
                if (i != empty - 1) {
                    buf.append(", ");
                }
            }
            buf.append(System.lineSeparator());
            printRowD(mat.numRows() - 1, _cols, mat, buf);
        }
        return buf.toString();
    }

    private static void printRowD(int row, int _cols, DMatrix m, StringBuilder buf) {
        String format = "%.12E";
        int col;
        for (col = 0; col < _cols; ++col) {
            buf.append(String.format(format, m.getUnsafe(row, col)));
            if (col < _cols - 1) {
                buf.append(", ");
            }
        }
        if (col == 5 && _cols < m.numColumns()) {
            buf.append(", ......, ");
            buf.append(String.format(format, m.getUnsafe(row, m.numColumns() - 1)));
        }
        buf.append(System.lineSeparator());
    }

    protected static void checkSameRows(DMatrix A, DMatrix B) {
        if (A.numRows() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numRows() (" + A.numRows() + " != " + B.numRows() + ")");
        }
    }

    protected static void checkSameCols(DMatrix A, DMatrix B) {
        if (A.numColumns() != B.numColumns()) {
            throw getSameColsException(A, B);
        }
    }

    protected static void checkEqualDimension(DMatrix A, DMatrix B) {
        checkSameRows(A, B);
        checkSameCols(A, B);
    }

    protected static void checkMul(DMatrix A, DMatrix B) {
        if (A.numColumns() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numRows() (" + A.numColumns() + " != " + B.numRows() + ")");
        }
    }

    protected static void checkMulBTrans(DMatrix A, DMatrix B) {
        checkSameCols(A, B);
    }

    protected static void checkMul(DMatrix A, DMatrix B, DMatrix C) {
        if (A.numRows() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != C.numRows() (" + A.numRows() + " != " + C.numRows() + ")");
        }
        if (A.numColumns() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numRows() (" + A.numColumns() + " != " + B.numRows() + ")");
        }
        if (B.numColumns() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numColumns() != C.numColumns() (" + B.numColumns() + " != " + C.numColumns() + ")");
        }
    }

    protected static void checkAdd(DMatrix A, DMatrix B, DMatrix C) {
        checkEqualDimension(A, B);
        if (B.numRows() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "B.numRows() != C.numRows() (" + B.numRows() + " != " + C.numRows() + ")");
        }
        if (B.numColumns() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numColumns() != C.numColumns() (" + B.numColumns() + " != " + C.numColumns() + ")");
        }
    }

    protected static IndexOutOfBoundsException getSameColsException(DMatrix A, DMatrix B) {
        return new IndexOutOfBoundsException(
                "A.numColumns() != B.numColumns() (" + A.numColumns() + " != " + B.numColumns() + ")");
    }

    protected static int checkArrayLength(int rows, int cols) {
        long length = (long) checkRows(rows) * (long) checkCols(cols);
        if (length > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "rows x cols (= " + length + ") exceeds the maximal possible length (= 2147483647) of an array");
        }
        return (int) length;
    }

    protected static int checkRows(int rows) {
        if (rows <= 0) {
            throw new IllegalArgumentException("number of rows must be strictly positive : " + rows);
        }
        return rows;
    }

    protected static int checkCols(int cols) {
        if (cols <= 0) {
            throw new IllegalArgumentException("number of columns must be strictly positive : " + cols);
        }
        return cols;
    }
}
