package io.github.openlumin.rhi;

/**
 * 管线（接口）
 *
 * LuminPipeline 是 RHI 后端管线的对外抽象：业务层通过它绑定到 RenderPass.setPipeline。
 * 后端用 record（持有 shader/vertexFormat/state 三个字段）实现此接口。
 * 关键：LuminPipeline 必须为 interface，createPipeline 才有返回类型可指向；LuminPipelineState/BlendState 为 record（数据类）。
 */
public interface LuminPipeline {
    LuminShader shader();
    LuminVertexFormat vertexFormat();
    LuminPipelineState state();
}
