package org.schwefel.kv;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteOptions;

public final class KVStore implements StoreOps {

    private static final long FLUSH_TIME_WINDOW_MILLIS = 985L;
    private static final long FLUSH_BATCH_SIZE = 20_000L;

    private static final Logger logger = Logger.getLogger(KVStore.class.getName());

    private volatile boolean open = false;
    private long totalSinceLastFsync = 0L;
    private long lastSync;

    private TransactionDB txnDb;
    private TransactionDBOptions txnDbOptions;
    private TransactionOptions txnOpts;
    private Options options;
    private WriteOptions writeOptions;
    private ReadOptions readOptions;
    private FlushOptions flushOptions;
    private FlushOptions flushOptionsNoWait;
    private final Path dir; // XXX ??
    private final String path;
    private final Stats stats = new Stats();

    public KVStore(Path dir) {
        this.dir = Objects.requireNonNull(dir);
        this.path = (String) wrapEx(() -> dir.toFile().getCanonicalPath());
        // TODO ensure dir exists / gets created
        open();
    }

    private void open() {
        options = new Options();
        options.setCreateIfMissing(true);
        options.setErrorIfExists(false);
        options.setIncreaseParallelism(Math.max(Runtime.getRuntime().availableProcessors(), 2));
        writeOptions = new WriteOptions();
        readOptions = new ReadOptions();
        flushOptions = new FlushOptions();
        flushOptions.setWaitForFlush(true);
        flushOptionsNoWait = new FlushOptions();
        flushOptionsNoWait.setWaitForFlush(false);
        txnDbOptions = new TransactionDBOptions();
        txnDb = (TransactionDB) wrapEx(() -> TransactionDB.open(options, txnDbOptions, path));
        txnOpts = new TransactionOptions();
        open = true;
        lastSync = System.currentTimeMillis();
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        open = false;
        ignoreEx(() -> syncWAL());
        ignoreEx(() -> flush());
        close(txnDb);
        close(txnDbOptions);
        close(txnOpts);
        close(writeOptions);
        close(readOptions);
        close(flushOptions);
        close(flushOptionsNoWait);
        close(options);
        txnDb = null;
        txnDbOptions = null;
        txnOpts = null;
        writeOptions = null;
        readOptions = null;
        flushOptions = null;
        flushOptionsNoWait = null;
        options = null;
    }

    @Override
    public synchronized void put(byte[] key, byte[] value) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.putTimeNanos.accept(delta);
    }

    @Override
    public synchronized void putIfAbsent(byte[] key, byte[] value) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        if (get(key) == null) {
            put(key, value);
        }

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta); // XXX ??? double counting?
        stats.putTimeNanos.accept(delta); // XXX ??? double counting?
    }

    @Override
    public synchronized byte[] get(byte[] key) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.getTimeNanos.accept(delta);
        return null; // XXX
    }

    @Override
    public synchronized void delete(byte[] key) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.deleteTimeNanos.accept(delta);
    }

    @Override
    public synchronized void deleteRange(byte[] beginKey, byte[] endKey) {
        long start = System.nanoTime();
        Objects.requireNonNull(beginKey, "beginKey cannot be null");
        Objects.requireNonNull(endKey, "endKey cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.deleteTimeNanos.accept(delta);
    }

    @Override
    public synchronized void update(byte[] key, byte[] value) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.mergeTimeNanos.accept(delta);
    }

    @Override
    public synchronized void writeBatch(Batch batch) {
        long start = System.nanoTime();
        Objects.requireNonNull(batch, "batch cannot be null");
        validateOpen();
        // TODO Auto-generated method stub

        long delta = System.nanoTime() - start;
        stats.allOpsTimeNanos.accept(delta);
        stats.batchTimeNanos.accept(delta);
    }

    @Override
    public synchronized void syncWAL() {
        if (isOpen()) {
            long start = System.nanoTime();
            try {
                if (txnDb.isOwningHandle()) {
                    txnDb.flushWal(true);
                }
            } catch (RocksDBException e) {
                throw new StoreException(e);
            }
            long delta = System.nanoTime() - start;
            stats.allOpsTimeNanos.accept(delta);
            stats.walTimeNanos.accept(delta);
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    private void validateOpen() {
        if (!isOpen()) {
            throw new StoreException("KVStore " + path + " is closed");
        }
    }

    @Override
    public synchronized void flush() {
        if (isOpen()) {
            long start = System.nanoTime();
            try {
                txnDb.flush(flushOptions);
            } catch (RocksDBException e) {
                throw new StoreException(e);
            }
            long delta = System.nanoTime() - start;
            stats.allOpsTimeNanos.accept(delta);
            stats.flushTimeNanos.accept(delta);
        }
    }

    @Override
    public synchronized void flushNoWait() {
        if (isOpen()) {
            long start = System.nanoTime();
            try {
                txnDb.flush(flushOptionsNoWait);
            } catch (RocksDBException e) {
                throw new StoreException(e);
            }
            long delta = System.nanoTime() - start;
            stats.allOpsTimeNanos.accept(delta);
            stats.flushTimeNanos.accept(delta);
        }
    }

    @Override
    public synchronized Stats getStats() {
        return stats;
    }

    private static void close(AutoCloseable ac) {
        if (ac != null) {
            try {
                ac.close();
            } catch (Exception ignore) {
                logger.log(Level.INFO, "", ignore);
            }
        }
    }

    private static interface ThrowingSupplier {
        Object get() throws Exception;
    }

    private static Object wrapEx(ThrowingSupplier block) {
        try {
            return block.get();
        } catch (Exception e) {
            logger.log(Level.WARNING, "", e);
            throw new StoreException(e);
        }
    }

    private static void ignoreEx(Runnable block) {
        try {
            block.run();
        } catch (Exception ignore) {
            logger.log(Level.INFO, "", ignore);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        KVStore kvs = new KVStore(Paths.get("D:/Temp/rocksdb_database"));
        kvs.putIfAbsent(new byte[] { 1, 2, 3, 4 }, new byte[] { 1, 2, 3, 4 });
        kvs.flushNoWait();
        kvs.close();
        Thread.sleep(3000L);
    }
}
