package misc;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A generic sparse set with primitive {@code int} keys.
 *
 * <p>Implements the sparse set algorithm well-known in game programming.
 * It maintains two parallel arrays:
 * <ul>
 *   <li><b>sparse[]</b> – maps a key directly to its index in the dense array
 *       ({@code sparse[key] = denseIndex}). Indexed by the key value itself,
 *       so its size must be at least {@code maxKey + 1}.</li>
 *   <li><b>dense[]</b> – stores keys in a tightly packed, gap-free layout.</li>
 *   <li><b>values[]</b> – stores values in parallel with {@code dense[]}.</li>
 * </ul>
 *
 * <p>Time complexity:
 * <ul>
 *   <li>{@link #contains} / {@link #get} / {@link #put} / {@link #remove}: <b>O(1)</b></li>
 *   <li>Iteration via {@link #forEach}: <b>O(n)</b>, cache-friendly linear scan</li>
 *   <li>{@link #clear}: <b>O(n)</b> only to null out value references (GC safety)</li>
 * </ul>
 *
 * <p><b>Constraint:</b> Keys must be in the range {@code [0, capacity)}.
 * The memory footprint of {@code sparse[]} scales with the <em>maximum possible key</em>,
 * not with the number of entries stored.
 *
 * @param <V> type of the stored values
 */
@SuppressWarnings("unchecked")
public final class SparseSet<V> implements Iterable<SparseSet.Entry<V>> {

    /**
     * Maps key ? index in the dense array.
     * A key {@code k} is considered present iff
     * {@code sparse[k] < size && dense[sparse[k]] == k}.
     */
    private final int[] sparse;

    /** Tightly packed array of keys (no gaps). Only indices {@code [0, size)} are valid. */
    private int[] dense;

    /** Values in parallel with {@code dense[]}. */
    private V[] values;

    /** Number of entries currently stored. */
    private int size;

    /** Exclusive upper bound for valid keys: keys must be in {@code [0, capacity)}. */
    private final int capacity;

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    /**
     * Creates a sparse set.
     *
     * @param capacity            exclusive upper bound for keys; keys must be in {@code [0, capacity)}
     * @param initialDenseCapacity initial capacity of the dense/values arrays
     * @throws IllegalArgumentException if either argument is {@code <= 0}
     */
    public SparseSet(int capacity, int initialDenseCapacity) {
        if (capacity <= 0)            throw new IllegalArgumentException("capacity must be > 0");
        if (initialDenseCapacity <= 0) throw new IllegalArgumentException("initialDenseCapacity must be > 0");

        this.capacity = capacity;
        this.sparse   = new int[capacity];
        this.dense    = new int[initialDenseCapacity];
        this.values   = (V[]) new Object[initialDenseCapacity];
        this.size     = 0;
        // sparse[] does not need explicit initialization:
        // validity is always verified via the double-check  dense[sparse[key]] == key.
    }

    /**
     * Creates a sparse set with {@code initialDenseCapacity = min(capacity, 16)}.
     *
     * @param capacity exclusive upper bound for keys
     */
    public SparseSet(int capacity) {
        this(capacity, Math.min(capacity, 16));
    }

    // ------------------------------------------------------------------
    // Core operations
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if the given key is present in this set.
     * Performs exactly two array accesses – no hashing involved.
     *
     * @param key the key to test; must be in {@code [0, capacity)}
     * @return {@code true} if the key is present
     * @throws IndexOutOfBoundsException if {@code key} is out of range
     */
    public boolean contains(int key) {
        checkKey(key);
        int idx = sparse[key];
        return idx < size && dense[idx] == key;
    }

    /**
     * Returns the value associated with {@code key}, or {@code null} if not present.
     *
     * @param key the key to look up; must be in {@code [0, capacity)}
     * @return the associated value, or {@code null}
     * @throws IndexOutOfBoundsException if {@code key} is out of range
     */
    public V get(int key) {
        checkKey(key);
        int idx = sparse[key];
        if (idx < size && dense[idx] == key) {
            return values[idx];
        }
        return null;
    }

    /**
     * Inserts or updates a key-value pair.
     * If the key is already present its value is replaced; otherwise a new entry is appended
     * to the end of the dense array in O(1).
     *
     * @param key   the key; must be in {@code [0, capacity)}
     * @param value the value to associate with {@code key}
     * @return the previous value, or {@code null} if the key was not present
     * @throws IndexOutOfBoundsException if {@code key} is out of range
     */
    public V put(int key, V value) {
        checkKey(key);
        int idx = sparse[key];

        if (idx < size && dense[idx] == key) {
            // Key already present – update value only.
            V old = values[idx];
            values[idx] = value;
            return old;
        }

        // New entry: append to the dense layer.
        ensureDenseCapacity(size + 1);
        sparse[key]  = size;
        dense[size]  = key;
        values[size] = value;
        size++;
        return null;
    }

    /**
     * Removes the entry for {@code key} and returns its value.
     *
     * <p>Uses the <em>swap-with-last</em> trick to keep the dense array gap-free in O(1):
     * the last entry is moved into the slot of the removed entry.
     *
     * @param key the key to remove; must be in {@code [0, capacity)}
     * @return the removed value, or {@code null} if the key was not present
     * @throws IndexOutOfBoundsException if {@code key} is out of range
     */
    public V remove(int key) {
        checkKey(key);
        int idx = sparse[key];

        if (idx >= size || dense[idx] != key) {
            return null; // key not present
        }

        V removed = values[idx];

        // Move the last entry into the freed slot.
        int lastKey     = dense[size - 1];
        dense[idx]      = lastKey;
        values[idx]     = values[size - 1];
        sparse[lastKey] = idx;

        // Release the last slot (helps GC).
        values[size - 1] = null;
        size--;

        return removed;
    }

    /**
     * Removes all entries.
     * Null-ifies value references in {@code [0, size)} for GC safety, then resets {@code size}.
     */
    public void clear() {
        Arrays.fill(values, 0, size, null);
        size = 0;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** Returns the number of entries currently stored. */
    public int size() { return size; }

    /** Returns {@code true} if no entries are stored. */
    public boolean isEmpty() { return size == 0; }

    /** Returns the exclusive upper bound for valid keys ({@code [0, capacity)}). */
    public int capacity() { return capacity; }

    // ------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------

    /**
     * Returns an iterator over all entries.
     *
     * <p><b>Note:</b> The {@link Entry} object returned by {@link Iterator#next()} is reused
     * across calls to avoid allocations. Do not hold references to it beyond a single iteration step.
     *
     * @return a fresh iterator
     */
    @Override
    public Iterator<Entry<V>> iterator() {
        return new Iterator<>() {
            private int cursor = 0;
            private final Entry<V> entry = new Entry<>(); // reused to avoid allocations

            @Override public boolean hasNext() { return cursor < size; }

            @Override
            public Entry<V> next() {
                if (!hasNext()) throw new NoSuchElementException();
                entry.key   = dense[cursor];
                entry.value = values[cursor];
                cursor++;
                return entry;
            }
        };
    }

    /**
     * Iterates all entries and invokes {@code action} for each key-value pair.
     * Preferred over the {@link Iterator} API because it avoids allocating an iterator object
     * and uses a primitive {@code int} for the key parameter.
     *
     * @param action the action to perform for each entry; must not be {@code null}
     */
    public void forEach(EntryConsumer<V> action) {
        Objects.requireNonNull(action, "action must not be null");
        for (int i = 0; i < size; i++) {
            action.accept(dense[i], values[i]);
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void checkKey(int key) {
        if (key < 0 || key >= capacity) {
            throw new IndexOutOfBoundsException(
                "Key " + key + " is out of range [0, " + capacity + ")");
        }
    }

    private void ensureDenseCapacity(int needed) {
        if (needed > dense.length) {
            int newLen = Math.max(needed, dense.length * 2);
            dense  = Arrays.copyOf(dense,  newLen);
            values = Arrays.copyOf(values, newLen);
        }
    }

    // ------------------------------------------------------------------
    // Nested types
    // ------------------------------------------------------------------

    /**
     * A reusable key-value entry returned during iteration.
     * The same instance is reused across {@link Iterator#next()} calls –
     * do not store references to it.
     *
     * @param <V> value type
     */
    public static final class Entry<V> {
        /** The key of this entry. */
        public int key;
        /** The value associated with {@link #key}. */
        public V   value;

        private Entry() {}

        @Override
        public String toString() { return key + "=" + value; }
    }

    /**
     * A functional interface for iterating entries with a primitive {@code int} key,
     * avoiding autoboxing overhead.
     *
     * @param <V> value type
     */
    @FunctionalInterface
    public interface EntryConsumer<V> {
        /**
         * Processes a single key-value entry.
         *
         * @param key   the primitive int key
         * @param value the associated value
         */
        void accept(int key, V value);
    }

    // ------------------------------------------------------------------
    // Object overrides
    // ------------------------------------------------------------------

    /**
     * Returns a human-readable representation of all entries, e.g. {@code SparseSet{7=foo, 42=bar}}.
     */
    @Override
    public String toString() {
        var sb = new StringBuilder("SparseSet{");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(dense[i]).append('=').append(values[i]);
        }
        return sb.append('}').toString();
    }
}
