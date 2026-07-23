package net.minecraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * NeoForge适配层：模拟Fabric的RenderPipelines常量
 */
public class RenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = new RenderPipeline.Snippet();
    public static final RenderPipeline.Snippet DEBUG_FILLED_SNIPPET = new RenderPipeline.Snippet();
    public static final RenderPipeline.Snippet LINES_SNIPPET = new RenderPipeline.Snippet();
    public static final RenderPipeline.Snippet POST_PROCESSING_SNIPPET = new RenderPipeline.Snippet();
}
