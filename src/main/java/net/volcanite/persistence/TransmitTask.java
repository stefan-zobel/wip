package net.volcanite.persistence;

import net.volcanite.task.AsyncTask;

public final class TransmitTask implements AsyncTask {

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
                // TODO
                t.printStackTrace();
            }
        }
    }
}
