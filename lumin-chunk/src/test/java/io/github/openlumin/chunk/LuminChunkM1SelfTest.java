package io.github.openlumin.chunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WP-1 M1 纯 CPU 自测（零依赖运行器，Gradle selfTest / java 直接执行均可）。
 * 覆盖：网格生成正确性、批量派发与窃取、important 优先、取消语义（排队/构建中）、
 * 失败隔离、BusyTracker 数学、忙碌度反馈、队列空判与停机排空。
 */
public final class LuminChunkM1SelfTest {

    private static final long TIMEOUT_SECONDS = 15;
    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M1 全部测试节，返回失败节数（可被 M2 聚合运行器复用）。 */
    public static int runAll() {
        failures = 0;
        section("mesh correctness", LuminChunkM1SelfTest::testMeshCorrectness);
        section("fan-out completion", LuminChunkM1SelfTest::testFanOutCompletion);
        section("important priority", LuminChunkM1SelfTest::testImportantPriority);
        section("cancel before run", LuminChunkM1SelfTest::testCancelBeforeRun);
        section("cancel during build", LuminChunkM1SelfTest::testCancelDuringBuild);
        section("failure isolation", LuminChunkM1SelfTest::testFailureIsolation);
        section("busy tracker math", LuminChunkM1SelfTest::testBusyTrackerMath);
        section("busy fraction range", LuminChunkM1SelfTest::testBusyFractionRange);
        section("queue empty and shutdown", LuminChunkM1SelfTest::testQueueEmptyAndShutdown);
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-26s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-26s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * consumer 回调在完成信号之后于 Worker 线程异步执行，
     * await 返回不保证 consumer 已返回——轮询等待其落地。
     */
    private static void awaitConsumerCount(List<?> consumed, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (consumed.size() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(5);
        }
        check(consumed.size() == expected,
                "consumer invocations=" + consumed.size() + ", expected " + expected);
    }

    static final class TestMesh implements LuminBuildOutput {

        final float[] vertices;
        final int[] indices;
        boolean closed = false;
        private int closeCalls = 0;

        TestMesh(float[] vertices, int[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }

        @Override
        public void close() {
            closeCalls++;
            closed = true;
        }

        int closeCalls() {
            return closeCalls;
        }
    }

    private static TestMesh quadMesh(float x0, float y0, float x1, float y1, float z) {
        return new TestMesh(
                new float[]{x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z},
                new int[]{0, 1, 2, 0, 2, 3});
    }

    private static void testMeshCorrectness() throws Exception {
        float[] expectedVertices = {0f, 0f, -1f, 1f, 0f, -1f, 1f, 1f, -1f, 0f, 1f, -1f};
        int[] expectedIndices = {0, 1, 2, 0, 2, 3};
        List<LuminChunkJobResult<TestMesh>> consumed = new CopyOnWriteArrayList<>();
        try (LuminChunkBuilder builder = new LuminChunkBuilder(1)) {
            LuminChunkJob<TestMesh> job = builder.scheduleTask(
                    (context, cancel) -> quadMesh(0f, 0f, 1f, 1f, -1f),
                    false,
                    consumed::add);
            LuminChunkJobResult<TestMesh> result = job.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(result != null, "await timed out");
            check(result.state() == LuminChunkJobResult.State.COMPLETED, "state=" + result.state());
            check(Arrays.equals(result.output().vertices, expectedVertices), "vertex data mismatch");
            check(Arrays.equals(result.output().indices, expectedIndices), "index data mismatch");
            check(!result.output().closed, "output must not be closed on normal completion");
            awaitConsumerCount(consumed, 1);
            check(consumed.get(0) == result, "consumer must receive the same result");
        }
    }

    private static void testFanOutCompletion() throws Exception {
        final int count = 200;
        AtomicInteger consumed = new AtomicInteger(0);
        List<LuminChunkJob<TestMesh>> jobs = new ArrayList<>(count);
        int[] expectedSizes = new int[count];
        try (LuminChunkBuilder builder = new LuminChunkBuilder(4)) {
            for (int i = 0; i < count; i++) {
                expectedSizes[i] = (i + 1) * 3;
                final int vertexCount = expectedSizes[i];
                jobs.add(builder.scheduleTask(
                        (context, cancel) -> new TestMesh(new float[vertexCount], new int[0]),
                        false,
                        result -> consumed.incrementAndGet()));
            }
            for (int i = 0; i < count; i++) {
                LuminChunkJobResult<TestMesh> result = jobs.get(i).await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                check(result != null, "job " + i + " timed out");
                check(result.state() == LuminChunkJobResult.State.COMPLETED,
                        "job " + i + " state=" + result.state());
                check(result.output().vertices.length == expectedSizes[i],
                        "job " + i + " payload mismatch");
            }
            check(consumed.get() == count, "consumed=" + consumed.get());
            check(builder.isQueueEmpty(), "queue must be empty after all jobs awaited");
        }
    }

    private static void testImportantPriority() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        try (LuminChunkBuilder builder = new LuminChunkBuilder(1)) {
            builder.scheduleTask((context, cancel) -> {
                blockerStarted.countDown();
                try {
                    releaseBlocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return quadMesh(0, 0, 1, 1, 0);
            }, false, result -> order.add("blocker"));
            check(blockerStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "blocker never started");
            builder.scheduleTask((context, cancel) -> quadMesh(0, 0, 1, 1, 0),
                    false, result -> order.add("normal-1"));
            builder.scheduleTask((context, cancel) -> quadMesh(0, 0, 1, 1, 0),
                    true, result -> order.add("important"));
            builder.scheduleTask((context, cancel) -> quadMesh(0, 0, 1, 1, 0),
                    false, result -> order.add("normal-2"));
            releaseBlocker.countDown();
            check(pollOrderStable(order, 4, TIMEOUT_SECONDS), "did not observe 4 completions in time: " + order);
            check(order.get(0).equals("blocker"), "unexpected first completion: " + order);
            check(order.indexOf("important") < order.indexOf("normal-1")
                    && order.indexOf("important") < order.indexOf("normal-2"),
                    "important task did not run before queued normals: " + order);
        }
    }

    private static boolean pollOrderStable(List<String> order, int expected, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (order.size() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return order.size() == expected;
    }

    private static void testCancelBeforeRun() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        List<LuminChunkJobResult<TestMesh>> consumed = new CopyOnWriteArrayList<>();
        try (LuminChunkBuilder builder = new LuminChunkBuilder(1)) {
            builder.scheduleTask((context, cancel) -> {
                blockerStarted.countDown();
                try {
                    releaseBlocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return quadMesh(0, 0, 1, 1, 0);
            }, false, null);
            check(blockerStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "blocker never started");
            AtomicBoolean built = new AtomicBoolean(false);
            LuminChunkJob<TestMesh> job = builder.scheduleTask((context, cancel) -> {
                built.set(true);
                return quadMesh(0, 0, 1, 1, 0);
            }, false, consumed::add);
            job.cancel();
            releaseBlocker.countDown();
            LuminChunkJobResult<TestMesh> result = job.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(result != null, "await timed out");
            check(result.state() == LuminChunkJobResult.State.CANCELLED, "state=" + result.state());
            check(!built.get(), "build must not run for a job cancelled while queued");
            check(result.output() == null, "cancelled result must not carry output");
            awaitConsumerCount(consumed, 1);
            check(consumed.get(0).state() == LuminChunkJobResult.State.CANCELLED,
                    "consumer must observe the CANCELLED result");
        }
    }

    private static void testCancelDuringBuild() throws Exception {
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch finishBuild = new CountDownLatch(1);
        try (LuminChunkBuilder builder = new LuminChunkBuilder(1)) {
            LuminChunkJob<TestMesh> job = builder.scheduleTask((context, cancel) -> {
                buildStarted.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                while (!cancel.isCancelled()) {
                    try {
                        if (finishBuild.await(10, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                }
                return quadMesh(0, 0, 1, 1, 0);
            }, false, null);
            check(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "task never started");
            job.cancel();
            finishBuild.countDown();
            LuminChunkJobResult<TestMesh> result = job.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(result != null, "await timed out");
            check(result.state() == LuminChunkJobResult.State.CANCELLED, "state=" + result.state());
            check(result.output() == null, "cancelled result must not carry output");
            check(job.isDone(), "job must be done");
        }
    }

    private static void testFailureIsolation() throws Exception {
        RuntimeException boom = new RuntimeException("boom");
        List<LuminChunkJobResult<TestMesh>> consumed = new CopyOnWriteArrayList<>();
        try (LuminChunkBuilder builder = new LuminChunkBuilder(1)) {
            LuminChunkJob<TestMesh> failed = builder.scheduleTask((context, cancel) -> {
                throw boom;
            }, false, consumed::add);
            LuminChunkJobResult<TestMesh> failureResult = failed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(failureResult != null, "await timed out");
            check(failureResult.state() == LuminChunkJobResult.State.FAILED,
                    "state=" + failureResult.state());
            check(failureResult.failure() == boom, "failure cause must be preserved");
            LuminChunkJob<TestMesh> next = builder.scheduleTask(
                    (context, cancel) -> quadMesh(0, 0, 1, 1, 0), false, consumed::add);
            LuminChunkJobResult<TestMesh> nextResult = next.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(nextResult != null
                    && nextResult.state() == LuminChunkJobResult.State.COMPLETED,
                    "builder must survive task failure");
            awaitConsumerCount(consumed, 2);
            check(consumed.get(0).state() == LuminChunkJobResult.State.FAILED
                    && consumed.get(0).failure() == boom,
                    "first consumer result must carry the failure");
            check(consumed.get(1).state() == LuminChunkJobResult.State.COMPLETED,
                    "second consumer result must be COMPLETED");
        }
    }

    private static void testBusyTrackerMath() {
        BusyTracker tracker = new BusyTracker();
        long now = System.nanoTime();
        long window = TimeUnit.MILLISECONDS.toNanos(100);
        check(tracker.fraction(now, window, 1) == 0f, "empty tracker must report 0");
        tracker.record(now - TimeUnit.MILLISECONDS.toNanos(10), now);
        assertNear(tracker.fraction(now, window, 1), 0.10f, "single 10ms interval in 100ms window");
        tracker.record(now - TimeUnit.MILLISECONDS.toNanos(150), now - TimeUnit.MILLISECONDS.toNanos(90));
        assertNear(tracker.fraction(now, window, 1), 0.20f, "clamped old interval must contribute 10ms");
        assertNear(tracker.fraction(now, window, 2), 0.10f, "thread budget must double the denominator");
        assertNear(tracker.fraction(now, window, 1), 0.20f, "repeated query must be stable (prune idempotent)");
        long future = now + TimeUnit.MILLISECONDS.toNanos(100);
        assertNear(tracker.fraction(future, window, 1), 0.0f, "fully expired window must report 0");
    }

    private static void assertNear(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 1e-4f, message + " (actual=" + actual + ")");
    }

    private static void testBusyFractionRange() throws Exception {
        try (LuminChunkBuilder builder = new LuminChunkBuilder(2)) {
            check(builder.getBusyFraction(TimeUnit.MILLISECONDS.toNanos(100)) >= 0f,
                    "busy fraction must be non-negative");
            List<LuminChunkJob<TestMesh>> jobs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                jobs.add(builder.scheduleTask(
                        (context, cancel) -> quadMesh(0, 0, 1, 1, 0), false, null));
            }
            float fraction = builder.getBusyFraction(TimeUnit.MILLISECONDS.toNanos(1000));
            check(fraction >= 0f && fraction <= 1f, "busy fraction out of range: " + fraction);
            for (LuminChunkJob<TestMesh> job : jobs) {
                check(job.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null, "await timed out");
            }
        }
    }

    private static void testQueueEmptyAndShutdown() throws Exception {
        LuminChunkBuilder builder = new LuminChunkBuilder(2);
        check(builder.isQueueEmpty(), "fresh builder must have an empty queue");
        List<LuminChunkJob<TestMesh>> jobs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            jobs.add(builder.scheduleTask((context, cancel) -> quadMesh(0, 0, 1, 1, 0), false, null));
        }
        for (LuminChunkJob<TestMesh> job : jobs) {
            check(job.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) != null, "await timed out");
        }
        check(builder.isQueueEmpty(), "queue must be empty after all jobs complete");
        builder.shutdown();
        try {
            builder.scheduleTask((context, cancel) -> quadMesh(0, 0, 1, 1, 0), false, null);
            check(false, "scheduleTask after shutdown must throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
        builder.close();
        builder.close();
    }

    private LuminChunkM1SelfTest() {
    }
}
