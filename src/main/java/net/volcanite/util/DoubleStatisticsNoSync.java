package net.volcanite.util;

import java.util.Objects;

/**
 * A state object for collecting statistics such as count, min, max, sum,
 * average and variance (or standard deviation).
 */
public class DoubleStatisticsNoSync implements DoubleStatistics {
    private long count;
    private double sum;
    private double sumCompensation; // Negative low order bits of sum
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    /**
     * the sum of squares of differences from the (current) mean
     * http://en.wikipedia
     * .org/wiki/Algorithms_for_calculating_variance#On-line_algorithm
     * (Welford's algorithm)
     */
    private double sumDiffFromCurrMeanSquared;
    private double sumDiffFromCurrMeanSquaredCompensation;
    /**
     * the variance - recursively calculated via Welford's algorithm
     */
    private double variance;

    /**
     * Construct an empty instance with zero count, zero sum,
     * {@code Double.POSITIVE_INFINITY} min, {@code Double.NEGATIVE_INFINITY}
     * max and zero average.
     */
    public DoubleStatisticsNoSync() {
    }

    /**
     * Constructs a non-empty instance with an initial state that corresponds to
     * the current state of the specified {@code other} DoubleStatisticsNoSync
     * instance.
     *
     * <p>
     * If {@code other.count} is zero then the remaining arguments are ignored
     * and an empty instance is constructed.
     *
     * <p>
     * If the state of {@code other} is inconsistent then an
     * {@code IllegalArgumentException} is thrown. The necessary conditions for
     * a consistent state are:
     * <ul>
     * <li>{@code other.count >= 0}</li>
     * <li>{@code (other.min <= other.max && !isNaN(other.sum)) || (isNaN(other.min) && isNaN(other.max) && isNaN(other.sum))}</li>
     * </ul>
     * <p>
     * <b>API Note:</b><br>
     * The enforcement of state correctness means that the retrieved set of
     * recorded values obtained from a {@code DoubleStatisticsNoSync} source
     * instance may not be a legal state for this constructor due to arithmetic
     * overflow of the source's recorded count of values. The consistency
     * conditions are not sufficient to prevent the creation of an internally
     * inconsistent instance. An example of such a state would be an instance
     * with: {@code other.count} = 2, {@code other.min} = 1, {@code other.max} =
     * 2, and {@code other.sum} = 0.
     *
     * @param other
     *            the DoubleStatisticsNoSync instance whose state should be
     *            replicated
     * @throws NullPointerException
     *             if {@code other} is null
     * @throws IllegalArgumentException
     *             if the internal state of the {@code other} object is
     *             inconsistent
     */
    public DoubleStatisticsNoSync(DoubleStatisticsNoSync other) throws IllegalArgumentException {
        Objects.requireNonNull(other);
        if (other.count < 0L) {
            throw new IllegalArgumentException("Negative count value");
        } else if (other.count > 0L) {
            if (other.min > other.max) {
                throw new IllegalArgumentException("Minimum greater than maximum");
            }
            // All NaN or non NaN
            int ncount = 0;
            if (Double.isNaN(other.min)) {
                ++ncount;
            }
            if (Double.isNaN(other.max)) {
                ++ncount;
            }
            if (Double.isNaN(other.sum)) {
                ++ncount;
            }
            if (ncount > 0 && ncount < 3) {
                throw new IllegalArgumentException("Some, not all, of the minimum, maximum, or sum is NaN");
            }

            this.count = other.count;
            this.sum = other.sum;
            this.sumCompensation = 0.0d;
            this.min = other.min;
            this.max = other.max;
            this.sumDiffFromCurrMeanSquared = other.sumDiffFromCurrMeanSquared;
            this.sumDiffFromCurrMeanSquaredCompensation = other.sumDiffFromCurrMeanSquaredCompensation;
            this.variance = other.variance;
        }
        // Use default field values if count == 0
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(double value) {
        long countSoFar = count;
        double average = getAverage();
        ++count;
        sumWithCompensation(value);
        min = Math.min(min, value);
        max = Math.max(max, value);
        double delta = value - average;
        average = ((countSoFar * average) / count) + (value / count);
        sumDiffFromCurrMeanSquaredWithCompensation(delta * (value - average));
        if (count > 1L) {
            variance = (sumDiffFromCurrMeanSquared - sumDiffFromCurrMeanSquaredCompensation) / countSoFar;
        }
    }

    /**
     * Incorporate a new double value using Kahan summation / compensated
     * summation.
     */
    private void sumWithCompensation(double value) {
        // https://en.wikipedia.org/wiki/Kahan_summation_algorithm
        double tmp = value - sumCompensation;
        double velvel = sum + tmp; // Little wolf of rounding error
        sumCompensation = (velvel - sum) - tmp;
        sum = velvel;
    }

    /**
     * Incorporate a new double value using Kahan summation / compensated
     * summation.
     */
    private void sumDiffFromCurrMeanSquaredWithCompensation(double value) {
        double tmp = value - sumDiffFromCurrMeanSquaredCompensation;
        double velvel = sumDiffFromCurrMeanSquared + tmp;
        sumDiffFromCurrMeanSquaredCompensation = (velvel - sumDiffFromCurrMeanSquared) - tmp;
        sumDiffFromCurrMeanSquared = velvel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getCount() {
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getSum() {
        // Better error bounds to add both terms as the final sum
        return sum - sumCompensation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getMin() {
        return min;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getMax() {
        return max;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getAverage() {
        return getCount() > 0 ? getSum() / getCount() : 0.0d;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getVariance() {
        double var = variance;
        if (var < 0.0) {
            var = 0.0;
        }
        return var;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("%s{count=%d, sum=%f, min=%f, average=%f, max=%f, stddev=%f}",
                this.getClass().getSimpleName(), getCount(), getSum(), getMin(), getAverage(), getMax(),
                getStandardDeviation());
    }
}
