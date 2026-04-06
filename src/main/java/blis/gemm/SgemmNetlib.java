/*
 * Copyright 2026 Stefan Zobel
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

/**
 * The original Netlib implementation (which was generated using the F2J
 * translator directly from Fortran 77 into a Java class file). The source code
 * had to be restored from bytecode and then the innermost loop was manually
 * unrolled. This downsized version handles only the cases
 * <code>C = A * B<sup>T</sup></code> and {@code C = A * B}.
 */
final class SgemmNetlib {

    private static final int UNROLL = 24;

    /**
     * C <- alpha * A * op( B ) + beta * C, where alpha=1.0 and beta=0.0
     * 
     * @param m
     *            the number of rows of the matrix A and of the matrix C
     * @param n
     *            the number of columns of the matrix op(B) and the number of
     *            columns of the matrix C
     * @param k
     *            the number of columns of the matrix A and the number of
     *            rows of the matrix op(B)
     */
    static void sgemm(boolean notA, boolean notB, int m, int n, int k, float alpha, float[] a, int _a_offset, int lda,
            float[] b, int _b_offset, int ldb, float beta, float[] c, int _c_offset, int ldc) {
        sgemm(notB, m, n, k, a, lda, b, ldb, c, ldc);
    }

    /**
     * C <- A * op( B )
     * 
     * @param m
     *            the number of rows of the matrix A and of the matrix C
     * @param n
     *            the number of columns of the matrix op(B) and the number of
     *            columns of the matrix C
     * @param k
     *            the number of columns of the matrix A and the number of
     *            rows of the matrix op(B)
     */
    static void sgemm(boolean notBTransposed, int m, int n, int k, float[] a, int lda, float[] b, int ldb, float[] c, int ldc) {

        if (notBTransposed) {
            // Form C <- A * B
            int u = 1;
            for (int o = n; o > 0; o--) {
                int w = 1;
                for (int p = k; p > 0; p--) {
                    float tmp = b[(w - 1) + (u - 1) * ldb];
                    
                    if (tmp != 0.0f) {
                        int i = 1;
                        int q = m;

                        // Pre-compute the offsets for the columns of A and C
                        int cOffset = (u - 1) * ldc;
                        int aOffset = (w - 1) * lda;

                        // 1. Main loop, manually unrolled into 24 steps
                        while (q >= UNROLL) {
                            int cIdx = (i - 1) + cOffset;
                            int aIdx = (i - 1) + aOffset;

                            c[cIdx] = Math.fma(tmp, a[aIdx], c[cIdx]);
                            c[cIdx + 1] = Math.fma(tmp, a[aIdx + 1], c[cIdx + 1]);
                            c[cIdx + 2] = Math.fma(tmp, a[aIdx + 2], c[cIdx + 2]);
                            c[cIdx + 3] = Math.fma(tmp, a[aIdx + 3], c[cIdx + 3]);
                            c[cIdx + 4] = Math.fma(tmp, a[aIdx + 4], c[cIdx + 4]);
                            c[cIdx + 5] = Math.fma(tmp, a[aIdx + 5], c[cIdx + 5]);
                            c[cIdx + 6] = Math.fma(tmp, a[aIdx + 6], c[cIdx + 6]);
                            c[cIdx + 7] = Math.fma(tmp, a[aIdx + 7], c[cIdx + 7]);
                            c[cIdx + 8] = Math.fma(tmp, a[aIdx + 8], c[cIdx + 8]);
                            c[cIdx + 9] = Math.fma(tmp, a[aIdx + 9], c[cIdx + 9]);
                            c[cIdx + 10] = Math.fma(tmp, a[aIdx + 10], c[cIdx + 10]);
                            c[cIdx + 11] = Math.fma(tmp, a[aIdx + 11], c[cIdx + 11]);
                            c[cIdx + 12] = Math.fma(tmp, a[aIdx + 12], c[cIdx + 12]);
                            c[cIdx + 13] = Math.fma(tmp, a[aIdx + 13], c[cIdx + 13]);
                            c[cIdx + 14] = Math.fma(tmp, a[aIdx + 14], c[cIdx + 14]);
                            c[cIdx + 15] = Math.fma(tmp, a[aIdx + 15], c[cIdx + 15]);
                            c[cIdx + 16] = Math.fma(tmp, a[aIdx + 16], c[cIdx + 16]);
                            c[cIdx + 17] = Math.fma(tmp, a[aIdx + 17], c[cIdx + 17]);
                            c[cIdx + 18] = Math.fma(tmp, a[aIdx + 18], c[cIdx + 18]);
                            c[cIdx + 19] = Math.fma(tmp, a[aIdx + 19], c[cIdx + 19]);
                            c[cIdx + 20] = Math.fma(tmp, a[aIdx + 20], c[cIdx + 20]);
                            c[cIdx + 21] = Math.fma(tmp, a[aIdx + 21], c[cIdx + 21]);
                            c[cIdx + 22] = Math.fma(tmp, a[aIdx + 22], c[cIdx + 22]);
                            c[cIdx + 23] = Math.fma(tmp, a[aIdx + 23], c[cIdx + 23]);

                            i += UNROLL;
                            q -= UNROLL;
                        }

                        // 2. Remaining elements (< 24)
                        while (q > 0) {
                            c[(i - 1) + cOffset] += tmp * a[(i - 1) + aOffset];
                            i++;
                            q--;
                        }
                    }
                    w++;
                }
                u++;
            }
        } else {
            // Form C <- A * B^T
            int u = 1;
            for (int o = n; o > 0; o--) {
                int w = 1;
                for (int p = k; p > 0; p--) {
                    // Index access of B is the only difference
                    float tmp = b[(u - 1) + (w - 1) * ldb]; 
                    
                    if (tmp != 0.0f) {
                        int i = 1;
                        int q = m;

                        // Pre-compute the offsets for the columns of A and C
                        int cOffset = (u - 1) * ldc;
                        int aOffset = (w - 1) * lda;

                        // 1. Main loop, manually unrolled into 24 steps
                        while (q >= UNROLL) {
                            int cIdx = (i - 1) + cOffset;
                            int aIdx = (i - 1) + aOffset;

                            c[cIdx] = Math.fma(tmp, a[aIdx], c[cIdx]);
                            c[cIdx + 1] = Math.fma(tmp, a[aIdx + 1], c[cIdx + 1]);
                            c[cIdx + 2] = Math.fma(tmp, a[aIdx + 2], c[cIdx + 2]);
                            c[cIdx + 3] = Math.fma(tmp, a[aIdx + 3], c[cIdx + 3]);
                            c[cIdx + 4] = Math.fma(tmp, a[aIdx + 4], c[cIdx + 4]);
                            c[cIdx + 5] = Math.fma(tmp, a[aIdx + 5], c[cIdx + 5]);
                            c[cIdx + 6] = Math.fma(tmp, a[aIdx + 6], c[cIdx + 6]);
                            c[cIdx + 7] = Math.fma(tmp, a[aIdx + 7], c[cIdx + 7]);
                            c[cIdx + 8] = Math.fma(tmp, a[aIdx + 8], c[cIdx + 8]);
                            c[cIdx + 9] = Math.fma(tmp, a[aIdx + 9], c[cIdx + 9]);
                            c[cIdx + 10] = Math.fma(tmp, a[aIdx + 10], c[cIdx + 10]);
                            c[cIdx + 11] = Math.fma(tmp, a[aIdx + 11], c[cIdx + 11]);
                            c[cIdx + 12] = Math.fma(tmp, a[aIdx + 12], c[cIdx + 12]);
                            c[cIdx + 13] = Math.fma(tmp, a[aIdx + 13], c[cIdx + 13]);
                            c[cIdx + 14] = Math.fma(tmp, a[aIdx + 14], c[cIdx + 14]);
                            c[cIdx + 15] = Math.fma(tmp, a[aIdx + 15], c[cIdx + 15]);
                            c[cIdx + 16] = Math.fma(tmp, a[aIdx + 16], c[cIdx + 16]);
                            c[cIdx + 17] = Math.fma(tmp, a[aIdx + 17], c[cIdx + 17]);
                            c[cIdx + 18] = Math.fma(tmp, a[aIdx + 18], c[cIdx + 18]);
                            c[cIdx + 19] = Math.fma(tmp, a[aIdx + 19], c[cIdx + 19]);
                            c[cIdx + 20] = Math.fma(tmp, a[aIdx + 20], c[cIdx + 20]);
                            c[cIdx + 21] = Math.fma(tmp, a[aIdx + 21], c[cIdx + 21]);
                            c[cIdx + 22] = Math.fma(tmp, a[aIdx + 22], c[cIdx + 22]);
                            c[cIdx + 23] = Math.fma(tmp, a[aIdx + 23], c[cIdx + 23]);

                            i += UNROLL;
                            q -= UNROLL;
                        }

                        // 2. Remaining elements (< 24)
                        while (q > 0) {
                            c[(i - 1) + cOffset] += tmp * a[(i - 1) + aOffset];
                            i++;
                            q--;
                        }
                    }
                    w++;
                }
                u++;
            }
        }
    }
}
