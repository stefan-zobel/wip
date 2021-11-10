/*
 * Copyright 2021 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A cheap "best effort" {@link Lock} implementation that does nothing unless
 * {@link #tryExecuteLocked(Runnable)} is executed. During the call to
 * {@link #tryExecuteLocked(Runnable)}, the {@code ToggleLock} behaves like a
 * non-fair {@link ReentrantLock}. As soon as the call returns the behavior of
 * the {@code ToggleLock} has been restored to a {@code no-op} implementation.
 * The shared resource is <b><i>not</i></b> protected against modification by
 * threads which already have acquired the lock before the execution of
 * {@link #tryExecuteLocked(Runnable)} began.
 * <p>
 * Note that {@link #newCondition()} can't be used (it throws an
 * {@link UnsupportedOperationException} always).
 */
public final class ToggleLock implements Lock {

    private final AtomicInteger acquired = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock(false);

    /**
     * Tries to acquire the lock and if that succeeds the {@code codeBlock} gets
     * executed and {@code true} will be returned (exceptions thrown by
     * {@code codeBlock} will be relayed to the caller). If the lock can't be
     * acquired {@code false} will be returned and {@code codeBlock} won't get
     * executed. Note that the shared resource is <b><i>not</i></b> protected
     * against concurrent modification by threads which already have acquired
     * the {@code no-op} lock before the execution of this method.
     * 
     * @param codeBlock
     *            the {@code Runnable} to execute when the lock could be
     *            acquired
     * @return {@code true} when the lock could be acquired and the
     *         {@code codeBlock} got executed, {@code false} otherwise
     */
    public boolean tryExecuteLocked(Runnable codeBlock) {
        boolean success = acquire();
        if (success) {
            // wait until we get the lock
            lock();
            // we own the lock
            try {
                codeBlock.run();
            } finally {
                // we are ready, free the lock
                unlock();
                // and release
                release();
            }
        }
        return success;
    }

    private boolean acquire() {
        return acquired.compareAndSet(0, 1);
    }

    private void release() {
        if (!acquired.compareAndSet(1, 0)) {
            throw new IllegalStateException("lock hasn't been acquired!");
        }
    }

    private boolean isAcquired() {
        return acquired.get() == 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock() {
        if (isAcquired()) {
            lock.lock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlock() {
        // we have to unlock regardless of the acquired state, otherwise there
        // can be race conditions!
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (isAcquired()) {
            lock.lockInterruptibly();
        } else if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock() {
        if (isAcquired()) {
            return lock.tryLock();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (isAcquired()) {
            return lock.tryLock(time, unit);
        } else if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    /**
     * Not implemented. Throws {@link UnsupportedOperationException} always.
     * 
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
