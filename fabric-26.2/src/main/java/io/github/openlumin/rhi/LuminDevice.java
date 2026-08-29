package io.github.openlumin.rhi;

import java.nio.ByteBuffer;

/**
 * 设备
 *
 * LuminDevice 暴露物理设备能力；命令录制由 LuminCommandEncoder 完成，
 * 完成后通过 LuminRHI.submit() 提交。
 */
public interface LuminDevice {
    LuminBuffer createBuffer(LuminBufferUsage usage, long size);
    LuminTexture createTexture2D(int width, int height, LuminFormat format);
    LuminTexture createTexture3D(int width, int height, int depth, LuminFormat format);
    LuminSampler createSampler(LuminFilter min, LuminFilter mag, LuminAddressMode u, LuminAddressMode v, LuminAddressMode w);
    LuminShader createShader(String vertexPath, String fragmentPath);
    LuminPipeline createPipeline(LuminShader shader, LuminVertexFormat vertexFormat, LuminPipelineState state);
}
