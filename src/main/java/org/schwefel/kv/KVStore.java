package org.schwefel.kv;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteOptions;

public class KVStore implements StoreOps {

    private static final Logger logger = Logger.getLogger(KVStore.class.getName());

    private volatile boolean open = false;

    private TransactionDB txnDb;
    private TransactionDBOptions txnDbOptions;
    private TransactionOptions txnOpts;
    private Options options;
    private WriteOptions writeOptions;
    private ReadOptions readOptions;
    private FlushOptions flushOptions;
    private final Path dir;

    public KVStore(Path dir) {
        this.dir = Objects.requireNonNull(dir);
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
        txnDbOptions = new TransactionDBOptions();
        txnDb = (TransactionDB) wrap(() -> TransactionDB.open(options, txnDbOptions, dir.toFile().getCanonicalPath()));
        txnOpts = new TransactionOptions();
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        wrap(() -> syncWAL());
        wrap(() -> flush());
        open = false;
        close(txnDb);
        close(txnDbOptions);
        close(txnOpts);
        close(writeOptions);
        close(readOptions);
        close(flushOptions);
        close(options);
        txnDb = null;
        txnDbOptions = null;
        txnOpts = null;
        writeOptions = null;
        readOptions = null;
        flushOptions = null;
        options = null;
    }

    @Override
    public synchronized void put(byte[] key, byte[] value) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void putIfAbsent(byte[] key, byte[] value) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized byte[] get(byte[] key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public synchronized void delete(byte[] key) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void deleteRange(byte[] beginKey, byte[] endKey) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void update(byte[] key, byte[] value) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void writeBatch(Batch batch) {
        // TODO Auto-generated method stub

    }

    @Override
    public synchronized void syncWAL() {
        if (isOpen()) {
            try {
                if (txnDb.isOwningHandle()) {
                    txnDb.flushWal(true);
                }
            } catch (RocksDBException e) {
                throw new StoreException(e);
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void flush() {
        if (isOpen()) {
            try {
                txnDb.flush(flushOptions);
            } catch (RocksDBException e) {
                throw new StoreException(e);
            }
        }
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
        RocksObject get() throws Exception;
    }

    private static RocksObject wrap(ThrowingSupplier block) {
        try {
            return block.get();
        } catch (Exception e) {
            throw new StoreException(e);
        }
    }

    private static void wrap(Runnable block) {
        try {
            block.run();
        } catch (Exception e) {
            logger.log(Level.INFO, "", e);
        }
    }
}
