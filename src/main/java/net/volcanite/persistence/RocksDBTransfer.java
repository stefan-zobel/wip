package net.volcanite.persistence;

import net.volcanite.db.Put;

public final class RocksDBTransfer {

    public static void transmit(byte[] key, byte[] value) {
        PutHolder.INSTANCE.write(key, value);
    }

    public static void shutdown() {
        PutHolder.INSTANCE.close();
    }

    private static final class PutHolder {
        static final Put INSTANCE = new Put(DbPath.getPath());
    }

    private RocksDBTransfer() {
        throw new AssertionError();
    }
}
