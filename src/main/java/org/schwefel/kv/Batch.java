package org.schwefel.kv;

public interface Batch {

    void put(byte[] key, byte[] value);
    void delete(byte[] key);
    void deleteRange(byte[] beginKey, byte[] endKey);
    void update(byte[] key, byte[] value); // merge
    void syncWAL(); // ???
    // boolean isOpen(); // ???
}
