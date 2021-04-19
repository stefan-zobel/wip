/*
 * Copyright 2019 Stefan Zobel
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
package collections.indexable;

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
final class NoopLock implements Lock {

    /**
     * The one and only {@code NoopLock} instance.
     */
    static final Lock INSTANCE = new NoopLock();

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
