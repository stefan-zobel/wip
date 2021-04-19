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

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.UnaryOperator;

/**
 * An indexable ordered sequence of elements that mostly provides a subset of
 * the operations defined in the {@link List} interface with the additional
 * understanding that integer indexed operations like {@link #get(int)} or
 * {@link #set(int, Object)} guarantee constant time random access as would be
 * expressed by implementing {@link RandomAccess} in the case of {@code List}s.
 * <p>
 * 
 * @param <E>
 *            the type of the elements
 */
public interface Indexable<E> extends Collection<E> {

    /**
     * Compares the specified object with this Indexable for equality. Returns
     * {@code true} if and only if the specified object is also an Indexable,
     * both containers have the same size, and all corresponding pairs of
     * elements in the two containers are <i>equal</i>. (Two elements {@code e1}
     * and {@code e2} are <i>equal</i> if {@code (e1==null ? e2==null :
     * e1.equals(e2))}.) In other words, two containers are defined to be equal
     * if they contain the same elements in the same order.
     * <p>
     *
     * This implementation first checks if the specified object is this
     * Indexable. If so, it returns {@code true}; if not, it checks if the
     * specified object is an Indexable. If not, it returns {@code false}; if
     * so, it iterates over both containers, comparing corresponding pairs of
     * elements. If any comparison returns {@code false}, this method returns
     * {@code false}. If either iterator runs out of elements before the other
     * it returns {@code false} (as the containers are of unequal length);
     * otherwise it returns {@code true} when the iterations complete.
     *
     * @param o
     *            the object to be compared for equality with this Indexable
     * @return {@code true} if the specified object is equal to this Indexable
     */
    boolean equals(Object o);

    /**
     * Returns the hash code value for this Indexable. The hash code of an
     * Indexable is defined to be the result of the following calculation:
     *
     * <pre>
     * {@code
     *  int hashCode = 1;
     *  for (int i = 0; i < indexable.size(); ++i)
     *      hashCode = 31 * hashCode + (indexable.get(i) == null ? 0 : indexable.get(i).hashCode());
     * }
     * </pre>
     *
     * This ensures that {@code indexable1.equals(indexable2)} implies that
     * {@code indexable1.hashCode()==indexable2.hashCode()} for any two
     * Indexables, {@code indexable1} and {@code indexable2}, as required by the
     * general contract of {@link Object#hashCode}.
     *
     * @return the hash code value for this Indexable
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    int hashCode();

    /**
     * Returns the number of elements in this Indexable.
     *
     * @return the number of elements in this Indexable
     */
    int size();

    /**
     * Inserts all of the elements in the specified collection into this
     * Indexable at the specified position. Shifts the element currently at that
     * position (if any) and any subsequent elements to the right (increases
     * their indices). The new elements will appear in this Indexable in the
     * order that they are returned by the specified collection's iterator. The
     * behavior of this operation is undefined if the specified collection is
     * modified while the operation is in progress. (Note that this will occur
     * if the specified collection is this Indexable, and it's nonempty.)
     *
     * @param index
     *            index at which to insert the first element from the specified
     *            collection
     * @param c
     *            collection containing elements to be added to this Indexable
     * @return {@code true} if this Indexable changed as a result of the call
     * @throws NullPointerException
     *             if the specified collection contains one or more null
     *             elements or if the specified collection is null
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index > size()})
     */
    boolean addAll(int index, Collection<? extends E> c);

    /**
     * Sorts this Indexable according to the order induced by the specified
     * {@link Comparator}. The sort is <i>stable</i>: this method must not
     * reorder equal elements.
     *
     * <p>
     * All elements in this Indexable must be <i>mutually comparable</i> using
     * the specified comparator (that is, {@code c.compare(e1, e2)} must not
     * throw a {@code ClassCastException} for any elements {@code e1} and
     * {@code e2} in the Indexable).
     *
     * <p>
     * If the specified comparator is {@code null} then all elements in this
     * Indexable must implement the {@link Comparable} interface and the
     * elements' {@linkplain Comparable natural ordering} should be used.
     *
     * @param c
     *            the {@code Comparator} used to compare Indexable elements. A
     *            {@code null} value indicates that the elements'
     *            {@linkplain Comparable natural ordering} should be used
     * @throws ClassCastException
     *             if the Indexable contains elements that are not <i>mutually
     *             comparable</i> using the specified comparator
     * @throws IllegalArgumentException
     *             if the comparator is found to violate the {@link Comparator}
     *             contract
     */
    void sort(Comparator<? super E> c);

    /**
     * Returns the element at the specified position in this Indexable.
     *
     * @param index
     *            index of the element to return
     * @return the element at the specified position in this Indexable
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index >= size()})
     */
    E get(int index);

    /**
     * Replaces the element at the specified position in this Indexable with the
     * specified element.
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
    E set(int index, E element);

    /**
     * Inserts the specified element at the specified position in this
     * Indexable. Shifts the element currently at that position (if any) and any
     * subsequent elements to the right (adds one to their indices).
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
    void add(int index, E element);

    /**
     * Removes the element at the specified position in this Indexable. Shifts
     * any subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the Indexable.
     *
     * @param index
     *            the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index >= size()})
     */
    E removeAt(int index);

    /**
     * Removes a randomly chosen element from this Indexable. Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the Indexable or {@code null}
     * when it was empty.
     *
     * @return the randomly chosen element that was removed or {@code null} if
     *         this Indexable is empty.
     */
    E removeAtRandom();

    /**
     * Returns the index of the first occurrence of the specified element in
     * this Indexable, or -1 if this Indexable does not contain the element.
     * More formally, returns the lowest index {@code i} such that
     * {@code Objects.equals(o, get(i))}, or -1 if there is no such index.
     *
     * @param o
     *            element to search for
     * @return the index of the first occurrence of the specified element in
     *         this Indexable, or -1 if this Indexable does not contain the
     *         element
     */
    int indexOf(Object o);

    /**
     * Returns the index of the last occurrence of the specified element in this
     * Indexable, or -1 if this Indexable does not contain the element. More
     * formally, returns the highest index {@code i} such that
     * {@code Objects.equals(o, get(i))}, or -1 if there is no such index.
     *
     * @param o
     *            element to search for
     * @return the index of the last occurrence of the specified element in this
     *         Indexable, or -1 if this Indexable does not contain the element
     */
    int lastIndexOf(Object o);

    /**
     * Returns an iterator over the elements in this Indexable in proper
     * sequence.
     *
     * @return an iterator over the elements in this Indexable in proper
     *         sequence
     */
    Iterator<E> iterator();

    /**
     * Returns a list iterator over the elements in this Indexable (in proper
     * sequence).
     *
     * @return a list iterator over the elements in this Indexable (in proper
     *         sequence)
     */
    ListIterator<E> listIterator();

    /**
     * Returns a list iterator over the elements in this Indexable (in proper
     * sequence), starting at the specified position in the Indexable. The
     * specified index indicates the first element that would be returned by an
     * initial call to {@link ListIterator#next next}. An initial call to
     * {@link ListIterator#previous previous} would return the element with the
     * specified index minus one.
     *
     * @param index
     *            index of the first element to be returned from the list
     *            iterator (by a call to {@link ListIterator#next next})
     * @return a list iterator over the elements in this Indexable (in proper
     *         sequence), starting at the specified position in the Indexable
     * @throws IndexOutOfBoundsException
     *             if the index is out of range
     *             ({@code index < 0 || index > size()})
     */
    ListIterator<E> listIterator(int index);

    /**
     * Increases the internal storage of this Indexable, if necessary, to ensure
     * that it can hold at least the given number of elements.
     *
     * @param minCapacity
     *            the desired minimum capacity
     */
    void ensureCapacity(int minCapacity);

    /**
     * Minimizes the internal storage of this Indexable.
     */
    void trimToSize();

    /**
     * Replaces each element of this Indexable with the result of applying the
     * operator to that element, as specified by {@link List#replaceAll}.
     *
     * @param operator
     *            the operator to apply to each element
     */
    void replaceAll(UnaryOperator<E> operator);

    /**
     * Returns a {@code List} containing all of the elements in this Indexable
     * in proper sequence (from first to last element).
     * 
     * @return a List containing all of the elements in this Indexable in proper
     *         order
     */
    List<E> toList();

    /**
     * Returns {@code true} when access to this Indexable by multiple threads
     * can effectively be controlled by the {@link Lock} exposed via the
     * {@link #getLock()}, {@link #lockInterruptibly()} and {@link #unlock()}
     * methods.
     * 
     * @return {@code true} when access to this Indexable can effectively be
     *         controlled via locking operations, {@code false} otherwise
     */
    boolean isSynced();

    /**
     * Returns the lock of this Indexable. If this is <b>not</b> a
     * {@linkplain #isSynced() synced} Indexable the returned {@link Lock}
     * implementation is a {@code no-op} lock that does nothing apart from
     * checking the {@code interrupted status} of the current thread if either
     * {@link Lock#lockInterruptibly()} or {@link Lock#tryLock(long, TimeUnit)}
     * is called on that lock.
     * 
     * @return the underlying actual lock or a no-op lock if this is not a
     *         synced Indexable
     */
    Lock getLock();

    /**
     * If this is a {@linkplain #isSynced() synced} Indexable acquires the lock
     * if it is available and returns immediately unless the current thread is
     * not {@linkplain Thread#interrupt interrupted}. Does nothing if this is
     * not a {@linkplain #isSynced() synced} Indexable.
     * <p>
     * If the lock is not available then the current thread becomes disabled for
     * thread scheduling purposes and lies dormant until one of two things
     * happens:
     *
     * <ul>
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts} the
     * current thread, and interruption of lock acquisition is supported.
     * </ul>
     * <p>
     * If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while acquiring the
     * lock, and interruption of lock acquisition is supported,
     * </ul>
     * then {@link CancellationException} is thrown and the current thread's
     * interrupted status <b>will not</b> be cleared.
     * 
     * @throws CancellationException
     *             if the current thread is interrupted while acquiring the lock
     */
    default void lockInterruptibly() throws CancellationException {
        if (isSynced()) {
            try {
                getLock().lockInterruptibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException ce = new CancellationException(
                        "Got interrupted while waiting to acquire a ReentrantLock");
                ce.initCause(e);
                throw ce;
            }
        }
    }

    /**
     * Releases the lock. Does nothing if this is not a {@linkplain #isSynced()
     * synced} Indexable.
     */
    default void unlock() {
        if (isSynced()) {
            getLock().unlock();
        }
    }
}
