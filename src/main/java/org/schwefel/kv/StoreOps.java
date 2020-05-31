package org.schwefel.kv;

public interface StoreOps extends AutoCloseable {

    void close();
    void put(byte[] key, byte[] value);
    void putIfAbsent(byte[] key, byte[] value);
    byte[] get(byte[] key);
    void delete(byte[] key);
    void deleteRange(byte[] beginKey, byte[] endKey);
    void update(byte[] key, byte[] value); // merge
    Batch createBatch();
    void writeBatch(Batch batch);
//  Tx startTx(); // ???
    void syncWAL();
    boolean isOpen(); // ???
    void flush();
    void flushNoWait();
//    void reopen(); // ???
    Stats getStats();
}
