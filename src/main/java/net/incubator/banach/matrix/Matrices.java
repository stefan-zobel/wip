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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Static utility methods for matrices.
 */
public final class Matrices {

    public static MatrixD fromJaggedArrayD(double[][] data) {
        double[] copy = Checks.checkJaggedArrayD(data);
        int _rows = data.length;
        int _cols = data[0].length;
        for (int row = 0; row < _rows; ++row) {
            double[] row_i = data[row];
            if (row_i.length != _cols) {
                Checks.throwInconsistentRowLengths(_cols, row, row_i.length);
            }
            for (int col = 0; col < row_i.length; ++col) {
                copy[col * _rows + row] = row_i[col];
            }
        }
        return new SimpleMatrixD(_rows, _cols, copy);
    }

    public static MatrixF fromJaggedArrayF(float[][] data) {
        float[] copy = Checks.checkJaggedArrayF(data);
        int _rows = data.length;
        int _cols = data[0].length;
        for (int row = 0; row < _rows; ++row) {
            float[] row_i = data[row];
            if (row_i.length != _cols) {
                Checks.throwInconsistentRowLengths(_cols, row, row_i.length);
            }
            for (int col = 0; col < row_i.length; ++col) {
                copy[col * _rows + row] = row_i[col];
            }
        }
        return new SimpleMatrixF(_rows, _cols, copy);
    }

    public static MatrixD identityD(int n) {
        SimpleMatrixD m = new SimpleMatrixD(n, n);
        for (int i = 0; i < n; ++i) {
            m.set(i, i, 1.0);
        }
        return m;
    }

    public static MatrixF identityF(int n) {
        SimpleMatrixF m = new SimpleMatrixF(n, n);
        for (int i = 0; i < n; ++i) {
            m.set(i, i, 1.0f);
        }
        return m;
    }

    public static MatrixD randomUniformD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextDouble();
        }
        return m;
    }

    public static MatrixF randomUniformF(int rows, int cols) {
        SimpleMatrixF m = new SimpleMatrixF(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextFloat();
        }
        return m;
    }

    public static MatrixD randomNormalD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextGaussian();
        }
        return m;
    }

    public static MatrixF randomNormalF(int rows, int cols) {
        SimpleMatrixF m = new SimpleMatrixF(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = (float) rnd.nextGaussian();
        }
        return m;
    }

    /**
     * Useful for tests.
     * 
     * @param rows
     * @param cols
     * @return matrix filled with the natural numbers starting with 1 in
     *         row-major order
     */
    public static MatrixD naturalNumbersD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        int nat = 1;
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < cols; ++j) {
                m.set(i, j, nat++);
            }
        }
        return m;
    }

    /**
     * Useful for tests.
     * 
     * @param rows
     * @param cols
     * @return matrix filled with the natural numbers starting with 1 in
     *         row-major order
     */
    public static MatrixF naturalNumbersF(int rows, int cols) {
        SimpleMatrixF m = new SimpleMatrixF(rows, cols);
        int nat = 1;
        for (int i = 0; i < rows; ++i) {
            for (int j = 0; j < cols; ++j) {
                m.set(i, j, nat++);
            }
        }
        return m;
    }

    private Matrices() {
        throw new AssertionError();
    }
}
