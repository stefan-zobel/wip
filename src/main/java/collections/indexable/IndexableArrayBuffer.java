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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Simple baseline implementation of {@link Indexable} which wraps an
 * {@link ArrayList}.
 *
 * @param <E>
 *            the type of the elements
 */
public final class IndexableArrayBuffer<E> implements Indexable<E> {

    private final ArrayList<E> list;
    private final boolean synced;
    private final Lock lock;

    /**
     * Constructs an empty IndexableArrayBuffer which is not
     * {@linkplain Indexable#isSynced() syncable}.
     */
    public IndexableArrayBuffer() {
        this(false);
    }

    /**
     * Constructs an empty IndexableArrayBuffer which is
     * {@linkplain Indexable#isSynced() syncable} if the {@code synced}
     * parameter is {@code true}.
     * 
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} IndexableArrayBuffer
     */
    public IndexableArrayBuffer(boolean synced) {
        lock = synced ? new ReentrantLock() : NoopLock.INSTANCE;
        this.synced = synced;
        list = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof IndexableArrayBuffer) {
            return sync(() -> {
                IndexableArrayBuffer<?> other = (IndexableArrayBuffer<?>) o;
                try {
                    other.lockInterruptibly();
                    return list.equals(other.list);
                } finally {
                    other.unlock();
                }
            });
        }
        if (!(o instanceof Indexable)) {
            return false;
        }
        return sync(() -> {
            Indexable<?> other = (Indexable<?>) o;
            final int s = list.size();
            try {
                other.lockInterruptibly();
                boolean equal;
                if (equal = (s == other.size())) {
                    for (int i = 0; i < s; i++) {
                        if (!Objects.equals(list.get(i), other.get(i))) {
                            equal = false;
                            break;
                        }
                    }
                }
                return equal;
            } finally {
                other.unlock();
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return sync(() -> list.hashCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return sync(() -> list.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E removeAt(int index) {
        return remove(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E removeAtRandom() {
        return sync(() -> {
            if (isEmpty()) {
                return null;
            }
            return remove(ThreadLocalRandom.current().nextInt(size()));
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureCapacity(int minCapacity) {
        sync(() -> {
            list.ensureCapacity(minCapacity);
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void trimToSize() {
        sync(() -> {
            list.trimToSize();
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSynced() {
        return synced;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Lock getLock() {
        return lock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEach(Consumer<? super E> action) {
        sync(() -> {
            list.forEach(action);
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return sync(() -> list.removeIf(filter));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        sync(() -> {
            list.replaceAll(operator);
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sort(Comparator<? super E> c) {
        sync(() -> {
            list.sort(c);
            return null;
        });
    }

    /**
     * Must be manually synced by the caller!
     */
    @Override
    public Spliterator<E> spliterator() {
        // Must be manually synced by caller
        return list.spliterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return sync(() -> list.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return sync(() -> list.isEmpty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object o) {
        return sync(() -> list.contains(o));
    }

    /**
     * Must be manually synced by the caller!
     */
    @Override
    public Iterator<E> iterator() {
        // Must be manually synced by caller
        return list.iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] toArray() {
        return sync(() -> list.toArray());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return sync(() -> list.toArray(a));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(E e) {
        return sync(() -> list.add(e));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object o) {
        return sync(() -> list.remove(o));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return sync(() -> list.containsAll(c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        return sync(() -> list.addAll(c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return sync(() -> list.addAll(index, c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return sync(() -> list.removeAll(c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return sync(() -> list.retainAll(c));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        sync(() -> {
            list.clear();
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E get(int index) {
        return sync(() -> list.get(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public E set(int index, E element) {
        return sync(() -> list.set(index, element));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(int index, E element) {
        sync(() -> {
            list.add(index, element);
            return null;
        });
    }

    /**
     * Removes the element at the specified position in this
     * IndexableArrayBuffer. Shifts any subsequent elements to the left
     * (subtracts one from their indices).
     *
     * @param index
     *            the index of the element to be removed
     * @return the element that was removed from the IndexableArrayBuffer
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             (<tt>index &lt; 0 || index &gt;= size()</tt>)
     */
    public E remove(int index) {
        return sync(() -> list.remove(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int indexOf(Object o) {
        return sync(() -> list.indexOf(o));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int lastIndexOf(Object o) {
        return sync(() -> list.lastIndexOf(o));
    }

    /**
     * Must be manually synced by the caller!
     */
    @Override
    public ListIterator<E> listIterator() {
        // Must be manually synced by caller
        return list.listIterator();
    }

    /**
     * Must be manually synced by the caller!
     */
    @Override
    public ListIterator<E> listIterator(int index) {
        // Must be manually synced by caller
        return list.listIterator(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<E> toList() {
        return sync(() -> new ArrayList<>(list));
    }

    <T> T sync(Supplier<T> block) {
        if (!synced) {
            return block.get();
        }
        final Lock lock = this.lock;
        try {
            lock.lockInterruptibly();
            try {
                return block.get();
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            throw translate(e);
        }
    }

    private static CancellationException translate(InterruptedException e) {
        Thread.currentThread().interrupt();
        CancellationException ce = new CancellationException(
                "Got interrupted while waiting to acquire a ReentrantLock");
        ce.initCause(e);
        return ce;
    }
}
