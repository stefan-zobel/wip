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
public class SimpleMatrixF extends MatrixFBase implements MatrixF {

    private static final float BETA = 1.0f;

    public SimpleMatrixF(int rows, int cols) {
        this(rows, cols, new float[Checks.checkArrayLength(rows, cols)]);
    }

    public SimpleMatrixF(int rows, int cols, float initialValue) {
        super(rows, cols, new float[Checks.checkArrayLength(rows, cols)], false);
        Arrays.fill(a, initialValue);
    }

    private SimpleMatrixF(SimpleMatrixF other) {
        super(other.rows, other.cols, other.a, true);
    }

    protected SimpleMatrixF(int rows, int cols, float[] data) {
        super(rows, cols, data, false);
    }

    @Override
    protected MatrixF create(int rows, int cols, float[] data) {
        return new SimpleMatrixF(rows, cols, data);
    }

    @Override
    public MatrixF multAdd(float alpha, MatrixF B, MatrixF C) {
        Checks.checkMultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.sgemm(TTrans.NO_TRANS.val(), TTrans.NO_TRANS.val(), C.numRows(), C.numColumns(), cols, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixF transABmultAdd(float alpha, MatrixF B, MatrixF C) {
        Checks.checkTransABmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.sgemm(TTrans.TRANS.val(), TTrans.TRANS.val(), C.numRows(), C.numColumns(), rows, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixF transAmultAdd(float alpha, MatrixF B, MatrixF C) {
        Checks.checkTransAmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.sgemm(TTrans.TRANS.val(), TTrans.NO_TRANS.val(), C.numRows(), C.numColumns(), rows, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixF transBmultAdd(float alpha, MatrixF B, MatrixF C) {
        Checks.checkTransBmultAdd(this, B, C);

        Blas blas = Blas.getInstance();
        blas.sgemm(TTrans.NO_TRANS.val(), TTrans.TRANS.val(), C.numRows(), C.numColumns(), cols, alpha, a,
                Math.max(1, rows), B.getArrayUnsafe(), Math.max(1, B.numRows()), BETA, C.getArrayUnsafe(),
                Math.max(1, C.numRows()));

        return C;
    }

    @Override
    public MatrixF copy() {
        return new SimpleMatrixF(this);
    }
}
