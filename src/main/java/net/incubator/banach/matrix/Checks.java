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

import static net.incubator.banach.matrix.DimensionsBase.checkRows;
import static net.incubator.banach.matrix.DimensionsBase.checkCols;

final class Checks {

    static int checkArrayLength(int rows, int cols) {
        long length = (long) checkRows(rows) * (long) checkCols(cols);
        if (length > (long) Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "rows x cols (= " + length + ") exceeds the maximal possible length (= 4294967295) of an array");
        }
        return (int) length;
    }

    static void checkMult(Dimensions A, Dimensions B) {
        if (A.numColumns() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numRows() (" + A.numColumns() + " != " + B.numRows() + ")");
        }
    }

    static void checkTrans(Dimensions A, Dimensions AT) {
        if (A.numRows() != AT.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numColumns() (" + A.numRows() + " != " + AT.numColumns() + ")");
        }
        if (A.numColumns() != AT.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numRows() (" + A.numColumns() + " != " + AT.numRows() + ")");
        }
    }

    static void checkRequiredMinDimension(int rows, int cols, Dimensions X) {
        if (X.numRows() < rows) {
            throw new IndexOutOfBoundsException("X.numRows() < rows (" + X.numRows() + " < " + rows + ")");
        }
        if (X.numColumns() < cols) {
            throw new IndexOutOfBoundsException("X.numColumns() < cols (" + X.numColumns() + " < " + cols + ")");
        }
    }

    static void checkRequiredExactDimension(int rows, int cols, Dimensions X) {
        if (X.numRows() != rows) {
            throw new IndexOutOfBoundsException("X.numRows() != rows (" + X.numRows() + " != " + rows + ")");
        }
        if (X.numColumns() != cols) {
            throw new IndexOutOfBoundsException("X.numColumns() != cols (" + X.numColumns() + " != " + cols + ")");
        }
    }

    static void checkEqualDimension(Dimensions A, Dimensions B) {
        if (A.numRows() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numRows() (" + A.numRows() + " != " + B.numRows() + ")");
        }
        if (A.numColumns() != B.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numColumns() (" + A.numColumns() + " != " + B.numColumns() + ")");
        }
    }

    static void checkAdd(Dimensions A, Dimensions B, Dimensions C) {
        if (A.numRows() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numRows() (" + A.numRows() + " != " + B.numRows() + ")");
        }
        if (A.numColumns() != B.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numColumns() (" + A.numColumns() + " != " + B.numColumns() + ")");
        }
        if (B.numRows() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "B.numRows() != C.numRows() (" + B.numRows() + " != " + C.numRows() + ")");
        }
        if (B.numColumns() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numColumns() != C.numColumns() (" + B.numColumns() + " != " + C.numColumns() + ")");
        }
    }

    static void checkMultAdd(Dimensions A, Dimensions B, Dimensions C) {
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

    static void checkTransABmultAdd(Dimensions A, Dimensions B, Dimensions C) {
        if (A.numRows() != B.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numColumns() (" + A.numRows() + " != " + B.numColumns() + ")");
        }
        if (A.numColumns() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != C.numRows() (" + A.numColumns() + " != " + C.numRows() + ")");
        }
        if (B.numRows() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numRows() != C.numColumns() (" + B.numRows() + " != " + C.numColumns() + ")");
        }
    }

    static void checkTransAmultAdd(Dimensions A, Dimensions B, Dimensions C) {
        if (A.numRows() != B.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != B.numRows() (" + A.numRows() + " != " + B.numRows() + ")");
        }
        if (A.numColumns() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != C.numRows() (" + A.numColumns() + " != " + C.numRows() + ")");
        }
        if (B.numColumns() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numColumns() != C.numColumns() (" + B.numColumns() + " != " + C.numColumns() + ")");
        }
    }

    static void checkTransBmultAdd(Dimensions A, Dimensions B, Dimensions C) {
        if (A.numColumns() != B.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "A.numColumns() != B.numColumns() (" + A.numColumns() + " != " + B.numColumns() + ")");
        }
        if (A.numRows() != C.numRows()) {
            throw new IndexOutOfBoundsException(
                    "A.numRows() != C.numRows() (" + A.numRows() + " != " + C.numRows() + ")");
        }
        if (B.numRows() != C.numColumns()) {
            throw new IndexOutOfBoundsException(
                    "B.numRows() != C.numColumns() (" + B.numRows() + " != " + C.numColumns() + ")");
        }
    }

    static double[] checkJaggedArrayD(double[][] data) {
        int _rows = data.length;
        int _cols = data[0].length;
        if (_rows < 1 || _cols < 1) {
            throw new IllegalArgumentException(
                    "number of rows and columns must be strictly positive : (" + _rows + " x " + _cols + ")");
        }
        return new double[checkArrayLength(_rows, _cols)];
    }

    static float[] checkJaggedArrayF(float[][] data) {
        int _rows = data.length;
        int _cols = data[0].length;
        if (_rows < 1 || _cols < 1) {
            throw new IllegalArgumentException(
                    "number of rows and columns must be strictly positive : (" + _rows + " x " + _cols + ")");
        }
        return new float[checkArrayLength(_rows, _cols)];
    }

    static void throwInconsistentRowLengths(int cols, int rowIdx, int rowLength) {
        throw new IllegalArgumentException("All rows must have the same length: " + cols + " (row " + rowIdx
                + " has length " + rowLength + ")");
    }

    private Checks() {
        throw new AssertionError();
    }
}
