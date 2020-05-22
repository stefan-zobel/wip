package net.volcanite.test;

import static net.volcanite.test.TestUtil.randomBytes;

import net.volcanite.persistence.RocksDBTransfer;
import net.volcanite.persistence.TransmitTask;
import net.volcanite.task.AsyncExecutor;

public class AsyncExample {

    public static void main(String[] args) {

        AsyncExecutor executor = new AsyncExecutor();
        executor.start();

        int RUNS = 500_000;
        long runtime = 0L;

        for (int i = 0; i < RUNS; ++i) {
            long start = System.currentTimeMillis();
            byte[] key = randomBytes();
            byte[] value = randomBytes();

            executor.execute(new TransmitTask(key, value));
            runtime += (System.currentTimeMillis() - start);
        }

        System.out.println("runti>  avg   :  " + (runtime / (double) RUNS) + " ms");
        System.out.println("shutdown took :  " + executor.stop(15_000L) + " ms");
        RocksDBTransfer.shutdown();
    }
}
