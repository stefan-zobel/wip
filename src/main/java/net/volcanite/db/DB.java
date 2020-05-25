package net.volcanite.db;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.RocksDBException;

class DB implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(DB.class.getName());

    final String dbPath;

    final Databases.DB db;

    DB(String dbPath) {
        this.db = Databases.prepare(dbPath);
        this.dbPath = dbPath;
    }

    @Override
    public void close() {
        tryFlushWal();
    }

    public final String getDbPath() {
        return dbPath;
    }

    void tryFlushWal() {
        try {
            if (db.txnDb.isOwningHandle()) {
                db.txnDb.flushWal(true);
            }
        } catch (RocksDBException e) {
            throw new DBException(e);
        }
    }

    void close(AutoCloseable ac) {
        if (ac != null) {
            try {
                ac.close();
            } catch (Exception ignore) {
                logger.log(Level.INFO, "", ignore);
            }
        }
    }
}
