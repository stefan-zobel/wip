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
    private final String name;
    static final AtomicInteger threadNumber = new AtomicInteger(1);

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
                System.err.println(AsyncExecutor.class.getSimpleName() + " was stopped. " + tasksRemaining.size()
                        + " AsyncTasks haven't been processed.");
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
            timeoutMillis = (timeoutMillis < 31L) ? 31L : timeoutMillis;
            while (queue != null && queue.size() > 0 && elapsed < timeoutMillis) {
                try {
                    Thread.sleep(31L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                elapsed += 31L;
            }
            stop();
        }
        return elapsed;
    }

    public void execute(AsyncTask dbTask) {
        if (dbTask != null && isRunning()) {
            executor.execute(dbTask);
        }
    }

    private boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    private ExecutorService newExecutorService(int queueCapacity) {
        queue = new DiscardOverflowsQueue(queueCapacity);
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName((name.isEmpty() ? AsyncExecutor.class.getSimpleName() : name) + "-Thread-"
                        + threadNumber.getAndIncrement());
                return t;
            } // throw away overflow tasks!
        }, new ThreadPoolExecutor.DiscardPolicy());
    }
}
