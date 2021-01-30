package lock;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class GlobalLock {

    private static final ReentrantLock acquisitionLock = new ReentrantLock(true);
    private static final ReentrantLock realLock = new ReentrantLock(false);
    private static final AtomicReference<Lock> globalLock = new AtomicReference<Lock>(NoopLock.INSTANCE);

    public static boolean acquire() {
        acquisitionLock.lock();
        return globalLock.compareAndSet(NoopLock.INSTANCE, realLock);
    }

    public static boolean release() {
        if (acquisitionLock.isHeldByCurrentThread()) {
            boolean success = globalLock.compareAndSet(realLock, NoopLock.INSTANCE);
            acquisitionLock.unlock();
            return success;
        }
        return false;
    }

    public static Lock get() {
        return globalLock.get();
    }

    private GlobalLock() {
    }
}
