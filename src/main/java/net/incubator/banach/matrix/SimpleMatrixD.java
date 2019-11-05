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

import net.dedekind.blas.Blas;
import net.frobenius.TTrans;

/**
 * <b>Note: this is experimental, unfinished and completely untested code!</b>
 */
public class SimpleMatrixD extends MatrixDBase implements MatrixD {

    private static final double BETA = 1.0;

    public SimpleMatrixD(int rows, int cols) {
        this(rows, cols, new double[Checks.checkArrayLength(rows, cols)]);
    }

    public SimpleMatrixD(int rows, int cols, double initialValue) {
        super(rows, cols, new double[Checks.checkArrayLength(rows, cols)], false);
        Arrays.fill(a, initialValue);
    }

    private SimpleMatrixD(SimpleMatrixD other) {
        super(other.rows, other.cols, other.a, true);
    }

    protected SimpleMatrixD(int rows, int cols, double[] data) {
        super(rows, cols, data, false);
    }

    @Override
    protected MatrixD create(int rows, int cols, double[] data) {
        return new SimpleMatrixD(rows, cols, data);
    }

    @Override
    public MatrixD multAdd(double alpha, MatrixD B, MatrixD C) {
        Checks.checkMultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.dgemm(TTrans.NO_TRANS.val(), TTrans.NO_TRANS.val(), C.numRows(), C.numColumns(), cols, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixD transABmultAdd(double alpha, MatrixD B, MatrixD C) {
        Checks.checkTransABmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.dgemm(TTrans.TRANS.val(), TTrans.TRANS.val(), C.numRows(), C.numColumns(), rows, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixD transAmultAdd(double alpha, MatrixD B, MatrixD C) {
        Checks.checkTransAmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.dgemm(TTrans.TRANS.val(), TTrans.NO_TRANS.val(), C.numRows(), C.numColumns(), rows, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixD transBmultAdd(double alpha, MatrixD B, MatrixD C) {
        Checks.checkTransBmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.dgemm(TTrans.NO_TRANS.val(), TTrans.TRANS.val(), C.numRows(), C.numColumns(), cols, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixD copy() {
        return new SimpleMatrixD(this); 
    }
}
