package net.volcanite.task;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.volcanite.util.DiscardOverflowsQueue;

/**
 * A dedicated "fixed ThreadPool" Executor for asynchronous operations.
 */
public final class AsyncExecutor {

    private volatile ExecutorService executor = null;
    private volatile int queueCapacityDefault = DiscardOverflowsQueue.DEFAULT_MAX_CAPACITY;
    private volatile BlockingQueue<Runnable> queue = null;
    volatile String name;
    private static final AtomicInteger threadNumber = new AtomicInteger(1);

    public AsyncExecutor() {
        this("");
    }

    public AsyncExecutor(String name) {
        this.name = (name == null || name.isEmpty()) ? "" : name.trim();
    }

    /**
     * Start the AsyncExecutor with a default queue capacity. This method has no
     * effect if the executor is already running.
     */
    public void start() {
        if (executor == null || !isRunning()) {
            executor = newExecutorService(queueCapacityDefault);
        }
    }

    /**
     * Start the AsyncExecutor with the given queue capacity. This method has no
     * effect if the executor is already running with the correct queue
     * capacity.
     * 
     * @param queueCapacity
     *            maximum capacity for the queue.
     */
    public void start(int queueCapacity) {
        // stop the executor if it is running with the wrong capacity
        if (isRunning() && queueCapacity != queueCapacityDefault) {
            stop();
        }
        queueCapacityDefault = queueCapacity;
        start();
    }

    /**
     * Stop the AsyncExecutor and discard any pending tasks in the queue. This
     * method has no effect if the executor is already stopped.
     */
    public void stop() {
        if (isRunning()) {
            List<Runnable> tasksRemaining = executor.shutdownNow();
            if (tasksRemaining != null && tasksRemaining.size() > 0) {
                // TODO
                System.err.println(
                        name + " was stopped. " + tasksRemaining.size() + " AsyncTasks haven't been processed.");
            }
        }
        executor = null;
        queue = null;
    }

    /**
     * Stop the AsyncExecutor within {@code timeoutMillis} milliseconds and
     * discard any pending tasks in the queue. This method has no effect if the
     * executor is already stopped.
     * 
     * @param timeoutMillis
     *            the maximum time to wait for shutdown (in milliseconds)
     * @return the number of milliseconds it actually took to shutdown the
     *         executor
     */
    public long stop(long timeoutMillis) {
        long elapsed = 0L;
        if (isRunning()) {
            // don't wait less than 31 milliseconds
            timeoutMillis = (timeoutMillis < 31L) ? 31L : timeoutMillis;
            long lastTime = System.nanoTime();
            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (queue != null && queue.size() > 0) {
                // compute remaining timeout
                long now = System.nanoTime();
                nanosTimeout -= (now - lastTime);
                lastTime = now;
                // truncate down to milliseconds
                long millisRemaining = TimeUnit.NANOSECONDS.toMillis(nanosTimeout);
                if (millisRemaining > 0L) {
                    millisRemaining = (millisRemaining > 16L) ? 16L : millisRemaining;
                    try {
                        Thread.sleep(millisRemaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    elapsed += ((System.nanoTime() - lastTime) / 1_000_000);
                } else {
                    break;
                }
            }
            stop();
        }
        return elapsed;
    }

    public void execute(AsyncTask task) {
        if (task != null && isRunning()) {
            executor.execute(task);
        }
    }

    private boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    public String getName() {
        return name;
    }

    private ExecutorService newExecutorService(int queueCapacity) {
        queue = new DiscardOverflowsQueue(queueCapacity);
        name = (name.isEmpty() ? AsyncExecutor.class.getSimpleName() : name) + "-Thread-"
                + threadNumber.getAndIncrement();
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName(name);
                return t;
            }
            // Overflow Tasks wegwerfen! (CallerRuns wäre fatal wenn der Caller
            // Thread bereits eine Tx hat!)
        }, new ThreadPoolExecutor.DiscardPolicy());
    }
}
