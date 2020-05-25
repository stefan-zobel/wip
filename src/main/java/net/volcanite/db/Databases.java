package net.volcanite.db;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.Transaction;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import org.rocksdb.TransactionOptions;
import org.rocksdb.WriteOptions;

public final class Databases {

    public static final class DB implements AutoCloseable {
        public final TransactionDBOptions txnDbOptions;
        public final TransactionDB txnDb;
        public final TransactionOptions txnOpts;
        public final WriteOptions writeOptions;
        public final String dbPath;

        DB(TransactionDBOptions txnDbOptions, TransactionDB txnDb, TransactionOptions txnOpts, String dbPath) {
            this.txnDbOptions = txnDbOptions;
            this.txnDb = txnDb;
            this.txnOpts = txnOpts;
            this.writeOptions = new WriteOptions();
            this.dbPath = dbPath;
        }

        public void close() {
            Databases.close(dbPath);
        }
    }

    private static final int PARALLELISM = 2;

    private static final Logger logger = Logger.getLogger(Databases.class.getName());

    private static final HashMap<String, DB> databases = new HashMap<>();

    private Databases() {
        throw new AssertionError();
    }

    public static DB prepare(String dbPath) {
        DB database = obtain(dbPath);
        if (database != null) {
            return database;
        }
        DB db = open(dbPath);
        databases.put(dbPath, db);
        return db;
    }

    private static DB open(String dbPath) {
        @SuppressWarnings("resource")
        Options options = new Options().setCreateIfMissing(true).setIncreaseParallelism(PARALLELISM);
        TransactionDBOptions txnDbOptions = new TransactionDBOptions();
        TransactionDB txnDb = (TransactionDB) wrap(() -> TransactionDB.open(options, txnDbOptions, dbPath));
        TransactionOptions txnOpts = new TransactionOptions();
        return new DB(txnDbOptions, txnDb, txnOpts, dbPath);
    }

    public static DB obtain(String dbPath) {
        return databases.get(dbPath);
    }

    public static Transaction newTransaction(String dbPath) {
        DB db = obtain(dbPath);
        if (db != null) {
            return db.txnDb.beginTransaction(db.writeOptions, db.txnOpts);
        }
        return null;
    }

    public static void close(String dbPath) {
        DB database = databases.remove(dbPath);
        if (database != null) {
            try {
                tryFlushWal(database);
            } finally {
                close(database.txnDb);
                close(database.txnDbOptions);
                close(database.txnOpts);
                close(database.writeOptions);
            }
        }
    }

    private static void tryFlushWal(DB db) {
        try {
            if (db.txnDb.isOwningHandle()) {
                db.txnDb.flushWal(true);
            }
        } catch (RocksDBException e) {
            throw new DBException(e);
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
            throw new DBException(e);
        }
    }
}
