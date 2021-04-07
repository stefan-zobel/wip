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
package doublelist;

import java.util.Arrays;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.DoubleConsumer;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

public interface DoubleList {

    default DoubleStream stream() {
        return StreamSupport.doubleStream(spliterator(), false);
    }

    default DoubleStream parallelStream() {
        return StreamSupport.doubleStream(spliterator(), true);
    }

    default void forEach(DoubleConsumer action) {
        Objects.requireNonNull(action);
        for (int i = 0; i < this.size(); ++i) {
            action.accept(this.get(i));
        }
    }

    default void sort() {
        double[] a = this.toArray();
        Arrays.sort(a);
        for (int i = 0; i < this.size(); ++i) {
            this.set(i, a[i]);
        }
    }

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    int size();

    /**
     * Returns {@code true} if this list contains no elements.
     *
     * @return {@code true} if this list contains no elements
     */
    boolean isEmpty();

    /**
     * Returns {@code true} if this list contains the specified element. More
     * formally, returns {@code true} if and only if this list contains at least
     * one element {@code e} such that {@code o == e}.
     *
     * @param o
     *            element whose presence in this list is to be tested
     * @return {@code true} if this list contains the specified element
     */
    boolean contains(double o);

    boolean containsAll(DoubleList c);

    /**
     * Returns the index of the first occurrence of the specified element in
     * this list, or -1 if this list does not contain the element. More
     * formally, returns the lowest index {@code i} such that
     * {@code o == get(i)}, or -1 if there is no such index.
     */
    int indexOf(double o);

    /**
     * Returns the index of the last occurrence of the specified element in this
     * list, or -1 if this list does not contain the element. More formally,
     * returns the highest index {@code i} such that {@code o == get(i)}, or -1
     * if there is no such index.
     */
    int lastIndexOf(double o);

    /**
     * Returns an array containing all of the elements in this list in proper
     * sequence (from first to last element).
     *
     * <p>
     * The returned array will be "safe" in that no references to it are
     * maintained by this list. (In other words, this method must allocate a new
     * array). The caller is thus free to modify the returned array.
     *
     * @return an array containing all of the elements in this list in proper
     *         sequence
     */
    double[] toArray();

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index
     *            index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException
     */
    double get(int index);

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @param index
     *            index of the element to replace
     * @param element
     *            element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException
     */
    double set(int index, double element);

    /**
     * Appends the specified element to the end of this list.
     *
     * @param e
     *            element to be appended to this list
     * @return {@code true}
     */
    boolean add(double e);

    /**
     * Inserts the specified element at the specified position in this list.
     * Shifts the element currently at that position (if any) and any subsequent
     * elements to the right (adds one to their indices).
     *
     * @param index
     *            index at which the specified element is to be inserted
     * @param element
     *            element to be inserted
     * @throws IndexOutOfBoundsException
     */
    void add(int index, double element);

    /**
     * Removes the element at the specified position in this list. Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     *
     * @param index
     *            the index of the element to be removed
     * @return the element that was removed from the list
     * @throws IndexOutOfBoundsException
     */
    double remove(int index);

    /**
     * {@inheritDoc}
     */
    boolean equals(Object o);

    /**
     * {@inheritDoc}
     */
    int hashCode();

    /**
     * Removes the first occurrence of the specified element from this list, if
     * it is present. If the list does not contain the element, it is unchanged.
     * More formally, removes the element with the lowest index {@code i} such
     * that {@code o == get(i)} (if such an element exists). Returns
     * {@code true} if this list contained the specified element (or
     * equivalently, if this list changed as a result of the call).
     *
     * @param o
     *            element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     */
    boolean remove(double o);

    /**
     * Removes all of the elements from this list. The list will be empty after
     * this call returns.
     */
    void clear();

    /**
     * Appends all of the elements in the specified list to the end of this
     * list, in the order that they are returned by the specified list's
     * DIterator. The behavior of this operation is undefined if the specified
     * list is modified while the operation is in progress. (This implies that
     * the behavior of this call is undefined if the specified list is this
     * list, and this list is nonempty.)
     *
     * @param c
     *            list containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws NullPointerException
     *             if the specified list is null
     */
    boolean addAll(DoubleList c);

    /**
     * Inserts all of the elements in the specified list into this list,
     * starting at the specified position. Shifts the element currently at that
     * position (if any) and any subsequent elements to the right (increases
     * their indices). The new elements will appear in the list in the order
     * that they are returned by the specified list's iterator.
     *
     * @param index
     *            index at which to insert the first element from the specified
     *            list
     * @param c
     *            list containing elements to be added to this list
     * @return {@code true} if this list changed as a result of the call
     * @throws IndexOutOfBoundsException
     * @throws NullPointerException
     *             if the specified list is null
     */
    boolean addAll(int index, DoubleList c);

    /**
     * Removes from this list all of its elements that are contained in the
     * specified list.
     *
     * @param c
     *            list containing elements to be removed from this list
     * @return {@code true} if this list changed as a result of the call
     */
    boolean removeAll(DoubleList c);

    /**
     * Retains only the elements in this list that are contained in the
     * specified list. In other words, removes from this list all of its
     * elements that are not contained in the specified list.
     *
     * @param c
     *            list containing elements to be retained in this list
     * @return {@code true} if this list changed as a result of the call
     */
    boolean retainAll(DoubleList c);

    /**
     * Returns a list iterator over the elements in this list (in proper
     * sequence), starting at the specified position in the list. The specified
     * index indicates the first element that would be returned by an initial
     * call to {@link DListIterator#next next}. An initial call to
     * {@link DListIterator#previous previous} would return the element with the
     * specified index minus one.
     *
     * <p>
     * The returned list iterator is <i>fail-fast</i>.
     *
     * @throws IndexOutOfBoundsException
     */
    DListIterator listIterator(int index);

    /**
     * Returns a list iterator over the elements in this list (in proper
     * sequence).
     *
     * <p>
     * The returned list iterator is <i>fail-fast</i>.
     */
    DListIterator listIterator();

    /**
     * Returns an iterator over the elements in this list in proper sequence.
     *
     * <p>
     * The returned iterator is <i>fail-fast</i>.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    DIterator iterator();

    /**
     * Returns a view of the portion of this list between the specified
     * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive. (If
     * {@code fromIndex} and {@code toIndex} are equal, the returned list is
     * empty.) The returned list is backed by this list, so non-structural
     * changes in the returned list are reflected in this list, and vice-versa.
     * The returned list supports all of the optional list operations.
     *
     * <p>
     * This method eliminates the need for explicit range operations (of the
     * sort that commonly exist for arrays). Any operation that expects a list
     * can be used as a range operation by passing a subList view instead of a
     * whole list. For example, the following idiom removes a range of elements
     * from a list:
     * 
     * <pre>
     * list.subList(from, to).clear();
     * </pre>
     * 
     * Similar idioms may be constructed for {@link #indexOf(double)} and
     * {@link #lastIndexOf(double)}, and all of the algorithms in a
     * {@code DoubleList} can be applied to a subList.
     *
     * <p>
     * The semantics of the list returned by this method become undefined if the
     * backing list (i.e., this list) is <i>structurally modified</i> in any way
     * other than via the returned list. (Structural modifications are those
     * that change the size of this list, or otherwise perturb it in such a
     * fashion that iterations in progress may yield incorrect results.)
     *
     * @throws IndexOutOfBoundsException
     * @throws IllegalArgumentException
     */
    DoubleList subList(int fromIndex, int toIndex);

    /**
     * Creates a <em>late-binding</em> and <em>fail-fast</em>
     * {@link Spliterator} over the elements in this list.
     *
     * <p>
     * The {@code Spliterator.OfDouble} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#ORDERED}. Overriding
     * implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator.OfDouble} over the elements in this list
     */
    Spliterator.OfDouble spliterator();

    /**
     * Returns the offset of this list.
     * 
     * @return the offset of this list
     */
    int offset();

    /**
     * Returns the underlying as an escape hatch. Note that the underlying array
     * may be the root array in the case of SubLists.
     * 
     * @return the underlying array
     */
    double[] getArrayUnsafe();

    /**
     * Assign the constant {@code val} to each element in this list.
     * 
     * @param val
     *            the constant to assign
     * @return this {@code DoubleList}
     */
    DoubleList assignConst(double val);

    /**
     * Add the scalar {@code val} to each element in this list.
     * 
     * @param val
     *            the scalar to add
     * @return this {@code DoubleList}
     */
    DoubleList plus(double val);

    /**
     * Multiply each element in this list by the scalar {@code val}.
     * 
     * @param val
     *            the scalar to multiply by
     * @return this {@code DoubleList}
     */
    DoubleList mul(double val);

    /**
     * Add the scalar {@code val} to the element at {@code index}.
     * 
     * @param index
     *            index of the element to increment by {@code val}
     * @param val
     *            the scalar to add to the element
     * @return the new value of the element after addition
     */
    double plusi(int index, double val);

    /**
     * Multiply the element at {@code index} by the scalar {@code val}.
     * 
     * @param index
     *            index of the element to multiply by {@code val}
     * @param val
     *            the scalar to multiply by
     * @return the new value of the element after multiplication
     */
    double muli(int index, double val);

    /**
     * Computes the dot product of this list and the given {@code list}.
     * 
     * @param list
     *            a DoubleList which must have the same {@link #size()} as this
     *            list
     * @return the dot product of this list and and the given {@code list}
     */
    double dot(DoubleList list);

    /**
     * Computes the dot product of this list and the given {@code array}.
     * 
     * @param array
     *            an array which must have the same length as this list's
     *            {@link #size()}
     * @return the dot product of this list and and the given {@code array}.
     */
    double dot(double[] array);

    /**
     * Add the scalars from the given {@code list} to the elements in this list.
     * Note that {@code list} doesn't have to have the same {@link #size()} as
     * this list as the minimum of both list sizes is used to determine the
     * number of scalars to add.
     * 
     * @param list
     *            a DoubleList of scalars to add to this list
     * @return this {@code DoubleList}
     */
    DoubleList plusn(DoubleList list);

    /**
     * Add the scalars from the given {@code array} to the elements in this
     * list. Note that {@code array} doesn't have to have the same length as
     * this list as the minimum of both lengths is used to determine the number
     * of scalars to add.
     * 
     * @param array
     *            an array of scalars to add to this list
     * @return this {@code DoubleList}
     */
    DoubleList plusn(double[] array);

    /**
     * Multiplies the scalars from this list by the scalars from the given
     * {@code list}. Note that {@code list} doesn't have to have the same
     * {@link #size()} as this list as the minimum of both list sizes is used to
     * determine the number of scalars to multiply.
     * 
     * @param list
     *            a DoubleList of scalars to multiply by the elements in this
     *            list
     * @return this {@code DoubleList}
     */
    DoubleList muln(DoubleList list);

    /**
     * Multiplies the scalars from this list by the scalars from the given
     * {@code array}. Note that {@code array} doesn't have to have the same
     * length as this list as the minimum of both lengths is used to determine
     * the number of scalars to multiply.
     * 
     * @param array
     *            an array of scalars to multiply by the elements in this list
     * @return this {@code DoubleList}
     */
    DoubleList muln(double[] array);
}
