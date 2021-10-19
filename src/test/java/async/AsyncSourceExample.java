package async;

import java.util.Objects;

public class AsyncSourceExample extends AbstractAsyncSource<Integer> {

    static final class Agent implements AsyncSourceAgent<Integer> {
        CancellationToken ct = null;

        @Override
        public void onNext(Integer e) {
            System.out.println(e);
            // the agent could also cancel itself
            if (e.intValue() == 200) {
                ct.cancel();
            }
        }

        @Override
        public void onCompleted() {
            System.out.println("Completed");
        }

        @Override
        public void onError(Throwable t) {
            System.out.println("Error: " + t.getClass().getName() + " " + t.getMessage());
        }

        @Override
        public void onInit(CancellationToken cancellationToken) {
            ct = Objects.requireNonNull(cancellationToken);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AsyncSourceAgent<Integer> agent = new Agent();
        AsyncSourceExample source = new AsyncSourceExample(5, 101, 250L);

        CancellationToken ct = source.forEach(agent);

        Thread.sleep(5_000L);
        ct.cancel();
        Thread.sleep(20_000L);

        System.out.println("done");
    }

    private final int start;
    private final int stop;
    private final long sleep;

    public AsyncSourceExample(int start, int stop, long sleep) {
        this.start = start;
        this.stop = stop;
        this.sleep = sleep;
    }

    @Override
    protected Runnable newRunnable(AsyncSourceAgent<Integer> agent, CancellationToken ct) {
        return new Producer(start, stop, agent, sleep, ct);
    }

    static final class Producer implements Runnable {

        int current;
        final int stop;
        final AsyncSourceAgent<Integer> agent;
        final CancellationToken ct;
        final long sleep;

        Producer(int start, int stop, AsyncSourceAgent<Integer> agent, long sleep, CancellationToken ct) {
            this.current = start;
            this.stop = stop;
            this.agent = agent;
            this.ct = ct;
            this.sleep = sleep;
        }

        @Override
        public void run() {
            try {
                while (current < stop && !ct.isCancellationRequested()) {
                    agent.onNext(current++);
                    if (!ct.isCancellationRequested()) {
                        try {
                            Thread.sleep(sleep);
                            System.out.println("-");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (Exception e) {
                agent.onError(e);
            }
            System.out.println("Thread exit: " + Thread.currentThread().getName());
        }
    }
}
