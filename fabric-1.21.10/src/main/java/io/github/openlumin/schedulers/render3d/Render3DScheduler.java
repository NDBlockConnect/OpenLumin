package io.github.openlumin.schedulers.render3d;

import net.minecraft.resources.ResourceLocation;
import io.github.openlumin.immediate.LuminImmediateRenderer;
import io.github.openlumin.shaders.BlurShader;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * fabric-1.21.10 override：
 * - DEBUG_FILLED_SNIPPET / LINES_SNIPPET 在 1.21.10 中是 private → 改用 POST_PROCESSING_SNIPPET
 * - withDepthStencilState(new DepthStencilState(...)) 在 1.21.10 已从 Builder 移除 → 删除该调用
 * - 移除 DepthStencilState / CompareOp 导入
 */
public final class Render3DScheduler {

    public static final Render3DScheduler INSTANCE = new Render3DScheduler();

    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipeline/filled_box"))
            .withCull(false)
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("openlumin","pipeline/lines"))
            .withCull(false)
            .build();

    private final List<BlurredBoxCommand> blurredBoxes = new ArrayList<>();
    private final List<FilledBoxCommand> filledBoxes = new ArrayList<>();
    private final List<FilledSideCommand> filledSides = new ArrayList<>();
    private final List<OutlineBoxCommand> outlineBoxes = new ArrayList<>();
    private final List<SideOutlineCommand> sideOutlines = new ArrayList<>();
    private final List<LineCommand> lines = new ArrayList<>();

    private Render3DScheduler() {}

    public static void init() {}

    public boolean isEmpty() {
        return blurredBoxes.isEmpty() && filledBoxes.isEmpty() && filledSides.isEmpty()
                && outlineBoxes.isEmpty() && sideOutlines.isEmpty() && lines.isEmpty();
    }

    public void clear() {
        blurredBoxes.clear(); filledBoxes.clear(); filledSides.clear();
        outlineBoxes.clear(); sideOutlines.clear(); lines.clear();
    }

    public void addBlurredBox(AABB box, double blurStrength) { blurredBoxes.add(new BlurredBoxCommand(box, blurStrength)); }
    public void addFilledBox(AABB box, Color color) { addFilledBox(box, color.getRGB()); }
    public void addFilledBox(AABB box, int color) { addFilledFadeBox(box, color, color); }
    public void addFilledFadeBox(AABB box, int bottomColor, int topColor) { filledBoxes.add(new FilledBoxCommand(box, bottomColor, topColor)); }
    public void addFilledSide(AABB box, int color, Direction direction) { filledSides.add(new FilledSideCommand(box, color, direction)); }
    public void addOutlineBox(PoseStack stack, AABB box, Color color) { addOutlineBox(stack, box, color.getRGB()); }
    public void addOutlineBox(AABB box, Color color) { addOutlineBox(box, color.getRGB()); }
    public void addOutlineBox(PoseStack stack, AABB box, int color) { addOutlineBox(stack, box, color, 2.0f); }
    public void addOutlineBox(AABB box, int color) { addOutlineBox(box, color, 2.0f); }
    public void addOutlineBox(PoseStack stack, AABB box, int color, float thickness) { outlineBoxes.add(new OutlineBoxCommand(box, color, thickness)); }
    public void addOutlineBox(AABB box, int color, float thickness) { outlineBoxes.add(new OutlineBoxCommand(box, color, thickness)); }
    public void addSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) { sideOutlines.add(new SideOutlineCommand(box, color, thickness, direction)); }
    public void addSideOutline(AABB box, int color, float thickness, Direction direction) { sideOutlines.add(new SideOutlineCommand(box, color, thickness, direction)); }
    public void addLine(Vec3 from, Vec3 to, Color color, float thickness) { addLine(from, to, color.getRGB(), thickness); }
    public void addLine(Vec3 from, Vec3 to, int color, float thickness) {
        if (from.distanceToSqr(to) < 1.0E-6) return;
        lines.add(new LineCommand(from, to, color, thickness));
    }

    /**
     * 1.21.10：renderLevel 已不传 PoseStack，改用无参 flush()，
     * 内部构造包含 ModelView 矩阵的临时 PoseStack，确保线段法线矩阵正确。
     */
    public void flush() {
        if (isEmpty()) return;
        try {
            flushBlur();
            flushFilled();
            PoseStack tempStack = new PoseStack();
            tempStack.mulPose(RenderSystem.getModelViewMatrix());
            flushLines(tempStack);
        } finally {
            clear();
        }
    }

    /** @deprecated 1.21.10 中 renderLevel 无 PoseStack 参数，请用 {@link #flush()} */
    @Deprecated
    public void flush(PoseStack stack) {
        flush();
    }

    private void flushBlur() {
        for (BlurredBoxCommand command : blurredBoxes) {
            BlurShader.INSTANCE.render3DBox(command.box(), command.blurStrength());
        }
    }

    private void flushFilled() {
        if (filledBoxes.isEmpty() && filledSides.isEmpty()) return;
        LuminImmediateRenderer.PosColorQuads builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE);
        Matrix4f matrix = RenderSystem.getModelViewMatrix();
        Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
        for (FilledBoxCommand command : filledBoxes) emitFilledBox(builder, matrix, camPos, command);
        for (FilledSideCommand command : filledSides) emitFilledSide(builder, matrix, camPos, command);
        builder.end();
    }

    private void flushLines(PoseStack stack) {
        if (outlineBoxes.isEmpty() && sideOutlines.isEmpty() && lines.isEmpty()) return;
        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
        Vec3 camPos = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();
        PoseStack.Pose pose = stack.last();
        Matrix4f matrix = pose.pose();
        for (OutlineBoxCommand command : outlineBoxes) emitOutlineBox(builder, matrix, pose, camPos, command);
        for (SideOutlineCommand command : sideOutlines) emitSideOutline(builder, matrix, pose, camPos, command);
        for (LineCommand command : lines) emitLine(builder, matrix, pose, camPos, command);
        builder.end();
    }

    private void emitFilledBox(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, Vec3 camPos, FilledBoxCommand command) {
        AABB box = command.box();
        float minX = (float)(box.minX - camPos.x), minY = (float)(box.minY - camPos.y), minZ = (float)(box.minZ - camPos.z);
        float maxX = (float)(box.maxX - camPos.x), maxY = (float)(box.maxY - camPos.y), maxZ = (float)(box.maxZ - camPos.z);
        quad(builder, matrix, minX, minY, minZ, command.bottomColor(), minX, minY, maxZ, command.bottomColor(), maxX, minY, maxZ, command.bottomColor(), maxX, minY, minZ, command.bottomColor());
        quad(builder, matrix, minX, maxY, minZ, command.topColor(), maxX, maxY, minZ, command.topColor(), maxX, maxY, maxZ, command.topColor(), minX, maxY, maxZ, command.topColor());
        quad(builder, matrix, minX, minY, minZ, command.bottomColor(), maxX, minY, minZ, command.bottomColor(), maxX, maxY, minZ, command.topColor(), minX, maxY, minZ, command.topColor());
        quad(builder, matrix, maxX, minY, minZ, command.bottomColor(), maxX, minY, maxZ, command.bottomColor(), maxX, maxY, maxZ, command.topColor(), maxX, maxY, minZ, command.topColor());
        quad(builder, matrix, minX, minY, maxZ, command.bottomColor(), minX, maxY, maxZ, command.topColor(), maxX, maxY, maxZ, command.topColor(), maxX, minY, maxZ, command.bottomColor());
        quad(builder, matrix, minX, minY, minZ, command.bottomColor(), minX, maxY, minZ, command.topColor(), minX, maxY, maxZ, command.topColor(), minX, minY, maxZ, command.bottomColor());
    }

    private void emitFilledSide(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, Vec3 camPos, FilledSideCommand command) {
        AABB box = command.box();
        float minX = (float)(box.minX-camPos.x), minY = (float)(box.minY-camPos.y), minZ = (float)(box.minZ-camPos.z);
        float maxX = (float)(box.maxX-camPos.x), maxY = (float)(box.maxY-camPos.y), maxZ = (float)(box.maxZ-camPos.z);
        int c = command.color();
        switch (command.direction()) {
            case DOWN  -> quad(builder,matrix, minX,minY,minZ,c, maxX,minY,minZ,c, maxX,minY,maxZ,c, minX,minY,maxZ,c);
            case NORTH -> quad(builder,matrix, minX,minY,minZ,c, minX,maxY,minZ,c, maxX,maxY,minZ,c, maxX,minY,minZ,c);
            case EAST  -> quad(builder,matrix, maxX,minY,minZ,c, maxX,maxY,minZ,c, maxX,maxY,maxZ,c, maxX,minY,maxZ,c);
            case SOUTH -> quad(builder,matrix, minX,minY,maxZ,c, maxX,minY,maxZ,c, maxX,maxY,maxZ,c, minX,maxY,maxZ,c);
            case WEST  -> quad(builder,matrix, minX,minY,minZ,c, minX,minY,maxZ,c, minX,maxY,maxZ,c, minX,maxY,minZ,c);
            case UP    -> quad(builder,matrix, minX,maxY,minZ,c, minX,maxY,maxZ,c, maxX,maxY,maxZ,c, maxX,maxY,minZ,c);
        }
    }

    private void emitOutlineBox(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, OutlineBoxCommand command) {
        AABB box = command.box();
        float minX = (float)(box.minX-camPos.x), minY = (float)(box.minY-camPos.y), minZ = (float)(box.minZ-camPos.z);
        float maxX = (float)(box.maxX-camPos.x), maxY = (float)(box.maxY-camPos.y), maxZ = (float)(box.maxZ-camPos.z);
        int c = command.color(); float t = command.thickness();
        vertexLine(builder, matrix, pose, minX,minY,minZ, maxX,minY,minZ, c, t);
        vertexLine(builder, matrix, pose, maxX,minY,minZ, maxX,minY,maxZ, c, t);
        vertexLine(builder, matrix, pose, maxX,minY,maxZ, minX,minY,maxZ, c, t);
        vertexLine(builder, matrix, pose, minX,minY,maxZ, minX,minY,minZ, c, t);
        vertexLine(builder, matrix, pose, minX,maxY,minZ, maxX,maxY,minZ, c, t);
        vertexLine(builder, matrix, pose, maxX,maxY,minZ, maxX,maxY,maxZ, c, t);
        vertexLine(builder, matrix, pose, maxX,maxY,maxZ, minX,maxY,maxZ, c, t);
        vertexLine(builder, matrix, pose, minX,maxY,maxZ, minX,maxY,minZ, c, t);
        vertexLine(builder, matrix, pose, minX,minY,minZ, minX,maxY,minZ, c, t);
        vertexLine(builder, matrix, pose, maxX,minY,minZ, maxX,maxY,minZ, c, t);
        vertexLine(builder, matrix, pose, maxX,minY,maxZ, maxX,maxY,maxZ, c, t);
        vertexLine(builder, matrix, pose, minX,minY,maxZ, minX,maxY,maxZ, c, t);
    }

    private void emitSideOutline(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, SideOutlineCommand command) {
        AABB box = command.box();
        float minX = (float)(box.minX-camPos.x), minY = (float)(box.minY-camPos.y), minZ = (float)(box.minZ-camPos.z);
        float maxX = (float)(box.maxX-camPos.x), maxY = (float)(box.maxY-camPos.y), maxZ = (float)(box.maxZ-camPos.z);
        int c = command.color(); float t = command.thickness();
        switch (command.direction()) {
            case UP    -> { vertexLine(builder,matrix,pose, minX,maxY,minZ, maxX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,maxY,minZ, maxX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, maxX,maxY,maxZ, minX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, minX,maxY,maxZ, minX,maxY,minZ, c,t); }
            case DOWN  -> { vertexLine(builder,matrix,pose, minX,minY,minZ, maxX,minY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,minZ, maxX,minY,maxZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,maxZ, minX,minY,maxZ, c,t); vertexLine(builder,matrix,pose, minX,minY,maxZ, minX,minY,minZ, c,t); }
            case EAST  -> { vertexLine(builder,matrix,pose, maxX,minY,minZ, maxX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,maxZ, maxX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, maxX,maxY,maxZ, maxX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,maxZ, maxX,minY,minZ, c,t); }
            case WEST  -> { vertexLine(builder,matrix,pose, minX,minY,minZ, minX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, minX,minY,maxZ, minX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, minX,maxY,maxZ, minX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, minX,minY,maxZ, minX,minY,minZ, c,t); }
            case NORTH -> { vertexLine(builder,matrix,pose, maxX,minY,minZ, maxX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, minX,minY,minZ, minX,maxY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,minZ, minX,minY,minZ, c,t); vertexLine(builder,matrix,pose, maxX,maxY,minZ, minX,maxY,minZ, c,t); }
            case SOUTH -> { vertexLine(builder,matrix,pose, minX,minY,maxZ, minX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, maxX,minY,maxZ, maxX,maxY,maxZ, c,t); vertexLine(builder,matrix,pose, minX,minY,maxZ, maxX,minY,maxZ, c,t); vertexLine(builder,matrix,pose, minX,maxY,maxZ, maxX,maxY,maxZ, c,t); }
        }
    }

    private void emitLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, Vec3 camPos, LineCommand command) {
        Vec3 from = command.from().subtract(camPos), to = command.to().subtract(camPos);
        vertexLine(builder, matrix, pose, (float)from.x,(float)from.y,(float)from.z, (float)to.x,(float)to.y,(float)to.z, command.color(), command.thickness());
    }

    private void quad(LuminImmediateRenderer.PosColorQuads b, Matrix4f m, float x1,float y1,float z1,int c1, float x2,float y2,float z2,int c2, float x3,float y3,float z3,int c3, float x4,float y4,float z4,int c4) {
        b.vertex(m,x1,y1,z1,c1); b.vertex(m,x2,y2,z2,c2); b.vertex(m,x3,y3,z3,c3); b.vertex(m,x4,y4,z4,c4);
    }

    private void vertexLine(LuminImmediateRenderer.Lines b, Matrix4f m, PoseStack.Pose pose, float x1,float y1,float z1, float x2,float y2,float z2, int color, float thickness) {
        Vector3f n = getNormal(x1,y1,z1, x2,y2,z2);
        b.vertex(m, pose, x1,y1,z1, color, n.x,n.y,n.z, thickness);
        b.vertex(m, pose, x2,y2,z2, color, n.x,n.y,n.z, thickness);
    }

    private Vector3f getNormal(float x1,float y1,float z1, float x2,float y2,float z2) {
        float dx=x2-x1, dy=y2-y1, dz=z2-z1, len=Mth.sqrt(dx*dx+dy*dy+dz*dz);
        return new Vector3f(dx/len, dy/len, dz/len);
    }

    private record BlurredBoxCommand(AABB box, double blurStrength) {}
    private record FilledBoxCommand(AABB box, int bottomColor, int topColor) {}
    private record FilledSideCommand(AABB box, int color, Direction direction) {}
    private record OutlineBoxCommand(AABB box, int color, float thickness) {}
    private record SideOutlineCommand(AABB box, int color, float thickness, Direction direction) {}
    private record LineCommand(Vec3 from, Vec3 to, int color, float thickness) {}
}
