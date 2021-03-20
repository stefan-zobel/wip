package catenaq;

import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.schwefel.kv.ForEachKeyValue;
import org.schwefel.kv.KVStore;
import org.schwefel.kv.Kind;
import org.schwefel.kv.StoreOps;

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

        try (StoreOps store = new KVStore(Paths.get("D:/Temp/rocksdb_database"))) {
            Kueue queue = new Kueue(store, family);
            Kind id = store.getKindManagement().getKind(family);

            System.out.println("queue size: " + queue.size());
            AtomicInteger ai = new AtomicInteger();
            ForEachKeyValue it = store.scanAll(id);
            it.forEachRemaining(new BiConsumer<byte[], byte[]>() {
                @Override
                public void accept(byte[] t, byte[] u) {
                    ai.incrementAndGet();
                }
            });
            System.out.println("scan  size: " + ai.get());

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
