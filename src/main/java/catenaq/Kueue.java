package catenaq;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.schwefel.kv.Kind;
import org.schwefel.kv.StoreOps;

import net.volcanite.util.Byte8Key;

/**
 * A simple persistent embedded (in-process) FIFO queue.
 */
public class Kueue {

    private Byte8Key minKey = new Byte8Key();
    private Byte8Key maxKey = new Byte8Key();

    private final StoreOps ops;
    private final Kind id;

    /** Lock held by put */
    private final ReentrantLock putLock = new ReentrantLock(true);
    /** Lock held by take */
    private final ReentrantLock takeLock = new ReentrantLock(true);
    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();
    /** Current number of messages */
    private final AtomicLong count;

    public Kueue(StoreOps store, String identifier) {
        ops = Objects.requireNonNull(store, "store");
        id = store.getKindManagement().getOrCreateKind(Objects.requireNonNull(identifier, "identifier"));
        Byte8Key lastMax = maxKey;
        byte[] currentMin = ops.findMinKey(id);
        byte[] currentMax = ops.findMaxKey(id);
        if (currentMin != null) {
            minKey = new Byte8Key(currentMin);
        }
        if (currentMax != null) {
            Byte8Key nextMax = new Byte8Key(currentMax);
            lastMax = new Byte8Key(nextMax.currentValue());
            nextMax.increment();
            maxKey = nextMax;
        }
        if (lastMax.currentValue() < minKey.currentValue()) {
            throw new IllegalStateException("maxKey < minKey");
        }
        long quantity = maxKey.minus(minKey);
        count = new AtomicLong(quantity);
    }

    public long size() {
        return count.get();
    }

    public boolean isEmpty() {
        return size() == 0L;
    }

    public boolean isClosed() {
        return !ops.isOpen();
    }

    /**
     * Signals a waiting take. Called only from put.
     */
    private void signalNotEmpty() {
        ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    public void put(byte[] value) {
        Objects.requireNonNull(value, "value");
        long c = -1L;
        ReentrantLock putLock = this.putLock;
        AtomicLong count = this.count;
        putLock.lock();
        try {
            ops.put(id, maxKey.next(), value);
            c = count.getAndIncrement();
        } catch (Throwable t) {
            maxKey.decrement();
            throw t;
        } finally {
            putLock.unlock();
        }
        if (c == 0L) {
            signalNotEmpty();
        }
    }

    public byte[] take() throws InterruptedException {
        byte[] value;
        long c = -1L;
        ReentrantLock takeLock = this.takeLock;
        AtomicLong count = this.count;
        takeLock.lock();
        try {
            while (count.get() == 0L) {
                notEmpty.await();
            }
            value = ops.singleDeleteIfPresent(id, minKey.current());
            minKey.increment();
            if (value != null) {
                c = count.getAndDecrement();
            }
            if (c > 1L) {
                // signal other waiting takers
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        return value;
    }
}
