/*
 * Copyright 2022 Stefan Zobel
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
package misc;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

/**
 * A map that maps non-negative integer keys to arbitrary integer values. An
 * integer value that cannot occur as a value in the map must be passed as
 * {@code valueIfKeyNotFound} on construction. This special value is returned
 * from all value returning methods when the passed key doesn't exist in the
 * map. All methods that accept an integer key argument throw
 * {@link ArrayIndexOutOfBoundsException} if a negative key gets passed.
 */
public class IntIntMap {

    private static final double THRESHOLD_FACTOR = 0.5;
    private static final int MAX_CAP = 1 << 30;
    private static final int INITIAL_CAP = 32;
    private static final int MIN_LEN = 8;

    private final int valIfNoKey;
    private int count;
    private int threshold;
    private int[][] keys;
    private int[][] vals;

    public IntIntMap(int valueIfKeyNotFound) {
        keys = new int[INITIAL_CAP][];
        vals = new int[INITIAL_CAP][];
        threshold = computeThreshold(keys.length);
        valIfNoKey = valueIfKeyNotFound;
    }

    public int get(int key) {
        checkKey(key);
        int idx = modPowerOf2(key, keys.length);
        int[] k = keys[idx];
        if (k != null) {
            for (int i = 0; i < k.length; ++i) {
                int key_ = k[i];
                if (key_ == -1) {
                    break;
                }
                if (key_ == key) {
                    return vals[idx][i];
                }
            }
        }
        return valIfNoKey;
    }

    public int put(int key, int value) {
        checkKey(key);
        int idx = modPowerOf2(key, keys.length);
        int[] k = keys[idx];
        if (k != null) {
            for (int i = 0; i < k.length; ++i) {
                int key_ = k[i];
                if (key_ == -1) {
                    break;
                }
                if (key_ == key) {
                    int oldVal = vals[idx][i];
                    vals[idx][i] = value;
                    return oldVal;
                }
            }
        }

        addNew(key, value, idx);
        return valIfNoKey;
    }

    private void addNew(int key, int value, int idx) {
        if (keys[idx] != null && count >= threshold) {
            resize();
            idx = modPowerOf2(key, keys.length);
        }
        append(keys, key, keys.length, vals, value);
        ++count;
    }

    private void resize() {
        int oldLength = keys.length;
        if (oldLength == MAX_CAP) {
            threshold = Integer.MAX_VALUE;
            return;
        }
        int newCapacity = 2 * oldLength;
        int[][] newKeys = new int[newCapacity][];
        int[][] newVals = new int[newCapacity][];
        transfer(newKeys, keys, newVals, vals);
        keys = newKeys;
        vals = newVals;
        threshold = computeThreshold(newCapacity);
        // XXX
        DEBUG_checkKeyConsistency();
    }

    public int remove(int key) {
        checkKey(key);
        int idx = modPowerOf2(key, keys.length);
        int[] k = keys[idx];
        if (k != null) {
            for (int i = 0; i < k.length; ++i) {
                int key_ = k[i];
                if (key_ == -1) {
                    break;
                }
                if (key_ == key) {
                    int val = vals[idx][i];
                    // XXX
                    if (k[i + 1] == -1) {
                        k[i + 1] = 0;
                        k[i] = -1;
                        vals[idx][i] = 0;
                    } else {
                        // XXX
                        int last = effectiveKeyArrayLength(k) - 1;
                        k[i] = k[last];
                        k[last] = -1;
                        k[last + 1] = 0;
                        vals[idx][i] = vals[idx][last];
                        vals[idx][last] = 0;
                    }
                    return val;
                }
            }
        }
        // XXX
        return valIfNoKey;
    }

    public static void main(String[] args) {
        int UPPER_BOUND = 511;
        int INVALID_KEY = -5;
        HashMap<Integer, Integer> testKeyValues = new HashMap<>();
        Random r = new Random();
        IntIntMap map = new IntIntMap(INVALID_KEY);
        for (int i = 0; i <= UPPER_BOUND; ++i) {
            int key = r.ints(0, Integer.MAX_VALUE).findFirst().getAsInt();
            int value = i;
            map.put(key, value);
            int retVal = map.get(key);
            if (retVal != value) {
                System.err.println("WRONG return value !!!");
            }
            testKeyValues.put(key, value);
        }
        int bucketCount = map.getBucketCount();
        int bucketsUsed = map.getBucketsOccupiedCount();
        double avgBucketLen = map.getAverageOccupiedBucketLength();
        int maxLength = map.getMaxOccupiedBucketLength();
        System.out.println("bucketCount : " + bucketCount);
        System.out.println("bucketsUsed : " + bucketsUsed);
        System.out.println("percent used: " + ((double) bucketsUsed / bucketCount));
        System.out.println("avgBucketLen: " + avgBucketLen);
        System.out.println("maxLength   : " + maxLength);
        for (Entry<Integer, Integer> entry : testKeyValues.entrySet()) {
            int key = entry.getKey();
            int expectedValue = entry.getValue();
            int removedValue = map.remove(key);
            if (removedValue != expectedValue) {
                System.err.println("WRONG value from remove: " + removedValue);
            }
            removedValue = map.remove(key);
            if (removedValue != INVALID_KEY) {
                System.err.println("WRONG value from remove for non-existing key: " + removedValue);
            }
        }
        int x;
        x = 0;
    }

    private static void transfer(int[][] newKeys, int[][] oldKeys, int[][] newVals, int[][] oldVals) {
        int newCapacity = newKeys.length;
        for (int i = 0; i < oldKeys.length; ++i) {
            int[] entries = oldKeys[i];
            if (entries != null && entries.length > 0) {
                for (int j = 0; j < entries.length; ++j) {
                    int key = entries[j];
                    if (key == -1) {
                        break;
                    }
                    int value = oldVals[i][j];
                    append(newKeys, key, newCapacity, newVals, value);
                }
            }
        }
    }

    private void DEBUG_checkKeyConsistency() {
        for (int[] k_ : keys) {
            if (k_ != null && k_.length > 0) {
                int key = k_[0];
                if (key == -1) {
                    break;
                }
                int expected = modPowerOf2(key, keys.length);
                for (int kk : k_) {
                    if (kk == -1) {
                        break;
                    }
                    int idx = modPowerOf2(kk, keys.length);
                    if (idx != expected) {
                        throw new IllegalArgumentException("WRONG index !!! -> " + expected);
                    }
                }
            }
        }
    }

    private static void append(int[][] keys, int key, int capacity, int[][] vals, int value) {
        int idx = modPowerOf2(key, capacity);

        int[] k_ = keys[idx];
        if (k_ == null) {
            k_ = new int[MIN_LEN];
            int[] v_ = new int[MIN_LEN];
            k_[0] = key;
            k_[1] = -1;
            v_[0] = value;
            keys[idx] = k_;
            vals[idx] = v_;
            return;
        }

        int used = effectiveKeyArrayLength(k_);
        if (used < k_.length - 1) {
            k_[used] = key;
            k_[used + 1] = -1;
            vals[idx][used] = value;
            return;
        }

        // it should be quite rare to get here
        k_[used] = key;
        vals[idx][used] = value;
        int[] k__ = new int[2 * k_.length];
        int[] v__ = new int[2 * k_.length];
        System.arraycopy(k_, 0, k__, 0, k_.length);
        System.arraycopy(vals[idx], 0, v__, 0, k_.length);
        k__[k_.length] = -1;
        keys[idx] = k__;
        vals[idx] = v__;
    }

    public int getCurrentThreshold() {
        return threshold;
    }

    public int getBucketCount() {
        return keys.length;
    }

    public int getBucketsOccupiedCount() {
        int occupied = 0;
        for (int[] bucket : keys) {
            if (bucket != null) {
                ++occupied;
            }
        }
        return occupied;
    }

    public double getAverageOccupiedBucketLength() {
        int occupied = 0;
        double length = 0.0;
        for (int[] bucket : keys) {
            if (bucket != null) {
                ++occupied;
                int len = 0;
                for (int i = 0; i < bucket.length; ++i) {
                    int key = bucket[i];
                    if (key == -1) {
                        break;
                    }
                    ++len;
                }
                length += len;
            }
        }
        length = length / occupied;
        return length;
    }

    public int getMaxOccupiedBucketLength() {
        int max = 0;
        for (int[] bucket : keys) {
            if (bucket != null) {
                int len = 0;
                for (int i = 0; i < bucket.length; ++i) {
                    int key = bucket[i];
                    if (key == -1) {
                        break;
                    }
                    ++len;
                }
                max = Math.max(len, max);
            }
        }
        return max;
    }

    private static int effectiveKeyArrayLength(int[] a) {
        int used = 0;
        for (int i = 0; i < a.length; ++i) {
            if (a[i] == -1) {
                break;
            }
            ++used;
        }
        return used;
    }

    private static int modPowerOf2(int key, int len) {
        return key & (len - 1);
    }

    private static int computeThreshold(int capacity) {
        return (int) Math.min(THRESHOLD_FACTOR * capacity, MAX_CAP + 1);
    }

    private static void checkKey(int key) {
        if (key < 0) {
            throw new ArrayIndexOutOfBoundsException(key);
        }
    }
}
