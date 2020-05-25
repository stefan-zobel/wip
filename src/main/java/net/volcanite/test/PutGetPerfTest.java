package net.volcanite.test;

import net.volcanite.db.Databases;
import net.volcanite.db.Get;
import net.volcanite.db.Put2;
import net.volcanite.util.DoubleStatistics;
import static net.volcanite.test.TestUtil.randomBytes;
import static net.volcanite.util.Precision.round;

import java.util.Arrays;

public class PutGetPerfTest {

    public static void main(String[] args) {
        String dbPath = "D:\\Temp\\rocksdb_database";
        int RUNS = 500_000;
        long runtime = 0L;

        try (Put2 put = new Put2(dbPath); Get get = new Get(dbPath)) {
            for (int i = 0; i < RUNS; ++i) {
                long start = System.currentTimeMillis();
                byte[] key = randomBytes();
                byte[] value = randomBytes();

                put.write(key, value);
                byte[] valueRead = get.read(key);
                runtime += (System.currentTimeMillis() - start);

                if (valueRead == null) {
                    throw new RuntimeException("Unexpected: valueRead == null");
                }
                if (!Arrays.equals(value, valueRead)) {
                    throw new RuntimeException("Unexpected: value != valueRead");
                }
            }

            Databases.close(dbPath);
            System.out.println("runti>  avg: " + (runtime / (double) RUNS) + " ms");
            printStatistics(put);
            printStatistics(get);
        }
    }

    private static void printStatistics(Put2 put) {
        DoubleStatistics writeTimeNanos = put.getWriteTimeNanos();
        DoubleStatistics fsyncTimeNanos = put.getFsyncTimeNanos();
        DoubleStatistics totalTimeNanos = put.getTotalTimeNanos();

        System.out.println(
                "write>  avg: " + round(writeTimeNanos.getAverage() / 1_000_000.0) + ", n: " + writeTimeNanos.getCount()
                        + ", std: " + round(writeTimeNanos.getStandardDeviation() / 1_000_000.0) + ", min: "
                        + writeTimeNanos.getMin() / 1_000_000.0 + ", max: " + writeTimeNanos.getMax() / 1_000_000.0);
        System.out.println(
                "fsync>  avg: " + round(fsyncTimeNanos.getAverage() / 1_000_000.0) + ", n: " + fsyncTimeNanos.getCount()
                        + ", std: " + round(fsyncTimeNanos.getStandardDeviation() / 1_000_000.0) + ", min: "
                        + fsyncTimeNanos.getMin() / 1_000_000.0 + ", max: " + fsyncTimeNanos.getMax() / 1_000_000.0);
        System.out.println(
                "total>  avg: " + round(totalTimeNanos.getAverage() / 1_000_000.0) + ", n: " + totalTimeNanos.getCount()
                        + ", std: " + round(totalTimeNanos.getStandardDeviation() / 1_000_000.0) + ", min: "
                        + totalTimeNanos.getMin() / 1_000_000.0 + ", max: " + totalTimeNanos.getMax() / 1_000_000.0);
        System.out.println("fsync every: "
                + (totalTimeNanos.getSum() - fsyncTimeNanos.getSum()) / (1_000_000.0 * fsyncTimeNanos.getCount())
                + " ms");
    }

    private static void printStatistics(Get get) {
        DoubleStatistics readTimeNanos = get.getReadTimeNanos();
        System.out.println("get  >  avg: " + round(readTimeNanos.getAverage() / 1_000_000.0) + ", n: "
                + readTimeNanos.getCount() + ", std: " + round(readTimeNanos.getStandardDeviation() / 1_000_000.0)
                + ", min: " + readTimeNanos.getMin() / 1_000_000.0 + ", max: " + readTimeNanos.getMax() / 1_000_000.0);
    }
}
