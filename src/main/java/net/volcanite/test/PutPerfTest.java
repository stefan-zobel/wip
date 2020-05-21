package net.volcanite.test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import net.volcanite.db.Put;
import net.volcanite.util.DoubleStatistics;

public class PutPerfTest {

    private static final Random rnd = new Random();

    private static final byte[] randomBytes() {
        int len = rnd.nextInt(400) + 1;
        byte[] b = new byte[len];
        rnd.nextBytes(b);
        return b;
    }

    public static void main(String[] args) {
        String dbPath = "D:\\Temp\\rocksdb_database";
        int RUNS = 500_000;
        long runtime = 0L;

        try (Put put = new Put(dbPath)) {
            for (int i = 0; i < RUNS; ++i) {
                long start = System.currentTimeMillis();
                byte[] key = randomBytes();
                byte[] value = randomBytes();

                put.write(key, value);
                runtime += (System.currentTimeMillis() - start);
            }

            printStatistics(put);
            System.out.println("runti>  avg: " + (runtime / (double) RUNS) + " ms");
        }
    }

    private static void printStatistics(Put put) {
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

    private static double round(double x) {
        return BigDecimal.valueOf(x).setScale(5, RoundingMode.HALF_EVEN).doubleValue();
    }
}
