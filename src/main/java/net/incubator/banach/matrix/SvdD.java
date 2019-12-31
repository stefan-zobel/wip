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

public class SvdD {

    private final TSvdJob jobType;

    private final SimpleMatrixD U;
    private final SimpleMatrixD Vt;
    private final double[] S;

    public MatrixD getU() {
        return U;
    }

    public MatrixD getVt() {
        return Vt;
    }

    public double[] getS() {
        return S;
    }

    public boolean hasSingularVectors() {
        return jobType == TSvdJob.ALL;
    }

    /* package */ SvdD(SimpleMatrixD A, boolean full) {
        S = new double[Math.min(A.numRows(), A.numColumns())];
        jobType = full ? TSvdJob.ALL : TSvdJob.NONE;
        if (jobType == TSvdJob.ALL) {
            U = new SimpleMatrixD(A.numRows(), A.numRows());
            Vt = new SimpleMatrixD(A.numColumns(), A.numColumns());
        } else {
            U = null;
            Vt = null;
        }
        computeSvdInplace(A);
    }

    private void computeSvdInplace(SimpleMatrixD A) {
        MatrixD AA = A.copy();
        int m = AA.numRows();
        int n = AA.numColumns();
        PlainLapack.dgesdd(Lapack.getInstance(), jobType, m, n, AA.getArrayUnsafe(), Math.max(1, m), S,
                hasSingularVectors() ? U.getArrayUnsafe() : new double[0], Math.max(1, m),
                hasSingularVectors() ? Vt.getArrayUnsafe() : new double[0], Math.max(1, n));
    }
}
