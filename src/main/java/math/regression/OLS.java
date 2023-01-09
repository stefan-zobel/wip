/*
 * Copyright 2023 Stefan Zobel
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
package math.regression;

import java.util.ArrayList;

import math.MathConsts;
import math.distribution.StudentT;
import math.list.DoubleArrayList;
import math.list.DoubleList;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;
import net.jamu.matrix.SvdEconD;

public class OLS {

    public static LSSummary estimate(double alpha, MatrixD X, MatrixD y) {
        if (X.numRows() != y.numRows()) {
            throw new IllegalArgumentException("X.numRows != y.numRows : " + X.numRows() + " != " + y.numRows());
        }
        if (X.numRows() - X.numColumns() < 1) {
            throw new IllegalArgumentException("degrees of freedom < 1 : " + (X.numRows() - X.numColumns()));
        }
        if (alpha <= 0.0) {
            throw new IllegalArgumentException("alpha <= 0 : " + alpha);
        }
        if (alpha >= 1.0) {
            throw new IllegalArgumentException("alpha >= 1 : " + alpha);
        }
        LSSummary smmry = new LSSummary(alpha, X, y);
        SvdEconD svd = X.svdEcon();
        double[] sigma = svd.getS();
        MatrixD sigmaPI = Matrices.createD(sigma.length, sigma.length);
        for (int i = 0; i < sigma.length; ++i) {
            double sv = sigma[i];
            if (sv > 5.0 * MathConsts.MACH_EPS_DBL) {
                sigmaPI.set(i, i, 1.0 / sv);
            } else {
                break;
            }
        }
        MatrixD V = svd.getVt().transpose();
        MatrixD Ut = svd.getU().transpose();
        MatrixD beta = V.timesMany(sigmaPI, Ut, y);
        smmry.setBeta(beta);
        MatrixD yHat = X.times(beta);
        smmry.setYHat(yHat);
        MatrixD ones = Matrices.onesD(1, y.numRows());
        double ybar = ones.times(y).scaleInplace(1.0 / y.numRows()).get(0, 0);
        smmry.setYBar(ybar);
        MatrixD yBarMat = Matrices.onesD(y.numRows(), 1).scaleInplace(ybar);
        MatrixD a = yHat.minus(yBarMat);
        MatrixD b = y.minus(yBarMat);
        double SQE = a.transpose().times(a).get(0, 0);
        double SQT = b.transpose().times(b).get(0, 0);
        double R_squared = SQE / SQT;
        smmry.setRSquared(R_squared);
        MatrixD epsHat = y.minus(yHat);
        smmry.setResiduals(epsHat);
        int df = epsHat.numRows() - X.numColumns();
        smmry.setDegreesOfFreedom(df);
        double sigmaHatSquared = epsHat.transpose().times(epsHat).scaleInplace(1.0 / (df)).get(0, 0);
        smmry.setSigmaHatSquared(sigmaHatSquared);
        MatrixD varCov = X.transpose().times(X).inverse().scaleInplace(sigmaHatSquared);
        smmry.setVarianceCovarianceMatrix(varCov);
        DoubleList standardErrors = new DoubleArrayList(varCov.numRows());
        for (int i = 0; i < varCov.numRows(); ++i) {
            double vari = varCov.get(i, i);
            if (vari < 0.0) {
                vari = 0.0;
                varCov.set(i, i, vari);
            }
            standardErrors.add(Math.sqrt(vari));
        }
        smmry.setCoefficientStandardErrors(standardErrors);
        DoubleList tValues = new DoubleArrayList(varCov.numRows());
        DoubleList pValues = new DoubleArrayList(varCov.numRows());
        ArrayList<DoubleList> confIntervals = new ArrayList<>();
        StudentT tDist = new StudentT(df);
        double tval = tDist.inverseCdf(1.0 - (alpha / 2.0));
        for (int i = 0; i < varCov.numRows(); ++i) {
            double coeff = beta.get(i, 0);
            double se = standardErrors.get(i);
            double t = coeff / se;
            double p = 2.0 * (1.0 - tDist.cdf(Math.abs(t)));
            double min = coeff - tval * se;
            double max = coeff + tval * se;
            tValues.add(t);
            pValues.add(p);
            confIntervals.add(DoubleList.of(min, max));
        }
        smmry.setTValues(tValues);
        smmry.setPValues(pValues);
        smmry.setConfidenceIntervals(confIntervals);
        return smmry;
    }
}
