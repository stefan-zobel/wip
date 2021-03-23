package catenaq;

/**
 * A simple RocksDB-based in-process durable queue.
 */
public interface Kueue {

    void put(byte[] value);
    byte[] take() throws InterruptedException;
    boolean accept(KueueMsgConsumer consumer);
    long size();
    boolean isEmpty();
    boolean isClosed();
    String identifier();
    void clear();
}
