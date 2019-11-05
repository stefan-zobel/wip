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

public abstract class DimensionsBase implements Dimensions {

    protected final int rows;
    protected final int cols;

    public DimensionsBase(int rows, int cols) {
        checkRows(rows);
        checkCols(cols);
        this.rows = rows;
        this.cols = cols;
    }

    @Override
    public boolean isScalar() {
        return rows == 1 && cols == 1;
    }

    @Override
    public boolean isColumnVector() {
        return cols == 1;
    }

    @Override
    public boolean isRowVector() {
        return rows == 1;
    }

    @Override
    public boolean isSquareMatrix() {
        return rows == cols;
    }

    @Override
    public int numColumns() {
        return cols;
    }

    @Override
    public int numRows() {
        return rows;
    }

    @Override
    public void checkIndex(int row, int col) {
        if (row < 0 || row >= rows) {
            throw new IllegalArgumentException("Illegal row index " + row + " in (" + rows + " x " + cols + ") matrix");
        }
        if (col < 0 || col >= cols) {
            throw new IllegalArgumentException(
                    "Illegal column index " + col + " in (" + rows + " x " + cols + ") matrix");
        }
    }

    @Override
    public void checkSubmatrixIndexes(int rFrom, int cFrom, int rTo, int cTo) {
        checkIndex(rFrom, cFrom);
        checkIndex(rTo, cTo);
        int _rows = rTo - rFrom + 1;
        int _cols = cTo - cFrom + 1;
        if (_rows <= 0 || _cols <= 0) {
            throw new IllegalArgumentException(
                    "Illegal submatrix indices : [" + rFrom + ", " + cFrom + ", " + rTo + ", " + cTo + "]");
        }
    }

    protected int idx(int row, int col) {
        return col * rows + row;
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
