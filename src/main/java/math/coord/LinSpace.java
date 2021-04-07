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
package math.coord;

import java.util.Arrays;
import java.util.NoSuchElementException;

import math.fun.DBiConsumer;
import math.fun.DConsumer;
import math.fun.DForEach;
import math.fun.DForEachBi;
import math.fun.DFunction;
import math.fun.DIndexIterator;

/**
 * Evenly spaced points between {@code start} and {@code end}. The spacing
 * between the points is {@code (end-start)/(n-1)} where {@code n} is the number
 * of points. A {@code LinSpace} always includes the endpoints. If {@code end}
 * is smaller than {@code start}, then the {@code LinSpace} describes descending
 * values.
 * <p>
 * Note that indexes are 1-based!
 */
public final class LinSpace {

    private final double start;
    private final double stop;
    private final int numberOfPoints;
    private double[] vec;

    private LinSpace(double start, double end, int numberOfPoints, double[] data) {
        checkArg(start, "start");
        checkArg(end, "end");
        if (numberOfPoints <= 0) {
            throw new IllegalArgumentException("numberOfPoints must be strictly positive : " + numberOfPoints);
        }
        if (start == end || numberOfPoints == 1) {
            this.start = end;
            this.stop = end;
            this.numberOfPoints = 1;
        } else {
            this.start = start;
            this.stop = end;
            this.numberOfPoints = numberOfPoints;
        }
        if (data != null) {
            if (data.length != this.numberOfPoints) {
                throw new IllegalStateException(
                        "inconsistent vector dimension : " + numberOfPoints + " != " + data.length);
            }
            vec = data;
        }
    }

    private LinSpace(LinSpace other) {
        start = other.start;
        stop = other.stop;
        numberOfPoints = other.numberOfPoints;
        if (other.vec != null) {
            vec = other.vec.clone();
        }
    }

    public double spacing() {
        if (numberOfPoints == 1) {
            return 0.0;
        }
        if (stop < start) {
            return (start - stop) / (numberOfPoints - 1);
        } else {
            return (stop - start) / (numberOfPoints - 1);
        }
    }

    public int size() {
        return numberOfPoints;
    }

    public double start() {
        return start;
    }

    public double end() {
        return stop;
    }

    public DIndexIterator iterator() {
        return new DblIt(numberOfPoints, start, stop, step());
    }

    private double step() {
        double step = spacing();
        return (start > stop) ? -step : step;
    }

    public LinSpace slice(int from, int to) {
        checkPosition(from, "from");
        checkPosition(to, "to");
        if (from > to) {
            int tmp = to;
            to = from;
            from = tmp;
        }
        if (from == 1 && to == numberOfPoints) {
            return new LinSpace(this);
        }
        boolean c = vec != null;
        // from >= 1
        // to <= n
        if (from == to) {
            int count = 1;
            double point = start + ((from - 1) * step());
            return new LinSpace(point, point, count, c ? new double[] { vec[from - 1] } : null);
        }
        int count = 1 + to - from;
        double step = step();
        double begin = start + ((from - 1) * step);
        double end = begin + ((count - 1) * step);
        double[] d = c ? Arrays.copyOfRange(vec, from - 1, to) : null;
        return new LinSpace(begin, end, count, d);
    }

    public LinSpace sliceTo(int to) {
        checkPosition(to, "to");
        boolean c = vec != null;
        if (to == 1) {
            return new LinSpace(start, start, 1, c ? new double[] { vec[0] } : null);
        }
        if (to == numberOfPoints) {
            return new LinSpace(this);
        }
        // to is in [2..n-1]
        int count = to;
        double[] d = c ? Arrays.copyOfRange(vec, 0, to) : null;
        return new LinSpace(start, start + ((count - 1) * step()), count, d);
    }

    public LinSpace sliceFrom(int from) {
        checkPosition(from, "from");
        boolean c = vec != null;
        if (from == 1) {
            return new LinSpace(this);
        }
        if (from == numberOfPoints) {
            return new LinSpace(stop, stop, 1, c ? new double[] { vec[numberOfPoints - 1] } : null);
        }
        // from is in [2..n-1]
        int count = 1 + numberOfPoints - from;
        double[] d = c ? Arrays.copyOfRange(vec, from - 1, from - 1 + count) : null;
        return new LinSpace(start + ((from - 1) * step()), stop, count, d);
    }

    public double point(int pos) {
        checkPosition(pos, "pos");
        if (pos == 1) {
            return start;
        }
        if (pos == numberOfPoints) {
            return stop;
        }
        return start + ((pos - 1) * step());
    }

    public LinSpace allocate() {
        vec = new double[numberOfPoints];
        return this;
    }

    // escape hatch
    public double[] points() {
        double[] points = new double[numberOfPoints];
        double current = start;
        double delta = step();
        for (int i = 0; i < points.length; ++i) {
            if (i == points.length - 1) {
                points[i] = stop;
            } else {
                points[i] = current;
            }
            current += delta;
        }
        return points;
    }

    public double value(int pos) {
        checkPosition(pos, "pos");
        if (vec == null) {
            throw new NoSuchElementException("no data");
        }
        return vec[pos - 1];
    }

    // escape hatch
    public double[] values() {
        if (vec == null) {
            throw new NoSuchElementException("no data");
        }
        return vec;
    }

    public LinSpace setValue(int pos, double x) {
        checkPosition(pos, "pos");
        if (vec == null) {
            throw new NoSuchElementException("no data");
        }
        vec[pos - 1] = x;
        return this;
    }

    public DForEach forEach() {
        return new DblForEach(numberOfPoints, start, stop, step());
    }

    public DForEachBi forEachBi() {
        if (!hasValues()) {
            throw new NoSuchElementException("no data");
        }
        return new DblForEachBi(numberOfPoints, start, stop, step(), vec);
    }

    public LinSpace eval(DFunction fun) {
        double current = start;
        double last = stop;
        double delta = step();
        LinSpace result = new LinSpace(this).allocate();
        double[] y = result.vec;
        for (int i = 0; i < y.length; ++i) {
            if (current <= last) {
                y[i] = fun.apply(current);
            } else {
                y[i] = fun.apply(last);
            }
            current += delta;
        }
        return result;
    }

    private static final class DblForEach implements DForEach {
        private int remaining;
        private double current;
        private final double last;
        private final double delta;

        DblForEach(int numberOfPoints, double start, double stop, double spacing) {
            remaining = numberOfPoints;
            current = start;
            last = stop;
            delta = spacing;
        }

        @Override
        public void forEachRemaining(DConsumer action) {
            while (remaining > 0) {
                --remaining;
                double x = current;
                if (remaining == 0) {
                    action.accept(last);
                    return;
                } else {
                    current = x + delta;
                }
                action.accept(x);
            }
        }

        @Override
        public boolean tryAdvance(DConsumer action) {
            if (remaining > 0) {
                --remaining;
                double x = current;
                if (remaining == 0) {
                    action.accept(last);
                    return true;
                } else {
                    current = x + delta;
                }
                action.accept(x);
                return true;
            }
            return false;
        }
    }

    private static final class DblForEachBi implements DForEachBi {

        private int remaining;
        private double current;
        private final double last;
        private final double delta;
        private final int total;
        private final double[] data;

        DblForEachBi(int numberOfPoints, double start, double stop, double spacing, double[] vec) {
            total = numberOfPoints;
            remaining = numberOfPoints;
            current = start;
            last = stop;
            delta = spacing;
            data = vec;
        }

        @Override
        public void forEachRemaining(DBiConsumer action) {
            while (remaining > 0) {
                --remaining;
                double x = current;
                if (remaining == 0) {
                    action.accept(last, data[total - 1]);
                    return;
                } else {
                    current = x + delta;
                }
                action.accept(x, data[total - remaining - 1]);
            }
        }

        @Override
        public boolean tryAdvance(DBiConsumer action) {
            if (remaining > 0) {
                --remaining;
                double x = current;
                if (remaining == 0) {
                    action.accept(last, data[total - 1]);
                    return true;
                } else {
                    current = x + delta;
                }
                action.accept(x, data[total - remaining - 1]);
                return true;
            }
            return false;
        }
    }

    private static final class DblIt implements DIndexIterator {
        private int remaining;
        private double current;
        private final double last;
        private final double delta;
        private final int total;

        DblIt(int numberOfPoints, double start, double stop, double spacing) {
            total = numberOfPoints;
            remaining = numberOfPoints;
            current = start;
            last = stop;
            delta = spacing;
        }

        @Override
        public boolean hasNext() {
            return remaining > 0;
        }

        @Override
        public int nextIndex() {
            if (remaining > 0) {
                return total - remaining + 1;
            }
            throw new NoSuchElementException("exhausted");
        }

        @Override
        public double next() {
            if (remaining > 0) {
                --remaining;
                double x = current;
                if (remaining == 0) {
                    return last;
                } else {
                    current = x + delta;
                }
                return x;
            }
            throw new NoSuchElementException("exhausted");
        }
    }

    /**
     * Returns {@code 128} evenly spaced points between {@code start} and
     * {@code end} (including the interval endpoints).
     * 
     * @param start
     *            start point of interval (included)
     * @param end
     *            endpoint of interval (included)
     * @return sample interval containing {@code 128} points
     */
    public static LinSpace linspace(double start, double end) {
        return new LinSpace(start, end, 128, null);
    }

    public static LinSpace linspace(double start, double end, int numberOfPoints) {
        return new LinSpace(start, end, numberOfPoints, null);
    }

    public static LinSpace compute(double start, double end, int numberOfPoints, DFunction fun) {
        LinSpace lsp = linspace(start, end, numberOfPoints).allocate();
        return lsp.eval(fun);
    }

    public static LinSpace centeredIntIndexed(double[] data) {
        final int length = data.length;
        if (length < 1) {
            throw new IllegalArgumentException("data.length must be strictly positive : 0");
        }
        if (length == 1) {
            return new LinSpace(0.0, 0.0, 1, new double[] {data[0]});
        }
        double sym = (((double) length) - 1.0) / 2.0;
        double start = (length % 2 != 0) ? -Math.floor(sym) : -Math.floor(sym) - 1.0;
        double end = start + length - 1.0;
        return new LinSpace(start, end, length, data.clone());
    }

    public static LinSpace centeredDoubleIndexed(double[] data) {
        final int length = data.length;
        if (length < 1) {
            throw new IllegalArgumentException("data.length must be strictly positive : 0");
        }
        if (length == 1) {
            return new LinSpace(0.0, 0.0, 1, new double[] {data[0]});
        }
        double sym = (((double) length) - 1.0) / 2.0;
        return new LinSpace(-sym, sym, length, data.clone());
    }

    public boolean hasValues() {
        return vec != null;
    }

    @Override
    public String toString() {
        return "1x" + numberOfPoints + " :  [" + start + "  ...  " + stop + "]";
    }

    private void checkPosition(int pos, String name) {
        if (pos < 1 || pos > numberOfPoints) {
            throw new IndexOutOfBoundsException(
                    name + " = " + pos + " (indexes are 1-based, size is " + numberOfPoints + ")");
        }
    }

    private static void checkArg(double a, String name) {
        if (isBadNum(a)) {
            throw new IllegalArgumentException("Bad argument : " + name + " (Inf or NaN)");
        }
    }

    private static boolean isBadNum(double v) {
        if (Double.isInfinite(v) || Double.isNaN(v)) {
            return true;
        }
        return false;
    }
}
