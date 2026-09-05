package io.github.openlumin.chunk.cull;

/**
 * 视锥测试（消费方桥接接口）：判断一个 section 的包围盒是否在视锥内可见。
 * <p>精确/宽松分级（Sodium 的 regular/wide 语义）由实现内部决定；
 * 库层只要求布尔结果。实现应线程安全、幂等。</p>
 */
@FunctionalInterface
public interface LuminFrustumTest {

    boolean isSectionVisible(LuminSectionPos pos);
}
