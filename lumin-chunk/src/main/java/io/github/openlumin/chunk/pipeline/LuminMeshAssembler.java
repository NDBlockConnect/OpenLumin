package io.github.openlumin.chunk.pipeline;

import io.github.openlumin.chunk.vertex.LuminQuantizedVertexFormat;
import io.github.openlumin.chunk.vertex.LuminVertexWriter;

/**
 * 网格装配器（M5c）：把"顶点属性管线"（逐顶点原始属性）写入 {@link LuminChunkMesh}
 * 的量化字节布局。
 *
 * <p>它与 {@link LuminVertexWriter} 的分工：Writer 负责单顶点编码；本类负责
 * <b>quad 级节拍</b>（写前先记录 quad 的 UV 质心以启用渗色偏置）、索引写出、
 * 以及在写超容量时抛出明确异常。</p>
 */
public final class LuminMeshAssembler {

    private final LuminChunkMesh mesh;
    private final LuminVertexWriter writer;
    private final boolean intIndices;
    private int writtenVertices;
    private int writtenIndices;
    private int writtenQuads;

    public LuminMeshAssembler(LuminChunkMesh mesh) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        this.mesh = mesh;
        this.writer = new LuminVertexWriter(mesh.vertexData());
        this.intIndices = mesh.indexData().capacity() >= mesh.indexCount() * 4;
    }

    /**
     * 写入一个 quad：4 个顶点属性 + 6 个索引（0,1,2, 0,2,3 相对 baseVertex）。
     *
     * @param baseVertex 本 quad 顶点在网格内的起始序号
     */
    public void putQuad(float[] positions, int[] colors, float[] texCoords, int[] lights,
                        int material, int sectionIndex) {
        if (positions.length != 12 || colors.length != 16 || texCoords.length != 8 || lights.length != 8) {
            throw new IllegalArgumentException("quad requires 4 vertices: pos(12) color(16) uv(8) light(8)");
        }
        int baseVertex = writtenVertices;
        writer.beginQuad(
                texCoords[0], texCoords[1], texCoords[2], texCoords[3],
                texCoords[4], texCoords[5], texCoords[6], texCoords[7]);
        for (int vertex = 0; vertex < 4; vertex++) {
            int positionOffset = vertex * 3;
            int colorOffset = vertex * 4;
            int uvOffset = vertex * 2;
            int lightOffset = vertex * 2;
            writer.putVertex(
                    positions[positionOffset], positions[positionOffset + 1], positions[positionOffset + 2],
                    colors[colorOffset], colors[colorOffset + 1], colors[colorOffset + 2], colors[colorOffset + 3],
                    texCoords[uvOffset], texCoords[uvOffset + 1],
                    lights[lightOffset], lights[lightOffset + 1],
                    material, sectionIndex);
            writtenVertices++;
        }
        putIndex(baseVertex);
        putIndex(baseVertex + 1);
        putIndex(baseVertex + 2);
        putIndex(baseVertex);
        putIndex(baseVertex + 2);
        putIndex(baseVertex + 3);
        writtenQuads++;
    }

    private void putIndex(int index) {
        if (index > 0xFFFF && !intIndices) {
            throw new IllegalStateException(
                    "index " + index + " exceeds 16-bit range; allocate the mesh with int indices");
        }
        if (intIndices) {
            mesh.indexData().putInt(index);
        } else {
            mesh.indexData().putShort((short) index);
        }
        writtenIndices++;
    }

    public int writtenVertices() {
        return writtenVertices;
    }

    public int writtenIndices() {
        return writtenIndices;
    }

    public int writtenQuads() {
        return writtenQuads;
    }

    /**
     * 完成装配：翻转缓冲并校验写入量与声明容量一致。
     *
     * @throws IllegalStateException 写入量与网格声明不一致（构建逻辑错误）
     */
    public void finish() {
        if (writtenVertices != mesh.vertexCount() || writtenIndices != mesh.indexCount()
                || writtenQuads != mesh.quadCount()) {
            throw new IllegalStateException("mesh assembly mismatch: wrote "
                    + writtenVertices + " verts/" + writtenIndices + " indices/" + writtenQuads
                    + " quads but the mesh declares " + mesh.vertexCount() + "/"
                    + mesh.indexCount() + "/" + mesh.quadCount());
        }
        mesh.vertexData().flip();
        mesh.indexData().flip();
    }

    /** 每顶点的量化字节数（供上层预估上传字节）。 */
    public static int strideBytes() {
        return LuminQuantizedVertexFormat.STRIDE_BYTES;
    }

    /** 每 quad 的顶点数据字节数。 */
    public static int bytesPerQuad() {
        return 4 * LuminQuantizedVertexFormat.STRIDE_BYTES;
    }
}
