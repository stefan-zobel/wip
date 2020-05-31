package org.schwefel.kv;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteBatch;
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
    private final String path;
    private final Stats stats = new Stats();

    public KVStore(Path dir) {
        this.path = (String) wrapEx(() -> Objects.requireNonNull(dir).toFile().getCanonicalPath());
        wrapEx(() -> Files.createDirectories(dir));
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
        try {
            put_(key, value);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            stats.allOpsTimeNanos.accept(System.nanoTime() - start);
        }
    }

    private void put_(byte[] key, byte[] value) throws RocksDBException {
        long putStart = System.nanoTime();
        try (Transaction txn = txnDb.beginTransaction(writeOptions, txnOpts)) {
            txn.put(key, value);
            txn.commit();
            stats.putTimeNanos.accept(System.nanoTime() - putStart);
            occasionalWalSync();
        }
    }

    private void occasionalWalSync() {
        ++totalSinceLastFsync;
        if (System.currentTimeMillis() - lastSync >= FLUSH_TIME_WINDOW_MILLIS) {
            syncWAL();
            lastSync = System.currentTimeMillis();
            totalSinceLastFsync = 0L;
        } else if (totalSinceLastFsync % FLUSH_BATCH_SIZE == 0L) {
            syncWAL();
            lastSync = System.currentTimeMillis();
            totalSinceLastFsync = 0L;
        }
    }

    @Override
    public synchronized void putIfAbsent(byte[] key, byte[] value) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        try {
            if (get_(key) == null) {
                put_(key, value);
            }
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            stats.allOpsTimeNanos.accept(System.nanoTime() - start);
        }
    }

    @Override
    public synchronized byte[] get(byte[] key) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        try {
            return get_(key);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            stats.allOpsTimeNanos.accept(System.nanoTime() - start);
        }
    }

    private byte[] get_(byte[] key) throws RocksDBException {
        long start = System.nanoTime();
        try {
            return txnDb.get(readOptions, key);
        } finally {
            stats.getTimeNanos.accept(System.nanoTime() - start);
        }
    }

    @Override
    public synchronized void delete(byte[] key) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        try {
            delete_(key);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            stats.allOpsTimeNanos.accept(System.nanoTime() - start);
        }
    }

    private void delete_(byte[] key) throws RocksDBException {
        long delStart = System.nanoTime();
        try (Transaction txn = txnDb.beginTransaction(writeOptions, txnOpts)) {
            txn.delete(key);
            txn.commit();
            stats.deleteTimeNanos.accept(System.nanoTime() - delStart);
            occasionalWalSync();
        }
    }

    @Override
    public synchronized void deleteRange(byte[] beginKey, byte[] endKey) {
        long start = System.nanoTime();
        Objects.requireNonNull(beginKey, "beginKey cannot be null");
        Objects.requireNonNull(endKey, "endKey cannot be null");
        validateOpen();
        try {
            txnDb.deleteRange(writeOptions, beginKey, endKey);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            long delta = System.nanoTime() - start;
            stats.allOpsTimeNanos.accept(delta);
            stats.deleteTimeNanos.accept(delta);
            occasionalWalSync();
        }
    }

    @Override
    public synchronized void update(byte[] key, byte[] value) {
        long start = System.nanoTime();
        Objects.requireNonNull(key, "key cannot be null");
        validateOpen();
        try {
            update_(key, value);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        } finally {
            stats.allOpsTimeNanos.accept(System.nanoTime() - start);
        }
    }

    private void update_(byte[] key, byte[] value) throws RocksDBException {
        long updStart = System.nanoTime();
        try (Transaction txn = txnDb.beginTransaction(writeOptions, txnOpts)) {
            txn.merge(key, value);
            txn.commit();
            stats.mergeTimeNanos.accept(System.nanoTime() - updStart);
            occasionalWalSync();
        }
    }

    @Override
    public synchronized void writeBatch(Batch batch) {
        long start = System.nanoTime();
        Objects.requireNonNull(batch, "batch cannot be null");
        validateOpen();
        WriteBatch wb = ((BatchImpl) batch).cedeOwnership();
        if (wb != null) {
            try {
                txnDb.write(writeOptions, wb);
            } catch (RocksDBException e) {
                throw new StoreException(e);
            } finally {
                close(wb);
                long delta = System.nanoTime() - start;
                stats.allOpsTimeNanos.accept(delta);
                stats.batchTimeNanos.accept(delta);
            }
        }
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
            } finally {
                long delta = System.nanoTime() - start;
                stats.allOpsTimeNanos.accept(delta);
                stats.walTimeNanos.accept(delta);
            }
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
            } finally {
                long delta = System.nanoTime() - start;
                stats.allOpsTimeNanos.accept(delta);
                stats.flushTimeNanos.accept(delta);
            }
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
            } finally {
                long delta = System.nanoTime() - start;
                stats.allOpsTimeNanos.accept(delta);
                stats.flushTimeNanos.accept(delta);
            }
        }
    }

    @Override
    public Batch createBatch() {
        return new BatchImpl();
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
}
