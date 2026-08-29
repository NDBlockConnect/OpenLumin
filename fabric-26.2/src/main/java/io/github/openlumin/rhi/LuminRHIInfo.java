package io.github.openlumin.rhi;

/**
 * RHI 后端信息——能力声明
 *
 * 后端实现必须诚实地报告自身能力，业务层据此决定降级或绕路。
 * 例如：GLES 路径不支持 compute，则 LuminCompute 不可用。
 */
public record LuminRHIInfo(
        String backendName,         // "OpenGL 4.6" / "Vulkan 1.3" / "D3D12" / "Metal"
        String version,
        int maxTextureSize,
        int maxUniformBufferSize,
        boolean supportsBindless,
        boolean supportsGeometryShader,
        boolean supportsTessellation,
        boolean supportsCompute,     // false on GLES / mobile
        boolean supportsFloatTextures,
        boolean supportsBCnCompression,
        boolean supportsDepthClamp,
        boolean supportsConservativeRaster
) {
    public static LuminRHIInfo gl46(String version) {
        return new LuminRHIInfo("OpenGL", version,
                16384, 65536,
                true, true, true, true, true, true, true, true);
    }
    public static LuminRHIInfo gles30(String version) {
        return new LuminRHIInfo("OpenGL ES 3.0", version,
                4096, 16384,
                false, false, false, false, true, false, false, false);
    }
    public static LuminRHIInfo vulkan(String version) {
        return new LuminRHIInfo("Vulkan", version,
                16384, 65536,
                true, true, true, true, true, true, true, true);
    }
    public static LuminRHIInfo dx12(String version) {
        return new LuminRHIInfo("Direct3D 12", version,
                16384, 65536,
                true, true, true, true, true, true, true, true);
    }
    public static LuminRHIInfo metal(String version) {
        return new LuminRHIInfo("Metal", version,
                16384, 65536,
                true, true, true, true, true, true, true, true);
    }
}
