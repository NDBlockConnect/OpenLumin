package io.github.openlumin.schedulers.render3d;

import io.github.openlumin.LuminRenderPipelines;
import io.github.openlumin.LuminVertexFormats;
import net.minecraft.resources.Identifier;
import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.shaders.BlurShader;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


/**
 * fabric-1.21.10 版本：使用 LuminRenderPipelines.BASE_SNIPPET 作为基础。
 * MC 1.21.10 的 RenderPipelines 常量都是私有的，且 API 已简化（无 withDepthStencilState）。
 */
public final class Render3DScheduler {

    public static final Render3DScheduler INSTANCE = new Render3DScheduler();

    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(LuminRenderPipelines.BASE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipeline/filled_box"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withVertexShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("openlumin","rectangle"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(LuminRenderPipelines.WORLD_LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("openlumin","pipeline/lines"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft","core/rendertype_lines"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft","core/rendertype_lines"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private final List<BlurredBoxCommand> blurredBoxes = new ArrayList<>();
    private final List<FilledBoxCommand> filledBoxes = new ArrayList<>();
    private final List<FilledSideCommand> filledSides = new ArrayList<>();
    private final List<OutlineBoxCommand> outlineBoxes = new ArrayList<>();
    private final List<SideOutlineCommand> sideOutlines = new ArrayList<>();
    private final List<LineCommand> lines = new ArrayList<>();

    private Render3DScheduler() {
    }

    public static void init() {
    }

    public boolean isEmpty() {
        return blurredBoxes.isEmpty()
                && filledBoxes.isEmpty()
                && filledSides.isEmpty()
                && outlineBoxes.isEmpty()
                && sideOutlines.isEmpty()
                && lines.isEmpty();
    }

    public void clear() {
        blurredBoxes.clear();
        filledBoxes.clear();
        filledSides.clear();
        outlineBoxes.clear();
        sideOutlines.clear();
        lines.clear();
    }

    public void addBlurredBox(AABB box, double blurStrength) {
        blurredBoxes.add(new BlurredBoxCommand(box, blurStrength));
    }

    public void addFilledBox(AABB box, Color color) {
        addFilledBox(box, color.getRGB());
    }

    public void addFilledBox(AABB box, int color) {
        addFilledFadeBox(box, color, color);
    }

    public void addFilledFadeBox(AABB box, int bottomColor, int topColor) {
        filledBoxes.add(new FilledBoxCommand(box, bottomColor, topColor));
    }

    public void addFilledSide(AABB box, int color, Direction direction) {
        filledSides.add(new FilledSideCommand(box, color, direction));
    }

    public void addOutlineBox(PoseStack stack, AABB box, Color color) {
        addOutlineBox(stack, box, color.getRGB());
    }

    public void addOutlineBox(AABB box, Color color) {
        addOutlineBox(box, color.getRGB());
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color) {
        addOutlineBox(stack, box, color, 2.0f);
    }

    public void addOutlineBox(AABB box, int color) {
        addOutlineBox(box, color, 2.0f);
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        outlineBoxes.add(new OutlineBoxCommand(box, color, thickness));
    }

    public void addOutlineBox(AABB box, int color, float thickness) {
        outlineBoxes.add(new OutlineBoxCommand(box, color, thickness));
    }

    public void addSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) {
        sideOutlines.add(new SideOutlineCommand(box, color, thickness, direction));
    }

    public void addSideOutline(AABB box, int color, float thickness, Direction direction) {
        sideOutlines.add(new SideOutlineCommand(box, color, thickness, direction));
    }

    public void addLine(Vec3 from, Vec3 to, Color color, float thickness) {
        addLine(from, to, color.getRGB(), thickness);
    }

    public void addLine(Vec3 from, Vec3 to, int color, float thickness) {
        if (from.distanceToSqr(to) < 1.0E-6) return;
        lines.add(new LineCommand(from, to, color, thickness));
    }

    public void flush(Matrix4f modelViewMatrix, Vec3 cameraPosition) {
        if (isEmpty()) {
            return;
        }

        try {
            flushBlur();
            flushFilled(modelViewMatrix, cameraPosition);
            flushLines(modelViewMatrix, cameraPosition);
        } finally {
            clear();
        }
    }

    private void flushBlur() {
        if (blurredBoxes.isEmpty()) {
            return;
        }

        for (int i = 0; i < blurredBoxes.size(); i++) {
            BlurredBoxCommand command = blurredBoxes.get(i);
            BlurShader.INSTANCE.render3DBox(command.box(), command.blurStrength());
        }
    }

    private void flushFilled(Matrix4f modelViewMatrix, Vec3 cameraPosition) {
        if (filledBoxes.isEmpty() && filledSides.isEmpty()) {
            return;
        }

        LuminImmediateRenderer.PosColorQuads builder =
                LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE, modelViewMatrix);

        for (FilledBoxCommand command : filledBoxes) {
            emitFilledBox(builder, IDENTITY_MATRIX, cameraPosition, command);
        }

        for (FilledSideCommand command : filledSides) {
            emitFilledSide(builder, IDENTITY_MATRIX, cameraPosition, command);
        }

        builder.end();
    }

    private void flushLines(Matrix4f modelViewMatrix, Vec3 cameraPosition) {
        if (outlineBoxes.isEmpty() && sideOutlines.isEmpty() && lines.isEmpty()) {
            return;
        }

        PoseStack identityStack = new PoseStack();
        PoseStack.Pose identityPose = identityStack.last();
        Matrix4f identity = identityPose.pose();

        float maxThickness = 1.0f;
        for (OutlineBoxCommand cmd : outlineBoxes) {
            maxThickness = Math.max(maxThickness, cmd.thickness());
        }
        for (SideOutlineCommand cmd : sideOutlines) {
            maxThickness = Math.max(maxThickness, cmd.thickness());
        }
        for (LineCommand cmd : lines) {
            maxThickness = Math.max(maxThickness, cmd.thickness());
        }

        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE, modelViewMatrix, maxThickness);

        for (OutlineBoxCommand command : outlineBoxes) {
            emitOutlineBox(builder, IDENTITY_MATRIX, identityPose, cameraPosition, command);
        }

        for (SideOutlineCommand command : sideOutlines) {
            emitSideOutline(builder, IDENTITY_MATRIX, identityPose, cameraPosition, command);
        }

        for (LineCommand command : lines) {
            emitLine(builder, IDENTITY_MATRIX, identityPose, cameraPosition, command);
        }

        builder.end();
    }

    private void emitFilledBox(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, Vec3 camPos, FilledBoxCommand command) {
        AABB box = command.box();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        quad(builder, matrix,
                minX, minY, minZ, command.bottomColor(),
                minX, minY, maxZ, command.bottomColor(),
                maxX, minY, maxZ, command.bottomColor(),
                maxX, minY, minZ, command.bottomColor());

        quad(builder, matrix,
                minX, maxY, minZ, command.topColor(),
                maxX, maxY, minZ, command.topColor(),
                maxX, maxY, maxZ, command.topColor(),
                minX, maxY, maxZ, command.topColor());

        quad(builder, matrix,
                minX, minY, minZ, command.bottomColor(),
                maxX, minY, minZ, command.bottomColor(),
                maxX, maxY, minZ, command.topColor(),
                minX, maxY, minZ, command.topColor());

        quad(builder, matrix,
                maxX, minY, minZ, command.bottomColor(),
                maxX, minY, maxZ, command.bottomColor(),
                maxX, maxY, maxZ, command.topColor(),
                maxX, maxY, minZ, command.topColor());

        quad(builder, matrix,
                minX, minY, maxZ, command.bottomColor(),
                minX, maxY, maxZ, command.topColor(),
                maxX, maxY, maxZ, command.topColor(),
                maxX, minY, maxZ, command.bottomColor());

        quad(builder, matrix,
                minX, minY, minZ, command.bottomColor(),
                minX, maxY, minZ, command.topColor(),
                minX, maxY, maxZ, command.topColor(),
                minX, minY, maxZ, command.bottomColor());
    }

    private void emitFilledSide(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, Vec3 camPos, FilledSideCommand command) {
        AABB box = command.box();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        switch (command.direction()) {
            case DOWN -> quad(builder, matrix,
                    minX, minY, minZ, command.color(),
                    maxX, minY, minZ, command.color(),
                    maxX, minY, maxZ, command.color(),
                    minX, minY, maxZ, command.color());
            case NORTH -> quad(builder, matrix,
                    minX, minY, minZ, command.color(),
                    minX, maxY, minZ, command.color(),
                    maxX, maxY, minZ, command.color(),
                    maxX, minY, minZ, command.color());
            case EAST -> quad(builder, matrix,
                    maxX, minY, minZ, command.color(),
                    maxX, maxY, minZ, command.color(),
                    maxX, maxY, maxZ, command.color(),
                    maxX, minY, maxZ, command.color());
            case SOUTH -> quad(builder, matrix,
                    minX, minY, maxZ, command.color(),
                    maxX, minY, maxZ, command.color(),
                    maxX, maxY, maxZ, command.color(),
                    minX, maxY, maxZ, command.color());
            case WEST -> quad(builder, matrix,
                    minX, minY, minZ, command.color(),
                    minX, minY, maxZ, command.color(),
                    minX, maxY, maxZ, command.color(),
                    minX, maxY, minZ, command.color());
            case UP -> quad(builder, matrix,
                    minX, maxY, minZ, command.color(),
                    minX, maxY, maxZ, command.color(),
                    maxX, maxY, maxZ, command.color(),
                    maxX, maxY, minZ, command.color());
        }
    }

    private void emitOutlineBox(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, OutlineBoxCommand command) {
        AABB box = command.box();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        vertexLine(builder, matrix, pose, minX, minY, minZ, maxX, minY, minZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color(), command.thickness());

        vertexLine(builder, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color(), command.thickness());

        vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color(), command.thickness());
        vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color(), command.thickness());
    }

    private void emitSideOutline(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, SideOutlineCommand command) {
        AABB box = command.box();
        float minX = (float) (box.minX - camPos.x);
        float minY = (float) (box.minY - camPos.y);
        float minZ = (float) (box.minZ - camPos.z);
        float maxX = (float) (box.maxX - camPos.x);
        float maxY = (float) (box.maxY - camPos.y);
        float maxZ = (float) (box.maxZ - camPos.z);

        switch (command.direction()) {
            case UP -> {
                vertexLine(builder, matrix, pose, minX, maxY, minZ, maxX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, maxY, minZ, maxX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, maxY, maxZ, minX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color(), command.thickness());
            }
            case DOWN -> {
                vertexLine(builder, matrix, pose, minX, minY, minZ, maxX, minY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, minY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, minX, minY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color(), command.thickness());
            }
            case EAST -> {
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, maxY, maxZ, maxX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, minY, minZ, command.color(), command.thickness());
            }
            case WEST -> {
                vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, minX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, minY, minZ, command.color(), command.thickness());
            }
            case NORTH -> {
                vertexLine(builder, matrix, pose, maxX, minY, minZ, maxX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, minY, minZ, minX, maxY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, minZ, minX, minY, minZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, maxY, minZ, minX, maxY, minZ, command.color(), command.thickness());
            }
            case SOUTH -> {
                vertexLine(builder, matrix, pose, minX, minY, maxZ, minX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, maxX, minY, maxZ, maxX, maxY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, minY, maxZ, maxX, minY, maxZ, command.color(), command.thickness());
                vertexLine(builder, matrix, pose, minX, maxY, maxZ, maxX, maxY, maxZ, command.color(), command.thickness());
            }
        }
    }

    private void emitLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, LineCommand command) {
        Vec3 from = command.from().subtract(camPos);
        Vec3 to = command.to().subtract(camPos);
        vertexLine(builder, matrix, pose,
                (float) from.x, (float) from.y, (float) from.z,
                (float) to.x, (float) to.y, (float) to.z,
                command.color(), command.thickness());
    }

    private void quad(
            LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix,
            float x1, float y1, float z1, int c1,
            float x2, float y2, float z2, int c2,
            float x3, float y3, float z3, int c3,
            float x4, float y4, float z4, int c4
    ) {
        builder.vertex(matrix, x1, y1, z1, c1);
        builder.vertex(matrix, x2, y2, z2, c2);
        builder.vertex(matrix, x3, y3, z3, c3);
        builder.vertex(matrix, x4, y4, z4, c4);
    }

    private void vertexLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color, float thickness) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        builder.vertex(matrix, pose, x1, y1, z1, color, normal.x, normal.y, normal.z);
        builder.vertex(matrix, pose, x2, y2, z2, color, normal.x, normal.y, normal.z);
    }

    private Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    private record BlurredBoxCommand(AABB box, double blurStrength) {
    }

    private record FilledBoxCommand(AABB box, int bottomColor, int topColor) {
    }

    private record FilledSideCommand(AABB box, int color, Direction direction) {
    }

    private record OutlineBoxCommand(AABB box, int color, float thickness) {
    }

    private record SideOutlineCommand(AABB box, int color, float thickness, Direction direction) {
    }

    private record LineCommand(Vec3 from, Vec3 to, int color, float thickness) {
    }

}
