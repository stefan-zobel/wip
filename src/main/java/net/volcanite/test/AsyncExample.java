package net.volcanite.test;

import static net.volcanite.test.TestUtil.randomBytes;

import net.volcanite.persistence.RocksDBTransfer;
import net.volcanite.persistence.TransmitTask;
import net.volcanite.task.AsyncExecutor;

public class AsyncExample {

    public static void main(String[] args) {

        AsyncExecutor.start();
        int RUNS = 500_000;
        long runtime = 0L;

        for (int i = 0; i < RUNS; ++i) {
            long start = System.currentTimeMillis();
            byte[] key = randomBytes();
            byte[] value = randomBytes();

            AsyncExecutor.execute(new TransmitTask(key, value));
            runtime += (System.currentTimeMillis() - start);
        }

        System.out.println("runti>  avg: " + (runtime / (double) RUNS) + " ms");
        pause(15);
        AsyncExecutor.stop();
        RocksDBTransfer.shutdown();
    }

    private static void pause(int seconds) {
        Object o = new Object();
        try {
            synchronized (o) {
                System.err.println("waiting");
                o.wait(1000L * seconds);
                System.err.println("done");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
