package io.github.openlumin;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * fabric-1.21.10 override：
 * MC 1.21.10 的 VertexFormatElement 构造器要求非 UV usage 的 index 必须为 0
 * （supportsUsage: index==0 → always OK; index!=0 && usage!=UV → throws）。
 * common 里的注册使用了 index=2,4,1,1，在 1.21.10 全部抛 IllegalStateException。
 * 这里统一改为 index=0。
 */
public class LuminVertexFormats {

    private static final int ROUND_INNER_RECT_ID = findNextId();
    private static final int ROUND_RADIUS_ID = findNextId(ROUND_INNER_RECT_ID + 1);
    private static final int ROUND_OUTLINE_WIDTH_ID = findNextId(ROUND_RADIUS_ID + 1);
    private static final int LINE_WIDTH_ID = findNextId(ROUND_OUTLINE_WIDTH_ID + 1);

    // 1.21.10 fix: index 必须为 0（非 UV usage 不允许 index != 0）
    public static final VertexFormatElement ROUND_INNER_RECT = VertexFormatElement.register(ROUND_INNER_RECT_ID, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
    public static final VertexFormatElement ROUND_RADIUS = VertexFormatElement.register(ROUND_RADIUS_ID, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);
    public static final VertexFormatElement ROUND_OUTLINE_WIDTH = VertexFormatElement.register(ROUND_OUTLINE_WIDTH_ID, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);
    public static final VertexFormatElement LINE_WIDTH = VertexFormatElement.register(LINE_WIDTH_ID, 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 1);

    public static final VertexFormat POSITION_COLOR_NORMAL_LINE_WIDTH = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("Normal", VertexFormatElement.NORMAL)
            .add("LineWidth", LINE_WIDTH)
            .build();

    public static final VertexFormat ROUND_RECT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .build();

    public static final VertexFormat ROUND_RECT_OUTLINE = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .add("OutlineWidth", ROUND_OUTLINE_WIDTH)
            .build();

    public static final VertexFormat TEXTURE = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .build();

    private static int findNextId() {
        return findNextId(0);
    }

    private static int findNextId(int start) {
        for (int i = Math.max(0, start); i < VertexFormatElement.MAX_COUNT; i++) {
            if (VertexFormatElement.byId(i) == null) {
                return i;
            }
        }
        throw new IllegalStateException("VertexFormatElement count limit exceeded");
    }

}
