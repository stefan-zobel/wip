package catenaq;

public interface KueueMsgConsumer {

    boolean accept(byte[] message);
}
