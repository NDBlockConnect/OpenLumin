package io.github.openlumin.chunk.store;

/**
 * 单区块区域内一个 section 的缓冲槽位分配结果（顶点/索引各自独立地址空间）。
 * 偏移相对所属 region 的 vertex/index GPU 缓冲起始处，单位字节。
 */
public record LuminSectionAllocation(
        long vertexOffset, long vertexBytes,
        long indexOffset, long indexBytes) {
}
