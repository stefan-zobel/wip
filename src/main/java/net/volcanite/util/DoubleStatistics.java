package net.volcanite.util;

/**
 * A state object for collecting statistics such as count, min, max, sum,
 * average and variance (or standard deviation).
 */
public interface DoubleStatistics {

    /**
     * Creates a new non-threadsafe {@code DoubleStatistics} instance.
     * 
     * @return a new non-threadsafe {@code DoubleStatistics} instance
     */
    public static DoubleStatistics newInstance() {
        return newInstance(false);
    }

    /**
     * Creates a new {@code DoubleStatistics} instance which, depending on the
     * {@code threadsafe} argument, is either threadsafe or non-threadsafe.
     * 
     * @param threadsafe
     *            if {@code true} a threadsafe {@code DoubleStatistics} gets
     *            created
     * @return a new {@code DoubleStatistics} instance which is either
     *         threadsafe or non-threadsafe (depending on the value of the
     *         {@code threadsafe} argument)
     */
    public static DoubleStatistics newInstance(boolean threadsafe) {
        if (threadsafe) {
            return new DoubleStatisticsSync();
        } else {
            return new DoubleStatisticsNoSync();
        }
    }

    /**
     * Records another value into the summary information.
     * 
     * @param value
     *            the input value
     */
    void accept(double value);

    /**
     * Return the count of values recorded.
     * 
     * @return the count of values
     */
    long getCount();

    /**
     * Returns the sum of values recorded, or zero if no values have been
     * recorded.
     * 
     * <p>
     * The value of a floating-point sum is a function both of the input values
     * as well as the order of addition operations. The order of addition
     * operations of this method is intentionally not defined to allow for
     * implementation flexibility to improve the speed and accuracy of the
     * computed result.
     * 
     * In particular, this method may be implemented using compensated summation
     * or other technique to reduce the error bound in the numerical sum
     * compared to a simple summation of {@code double} values. Because of the
     * unspecified order of operations and the possibility of using differing
     * summation schemes, the output of this method may vary on the same input
     * values.
     * 
     * <p>
     * Various conditions can result in a non-finite sum being computed. This
     * can occur even if the all the recorded values being summed are finite. If
     * any recorded value is non-finite, the sum will be non-finite:
     * 
     * <ul>
     * 
     * <li>If any recorded value is a NaN, then the final sum will be NaN.
     * 
     * <li>If the recorded values contain one or more infinities, the sum will
     * be infinite or NaN.
     * 
     * <ul>
     * 
     * <li>If the recorded values contain infinities of opposite sign, the sum
     * will be NaN.
     * 
     * <li>If the recorded values contain infinities of one sign and an
     * intermediate sum overflows to an infinity of the opposite sign, the sum
     * may be NaN.
     * 
     * </ul>
     * 
     * </ul>
     * 
     * It is possible for intermediate sums of finite values to overflow into
     * opposite-signed infinities; if that occurs, the final sum will be NaN
     * even if the recorded values are all finite.
     * 
     * If all the recorded values are zero, the sign of zero is <em>not</em>
     * guaranteed to be preserved in the final sum.
     * 
     * <p>
     * <b>API Note:</b><br>
     * Values sorted by increasing absolute magnitude tend to yield more
     * accurate results.
     * 
     * @return the sum of values, or zero if none
     */
    double getSum();

    /**
     * Returns the minimum recorded value, {@code Double.NaN} if any recorded
     * value was NaN or {@code Double.POSITIVE_INFINITY} if no values were
     * recorded. Unlike the numerical comparison operators, this method
     * considers negative zero to be strictly smaller than positive zero.
     * 
     * @return the minimum recorded value, {@code Double.NaN} if any recorded
     *         value was NaN or {@code Double.POSITIVE_INFINITY} if no values
     *         were recorded
     */
    double getMin();

    /**
     * Returns the maximum recorded value, {@code Double.NaN} if any recorded
     * value was NaN or {@code Double.NEGATIVE_INFINITY} if no values were
     * recorded. Unlike the numerical comparison operators, this method
     * considers negative zero to be strictly smaller than positive zero.
     * 
     * @return the maximum recorded value, {@code Double.NaN} if any recorded
     *         value was NaN or {@code Double.NEGATIVE_INFINITY} if no values
     *         were recorded
     */
    double getMax();

    /**
     * Returns the arithmetic mean of values recorded, or zero if no values have
     * been recorded.
     * 
     * <p>
     * The computed average can vary numerically and have the special case
     * behavior as computing the sum; see {@link #getSum} for details.
     * 
     * <p>
     * <b>API Note:</b><br>
     * Values sorted by increasing absolute magnitude tend to yield more
     * accurate results.
     * 
     * @return the arithmetic mean of values, or zero if none
     */
    double getAverage();

    double getVariance();

    double getStandardDeviation();

    /**
     * Returns a non-empty string representation of this object suitable for
     * debugging. The exact presentation format is unspecified and may vary
     * between implementations and versions.
     * 
     * @return a string representation of this object
     */
    String toString();
}
