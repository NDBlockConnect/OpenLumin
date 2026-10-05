package io.github.openlumin.shaderpack.exec;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import io.github.openlumin.shaderpack.Diagnostic;
import io.github.openlumin.shaderpack.LuminBufferFormat;
import io.github.openlumin.shaderpack.LuminTargetId;
import io.github.openlumin.shaderpack.graph.LuminResourceId;
import io.github.openlumin.shaderpack.plan.LuminResourceAllocation;
import io.github.openlumin.shaderpack.plan.LuminResourcePlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shaderpack GPU 资源管理者（WP-2 M4 平台层，26.1.2 GL 路径）：
 * 按 {@link LuminResourcePlan} 惰性分配纹理与视图（colortex main/alt 乒乓对）。
 *
 * <p><b>格式映射的现实约束</b>：26.1.2 的 {@link TextureFormat} 仅暴露
 * RGBA8/RED8/RED8I/DEPTH32（javap 实证）——高精度格式（RGBA16F 等）降级为 RGBA8
 * 并出 WARNING（不静默）；深度目标一律 DEPTH32。</p>
 *
 * <p>尺寸变化时整体重建（对齐 Iris"resize 才重建"语义）；{@link #close()} 逆序释放。</p>
 */
public final class ShaderpackTargets implements AutoCloseable {

    /**
     * 目标纹理 usage（与 vanilla RenderTarget 的 15 一致，java 实证）：
     * COPY_DST（clearColorTexture/copy 目标所需——运行时实测强制）+ COPY_SRC（present 拷贝源）
     * + TEXTURE_BINDING（采样）+ RENDER_ATTACHMENT（pass 颜色附件）。
     */
    public static final int TARGET_USAGE = GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private final Map<LuminResourceId, GpuTexture> textures = new LinkedHashMap<>();
    private final Map<LuminResourceId, GpuTextureView> views = new LinkedHashMap<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final int width;
    private final int height;
    private boolean closed;

    private ShaderpackTargets(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** 按资源计划分配全部目标（尺寸 = 主目标分辨率 × 各目标尺寸策略）。 */
    public static ShaderpackTargets allocate(GpuDevice device, LuminResourcePlan plan,
                                             int width, int height) {
        if (device == null) {
            throw new NullPointerException("device");
        }
        if (plan == null) {
            throw new NullPointerException("plan");
        }
        ShaderpackTargets targets = new ShaderpackTargets(width, height);
        for (LuminResourceAllocation allocation : plan.allocations().values()) {
            targets.allocate(device, allocation, width, height);
        }
        return targets;
    }

    private void allocate(GpuDevice device, LuminResourceAllocation allocation,
                          int baseWidth, int baseHeight) {
        LuminTargetId target = allocation.target();
        int targetWidth = Math.max(1, allocation.size().resolveWidth(baseWidth));
        int targetHeight = Math.max(1, allocation.size().resolveHeight(baseHeight));
        TextureFormat format = mapTextureFormat(target, allocation.format(), diagnostics);
        int usage = TARGET_USAGE;
        if (allocation.mipmap()) {
            diagnostics.add(Diagnostic.warning(target.canonicalName(), 0,
                    "mipmap requested but generation is not wired yet; allocating 1 level"));
        }
        createPair(device, target, LuminResourceId.Copy.MAIN, format, usage,
                targetWidth, targetHeight, false);
        if (allocation.pingPong()) {
            createPair(device, target, LuminResourceId.Copy.ALT, format, usage,
                    targetWidth, targetHeight, true);
        }
    }

    private void createPair(GpuDevice device, LuminTargetId target, LuminResourceId.Copy copy,
                            TextureFormat format, int usage, int width, int height,
                            boolean alt) {
        String label = "openlumin:shaderpack/" + target.canonicalName() + (alt ? ".alt" : "");
        GpuTexture texture = device.createTexture(label, usage, format, width, height, 1, 1);
        GpuTextureView view = device.createTextureView(texture);
        LuminResourceId id = new LuminResourceId(target, copy);
        textures.put(id, texture);
        views.put(id, view);
    }

    /** 取视图；目标未分配返回 null。 */
    public GpuTextureView view(LuminTargetId target, LuminResourceId.Copy copy) {
        return views.get(new LuminResourceId(target, copy));
    }

    /** 取纹理（清屏 API 用）；目标未分配返回 null。 */
    public GpuTexture texture(LuminTargetId target, LuminResourceId.Copy copy) {
        return textures.get(new LuminResourceId(target, copy));
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (GpuTextureView view : views.values()) {
            view.close();
        }
        for (GpuTexture texture : textures.values()) {
            texture.close();
        }
        views.clear();
        textures.clear();
    }

    /**
     * 计划格式 → 26.1.2 纹理格式（降级策略见类注释）。
     */
    public static TextureFormat mapTextureFormat(LuminTargetId target, LuminBufferFormat format,
                                                 List<Diagnostic> diagnostics) {
        if (target.kind() == LuminTargetId.Kind.DEPTH
                || target.kind() == LuminTargetId.Kind.SHADOW_DEPTH) {
            return TextureFormat.DEPTH32;
        }
        return switch (format) {
            case RGBA8, RGB10_A2, RGB5_A1, RG16 -> TextureFormat.RGBA8;
            case R8 -> TextureFormat.RED8;
            case RGBA16, RGBA16F, RGBA32F, RGB16F, RGB32F, RG16F, RG32F, R16F, R32F -> {
                if (diagnostics != null) {
                    diagnostics.add(Diagnostic.warning(target.canonicalName(), 0,
                            "format " + format + " is not exposed by the 26.1.2 texture API; "
                                    + "falling back to RGBA8 (precision loss)"));
                }
                yield TextureFormat.RGBA8;
            }
        };
    }
}
