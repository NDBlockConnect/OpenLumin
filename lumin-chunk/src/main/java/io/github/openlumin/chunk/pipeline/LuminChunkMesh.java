package io.github.openlumin.chunk.pipeline;

import io.github.openlumin.chunk.LuminBuildOutput;
import io.github.openlumin.chunk.vertex.LuminQuantizedVertexFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 区块网格构建产出（M5c 管线数据包）：量化顶点数据 + 索引数据 + 计数。
 *
 * <p>这是"构建层产出 → Store 层上传"的载体：{@link #vertexData()} 与 {@link #indexData()}
 * 直接交给上传路径（arena/staging），计数用于绘制与批次装配。</p>
 *
 * <p>资源语义：{@link #close()} 幂等；关闭后访问数据抛 {@link IllegalStateException}
 * （防止上传后误用同一份数据）。</p>
 */
public final class LuminChunkMesh implements LuminBuildOutput {

    private final ByteBuffer vertexData;
    private final ByteBuffer indexData;
    private final int vertexCount;
    private final int indexCount;
    private final int quadCount;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public LuminChunkMesh(ByteBuffer vertexData, ByteBuffer indexData,
                          int vertexCount, int indexCount, int quadCount) {
        if (vertexData == null || indexData == null) {
            throw new NullPointerException("vertexData and indexData are required");
        }
        if (vertexCount < 0 || indexCount < 0 || quadCount < 0) {
            throw new IllegalArgumentException("counts must be >= 0");
        }
        if (vertexData.capacity() < vertexCount * LuminQuantizedVertexFormat.STRIDE_BYTES) {
            throw new IllegalArgumentException("vertex buffer too small for " + vertexCount + " vertices");
        }
        // 量化布局为小端：外部传入的缓冲也强制为小端，避免宿主默认大端导致字节序错误
        this.vertexData = vertexData.order(ByteOrder.LITTLE_ENDIAN);
        this.indexData = indexData.order(ByteOrder.LITTLE_ENDIAN);
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.quadCount = quadCount;
    }

    /** 分配一个可容纳给定顶点/索引数的网格缓冲。 */
    public static LuminChunkMesh allocate(int vertexCount, int indexCount, int quadCount, boolean intIndices) {
        int vertexBytes = vertexCount * LuminQuantizedVertexFormat.STRIDE_BYTES;
        int indexBytes = indexCount * (intIndices ? 4 : 2);
        return new LuminChunkMesh(
                ByteBuffer.allocate(vertexBytes).order(ByteOrder.LITTLE_ENDIAN),
                ByteBuffer.allocate(indexBytes).order(ByteOrder.LITTLE_ENDIAN),
                vertexCount, indexCount, quadCount);
    }

    public ByteBuffer vertexData() {
        checkOpen();
        return vertexData;
    }

    public ByteBuffer indexData() {
        checkOpen();
        return indexData;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public int quadCount() {
        return quadCount;
    }

    public int vertexBytes() {
        return vertexCount * LuminQuantizedVertexFormat.STRIDE_BYTES;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("mesh already closed (uploaded or discarded)");
        }
    }

    @Override
    public String toString() {
        return "LuminChunkMesh{verts=" + vertexCount + ", idx=" + indexCount + ", quads=" + quadCount + "}";
    }
}
