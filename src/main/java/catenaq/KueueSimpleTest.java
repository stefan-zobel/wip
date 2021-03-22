package catenaq;

import java.nio.file.Paths;
import java.util.Random;

public class KueueSimpleTest {

    private static final Random rnd = new Random();

    private static final byte[] randomBytes() {
        int len = rnd.nextInt(400) + 1;
        byte[] b = new byte[len];
        rnd.nextBytes(b);
        return b;
    }

    public static void main(String[] args) {

        final int RUNS = 500_000;
        final String family = "Test-DB";

        try (KueueManager km = new KueueManager(Paths.get("D:/Temp/rocksdb_database"))) {
            Kueue queue = km.get(family);

            System.out.println("queue size: " + queue.size());

            long start = System.currentTimeMillis();
            for (int i = 1; i <= RUNS; ++i) {
                byte[] value = randomBytes();
                queue.put(value);
            }
            long end = System.currentTimeMillis();
            System.out.println("put took  : " + ((end - start) / (double) queue.size()) + " ms / message");

            System.out.println("queue size: " + queue.size());
            long count = queue.size();
            start = System.currentTimeMillis();
            while (queue.size() > 0L) {
                queue.take();
            }
            end = System.currentTimeMillis();
            System.out.println("queue size: " + queue.size());
            System.out.println("del took  : " + ((end - start) / (double) count) + " ms / message");

            System.out.println("done");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
