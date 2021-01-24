package disk;

import java.io.IOException;

@SuppressWarnings("serial")
public final class ClosedQueueFileException extends IOException {

    public ClosedQueueFileException(String message) {
        super(message);
    }
}
