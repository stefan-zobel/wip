package net.volcanite.db;

import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;

import net.volcanite.util.DoubleStatistics;

public class Get extends DB {

	private final DoubleStatistics readTimeNanos = DoubleStatistics.newInstance();

	private final ReadOptions readOptions;

	public Get(String dbPath) {
		super(dbPath);
		readOptions = new ReadOptions();
	}

	@Override
	public void close() {
		try {
			super.close();
		} finally {
			close(readOptions);
		}
	}

	public byte[] read(byte[] key) {
		try {
			return read(key, db.txnDb, readOptions);
		} catch (RocksDBException e) {
			throw new DBException(e);
		}
	}

	private byte[] read(byte[] key, TransactionDB txnDb, ReadOptions readOptions)
			throws RocksDBException {

		long readStart = System.nanoTime();
		try {
			return txnDb.get(readOptions, key);
		} finally {
			readTimeNanos.accept(System.nanoTime() - readStart);
		}
	}

	public DoubleStatistics getReadTimeNanos() {
		return readTimeNanos;
	}
}
