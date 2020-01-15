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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Static utility methods for matrices.
 */
public final class Matrices {

    /**
     * Create a new {@link MatrixD} of dimension {@code (row, cols)}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number of columns
     * @return a {@code MatrixD} of dimension {@code (row, cols)}
     */
    public static MatrixD createD(int rows, int cols) {
        return new SimpleMatrixD(rows, cols);
    }

    /**
     * Create a new {@link MatrixF} of dimension {@code (row, cols)}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number of columns
     * @return a {@code MatrixF} of dimension {@code (row, cols)}
     */
    public static MatrixF createF(int rows, int cols) {
        return new SimpleMatrixF(rows, cols);
    }

    /**
     * Create a {@link MatrixD} from a {@code double[][]} array. The elements of
     * {@code data} get copied, i.e. the array is not referenced.
     * <p>
     * The first index of {@code data} is interpreted as the row index. Note
     * that all rows must have the same length otherwise an
     * IllegalArgumentException is thrown.
     * 
     * @param data
     *            array whose shape and content determines the shape and content
     *            of the newly created matrix
     * @return a {@code MatrixD} of the same shape as {@code data} filled with
     *         the content of {@code data}.
     * @throws IllegalArgumentException
     *             if not all rows have the same length
     */
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

    /**
     * Create a {@link MatrixF} from a {@code float[][]} array. The elements of
     * {@code data} get copied, i.e. the array is not referenced.
     * <p>
     * The first index of {@code data} is interpreted as the row index. Note
     * that all rows must have the same length otherwise an
     * IllegalArgumentException is thrown.
     * 
     * @param data
     *            array whose shape and content determines the shape and content
     *            of the newly created matrix
     * @return a {@code MatrixF} of the same shape as {@code data} filled with
     *         the content of {@code data}.
     * @throws IllegalArgumentException
     *             if not all rows have the same length
     */
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

    /**
     * Create an MatrixD identity matrix of dimension {@code (n, n)}.
     * 
     * @param n
     *            dimension of the quadratic identity matrix
     * @return MatrixD identity matrix of dimension {@code (n, n)}
     */
    public static MatrixD identityD(int n) {
        SimpleMatrixD m = new SimpleMatrixD(n, n);
        for (int i = 0; i < n; ++i) {
            m.set(i, i, 1.0);
        }
        return m;
    }

    /**
     * Create an MatrixF identity matrix of dimension {@code (n, n)}.
     * 
     * @param n
     *            dimension of the quadratic identity matrix
     * @return MatrixF identity matrix of dimension {@code (n, n)}
     */
    public static MatrixF identityF(int n) {
        SimpleMatrixF m = new SimpleMatrixF(n, n);
        for (int i = 0; i < n; ++i) {
            m.set(i, i, 1.0f);
        }
        return m;
    }

    /**
     * Create a MatrixD of dimension {@code (rows, cols)} filled with uniformly
     * distributed random numbers drawn from the range {@code [0.0, 1.0)}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return {@code (rows, cols)} MatrixD filled with {@code ~U[0, 1]}
     *         distributed random numbers
     */
    public static MatrixD randomUniformD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextDouble();
        }
        return m;
    }

    /**
     * Create a MatrixF of dimension {@code (rows, cols)} filled with uniformly
     * distributed random numbers drawn from the range {@code [0.0f, 1.0f)}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return {@code (rows, cols)} MatrixF filled with {@code ~U[0, 1]}
     *         distributed random numbers
     */
    public static MatrixF randomUniformF(int rows, int cols) {
        SimpleMatrixF m = new SimpleMatrixF(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        float[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextFloat();
        }
        return m;
    }

    /**
     * Create a MatrixD of dimension {@code (rows, cols)} filled with normally
     * distributed (i.e., standard gausssian) random numbers with expectation
     * {@code 0.0} and variance {@code 1.0}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return {@code (rows, cols)} MatrixD filled with {@code ~N[0, 1]}
     *         distributed random numbers (standard normal distribution)
     */
    public static MatrixD randomNormalD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double[] _a = m.getArrayUnsafe();
        for (int i = 0; i < _a.length; ++i) {
            _a[i] = rnd.nextGaussian();
        }
        return m;
    }

    /**
     * Create a MatrixF of dimension {@code (rows, cols)} filled with normally
     * distributed (i.e., standard gausssian) random numbers with expectation
     * {@code 0.0f} and variance {@code 1.0f}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return {@code (rows, cols)} MatrixF filled with {@code ~N[0, 1]}
     *         distributed random numbers (standard normal distribution)
     */
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
     * Create a MatrixD of dimension {@code (rows, cols)} whose elements are all
     * {@code 1.0}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return MatrixD of dimension {@code (rows, cols)} filled with ones
     */
    public static MatrixD onesD(int rows, int cols) {
        SimpleMatrixD m = new SimpleMatrixD(rows, cols);
        Arrays.fill(m.getArrayUnsafe(), 1.0);
        return m;
    }

    /**
     * Create a MatrixF of dimension {@code (rows, cols)} whose elements are all
     * {@code 1.0f}.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
     * @return MatrixD of dimension {@code (rows, cols)} filled with ones
     */
    public static MatrixF onesF(int rows, int cols) {
        SimpleMatrixF m = new SimpleMatrixF(rows, cols);
        Arrays.fill(m.getArrayUnsafe(), 1.0f);
        return m;
    }

    /**
     * Create a MatrixD of dimension {@code (rows, cols)} filled with the
     * natural numbers starting with 1 in row-major order. This is mainly useful
     * for tests.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
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
     * Create a MatrixF of dimension {@code (rows, cols)} filled with the
     * natural numbers starting with 1 in row-major order. This is mainly useful
     * for tests.
     * 
     * @param rows
     *            number or rows
     * @param cols
     *            number or columns
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

    /**
     * Create a new zero matrix of same dimension as {@code md}.
     * 
     * @param md
     *            {@code MatrixD} template for the dimensions to use for the new
     *            matrix
     * @return new zero matrix of same dimension as {@code md}
     */
    public static MatrixD sameDimD(MatrixD md) {
        return new SimpleMatrixD(md.numRows(), md.numColumns());
    }

    /**
     * Create a new zero matrix of same dimension as {@code mf}.
     * 
     * @param mf
     *            {@code MatrixF} template for the dimensions to use for the new
     *            matrix
     * @return new zero matrix of same dimension as {@code mf}
     */
    public static MatrixF sameDimF(MatrixF mf) {
        return new SimpleMatrixF(mf.numRows(), mf.numColumns());
    }

    public static long serializeF(MatrixF mf, Path file) throws IOException {
        //@formatter:off
        try (OutputStream os = Files.newOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(os, BUF_SIZE)
        )
        {
            long sz = serializeF(mf, bos);
            bos.flush();
            return sz;
        }
        //@formatter:on
    }

    public static long serializeF(MatrixF mf, OutputStream os) throws IOException {
        byte[] buf = new byte[4];
        long sz = IO.writeMatrixHeaderB(mf.numRows(), mf.numColumns(), Float.SIZE, buf, os);
        float[] data = mf.getArrayUnsafe();
        for (int i = 0; i < data.length; ++i) {
            sz += IO.putFloatB(data[i], buf, os);
        }
        return sz;
    }

    public static long serializeD(MatrixD md, Path file) throws IOException {
        //@formatter:off
        try (OutputStream os = Files.newOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(os, BUF_SIZE)
        )
        {
            long sz = serializeD(md, bos);
            bos.flush();
            return sz;
        }
        //@formatter:on
    }

    public static long serializeD(MatrixD md, OutputStream os) throws IOException {
        byte[] buf = new byte[8];
        long sz = IO.writeMatrixHeaderB(md.numRows(), md.numColumns(), Double.SIZE, buf, os);
        double[] data = md.getArrayUnsafe();
        for (int i = 0; i < data.length; ++i) {
            sz += IO.putDoubleB(data[i], buf, os);
        }
        return sz;
    }

    public static MatrixF deserializeF(Path file) throws IOException {
        //@formatter:off
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUF_SIZE)
        )
        {
            return deserializeF(bis);
        }
        //@formatter:on
    }

    public static MatrixF deserializeF(InputStream is) throws IOException {
        byte[] buf = new byte[4];
        checkBigendian(IO.isBigendian(buf, is));
        if (IO.isDoubleType(buf, is)) {
            throw new IOException("Unexpected MatrixD. Use deserializeD() instead.");
        }
        int rows = IO.readRows(true, buf, is);
        int cols = IO.readCols(true, buf, is);
        MatrixF mf = createF(rows, cols);
        float[] data = mf.getArrayUnsafe();
        for (int i = 0; i < data.length; ++i) {
            data[i] = IO.getFloatB(buf, is);
        }
        return mf;
    }

    public static MatrixD deserializeD(Path file) throws IOException {
        //@formatter:off
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, BUF_SIZE)
        )
        {
            return deserializeD(bis);
        }
        //@formatter:on
    }

    public static MatrixD deserializeD(InputStream is) throws IOException {
        byte[] buf = new byte[8];
        checkBigendian(IO.isBigendian(buf, is));
        if (!IO.isDoubleType(buf, is)) {
            throw new IOException("Unexpected MatrixF. Use deserializeF() instead.");
        }
        int rows = IO.readRows(true, buf, is);
        int cols = IO.readCols(true, buf, is);
        MatrixD md = createD(rows, cols);
        double[] data = md.getArrayUnsafe();
        for (int i = 0; i < data.length; ++i) {
            data[i] = IO.getDoubleB(buf, is);
        }
        return md;
    }

    private static void checkBigendian(boolean isBigendian) throws IOException {
        if (!isBigendian) {
            throw new IOException("Unexpected little endian storage format");
        }
    }

    // 256k
    private static final int BUF_SIZE = 1 << 18;

    private Matrices() {
        throw new AssertionError();
    }
}
