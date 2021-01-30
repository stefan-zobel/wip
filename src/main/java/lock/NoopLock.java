package lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A {@code no-op} {@link Lock} implementation that does nothing apart from
 * checking the {@code interrupted status} of the current thread if either
 * {@link #lockInterruptibly()} or {@link #tryLock(long, TimeUnit)} is called.
 * <p>
 * Note that {@link #newCondition()} can't be used (it throws an
 * {@link UnsupportedOperationException} always).
 */
public final class NoopLock implements Lock {

    /**
     * The one and only {@code NoopLock} instance.
     */
    public static final Lock INSTANCE = new NoopLock();

    @Override
    public void lock() {
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    @Override
    public boolean tryLock() {
        return true;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    @Override
    public void unlock() {
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    private NoopLock() {
    }
}
