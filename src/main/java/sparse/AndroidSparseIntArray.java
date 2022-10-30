/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sparse;

import java.util.Arrays;

/**
 * AndroidSparseIntArrays map integers to integers. Unlike a normal array of
 * integers, there can be gaps in the indices. It is intended to be more memory
 * efficient than using a HashMap to map Integers to Integers, both because it
 * avoids auto-boxing keys and values and its data structure doesn't rely on an
 * extra entry object for each mapping.
 *
 * <p>
 * Note that this container keeps its mappings in an array data structure, using
 * a binary search to find keys. The implementation is not intended to be
 * appropriate for data structures that may contain large numbers of items. It
 * is generally slower than a traditional HashMap, since lookups require a
 * binary search and adds and removes require inserting and deleting entries in
 * the array. For containers holding up to hundreds of items, the performance
 * difference is not significant, less than 50%.
 * </p>
 *
 * <p>
 * It is possible to iterate over the items in this container using
 * {@link #keyAt(int)} and {@link #valueAt(int)}. Iterating over the keys using
 * <code>keyAt(int)</code> with ascending values of the index will return the
 * keys in ascending order, or the values corresponding to the keys in ascending
 * order in the case of <code>valueAt(int)</code>.
 * </p>
 */
public class AndroidSparseIntArray implements Cloneable {

    private static final int[] EMPTY = new int[] {};

    private int[] mKeys;
    private int[] mValues;
    private int mSize;

    /**
     * Creates a new AndroidSparseIntArray containing no mappings.
     */
    public AndroidSparseIntArray() {
        this(10);
    }

    /**
     * Creates a new AndroidSparseIntArray containing no mappings that will not
     * require any additional memory allocation to store the specified number of
     * mappings. If you supply an initial capacity of 0, the sparse array will
     * be initialized with a light-weight representation not requiring any
     * additional array allocations.
     */
    public AndroidSparseIntArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = EMPTY;
            mValues = EMPTY;
        } else {
            mKeys = new int[initialCapacity];
            mValues = new int[mKeys.length];
        }
        mSize = 0;
    }

    @Override
    public AndroidSparseIntArray clone() {
        AndroidSparseIntArray clone = null;
        try {
            clone = (AndroidSparseIntArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* ignore */
        }
        return clone;
    }

    /**
     * Gets the int mapped from the specified key, or <code>0</code> if no such
     * mapping has been made.
     */
    public int get(int key) {
        return get(key, 0);
    }

    /**
     * Gets the int mapped from the specified key, or the specified value if no
     * such mapping has been made.
     */
    public int get(int key, int valueIfKeyNotFound) {
        int i = binarySearch(mKeys, mSize, key);

        if (i < 0) {
            return valueIfKeyNotFound;
        } else {
            return mValues[i];
        }
    }

    /**
     * Removes the mapping from the specified key, if there was any.
     */
    public void delete(int key) {
        int i = binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            removeAt(i);
        }
    }

    /**
     * Removes the mapping at the given index.
     */
    public void removeAt(int index) {
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        mSize--;
    }

    /**
     * Adds a mapping from the specified key to the specified value, replacing
     * the previous mapping from the specified key if there was one.
     */
    public void put(int key, int value) {
        int i = binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            mKeys = insert(mKeys, mSize, i, key);
            mValues = insert(mValues, mSize, i, value);
            mSize++;
        }
    }

    /**
     * Returns the number of key-value mappings that this AndroidSparseIntArray
     * currently stores.
     */
    public int size() {
        return mSize;
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns the key
     * from the <code>index</code>th key-value mapping that this
     * AndroidSparseIntArray stores.
     *
     * <p>
     * The keys corresponding to indices in ascending order are guaranteed to be
     * in ascending order, e.g., <code>keyAt(0)</code> will return the smallest
     * key and <code>keyAt(size()-1)</code> will return the largest key.
     * </p>
     *
     * <p>
     * For indices outside of the range <code>0...size()-1</code> an
     * {@link ArrayIndexOutOfBoundsException} is thrown.
     */
    public int keyAt(int index) {
        checkIndex(index, mSize);
        return mKeys[index];
    }

    /**
     * Given an index in the range <code>0...size()-1</code>, returns the value
     * from the <code>index</code>th key-value mapping that this
     * AndroidSparseIntArray stores.
     *
     * <p>
     * The values corresponding to indices in ascending order are guaranteed to
     * be associated with keys in ascending order, e.g., <code>valueAt(0)</code>
     * will return the value associated with the smallest key and
     * <code>valueAt(size()-1)</code> will return the value associated with the
     * largest key.
     * </p>
     *
     * <p>
     * For indices outside of the range <code>0...size()-1</code> an
     * {@link ArrayIndexOutOfBoundsException} is thrown.
     */
    public int valueAt(int index) {
        checkIndex(index, mSize);
        return mValues[index];
    }

    /**
     * Directly set the value at a particular index.
     *
     * <p>
     * For indices outside of the range <code>0...size()-1</code> an
     * {@link ArrayIndexOutOfBoundsException} is thrown.
     */
    public void setValueAt(int index, int value) {
        checkIndex(index, mSize);
        mValues[index] = value;
    }

    /**
     * Returns the index for which {@link #keyAt} would return the specified
     * key, or a negative number if the specified key is not mapped.
     */
    public int indexOfKey(int key) {
        return binarySearch(mKeys, mSize, key);
    }

    /**
     * Returns an index for which {@link #valueAt} would return the specified
     * key, or a negative number if no keys map to the specified value. Beware
     * that this is a linear search, unlike lookups by key, and that multiple
     * keys can map to the same value and this will find only one of them.
     */
    public int indexOfValue(int value) {
        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Removes all key-value mappings from this AndroidSparseIntArray.
     */
    public void clear() {
        mSize = 0;
    }

    /**
     * Puts a key/value pair into the array, optimizing for the case where the
     * key is greater than all existing keys in the array.
     */
    public void append(int key, int value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = append(mKeys, mSize, key);
        mValues = append(mValues, mSize, value);
        mSize++;
    }

    /**
     * Provides a copy of keys.
     */
    public int[] copyKeys() {
        if (size() == 0) {
            return null;
        }
        return Arrays.copyOf(mKeys, size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            int key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            int value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }

    // This is Arrays.binarySearch(), but doesn't do any argument validation.
    private static int binarySearch(int[] array, int size, int value) {
        int lo = 0;
        int hi = size - 1;

        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final int midVal = array[mid];

            if (midVal < value) {
                lo = mid + 1;
            } else if (midVal > value) {
                hi = mid - 1;
            } else {
                return mid; // value found
            }
        }
        return ~lo; // value not present
    }

    private static int[] append(int[] array, int currentSize, int element) {
        if (currentSize + 1 > array.length) {
            int[] newArray = new int[growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    private static int[] insert(int[] array, int currentSize, int index, int element) {
        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }

        int[] newArray = new int[growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    /*
     * Given the current size of an array, returns an ideal size to which the
     * array should grow. This is typically double the given size, but should
     * not be relied upon to do so in the future.
     */
    private static int growSize(int currentSize) {
        return currentSize <= 4 ? 8 : currentSize * 2;
    }

    private static void checkIndex(int index, int mSize) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
    }
}
