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
import java.util.Deque;
import java.util.Objects;

/**
 * A {@link Deque} that supports random access. This is solely an intersection
 * type that brings together {@link Indexable} and {@link Deque} and offers a
 * couple of static factory methods.
 * 
 * @param <E>
 *            the type of the deque elements
 */
public interface VecDeque<E> extends Deque<E>, Indexable<E> {

    /**
     * Constructs an empty VecDeque which is not
     * {@linkplain Indexable#isSynced() syncable}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> create() {
        return new IndexableArrayDeque<>();
    }

    /**
     * Constructs an empty VecDeque which is {@linkplain Indexable#isSynced()
     * syncable} if the {@code synced} parameter is {@code true}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} VecDeque
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> create(boolean synced) {
        return new IndexableArrayDeque<>(synced);
    }

    /**
     * Constructs an empty VecDeque with an initial capacity sufficient to hold
     * the specified number of elements which is not
     * {@linkplain Indexable#isSynced() syncable}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @param numElements
     *            lower bound on the initial capacity of the VecDeque
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> create(int numElements) {
        return new IndexableArrayDeque<>(numElements);
    }

    /**
     * Constructs an empty VecDeque with an initial capacity sufficient to hold
     * the specified number of elements that can be
     * {@linkplain Indexable#isSynced() synced} if the {@code synced} parameter
     * is {@code true}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @param numElements
     *            lower bound on the initial capacity of the VecDeque
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} VecDeque
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> create(int numElements, boolean synced) {
        return new IndexableArrayDeque<>(numElements, synced);
    }

    /**
     * Constructs a VecDeque containing the elements of the specified
     * collection, in the order they are returned by the collection's iterator
     * which is not {@linkplain Indexable#isSynced() syncable}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @param c
     *            the collection whose elements are to be placed into the new
     *            VecDeque
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> of(Collection<? extends E> c) {
        return new IndexableArrayDeque<>(Objects.requireNonNull(c));
    }

    /**
     * Constructs a VecDeque containing the elements of the specified
     * collection, in the order they are returned by the collection's iterator
     * that can be {@linkplain Indexable#isSynced() synced} if the
     * {@code synced} parameter is {@code true}.
     * 
     * @param <E>
     *            the type of the VecDeque elements
     * @param c
     *            the collection whose elements are to be placed into the new
     *            VecDeque
     * @param synced
     *            if {@code true} create a {@linkplain Indexable#isSynced()
     *            syncable} VecDeque
     * @return a new VecDeque
     */
    public static <E> VecDeque<E> of(Collection<? extends E> c, boolean synced) {
        return new IndexableArrayDeque<>(Objects.requireNonNull(c), synced);
    }
}
