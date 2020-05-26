package org.schwefel.kv;

public interface StoreOps extends AutoCloseable {

//	void open();
	void close();
	void put(byte[] key, byte[] value);
	void putIfAbsent(byte[] key, byte[] value);
	byte[] get(byte[] key);
	void delete(byte[] key);
	void deleteRange(byte[] beginKey, byte[] endKey);
	void update(byte[] key, byte[] value); // merge
	void writeBatch(Batch batch);
//	Tx startTx(); // ???
	void syncWAL(); // syncWAL ???
	boolean isOpen(); // ???
	void flush();
}
