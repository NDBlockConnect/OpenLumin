package io.github.openlumin.chunk.schedule;

/**
 * 任务延迟档（原理参照：更新紧急度分层）。
 * <ul>
 *   <li>{@link #ZERO_FRAMES}：本帧内必须完成（阻塞等待）——近距/玩家触发的更新；</li>
 *   <li>{@link #ONE_FRAME}：允许延迟一帧完成（下帧再收敛）；</li>
 *   <li>{@link #ALWAYS_FRAME}：仅在帧预算有余量时提交，可无限期延后——远景/非紧急更新。</li>
 * </ul>
 */
public enum LuminDeferMode {
    ZERO_FRAMES,
    ONE_FRAME,
    ALWAYS_FRAME;

    /** 该档是否不受上传字节预算约束（本帧阻塞者不设上传上限）。 */
    public boolean allowsUnlimitedUploadDuration() {
        return this == ZERO_FRAMES;
    }
}
