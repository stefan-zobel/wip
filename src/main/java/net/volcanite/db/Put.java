package net.volcanite.db;

import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteOptions;

import net.volcanite.util.DoubleStatistics;

public class Put implements AutoCloseable {

    private static final long FLUSH_TIME_WINDOW_MILLIS = 985L;
    private static final long FLUSH_BATCH_SIZE = 20_000L;
    private static final int PARALLELISM = 2;

    private long totalSinceLastFsync = 0L;
    private final DoubleStatistics writeTimeNanos = DoubleStatistics.newInstance();
    private final DoubleStatistics fsyncTimeNanos = DoubleStatistics.newInstance();
    private final DoubleStatistics totalTimeNanos = DoubleStatistics.newInstance();
    private long lastFlush;
    private final String dbPath_;

    private final WriteOptions writeOptions;
    private final TransactionDBOptions txnDbOptions;
    private final TransactionDB txnDb;
    private final TransactionOptions txnOpts;

    public Put(String dbPath) {
        @SuppressWarnings("resource")
        Options options = new Options().setCreateIfMissing(true).setIncreaseParallelism(PARALLELISM);
        writeOptions = new WriteOptions();
        txnDbOptions = new TransactionDBOptions();
        txnDb = (TransactionDB) wrap(() -> TransactionDB.open(options, txnDbOptions, dbPath));
        txnOpts = new TransactionOptions();
        dbPath_ = dbPath;
        lastFlush = System.currentTimeMillis();
    }

    @Override
    public void close() {
        try {
            tryFlushWal();
        } finally {
            close(txnDb);
            close(txnDbOptions);
            close(txnOpts);
            close(writeOptions);
        }
    }

    private static void close(AutoCloseable ac) {
        if (ac != null) {
            try {
                ac.close();
            } catch (Exception ignore) {
                //
            }
        }
    }

    private void tryFlushWal() {
        try {
            txnDb.flushWal(true);
        } catch (RocksDBException e) {
            throw new DBException(e);
        }
    }

    private static interface ThrowingSupplier {
        RocksObject get() throws Exception;
    }

    private static RocksObject wrap(ThrowingSupplier block) {
        try {
            return block.get();
        } catch (Exception e) {
            throw new DBException(e);
        }
    }

    public void write(byte[] key, byte[] value) {
        try {
            write(key, value, txnDb, txnOpts, writeOptions);
        } catch (RocksDBException e) {
            throw new DBException(e);
        }
    }

    private void write(byte[] key, byte[] value, TransactionDB txnDb, TransactionOptions txnOpts,
            WriteOptions writeOptions) throws RocksDBException {

        long totalStart = System.nanoTime();

        try (Transaction txn = txnDb.beginTransaction(writeOptions, txnOpts)) {

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

    public String getDbPath() {
        return dbPath_;
    }
}
