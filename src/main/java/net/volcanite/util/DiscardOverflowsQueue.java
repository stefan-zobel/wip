package net.volcanite.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A bounded asymmetrically blocking queue that discards new elements if the max
 * capacity is reached instead of blocking client threads trying to add new
 * elements. Threads attempting to read from the queue are still blocked if the
 * queue is empty.
 */
public final class DiscardOverflowsQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 3052494991610810267L;
    private static final Logger logger = Logger.getLogger(DiscardOverflowsQueue.class.getName());

    /**
     * Default maximum capacity for {@code DiscardOverflowsQueue} if no capacity
     * is specified (currently {@code 15_000_000}).
     */
    public static final int DEFAULT_MAX_CAPACITY = 15_000_000;

    /** The capacity bound */
    private final int capacity;

    /**
     * Create a new DiscardOverflowsQueue of max capacity
     * {@link DiscardOverflowsQueue#DEFAULT_MAX_CAPACITY}.
     */
    public DiscardOverflowsQueue() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * Create a new DiscardOverflowsQueue of max capacity {@code capacity}.
     * 
     * @param capacity
     *            max capacity.
     */
    public DiscardOverflowsQueue(int capacity) {
        super(capacity);
        this.capacity = capacity;
    }

    /**
     * Non-blocking put. New elements are discarded if the queue is currently
     * full.
     */
    @Override
    public void put(Runnable o) throws InterruptedException {
        this.offer(o);
    }

    /**
     * Non-blocking add. New elements are discarded if the queue is currently
     * full.
     */
    @Override
    public boolean add(Runnable o) {
        return this.offer(o);
    }

    /**
     * Non-blocking offer. New elements are discarded if the queue is currently
     * full.
     */
    @Override
    public boolean offer(Runnable o) {
        boolean couldAdd = super.offer(o);
        if (!couldAdd) {
            logger.log(Level.WARNING,
                    "Rejected Runnable: " + o.toString() + " (reached queue max. capacity of " + this.capacity + ")");
        }
        return couldAdd;
    }
}
