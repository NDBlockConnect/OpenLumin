package io.github.openlumin.chunk;

import io.github.openlumin.chunk.vertex.LuminQuantizedVertexFormat;
import io.github.openlumin.chunk.vertex.LuminVertexReader;
import io.github.openlumin.chunk.vertex.LuminVertexWriter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WP-1 M4b 自测：量化顶点格式（20 字节布局、往返精度、钳制、UV 质心渗色偏置、字节布局）。
 * 聚合 M4a（含 M3/M2/M1）全量回归。
 */
public final class LuminChunkM4bSelfTest {

    private static int failures = 0;

    @FunctionalInterface
    private interface Section {
        void run() throws Exception;
    }

    public static void main(String[] args) {
        System.exit(runAll() == 0 ? 0 : 1);
    }

    /** 运行 M4b 全部测试节（先聚合 M4a+M3+M2+M1 回归），返回失败节数。 */
    public static int runAll() {
        failures = LuminChunkM4SelfTest.runAll();
        section("vertex round trip precision", LuminChunkM4bSelfTest::testRoundTripPrecision);
        section("vertex clamp semantics", LuminChunkM4bSelfTest::testClampSemantics);
        section("vertex centroid bias", LuminChunkM4bSelfTest::testCentroidBias);
        section("vertex byte layout", LuminChunkM4bSelfTest::testByteLayout);
        section("vertex stride and count", LuminChunkM4bSelfTest::testStride);
        if (failures > 0) {
            System.err.println("[lumin-chunk M4b] " + failures + " section(s) FAILED");
            System.exit(1);
        }
        System.out.println("[lumin-chunk M4b] ALL SELF TESTS PASSED (M1..M4b)");
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

    private static LuminVertexWriter writer(ByteBuffer buffer) {
        LuminVertexWriter writer = new LuminVertexWriter(buffer);
        // 单顶点测试无需真实质心：关闭自动偏置判定
        writer.beginQuad(0, 0, 0, 0, 0, 0, 0, 0);
        return writer;
    }

    private static void testRoundTripPrecision() {
        ByteBuffer buffer = ByteBuffer.allocate(8 * LuminQuantizedVertexFormat.STRIDE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        LuminVertexWriter writer = writer(buffer);

        // 位置：0、+16、-7.999、8（section 局部坐标边界附近值）
        float[] positions = {0f, 16f, -7.999f, 8f};
        for (float pos : positions) {
            writer.putVertex(pos, pos, pos, 255, 255, 255, 255,
                    0.5f, 0.5f, 128, 128, 1, 3);
        }
        LuminVertexReader reader = new LuminVertexReader((ByteBuffer) buffer.flip());
        for (float expected : positions) {
            LuminVertexReader.DecodedVertex decoded = reader.getVertex();
            check(Math.abs(decoded.x() - expected) < 0.001f,
                    "x round trip: expected " + expected + " got " + decoded.x());
            check(Math.abs(decoded.y() - expected) < 0.001f, "y round trip");
            check(Math.abs(decoded.z() - expected) < 0.001f, "z round trip");
        }

        // UV 精度：15 位量化 → 精度约 1/32768
        buffer.clear();
        LuminVertexWriter w2 = writer(buffer);
        w2.putVertex(0, 0, 0, 255, 255, 255, 255, 0.123456f, 0.987654f, 128, 128, 0, 0);
        LuminVertexReader r2 = new LuminVertexReader((ByteBuffer) buffer.flip());
        LuminVertexReader.DecodedVertex uv = r2.getVertex();
        check(Math.abs(uv.u() - 0.123456f) < 1f / 32768f + 1e-6f,
                "u quantization within one LSB, got " + uv.u());
        check(Math.abs(uv.v() - 0.987654f) < 1f / 32768f + 1e-6f, "v quantization within one LSB");
    }

    private static void testClampSemantics() {
        ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        LuminVertexWriter writer = writer(buffer);
        writer.putVertex(0, 0, 0, 300, -5, 128, 255,
                1.5f, -0.5f, 0, 255, 7, 255);
        LuminVertexReader reader = new LuminVertexReader((ByteBuffer) buffer.flip());
        LuminVertexReader.DecodedVertex decoded = reader.getVertex();
        check(decoded.r() == 255, "r clamps to 255, got " + decoded.r());
        check(decoded.g() == 0, "g clamps to 0, got " + decoded.g());
        check(decoded.blockLight() == LuminQuantizedVertexFormat.LIGHT_MIN, "block light clamps to min");
        check(decoded.skyLight() == LuminQuantizedVertexFormat.LIGHT_MAX, "sky light clamps to max");
        check(decoded.sectionIndex() == 255, "section index max");
        // UV 越界钳制
        check(decoded.u() <= 1.0f, "u clamps to [0,1], got " + decoded.u());
        check(decoded.v() >= 0.0f, "v clamps to [0,1]");
    }

    private static void testCentroidBias() {
        ByteBuffer buffer = ByteBuffer.allocate(4 * LuminQuantizedVertexFormat.STRIDE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        LuminVertexWriter writer = new LuminVertexWriter(buffer);
        // quad：u ∈ {0.1, 0.3, 0.3, 0.1}，质心 0.2 → 0.1 的偏置 = 1（正方向），0.3 的偏置 = 0
        writer.beginQuad(0.1f, 0f, 0.3f, 0f, 0.3f, 0f, 0.1f, 0f);
        float[] us = {0.1f, 0.3f, 0.3f, 0.1f};
        for (int i = 0; i < 4; i++) {
            writer.putVertex(0, 0, 0, 255, 255, 255, 255, us[i], 0.5f, 128, 128, 0, 0);
        }
        LuminVertexReader reader = new LuminVertexReader((ByteBuffer) buffer.flip());
        LuminVertexReader.DecodedVertex d0 = reader.getVertex();
        LuminVertexReader.DecodedVertex d1 = reader.getVertex();
        check(d0.biasU(), "u=0.1 < centroid 0.2 -> bias bit set");
        check(!d1.biasU(), "u=0.3 > centroid 0.2 -> bias bit clear");
        // 显式覆盖：即使高于质心也强制正偏置
        buffer.clear();
        LuminVertexWriter w2 = new LuminVertexWriter(buffer);
        w2.beginQuad(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        w2.putVertex(0, 0, 0, 255, 255, 255, 255, 0.9f, 0.5f, 128, 128, 0, 0, true, null);
        LuminVertexReader r2 = new LuminVertexReader((ByteBuffer) buffer.flip());
        check(r2.getVertex().biasU(), "explicit bias override honoured");
    }

    private static void testByteLayout() {
        ByteBuffer buffer = ByteBuffer.allocate(LuminQuantizedVertexFormat.STRIDE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        LuminVertexWriter writer = writer(buffer);
        writer.putVertex(0, 0, 0, 0x10, 0x20, 0x30, 0x40,
                0f, 0f, 8, 16, 0xAB, 0xCD);
        byte[] bytes = new byte[LuminQuantizedVertexFormat.STRIDE_BYTES];
        buffer.flip().get(bytes);
        check(bytes.length == 20, "stride is exactly 20 bytes");
        // word2（字节 8..11）= 颜色 RGBA8 小端
        check(bytes[8] == 0x10 && bytes[9] == 0x20 && bytes[10] == 0x30 && bytes[11] == 0x40,
                "color word little-endian RGBA8");
        // word4（字节 16..19）= block|sky<<8|material<<16|section<<24
        check(bytes[16] == 8 && bytes[17] == 16 && bytes[18] == (byte) 0xAB && bytes[19] == (byte) 0xCD,
                "light/material/section word layout");
        // word0/word1：位置全 0 → 两个 word 都应为 0（模型原点 8 的归一化正好是 0.25，非 0！）
        // 位置 0 → normalized = 8/32 = 0.25 → quantized = 0.25 * 2^20 = 262144
        // 高 10 位 = 256（在 word0 低 10 位），低 10 位 = 0（word1）
        check((bytes[0] & 0xFF) == 0 && (bytes[1] == 1),
                "position hi word encodes origin-normalized 0.25 in 20 bits");
    }

    private static void testStride() {
        ByteBuffer buffer = ByteBuffer.allocate(8 * LuminQuantizedVertexFormat.STRIDE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        LuminVertexWriter writer = writer(buffer);
        for (int i = 0; i < 8; i++) {
            writer.putVertex(0, 0, 0, 255, 255, 255, 255, 0, 0, 128, 128, 0, i);
        }
        check(writer.bytesWritten() == 8 * LuminQuantizedVertexFormat.STRIDE_BYTES,
                "8 vertices = 160 bytes, got " + writer.bytesWritten());
        LuminVertexReader reader = new LuminVertexReader((ByteBuffer) buffer.flip());
        int count = 0;
        while (reader.hasRemaining()) {
            LuminVertexReader.DecodedVertex decoded = reader.getVertex();
            check(decoded.sectionIndex() == count, "section index round trip at " + count);
            count++;
        }
        check(count == 8, "reader consumed exactly 8 vertices");
    }

    private LuminChunkM4bSelfTest() {
    }
}
