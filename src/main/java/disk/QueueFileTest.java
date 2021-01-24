package disk;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Random;

public final class QueueFileTest {

    /**
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final int TELEGRAM_COUNT = 20_000;
        // don't overwrite old data with zeroes
        boolean overwriteWithZeros = false;
        QueueFile shared = new QueueFile.Builder(new File("shared_queue_file.bin")).build(overwriteWithZeros);

        Producer p = new Producer(shared, TELEGRAM_COUNT);
        Consumer c = new Consumer(shared, TELEGRAM_COUNT);

        long start = System.currentTimeMillis();

        p.start();
        c.start();

        p.join();
        c.join();

        long end = System.currentTimeMillis();

        shared.close();

        System.out.println("took: " + (end - start) + " ms");
        System.out.println("avg.: " + ((end - start) / (double) TELEGRAM_COUNT) + " ms");
        System.out.println("finished.");
        System.out.println(shared.toString());
        System.out.flush();
        Thread.sleep(31L);
    }

    static final class Producer extends Thread {
        private final QueueFile shared;
        private final Random rnd = new Random();

        private int count = 0;
        private final int max;

        Producer(QueueFile shared, int max) {
            this.shared = shared;
            this.max = max;
        }

        @Override
        public void run() {
            while (!shared.isClosed() && count <= max) {
                try {
                    shared.addMessage(produceRandomData(count));
                    ++count;
                } catch (Exception e) {
                    count = max + 1;
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        byte[] produceRandomData(int counter) {
            int len = rnd.nextInt(8192) + 1;
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
        private final QueueFile shared;

        private final MessageConsumer mc = new MessageConsumer() {
            private int cntAll = 0;
            private int cntGood = 0;

            @Override
            public void acceptMessage(byte[] message) throws Throwable {
                if (message == null) {
                    System.out.println(cntAll + ": received null");
                    ++cntAll;
                } else if (message.length == 0) {
                    System.out.println(cntAll + ": received 0 bytes");
                    ++cntAll;
                } else {
                    int len = message.length;
                    int expLen = readInt(message, 0);
                    if (expLen != len) {
                        throw new IllegalStateException(expLen + " != " + len);
                    }
                    int counter = readInt(message, 4);
                    byte content = message[8];
                    System.out.println(
                            cntGood + ": counter " + counter + " received " + (len - 8) + " bytes of " + content);
                    for (int i = 9; i < len; ++i) {
                        if (message[i] != content) {
                            throw new IllegalStateException(content + " != " + message[i]);
                        }
                    }
                    ++cntGood;
                    ++cntAll;
                }
            }
        };

        private int count = 0;
        private final int max;

        Consumer(QueueFile shared, int max) {
            this.shared = shared;
            this.max = max;
        }

        private void sleepRandom() {
            // don't sleep for now
        }

        @Override
        public void run() {
            while (!shared.isClosed() && count <= max) {
                if (shared.isEmpty()) {
                    sleepRandom();
                }
                if (!shared.isEmpty()) {
                    try {
                        shared.removeNextMessage(mc);
                        ++count;
                    } catch (Exception e) {
                        count = max + 1;
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * Stores an {@code int} in the {@code byte[]}. The behavior is equivalent
     * to calling {@link RandomAccessFile#writeInt}.
     */
    static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >> 24);
        buffer[offset + 1] = (byte) (value >> 16);
        buffer[offset + 2] = (byte) (value >> 8);
        buffer[offset + 3] = (byte) value;
    }

    /** Reads an {@code int} from the {@code byte[]}. */
    //@formatter:off
    static int readInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xff) << 24)
                + ((buffer[offset + 1] & 0xff) << 16)
                + ((buffer[offset + 2] & 0xff) << 8)
                + (buffer[offset + 3] & 0xff);
    }
    //@formatter:on
}
