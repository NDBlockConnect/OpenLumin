package io.github.openlumin.chunk;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * 区块构建执行器：N 个 Worker 线程 + 类型化任务队列 + 任务窃取 + 忙碌度反馈。
 * <p>调度语义：</p>
 * <ul>
 *   <li>important 任务进入全局优先队列，任何 Worker 取任务时最先消费；</li>
 *   <li>普通任务轮转派发到 Worker 本地队列（FIFO）；本地空闲时从其他 Worker
 *       队列<b>尾部</b>窃取（优先消化最早提交的任务）；</li>
 *   <li>排队中即被取消的任务在出队时被跳过，{@code build} 不会被调用；</li>
 *   <li>构建完成后才被取消的任务：产出由构建器关闭，结果仍报告 CANCELLED；</li>
 *   <li>consumer 在 Worker 线程回调、且在完成信号（isDone/await）之后异步执行——
 *       await 返回不保证 consumer 已返回；其抛出的异常被隔离，不影响 Worker 存活；</li>
 *   <li>{@link #isQueueEmpty()} 为 true 表示无排队/待取任务，但不代表所有任务已执行完毕，
 *       完成判定请用 {@link LuminChunkJob#await}。</li>
 * </ul>
 * <p>架构参照 Sodium ChunkBuilder 的执行器模型（仅语义参照，零代码移植）。</p>
 */
public final class LuminChunkBuilder implements AutoCloseable {

    private static final long IDLE_PARK_NANOS = 250_000L;
    private static final long TERMINATION_TIMEOUT_SECONDS = 30L;

    private static final LuminBuildContext BUILD_CONTEXT = new LuminBuildContext();

    private final Worker[] workers;
    private final CountDownLatch termination;
    private final ConcurrentLinkedDeque<Job<?>> importantQueue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger roundRobin = new AtomicInteger(0);
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final BusyTracker busyTracker = new BusyTracker();
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private volatile boolean shutdown = false;

    public LuminChunkBuilder(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1, got " + threads);
        }
        this.workers = new Worker[threads];
        this.termination = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            this.workers[i] = new Worker();
        }
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(this.workers[i], "lumin-chunk-worker-" + i);
            thread.setDaemon(true);
            thread.start();
        }
    }

    public <O extends LuminBuildOutput> LuminChunkJob<O> scheduleTask(
            LuminChunkTask<O> task, boolean important, Consumer<LuminChunkJobResult<O>> consumer) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (shutdown) {
            throw new IllegalStateException("builder is shut down");
        }
        Job<O> job = new Job<>(task, consumer);
        pendingTasks.incrementAndGet();
        if (important) {
            importantQueue.addLast(job);
        } else {
            workers[Math.floorMod(roundRobin.getAndIncrement(), workers.length)].queue.addLast(job);
        }
        return job;
    }

    public boolean isQueueEmpty() {
        return pendingTasks.get() == 0;
    }

    /**
     * 忙碌度节流依据：最近 {@code frameDuration} 纳秒窗口内，全部 Worker 的
     * 忙碌线程时间占总预算（窗口 × 线程数）的比例，恒在 [0, 1]。
     */
    public float getBusyFraction(long frameDuration) {
        if (frameDuration <= 0) {
            throw new IllegalArgumentException("frameDuration must be > 0, got " + frameDuration);
        }
        return busyTracker.fraction(System.nanoTime(), frameDuration, workers.length);
    }

    /** 优雅停机：不再接收新任务，已入队任务全部执行完毕后 Worker 退出（drain）。 */
    public void shutdown() {
        shutdown = true;
    }

    /** {@link #shutdown()} 并阻塞等待全部 Worker 终止；幂等。 */
    @Override
    public void close() {
        if (terminated.get()) {
            return;
        }
        shutdown = true;
        try {
            if (!termination.await(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "lumin-chunk workers did not terminate within " + TERMINATION_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for worker termination", e);
        }
        terminated.set(true);
    }

    private Job<?> pollNext(Worker self) {
        Job<?> job = importantQueue.pollFirst();
        if (job == null) {
            job = self.queue.pollFirst();
        }
        if (job == null) {
            for (Worker worker : workers) {
                if (worker == self) {
                    continue;
                }
                job = worker.queue.pollLast();
                if (job != null) {
                    break;
                }
            }
        }
        if (job != null) {
            pendingTasks.decrementAndGet();
        }
        return job;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void execute(Job<?> job) {
        LuminChunkJobResult<?> result;
        long start = System.nanoTime();
        try {
            if (job.isCancelled()) {
                result = LuminChunkJobResult.cancelled();
            } else {
                result = runTask(job);
            }
        } finally {
            busyTracker.record(start, System.nanoTime());
        }
        Job raw = job;
        raw.finish(result);
        runConsumer(raw.consumer, result);
    }

    private <O extends LuminBuildOutput> LuminChunkJobResult<O> runTask(Job<O> job) {
        O output;
        try {
            output = job.task.build(BUILD_CONTEXT, job.token);
        } catch (Throwable t) {
            return LuminChunkJobResult.failed(t);
        }
        if (job.isCancelled()) {
            closeQuietly(output);
            return LuminChunkJobResult.cancelled();
        }
        return LuminChunkJobResult.completed(output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void runConsumer(Consumer<?> consumer, LuminChunkJobResult<?> result) {
        if (consumer == null) {
            return;
        }
        try {
            ((Consumer) consumer).accept(result);
        } catch (Throwable t) {
            System.err.println("[lumin-chunk] job consumer threw: " + t);
        }
    }

    private static void closeQuietly(LuminBuildOutput output) {
        if (output == null) {
            return;
        }
        try {
            output.close();
        } catch (Throwable ignored) {
        }
    }

    private final class Worker implements Runnable {

        private final ConcurrentLinkedDeque<Job<?>> queue = new ConcurrentLinkedDeque<>();

        @Override
        public void run() {
            try {
                while (true) {
                    Job<?> job = pollNext(this);
                    if (job != null) {
                        execute(job);
                        continue;
                    }
                    if (shutdown) {
                        break;
                    }
                    LockSupport.parkNanos(IDLE_PARK_NANOS);
                }
            } finally {
                termination.countDown();
            }
        }
    }

    private static final class Job<O extends LuminBuildOutput> implements LuminChunkJob<O> {

        private final LuminChunkTask<O> task;
        private final Consumer<LuminChunkJobResult<O>> consumer;
        private final CancellationToken.Token token = new CancellationToken.Token();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile LuminChunkJobResult<O> result;

        Job(LuminChunkTask<O> task, Consumer<LuminChunkJobResult<O>> consumer) {
            this.task = task;
            this.consumer = consumer;
        }

        @Override
        public void cancel() {
            token.cancel();
        }

        @Override
        public boolean isCancelled() {
            return token.isCancelled();
        }

        @Override
        public boolean isDone() {
            return done.getCount() == 0;
        }

        @Override
        public LuminChunkJobResult<O> await(long timeout, TimeUnit unit) throws InterruptedException {
            if (!done.await(timeout, unit)) {
                return null;
            }
            return result;
        }

        void finish(LuminChunkJobResult<O> value) {
            this.result = value;
            done.countDown();
        }
    }
}
