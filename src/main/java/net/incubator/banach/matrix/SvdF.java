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

import net.dedekind.lapack.Lapack;
import net.frobenius.TSvdJob;
import net.frobenius.lapack.PlainLapack;

public class SvdF {

    private final TSvdJob jobType;

    private final SimpleMatrixF U;
    private final SimpleMatrixF Vt;
    private final float[] S;

    public MatrixF getU() {
        return U;
    }

    public MatrixF getVt() {
        return Vt;
    }

    public float[] getS() {
        return S;
    }

    public boolean hasSingularVectors() {
        return jobType == TSvdJob.ALL;
    }

    /* package */ SvdF(SimpleMatrixF A, boolean full) {
        S = new float[Math.min(A.numRows(), A.numColumns())];
        jobType = full ? TSvdJob.ALL : TSvdJob.NONE;
        if (jobType == TSvdJob.ALL) {
            U = new SimpleMatrixF(A.numRows(), A.numRows());
            Vt = new SimpleMatrixF(A.numColumns(), A.numColumns());
        } else {
            U = null;
            Vt = null;
        }
        computeSvdInplace(A);
    }

    private void computeSvdInplace(SimpleMatrixF A) {
        MatrixF AA = A.copy();
        int m = AA.numRows();
        int n = AA.numColumns();
        PlainLapack.sgesdd(Lapack.getInstance(), jobType, m, n, AA.getArrayUnsafe(), Math.max(1, m), S,
                hasSingularVectors() ? U.getArrayUnsafe() : new float[0], Math.max(1, m),
                hasSingularVectors() ? Vt.getArrayUnsafe() : new float[0], Math.max(1, n));
    }
}
