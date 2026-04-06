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
package blis.gemm.vectorapi;

import java.util.Arrays;

import blis.gemm.P;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;


/**
 * A straightforward Java translation of Michael Lehn's pure ANSI C variant of a
 * cache-friendly BLIS sgemm routine.
 */
public final class SgemmMRxNR {
    
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

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

    private static void pack_A_8xk(P p, int k, int A_start, float[] A, int incRowA, int incColA, float[] work,
            int work_start) {

        final int MR_Height = p.MR_Height; // 8

        for (int j = 0; j < k; ++j) {
            // Packing manually unrolled
            work[work_start + 0] = A[A_start + 0 * incRowA];
            work[work_start + 1] = A[A_start + 1 * incRowA];
            work[work_start + 2] = A[A_start + 2 * incRowA];
            work[work_start + 3] = A[A_start + 3 * incRowA];
            work[work_start + 4] = A[A_start + 4 * incRowA];
            work[work_start + 5] = A[A_start + 5 * incRowA];
            work[work_start + 6] = A[A_start + 6 * incRowA];
            work[work_start + 7] = A[A_start + 7 * incRowA];

            work_start += MR_Height;
            A_start += incColA;
        }
    }

    private static void pack_A_8xk_fast(P p, int k, int A_start, float[] A, int incColA, float[] work,
            int work_start) {

        final int MR_Height = p.MR_Height; // 8

        for (int j = 0; j < k; ++j) {
            // Packing manually unrolled
            work[work_start + 0] = A[A_start + 0];
            work[work_start + 1] = A[A_start + 1];
            work[work_start + 2] = A[A_start + 2];
            work[work_start + 3] = A[A_start + 3];
            work[work_start + 4] = A[A_start + 4];
            work[work_start + 5] = A[A_start + 5];
            work[work_start + 6] = A[A_start + 6];
            work[work_start + 7] = A[A_start + 7];

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

    private static void pack_B_kx8(P p, int k, int B_start, float[] B, int incRowB, int incColB, float[] work,
            int work_start) {

        final int NR_Width = p.NR_Width; // 8

        for (int i = 0; i < k; ++i) {
            // Packing manually unrolled
            work[work_start + 0] = B[B_start + 0 * incColB];
            work[work_start + 1] = B[B_start + 1 * incColB];
            work[work_start + 2] = B[B_start + 2 * incColB];
            work[work_start + 3] = B[B_start + 3 * incColB];
            work[work_start + 4] = B[B_start + 4 * incColB];
            work[work_start + 5] = B[B_start + 5 * incColB];
            work[work_start + 6] = B[B_start + 6 * incColB];
            work[work_start + 7] = B[B_start + 7 * incColB];

            work_start += NR_Width;
            B_start += incRowB;
        }
    }

    private static void pack_B_kx8_fast(P p, int k, int B_start, float[] B, int incRowB, float[] work,
            int work_start) {

        final int NR_Width = p.NR_Width; // 8

        for (int i = 0; i < k; ++i) {
            // Packing manually unrolled
            work[work_start + 0] = B[B_start + 0];
            work[work_start + 1] = B[B_start + 1];
            work[work_start + 2] = B[B_start + 2];
            work[work_start + 3] = B[B_start + 3];
            work[work_start + 4] = B[B_start + 4];
            work[work_start + 5] = B[B_start + 5];
            work[work_start + 6] = B[B_start + 6];
            work[work_start + 7] = B[B_start + 7];
            
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
                pack_A_8xk_fast(p, kc, A_start, A, incColA, work, work_start); // XXX
            } else {
                pack_A_8xk(p, kc, A_start, A, incRowA, incColA, work, work_start); // XXX
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
                pack_B_kx8_fast(p, kc, B_start, B, incRowB, work, work_start); // XXX
            } else {
                pack_B_kx8(p, kc, B_start, B, incRowB, incColB, work, work_start); // XXX
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

        final int NR_Width = p.NR_Width; // 8
        final int MR_Height = p.MR_Height; // 8

        //
        // Compute AB = A*B
        //
        
//        for (int l = 0; l < kc; ++l) {
//            for (int j = 0; j < NR_Width; ++j) { // 6 j
//                float b_val = B_panel[B_panel_start + j]; // save the B value beforehand
//                int offset = j * MR_Height; // pre-compute the offset
//                for (int i = 0; i < 8; ++i) {
//                    // inner loop should now look a bit more "vectorizable" to the JIT
//                    AB[offset + i] += A_panel[A_panel_start + i] * b_val;
//                }
//            }
//            A_panel_start += MR_Height;
//            B_panel_start += NR_Width;
//        }
        
        
//        for (int l = 0; l < kc; ++l) {
//            // 1. Load a column of A (8 floats) in a register
//            // Since MR = 8, these values are perfectly aligned one after another in A_panel
//            FloatVector va = FloatVector.fromArray(SPECIES, A_panel, A_panel_start);
//
//            for (int j = 0; j < NR_Width; ++j) {
//                // 2. Load the b_val and "broadcast" it on all 8 positions of a register (Broadcast)
//                float b_val = B_panel[B_panel_start + j];
//                FloatVector vb = FloatVector.broadcast(SPECIES, b_val);
//
//                // 3. Load 8 floats from the current position in AB (8 floats)
//                int offset = j * MR_Height;
//                FloatVector vc = FloatVector.fromArray(SPECIES, AB, offset);
//
//                // 4. The Magic: vc = va * vb + vc (a single CPU instruction)
//                vc = va.fma(vb, vc);
//
//                // 5. Store it back into the AB array
//                vc.intoArray(AB, offset);
//            }
//            // After each l-step move to the next K element
//            A_panel_start += MR_Height;
//            B_panel_start += NR_Width;
//        }


        // 1. Initialize 8 accumulator registers (Vectors) with 0
        FloatVector c0 = FloatVector.zero(SPECIES);
        FloatVector c1 = FloatVector.zero(SPECIES);
        FloatVector c2 = FloatVector.zero(SPECIES);
        FloatVector c3 = FloatVector.zero(SPECIES);
        FloatVector c4 = FloatVector.zero(SPECIES);
        FloatVector c5 = FloatVector.zero(SPECIES);
        
        FloatVector c6 = FloatVector.zero(SPECIES);
        FloatVector c7 = FloatVector.zero(SPECIES);

        // 2. Main loop (only computations, almost no loads / stores!)
        for (int l = 0; l < kc; ++l) {
            FloatVector va = FloatVector.fromArray(SPECIES, A_panel, A_panel_start);

            // Broadcast of the 8 B values
            c0 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 0]), c0);
            c1 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 1]), c1);
            c2 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 2]), c2);
            c3 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 3]), c3);
            c4 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 4]), c4);
            c5 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 5]), c5);
            c6 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 6]), c6);
            c7 = va.fma(FloatVector.broadcast(SPECIES, B_panel[B_panel_start + 7]), c7);

            A_panel_start += MR_Height; // 8
            B_panel_start += NR_Width;  // 8
        }

        // 3. Now, only at the very end, write the results back into the AB array
        // (Could also use Math.fma() with the existing values in AB, if necessary)
        c0.intoArray(AB, 0);
        c1.intoArray(AB, 8);
        c2.intoArray(AB, 16);
        c3.intoArray(AB, 24);
        c4.intoArray(AB, 32);
        c5.intoArray(AB, 40);
        c6.intoArray(AB, 48);
        c7.intoArray(AB, 56);


        //
        // Update C <- beta*C
        //
        if (beta != 1.0f) {
//            sgemm_micro_betaMulC(p, beta, C_panel_start, C_panel, incColC); // XXX
            sgemm_micro_betaMulC_MR8(p, beta, C_panel_start, C_panel, incColC); // XXX
        }

        //
        // Update C <- C + alpha*AB (note: the case alpha==0.0 was already
        // treated in the above layer dgemm)
        //
//        sgemm_micro_plusAlphaAB(p, alpha, C_panel_start, C_panel, incColC, AB); // XXX
        sgemm_micro_plusAlphaAB_MR8(p, alpha, C_panel_start, C_panel, incColC, AB); // XXX
    }

    //
    // Update C <- beta*C
    //
    private static void sgemm_micro_betaMulC(P p, float beta, int C_panel_start, float[] C_panel,
            int incColC) {

        final int NR_Width = p.NR_Width;
        final int MR_Height = p.MR_Height;

        if (beta == 0.0f) {
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

    private static void sgemm_micro_betaMulC_MR8(P p, float beta, int C_panel_start, float[] C_panel,
            int incColC) {

        final int NR_Width = p.NR_Width;

        if (beta == 0.0f) {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] = 0.0f;
                C_panel[base_C + 1] = 0.0f;
                C_panel[base_C + 2] = 0.0f;
                C_panel[base_C + 3] = 0.0f;
                C_panel[base_C + 4] = 0.0f;
                C_panel[base_C + 5] = 0.0f;
                C_panel[base_C + 6] = 0.0f;
                C_panel[base_C + 7] = 0.0f;
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] *= beta;
                C_panel[base_C + 1] *= beta;
                C_panel[base_C + 2] *= beta;
                C_panel[base_C + 3] *= beta;
                C_panel[base_C + 4] *= beta;
                C_panel[base_C + 5] *= beta;
                C_panel[base_C + 6] *= beta;
                C_panel[base_C + 7] *= beta;
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

    private static void sgemm_micro_plusAlphaAB_MR8(P p, float alpha, int C_panel_start, float[] C_panel,
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
                C_panel[base_C + 4] += AB[4 + jIdx];
                C_panel[base_C + 5] += AB[5 + jIdx];
                C_panel[base_C + 6] += AB[6 + jIdx];
                C_panel[base_C + 7] += AB[7 + jIdx];
            }
        } else {
            for (int j = 0; j < NR_Width; ++j) {
                int jIdx = j * MR_Height;
                int base_C = C_panel_start + j * incColC;
                C_panel[base_C + 0] += alpha * AB[0 + jIdx];
                C_panel[base_C + 1] += alpha * AB[1 + jIdx];
                C_panel[base_C + 2] += alpha * AB[2 + jIdx];
                C_panel[base_C + 3] += alpha * AB[3 + jIdx];
                C_panel[base_C + 4] += alpha * AB[4 + jIdx];
                C_panel[base_C + 5] += alpha * AB[5 + jIdx];
                C_panel[base_C + 6] += alpha * AB[6 + jIdx];
                C_panel[base_C + 7] += alpha * AB[7 + jIdx];
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
