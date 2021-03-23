package catenaq;

public interface Kueue {

    void put(byte[] value);
    byte[] take() throws InterruptedException;
    boolean accept(KueueMsgConsumer consumer);
    long size();
    boolean isEmpty();
    boolean isClosed();
    void clear();
}