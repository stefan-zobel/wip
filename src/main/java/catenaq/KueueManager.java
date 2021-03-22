package catenaq;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.schwefel.kv.KVStore;
import org.schwefel.kv.StoreOps;

public final class KueueManager implements AutoCloseable {

    private final StoreOps ops;
    private final Path dir;
    private final ConcurrentHashMap<String, Kueue> kueues = new ConcurrentHashMap<>();

    public KueueManager(Path directory) {
        ops = new KVStore(Objects.requireNonNull(directory));
        dir = directory;
    }

    public Kueue get(String identifier) {
        if (!isClosed()) {
            return kueues.computeIfAbsent(Objects.requireNonNull(identifier), id -> new Kueue(ops, id));
        }
        throw new IllegalStateException(KueueManager.class.getName() + " for " + getDirectory() + " is closed");
    }

    public Path getPath() {
        return dir;
    }

    public String getDirectory() {
        String directory = "???";
        try {
            directory = getPath().toFile().getCanonicalPath();
        } catch (IOException ignore) {
        }
        return directory;
    }

    public boolean isClosed() {
        return !ops.isOpen();
    }

    @Override
    public void close() {
        ops.close();
        kueues.clear();
    }
}
