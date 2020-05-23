package net.volcanite.persistence;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.volcanite.task.AsyncTask;

public final class TransmitTask implements AsyncTask {

    private static final Logger logger = Logger.getLogger(AsyncTask.class.getName());

    private final byte[] key;
    private final byte[] value;

    public TransmitTask(byte[] key, byte[] value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public void run() {
        byte[] key = this.key;
        if (key != null && key.length > 0) {
            try {
                RocksDBTransfer.transmit(key, value);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "", t);
            }
        }
    }
}
