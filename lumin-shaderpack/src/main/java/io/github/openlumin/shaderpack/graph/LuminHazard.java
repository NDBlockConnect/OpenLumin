package io.github.openlumin.shaderpack.graph;

/**
 * 资源竞争（hazard）：依赖分析推导出的同步需求。
 *
 * <p>三类经典竞争：</p>
 * <ul>
 *   <li>{@link Kind#READ_AFTER_WRITE}：先写后读（真依赖）——读者须看到写者的结果；</li>
 *   <li>{@link Kind#WRITE_AFTER_READ}：先读后写（反依赖）——写者须等读者读完；</li>
 *   <li>{@link Kind#WRITE_AFTER_WRITE}：先写后写（输出依赖）——两次写入须有序，后者覆盖前者；</li>
 * </ul>
 *
 * <p>执行层据此插入内存屏障（图形后端）或依赖（Vulkan 的 image barrier / 子通道依赖）。</p>
 *
 * @param resource 竞争资源
 * @param kind     竞争类型
 * @param producer 先发生的 pass 序号
 * @param consumer 后发生的 pass 序号
 */
public record LuminHazard(LuminResourceId resource, Kind kind, int producer, int consumer) {

    public enum Kind {
        READ_AFTER_WRITE,
        WRITE_AFTER_READ,
        WRITE_AFTER_WRITE
    }

    public LuminHazard {
        if (resource == null) {
            throw new NullPointerException("resource");
        }
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (producer == consumer) {
            throw new IllegalArgumentException("hazard needs two distinct passes, got " + producer);
        }
    }

    @Override
    public String toString() {
        return kind + " on " + resource + " from [" + producer + "] to [" + consumer + "]";
    }
}
