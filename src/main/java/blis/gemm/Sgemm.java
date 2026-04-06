/*
 * Copyright 2018 Stefan Zobel
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

import java.util.Objects;

import blis.gemm.novectorapi.FloatPolicyNoVectorApi;
import blis.gemm.vectorapi.FloatPolicy;

/**
 * Java implementation of BLAS generalized matrix multiplication (gemm) for
 * single-precision Fortran-style matrices (i.e., column-major storage layout
 * matrices holding floats).
 */
public final class Sgemm {

    public static void sgemm(P p, boolean notA, boolean notB, int m, int n, int k, float alpha, float[] a, int _a_offset,
            int lda, float[] b, int _b_offset, int ldb, float beta, float[] c, int _c_offset, int ldc, boolean useBLIS) {

        requireNonNull(a, b, c);

        int nRowA = 0;
        int nRowB = 0;
        if (notA) { // N
            nRowA = m;
        } else {
            nRowA = k;
        }
        if (notB) { // N
            nRowB = k;
        } else {
            nRowB = n;
        }

        if (m < 0) {
            throw new IllegalArgumentException("m < 0");
        } else if (n < 0) {
            throw new IllegalArgumentException("n < 0");
        } else if (k < 0) {
            throw new IllegalArgumentException("k < 0");
        } else if (lda < Math.max(1, nRowA)) {
            throw new IllegalArgumentException("lda < Math.max(1, nRowA)");
        } else if (ldb < Math.max(1, nRowB)) {
            throw new IllegalArgumentException("ldb < Math.max(1, nRowB)");
        } else if (ldc < Math.max(1, m)) {
            throw new IllegalArgumentException("ldc < Math.max(1, m)");
        }

        // Quick return if possible
        if ((m == 0 || n == 0) || ((alpha == 0.0f || k == 0) && beta == 1.0f)) {
            return;
        }

        /*
         * @param m the number of rows of the matrix op(A) and of the matrix C
         * 
         * @param n the number of columns of the matrix op(B) and the number of
         * columns of the matrix C
         * 
         * @param k the number of columns of the matrix op(A) and the number of
         * rows of the matrix op(B)
         */
        if (!useBLIS) { // XXX
            SgemmNetlib.sgemm(notA, notB, m, n, k, alpha, a, _a_offset, lda, b, _b_offset, ldb, beta, c, _c_offset,
                    ldc);
        } else {
            SgemmBLIS.sgemm(p, notA, notB, m, n, k, alpha, a, _a_offset, lda, b, _b_offset, ldb, beta, c, _c_offset, ldc);
        }
    }

    public static void sgemm(boolean notA, boolean notB, int m, int n, int k, float alpha, float[] a, int _a_offset,
            int lda, float[] b, int _b_offset, int ldb, float beta, float[] c, int _c_offset, int ldc) {
        P p = null;
        sgemm(p, notA, notB, m, n, k, alpha, a, _a_offset, lda, b, _b_offset, ldb, beta, c, _c_offset, ldc, false);
    }

    public static void sgemmBLIS(boolean notA, boolean notB, int m, int n, int k, float alpha, float[] a, int _a_offset,
            int lda, float[] b, int _b_offset, int ldb, float beta, float[] c, int _c_offset, int ldc) {
        P p = new FloatPolicy();
        sgemm(p, notA, notB, m, n, k, alpha, a, _a_offset, lda, b, _b_offset, ldb, beta, c, _c_offset, ldc, true);
    }

    private static void requireNonNull(Object... args) {
        for (Object arg : args) {
            Objects.requireNonNull(arg);
        }
    }

    private Sgemm() {
        throw new AssertionError();
    }
}
