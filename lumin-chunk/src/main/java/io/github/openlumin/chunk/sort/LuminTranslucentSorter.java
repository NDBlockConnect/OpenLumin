package io.github.openlumin.chunk.sort;

import java.util.List;

/**
 * 半透明排序策略（Sodium translucent_sorting 参照：Sorter 接口语义、BSP 遍历、
 * 相机相关排序序；零拷码，仅架构与算法语义）。
 *
 * <p>Store 层联动（消费方职责）：
 * 重排序 = free 旧的 section 索引槽位（M2 {@code LuminChunkStore.free}）
 * + upload 新索引数据（{@code LuminChunkStore.upload}），
 * 顶点槽位不动——复用 M2 账本，无新增 GPU 路径。</p>
 */
public interface LuminTranslucentSorter extends AutoCloseable {

    LuminSortStrategy strategy();

    /**
     * 按策略对四边形列表排序：返回新顺序列表（不修改输入）。
     * 消费方按返回序写入索引缓冲后 upload 替代旧槽位。
     *
     * @param camera 当前相机状态
     * @param quads  四边形列表（不可变参数）
     * @return 排序后的新列表
     */
    List<LuminTranslucentQuad> sort(LuminCameraState camera, List<LuminTranslucentQuad> quads);

    @Override
    void close();
}