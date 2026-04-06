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
package blis.gemm;

import blis.gemm.vectorapi.SgemmMRxNR;

/**
 * Trampoline into Michael Lehn's cache-friendly BLIS sgemm routine (called
 * SgemmMRxNR here).
 */
final class SgemmBLIS {
    /**
     * @param m
     *            the number of rows of the matrix op(A) and of the matrix C
     * @param n
     *            the number of columns of the matrix op(B) and the number of
     *            columns of the matrix C
     * @param k
     *            the number of columns of the matrix op(A) and the number of
     *            rows of the matrix op(B)
     */
    static void sgemm(P p, boolean notA, boolean notB, int m, int n, int k, float alpha, float[] A, int _a_offset, int ldA,
            float[] B, int _b_offset, int ldB, float beta, float[] C, int _c_offset, int ldC) {

        if (notB) {
            if (notA) {
                // Form C := alpha*A*B + beta*C.
                SgemmMRxNR.sgemm(p, m, n, k, alpha, _a_offset, A, 1, ldA, _b_offset, B, 1, ldB, beta, _c_offset, C, 1, ldC);
            } else {
                // Form C := alpha*A**T*B + beta*C
                SgemmMRxNR.sgemm(p, m, n, k, alpha, _a_offset, A, ldA, 1, _b_offset, B, 1, ldB, beta, _c_offset, C, 1, ldC);
            }
        } else {
            if (notA) {
                // Form C := alpha*A*B**T + beta*C
                SgemmMRxNR.sgemm(p, m, n, k, alpha, _a_offset, A, 1, ldA, _b_offset, B, ldB, 1, beta, _c_offset, C, 1, ldC);
            } else {
                // Form C := alpha*A**T*B**T + beta*C
                SgemmMRxNR.sgemm(p, m, n, k, alpha, _a_offset, A, ldA, 1, _b_offset, B, ldB, 1, beta, _c_offset, C, 1, ldC);
            }
        }
    }
}
