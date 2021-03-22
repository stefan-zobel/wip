package catenaq;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

public class KueueThreadsTest {

    public static void main(String[] args) throws InterruptedException {
        final int MSG_COUNT = 500_000;
        final String family = "Test-DB";

        try (KueueManager km = new KueueManager(Paths.get("D:/Temp/rocksdb_database"))) {
            Kueue kueue = km.get(family);
            Producer p = new Producer(kueue, MSG_COUNT);
            Consumer c = new Consumer(kueue, MSG_COUNT);

            long start = System.currentTimeMillis();

            c.start();
            p.start();

            p.join();
            c.join();

            long end = System.currentTimeMillis();

            System.out.println("put & del took: " + (end - start) + " ms");
            System.out.println("average       : " + ((end - start) / (double) MSG_COUNT) + " ms / message");
            System.out.println("queue size    : " + kueue.size());
            System.out.println("done");
            System.out.flush();
        }
    }

    static final class Producer extends Thread {
        private final Kueue shared;
        private final Random rnd = new Random();

        private int count = 0;
        private final int max;

        Producer(Kueue shared, int max) {
            this.shared = shared;
            this.max = max;
        }

        @Override
        public void run() {
            while (!shared.isClosed() && count < max) {
                try {
                    shared.put(produceRandomData(count));
                    ++count;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        }

        byte[] produceRandomData(int counter) {
            int len = rnd.nextInt(400) + 1;
            int finalLen = len + 8;
            byte[] array = new byte[finalLen];
            int content = rnd.nextInt(127) + 1;
            Arrays.fill(array, (byte) content);
            writeInt(array, 0, finalLen);
            writeInt(array, 4, counter);
            return array;
        }
    }

    static final class Consumer extends Thread {
        private final Kueue shared;

        private int count = 0;
        private final int max;

        Consumer(Kueue shared, int max) {
            this.shared = shared;
            this.max = max;
        }

        static void sleepRandom() {
            // don't sleep for now
        }

//      @Override
//      public void run() {
//          while (!shared.isClosed() && count < max) {
//              if (shared.isEmpty()) {
//                  sleepRandom();
//              }
//              if (!shared.isEmpty()) {
//                  try {
//                      shared.take();
//                      ++count;
//                  } catch (Exception e) {
//                      e.printStackTrace();
//                      throw new RuntimeException(e);
//                  }
//              }
//          }
//          System.out.println("removed       : " + count + " messages");
//      }
        @Override
        public void run() {
            while (!shared.isClosed() && count < max) {
                try {
                    shared.take();
                    ++count;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
            System.out.println("removed       : " + count + " messages");
        }
    }

    /**
     * Stores an {@code int} in the {@code byte[]}.
     */
    static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >> 24);
        buffer[offset + 1] = (byte) (value >> 16);
        buffer[offset + 2] = (byte) (value >> 8);
        buffer[offset + 3] = (byte) value;
    }

    /** Reads an {@code int} from the {@code byte[]}. */
    static int readInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xff) << 24) + ((buffer[offset + 1] & 0xff) << 16)
                + ((buffer[offset + 2] & 0xff) << 8) + (buffer[offset + 3] & 0xff);
    }
}
