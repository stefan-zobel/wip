/*
 * Copyright 2020 Stefan Zobel
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
package net.jamu.matrix;

import java.util.Arrays;

/**
 * A Java port of the MATLAB <b>expm2.m</b> source code available online at
 * http://www.gicas.uji.es/Research/MatrixExp.html
 * <p>
 * Compare the paper "Bader, P.; Blanes, S.; Casas, F.: Computing the Matrix
 * Exponential with an Optimized Taylor Polynomial Approximation. Mathematics
 * 2019, 7, 1174." for the implementation approach.
 * <p>
 * 
 * @see https://doi.org/10.3390/math7121174
 */
public final class Expm {

    public static void main(String[] args) {

        // https://de.mathworks.com/help/matlab/ref/expm.html
        MatrixD A = Matrices.createD(3, 3);
        A.set(0, 0, 1);
        A.set(0, 1, 1);
        A.set(0, 2, 0);
        A.set(1, 0, 0);
        A.set(1, 1, 0);
        A.set(1, 2, 2);
        A.set(2, 0, 0);
        A.set(2, 1, 0);
        A.set(2, 2, -1);
        System.out.println(A);
        MatrixD eA = expmD(A, norm(A));
        System.out.println(eA);

        // A4: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A2 = Matrices.createD(2, 2);
        A2.set(0, 0, 0.25);
        A2.set(0, 1, 0.25);
        A2.set(1, 0, 0);
        A2.set(1, 1, 0);
        System.out.println(A2);
        MatrixD eA2 = expmD(A2, norm(A2));
        System.out.println(eA2);

        // A5: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A3 = Matrices.createD(2, 2);
        A3.set(0, 0, 0);
        A3.set(0, 1, 0.02);
        A3.set(1, 0, 0);
        A3.set(1, 1, 0);
        System.out.println(A3);
        MatrixD eA3 = expmD(A3, norm(A3));
        System.out.println(eA3);

        // A3: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A4 = Matrices.createD(3, 3);
        A4.set(0, 0, -131);
        A4.set(0, 1, 19);
        A4.set(0, 2, 18);
        A4.set(1, 0, -390);
        A4.set(1, 1, 56);
        A4.set(1, 2, 54);
        A4.set(2, 0, -387);
        A4.set(2, 1, 57);
        A4.set(2, 2, 52);
        A4 = A4.transpose();
        System.out.println(A4);
        MatrixD eA4 = expmD(A4, norm(A4));
        System.out.println(eA4);

        // A1: https://gist.github.com/sdewaele/2c176cb634280cf8a23c5970739cea0e
        MatrixD A5 = Matrices.createD(3, 3);
        A5.set(0, 0, 4);
        A5.set(0, 1, 2);
        A5.set(0, 2, 0);
        A5.set(1, 0, 1);
        A5.set(1, 1, 4);
        A5.set(1, 2, 1);
        A5.set(2, 0, 1);
        A5.set(2, 1, 1);
        A5.set(2, 2, 4);
        A5 = A5.transpose();
        System.out.println(A5);
        MatrixD eA5 = expmD(A5, norm(A5));
        System.out.println(eA5);
    }

    private static double norm(MatrixD A) {
        double max = Double.NEGATIVE_INFINITY;
        double[] a = A.getArrayUnsafe();
        for (int i = 0; i < a.length; ++i) {
            double x = Math.abs(a[i]);
            if (x > max) {
                max = x;
            }
        }
        return max;
    }

    static MatrixD expmD(MatrixD A, double norm) {
        MatrixD F = null;
        if (!isDoubleScalingRequired(norm)) {
            // no scaling and squaring is required
            for (int i = 0; i < theta_d.length; ++i) {
                if (norm <= theta_d[i]) {
                    F = getTaylorApproximant(A, order[i]);
                    break;
                }
            }
        } else {
            F = scalingAndSquaring(A, norm);
        }

        if (F == null) {
            throw new IllegalStateException("missing Taylor approximant");
        }
        return F;
    }

    static MatrixF expmF(MatrixF A, float norm) {
        MatrixF F = null;
        if (!isFloatScalingRequired(norm)) {
            // no scaling and squaring is required
            for (int i = 0; i < theta_f.length; ++i) {
                if (norm <= theta_f[i]) {
                    F = getTaylorApproximant(A, order[i]);
                    break;
                }
            }
        } else {
            F = scalingAndSquaring(A, norm);
        }

        if (F == null) {
            throw new IllegalStateException("missing Taylor approximant");
        }
        return F;
    }

    static ComplexMatrixD expmComplexD(ComplexMatrixD A, double norm) {
        ComplexMatrixD F = null;
        if (!isDoubleScalingRequired(norm)) {
            // no scaling and squaring is required
            for (int i = 0; i < theta_d.length; ++i) {
                if (norm <= theta_d[i]) {
                    F = getTaylorApproximant(A, order[i]);
                    break;
                }
            }
        } else {
            F = scalingAndSquaring(A, norm);
        }

        if (F == null) {
            throw new IllegalStateException("missing Taylor approximant");
        }
        return F;
    }

    static ComplexMatrixF expmComplexF(ComplexMatrixF A, float norm) {
        ComplexMatrixF F = null;
        if (!isFloatScalingRequired(norm)) {
            // no scaling and squaring is required
            for (int i = 0; i < theta_f.length; ++i) {
                if (norm <= theta_f[i]) {
                    F = getTaylorApproximant(A, order[i]);
                    break;
                }
            }
        } else {
            F = scalingAndSquaring(A, norm);
        }

        if (F == null) {
            throw new IllegalStateException("missing Taylor approximant");
        }
        return F;
    }

    private static MatrixD scalingAndSquaring(MatrixD A, double norm) {
        // scaling
        int scale = getScaleD(norm);
        if (scale > 0) {
            A = A.copy().scaleInplace(1.0 / Math.pow(2.0, scale));
        }
        // determine approximant
        MatrixD F = taylor18D(A);
        // squaring
        if (scale > 0) {
            MatrixD tmp = Matrices.sameDimD(F);
            for (int i = 0; i < scale; ++i) {
                tmp = F.mult(F, tmp);
                F.setInplace(tmp);
            }
        }
        return F;
    }

    private static MatrixF scalingAndSquaring(MatrixF A, float norm) {
        // scaling
        int scale = getScaleF(norm);
        if (scale > 0) {
            A = A.copy().scaleInplace(1.0f / (float) Math.pow(2.0, scale));
        }
        // determine approximant
        MatrixF F = taylor18F(A);
        // squaring
        if (scale > 0) {
            MatrixF tmp = Matrices.sameDimF(F);
            for (int i = 0; i < scale; ++i) {
                tmp = F.mult(F, tmp);
                F.setInplace(tmp);
            }
        }
        return F;
    }

    private static ComplexMatrixD scalingAndSquaring(ComplexMatrixD A, double norm) {
        // scaling
        int scale = getScaleD(norm);
        if (scale > 0) {
            A = A.copy().scaleInplace(1.0 / Math.pow(2.0, scale), 0.0);
        }
        // determine approximant
        ComplexMatrixD F = taylor18ComplexD(A);
        // squaring
        if (scale > 0) {
            ComplexMatrixD tmp = Matrices.sameDimComplexD(F);
            for (int i = 0; i < scale; ++i) {
                tmp = F.mult(F, tmp);
                F.setInplace(tmp);
            }
        }
        return F;
    }

    private static ComplexMatrixF scalingAndSquaring(ComplexMatrixF A, float norm) {
        // scaling
        int scale = getScaleF(norm);
        if (scale > 0) {
            A = A.copy().scaleInplace(1.0f / (float) Math.pow(2.0, scale), 0.0f);
        }
        // determine approximant
        ComplexMatrixF F = taylor18ComplexF(A);
        // squaring
        if (scale > 0) {
            ComplexMatrixF tmp = Matrices.sameDimComplexF(F);
            for (int i = 0; i < scale; ++i) {
                tmp = F.mult(F, tmp);
                F.setInplace(tmp);
            }
        }
        return F;
    }

    private static int getScaleD(double norm) {
        if (norm <= theta_d_max) {
            throw new IllegalStateException("norm too small");
        }
        double adaptedNorm = norm / theta_d_max;
        int ceilLog2 = (int) Math.ceil(Math.log(adaptedNorm) / Math.log(2.0));
        return ceilLog2;
    }

    private static int getScaleF(float norm) {
        if (norm <= theta_f_max) {
            throw new IllegalStateException("norm too small");
        }
        double adaptedNorm = (double) norm / theta_f_max;
        int ceilLog2 = (int) Math.ceil(Math.log(adaptedNorm) / Math.log(2.0));
        return ceilLog2;
    }

    private static MatrixD getTaylorApproximant(MatrixD A, int order) {
        // Improved Paterson-Stockmeyer scheme, with Pade at end point
        switch (order) {
        case 1:
            return taylor1D(A);
        case 2:
            return taylor2D(A);
        case 4:
            return taylor4D(A);
        case 8:
            return taylor8D(A);
        case 12:
            return taylor12D(A);
        case 18:
            return taylor18D(A);
        default:
            throw new IllegalStateException("Unrecognized order: " + order);
        }
    }

    private static MatrixF getTaylorApproximant(MatrixF A, int order) {
        // Improved Paterson-Stockmeyer scheme, with Pade at end point
        switch (order) {
        case 1:
            return taylor1F(A);
        case 2:
            return taylor2F(A);
        case 4:
            return taylor4F(A);
        case 8:
            return taylor8F(A);
        case 12:
            return taylor12F(A);
        case 18:
            return taylor18F(A);
        default:
            throw new IllegalStateException("Unrecognized order: " + order);
        }
    }

    private static ComplexMatrixD getTaylorApproximant(ComplexMatrixD A, int order) {
        // Improved Paterson-Stockmeyer scheme, with Pade at end point
        switch (order) {
        case 1:
            return taylor1ComplexD(A);
        case 2:
            return taylor2ComplexD(A);
        case 4:
            return taylor4ComplexD(A);
        case 8:
            return taylor8ComplexD(A);
        case 12:
            return taylor12ComplexD(A);
        case 18:
            return taylor18ComplexD(A);
        default:
            throw new IllegalStateException("Unrecognized order: " + order);
        }
    }

    private static ComplexMatrixF getTaylorApproximant(ComplexMatrixF A, int order) {
        // Improved Paterson-Stockmeyer scheme, with Pade at end point
        switch (order) {
        case 1:
            return taylor1ComplexF(A);
        case 2:
            return taylor2ComplexF(A);
        case 4:
            return taylor4ComplexF(A);
        case 8:
            return taylor8ComplexF(A);
        case 12:
            return taylor12ComplexF(A);
        case 18:
            return taylor18ComplexF(A);
        default:
            throw new IllegalStateException("Unrecognized order: " + order);
        }
    }

    private static MatrixD taylor18D(MatrixD A) {
        MatrixD I = Matrices.identityD(A.numRows());
        MatrixD A2 = A.times(A);
        MatrixD A3 = A.times(A2);
        MatrixD A6 = A3.times(A3);

        MatrixD q31 = Matrices.sameDimD(A);
        q31 = q31.addInplace(a11_t18d, A);
        q31 = q31.addInplace(a21_t18d, A2);
        q31 = q31.addInplace(a31_t18d, A3);

        MatrixD q61 = Matrices.sameDimD(A);
        q61 = q61.addInplace(b11_t18d, A);
        q61 = q61.addInplace(b21_t18d, A2);
        q61 = q61.addInplace(b31_t18d, A3);
        q61 = q61.addInplace(b61_t18d, A6);

        MatrixD q62 = Matrices.sameDimD(A);
        q62 = q62.addInplace(b02_t18d, I);
        q62 = q62.addInplace(b12_t18d, A);
        q62 = q62.addInplace(b22_t18d, A2);
        q62 = q62.addInplace(b32_t18d, A3);
        q62 = q62.addInplace(b62_t18d, A6);

        MatrixD q63 = Matrices.sameDimD(A);
        q63 = q63.addInplace(b03_t18d, I);
        q63 = q63.addInplace(b13_t18d, A);
        q63 = q63.addInplace(b23_t18d, A2);
        q63 = q63.addInplace(b33_t18d, A3);
        q63 = q63.addInplace(b63_t18d, A6);

        MatrixD q64 = Matrices.sameDimD(A);
        q64 = q64.addInplace(b24_t18d, A2);
        q64 = q64.addInplace(b34_t18d, A3);
        q64 = q64.addInplace(b64_t18d, A6);

        MatrixD q91 = q31.times(q64);
        q91 = q91.addInplace(q63);

        MatrixD q18 = q62.addInplace(q91);
        q18 = q18.times(q91);
        q18 = q61.addInplace(q18);

        MatrixD E = q18;

        return E;
    }

    private static MatrixF taylor18F(MatrixF A) {
        MatrixF I = Matrices.identityF(A.numRows());
        MatrixF A2 = A.times(A);
        MatrixF A3 = A.times(A2);
        MatrixF A6 = A3.times(A3);

        MatrixF q31 = Matrices.sameDimF(A);
        q31 = q31.addInplace(a11_t18f, A);
        q31 = q31.addInplace(a21_t18f, A2);
        q31 = q31.addInplace(a31_t18f, A3);

        MatrixF q61 = Matrices.sameDimF(A);
        q61 = q61.addInplace(b11_t18f, A);
        q61 = q61.addInplace(b21_t18f, A2);
        q61 = q61.addInplace(b31_t18f, A3);
        q61 = q61.addInplace(b61_t18f, A6);

        MatrixF q62 = Matrices.sameDimF(A);
        q62 = q62.addInplace(b02_t18f, I);
        q62 = q62.addInplace(b12_t18f, A);
        q62 = q62.addInplace(b22_t18f, A2);
        q62 = q62.addInplace(b32_t18f, A3);
        q62 = q62.addInplace(b62_t18f, A6);

        MatrixF q63 = Matrices.sameDimF(A);
        q63 = q63.addInplace(b03_t18f, I);
        q63 = q63.addInplace(b13_t18f, A);
        q63 = q63.addInplace(b23_t18f, A2);
        q63 = q63.addInplace(b33_t18f, A3);
        q63 = q63.addInplace(b63_t18f, A6);

        MatrixF q64 = Matrices.sameDimF(A);
        q64 = q64.addInplace(b24_t18f, A2);
        q64 = q64.addInplace(b34_t18f, A3);
        q64 = q64.addInplace(b64_t18f, A6);

        MatrixF q91 = q31.times(q64);
        q91 = q91.addInplace(q63);

        MatrixF q18 = q62.addInplace(q91);
        q18 = q18.times(q91);
        q18 = q61.addInplace(q18);

        MatrixF E = q18;

        return E;
    }

    private static ComplexMatrixD taylor18ComplexD(ComplexMatrixD A) {
        ComplexMatrixD I = Matrices.identityComplexD(A.numRows());
        ComplexMatrixD A2 = A.times(A);
        ComplexMatrixD A3 = A.times(A2);
        ComplexMatrixD A6 = A3.times(A3);

        ComplexMatrixD q31 = Matrices.sameDimComplexD(A);
        q31 = q31.addInplace(a11_t18d, 0.0, A);
        q31 = q31.addInplace(a21_t18d, 0.0, A2);
        q31 = q31.addInplace(a31_t18d, 0.0, A3);

        ComplexMatrixD q61 = Matrices.sameDimComplexD(A);
        q61 = q61.addInplace(b11_t18d, 0.0, A);
        q61 = q61.addInplace(b21_t18d, 0.0, A2);
        q61 = q61.addInplace(b31_t18d, 0.0, A3);
        q61 = q61.addInplace(b61_t18d, 0.0, A6);

        ComplexMatrixD q62 = Matrices.sameDimComplexD(A);
        q62 = q62.addInplace(b02_t18d, 0.0, I);
        q62 = q62.addInplace(b12_t18d, 0.0, A);
        q62 = q62.addInplace(b22_t18d, 0.0, A2);
        q62 = q62.addInplace(b32_t18d, 0.0, A3);
        q62 = q62.addInplace(b62_t18d, 0.0, A6);

        ComplexMatrixD q63 = Matrices.sameDimComplexD(A);
        q63 = q63.addInplace(b03_t18d, 0.0, I);
        q63 = q63.addInplace(b13_t18d, 0.0, A);
        q63 = q63.addInplace(b23_t18d, 0.0, A2);
        q63 = q63.addInplace(b33_t18d, 0.0, A3);
        q63 = q63.addInplace(b63_t18d, 0.0, A6);

        ComplexMatrixD q64 = Matrices.sameDimComplexD(A);
        q64 = q64.addInplace(b24_t18d, 0.0, A2);
        q64 = q64.addInplace(b34_t18d, 0.0, A3);
        q64 = q64.addInplace(b64_t18d, 0.0, A6);

        ComplexMatrixD q91 = q31.times(q64);
        q91 = q91.addInplace(q63);

        ComplexMatrixD q18 = q62.addInplace(q91);
        q18 = q18.times(q91);
        q18 = q61.addInplace(q18);

        ComplexMatrixD E = q18;

        return E;
    }

    private static ComplexMatrixF taylor18ComplexF(ComplexMatrixF A) {
        ComplexMatrixF I = Matrices.identityComplexF(A.numRows());
        ComplexMatrixF A2 = A.times(A);
        ComplexMatrixF A3 = A.times(A2);
        ComplexMatrixF A6 = A3.times(A3);

        ComplexMatrixF q31 = Matrices.sameDimComplexF(A);
        q31 = q31.addInplace(a11_t18f, 0.0f, A);
        q31 = q31.addInplace(a21_t18f, 0.0f, A2);
        q31 = q31.addInplace(a31_t18f, 0.0f, A3);

        ComplexMatrixF q61 = Matrices.sameDimComplexF(A);
        q61 = q61.addInplace(b11_t18f, 0.0f, A);
        q61 = q61.addInplace(b21_t18f, 0.0f, A2);
        q61 = q61.addInplace(b31_t18f, 0.0f, A3);
        q61 = q61.addInplace(b61_t18f, 0.0f, A6);

        ComplexMatrixF q62 = Matrices.sameDimComplexF(A);
        q62 = q62.addInplace(b02_t18f, 0.0f, I);
        q62 = q62.addInplace(b12_t18f, 0.0f, A);
        q62 = q62.addInplace(b22_t18f, 0.0f, A2);
        q62 = q62.addInplace(b32_t18f, 0.0f, A3);
        q62 = q62.addInplace(b62_t18f, 0.0f, A6);

        ComplexMatrixF q63 = Matrices.sameDimComplexF(A);
        q63 = q63.addInplace(b03_t18f, 0.0f, I);
        q63 = q63.addInplace(b13_t18f, 0.0f, A);
        q63 = q63.addInplace(b23_t18f, 0.0f, A2);
        q63 = q63.addInplace(b33_t18f, 0.0f, A3);
        q63 = q63.addInplace(b63_t18f, 0.0f, A6);

        ComplexMatrixF q64 = Matrices.sameDimComplexF(A);
        q64 = q64.addInplace(b24_t18f, 0.0f, A2);
        q64 = q64.addInplace(b34_t18f, 0.0f, A3);
        q64 = q64.addInplace(b64_t18f, 0.0f, A6);

        ComplexMatrixF q91 = q31.times(q64);
        q91 = q91.addInplace(q63);

        ComplexMatrixF q18 = q62.addInplace(q91);
        q18 = q18.times(q91);
        q18 = q61.addInplace(q18);

        ComplexMatrixF E = q18;

        return E;
    }

    private static MatrixD taylor12D(MatrixD A) {
        MatrixD I = Matrices.identityD(A.numRows());
        MatrixD A2 = A.times(A);
        MatrixD A3 = A.times(A2);

        MatrixD q31 = Matrices.sameDimD(A);
        q31 = q31.addInplace(a01_t12d, I);
        q31 = q31.addInplace(a11_t12d, A);
        q31 = q31.addInplace(a21_t12d, A2);
        q31 = q31.addInplace(a31_t12d, A3);

        MatrixD q32 = Matrices.sameDimD(A);
        q32 = q32.addInplace(a02_t12d, I);
        q32 = q32.addInplace(a12_t12d, A);
        q32 = q32.addInplace(a22_t12d, A2);
        q32 = q32.addInplace(a32_t12d, A3);

        MatrixD q33 = Matrices.sameDimD(A);
        q33 = q33.addInplace(a03_t12d, I);
        q33 = q33.addInplace(a13_t12d, A);
        q33 = q33.addInplace(a23_t12d, A2);
        q33 = q33.addInplace(a33_t12d, A3);

        MatrixD q34 = Matrices.sameDimD(A);
        q34 = q34.addInplace(a14_t12d, A);
        q34 = q34.addInplace(a24_t12d, A2);
        q34 = q34.addInplace(a34_t12d, A3);

        MatrixD q61 = q34.times(q34);
        q61 = q33.addInplace(q61);

        MatrixD E = q32.addInplace(q61);
        q34 = q34.zeroInplace();
        E = E.mult(q61, q34);
        E = q31.addInplace(E);

        return E;
    }

    private static MatrixF taylor12F(MatrixF A) {
        MatrixF I = Matrices.identityF(A.numRows());
        MatrixF A2 = A.times(A);
        MatrixF A3 = A.times(A2);

        MatrixF q31 = Matrices.sameDimF(A);
        q31 = q31.addInplace(a01_t12f, I);
        q31 = q31.addInplace(a11_t12f, A);
        q31 = q31.addInplace(a21_t12f, A2);
        q31 = q31.addInplace(a31_t12f, A3);

        MatrixF q32 = Matrices.sameDimF(A);
        q32 = q32.addInplace(a02_t12f, I);
        q32 = q32.addInplace(a12_t12f, A);
        q32 = q32.addInplace(a22_t12f, A2);
        q32 = q32.addInplace(a32_t12f, A3);

        MatrixF q33 = Matrices.sameDimF(A);
        q33 = q33.addInplace(a03_t12f, I);
        q33 = q33.addInplace(a13_t12f, A);
        q33 = q33.addInplace(a23_t12f, A2);
        q33 = q33.addInplace(a33_t12f, A3);

        MatrixF q34 = Matrices.sameDimF(A);
        q34 = q34.addInplace(a14_t12f, A);
        q34 = q34.addInplace(a24_t12f, A2);
        q34 = q34.addInplace(a34_t12f, A3);

        MatrixF q61 = q34.times(q34);
        q61 = q33.addInplace(q61);

        MatrixF E = q32.addInplace(q61);
        q34 = q34.zeroInplace();
        E = E.mult(q61, q34);
        E = q31.addInplace(E);

        return E;
    }

    private static ComplexMatrixD taylor12ComplexD(ComplexMatrixD A) {
        ComplexMatrixD I = Matrices.identityComplexD(A.numRows());
        ComplexMatrixD A2 = A.times(A);
        ComplexMatrixD A3 = A.times(A2);

        ComplexMatrixD q31 = Matrices.sameDimComplexD(A);
        q31 = q31.addInplace(a01_t12d, 0.0, I);
        q31 = q31.addInplace(a11_t12d, 0.0, A);
        q31 = q31.addInplace(a21_t12d, 0.0, A2);
        q31 = q31.addInplace(a31_t12d, 0.0, A3);

        ComplexMatrixD q32 = Matrices.sameDimComplexD(A);
        q32 = q32.addInplace(a02_t12d, 0.0, I);
        q32 = q32.addInplace(a12_t12d, 0.0, A);
        q32 = q32.addInplace(a22_t12d, 0.0, A2);
        q32 = q32.addInplace(a32_t12d, 0.0, A3);

        ComplexMatrixD q33 = Matrices.sameDimComplexD(A);
        q33 = q33.addInplace(a03_t12d, 0.0, I);
        q33 = q33.addInplace(a13_t12d, 0.0, A);
        q33 = q33.addInplace(a23_t12d, 0.0, A2);
        q33 = q33.addInplace(a33_t12d, 0.0, A3);

        ComplexMatrixD q34 = Matrices.sameDimComplexD(A);
        q34 = q34.addInplace(a14_t12d, 0.0, A);
        q34 = q34.addInplace(a24_t12d, 0.0, A2);
        q34 = q34.addInplace(a34_t12d, 0.0, A3);

        ComplexMatrixD q61 = q34.times(q34);
        q61 = q33.addInplace(q61);

        ComplexMatrixD E = q32.addInplace(q61);
        q34 = q34.zeroInplace();
        E = E.mult(q61, q34);
        E = q31.addInplace(E);

        return E;
    }

    private static ComplexMatrixF taylor12ComplexF(ComplexMatrixF A) {
        ComplexMatrixF I = Matrices.identityComplexF(A.numRows());
        ComplexMatrixF A2 = A.times(A);
        ComplexMatrixF A3 = A.times(A2);

        ComplexMatrixF q31 = Matrices.sameDimComplexF(A);
        q31 = q31.addInplace(a01_t12f, 0.0f, I);
        q31 = q31.addInplace(a11_t12f, 0.0f, A);
        q31 = q31.addInplace(a21_t12f, 0.0f, A2);
        q31 = q31.addInplace(a31_t12f, 0.0f, A3);

        ComplexMatrixF q32 = Matrices.sameDimComplexF(A);
        q32 = q32.addInplace(a02_t12f, 0.0f, I);
        q32 = q32.addInplace(a12_t12f, 0.0f, A);
        q32 = q32.addInplace(a22_t12f, 0.0f, A2);
        q32 = q32.addInplace(a32_t12f, 0.0f, A3);

        ComplexMatrixF q33 = Matrices.sameDimComplexF(A);
        q33 = q33.addInplace(a03_t12f, 0.0f, I);
        q33 = q33.addInplace(a13_t12f, 0.0f, A);
        q33 = q33.addInplace(a23_t12f, 0.0f, A2);
        q33 = q33.addInplace(a33_t12f, 0.0f, A3);

        ComplexMatrixF q34 = Matrices.sameDimComplexF(A);
        q34 = q34.addInplace(a14_t12f, 0.0f, A);
        q34 = q34.addInplace(a24_t12f, 0.0f, A2);
        q34 = q34.addInplace(a34_t12f, 0.0f, A3);

        ComplexMatrixF q61 = q34.times(q34);
        q61 = q33.addInplace(q61);

        ComplexMatrixF E = q32.addInplace(q61);
        q34 = q34.zeroInplace();
        E = E.mult(q61, q34);
        E = q31.addInplace(E);

        return E;
    }

    private static MatrixD taylor8D(MatrixD A) {
        MatrixD I = Matrices.identityD(A.numRows());
        MatrixD A2 = A.times(A);

        MatrixD tmp = Matrices.sameDimD(A);
        tmp = tmp.addInplace(a1_t8d, A);
        tmp = tmp.addInplace(a2_t8d, A2);
        MatrixD A4 = A2.times(tmp);

        tmp = tmp.zeroInplace();
        tmp = tmp.addInplace(x3_t8d, A2);
        tmp = tmp.addInplace(A4);
        MatrixD tmp2 = Matrices.sameDimD(A);
        tmp2 = tmp2.addInplace(c0_t8d, I);
        tmp2 = tmp2.addInplace(c1_t8d, A);
        tmp2 = tmp2.addInplace(c2_t8d, A2);
        tmp2 = tmp2.addInplace(c4_t8d, A4);
        MatrixD A8 = tmp.mult(tmp2, A4);

        MatrixD E = tmp.zeroInplace();
        E = E.addInplace(I);
        E = E.addInplace(A);
        E = E.addInplace(u2_t8d, A2);
        E = E.addInplace(A8);

        return E;
    }

    private static MatrixF taylor8F(MatrixF A) {
        MatrixF I = Matrices.identityF(A.numRows());
        MatrixF A2 = A.times(A);

        MatrixF tmp = Matrices.sameDimF(A);
        tmp = tmp.addInplace(a1_t8f, A);
        tmp = tmp.addInplace(a2_t8f, A2);
        MatrixF A4 = A2.times(tmp);

        tmp = tmp.zeroInplace();
        tmp = tmp.addInplace(x3_t8f, A2);
        tmp = tmp.addInplace(A4);
        MatrixF tmp2 = Matrices.sameDimF(A);
        tmp2 = tmp2.addInplace(c0_t8f, I);
        tmp2 = tmp2.addInplace(c1_t8f, A);
        tmp2 = tmp2.addInplace(c2_t8f, A2);
        tmp2 = tmp2.addInplace(c4_t8f, A4);
        MatrixF A8 = tmp.mult(tmp2, A4);

        MatrixF E = tmp.zeroInplace();
        E = E.addInplace(I);
        E = E.addInplace(A);
        E = E.addInplace(u2_t8f, A2);
        E = E.addInplace(A8);

        return E;
    }

    private static ComplexMatrixD taylor8ComplexD(ComplexMatrixD A) {
        ComplexMatrixD I = Matrices.identityComplexD(A.numRows());
        ComplexMatrixD A2 = A.times(A);

        ComplexMatrixD tmp = Matrices.sameDimComplexD(A);
        tmp = tmp.addInplace(a1_t8d, 0.0, A);
        tmp = tmp.addInplace(a2_t8d, 0.0, A2);
        ComplexMatrixD A4 = A2.times(tmp);

        tmp = tmp.zeroInplace();
        tmp = tmp.addInplace(x3_t8d, 0.0, A2);
        tmp = tmp.addInplace(A4);
        ComplexMatrixD tmp2 = Matrices.sameDimComplexD(A);
        tmp2 = tmp2.addInplace(c0_t8d, 0.0, I);
        tmp2 = tmp2.addInplace(c1_t8d, 0.0, A);
        tmp2 = tmp2.addInplace(c2_t8d, 0.0, A2);
        tmp2 = tmp2.addInplace(c4_t8d, 0.0, A4);
        ComplexMatrixD A8 = tmp.mult(tmp2, A4);

        ComplexMatrixD E = tmp.zeroInplace();
        E = E.addInplace(I);
        E = E.addInplace(A);
        E = E.addInplace(u2_t8d, 0.0, A2);
        E = E.addInplace(A8);

        return E;
    }

    private static ComplexMatrixF taylor8ComplexF(ComplexMatrixF A) {
        ComplexMatrixF I = Matrices.identityComplexF(A.numRows());
        ComplexMatrixF A2 = A.times(A);

        ComplexMatrixF tmp = Matrices.sameDimComplexF(A);
        tmp = tmp.addInplace(a1_t8f, 0.0f, A);
        tmp = tmp.addInplace(a2_t8f, 0.0f, A2);
        ComplexMatrixF A4 = A2.times(tmp);

        tmp = tmp.zeroInplace();
        tmp = tmp.addInplace(x3_t8f, 0.0f, A2);
        tmp = tmp.addInplace(A4);
        ComplexMatrixF tmp2 = Matrices.sameDimComplexF(A);
        tmp2 = tmp2.addInplace(c0_t8f, 0.0f, I);
        tmp2 = tmp2.addInplace(c1_t8f, 0.0f, A);
        tmp2 = tmp2.addInplace(c2_t8f, 0.0f, A2);
        tmp2 = tmp2.addInplace(c4_t8f, 0.0f, A4);
        ComplexMatrixF A8 = tmp.mult(tmp2, A4);

        ComplexMatrixF E = tmp.zeroInplace();
        E = E.addInplace(I);
        E = E.addInplace(A);
        E = E.addInplace(u2_t8f, 0.0f, A2);
        E = E.addInplace(A8);

        return E;
    }

    private static MatrixD taylor4D(MatrixD A) {
        MatrixD A2 = A.times(A);

        MatrixD E = Matrices.identityD(A.numRows());
        E = E.addInplace(A);

        double[] diagonal = new double[A.numRows()];
        Arrays.fill(diagonal, 0.5);
        MatrixD I_half = Matrices.diagD(diagonal);

        MatrixD tmp = Matrices.sameDimD(A);
        tmp = tmp.addInplace(I_half);
        tmp = tmp.addInplace(1.0 / 6.0, A);
        tmp = tmp.addInplace(1.0 / 24.0, A2);

        I_half = I_half.zeroInplace();
        MatrixD snd = A2.mult(tmp, I_half);

        E = E.addInplace(snd);

        return E;
    }

    private static MatrixF taylor4F(MatrixF A) {
        MatrixF A2 = A.times(A);

        MatrixF E = Matrices.identityF(A.numRows());
        E = E.addInplace(A);

        float[] diagonal = new float[A.numRows()];
        Arrays.fill(diagonal, 0.5f);
        MatrixF I_half = Matrices.diagF(diagonal);

        MatrixF tmp = Matrices.sameDimF(A);
        tmp = tmp.addInplace(I_half);
        tmp = tmp.addInplace((float) (1.0 / 6.0), A);
        tmp = tmp.addInplace((float) (1.0 / 24.0), A2);

        I_half = I_half.zeroInplace();
        MatrixF snd = A2.mult(tmp, I_half);

        E = E.addInplace(snd);

        return E;
    }

    private static ComplexMatrixD taylor4ComplexD(ComplexMatrixD A) {
        ComplexMatrixD A2 = A.times(A);

        ComplexMatrixD E = Matrices.identityComplexD(A.numRows());
        E = E.addInplace(A);

        ComplexMatrixD I_half = Matrices.identityComplexD(A.numRows());
        I_half = I_half.scaleInplace(0.5, 0.0);

        ComplexMatrixD tmp = Matrices.sameDimComplexD(A);
        tmp = tmp.addInplace(I_half);
        tmp = tmp.addInplace(1.0 / 6.0, 0.0, A);
        tmp = tmp.addInplace(1.0 / 24.0, 0.0, A2);

        I_half = I_half.zeroInplace();
        ComplexMatrixD snd = A2.mult(tmp, I_half);

        E = E.addInplace(snd);

        return E;
    }

    private static ComplexMatrixF taylor4ComplexF(ComplexMatrixF A) {
        ComplexMatrixF A2 = A.times(A);

        ComplexMatrixF E = Matrices.identityComplexF(A.numRows());
        E = E.addInplace(A);

        ComplexMatrixF I_half = Matrices.identityComplexF(A.numRows());
        I_half = I_half.scaleInplace(0.5f, 0.0f);

        ComplexMatrixF tmp = Matrices.sameDimComplexF(A);
        tmp = tmp.addInplace(I_half);
        tmp = tmp.addInplace((float) (1.0 / 6.0), 0.0f, A);
        tmp = tmp.addInplace((float) (1.0 / 24.0), 0.0f, A2);

        I_half = I_half.zeroInplace();
        ComplexMatrixF snd = A2.mult(tmp, I_half);

        E = E.addInplace(snd);

        return E;
    }

    private static MatrixD taylor2D(MatrixD A) {
        MatrixD A2_half = A.times(A);
        A2_half = A2_half.scaleInplace(0.5);

        MatrixD E = Matrices.identityD(A.numRows());
        E = E.addInplace(A);
        E = E.addInplace(A2_half);

        return E;
    }

    private static MatrixF taylor2F(MatrixF A) {
        MatrixF A2_half = A.times(A);
        A2_half = A2_half.scaleInplace(0.5f);

        MatrixF E = Matrices.identityF(A.numRows());
        E = E.addInplace(A);
        E = E.addInplace(A2_half);

        return E;
    }

    private static ComplexMatrixD taylor2ComplexD(ComplexMatrixD A) {
        ComplexMatrixD A2_half = A.times(A);
        A2_half = A2_half.scaleInplace(0.5, 0.0);

        ComplexMatrixD E = Matrices.identityComplexD(A.numRows());
        E = E.addInplace(A);
        E = E.addInplace(A2_half);

        return E;
    }

    private static ComplexMatrixF taylor2ComplexF(ComplexMatrixF A) {
        ComplexMatrixF A2_half = A.times(A);
        A2_half = A2_half.scaleInplace(0.5f, 0.0f);

        ComplexMatrixF E = Matrices.identityComplexF(A.numRows());
        E = E.addInplace(A);
        E = E.addInplace(A2_half);

        return E;
    }

    private static MatrixD taylor1D(MatrixD A) {
        MatrixD E = Matrices.identityD(A.numRows());
        E = E.addInplace(A);
        return E;
    }

    private static MatrixF taylor1F(MatrixF A) {
        MatrixF E = Matrices.identityF(A.numRows());
        E = E.addInplace(A);
        return E;
    }

    private static ComplexMatrixD taylor1ComplexD(ComplexMatrixD A) {
        ComplexMatrixD E = Matrices.identityComplexD(A.numRows());
        E = E.addInplace(A);
        return E;
    }

    private static ComplexMatrixF taylor1ComplexF(ComplexMatrixF A) {
        ComplexMatrixF E = Matrices.identityComplexF(A.numRows());
        E = E.addInplace(A);
        return E;
    }

    private static boolean isDoubleScalingRequired(double norm) {
        return norm > theta_d_max;
    }

    private static boolean isFloatScalingRequired(float norm) {
        return norm > theta_f_max;
    }

    private Expm() {
        throw new AssertionError();
    }

    private static final double theta_d_max = 1.090863719290036e+00;
    private static final double theta_f_max = 3.010066362817634e+00;

    //@formatter:off
    private static final int[] order =
        {
            1,
            2,
            4,
            8,
            12,
            18
        };
    //@formatter:on

    //@formatter:off
    private static final double[] theta_d =
        {
            2.220446049250313e-16,  // order = 1
            2.580956802971767e-08,  // order = 2
            3.397168839976962e-04,  // order = 4
            4.991228871115323e-02,  // order = 8
            2.996158913811580e-01,  // order = 12
            theta_d_max             // order = 18
        };
    //@formatter:on

    //@formatter:off
    private static final double[] theta_f =
        {
            1.192092800768788e-07,  // order = 1
            5.978858893805233e-04,  // order = 2
            5.116619363445086e-02,  // order = 4
            5.800524627688768e-01,  // order = 8
            1.461661507209034e+00,  // order = 12
            theta_f_max             // order = 18
        };
    //@formatter:on

    // coefficients for the double-precision degree 18 Taylor polynomial T18
    private static final double a11_t18d = -0.100365581030144620014614939405;
    private static final double a21_t18d = -0.00802924648241156960116919515244;
    private static final double a31_t18d = -0.000892138498045729955685466128049;
    private static final double b11_t18d = 0.3978497494996450761451961277102845756965081084076856223845951607640145373149032030404660339703426170;
    private static final double b21_t18d = 1.367837784604117199225237068782228242106453540654915795267462332707000476284638745738812082761860458;
    private static final double b31_t18d = 0.4982896225253826775568588172622733561849181397319696269923450773179744578906675427707618377504305561;
    private static final double b61_t18d = -0.0006378981945947233092415500564919285518773827581013332055148653241353120789646323186965398317523194760;
    private static final double b02_t18d = -10.96763960529620625935174626753684124863041876254774214673058385106461743913502064396554589372626845;
    private static final double b12_t18d = 1.680158138789061971827854017248105095278579593010566858091585875627364747895724070033586802947436157;
    private static final double b22_t18d = 0.05717798464788655127028717132252481317274975093707600394506105236273081373356534970690710643085727120;
    private static final double b32_t18d = -0.006982101224880520842904664838015531664389838152077753258353447125605205335288966277257918925881337834;
    private static final double b62_t18d = 0.00003349750170860705383133673406684398020225996444991565389728295589367037178816169580298011831485225359;
    private static final double b03_t18d = -0.09043168323908105619714688770183495499470866281162649735086602288456671216199491949073419844120202066;
    private static final double b13_t18d = -0.06764045190713819075600799003797074854562826013273060070581796689746210078264908587143648477465431597;
    private static final double b23_t18d = 0.06759613017704596460827991950789370565071000768138331455747136830953224978586305376886338214283464385;
    private static final double b33_t18d = 0.02955525704293155274260691822422312437473046472743510159951445245685893720550532525991666174704105350;
    private static final double b63_t18d = -0.00001391802575160607011247399793437424028074102305234870853220069004278819595344896368546425681168813708;
    private static final double b24_t18d = -0.0923364619367118592764570775143;
    private static final double b34_t18d = -0.0169364939002081717191385115723;
    private static final double b64_t18d = -0.0000140086798182036159794363205726;

    // coefficients for the single-precision degree 18 Taylor polynomial T18
    private static final float a11_t18f = (float) a11_t18d;
    private static final float a21_t18f = (float) a21_t18d;
    private static final float a31_t18f = (float) a31_t18d;
    private static final float b11_t18f = (float) b11_t18d;
    private static final float b21_t18f = (float) b21_t18d;
    private static final float b31_t18f = (float) b31_t18d;
    private static final float b61_t18f = (float) b61_t18d;
    private static final float b02_t18f = (float) b02_t18d;
    private static final float b12_t18f = (float) b12_t18d;
    private static final float b22_t18f = (float) b22_t18d;
    private static final float b32_t18f = (float) b32_t18d;
    private static final float b62_t18f = (float) b62_t18d;
    private static final float b03_t18f = (float) b03_t18d;
    private static final float b13_t18f = (float) b13_t18d;
    private static final float b23_t18f = (float) b23_t18d;
    private static final float b33_t18f = (float) b33_t18d;
    private static final float b63_t18f = (float) b63_t18d;
    private static final float b24_t18f = (float) b24_t18d;
    private static final float b34_t18f = (float) b34_t18d;
    private static final float b64_t18f = (float) b64_t18d;

    // coefficients for the double-precision degree 12 Taylor polynomial T12
    private static final double a01_t12d = -0.0186023205146205532243437300433;
    private static final double a02_t12d = 4.60000000000000000000000000000;
    private static final double a03_t12d = 0.211693118299809442949323323336;
    private static final double a11_t12d = -0.00500702322573317730979741843919;
    private static final double a12_t12d = 0.992875103538486836140479571505;
    private static final double a13_t12d = 0.158224384715726725371768893252;
    private static final double a14_t12d = -0.131810610138301840156819349464;
    private static final double a21_t12d = -0.573420122960522263905952420789;
    private static final double a22_t12d = -0.132445561052799638845074997454;
    private static final double a23_t12d = 0.165635169436727415011171668419;
    private static final double a24_t12d = -0.0202785554058925907933568229945;
    private static final double a31_t12d = -0.133399693943892059700768926983;
    private static final double a32_t12d = 0.00172990000000000000000000000000;
    private static final double a33_t12d = 0.0107862779315792425026320640108;
    private static final double a34_t12d = -0.00675951846863086359778560766482;

    // coefficients for the single-precision degree 12 Taylor polynomial T12
    private static final float a01_t12f = (float) a01_t12d;
    private static final float a02_t12f = (float) a02_t12d;
    private static final float a03_t12f = (float) a03_t12d;
    private static final float a11_t12f = (float) a11_t12d;
    private static final float a12_t12f = (float) a12_t12d;
    private static final float a13_t12f = (float) a13_t12d;
    private static final float a14_t12f = (float) a14_t12d;
    private static final float a21_t12f = (float) a21_t12d;
    private static final float a22_t12f = (float) a22_t12d;
    private static final float a23_t12f = (float) a23_t12d;
    private static final float a24_t12f = (float) a24_t12d;
    private static final float a31_t12f = (float) a31_t12d;
    private static final float a32_t12f = (float) a32_t12d;
    private static final float a33_t12f = (float) a33_t12d;
    private static final float a34_t12f = (float) a34_t12d;

    // coefficients for the double-precision degree 8 Taylor polynomial T8
    private static final double sqrt177 = Math.sqrt(177.0);
    private static final double x3_t8d = 2.0 / 3.0;
    private static final double a1_t8d = 1.0 / 88.0 * (1.0 + sqrt177) * x3_t8d;
    private static final double a2_t8d = 1.0 / 352.0 * (1.0 + sqrt177) * x3_t8d;
    private static final double u2_t8d = 1.0 / 630.0 * (857.0 - 58.0 * sqrt177);
    private static final double c0_t8d = (-271.0 + 29.0 * sqrt177) / (315.0 * x3_t8d);
    private static final double c1_t8d = (11.0 * (-1.0 + sqrt177)) / (1260.0 * x3_t8d);
    private static final double c2_t8d = (11.0 * (-9.0 + sqrt177)) / (5040.0 * x3_t8d);
    private static final double c4_t8d = -((-89.0 + sqrt177) / (5040.0 * (x3_t8d * x3_t8d)));

    // coefficients for the single-precision degree 8 Taylor polynomial T8
    private static final float x3_t8f = (float) x3_t8d;
    private static final float a1_t8f = (float) a1_t8d;
    private static final float a2_t8f = (float) a2_t8d;
    private static final float u2_t8f = (float) u2_t8d;
    private static final float c0_t8f = (float) c0_t8d;
    private static final float c1_t8f = (float) c1_t8d;
    private static final float c2_t8f = (float) c2_t8d;
    private static final float c4_t8f = (float) c4_t8d;
}
