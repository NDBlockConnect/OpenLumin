package io.github.openlumin.shaderpack.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import io.github.openlumin.Constants;
import io.github.openlumin.shaderpack.ShaderpackIR;
import io.github.openlumin.shaderpack.compile.CompiledShaderpack;
import io.github.openlumin.shaderpack.compile.LuminShaderpackCompiler;
import io.github.openlumin.shaderpack.exec.ShaderpackFrameExecutor;
import io.github.openlumin.shaderpack.frame.LuminFrameUniformInputs;
import io.github.openlumin.shaderpack.frame.LuminFrameUniforms;
import io.github.openlumin.shaderpack.graph.PassGraph;
import io.github.openlumin.shaderpack.parse.ShaderpackLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * Shaderpack 宿主（WP-2 首条全链路，26.1.2 GL）：pack 供给 → 解析/编译 → 每帧执行。
 *
 * <p><b>opt-in 且失效保护</b>：仅当系统属性 {@code -Dopenlumin.shaderpack=<目录>} 存在时启用；
 * 未配置时零开销。任何加载/执行异常都会记录并**永久禁用**，不影响正常游戏。</p>
 *
 * <p>回路（过渡形态，M7 前的验证通路）：MC 主目标 → colortex0 种子 →
 * pack 合成链（预处理/翻译后的着色器 + ShaderpackUniforms）→ colortex0 最终副本 → MC 主目标。
 * 当前在 GUI 之后执行（GameRenderer.render RETURN），属过渡时序；正式序（世界后/GUI 前）随 M7 接线。</p>
 *
 * <p>过渡行为：无世界（菜单）时以正午回退值（sunAngle=0/moonAngle=180/时间 6000）执行，
 * 便于在菜单帧验证编译与执行链；进入世界后使用真实环境属性。</p>
 */
public final class ShaderpackHost {

    /** 启用开关的系统属性名。 */
    public static final String PROPERTY = "openlumin.shaderpack";

    private static final ShaderpackHost INSTANCE = new ShaderpackHost();
    private static final float MAX_FRAME_TIME_SECONDS = 0.25f;
    private static final int FOG_CLEAR_UNUSED = 0;

    public static ShaderpackHost get() {
        return INSTANCE;
    }

    private boolean loadAttempted;
    private boolean disabled;
    private ShaderpackFrameExecutor executor;
    private GpuSampler sampler;
    private int frameCounter;
    private float frameTimeCounter;
    private long lastFrameNanos;
    private int loggedExecutorDiagnostics;

    private ShaderpackHost() {
    }

    /** 是否启用（系统属性存在且非空）。 */
    public static boolean enabled() {
        String configured = System.getProperty(PROPERTY);
        return configured != null && !configured.isBlank();
    }

    /** 帧尾钩子入口（mixin 调用；未启用时零开销）。 */
    public void onFrameEnd(DeltaTracker deltaTracker) {
        if (disabled || !enabled()) {
            return;
        }
        try {
            ensureLoaded();
            if (disabled || executor == null) {
                return;
            }
            executeFrame(deltaTracker);
            logNewExecutorDiagnostics();
        } catch (Throwable failure) {
            disabled = true;
            Constants.LOGGER.error("[OpenLumin-Shaderpack] frame execution failed; host disabled", failure);
            closeQuietly();
        }
    }

    /** 执行器新增诊断只记录一次（避免每帧刷屏）。 */
    private void logNewExecutorDiagnostics() {
        var diagnostics = executor.diagnostics();
        for (int index = loggedExecutorDiagnostics; index < diagnostics.size(); index++) {
            Constants.LOGGER.warn("[OpenLumin-Shaderpack] {}", diagnostics.get(index).format());
        }
        loggedExecutorDiagnostics = diagnostics.size();
    }

    private void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        try {
            Path packDirectory = resolvePackDirectory();
            if (packDirectory == null) {
                Constants.LOGGER.warn("[OpenLumin-Shaderpack] no pack directory found; host disabled");
                disabled = true;
                return;
            }
            ShaderpackIR ir = loadPack(packDirectory);
            if (ir.hasErrors()) {
                ir.errors().forEach(d -> Constants.LOGGER.error("[OpenLumin-Shaderpack] {}", d.format()));
                Constants.LOGGER.error("[OpenLumin-Shaderpack] pack rejected ({} errors); host disabled",
                        ir.errors().size());
                disabled = true;
                return;
            }
            CompiledShaderpack compiled = LuminShaderpackCompiler.compile(ir, PassGraph.from(ir));
            if (compiled.hasErrors()) {
                compiled.errors().forEach(d -> Constants.LOGGER.error("[OpenLumin-Shaderpack] {}", d.format()));
                Constants.LOGGER.error("[OpenLumin-Shaderpack] compile rejected; host disabled");
                disabled = true;
                return;
            }
            compiled.diagnostics().forEach(d -> Constants.LOGGER.info("[OpenLumin-Shaderpack] {}", d.format()));
            ShaderpackSourceRegistry.register(compiled.shaderResources());
            executor = new ShaderpackFrameExecutor(compiled);
            sampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            Constants.LOGGER.info("[OpenLumin-Shaderpack] loaded '{}': {} pipeline(s), {} pass(es)",
                    ir.metadata().name(), compiled.pipelines().size(),
                    compiled.framePlan().passSteps().size());
        } catch (Throwable failure) {
            disabled = true;
            Constants.LOGGER.error("[OpenLumin-Shaderpack] load failed; host disabled", failure);
        }
    }

    /** pack 目录：系统属性优先；否则 <gameDir>/openlumin/shaderpacks 的首个子目录。 */
    private Path resolvePackDirectory() throws IOException {
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isDirectory(path)) {
                return path;
            }
            Constants.LOGGER.warn("[OpenLumin-Shaderpack] configured path is not a directory: {}", path);
            return null;
        }
        return null;
    }

    private ShaderpackIR loadPack(Path directory) throws IOException {
        List<String> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.filter(Files::isRegularFile)
                    .forEach(file -> paths.add(directory.relativize(file).toString()
                            .replace('\\', '/')));
        }
        ShaderpackLoader loader = new ShaderpackLoader(
                directory.getFileName() == null ? "<pack>" : directory.getFileName().toString(),
                relative -> {
                    Path file = directory.resolve(relative.replace('\\', '/'));
                    if (!Files.isRegularFile(file)) {
                        return null;
                    }
                    try {
                        return Files.readString(file, StandardCharsets.UTF_8);
                    } catch (IOException readFailure) {
                        Constants.LOGGER.warn("[OpenLumin-Shaderpack] failed to read {}", file, readFailure);
                        return null;
                    }
                });
        return loader.load(paths);
    }

    private void executeFrame(DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float rawSunAngle;
        float rawMoonAngle;
        if (level == null) {
            rawSunAngle = 0f;
            rawMoonAngle = 180f;
        } else {
            rawSunAngle = camera.attributeProbe()
                    .getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
            rawMoonAngle = camera.attributeProbe()
                    .getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);
        }

        Vec3 cameraPosition = camera.position();
        Vec3 eyePosition = cameraPosition;
        var cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity != null) {
            eyePosition = cameraEntity.getEyePosition(partialTick);
        }
        long clockTime = level == null ? 6000L : level.getDefaultClockTime();
        int worldTime = (int) (clockTime % 24000L);
        int worldDay = (int) (clockTime / 24000L);
        float rainStrength = level == null ? 0f : level.getRainLevel(partialTick);
        float isEyeInWater = switch (camera.getFluidInCamera()) {
            case WATER -> 1f;
            case LAVA -> 2f;
            case POWDER_SNOW -> 3f;
            default -> 0f;
        };
        Matrix4f modelView = camera.getViewRotationMatrix(new Matrix4f())
                .translate((float) -cameraPosition.x, (float) -cameraPosition.y,
                        (float) -cameraPosition.z);

        float frameTime = updateFrameTime();
        LuminFrameUniforms uniforms = LuminFrameUniforms.compute(new LuminFrameUniformInputs(
                rawSunAngle, rawMoonAngle, 0f, modelView,
                worldTime, worldDay, frameCounter, frameTime, frameTimeCounter,
                rainStrength, rainStrength,
                cameraPosition.x, cameraPosition.y, cameraPosition.z,
                isEyeInWater, 0f, 0f, 0f,
                eyePosition.x, eyePosition.y, eyePosition.z));
        frameCounter = uniforms.frameCounter();
        frameTimeCounter = uniforms.frameTimeCounter();

        var mainTarget = minecraft.getMainRenderTarget();
        var mainColor = mainTarget.getColorTexture();
        executor.execute(sampler, mainTarget.width, mainTarget.height,
                FOG_CLEAR_UNUSED, uniforms, mainColor, mainColor);
    }

    private float updateFrameTime() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L
                ? 1f / 60f
                : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.max(0f, Math.min(MAX_FRAME_TIME_SECONDS, delta));
    }

    private void closeQuietly() {
        if (executor != null) {
            try {
                executor.close();
            } catch (Throwable ignored) {
                // 关闭失败不覆盖已记录的根因
            }
            executor = null;
        }
    }
}
