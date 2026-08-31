package io.github.openlumin.chunk;

/**
 * 区块构建任务的产出数据包（M1 为纯 CPU 网格数据，M2 起对接 GPU 上传路径）。
 * <p>实现约定：{@link #close()} 必须幂等；释放语义（直接释放 / 归还池）
 * 由具体实现决定。正常完成的产出由消费方负责关闭；
 * 构建完成后才被取消的产出由 {@link LuminChunkBuilder} 代为关闭。</p>
 */
public interface LuminBuildOutput extends AutoCloseable {

    @Override
    void close();
}
