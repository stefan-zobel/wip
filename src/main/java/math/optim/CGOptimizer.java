/*
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
package math.optim;

import java.text.DecimalFormat;

import math.fun.DiffDMultiFunction;
import math.fun.DMultiFunctionEval;

/**
 * Conjugate-gradient implementation based on the code in "Numerical Recipes in
 * C" (see p. 423 and others).
 * <p>
 * As of now, it requires a differentiable function
 * {@link DiffDMultiFunction} as input.
 * <p>
 * The basic way to use the CGOptimizer is with the simple {@code minimize}
 * method:
 * <p>
 * <code>DiffDMultiFunction dmf = new SomeDiffDMultiFunction();</code>
 * <br>
 * <code>double[] initial = getInitialGuess();</code> <br>
 * <code>DMultiFunctionEval minimum = CGOptimizer.minimize(dmf, initial);</code>
 * 
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 */
public final class CGOptimizer {

    private static final DecimalFormat NF = new DecimalFormat("0.000E0");

    private static final boolean SIMPLE_GD = false;
    private static final boolean CHECK_SIMPLE_GD_CONVERGENCE = true;

    // constants
    private static final double GOLD = 1.618034;
    private static final double GLIMIT = 100.0;
    private static final double TINY = 1.0e-20;

    // overridden in dbrent()
    private static final int ITMAX = 10001;
    private static final double EPS = 1.0e-30;

    private static final int RESET_FREQ = 10;
    // default function tolerance
    private static final double FUNC_DEFAULT_TOL = 1e-10;

    static final class Minimand implements DiffDMultiFunction {
        private final DiffDMultiFunction f;

        Minimand(DiffDMultiFunction f) {
            this.f = f;
        }

        @Override
        public double apply(double[] x) {
            return -f.apply(x);
        }

        @Override
        public void derivativeAt(double[] x, double[] grad) {
            f.derivativeAt(x, grad);
            for (int i = 0; i < grad.length; ++i) {
                grad[i] = -grad[i];
            }
        }
    } // Minimand

    static final class OneDimDiffFunction {
        private final DiffDMultiFunction function;
        private final double[] initial;
        private final double[] direction;
        private final double[] currVector;
        private final double[] currGradient;

        OneDimDiffFunction(DiffDMultiFunction function, double[] initial,
                double[] direction) {
            this.function = function;
            this.initial = initial.clone();
            this.direction = direction.clone();
            this.currVector = new double[initial.length];
            this.currGradient = new double[initial.length];
        }

        double[] vectorOf(double x) {
            for (int i = 0; i < initial.length; i++) {
                currVector[i] = initial[i] + (x * direction[i]);
            }
            return currVector;
        }

        double valueAt(double x) {
            return function.apply(vectorOf(x));
        }

        double derivativeAt(double x) {
            function.derivativeAt(vectorOf(x), currGradient);
            double d = 0.0;
            for (int i = 0; i < currGradient.length; i++) {
                d += currGradient[i] * direction[i];
            }
            return d;
        }
    } // OneDimDiffFunction

    private CGOptimizer() {
    }

    public static DMultiFunctionEval maximize(
            DiffDMultiFunction function, double[] initial) {
        return maximize(function, FUNC_DEFAULT_TOL, initial);
    }

    public static DMultiFunctionEval maximize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial) {
        return maximize(function, functionTolerance, initial, ITMAX);
    }

    public static DMultiFunctionEval maximize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial, int maxIterations) {
        return maximize(function, functionTolerance, initial, maxIterations,
                true);
    }

    public static DMultiFunctionEval maximize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial, int maxIterations, boolean silent) {
        return minimize(new Minimand(function), functionTolerance, initial,
                maxIterations, silent, true);
    }

    public static DMultiFunctionEval minimize(
            DiffDMultiFunction function, double[] initial) {
        return minimize(function, FUNC_DEFAULT_TOL, initial);
    }

    public static DMultiFunctionEval minimize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial) {
        return minimize(function, functionTolerance, initial, ITMAX);
    }

    public static DMultiFunctionEval minimize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial, int maxIterations) {
        return minimize(function, functionTolerance, initial, maxIterations,
                true);
    }

    public static DMultiFunctionEval minimize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial, int maxIterations, boolean silent) {
        return minimize(function, functionTolerance, initial, maxIterations,
                silent, false);
    }

    private static DMultiFunctionEval minimize(
            DiffDMultiFunction function, double functionTolerance,
            double[] initial, int maxIterations, boolean silent,
            boolean isMaximization) {

        int dimension = initial.length;
        double sign = isMaximization ? -1.0 : 1.0;

        // evaluate function
        double fp = function.apply(initial);
        double[] xi = new double[dimension];
        function.derivativeAt(initial, xi);

        // make some vectors
        double[] g = new double[dimension];
        double[] h = new double[dimension];
        double[] p = new double[dimension];
        double[] bracketing = new double[3];
        for (int j = 0; j < dimension; j++) {
            g[j] = -xi[j];
            xi[j] = g[j];
            h[j] = g[j];
            p[j] = initial[j];
        }

        // iterations
        boolean simpleGDStep = false;
        int iter = 1;
        while (iter < maxIterations) {
            if (!silent) {
                System.err.print("Iter " + iter + ' ');
            }
            // do a line min along descent direction
            double[] p2 = lineMinimize(function, p, xi, bracketing);
            double fp2 = function.apply(p2);

            if (!silent) {
                System.err.printf(" %s (delta: %s)\n", NF.format(fp2),
                        NF.format(fp - fp2));
            }

            // check convergence
            if (2.0 * Math.abs(fp2 - fp) <= functionTolerance
                    * (Math.abs(fp2) + Math.abs(fp) + EPS)) {
                // convergence
                if (!CHECK_SIMPLE_GD_CONVERGENCE || simpleGDStep || SIMPLE_GD) {
                    return new DMultiFunctionEval(p, sign * fp2, iter);
                }
                simpleGDStep = true;
            } else {
                simpleGDStep = false;
            }
            // shift variables
            for (int j = 0; j < dimension; j++) {
                xi[j] = p2[j] - p[j];
                p[j] = p2[j];
            }
            fp = fp2;
            // find the new gradient
            function.derivativeAt(p, xi);

            if (!simpleGDStep && !SIMPLE_GD && (iter % RESET_FREQ != 0)) {
                // do the magic -- part i)
                // (calculate some dot products we'll need)
                double dgg = 0.0;
                double gg = 0.0;
                for (int j = 0; j < dimension; j++) {
                    // g dot g
                    gg += g[j] * g[j];
                    // grad dot grad
                    // FR method is:
                    // dgg += x[j]*x[j];
                    // PR method is:
                    dgg += (xi[j] + g[j]) * xi[j];
                }

                // check for miraculous convergence
                if (gg == 0.0) {
                    return new DMultiFunctionEval(p, sign
                            * function.apply(p), iter);
                }

                // do the magic -- part ii)
                // (update the sequence in a way that tries to preserve
                // conjugacy)
                double gam = dgg / gg;
                for (int j = 0; j < dimension; j++) {
                    g[j] = -xi[j];
                    h[j] = g[j] + gam * h[j];
                    xi[j] = h[j];
                }
            } else {
                // miraculous simpleGD convergence
                double xixi = 0.0;
                for (int j = 0; j < dimension; j++) {
                    xixi += xi[j] * xi[j];
                }
                // reset cgd
                for (int j = 0; j < dimension; j++) {
                    g[j] = -xi[j];
                    xi[j] = g[j];
                    h[j] = g[j];
                }
                if (xixi == 0.0) {
                    return new DMultiFunctionEval(p, sign
                            * function.apply(p), iter);
                }
            }
            ++iter;
        } // while

        // too many iterations
        System.err.println("Warning: exiting minimize because ITER exceeded!");
        return new DMultiFunctionEval(p, sign * function.apply(p),
                iter, false);
    }

    private static double[] lineMinimize(DiffDMultiFunction function,
            double[] initial, double[] direction, double[] bracketing) {
        // make a 1-dim function along the direction line
        // THIS IS A HACK (but it's the NRiC peoples' hack)
        OneDimDiffFunction oneDim = new OneDimDiffFunction(function, initial,
                direction);
        // do a 1-dim line min on this function
        // bracket the extreme point
        double guess = 0.01;
        bracketing[0] = 0.0;
        bracketing[1] = guess;
        bracketing[2] = 0.0;
        mnbrak(bracketing, oneDim);
        double ax = bracketing[0];
        double xx = bracketing[1];
        double bx = bracketing[2];
        // CHECK FOR END OF WORLD
        if (!(ax <= xx && xx <= bx) && !(bx <= xx && xx <= ax)) {
            System.err.println("Bad bracket order!");
        }
        // find the extreme point
        double xmin = dbrent(oneDim, ax, xx, bx);
        // return the full vector
        return oneDim.vectorOf(xmin);
    }

    private static void mnbrak(double[] bracketing, OneDimDiffFunction func) {
        // inputs
        double ax = bracketing[0];
        double fa = func.valueAt(ax);
        double bx = bracketing[1];
        double fb = func.valueAt(bx);

        if (fb > fa) {
            // swap
            double tmp = fa;
            fa = fb;
            fb = tmp;
            tmp = ax;
            ax = bx;
            bx = tmp;
        }

        // guess cx
        double cx = bx + GOLD * (bx - ax);
        double fc = func.valueAt(cx);

        // loop until we get a bracket
        while (fb > fc) {
            double r = (bx - ax) * (fb - fc);
            double q = (bx - cx) * (fb - fa);
            double u = bx - ((bx - cx) * q - (bx - ax) * r)
                    / (2.0 * sign(Math.max(Math.abs(q - r), TINY), q - r));
            double fu;
            double ulim = bx + GLIMIT * (cx - bx);
            if ((bx - u) * (u - cx) > 0.0) {
                fu = func.valueAt(u);
                if (fu < fc) {
                    bracketing[0] = bx;
                    bracketing[1] = u;
                    bracketing[2] = cx;
                    return;
                } else if (fu > fb) {
                    bracketing[0] = ax;
                    bracketing[1] = bx;
                    bracketing[2] = u;
                    return;
                }
                u = cx + GOLD * (cx - bx);
                fu = func.valueAt(u);
            } else if ((cx - u) * (u - ulim) > 0.0) {
                fu = func.valueAt(u);
                if (fu < fc) {
                    bx = cx;
                    cx = u;
                    u = cx + GOLD * (cx - bx);
                    fb = fc;
                    fc = fu;
                    fu = func.valueAt(u);
                }
            } else if ((u - ulim) * (ulim - cx) >= 0.0) {
                u = ulim;
                fu = func.valueAt(u);
            } else {
                u = cx + GOLD * (cx - bx);
                fu = func.valueAt(u);
            }
            ax = bx;
            bx = cx;
            cx = u;
            fa = fb;
            fb = fc;
            fc = fu;
        }
        bracketing[0] = ax;
        bracketing[1] = bx;
        bracketing[2] = cx;
    }

    private static double dbrent(OneDimDiffFunction func, double ax, double bx,
            double cx) {
        // constants
        final int ITMAX = 100;
        final double TOL = 1.0e-4;

        double d = 0.0, e = 0.0;

        double a = (ax < cx ? ax : cx);
        double b = (ax > cx ? ax : cx);
        double x = bx;
        double v = bx;
        double w = bx;
        double fx = func.valueAt(x);
        double fv = fx;
        double fw = fx;
        double dx = func.derivativeAt(x);
        double dv = dx;
        double dw = dx;
        for (int iteration = 0; iteration < ITMAX; iteration++) {
            double xm = 0.5 * (a + b);
            double tol1 = TOL * Math.abs(x);
            double tol2 = 2.0 * tol1;
            if (Math.abs(x - xm) <= (tol2 - 0.5 * (b - a))) {
                return x;
            }
            double u;
            if (Math.abs(e) > tol1) {
                double d1 = 2.0 * (b - a);
                double d2 = d1;
                if (dw != dx) {
                    d1 = (w - x) * dx / (dx - dw);
                }
                if (dv != dx) {
                    d2 = (v - x) * dx / (dx - dv);
                }
                double u1 = x + d1;
                double u2 = x + d2;
                boolean ok1 = ((a - u1) * (u1 - b) > 0.0 && dx * d1 <= 0.0);
                boolean ok2 = ((a - u2) * (u2 - b) > 0.0 && dx * d2 <= 0.0);
                double olde = e;
                e = d;
                if (ok1 || ok2) {
                    if (ok1 && ok2) {
                        d = (Math.abs(d1) < Math.abs(d2) ? d1 : d2);
                    } else if (ok1) {
                        d = d1;
                    } else {
                        d = d2;
                    }
                    if (Math.abs(d) <= Math.abs(0.5 * olde)) {
                        u = x + d;
                        if (u - a < tol2 || b - u < tol2) {
                            d = sign(tol1, xm - x);
                        }
                    } else {
                        e = (dx >= 0.0 ? a - x : b - x);
                        d = 0.5 * e;
                    }
                } else {
                    e = (dx >= 0.0 ? a - x : b - x);
                    d = 0.5 * e;
                }
            } else {
                e = (dx >= 0.0 ? a - x : b - x);
                d = 0.5 * e;
            }
            double fu;
            if (Math.abs(d) >= tol1) {
                u = x + d;
                fu = func.valueAt(u);
            } else {
                u = x + sign(tol1, d);
                fu = func.valueAt(u);
                if (fu > fx) {
                    return x;
                }
            }
            double du = func.derivativeAt(u);
            if (fu <= fx) {
                if (u >= x) {
                    a = x;
                } else {
                    b = x;
                }
                v = w;
                fv = fw;
                dv = dw;
                w = x;
                fw = fx;
                dw = dx;
                x = u;
                fx = fu;
                dx = du;
            } else {
                if (u < x) {
                    a = u;
                } else {
                    b = u;
                }
                if (fu <= fw || w == x) {
                    v = w;
                    fv = fw;
                    dv = dw;
                    w = u;
                    fw = fu;
                    dw = du;
                } else if (fu < fv || v == x || v == w) {
                    v = u;
                    fv = fu;
                    dv = du;
                }
            }
        }
        // Dan's addition:
        if (fx < func.valueAt(0.0)) {
            return x;
        }

        return 0.0;
    }

    private static double sign(double x, double y) {
        if (y >= 0.0) {
            return Math.abs(x);
        }
        return -Math.abs(x);
    }
}
