package disk;

import java.nio.charset.Charset;

public interface MessageConsumer {

    static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");

    /**
     * Get the next message from the queue. Implementors must throw an exception
     * if they don't want that message to be permanently removed from the queue.
     * Note that {@code message} will be {@code null} if there is no message
     * available!
     * 
     * @param message
     *            the next message in the queue or {@code null} if currently
     *            there is no message available
     * @throws Throwable
     *             the implementation may throw anything it wants to throw to
     *             indicate that it can't (yet) process the message and it
     *             therefore must not be removed from the queue
     */
    void acceptMessage(byte[] message) throws Throwable;
}
