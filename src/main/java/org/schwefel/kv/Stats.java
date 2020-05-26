package org.schwefel.kv;

import net.volcanite.util.DoubleStatistics;

public class Stats {

    final DoubleStatistics putTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics getTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics deleteTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics mergeTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics batchTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics walTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics flushTimeNanos = DoubleStatistics.newInstance();
    final DoubleStatistics allOpsTimeNanos = DoubleStatistics.newInstance();

    public Stats() {
        //
    }

    public DoubleStatistics getPutTimeNanos() {
        return putTimeNanos;
    }

    public DoubleStatistics getGetTimeNanos() {
        return getTimeNanos;
    }

    public DoubleStatistics getDeleteTimeNanos() {
        return deleteTimeNanos;
    }

    public DoubleStatistics getMergeTimeNanos() {
        return mergeTimeNanos;
    }

    public DoubleStatistics getBatchTimeNanos() {
        return batchTimeNanos;
    }

    public DoubleStatistics getWalTimeNanos() {
        return walTimeNanos;
    }

    public DoubleStatistics getFlushTimeNanos() {
        return flushTimeNanos;
    }

    public DoubleStatistics getAllOpsTimeNanos() {
        return allOpsTimeNanos;
    }
}
