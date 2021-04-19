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
/*
 * Written by Josh Bloch of Google Inc. and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/.
 */
package collections.indexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Stack;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Resizable-array implementation of the {@link VecDeque} interface. Array
 * deques have no capacity restrictions; they grow as necessary to support
 * usage. Null elements are prohibited. This class is likely to be faster than
 * {@link Stack} when used as a stack, and faster than {@link LinkedList} when
 * used as a queue.
 *
 * <p>
 * Indexed operations like {@link #get(int)} or {@link #set(int, Object)} run in
 * constant time. Most other {@code IndexableArrayDeque} operations run in
 * amortized constant time. Exceptions include {@link #remove(Object) remove},
 * {@link #removeFirstOccurrence removeFirstOccurrence},
 * {@link #removeLastOccurrence removeLastOccurrence}, {@link #contains
 * contains}, {@link #iterator iterator.remove()}, and the bulk operations, all
 * of which run in linear time.
 *
 * <p>
 * This class and its iterator implement all of the <em>optional</em> methods of
 * the {@link Collection} and {@link Iterator} interfaces.
 *
 * @param <E>
 *            the type of elements held in this deque
 */
public final class IndexableArrayDeque<E> implements VecDeque<E> {
    // CVS rev. 1.138
    /*
     * VMs excel at optimizing simple array loops where indices are incrementing
     * or decrementing over a valid slice, e.g.
     *
     * for (int i = start; i < end; i++) ... elements[i]
     *
     * Because in a circular array, elements are in general stored in two
     * disjoint such slices, we help the VM by writing unusual nested loops for
     * all traversals over the elements. Having only one hot inner loop body
     * instead of two or three eases human maintenance and encourages VM loop
     * inlining into the caller.
     */

    /**
     * The array in which the elements of the deque are stored. All array cells
     * not holding deque elements are always null. The array always has at least
     * one null slot (at tail).
     */
    Object[] elements;

    /**
     * The index of the element at the head of the deque (which is the element
     * that would be removed by remove() or pop()); or an arbitrary number 0 <=
     * head < elements.length equal to tail if the deque is empty.
     */
    int head;

    /**
     * The index at which the next element would be added to the tail of the
     * deque (via addLast(E), add(E), or push(E)); elements[tail] is always
     * null.
     */
    int tail;

    private final boolean synced;
    private final Lock lock;

    /**
     * The maximum size of array to allocate. Some VMs reserve some header words
     * in an array. Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity of this deque by at least the given amount.
     *
     * @param needed
     *            the required minimum extra capacity; must be positive
     */
    private void grow(int needed) {
        // overflow-conscious code
        final int oldCapacity = elements.length;
        int newCapacity;
        // Double capacity if small; else grow by 50%
        int jump = jump();
        if (jump < needed || (newCapacity = (oldCapacity + jump)) - MAX_ARRAY_SIZE > 0) {
            newCapacity = newCapacity(needed, jump);
        }
        final Object[] es = elements = Arrays.copyOf(elements, newCapacity);
        // Exceptionally, here tail == head needs to be disambiguated
        if (tail < head || (tail == head && es[head] != null)) {
            // wrap around; slide first leg forward to end of array
            int newSpace = newCapacity - oldCapacity;
            System.arraycopy(es, head, es, head + newSpace, oldCapacity - head);
            for (int i = head, to = (head += newSpace); i < to; i++) {
                es[i] = null;
            }
        }
        // checkInvariants();
    }

    private int jump() {
        final int oldCapacity = elements.length;
        return (oldCapacity < 64) ? (oldCapacity + 2) : (oldCapacity >> 1);
    }

    /** Capacity calculation for edge conditions, especially overflow. */
    private int newCapacity(int needed, int jump) {
        final int oldCapacity = elements.length, minCapacity;
        if ((minCapacity = oldCapacity + needed) - MAX_ARRAY_SIZE > 0) {
            if (minCapacity < 0) {
                throw new IllegalStateException("Sorry, deque too big");
            }
            return Integer.MAX_VALUE;
        }
        if (needed > jump) {
            return minCapacity;
        }
        return (oldCapacity + jump - MAX_ARRAY_SIZE < 0) ? oldCapacity + jump : MAX_ARRAY_SIZE;
    }

    /**
     * Increases the internal storage of this collection, if necessary, to
     * ensure that it can hold at least the given number of elements.
     *
     * @param minCapacity
     *            the desired minimum capacity
     */
    @Override
    public void ensureCapacity(int minCapacity) {
        sync(() -> {
            int needed;
            if ((needed = (minCapacity + 1 - elements.length)) > 0) {
                grow(needed);
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * Minimizes the internal storage of this collection.
     */
    public void trimToSize() {
        sync(() -> {
            int size;
            if ((size = size()) + 1 < elements.length) {
                elements = toArray(new Object[size + 1]);
                head = 0;
                tail = size;
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * Constructs an empty IndexableArrayDeque with an initial capacity
     * sufficient to hold 16 elements which is not
     * {@linkplain Indexable#isSynced() syncable}.
     */
    public IndexableArrayDeque() {
        this(false);
    }

    /**
     * Constructs an empty IndexableArrayDeque that can be
     * {@linkplain Indexable#isSynced() synced} if the {@code synced} parameter
     * is {@code true}.
     * 
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} IndexableArrayDeque
     */
    public IndexableArrayDeque(boolean synced) {
        lock = synced ? new ReentrantLock() : NoopLock.INSTANCE;
        this.synced = synced;
        elements = new Object[16 + 1];
    }

    /**
     * Constructs an empty IndexableArrayDeque with an initial capacity
     * sufficient to hold the specified number of elements which is not
     * {@linkplain Indexable#isSynced() syncable}.
     *
     * @param numElements
     *            lower bound on the initial capacity of the deque
     */
    public IndexableArrayDeque(int numElements) {
        this(numElements, true);
    }

    /**
     * Constructs an empty IndexableArrayDeque with an initial capacity
     * sufficient to hold the specified number of elements that can be
     * {@linkplain Indexable#isSynced() synced} if the {@code synced} parameter
     * is {@code true}.
     * 
     * @param numElements
     *            lower bound on the initial capacity of the deque
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} IndexableArrayDeque
     */
    public IndexableArrayDeque(int numElements, boolean synced) {
        lock = synced ? new ReentrantLock() : NoopLock.INSTANCE;
        this.synced = synced;
        elements = new Object[(numElements < 1) ? 1
                : (numElements == Integer.MAX_VALUE) ? Integer.MAX_VALUE : numElements + 1];
    }

    /**
     * Constructs a deque containing the elements of the specified collection,
     * in the order they are returned by the collection's iterator. (The first
     * element returned by the collection's iterator becomes the first element,
     * or <i>front</i> of the deque.) This IndexableArrayDeque is not
     * {@linkplain Indexable#isSynced() syncable}.
     *
     * @param c
     *            the collection whose elements are to be placed into the deque
     * @throws NullPointerException
     *             if the specified collection is null
     */
    public IndexableArrayDeque(Collection<? extends E> c) {
        this(c, true);
    }

    /**
     * Constructs a deque containing the elements of the specified collection,
     * in the order they are returned by the collection's iterator that can be
     * {@linkplain Indexable#isSynced() synced} if the {@code synced} parameter
     * is {@code true}.
     * 
     * @param c
     *            the collection whose elements are to be placed into the deque
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} IndexableArrayDeque
     */
    public IndexableArrayDeque(Collection<? extends E> c, boolean synced) {
        this(c.size(), synced);
        final Lock lock = this.lock;
        lock.lock(); // Lock only for visibility, not mutual exclusion
        try {
            copyElements(c);
        } finally {
            lock.unlock();
        }
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
     * Circularly increments i, mod modulus. Precondition and postcondition: 0
     * <= i < modulus.
     */
    static final int inc(int i, int modulus) {
        if (++i >= modulus) {
            i = 0;
        }
        return i;
    }

    /**
     * Circularly decrements i, mod modulus. Precondition and postcondition: 0
     * <= i < modulus.
     */
    static final int dec(int i, int modulus) {
        if (--i < 0) {
            i = modulus - 1;
        }
        return i;
    }

    /**
     * Circularly adds the given distance to index i, mod modulus. Precondition:
     * 0 <= i < modulus, 0 <= distance <= modulus.
     * 
     * @return index 0 <= i < modulus
     */
    static final int inc(int i, int distance, int modulus) {
        if ((i += distance) - modulus >= 0) {
            i -= modulus;
        }
        return i;
    }

    /**
     * Subtracts j from i, mod modulus. Index i must be logically ahead of index
     * j. Precondition: 0 <= i < modulus, 0 <= j < modulus.
     * 
     * @return the "circular distance" from j to i; corner case i == j is
     *         disambiguated to "empty", returning 0.
     */
    static final int sub(int i, int j, int modulus) {
        if ((i -= j) < 0) {
            i += modulus;
        }
        return i;
    }

    /**
     * Returns element at array index i. This is a slight abuse of generics,
     * accepted by javac.
     */
    @SuppressWarnings("unchecked")
    static final <E> E elementAt(Object[] es, int i) {
        return (E) es[i];
    }

    /**
     * A version of elementAt that checks for null elements. This check doesn't
     * catch all possible comodifications, but does catch ones that corrupt
     * traversal.
     */
    static final <E> E nonNullElementAt(Object[] es, int i) {
        @SuppressWarnings("unchecked")
        E e = (E) es[i];
        if (e == null) {
            throw new ConcurrentModificationException();
        }
        return e;
    }

    // The main insertion and extraction methods are addFirst,
    // addLast, pollFirst, pollLast. The other methods are defined in
    // terms of these.

    /**
     * Inserts the specified element at the front of this deque.
     *
     * @param e
     *            the element to add
     * @throws NullPointerException
     *             if the specified element is null
     */
    public void addFirst(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        sync(() -> {
            final Object[] es = elements;
            es[head = dec(head, es.length)] = e;
            if (head == tail) {
                grow(1);
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>
     * This method is equivalent to {@link #add}.
     *
     * @param e
     *            the element to add
     * @throws NullPointerException
     *             if the specified element is null
     */
    public void addLast(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        sync(() -> {
            final Object[] es = elements;
            es[tail] = e;
            if (head == (tail = inc(tail, es.length))) {
                grow(1);
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * Adds all of the elements in the specified collection at the end of this
     * deque, as if by calling {@link #addLast} on each one, in the order that
     * they are returned by the collection's iterator.
     *
     * @param c
     *            the elements to be inserted into this deque
     * @return {@code true} if this deque changed as a result of the call
     * @throws NullPointerException
     *             if the specified collection or any of its elements are null
     */
    public boolean addAll(Collection<? extends E> c) {
        return sync(() -> {
            final int s, needed;
            if ((needed = (s = size()) + c.size() + 1 - elements.length) > 0) {
                grow(needed);
            }
            copyElements(c);
            // checkInvariants();
            return size() > s;
        });
    }

    private void copyElements(Collection<? extends E> c) {
        c.forEach(this::addLast);
    }

    /**
     * Inserts the specified element at the front of this deque.
     *
     * @param e
     *            the element to add
     * @return {@code true} (as specified by {@link Deque#offerFirst})
     * @throws NullPointerException
     *             if the specified element is null
     */
    public boolean offerFirst(E e) {
        return sync(() -> {
            addFirst(e);
            return true;
        });
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * @param e
     *            the element to add
     * @return {@code true} (as specified by {@link Deque#offerLast})
     * @throws NullPointerException
     *             if the specified element is null
     */
    public boolean offerLast(E e) {
        return sync(() -> {
            addLast(e);
            return true;
        });
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E removeFirst() {
        E e = sync(() -> pollFirst());
        if (e == null) {
            throw new NoSuchElementException();
        }
        // checkInvariants();
        return e;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E removeLast() {
        E e = sync(() -> pollLast());
        if (e == null) {
            throw new NoSuchElementException();
        }
        // checkInvariants();
        return e;
    }

    /**
     * {@inheritDoc}
     */
    public E pollFirst() {
        return sync(() -> {
            final Object[] es;
            final int h;
            E e = elementAt(es = elements, h = head);
            if (e != null) {
                es[h] = null;
                head = inc(h, es.length);
            }
            // checkInvariants();
            return e;
        });
    }

    /**
     * {@inheritDoc}
     */
    public E pollLast() {
        return sync(() -> {
            final Object[] es;
            final int t;
            E e = elementAt(es = elements, t = dec(tail, es.length));
            if (e != null) {
                es[tail = t] = null;
            }
            // checkInvariants();
            return e;
        });
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E getFirst() {
        E e = sync(() -> elementAt(elements, head));
        if (e == null) {
            throw new NoSuchElementException();
        }
        // checkInvariants();
        return e;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E getLast() {
        return sync(() -> {
            final Object[] es = elements;
            E e = elementAt(es, dec(tail, es.length));
            if (e == null) {
                throw new NoSuchElementException();
            }
            // checkInvariants();
            return e;
        });
    }

    /**
     * {@inheritDoc}
     */
    public E peekFirst() {
        // checkInvariants();
        return sync(() -> elementAt(elements, head));
    }

    /**
     * {@inheritDoc}
     */
    public E peekLast() {
        return sync(() -> {
            // checkInvariants();
            final Object[] es;
            return elementAt(es = elements, dec(tail, es.length));
        });
    }

    /**
     * Removes the first occurrence of the specified element in this deque (when
     * traversing the deque from head to tail). If the deque does not contain
     * the element, it is unchanged. More formally, removes the first element
     * {@code e} such that {@code o.equals(e)} (if such an element exists).
     * Returns {@code true} if this deque contained the specified element (or
     * equivalently, if this deque changed as a result of the call).
     *
     * @param o
     *            element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeFirstOccurrence(Object o) {
        return sync(() -> {
            if (o != null) {
                final Object[] es = elements;
                for (int i = head, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                    for (; i < to; i++)
                        if (o.equals(es[i])) {
                            delete(i);
                            return true;
                        }
                    if (to == end)
                        break;
                }
            }
            return false;
        });
    }

    /**
     * Removes the last occurrence of the specified element in this deque (when
     * traversing the deque from head to tail). If the deque does not contain
     * the element, it is unchanged. More formally, removes the last element
     * {@code e} such that {@code o.equals(e)} (if such an element exists).
     * Returns {@code true} if this deque contained the specified element (or
     * equivalently, if this deque changed as a result of the call).
     *
     * @param o
     *            element to be removed from this deque, if present
     * @return {@code true} if the deque contained the specified element
     */
    public boolean removeLastOccurrence(Object o) {
        return sync(() -> {
            if (o != null) {
                final Object[] es = elements;
                for (int i = tail, end = head, to = (i >= end) ? end : 0;; i = es.length, to = end) {
                    for (i--; i > to - 1; i--)
                        if (o.equals(es[i])) {
                            delete(i);
                            return true;
                        }
                    if (to == end)
                        break;
                }
            }
            return false;
        });
    }

    // *** Queue methods ***

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>
     * This method is equivalent to {@link #addLast}.
     *
     * @param e
     *            the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException
     *             if the specified element is null
     */
    public boolean add(E e) {
        return sync(() -> {
            addLast(e);
            return true;
        });
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * <p>
     * This method is equivalent to {@link #offerLast}.
     *
     * @param e
     *            the element to add
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws NullPointerException
     *             if the specified element is null
     */
    public boolean offer(E e) {
        return sync(() -> offerLast(e));
    }

    /**
     * Retrieves and removes the head of the queue represented by this deque.
     *
     * This method differs from {@link #poll() poll()} only in that it throws an
     * exception if this deque is empty.
     *
     * <p>
     * This method is equivalent to {@link #removeFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E remove() {
        return sync(() -> removeFirst());
    }

    /**
     * Retrieves and removes the head of the queue represented by this deque (in
     * other words, the first element of this deque), or returns {@code null} if
     * this deque is empty.
     *
     * <p>
     * This method is equivalent to {@link #pollFirst}.
     *
     * @return the head of the queue represented by this deque, or {@code null}
     *         if this deque is empty
     */
    public E poll() {
        return sync(() -> pollFirst());
    }

    /**
     * Retrieves, but does not remove, the head of the queue represented by this
     * deque. This method differs from {@link #peek peek} only in that it throws
     * an exception if this deque is empty.
     *
     * <p>
     * This method is equivalent to {@link #getFirst}.
     *
     * @return the head of the queue represented by this deque
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E element() {
        return sync(() -> getFirst());
    }

    /**
     * Retrieves, but does not remove, the head of the queue represented by this
     * deque, or returns {@code null} if this deque is empty.
     *
     * <p>
     * This method is equivalent to {@link #peekFirst}.
     *
     * @return the head of the queue represented by this deque, or {@code null}
     *         if this deque is empty
     */
    public E peek() {
        return sync(() -> peekFirst());
    }

    // *** Stack methods ***

    /**
     * Pushes an element onto the stack represented by this deque. In other
     * words, inserts the element at the front of this deque.
     *
     * <p>
     * This method is equivalent to {@link #addFirst}.
     *
     * @param e
     *            the element to push
     * @throws NullPointerException
     *             if the specified element is null
     */
    public void push(E e) {
        sync(() -> {
            addFirst(e);
            return null;
        });
    }

    /**
     * Pops an element from the stack represented by this deque. In other words,
     * removes and returns the first element of this deque.
     *
     * <p>
     * This method is equivalent to {@link #removeFirst()}.
     *
     * @return the element at the front of this deque (which is the top of the
     *         stack represented by this deque)
     * @throws NoSuchElementException
     *             {@inheritDoc}
     */
    public E pop() {
        return sync(() -> removeFirst());
    }

    /**
     * Removes the element at the specified position in the elements array. This
     * can result in forward or backwards motion of array elements. We optimize
     * for least element motion.
     *
     * <p>
     * This method is called delete rather than remove to emphasize that its
     * semantics differ from those of {@link List#remove(int)}.
     *
     * @return true if elements near tail moved backwards
     */
    boolean delete(int i) {
        // checkInvariants();
        final Object[] es = elements;
        final int capacity = es.length;
        final int h, t;
        // number of elements before to-be-deleted elt
        final int front = sub(i, h = head, capacity);
        // number of elements after to-be-deleted elt
        final int back = sub(t = tail, i, capacity) - 1;
        if (front < back) {
            // move front elements forwards
            if (h <= i) {
                System.arraycopy(es, h, es, h + 1, front);
            } else { // Wrap around
                System.arraycopy(es, 0, es, 1, i);
                es[0] = es[capacity - 1];
                System.arraycopy(es, h, es, h + 1, front - (i + 1));
            }
            es[h] = null;
            head = inc(h, capacity);
            // checkInvariants();
            return false;
        } else {
            // move back elements backwards
            tail = dec(t, capacity);
            if (i <= tail) {
                System.arraycopy(es, i + 1, es, i, back);
            } else { // Wrap around
                System.arraycopy(es, i + 1, es, i, capacity - (i + 1));
                es[capacity - 1] = es[0];
                System.arraycopy(es, 1, es, 0, t - 1);
            }
            es[tail] = null;
            // checkInvariants();
            return true;
        }
    }

    /**
     * Inserts the element at the specified position in the elements array. May
     * either shift the element at the specified index and all after forward, of
     * shift all elements before the index backwards and insert the element
     * before the index, or resize the elements array.
     *
     * @return 1 if elements moved forward (and tail was incremented), -1 if
     *         elements moved backward, 0 if the elements array was resized
     */
    int insert(int i, E e) {
        Object[] es = elements;
        final int capacity = es.length;
        int h = head;
        final int t = tail;
        final int front = sub(i, h, capacity);
        final int back = sub(t, i, capacity);

        if (front + back + 1 == capacity) {
            Object[] a = elements = new Object[newCapacity(1, jump())];
            if (h <= t) {
                System.arraycopy(es, h, a, 0, front);
                a[front] = e;
                System.arraycopy(es, h + front, a, front + 1, back);
            } else {
                if (h <= i) {
                    System.arraycopy(es, h, a, 0, front);
                    a[front] = e;
                    System.arraycopy(es, i, a, front + 1, capacity - i);
                    System.arraycopy(es, 0, a, front + 1 + capacity - i, t);
                } else {
                    System.arraycopy(es, h, a, 0, capacity - h);
                    System.arraycopy(es, 0, a, capacity - h, i);
                    a[front] = e;
                    System.arraycopy(es, i, a, front + 1, back);
                }
            }
            head = 0;
            tail = capacity;
            return 0;
        }
        // Optimize for least element motion
        if (front < back) {
            i = dec(i, capacity);
            h = dec(h, capacity);
            if (h <= i) {
                System.arraycopy(es, h + 1, es, h, front);
            } else { // Wrap around
                System.arraycopy(es, h + 1, es, h, capacity - 1 - h);
                es[capacity - 1] = es[0];
                System.arraycopy(es, 1, es, 0, i);
            }
            es[i] = e;
            head = h;
            return -1;
        } else {
            if (i <= t) {
                System.arraycopy(es, i, es, i + 1, back);
            } else { // Wrap around
                System.arraycopy(es, 0, es, 1, t);
                es[0] = es[capacity - 1];
                System.arraycopy(es, i, es, i + 1, capacity - 1 - i);
            }
            es[i] = e;
            tail = inc(t, capacity);
            return 1;
        }
    }

    /**
     * Inserts length elements from a starting at offset (inclusive) into the
     * elements array at index + head. May shift elements forwards or backwards
     * or resize the elements array.
     */
    void insert(int index, Object[] a, int offset, int length) {
        int size = size();
        int newSize = size + length;
        if (newSize + 1 < 0) {
            throw new IllegalStateException("Sorry, deque too big");
        }
        Object[] es = elements;
        if (newSize >= es.length) {
            Object[] n = elements = new Object[newCapacity(newSize + 1 - es.length, jump())];
            if (tail >= head) {
                System.arraycopy(es, head, n, 0, index);
                System.arraycopy(a, offset, n, index, length);
                System.arraycopy(es, head + index, n, index + length, size - index);
            } else {
                int r = es.length - head;
                if (r < index) {
                    System.arraycopy(es, head, n, 0, r);
                    System.arraycopy(es, 0, n, r, index - r);
                    System.arraycopy(a, offset, n, index, length);
                    System.arraycopy(es, index - r, n, index + length, size - index);
                } else {
                    System.arraycopy(es, head, n, 0, index);
                    System.arraycopy(a, offset, n, index, length);
                    System.arraycopy(es, head + index, n, index + length, r - index);
                    System.arraycopy(es, 0, n, r + length, tail);
                }
            }
            head = 0;
            tail = newSize;
            return;
        }
        int back = size - index;
        int capacity = es.length;
        if (index <= back) {
            int h = head - length;
            if (h < 0) {
                int nh = h + capacity;
                if (index >= -h) {
                    System.arraycopy(es, head, es, nh, -h);
                    System.arraycopy(es, head - h, es, 0, index + h);
                    System.arraycopy(a, offset, es, h + index, length);
                } else {
                    System.arraycopy(es, head, es, nh, index);
                    int f = -h - index;
                    System.arraycopy(a, offset, es, nh + index, f);
                    System.arraycopy(a, offset + f, es, 0, length - f);
                }
                h = nh;
            } else {
                int i = head + index - es.length;
                if (i <= 0) {
                    System.arraycopy(es, head, es, h, index);
                    System.arraycopy(a, offset, es, h + index, length);
                } else {
                    int r = index - i;
                    System.arraycopy(es, head, es, h, r);
                    int ni = i - length;
                    if (ni >= 0) {
                        System.arraycopy(es, 0, es, h + r, length);
                        System.arraycopy(es, length, es, 0, ni);
                        System.arraycopy(a, offset, es, ni, length);
                    } else {
                        System.arraycopy(es, 0, es, h + r, i);
                        System.arraycopy(a, offset, es, h + index, -ni);
                        System.arraycopy(a, offset - ni, es, 0, length + ni);
                    }
                }
            }
            head = h;
        } else {
            int t = tail + length;
            if (t - capacity >= 0) {
                t -= capacity;
                if (t >= back) {
                    int f = t - back;
                    System.arraycopy(es, tail - back, es, f, back);
                    System.arraycopy(a, offset, es, tail - back, length - f);
                    System.arraycopy(a, offset + length - f, es, 0, f);
                } else {
                    System.arraycopy(es, tail - t, es, 0, t);
                    System.arraycopy(es, tail - back, es, es.length - back + t, back - t);
                    System.arraycopy(a, offset, es, tail - back, length);
                }
            } else {
                int i = tail - back;
                if (i >= 0) {
                    System.arraycopy(es, i, es, i + length, back);
                    System.arraycopy(a, offset, es, i, length);
                } else {
                    System.arraycopy(es, 0, es, t - tail, tail);
                    int ni = i + length;
                    int mi = i + capacity;
                    if (ni >= 0) {
                        System.arraycopy(es, mi, es, ni, -i);
                        System.arraycopy(a, offset, es, mi, -i);
                        System.arraycopy(a, offset - i, es, 0, ni);
                    } else {
                        System.arraycopy(es, mi - ni, es, 0, -i + ni);
                        System.arraycopy(es, mi, es, ni + capacity, -ni);
                        System.arraycopy(a, offset, es, mi, length);
                    }
                }
            }
            tail = t;
        }
    }

    private static void checkArray(Object[] a) {
        for (int i = 0; i < a.length; ++i) {
            if (a[i] == null) {
                throw new NullPointerException(Integer.toString(i));
            }
        }
    }

    // *** Collection Methods ***

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements in this deque
     */
    public int size() {
        return sync(() -> sub(tail, head, elements.length));
    }

    /**
     * Returns {@code true} if this deque contains no elements.
     *
     * @return {@code true} if this deque contains no elements
     */
    public boolean isEmpty() {
        return sync(() -> head == tail);
    }

    /**
     * Returns an iterator over the elements in this deque. The elements will be
     * ordered from first (head) to last (tail). This is the same order that
     * elements would be dequeued (via successive calls to {@link #remove} or
     * popped (via successive calls to {@link #pop}).
     *
     * @return an iterator over the elements in this deque
     */
    public Iterator<E> iterator() {
        // Must be manually synched by user
        return new DeqIterator();
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<E> descendingIterator() {
        // Must be manually synched by user
        return new DescendingIterator();
    }

    private class DeqIterator implements Iterator<E> {
        /** Index of element to be returned by subsequent call to next. */
        int cursor;

        /** Number of elements yet to be returned. */
        int remaining = size();

        /**
         * Index of element returned by most recent call to next. Reset to -1 if
         * element is deleted by a call to remove.
         */
        int lastRet = -1;

        DeqIterator() {
            cursor = head;
        }

        public final boolean hasNext() {
            return remaining > 0;
        }

        public E next() {
            if (remaining <= 0) {
                throw new NoSuchElementException();
            }
            final Object[] es = elements;
            E e = nonNullElementAt(es, cursor);
            cursor = inc(lastRet = cursor, es.length);
            remaining--;
            return e;
        }

        void postDelete(boolean leftShifted) {
            if (leftShifted) {
                cursor = dec(cursor, elements.length);
            }
        }

        public final void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            postDelete(delete(lastRet));
            lastRet = -1;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            int r;
            if ((r = remaining) <= 0) {
                return;
            }
            remaining = 0;
            final Object[] es = elements;
            if (es[cursor] == null || sub(tail, cursor, es.length) != r) {
                throw new ConcurrentModificationException();
            }
            for (int i = cursor, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                for (; i < to; i++) {
                    action.accept(elementAt(es, i));
                }
                if (to == end) {
                    if (end != tail) {
                        throw new ConcurrentModificationException();
                    }
                    lastRet = dec(end, es.length);
                    break;
                }
            }
        }
    }

    private final class DeqListIterator implements ListIterator<E> {
        /**
         * Index of element to be returned by subsequent call to next.
         */
        int cursor;
        /**
         * Tail and head recorded at construction (also in remove), to stop
         * iterator and also to check for comodification.
         */
        int end = tail, start = head;
        /**
         * Index of element returned by most recent call to next. Reset to -1 if
         * element is deleted by a call to remove.
         */
        int lastRet = -1;

        DeqListIterator(int index) {
            cursor = index;
        }

        public boolean hasNext() {
            return cursor != end;
        }

        public boolean hasPrevious() {
            return cursor != start;
        }

        public int nextIndex() {
            return sub(cursor, start, elements.length);
        }

        public int previousIndex() {
            return nextIndex() - 1;
        }

        private final void checkMod() {
            if (tail != end || head != start)
                throw new ConcurrentModificationException();
        }

        public E next() {
            int c = cursor;
            if (c == end)
                throw new NoSuchElementException();
            checkMod();
            Object[] a = elements;
            cursor = inc(c, a.length);
            return elementAt(a, lastRet = c);
        }

        public E previous() {
            int c = cursor;
            if (c == start)
                throw new NoSuchElementException();
            checkMod();
            Object[] a = elements;
            return elementAt(a, lastRet = cursor = dec(c, a.length));
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            checkMod();
            int i = cursor, to = end;
            if (i == to)
                return;
            cursor = to;
            Object[] a = elements;
            for (int e = (i <= to) ? to : a.length;; i = 0, e = to) {
                for (; i < e; i++) {
                    action.accept(elementAt(a, i));
                }
                if (e == to)
                    break;
            }
            lastRet = dec(to, a.length);
        }

        public void remove() {
            int l = lastRet;
            if (l < 0)
                throw new IllegalStateException();
            checkMod();
            int c = cursor;
            boolean prev = l == c;
            if (delete(l)) {
                if (!prev)
                    cursor = dec(c, elements.length);
                end = tail;
            } else {
                if (prev)
                    cursor = inc(c, elements.length);
                start = head;
            }
            lastRet = -1;
        }

        public void set(E e) {
            int l = lastRet;
            if (l < 0)
                throw new IllegalStateException();
            checkMod();
            if (e == null) {
                throw new NullPointerException();
            }
            elements[l] = e;
        }

        public void add(E e) {
            checkMod();
            if (e == null) {
                throw new NullPointerException();
            }
            int c = cursor, l = elements.length;
            switch (insert(c, e)) {
            case 0:
                cursor = sub(c, start, l) + 1;
                start = 0;
                end = tail;
                break;
            case 1:
                cursor = inc(c, l);
                end = tail;
                break;
            case -1:
                start = head;
                break;
            }
            lastRet = -1;
        }
    }

    private final class DescendingIterator extends DeqIterator {
        DescendingIterator() {
            cursor = dec(tail, elements.length);
        }

        public final E next() {
            if (remaining <= 0) {
                throw new NoSuchElementException();
            }
            final Object[] es = elements;
            E e = nonNullElementAt(es, cursor);
            cursor = dec(lastRet = cursor, es.length);
            remaining--;
            return e;
        }

        void postDelete(boolean leftShifted) {
            if (!leftShifted) {
                cursor = inc(cursor, elements.length);
            }
        }

        public final void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            int r;
            if ((r = remaining) <= 0) {
                return;
            }
            remaining = 0;
            final Object[] es = elements;
            if (es[cursor] == null || sub(cursor, head, es.length) + 1 != r) {
                throw new ConcurrentModificationException();
            }
            for (int i = cursor, end = head, to = (i >= end) ? end : 0;; i = es.length - 1, to = end) {
                // hotspot generates faster code than for: i >= to !
                for (; i > to - 1; i--) {
                    action.accept(elementAt(es, i));
                }
                if (to == end) {
                    if (end != head) {
                        throw new ConcurrentModificationException();
                    }
                    lastRet = end;
                    break;
                }
            }
        }
    }

    /**
     * Creates a <em>late-binding</em> and <em>fail-fast</em>
     * {@link Spliterator} over the elements in this deque.
     *
     * <p>
     * The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, {@link Spliterator#ORDERED}, and
     * {@link Spliterator#NONNULL}. Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this deque
     */
    public Spliterator<E> spliterator() {
        // Must be manually synced by the caller!
        return new DeqSpliterator();
    }

    final class DeqSpliterator implements Spliterator<E> {
        private int fence; // -1 until first use
        private int cursor; // current index, modified on traverse/split

        /** Constructs late-binding spliterator over all elements. */
        DeqSpliterator() {
            this.fence = -1;
        }

        /** Constructs spliterator over the given range. */
        DeqSpliterator(int origin, int fence) {
            // assert 0 <= origin && origin < elements.length;
            // assert 0 <= fence && fence < elements.length;
            this.cursor = origin;
            this.fence = fence;
        }

        /** Ensures late-binding initialization; then returns fence. */
        private int getFence() { // force initialization
            int t;
            if ((t = fence) < 0) {
                t = fence = tail;
                cursor = head;
            }
            return t;
        }

        public DeqSpliterator trySplit() {
            final Object[] es = elements;
            final int i, n;
            return ((n = sub(getFence(), i = cursor, es.length) >> 1) <= 0) ? null
                    : new DeqSpliterator(i, cursor = inc(i, n, es.length));
        }

        public void forEachRemaining(Consumer<? super E> action) {
            if (action == null) {
                throw new NullPointerException();
            }
            final int end = getFence(), cursor = this.cursor;
            final Object[] es = elements;
            if (cursor != end) {
                this.cursor = end;
                // null check at both ends of range is sufficient
                if (es[cursor] == null || es[dec(end, es.length)] == null) {
                    throw new ConcurrentModificationException();
                }
                for (int i = cursor, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                    for (; i < to; i++) {
                        action.accept(elementAt(es, i));
                    }
                    if (to == end)
                        break;
                }
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final Object[] es = elements;
            if (fence < 0) {
                fence = tail;
                cursor = head;
            } // late-binding
            final int i;
            if ((i = cursor) == fence) {
                return false;
            }
            E e = nonNullElementAt(es, i);
            cursor = inc(i, es.length);
            action.accept(e);
            return true;
        }

        public long estimateSize() {
            return sub(getFence(), cursor, elements.length);
        }

        public int characteristics() {
            return Spliterator.NONNULL | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException
     *             {@inheritDoc}
     */
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        sync(() -> {
            final Object[] es = elements;
            for (int i = head, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                for (; i < to; i++) {
                    action.accept(elementAt(es, i));
                }
                if (to == end) {
                    if (end != tail)
                        throw new ConcurrentModificationException();
                    break;
                }
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * Replaces each element of this deque with the result of applying the
     * operator to that element, as specified by {@link List#replaceAll}.
     *
     * @param operator
     *            the operator to apply to each element
     */
    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        sync(() -> {
            final Object[] es = elements;
            for (int i = head, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                for (; i < to; i++) {
                    es[i] = operator.apply(elementAt(es, i));
                }
                if (to == end) {
                    if (end != tail)
                        throw new ConcurrentModificationException();
                    break;
                }
            }
            // checkInvariants();
            return null;
        });
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException
     *             {@inheritDoc}
     */
    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return sync(() -> bulkRemove(filter));
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException
     *             {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return sync(() -> bulkRemove(e -> c.contains(e)));
    }

    /**
     * {@inheritDoc}
     * 
     * @throws NullPointerException
     *             {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return sync(() -> bulkRemove(e -> !c.contains(e)));
    }

    /** Implementation of bulk remove methods. */
    private boolean bulkRemove(Predicate<? super E> filter) {
        // checkInvariants();
        final Object[] es = elements;
        // Optimize for initial run of survivors
        for (int i = head, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
            for (; i < to; i++) {
                if (filter.test(elementAt(es, i))) {
                    return bulkRemoveModified(filter, i);
                }
            }
            if (to == end) {
                if (end != tail)
                    throw new ConcurrentModificationException();
                break;
            }
        }
        return false;
    }

    // A tiny bit set implementation

    private static long[] nBits(int n) {
        return new long[((n - 1) >> 6) + 1];
    }

    private static void setBit(long[] bits, int i) {
        bits[i >> 6] |= 1L << i;
    }

    private static boolean isClear(long[] bits, int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    /**
     * Helper for bulkRemove, in case of at least one deletion. Tolerate
     * predicates that reentrantly access the collection for read (but writers
     * still get CME), so traverse once to find elements to delete, a second
     * pass to physically expunge.
     *
     * @param beg
     *            valid index of first element to be deleted
     */
    private boolean bulkRemoveModified(Predicate<? super E> filter, final int beg) {
        final Object[] es = elements;
        final int capacity = es.length;
        final int end = tail;
        final long[] deathRow = nBits(sub(end, beg, capacity));
        deathRow[0] = 1L; // set bit 0
        for (int i = beg + 1, to = (i <= end) ? end : es.length, k = beg;; i = 0, to = end, k -= capacity) {
            for (; i < to; i++) {
                if (filter.test(elementAt(es, i))) {
                    setBit(deathRow, i - k);
                }
            }
            if (to == end)
                break;
        }
        // a two-finger traversal, with hare i reading, tortoise w writing
        int w = beg;
        for (int i = beg + 1, to = (i <= end) ? end : es.length, k = beg;; w = 0) { // w
                                                                                    // rejoins
                                                                                    // i
                                                                                    // on
                                                                                    // second
                                                                                    // leg
            // In this loop, i and w are on the same leg, with i > w
            for (; i < to; i++) {
                if (isClear(deathRow, i - k)) {
                    es[w++] = es[i];
                }
            }
            if (to == end)
                break;
            // In this loop, w is on the first leg, i on the second
            for (i = 0, to = end, k -= capacity; i < to && w < capacity; i++) {
                if (isClear(deathRow, i - k)) {
                    es[w++] = es[i];
                }
            }
            if (i >= to) {
                if (w == capacity)
                    w = 0; // "corner" case
                break;
            }
        }
        if (end != tail)
            throw new ConcurrentModificationException();
        circularClear(es, tail = w, end);
        // checkInvariants();
        return true;
    }

    /**
     * Returns {@code true} if this deque contains the specified element. More
     * formally, returns {@code true} if and only if this deque contains at
     * least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o
     *            object to be checked for containment in this deque
     * @return {@code true} if this deque contains the specified element
     */
    public boolean contains(Object o) {
        return sync(() -> {
            if (o != null) {
                final Object[] es = elements;
                for (int i = head, end = tail, to = (i <= end) ? end : es.length;; i = 0, to = end) {
                    for (; i < to; i++) {
                        if (o.equals(es[i])) {
                            return true;
                        }
                    }
                    if (to == end)
                        break;
                }
            }
            return false;
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * <b>Implementation Requirements:</b><br>
     * This implementation iterates over the specified collection, checking each
     * element returned by the iterator in turn to see if it's contained in this
     * collection. If all elements are so contained {@code true} is returned,
     * otherwise {@code false}.
     *
     * @throws ClassCastException
     *             {@inheritDoc}
     * @throws NullPointerException
     *             {@inheritDoc}
     * @see #contains(Object)
     */
    public boolean containsAll(Collection<?> c) {
        return sync(() -> {
            for (Object e : c) {
                if (!contains(e)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Removes a single instance of the specified element from this deque. If
     * the deque does not contain the element, it is unchanged. More formally,
     * removes the first element {@code e} such that {@code o.equals(e)} (if
     * such an element exists). Returns {@code true} if this deque contained the
     * specified element (or equivalently, if this deque changed as a result of
     * the call).
     *
     * <p>
     * This method is equivalent to {@link #removeFirstOccurrence(Object)}.
     *
     * @param o
     *            element to be removed from this deque, if present
     * @return {@code true} if this deque contained the specified element
     */
    public boolean remove(Object o) {
        return sync(() -> removeFirstOccurrence(o));
    }

    /**
     * Removes all of the elements from this deque. The deque will be empty
     * after this call returns.
     */
    public void clear() {
        sync(() -> {
            circularClear(elements, head, tail);
            head = tail = 0;
            // checkInvariants();
            return null;
        });
    }

    /**
     * Nulls out slots starting at array index i, upto index end. Condition i ==
     * end means "empty" - nothing to do.
     */
    private static void circularClear(Object[] es, int i, int end) {
        // assert 0 <= i && i < es.length;
        // assert 0 <= end && end < es.length;
        for (int to = (i <= end) ? end : es.length;; i = 0, to = end) {
            for (; i < to; i++)
                es[i] = null;
            if (to == end)
                break;
        }
    }

    /**
     * Returns an array containing all of the elements in this deque in proper
     * sequence (from first to last element).
     *
     * <p>
     * The returned array will be "safe" in that no references to it are
     * maintained by this deque. (In other words, this method must allocate a
     * new array). The caller is thus free to modify the returned array.
     *
     * <p>
     * This method acts as bridge between array-based and collection-based APIs.
     *
     * @return an array containing all of the elements in this deque
     */
    public Object[] toArray() {
        return sync(() -> toArray(Object[].class));
    }

    private <T> T[] toArray(Class<T[]> klazz) {
        final Object[] es = elements;
        final T[] a;
        final int head = this.head, tail = this.tail, end;
        if ((end = tail + ((head <= tail) ? 0 : es.length)) >= 0) {
            // Uses null extension feature of copyOfRange
            a = Arrays.copyOfRange(es, head, end, klazz);
        } else {
            // integer overflow!
            a = Arrays.copyOfRange(es, 0, end - head, klazz);
            System.arraycopy(es, head, a, 0, es.length - head);
        }
        if (end != tail) {
            System.arraycopy(es, 0, a, es.length - head, tail);
        }
        return a;
    }

    /**
     * Returns an array containing all of the elements in this deque in proper
     * sequence (from first to last element); the runtime type of the returned
     * array is that of the specified array. If the deque fits in the specified
     * array, it is returned therein. Otherwise, a new array is allocated with
     * the runtime type of the specified array and the size of this deque.
     *
     * <p>
     * If this deque fits in the specified array with room to spare (i.e., the
     * array has more elements than this deque), the element in the array
     * immediately following the end of the deque is set to {@code null}.
     *
     * <p>
     * Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs. Further, this method allows
     * precise control over the runtime type of the output array, and may, under
     * certain circumstances, be used to save allocation costs.
     *
     * <p>
     * Suppose {@code x} is a deque known to contain only strings. The following
     * code can be used to dump the deque into a newly allocated array of
     * {@code String}:
     *
     * <pre>
     * {@code
     *  String[] y = x.toArray(new String[0]);
     * }
     * </pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a
     *            the array into which the elements of the deque are to be
     *            stored, if it is big enough; otherwise, a new array of the
     *            same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this deque
     * @throws ArrayStoreException
     *             if the runtime type of the specified array is not a supertype
     *             of the runtime type of every element in this deque
     * @throws NullPointerException
     *             if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        return sync(() -> {
            final int size;
            if ((size = size()) > a.length) {
                return toArray((Class<T[]>) a.getClass());
            }
            final Object[] es = elements;
            for (int i = head, j = 0, len = Math.min(size, es.length - i);; i = 0, len = tail) {
                System.arraycopy(es, i, a, j, len);
                if ((j += len) == size)
                    break;
            }
            if (size < a.length) {
                a[size] = null;
            }
            return a;
        });
    }

    // *** Indexable Methods ***

    /**
     * Inserts all of the elements in the specified collection into this
     * IndexableArrayDeque at the specified position. Shifts the element
     * currently at that position (if any) and any subsequent elements to the
     * right (increases their indices). The new elements will appear in this
     * list in the order that they are returned by the specified collection's
     * iterator. The behavior of this operation is undefined if the specified
     * collection is modified while the operation is in progress. (Note that
     * this will occur if the specified collection is this IndexableArrayDeque,
     * and it's nonempty.)
     *
     * @param index
     *            index at which to insert the first element from the specified
     *            collection
     * @param c
     *            collection containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws NullPointerException
     *             if the specified collection contains one or more null
     *             elements or if the specified collection is null
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index > size()})
     */
    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        if (index < 0)
            throw new IndexOutOfBoundsException(Integer.toString(index));
        Object[] a = c.toArray();
        if (a.length == 0)
            return false;
        checkArray(a);
        return sync(() -> {
            if (index > size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            insert(index, a, 0, a.length);
            return true;
        });
    }

    /**
     * Sorts this IndexableArrayDeque according to the order induced by the
     * specified {@link Comparator}. The sort is <i>stable</i>: this method must
     * not reorder equal elements.
     *
     * <p>
     * All elements in this IndexableArrayDeque must be <i>mutually
     * comparable</i> using the specified comparator (that is,
     * {@code c.compare(e1, e2)} must not throw a {@code ClassCastException} for
     * any elements {@code e1} and {@code e2} in the IndexableArrayDeque).
     *
     * <p>
     * If the specified comparator is {@code null} then all elements in this
     * IndexableArrayDeque must implement the {@link Comparable} interface and
     * the elements' {@linkplain Comparable natural ordering} should be used.
     *
     * <p>
     * <b>Implementation Note:</b> This implementation is a stable, adaptive,
     * iterative mergesort that requires far fewer than n lg(n) comparisons when
     * the input array is partially sorted, while offering the performance of a
     * traditional mergesort when the input array is randomly ordered. If the
     * input array is nearly sorted, the implementation requires approximately n
     * comparisons. Temporary storage requirements vary from a small constant
     * for nearly sorted input arrays to n/2 object references for randomly
     * ordered input arrays.
     *
     * <p>
     * The implementation takes equal advantage of ascending and descending
     * order in its input array, and can take advantage of ascending and
     * descending order in different parts of the same input array. It is
     * well-suited to merging two or more sorted arrays: simply concatenate the
     * arrays and sort the resulting array.
     *
     * <p>
     * The implementation was adapted from Tim Peters's list sort for Python
     * (<a href=
     * "http://svn.python.org/projects/python/trunk/Objects/listsort.txt">
     * TimSort</a>). It uses techniques from Peter McIlroy's "Optimistic Sorting
     * and Information Theoretic Complexity", in Proceedings of the Fourth
     * Annual ACM-SIAM Symposium on Discrete Algorithms, pp 467-474, January
     * 1993.
     *
     * @param c
     *            the {@code Comparator} used to compare deque elements. A
     *            {@code null} value indicates that the elements'
     *            {@linkplain Comparable natural ordering} should be used
     * @throws ClassCastException
     *             if the list contains elements that are not <i>mutually
     *             comparable</i> using the specified comparator
     * @throws IllegalArgumentException
     *             if the comparator is found to violate the {@link Comparator}
     *             contract
     */
    @Override
    public void sort(Comparator<? super E> c) {
        sync(() -> {
            sort(c, head, tail);
            return null;
        });
    }

    /**
     * Returns the element at the specified position in this
     * IndexableArrayDeque.
     *
     * @param index
     *            index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index >= size()})
     */
    @Override
    public E get(int index) {
        return sync(() -> {
            if (index < 0 || index >= size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            final Object[] es = elements;
            return elementAt(es, inc(head, index, es.length));
        });
    }

    /**
     * Replaces the element at the specified position in this
     * IndexableArrayDeque with the specified element.
     *
     * @param index
     *            index of the element to replace
     * @param element
     *            element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws NullPointerException
     *             if the specified element is null
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index >= size()})
     */
    @Override
    public E set(int index, E element) {
        if (element == null) {
            throw new NullPointerException();
        }
        return sync(() -> {
            if (index < 0 || index >= size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            final Object[] es = elements;
            int idx = inc(head, index, es.length);
            E old = elementAt(es, idx);
            es[idx] = element;
            return old;
        });
    }

    /**
     * Inserts the specified element at the specified position in this
     * IndexableArrayDeque. Shifts the element currently at that position (if
     * any) and any subsequent elements to the right (adds one to their
     * indices).
     *
     * @param index
     *            index at which the specified element is to be inserted
     * @param element
     *            element to be inserted
     * @throws NullPointerException
     *             if the specified element is null
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index > size()})
     */
    @Override
    public void add(int index, E element) {
        if (element == null) {
            throw new NullPointerException();
        }
        sync(() -> {
            if (index < 0 || index > size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            insert(inc(head, index, elements.length), element);
            return null;
        });
    }

    /**
     * Removes the element at the specified position in this
     * IndexableArrayDeque. Shifts any subsequent elements to the left
     * (subtracts one from their indices). Returns the element that was removed
     * from the IndexableArrayDeque.
     *
     * @param index
     *            the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index >= size()})
     */
    @Override
    public E removeAt(int index) {
        return sync(() -> {
            if (index < 0 || index >= size())
                throw new IndexOutOfBoundsException(Integer.toString(index));
            final Object[] es = elements;
            int idx = inc(head, index, es.length);
            E old = elementAt(es, idx);
            delete(idx);
            return old;
        });
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
            return removeAt(ThreadLocalRandom.current().nextInt(size()));
        });
    }

    /**
     * Returns the index of the first occurrence of the specified element in
     * this IndexableArrayDeque, or -1 if this IndexableArrayDeque does not
     * contain the element. More formally, returns the lowest index {@code i}
     * such that {@code Objects.equals(o, get(i))}, or -1 if there is no such
     * index.
     *
     * @param o
     *            element to search for
     * @return the index of the first occurrence of the specified element in
     *         this list, or -1 if this list does not contain the element
     */
    @Override
    public int indexOf(Object o) {
        return sync(() -> {
            int i = index(o, head, tail);
            return i == -1 ? -1 : sub(i, head, elements.length);
        });
    }

    /**
     * Returns the index of the last occurrence of the specified element in this
     * IndexableArrayDeque, or -1 if this IndexableArrayDeque does not contain
     * the element. More formally, returns the highest index {@code i} such that
     * {@code Objects.equals(o, get(i))}, or -1 if there is no such index.
     *
     * @param o
     *            element to search for
     * @return the index of the last occurrence of the specified element in this
     *         list, or -1 if this list does not contain the element
     */
    @Override
    public int lastIndexOf(Object o) {
        return sync(() -> {
            int i = lastIndex(o, head, tail);
            return i == -1 ? -1 : sub(i, head, elements.length);
        });
    }

    /**
     * Returns a list iterator over the elements in this IndexableArrayDeque (in
     * proper sequence).
     *
     * @return a list iterator over the elements in this IndexableArrayDeque (in
     *         proper sequence)
     */
    @Override
    public ListIterator<E> listIterator() {
        // Must be manually synched by user
        return new DeqListIterator(head);
    }

    /**
     * Returns a list iterator over the elements in this IndexableArrayDeque (in
     * proper sequence), starting at the specified position in the
     * IndexableArrayDeque. The specified index indicates the first element that
     * would be returned by an initial call to {@link ListIterator#next next}.
     * An initial call to {@link ListIterator#previous previous} would return
     * the element with the specified index minus one.
     *
     * @param index
     *            index of the first element to be returned from the list
     *            iterator (by a call to {@link ListIterator#next next})
     * @return a list iterator over the elements in this IndexableArrayDeque (in
     *         proper sequence), starting at the specified position in the
     *         IndexableArrayDeque
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index > size()})
     */
    @Override
    public ListIterator<E> listIterator(int index) {
        // Must be manually synched by user
        if (index < 0 || index > size())
            throw new IndexOutOfBoundsException(Integer.toString(index));
        return new DeqListIterator(inc(head, index, elements.length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<E> toList() {
        return sync(() -> new ArrayList<>(this));
    }

    int index(Object o, int from, int to) {
        if (o != null) {
            final Object[] es = elements;
            for (int i = from, e = (i <= to) ? to : es.length; /**/; i = 0, e = to) {
                for (/**/; i < e; i++) {
                    if (o.equals(es[i]))
                        return i;
                }
                if (e == to)
                    break;
            }
        }
        return -1;
    }

    int lastIndex(Object o, int to, int from) {
        if (o != null) {
            final Object[] es = elements;
            for (int i = from, e = (i >= to) ? to : 0; /**/; i = es.length, e = to) {
                for (/**/; i > e;) {
                    if (o.equals(es[--i]))
                        return i;
                }
                if (e == to)
                    break;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    void sort(Comparator<? super E> c, int from, int to) {
        E[] a = (E[]) elements;
        if (from <= to) {
            Arrays.sort(a, from, to, c);
            return;
        }
        if (to == 0) {
            Arrays.sort(a, from, a.length, c);
            return;
        }
        int end = a.length - from;
        int size = end + to;
        int t = tail;
        if (t + (long) size <= head) {
            System.arraycopy(a, from, a, t, end);
            System.arraycopy(a, 0, a, t + end, to);
            try {
                Arrays.sort(a, t, t + size, c);
            } catch (RuntimeException | Error e) {
                Arrays.fill(a, t, t + size, null);
                throw e;
            }
            System.arraycopy(a, t, a, from, end);
            System.arraycopy(a, t + end, a, 0, to);
            Arrays.fill(a, t, t + size, null);
            return;
        }
        E[] e = (E[]) new Object[size];
        System.arraycopy(a, from, e, 0, end);
        System.arraycopy(a, 0, e, end, to);
        Arrays.sort(e, 0, size, c);
        System.arraycopy(e, 0, a, from, end);
        System.arraycopy(e, end, a, 0, to);
    }

    // *** Object Methods ***

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return sync(() -> hashCode(head, tail));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return obj == this || sync(() -> equals(obj, head, tail));
    }

    /**
     * Returns a string representation of this collection. The string
     * representation consists of a list of the collection's elements in the
     * order they are returned by its iterator, enclosed in square brackets
     * ({@code "[]"}). Adjacent elements are separated by the characters
     * {@code ", "} (comma and space). Elements are converted to strings as by
     * {@link String#valueOf(Object)}.
     *
     * @return a string representation of this collection
     */
    @Override
    public String toString() {
        return sync(() -> {
            if (isEmpty()) {
                return "[]";
            }

            StringBuilder sb = new StringBuilder();
            sb.append('[');

            final Object[] es = elements;
            int to = tail;
            for (int i = head, e = (i <= to) ? to : es.length; /**/; i = 0, e = to) {
                for (/**/; i < e; i++) {
                    Object o = es[i];
                    sb.append(o == this ? "(this Collection)" : o);
                    sb.append(',').append(' ');
                }
                if (e == to)
                    break;
            }
            return sb.append(']').toString();
        });
    }

    int hashCode(int from, int to) {
        int hashCode = 1;
        final Object[] es = elements;
        for (int i = from, e = (i <= to) ? to : es.length;; i = 0, e = to) {
            for (; i < e; i++) {
                Object o = es[i];
                hashCode = 31 * hashCode + (o == null ? 0 : o.hashCode());
            }
            if (e == to)
                break;
        }
        return hashCode;
    }

    boolean equals(Object o, int from, int to) {
        if (!(o instanceof Indexable))
            return false;
        Indexable<?> l = (Indexable<?>) o;
        final Object[] es = elements;
        int i = from, s = l.size();
        if (s != sub(to, from, es.length))
            return false;
        if (s == 0)
            return true;
        for (int j = 0, e = (i <= to) ? to : es.length; /**/; i = 0, e = to) {
            for (/**/; i < e; i++) {
                Object t = es[i];
                Object c = l.get(j++);
                if (!(t == null ? c == null : t.equals(c)))
                    return false;
            }
            if (e == to)
                break;
        }
        return true;
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

    /** debugging */
    void checkInvariants() {
        // Use head and tail fields with empty slot at tail strategy.
        // head == tail disambiguates to "empty".
        try {
            // int capacity = elements.length;
            // assert 0 <= head && head < capacity;
            // assert 0 <= tail && tail < capacity;
            // assert capacity > 0;
            // assert size() < capacity;
            // assert head == tail || elements[head] != null;
            // assert elements[tail] == null;
            // assert head == tail || elements[dec(tail, capacity)] != null;
        } catch (Throwable t) {
            System.err.printf("head=%d tail=%d capacity=%d%n", head, tail, elements.length);
            System.err.printf("elements=%s%n", Arrays.toString(elements));
            throw t;
        }
    }
}
