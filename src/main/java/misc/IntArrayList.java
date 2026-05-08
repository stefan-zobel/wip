package misc;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * A resizable array of primitive {@code int} values – analogous to {@code ArrayList<Integer>}
 * but without any boxing/unboxing overhead and without implementing {@code Collection}.
 *
 * <p>Backed by a plain {@code int[]} that is grown automatically (doubling strategy)
 * when needed. All index-based operations mirror the familiar {@code ArrayList} API
 * wherever it makes sense for a primitive type.
 *
 * <p>Time complexity:
 * <ul>
 *   <li>{@link #add(int)}: amortised O(1)</li>
 *   <li>{@link #get} / {@link #set}: O(1)</li>
 *   <li>{@link #removeAt}: O(n) – shifts tail left</li>
 *   <li>{@link #removeAtFast}: O(1) – swap-with-last, does not preserve order</li>
 *   <li>{@link #indexOf} / {@link #contains}: O(n)</li>
 *   <li>{@link #sort}: O(n log n)</li>
 * </ul>
 */
public final class IntArrayList {

    private static final int DEFAULT_CAPACITY = 16;

    /** Backing store. Only indices {@code [0, size)} are valid. */
    private int[] data;

    /** Number of elements currently stored. */
    private int size;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Creates an empty list with the given initial capacity.
     *
     * @param initialCapacity initial capacity of the backing array; must be {@code >= 0}
     * @throws IllegalArgumentException if {@code initialCapacity < 0}
     */
    public IntArrayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity must be >= 0");
        this.data = new int[Math.max(initialCapacity, 1)];
    }

    /** Creates an empty list with a default initial capacity of {@value #DEFAULT_CAPACITY}. */
    public IntArrayList() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a list pre-populated with a copy of the given array.
     *
     * @param values source array; must not be {@code null}
     */
    public IntArrayList(int[] values) {
        this.data = Arrays.copyOf(values, Math.max(values.length, 1));
        this.size = values.length;
    }

    // ------------------------------------------------------------------
    // Adding elements
    // ------------------------------------------------------------------

    /**
     * Appends {@code value} to the end of this list. Amortised O(1).
     *
     * @param value the value to append
     */
    public void add(int value) {
        ensureCapacity(size + 1);
        data[size++] = value;
    }

    /**
     * Inserts {@code value} at position {@code index}, shifting all subsequent elements right.
     * O(n).
     *
     * @param index position at which to insert; must be in {@code [0, size]}
     * @param value the value to insert
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public void add(int index, int value) {
        checkIndexForAdd(index);
        ensureCapacity(size + 1);
        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = value;
        size++;
    }

    /**
     * Appends all elements of {@code other} to the end of this list.
     *
     * @param other the list to append; must not be {@code null}
     */
    public void addAll(IntArrayList other) {
        ensureCapacity(size + other.size);
        System.arraycopy(other.data, 0, data, size, other.size);
        size += other.size;
    }

    /**
     * Appends all elements of the given array to the end of this list.
     *
     * @param values the array to append; must not be {@code null}
     */
    public void addAll(int[] values) {
        ensureCapacity(size + values.length);
        System.arraycopy(values, 0, data, size, values.length);
        size += values.length;
    }

    // ------------------------------------------------------------------
    // Accessing elements
    // ------------------------------------------------------------------

    /**
     * Returns the element at position {@code index}. O(1).
     *
     * @param index must be in {@code [0, size)}
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public int get(int index) {
        checkIndex(index);
        return data[index];
    }

    /**
     * Replaces the element at position {@code index} and returns the old value. O(1).
     *
     * @param index must be in {@code [0, size)}
     * @param value the new value
     * @return the previous value at {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public int set(int index, int value) {
        checkIndex(index);
        int old = data[index];
        data[index] = value;
        return old;
    }

    /** Returns the first element. Equivalent to {@code get(0)}. */
    public int getFirst() { return get(0); }

    /** Returns the last element. Equivalent to {@code get(size - 1)}. */
    public int getLast() { return get(size - 1); }

    // ------------------------------------------------------------------
    // Removing elements
    // ------------------------------------------------------------------

    /**
     * Removes the element at {@code index}, shifting all subsequent elements left. O(n).
     *
     * @param index must be in {@code [0, size)}
     * @return the removed value
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public int removeAt(int index) {
        checkIndex(index);
        int removed = data[index];
        int tail = size - 1 - index;
        if (tail > 0) System.arraycopy(data, index + 1, data, index, tail);
        size--;
        return removed;
    }

    /**
     * Removes the element at {@code index} by swapping it with the last element. O(1).
     * <b>Does not preserve insertion order.</b>
     *
     * @param index must be in {@code [0, size)}
     * @return the removed value
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public int removeAtFast(int index) {
        checkIndex(index);
        int removed = data[index];
        data[index] = data[size - 1];
        size--;
        return removed;
    }

    /**
     * Removes the first occurrence of {@code value}. O(n).
     *
     * @param value the value to remove
     * @return {@code true} if the value was found and removed
     */
    public boolean removeFirst(int value) {
        int idx = indexOf(value);
        if (idx < 0) return false;
        removeAt(idx);
        return true;
    }

    /** Removes and returns the last element. O(1). */
    public int removeLast() {
        if (size == 0) throw new java.util.NoSuchElementException("list is empty");
        return data[--size];
    }

    /**
     * Removes all elements that match the given predicate. O(n).
     *
     * @param predicate test applied to each element; must not be {@code null}
     * @return number of elements removed
     */
    public int removeIf(IntPredicate predicate) {
        int writeIdx = 0;
        for (int i = 0; i < size; i++) {
            if (!predicate.test(data[i])) {
                data[writeIdx++] = data[i];
            }
        }
        int removed = size - writeIdx;
        size = writeIdx;
        return removed;
    }

    /** Removes all elements, resetting {@code size} to 0. O(1). */
    public void clear() { size = 0; }

    // ------------------------------------------------------------------
    // Searching
    // ------------------------------------------------------------------

    /**
     * Returns the index of the first occurrence of {@code value}, or {@code -1}. O(n).
     *
     * @param value the value to search for
     */
    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    /**
     * Returns the index of the last occurrence of {@code value}, or {@code -1}. O(n).
     *
     * @param value the value to search for
     */
    public int lastIndexOf(int value) {
        for (int i = size - 1; i >= 0; i--) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    /**
     * Returns {@code true} if {@code value} is present in this list. O(n).
     *
     * @param value the value to test
     */
    public boolean contains(int value) { return indexOf(value) >= 0; }

    // ------------------------------------------------------------------
    // Bulk / utility operations
    // ------------------------------------------------------------------

    /**
     * Sorts all elements in ascending order (unsigned comparison via
     * {@link Arrays#sort(int[], int, int)}). O(n log n).
     */
    public void sort() { Arrays.sort(data, 0, size); }

    /**
     * Performs a binary search for {@code value} on a <b>sorted</b> list. O(log n).
     * Behaviour is undefined if the list is not sorted.
     *
     * @param value the value to search for
     * @return index of the value, or {@code (-(insertion point) - 1)} if not found
     */
    public int binarySearch(int value) { return Arrays.binarySearch(data, 0, size, value); }

    /**
     * Replaces every element with the result of applying {@code operator}. O(n).
     *
     * @param operator must not be {@code null}
     */
    public void replaceAll(IntUnaryOperator operator) {
        for (int i = 0; i < size; i++) data[i] = operator.applyAsInt(data[i]);
    }

    /**
     * Fills the entire list (all {@code size} elements) with {@code value}. O(n).
     *
     * @param value the fill value
     */
    public void fill(int value) { Arrays.fill(data, 0, size, value); }

    /**
     * Iterates all elements and passes each to {@code action}. O(n).
     *
     * @param action must not be {@code null}
     */
    public void forEach(IntConsumer action) {
        for (int i = 0; i < size; i++) action.accept(data[i]);
    }

    /**
     * Returns a copy of the valid elements as a new {@code int[]}. O(n).
     *
     * @return a fresh array of length {@code size}
     */
    public int[] toArray() { return Arrays.copyOf(data, size); }

    /**
     * Returns a new {@code IntArrayList} that is an independent copy of this list.
     */
    public IntArrayList copy() { return new IntArrayList(toArray()); }

    /**
     * Trims the backing array to exactly {@code size} elements,
     * releasing any excess memory. O(n).
     */
    public void trimToSize() {
        if (data.length > size) data = Arrays.copyOf(data, Math.max(size, 1));
    }

    /**
     * Ensures that the backing array can hold at least {@code minCapacity} elements
     * without reallocation.
     *
     * @param minCapacity the desired minimum capacity
     */
    public void ensureCapacity(int minCapacity) {
        if (minCapacity > data.length) {
            data = Arrays.copyOf(data, Math.max(minCapacity, data.length * 2));
        }
    }

    // ------------------------------------------------------------------
    // State queries
    // ------------------------------------------------------------------

    /** Returns the number of elements in this list. */
    public int size() { return size; }

    /** Returns {@code true} if this list contains no elements. */
    public boolean isEmpty() { return size == 0; }

    /** Returns the current capacity of the backing array. */
    public int capacity() { return data.length; }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void checkIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("Index " + index + " out of range [0, " + size + ")");
    }

    private void checkIndexForAdd(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("Index " + index + " out of range [0, " + size + "]");
    }

    // ------------------------------------------------------------------
    // Object overrides
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code obj} is an {@code IntArrayList} with the same
     * size and identical elements in the same order.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IntArrayList other)) return false;
        if (size != other.size) return false;
        for (int i = 0; i < size; i++) {
            if (data[i] != other.data[i]) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size; i++) result = 31 * result + data[i];
        return result;
    }

    /** Returns a string like {@code [1, 2, 3]}. */
    @Override
    public String toString() {
        if (size == 0) return "[]";
        var sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(data[i]);
        }
        return sb.append(']').toString();
    }
}
