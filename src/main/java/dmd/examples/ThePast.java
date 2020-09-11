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
package dmd.examples;

import math.coord.LinSpace;
import math.fun.DIterator;
import net.jamu.complex.Zd;
import net.jamu.complex.ZdImpl;
import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;

/**
 * "Predicting" the past also works.
 */
public class ThePast {

    static final double x_start = -10.0;
    static final double x_end = 10.0;
    static final int x_num = 400;

    static final double t_start = 0.0;
    static final double t_end = 4.0 * Math.PI;
    static final int t_num = 201;

    // space dimension
    static final LinSpace xi = LinSpace.linspace(x_start, x_end, x_num);
    // time dimension
    static final LinSpace ti = LinSpace.linspace(t_start, t_end, t_num);

    public static void main(String[] args) {
        // build data 'measurements' matrix
        MatrixD data = setupMeasurementsMatrix(ti);

        // step size
        double deltaT = (t_end - t_start) / (data.numColumns() - 1);
        System.out.println("deltaT: " + deltaT);
        // assumed rank
        int rank = 2;

        ExactDMD dmd = new ExactDMD(data, deltaT, rank).compute();

        // predict the same interval that was used to compute the DMD
        MatrixD pred = dmd.predict(t_start, data.numColumns());

        System.out.println("reconstructed:" + pred);
        System.out.println("original     :" + data);

        // compare Frobenius norms for approximate equality
        double normDmd = pred.normF();
        double normData = data.normF();
        System.out.println("reconstructed: " + normDmd);
        System.out.println("original     : " + normData);

        // now attempt to "predict" the past starting from -4.0 * PI for t_num
        // predictions with the same stepsize
        int t_num = 51;
        double t_start = -t_end;
        double t_end = t_start + dmd.getDeltaT() * (t_num - 1);
        deltaT = (t_num == 1) ? dmd.getDeltaT() : (t_end - t_start) / (t_num - 1);
        System.out.println("\ndeltaT: " + deltaT);

        MatrixD fut = dmd.predict(t_start, t_num);
        System.out.println("future       :" + fut);

        // setup a LinSpace corresponding to the prediction interval
        LinSpace newTime = LinSpace.linspace(t_start, t_end, t_num);
        // and create the data for that interval
        MatrixD newData = setupMeasurementsMatrix(newTime);
        System.out.println("new data     :" + newData);
        // and compare the Frobenius norms
        double normPred = fut.normF();
        double normRea = newData.normF();
        System.out.println("predicted    : " + normPred);
        System.out.println("realized     : " + normRea);
    }

    private static MatrixD setupMeasurementsMatrix(LinSpace time) {
        // build data 'measurements' matrix
        MatrixD X_ = Matrices.createD(xi.size(), time.size());

        for (DIterator tIt = time.iterator(); tIt.hasNext(); /**/) {
            int colIdx = tIt.nextIndex() - 1;
            double t = tIt.next();
            for (DIterator xIt = xi.iterator(); xIt.hasNext(); /**/) {
                int rowIdx = xIt.nextIndex() - 1;
                Zd z = f(xIt.next(), t);
                // copy only the real part
                X_.set(rowIdx, colIdx, z.re());
            }
        }

        return X_;
    }

    // merged spatio-temporal signal
    private static Zd f(double x, double t) {
        return f1a(x, t).add(f2a(x, t));
    }

    // first spatio-temporal pattern
    private static Zd f1a(double x, double t) {
        Zd zt = new ZdImpl(0.1, 2.2 * t).exp();
        Zd zx = new ZdImpl(sech(x + 3.0), Math.tanh(x));
        return zt.mul(zx);
    }

    // second spatio-temporal pattern
    private static Zd f2a(double x, double t) {
        Zd zt = new ZdImpl(0.1, -2.2 * t).exp();
        Zd zx = new ZdImpl(sech(x - 3.0), -Math.tanh(x));
        return zt.mul(zx);
    }

    private static double sech(double y) {
        return 1.0 / Math.cosh(y);
    }
}
