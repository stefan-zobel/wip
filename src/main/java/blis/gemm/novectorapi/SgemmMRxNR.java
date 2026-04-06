/*
 * Copyright 2018 - 2026 Stefan Zobel
 *
 * The original C code this was derived from is Copyright Dr. Michael Lehn,
 * Ulm University 
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
package blis.gemm.novectorapi;

import java.util.Arrays;

import blis.gemm.P;

/**
 * A straightforward Java translation of Michael Lehn's pure ANSI C variant of a
 * cache-friendly BLIS sgemm routine.
 */
public final class SgemmMRxNR {

    //
    // Packing complete panels from A (i.e. without padding)
    //
    private static void pack_A_MRxk(P p, int k, int A_start, float[] A, int incRowA, int incColA, float[] work,
            int work_start) {

        final int MR_Height = p.MR_Height;

        for (int j = 0; j < k; ++j) {
            for (int i = 0; i < MR_Height; ++i) {
                work[work_start + i] = A[A_start + i * incRowA];
            }
            work_start += MR_Height;
            A_start += incColA;
        }
    }

    private static void pack_A_4xk(P p, int k, int A_start, float[] A, int incRowA, int incColA, float[] work,
            int work_start) {

        final int MR_Height = p.MR_Height; // 4

        for (int j = 0; j < k; ++j) {
            // Packing manually unrolled
            work[work_start + 0] = A[A_start + 0 * incRowA];
            work[work_start + 1] = A[A_start + 1 * incRowA];
            work[work_start + 2] = A[A_start + 2 * incRowA];
            work[work_start + 3] = A[A_start + 3 * incRowA];

            work_start += MR_Height;
            A_start += incColA;
        }
    }

    private static void pack_A_4xk_fast(P p, int k, int A_start, float[] A, int incColA, float[] work,
            int work_start) {

        final int MR_Height = p.MR_Height; // 4

        for (int j = 0; j < k; ++j) {
            // Packing manually unrolled
            work[work_start + 0] = A[A_start + 0];
            work[work_start + 1] = A[A_start + 1];
            work[work_start + 2] = A[A_start + 2];
            work[work_start + 3] = A[A_start + 3];

            work_start += MR_Height;
            A_start += incColA;
        }
    }

    //
    // Packing complete panels from B (i.e. without padding)
    //
    private static void pack_B_kxNR(P p, int k, int B_start, float[] B, int incRowB, int incColB, float[] work,
            int work_start) {

        final int NR_Width = p.NR_Width;

        for (int i = 0; i < k; ++i) {
            for (int j = 0; j < NR_Width; ++j) {
                work[work_start + j] = B[B_start + j * incColB];
            }
            
            work_start += NR_Width;
            B_start += incRowB;
        }
    }

    private static void pack_B_kx6(P p, int k, int B_start, float[] B, int incRowB, int incColB, float[] work,
            int work_start) {

        final int NR_Width = p.NR_Width; // 6

        for (int i = 0; i < k; ++i) {
            // Packing manually unrolled
            work[work_start + 0] = B[B_start + 0 * incColB];
            work[work_start + 1] = B[B_start + 1 * incColB];
            work[work_start + 2] = B[B_start + 2 * incColB];
            work[work_start + 3] = B[B_start + 3 * incColB];
            work[work_start + 4] = B[B_start + 4 * incColB];
            work[work_start + 5] = B[B_start + 5 * incColB];

            work_start += NR_Width;
            B_start += incRowB;
        }
    }

    private static void pack_B_kx6_fast(P p, int k, int B_start, float[] B, int incRowB, float[] work,
            int work_start) {

        final int NR_Width = p.NR_Width; // 6

        for (int i = 0; i < k; ++i) {
            // Packing manually unrolled
            work[work_start + 0] = B[B_start + 0];
            work[work_start + 1] = B[B_start + 1];
            work[work_start + 2] = B[B_start + 2];
            work[work_start + 3] = B[B_start + 3];
            work[work_start + 4] = B[B_start + 4];
            work[work_start + 5] = B[B_start + 5];

            work_start += NR_Width;
            B_start += incRowB;
        }
    }

    //
    // Packing panels from A with padding if required
    //
    static void pack_A(P p, int mc, int kc, int A_start, float[] A, int incRowA, int incColA, float[] work) {

        final int mp = mc / p.MR_Height;
        final int _mr = mc % p.MR_Height;

        int work_start = 0;
        int i;

        for (i = 0; i < mp; ++i) {
//            pack_A_MRxk(p, kc, A_start, A, incRowA, incColA, work, work_start); // XXX
            if (incRowA == 1) {
                pack_A_4xk_fast(p, kc, A_start, A, incColA, work, work_start); // XXX
            } else {
                pack_A_4xk(p, kc, A_start, A, incRowA, incColA, work, work_start); // XXX
            }
            work_start += kc * p.MR_Height;
            A_start += p.MR_Height * incRowA;
        }
        if (_mr > 0) {
            for (int j = 0; j < kc; ++j) {
                for (i = 0; i < _mr; ++i) {
                    work[work_start + i] = A[A_start + i * incRowA];
                }
                for (i = _mr; i < p.MR_Height; ++i) {
                    work[work_start + i] = 0.0f;
                }
                work_start += p.MR_Height;
                A_start += incColA;
            }
        }
    }

    //
    // Packing panels from B with padding if required
    //
    static void pack_B(P p, int kc, int nc, int B_start, float[] B, int incRowB, int incColB, float[] work) {

        final int np = nc / p.NR_Width;
        final int _nr = nc % p.NR_Width;

        int work_start = 0;
        int j;

        for (j = 0; j < np; ++j) {
//            pack_B_kxNR(p, kc, B_start, B, incRowB, incColB, work, work_start); // XXX
            if (incColB == 1) {
                pack_B_kx6_fast(p, kc, B_start, B, incRowB, work, work_start); // XXX
            } else {
                pack_B_kx6(p, kc, B_start, B, incRowB, incColB, work, work_start); // XXX
            }
            work_start += kc * p.NR_Width;
            B_start += p.NR_Width * incColB;
        }
        if (_nr > 0) {
            for (int i = 0; i < kc; ++i) {
                for (j = 0; j < _nr; ++j) {
                    work[work_start + j] = B[B_start + j * incColB];
                }
                for (j = _nr; j < p.NR_Width; ++j) {
                    work[work_start + j] = 0.0f;
                }
                work_start += p.NR_Width;
                B_start += incRowB;
            }
        }
    }

    //
    // Micro kernel for multiplying panels from A and B
    //
    private static void sgemm_micro_kernel(P p, int kc, float alpha, int A_panel_start, float[] A_panel, int B_panel_start,
            float[] B_panel, float beta, final int C_panel_start, float[] C_panel, int incRowC, int incColC,
            float[] AB) {

        // clear buffer
        Arrays.fill(AB, 0.0f);

//        final int NR_Width = p.NR_Width;
//        final int MR_Height = p.MR_Height;

        //
        // Compute AB = A*B
        //
        /*
        for (int l = 0; l < kc; ++l) {
            for (int j = 0; j < NR_Width; ++j) { // 6 j
                float b_val = B_panel[B_panel_start + j]; // save the B value beforehand
                int offset = j * MR_Height; // pre-compute the offset
                for (int i = 0; i < 8; ++i) {
                    // inner loop should now look a bit more "vectorizable" to the JIT
                    AB[offset + i] += A_panel[A_panel_start + i] * b_val;
                }
            }
            A_panel_start += MR_Height;
            B_panel_start += NR_Width;
        }
        */

        for (int l = 0; l < kc; ++l) {

            float b0j = B_panel[B_panel_start++];
            float b1j = B_panel[B_panel_start++];
            float b2j = B_panel[B_panel_start++];
            float b3j = B_panel[B_panel_start++];
            float b4j = B_panel[B_panel_start++];
            float b5j = B_panel[B_panel_start++];

            float a0i = A_panel[A_panel_start++];
            float a1i = A_panel[A_panel_start++];
            float a2i = A_panel[A_panel_start++];
            float a3i = A_panel[A_panel_start++];
//            float a4i = A_panel[A_panel_start++];
//            float a5i = A_panel[A_panel_start++];
//            float a6i = A_panel[A_panel_start++];
//            float a7i = A_panel[A_panel_start++];

            // Column 0 of B
            AB[0] = Math.fma(a0i, b0j, AB[0]);
            AB[1] = Math.fma(a1i, b0j, AB[1]);
            AB[2] = Math.fma(a2i, b0j, AB[2]);
            AB[3] = Math.fma(a3i, b0j, AB[3]);
//            AB[4] = Math.fma(a4i, b0j, AB[4]);
//            AB[5] = Math.fma(a5i, b0j, AB[5]);
//            AB[6] = Math.fma(a6i, b0j, AB[6]);
//            AB[7] = Math.fma(a7i, b0j, AB[7]);

            AB[4] = Math.fma(a0i, b1j, AB[4]);
            AB[5] = Math.fma(a1i, b1j, AB[5]);
            AB[6] = Math.fma(a2i, b1j, AB[6]);
            AB[7] = Math.fma(a3i, b1j, AB[7]);
//            AB[12] = Math.fma(a4i, b1j, AB[12]);
//            AB[13] = Math.fma(a5i, b1j, AB[13]);
//            AB[14] = Math.fma(a6i, b1j, AB[14]);
//            AB[15] = Math.fma(a7i, b1j, AB[15]);

            AB[8] = Math.fma(a0i, b2j, AB[8]);
            AB[9] = Math.fma(a1i, b2j, AB[9]);
            AB[10] = Math.fma(a2i, b2j, AB[10]);
            AB[11] = Math.fma(a3i, b2j, AB[11]);
//            AB[20] = Math.fma(a4i, b2j, AB[20]);
//            AB[21] = Math.fma(a5i, b2j, AB[21]);
//            AB[22] = Math.fma(a6i, b2j, AB[22]);
//            AB[23] = Math.fma(a7i, b2j, AB[23]);

            AB[12] = Math.fma(a0i, b3j, AB[12]);
            AB[13] = Math.fma(a1i, b3j, AB[13]);
            AB[14] = Math.fma(a2i, b3j, AB[14]);
            AB[15] = Math.fma(a3i, b3j, AB[15]);
//            AB[28] = Math.fma(a4i, b3j, AB[28]);
//            AB[29] = Math.fma(a5i, b3j, AB[29]);
//            AB[30] = Math.fma(a6i, b3j, AB[30]);
//            AB[31] = Math.fma(a7i, b3j, AB[31]);

            AB[16] = Math.fma(a0i, b4j, AB[16]);
            AB[17] = Math.fma(a1i, b4j, AB[17]);
            AB[18] = Math.fma(a2i, b4j, AB[18]);
            AB[19] = Math.fma(a3i, b4j, AB[19]);
//            AB[36] = Math.fma(a4i, b4j, AB[36]);
//            AB[37] = Math.fma(a5i, b4j, AB[37]);
//            AB[38] = Math.fma(a6i, b4j, AB[38]);
//            AB[39] = Math.fma(a7i, b4j, AB[39]);

            // Column 5 of B
            AB[20] = Math.fma(a0i, b5j, AB[20]);
            AB[21] = Math.fma(a1i, b5j, AB[21]);
            AB[22] = Math.fma(a2i, b5j, AB[22]);
            AB[23] = Math.fma(a3i, b5j, AB[23]);
//            AB[44] = Math.fma(a4i, b5j, AB[44]);
//            AB[45] = Math.fma(a5i, b5j, AB[45]);
//            AB[46] = Math.fma(a6i, b5j, AB[46]);
//            AB[47] = Math.fma(a7i, b5j, AB[47]);


//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 0);  // Spalte 0
//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 8);  // Spalte 1
//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 16); // Spalte 2
//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 24); // Spalte 3
//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 32); // Spalte 4
//            updateColumn(a0i, a1i, a2i, a3i, a4i, a5i, a6i, a7i, B_panel[B_panel_start++], AB, 40); // Spalte 5
        }



        //
        // Update C <- beta*C
        //
        if (beta != 1.0f) {
//            sgemm_micro_betaMulC(p, beta, C_panel_start, C_panel, incColC); // XXX
            sgemm_micro_betaMulC_MR4(p, beta, C_panel_start, C_panel, incColC); // XXX
        }

        //
        // Update C <- C + alpha*AB (note: the case alpha==0.0 was already
        // treated in the above layer dgemm)
        //
//        sgemm_micro_plusAlphaAB(p, alpha, C_panel_start, C_panel, incColC, AB); // XXX
        sgemm_micro_plusAlphaAB_MR4(p, alpha, C_panel_start, C_panel, incColC, AB); // XXX
    }


    private static void updateColumn(float a0, float a1, float a2, float a3, 
                                     float a4, float a5, float a6, float a7, 
                                     float b, float[] AB, int offset) {
        // Should be easily vectorizable, but it isn't 
        AB[offset + 0] = Math.fma(a0, b, AB[offset + 0]);
        AB[offset + 1] = Math.fma(a1, b, AB[offset + 1]);
        AB[offset + 2] = Math.fma(a2, b, AB[offset + 2]);
        AB[offset + 3] = Math.fma(a3, b, AB[offset + 3]);
        AB[offset + 4] = Math.fma(a4, b, AB[offset + 4]);
        AB[offset + 5] = Math.fma(a5, b, AB[offset + 5]);
        AB[offset + 6] = Math.fma(a6, b, AB[offset + 6]);
        AB[offset + 7] = Math.fma(a7, b, AB[offset + 7]);
    }

    //
    // Update C <- beta*C
    //
    private static void sgemm_micro_betaMulC(P p, float beta, int C_panel_start, float[] C_panel,
            int incColC) {

        final int NR_Width = p.NR_Width;
        final int MR_Height = p.MR_Height;

        if (beta == 0.0) {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                for (int i = 0; i < MR_Height; ++i) {
                    C_panel[base_C + i] = 0.0f;
                }
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                for (int i = 0; i < MR_Height; ++i) {
                    C_panel[base_C + i] *= beta;
                }
            }
        }
    }

    private static void sgemm_micro_betaMulC_MR4(P p, float beta, int C_panel_start, float[] C_panel,
            int incColC) {

        final int NR_Width = p.NR_Width;

        if (beta == 0.0f) {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] = 0.0f;
                C_panel[base_C + 1] = 0.0f;
                C_panel[base_C + 2] = 0.0f;
                C_panel[base_C + 3] = 0.0f;
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] *= beta;
                C_panel[base_C + 1] *= beta;
                C_panel[base_C + 2] *= beta;
                C_panel[base_C + 3] *= beta;
            }
        }
    }

    //
    // Update C <- C + alpha*AB (note: the case alpha==0.0f was already
    // treated in the above layer sgemm)
    //
    private static void sgemm_micro_plusAlphaAB(P p, float alpha, int C_panel_start, float[] C_panel,
            int incColC, float[] AB) {

        final int NR_Width = p.NR_Width;
        final int MR_Height = p.MR_Height;

        if (alpha == 1.0f) {
            for (int j = 0; j < NR_Width; ++j) {
                int jIdx = j * MR_Height;
                int base_C = C_panel_start + j * incColC;
                for (int i = 0; i < MR_Height; ++i) {
                    C_panel[base_C + i] += AB[i + jIdx];
                }
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int jIdx = j * MR_Height;
                int base_C = C_panel_start + j * incColC;
                for (int i = 0; i < MR_Height; ++i) {
                    C_panel[base_C + i] += alpha * AB[i + jIdx];
                }
            }
        }
    }

    private static void sgemm_micro_plusAlphaAB_MR4(P p, float alpha, int C_panel_start, float[] C_panel,
            int incColC, float[] AB) {

        final int NR_Width = p.NR_Width;
        final int MR_Height = p.MR_Height;

        if (alpha == 1.0f) {
            for (int j = 0; j < NR_Width; ++j) {
                int jIdx = j * MR_Height;
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] += AB[0 + jIdx];
                C_panel[base_C + 1] += AB[1 + jIdx];
                C_panel[base_C + 2] += AB[2 + jIdx];
                C_panel[base_C + 3] += AB[3 + jIdx];
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int jIdx = j * MR_Height;
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] += alpha * AB[0 + jIdx];
                C_panel[base_C + 1] += alpha * AB[1 + jIdx];
                C_panel[base_C + 2] += alpha * AB[2 + jIdx];
                C_panel[base_C + 3] += alpha * AB[3 + jIdx];
            }
        }
    }

    //
    // Macro Kernel for the multiplication of blocks of A and B. We assume that
    // these blocks were previously packed to buffers _A and _B.
    //
    static int sgemm_macro_kernel(P p, int mc, int nc, int kc, float alpha, float beta, int C_start, float[] C,
            int incRowC, int incColC, float[] _A, float[] _B, float[] AB, float[] workC) {

        int micro_kernel_calls = 0;

        final int mp = (mc + p.MR_Height - 1) / p.MR_Height;
        final int np = (nc + p.NR_Width - 1) / p.NR_Width;

        final int _mr = mc % p.MR_Height;
        final int _nr = nc % p.NR_Width;

        int nr, mr;
        int i, j;

        for (j = 0; j < np; ++j) {
            nr = (j != np - 1 || _nr == 0) ? p.NR_Width : _nr;

            for (i = 0; i < mp; ++i) {
                mr = (i != mp - 1 || _mr == 0) ? p.MR_Height : _mr;

                if (mr == p.MR_Height && nr == p.NR_Width) {
                    sgemm_micro_kernel(p, kc, alpha, (i * kc * p.MR_Height), _A, (j * kc * p.NR_Width), _B, beta,
                            (C_start + i * p.MR_Height * incRowC + j * p.NR_Width * incColC), C, incRowC, incColC, AB);
                    ++micro_kernel_calls;
                } else {
                    sgemm_micro_kernel(p, kc, alpha, (i * kc * p.MR_Height), _A, (j * kc * p.NR_Width), _B, 0.0f, 0, workC, 1,
                            p.MR_Height, AB);

                    sgescal(mr, nr, beta, (C_start + i * p.MR_Height * incRowC + j * p.NR_Width * incColC), C, incRowC,
                            incColC);

                    sgeaxpy(mr, nr, 1.0f, workC, 1, p.MR_Height,
                            (C_start + i * p.MR_Height * incRowC + j * p.NR_Width * incColC), C, incRowC, incColC);
                    ++micro_kernel_calls;
                }
            }
        }
        return micro_kernel_calls;
    }

    //
    // Compute X *= alpha
    //
    private static void sgescal(int m, int n, float alpha, int X_start, float[] X, int incRowX, int incColX) {

        if (alpha != 0.0f) {
            for (int j = 0; j < n; ++j) {
                int base_X = X_start + j * incColX;
                for (int i = 0; i < m; ++i) {
                    X[base_X + i * incRowX] *= alpha;
                }
            }
        } else {
            for (int j = 0; j < n; ++j) {
                int base_X = X_start + j * incColX;
                for (int i = 0; i < m; ++i) {
                    X[base_X + i * incRowX] = 0.0f;
                }
            }
        }
    }

    //
    // Compute Y += alpha*X
    //
    private static void sgeaxpy(int m, int n, float alpha, float[] X, int incRowX, int incColX, int Y_start,
            float[] Y, int incRowY, int incColY) {

        if (alpha != 1.0f) {
            for (int j = 0; j < n; ++j) {
                int base_Y = Y_start + j * incColY;
                int _incColX = j * incColX;
                for (int i = 0; i < m; ++i) {
                    Y[base_Y + i * incRowY] += alpha * X[i * incRowX + _incColX];
                }
            }
        } else {
            for (int j = 0; j < n; ++j) {
                int base_Y = Y_start + j * incColY;
                int _incColX = j * incColX;
                for (int i = 0; i < m; ++i) {
                    Y[base_Y + i * incRowY] += X[i * incRowX + _incColX];
                }
            }
        }
    }

    //
    // Compute C <- beta*C + alpha*A*B
    //
    public static int sgemm(P p,int rowsA, int colsB, int colsA, float alpha, int offA, float[] A, int incRowA, int incColA,
            int offB, float[] B, int incRowB, int incColB, float beta, int offC, float[] C, int incRowC,
            int incColC) {

        int micro_kernel_calls = 0;

        if (alpha == 0.0f || colsA == 0) {
            sgescal(rowsA, colsB, beta, offC, C, incRowC, incColC);
            return micro_kernel_calls;
        }

        final int mb = (rowsA + p.MC - 1) / p.MC;
        final int nb = (colsB + p.NC - 1) / p.NC;
        final int kb = (colsA + p.KC - 1) / p.KC;

        final int _mc = rowsA % p.MC;
        final int _nc = colsB % p.NC;
        final int _kc = colsA % p.KC;

        //
        // Local buffers for storing panels from A, B and C
        //
        final float[] _A = new float[p.MC * p.KC];
        final float[] _B = new float[p.KC * p.NC];
        final float[] _C = new float[p.MR_Height * p.NR_Width];
        final float[] AB = new float[p.MR_Height * p.NR_Width];

        for (int j = 0; j < nb; ++j) {
            int nc = (j != nb - 1 || _nc == 0) ? p.NC : _nc;

            for (int l = 0; l < kb; ++l) {
                int kc = (l != kb - 1 || _kc == 0) ? p.KC : _kc;
                float _beta = (l == 0) ? beta : 1.0f;

                pack_B(p, kc, nc, (offB + l * p.KC * incRowB + j * p.NC * incColB), B, incRowB, incColB, _B);

                for (int i = 0; i < mb; ++i) {
                    int mc = (i != mb - 1 || _mc == 0) ? p.MC : _mc;

                    pack_A(p, mc, kc, (offA + i * p.MC * incRowA + l * p.KC * incColA), A, incRowA, incColA, _A);

                    micro_kernel_calls += sgemm_macro_kernel(p, mc, nc, kc, alpha, _beta,
                            (offC + i * p.MC * incRowC + j * p.NC * incColC), C, incRowC, incColC, _A, _B, AB, _C);
                }
            }
        }
        return micro_kernel_calls;
    }
}
