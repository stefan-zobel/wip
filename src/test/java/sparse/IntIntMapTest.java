package sparse;

import java.util.HashMap;
import java.util.Random;
import java.util.Map.Entry;

public class IntIntMapTest {

    public static void main(String[] args) {
        int UPPER_BOUND = 511;
        int INVALID_KEY = -5;
        HashMap<Integer, Integer> testedKeyValues = new HashMap<>();
        Random r = new Random();
        IntIntMap map = new IntIntMap(INVALID_KEY);

        testFill(map, testedKeyValues, INVALID_KEY, UPPER_BOUND, r);
        testRemove(map, testedKeyValues, INVALID_KEY, UPPER_BOUND);

        testedKeyValues.clear();
        testFill(map, testedKeyValues, INVALID_KEY, UPPER_BOUND, r);
        testRemove(map, testedKeyValues, INVALID_KEY, UPPER_BOUND);

        testedKeyValues.clear();
        map.clear();
        printStats(map);
    }

    private static void testFill(IntIntMap map, HashMap<Integer, Integer> testedKeyValues, int INVALID_KEY,
            int UPPER_BOUND, Random r) {
        for (int i = 0; i <= UPPER_BOUND; ++i) {
            int key = r.ints(0, Integer.MAX_VALUE).findFirst().getAsInt();
            int value = i;
            map.put(key, value);
            int retVal = map.get(key);
            if (retVal != value) {
                System.err.println("WRONG return value !!!");
            }
            testedKeyValues.put(key, value);
        }
        printStats(map);
    }

    private static void testRemove(IntIntMap map, HashMap<Integer, Integer> testedKeyValues, int INVALID_KEY,
            int UPPER_BOUND) {
        for (Entry<Integer, Integer> entry : testedKeyValues.entrySet()) {
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
        printStats(map);
    }

    private static void printStats(IntIntMap map) {
        int count = map.size();
        int bucketCount = map.getBucketCount();
        int bucketsUsed = map.getBucketsOccupiedCount();
        double avgBucketLen = map.getAverageOccupiedBucketLength();
        int maxLength = map.getMaxOccupiedBucketLength();
        System.out.println("#elements   : " + count);
        System.out.println("bucketCount : " + bucketCount);
        System.out.println("bucketsUsed : " + bucketsUsed);
        System.out.println("percent used: " + ((double) bucketsUsed / bucketCount));
        System.out.println("avgBucketLen: " + avgBucketLen);
        System.out.println("maxLength   : " + maxLength);
        System.out.println("---");
    }
}
