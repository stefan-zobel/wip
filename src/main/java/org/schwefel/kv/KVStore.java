package org.schwefel.kv;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.FlushOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
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
	private final WriteOptions writeOptions;
	private final ReadOptions readOptions;
	private final FlushOptions flushOptions;

	public KVStore(Path dir) {
		// TODO Auto-generated constructor stub
		writeOptions = new WriteOptions(); // XXX ???
		readOptions = new ReadOptions(); // XXX ???
		flushOptions = new FlushOptions(); // XXX ???
	}

	@Override
	public void close() {
		if (!isOpen()) {
			return;
		}
		try {
			syncWAL();
		} catch (Exception e) {
			logger.log(Level.INFO, "", e);
		}
		try {
			flush();
		} catch (Exception e) {
			logger.log(Level.INFO, "", e);
		}
		open = false;
        close(txnDb);
        close(txnDbOptions);
        close(txnOpts);
        close(writeOptions);
        close(readOptions);
        close(flushOptions);
	}

	@Override
	public void put(byte[] key, byte[] value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void putIfAbsent(byte[] key, byte[] value) {
		// TODO Auto-generated method stub

	}

	@Override
	public byte[] get(byte[] key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void delete(byte[] key) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deleteRange(byte[] beginKey, byte[] endKey) {
		// TODO Auto-generated method stub

	}

	@Override
	public void update(byte[] key, byte[] value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void writeBatch(Batch batch) {
		// TODO Auto-generated method stub

	}

	@Override
	public void syncWAL() {
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
	public void flush() {
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
}
