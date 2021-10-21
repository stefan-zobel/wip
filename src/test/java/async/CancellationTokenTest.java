package async;

import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.TestCase;

public class CancellationTokenTest extends TestCase {

    public void testCancellation() {
        AtomicBoolean cancellationCallbackWasRun = new AtomicBoolean();
        CancellationToken ct = new CancellationToken();
        ct.registerOnCancel(() -> {
            cancellationCallbackWasRun.set(true);
            System.out.println("Cancellation Runnable got called");
        });
        ct.cancel();
        assertTrue("Failed to cancel", ct.isCancellationRequested());
        assertTrue("Callback was not called", cancellationCallbackWasRun.get());
    }
}
