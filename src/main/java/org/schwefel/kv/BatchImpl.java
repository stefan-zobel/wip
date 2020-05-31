package org.schwefel.kv;

import java.util.Objects;

import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;

class BatchImpl implements Batch, AutoCloseable {

    private volatile WriteBatch batch;

    BatchImpl() {
        batch = new WriteBatch();
    }

    @Override
    public synchronized void put(byte[] key, byte[] value) {
        Objects.requireNonNull(key, "key cannot be null");
        validateOwned();
        try {
            batch.put(key, value);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        }
    }

    @Override
    public synchronized void delete(byte[] key) {
        Objects.requireNonNull(key, "key cannot be null");
        validateOwned();
        try {
            batch.delete(key);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        }
    }

    @Override
    public synchronized void deleteRange(byte[] beginKey, byte[] endKey) {
        Objects.requireNonNull(beginKey, "beginKey cannot be null");
        Objects.requireNonNull(endKey, "endKey cannot be null");
        validateOwned();
        try {
            batch.deleteRange(beginKey, endKey);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        }
    }

    @Override
    public synchronized void update(byte[] key, byte[] value) {
        Objects.requireNonNull(key, "key cannot be null");
        validateOwned();
        try {
            batch.merge(key, value);
        } catch (RocksDBException e) {
            throw new StoreException(e);
        }
    }

    synchronized WriteBatch cedeOwnership() {
        WriteBatch b = batch;
        batch = null;
        return b;
    }

    public synchronized void close() {
        if (batch != null) {
            try {
                batch.close();
            } finally {
                batch = null;
            }
        }
    }

    private void validateOwned() {
        if (batch == null) {
            throw new StoreException("Batch has already lost ownership");
        }
    }
}
