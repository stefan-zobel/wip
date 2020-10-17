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
package randomizedSVD;

import java.util.ArrayList;
import java.util.Objects;

import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;

/**
 * Adaptive Randomized Range Finder.
 * <p>
 * Algorithm 4.2 from Nathan Halko, Per-Gunnar Martinsson, and Joel A Tropp.
 * Finding structure with randomness: Probabilistic algorithms for constructing
 * approximate matrix decompositions. SIAM review, 53(2):217–288, 2011.
 */
public class AdaRangeFinder {

    private static final int r = 10;
    private static final double BOUND = 1.0 / (10.0 * Math.sqrt(2.0 / Math.PI));

    private final MatrixD A;
    private final MatrixD I;
    private final int n;

    public AdaRangeFinder(MatrixD A) {
        this.A = Objects.requireNonNull(A);
        this.I = Matrices.identityD(A.numRows());
        this.n = A.numColumns();
    }

    private static double norm(MatrixD y) {
        return y.norm2(); // normF() ?
    }

    private static double getMax(ArrayList<MatrixD> vectors) {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < vectors.size(); ++i) {
            double norm = norm(vectors.get(i));
            if (norm > max) {
                max = norm;
            }
        }
        return max;
    }

    public MatrixD computeQ() {

        ArrayList<MatrixD> vectors = new ArrayList<>(r);
        for (int k = 0; k < r; ++k) {
            vectors.add(A.times(Matrices.randomNormalD(n, 1)));
        }
        double max = getMax(vectors);

        MatrixD y = vectors.get(0);
        double norm = norm(y);
        MatrixD q = Matrices.sameDimD(y);
        q = y.scale(1.0 / norm, q);

        MatrixD Q = q.copy();

        shift(vectors, q, Q);

        // we implicitly set epsilon == 1
        while (max > BOUND) {

            MatrixD x = I.minus(Q.times(Q.transpose()));
            y = vectors.get(0);
            y = x.times(y);

            norm = norm(y);
            q = y.scale(1.0 / norm, q);
            Q = Q.appendColumn(q);

            shift(vectors, q, Q);

            max = getMax(vectors);
        }

        return Q;
    }

    private void shift(ArrayList<MatrixD> vectors, MatrixD q, MatrixD Q) {
        vectors.remove(0);
        MatrixD omega = Matrices.randomNormalD(n, 1);
        MatrixD yr = I.minus(Q.times(Q.transpose())).times(A).times(omega);
        vectors.add(yr);
        for (int i = 0; i < vectors.size() - 1; ++i) {
            MatrixD y = vectors.get(i);
            y = y.minus(q.times(q.transpose().times(y)));
            vectors.set(i, y);
        }
    }
}
