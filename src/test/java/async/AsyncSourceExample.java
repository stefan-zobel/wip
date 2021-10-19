package async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class AsyncSourceExample implements AsyncSource<Integer> {

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
            System.out.println("Error: " + t.getClass().getName());
        }

        @Override
        public void onInit(CancellationToken cancellationToken) {
            ct = cancellationToken;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AsyncSourceAgent<Integer> agent = new Agent();
        AsyncSourceExample source = new AsyncSourceExample();

        CancellationToken ct = source.forEach(agent);

        Thread.sleep(5_000L);
        ct.cancel();
        Thread.sleep(20_000L);

        System.out.println("done");
    }

    @Override
    public CancellationToken forEach(AsyncSourceAgent<Integer> agent) {
        CancellationToken token = new CancellationToken();
        agent.onInit(token);

        // handle unlikely case that the agent cancels in onInit()
        if (token.isCancellationRequested()) {
            agent.onError(new CancellationException("onInit"));
            return token;
        }

        CompletableFuture<Void> future = enumerateSource(agent, token);
        token.registerOnCancel(() -> {
            future.cancel(true); // this won't interrupt threads!
        });

        future.whenCompleteAsync((ignored, throwable) -> {
            if (throwable != null) {
                agent.onError(throwable);
            } else {
                agent.onCompleted();
            }
        });

        return token;
    }

    private CompletableFuture<Void> enumerateSource(AsyncSourceAgent<Integer> agent, CancellationToken ct) {
        return CompletableFuture.runAsync(new Producer(5, 101, agent, 250L, ct)).thenCompose(stage -> {
            if (!ct.isCancellationRequested()) {
                return CompletableFuture.completedFuture(null);
            } else {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new CancellationException("afterCompletion"));
                return future;
            }
        });
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
