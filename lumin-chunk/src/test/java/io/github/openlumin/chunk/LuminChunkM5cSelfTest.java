package io.github.openlumin.chunk;

import io.github.openlumin.chunk.cull.LuminCullRequest;
import io.github.openlumin.chunk.cull.LuminCullResult;
import io.github.openlumin.chunk.cull.LuminOcclusionCuller;
import io.github.openlumin.chunk.cull.LuminSectionPos;
import io.github.openlumin.chunk.cull.LuminSectionVisibilityGraph;
import io.github.openlumin.chunk.pipeline.LuminChunkMesh;
import io.github.openlumin.chunk.pipeline.LuminChunkPipeline;
import io.github.openlumin.chunk.pipeline.LuminMeshAssembler;
import io.github.openlumin.chunk.schedule.LuminDeferMode;
import io.github.openlumin.chunk.store.LuminArenaAllocator;
import io.github.openlumin.chunk.store.LuminSectionAllocation;
import io.github.openlumin.chunk.vertex.LuminQuantizedVertexFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WP-1 M5c 自测：端到端 CPU 管线——量化网格装配 → 帧预算构建 → 在线完成回调 →
 * 槽位记账 → 剔除可见集 → 帧统计。聚合 M5b（含 M1..M5a）全量回归。
 */
public final class LuminChunkM5cSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M5c 全部测试节（先聚合 M5b 及以下回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM5bSelfTest.runAll();
        section("mesh assembler round trip", LuminChunkM5cSelfTest::testMeshAssembler);
        section("mesh close semantics", LuminChunkM5cSelfTest::testMeshClose);
        section("pipeline budgeted builds", LuminChunkM5cSelfTest::testPipelineBuilds);
        section("pipeline budget defers", LuminChunkM5cSelfTest::testBudgetDefers);
        section("pipeline cull integration", LuminChunkM5cSelfTest::testCullIntegration);
        section("pipeline evict accounting", LuminChunkM5cSelfTest::testEvictAccounting);
        if (failures > 0) {
            System.err.println("[lumin-chunk M5c] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-chunk M5c] ALL SELF TESTS PASSED (M1..M5c)");
        return failures;
    }

    private static void section(String name, Section body) {
        long start = System.nanoTime();
        try {
            body.run();
            System.out.printf("PASS %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
        } catch (Throwable t) {
            failures++;
            System.out.printf("FAIL %-34s (%.1f ms)%n", name, (System.nanoTime() - start) / 1e6);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 装配一个 2-quad 网格并校验量化布局。 */
    private static void testMeshAssembler() {
        LuminChunkMesh mesh = LuminChunkMesh.allocate(8, 12, 2, false);
        LuminMeshAssembler assembler = new LuminMeshAssembler(mesh);
        for (int quad = 0; quad < 2; quad++) {
            float z = quad * 4f;
            assembler.putQuad(
                    new float[]{0, 0, z, 16, 0, z, 16, 16, z, 0, 16, z},
                    new int[]{255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 0, 255},
                    new float[]{0, 0, 1, 0, 1, 1, 0, 1},
                    new int[]{200, 200, 200, 200, 200, 200, 200, 200},
                    quad, 3);
        }
        assembler.finish();
        check(assembler.writtenVertices() == 8 && assembler.writtenIndices() == 12, "assembler counts");
        check(mesh.vertexData().remaining() == 8 * LuminQuantizedVertexFormat.STRIDE_BYTES,
                "vertex bytes flipped");
        check(mesh.indexData().remaining() == 12 * 2, "short indices flipped");
        // 抽验第一个顶点的量化往返：位置 (0,0,0) → 归一化 0.25
        int firstWord = mesh.vertexData().getInt();
        check((firstWord & 0x3FF) == 256, "position hi word carries 0.25 normalized, got "
                + (firstWord & 0x3FF));
        check(mesh.isClosed() == false, "fresh mesh open");
        mesh.close();
        check(mesh.isClosed(), "closed after close()");
    }

    private static void testMeshClose() {
        LuminChunkMesh mesh = LuminChunkMesh.allocate(4, 6, 1, false);
        mesh.close();
        mesh.close(); // 幂等
        try {
            mesh.vertexData();
            check(false, "access after close must throw");
        } catch (IllegalStateException expected) {
        }
    }

    /** 一个 2-quad 的测试构建任务。 */
    private static LuminChunkTask<LuminChunkMesh> quadMeshTask(int sectionIndex) {
        return (context, cancel) -> {
            LuminChunkMesh mesh = LuminChunkMesh.allocate(8, 12, 2, false);
            LuminMeshAssembler assembler = new LuminMeshAssembler(mesh);
            for (int quad = 0; quad < 2; quad++) {
                float z = quad * 4f;
                assembler.putQuad(
                        new float[]{0, 0, z, 16, 0, z, 16, 16, z, 0, 16, z},
                        new int[]{255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 0, 255},
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1},
                        new int[]{200, 200, 200, 200, 200, 200, 200, 200},
                        quad, sectionIndex);
            }
            assembler.finish();
            return mesh;
        };
    }

    private static void testPipelineBuilds() throws Exception {
        try (LuminChunkPipeline pipeline = new LuminChunkPipeline(
                new LuminChunkBuilder(2), new LuminOcclusionCuller(openGraph()))) {
            AtomicInteger completed = new AtomicInteger();
            List<LuminChunkMesh> meshes = new ArrayList<>();
            pipeline.setMeshConsumer((section, mesh, allocation) -> {
                completed.incrementAndGet();
                synchronized (meshes) {
                    meshes.add(mesh);
                }
            });
            pipeline.beginFrame(16_000_000L, 50_000_000L, 1 << 20);
            List<LuminChunkJob<LuminChunkMesh>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                LuminSectionPos section = new LuminSectionPos(i, 0, 0);
                LuminChunkJob<LuminChunkMesh> job = pipeline.requestBuild(
                        section, 1024, LuminDeferMode.ALWAYS_FRAME, quadMeshTask(i));
                check(job != null, "first-frame budget must accept small builds, deferral at " + i);
                jobs.add(job);
            }
            for (LuminChunkJob<LuminChunkMesh> job : jobs) {
                check(job.await(15, TimeUnit.SECONDS) != null, "build completes");
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (completed.get() < 6 && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(2);
            }
            check(completed.get() == 6, "mesh consumer invoked for every build, got " + completed.get());
            synchronized (meshes) {
                check(meshes.size() == 6, "all meshes delivered");
                for (LuminChunkMesh mesh : meshes) {
                    check(mesh.vertexCount() == 8 && mesh.quadCount() == 2, "mesh shape preserved");
                    mesh.close();
                }
            }
            LuminChunkPipeline.FrameStats stats = pipeline.endFrame();
            check(stats.submitted() == 6, "stats count submissions");
            check(stats.deferredByBudget() == 0, "no deferrals expected with a generous budget");
            check(pipeline.inFlightBuildCount() == 0, "in-flight drained");
        }
    }

    private static void testBudgetDefers() {
        try (LuminChunkPipeline pipeline = new LuminChunkPipeline(
                new LuminChunkBuilder(1), new LuminOcclusionCuller(openGraph()))) {
            // 零上传/零时长预算：非阻塞档应全部被延后
            pipeline.beginFrame(16_000_000L, 1L, 1L);
            int deferred = 0;
            for (int i = 0; i < 4; i++) {
                LuminChunkJob<LuminChunkMesh> job = pipeline.requestBuild(
                        new LuminSectionPos(i, 0, 0), 1024,
                        LuminDeferMode.ALWAYS_FRAME, quadMeshTask(i));
                if (job == null) {
                    deferred++;
                }
            }
            check(deferred == 4, "exhausted budget defers everything, deferred=" + deferred);
            check(pipeline.submittedThisFrame() == 0, "nothing submitted");
            // ZERO_FRAMES 不受预算限制
            LuminChunkJob<LuminChunkMesh> urgent = pipeline.requestBuild(
                    new LuminSectionPos(9, 9, 9), 1024, LuminDeferMode.ZERO_FRAMES, quadMeshTask(9));
            check(urgent != null, "ZERO_FRAMES bypasses the frame budget");
            check(pipeline.submittedThisFrame() == 1, "one urgent submission counted");
            LuminChunkPipeline.FrameStats stats = pipeline.endFrame();
            check(stats.deferredByBudget() == 4, "deferrals reported in stats");
        }
    }

    private static void testCullIntegration() {
        try (LuminChunkPipeline pipeline = new LuminChunkPipeline(
                new LuminChunkBuilder(1), new LuminOcclusionCuller(openGraph()))) {
            List<LuminSectionPos> visible = new ArrayList<>();
            LuminCullResult result = pipeline.cull(
                    new LuminCullRequest(new LuminSectionPos(0, 0, 0), pos -> true, 2, true),
                    CancellationToken.create(),
                    visible::add);
            check(!result.isCancelled(), "not cancelled");
            check(visible.size() == 33, "radius-2 sphere = 33 sections, got " + visible.size());
            check(pipeline.allocatedSectionCount() == 0, "no allocations recorded yet");
            // 记账后可查
            LuminArenaAllocator allocator = new LuminArenaAllocator(4096);
            LuminSectionAllocation allocation = new LuminSectionAllocation(0, 160, 0, 24);
            pipeline.recordAllocation(new LuminSectionPos(1, 0, 0), allocation);
            check(pipeline.allocatedSectionCount() == 1, "allocation recorded");
        }
    }

    private static void testEvictAccounting() throws Exception {
        try (LuminChunkPipeline pipeline = new LuminChunkPipeline(
                new LuminChunkBuilder(1), new LuminOcclusionCuller(openGraph()))) {
            pipeline.setMeshConsumer((section, mesh, allocation) -> mesh.close());
            pipeline.beginFrame(16_000_000L, 50_000_000L, 1 << 20);
            LuminSectionPos target = new LuminSectionPos(0, 0, 0);
            pipeline.recordAllocation(target, new LuminSectionAllocation(0, 160, 0, 24));
            LuminChunkJob<LuminChunkMesh> job = pipeline.requestBuild(
                    target, 256, LuminDeferMode.ALWAYS_FRAME, quadMeshTask(0));
            check(job != null, "build submitted");
            // 在完成前淘汰：回调应发现未知 section 并自行关闭产出
            pipeline.evict(target);
            check(pipeline.allocatedSectionCount() == 0, "allocation cleared by evict");
            job.await(15, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (pipeline.inFlightBuildCount() != 0 && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(2);
            }
            check(pipeline.inFlightBuildCount() == 0, "orphaned completion cleaned up");
        }
    }

    /** 全开阔图（[-4,4]³ 全连通）。 */
    private static LuminSectionVisibilityGraph openGraph() {
        return new LuminSectionVisibilityGraph() {
            @Override
            public boolean exists(LuminSectionPos pos) {
                return Math.abs(pos.x()) <= 4 && Math.abs(pos.y()) <= 4 && Math.abs(pos.z()) <= 4;
            }

            @Override
            public long visibilityBits(LuminSectionPos pos) {
                return 0b111111;
            }
        };
    }

    private LuminChunkM5cSelfTest() {
    }
}
