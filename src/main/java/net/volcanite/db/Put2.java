package net.volcanite.db;

import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;

import net.volcanite.util.DoubleStatistics;

public class Put2 extends DB {

    private static final long FLUSH_TIME_WINDOW_MILLIS = 985L;
    private static final long FLUSH_BATCH_SIZE = 20_000L;

    private long totalSinceLastFsync = 0L;
    private final DoubleStatistics writeTimeNanos = DoubleStatistics.newInstance();
    private final DoubleStatistics fsyncTimeNanos = DoubleStatistics.newInstance();
    private final DoubleStatistics totalTimeNanos = DoubleStatistics.newInstance();
    private long lastFlush;

    public Put2(String dbPath) {
        super(dbPath);
        lastFlush = System.currentTimeMillis();
    }

    public void write(byte[] key, byte[] value) {
        try {
            write_(key, value);
        } catch (RocksDBException e) {
            throw new DBException(e);
        }
    }

    private void write_(byte[] key, byte[] value) throws RocksDBException {

        long totalStart = System.nanoTime();

        try (Transaction txn = Databases.newTransaction(dbPath)) {

            long writeStart = System.nanoTime();
            txn.put(key, value);
            txn.commit();
            ++totalSinceLastFsync;
            writeTimeNanos.accept(System.nanoTime() - writeStart);

            if (System.currentTimeMillis() - lastFlush >= FLUSH_TIME_WINDOW_MILLIS) {
                long flushStart = System.nanoTime();
                tryFlushWal();
                fsyncTimeNanos.accept(System.nanoTime() - flushStart);
                lastFlush = System.currentTimeMillis();
                totalSinceLastFsync = 0L;
            } else if (totalSinceLastFsync % FLUSH_BATCH_SIZE == 0L) {
                long flushStart = System.nanoTime();
                tryFlushWal();
                fsyncTimeNanos.accept(System.nanoTime() - flushStart);
                lastFlush = System.currentTimeMillis();
                totalSinceLastFsync = 0L;
            }
        }
        totalTimeNanos.accept(System.nanoTime() - totalStart);
    }

    public DoubleStatistics getWriteTimeNanos() {
        return writeTimeNanos;
    }

    public DoubleStatistics getFsyncTimeNanos() {
        return fsyncTimeNanos;
    }

    public DoubleStatistics getTotalTimeNanos() {
        return totalTimeNanos;
    }
}
