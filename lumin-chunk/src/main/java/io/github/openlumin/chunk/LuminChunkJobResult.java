package io.github.openlumin.chunk;

/**
 * 任务结果：终态 + 产出（或失败原因）。
 * <ul>
 *   <li>{@link State#COMPLETED}：{@code output} 非 null（任务显式返回 null 除外）</li>
 *   <li>{@link State#CANCELLED}：{@code output} 恒为 null（完成后的产出已由构建器关闭）</li>
 *   <li>{@link State#FAILED}：{@code failure} 非 null，任务抛出未检异常</li>
 * </ul>
 *
 * @param <O> 产出类型
 */
public final class LuminChunkJobResult<O extends LuminBuildOutput> {

    public enum State {COMPLETED, CANCELLED, FAILED}

    private final State state;
    private final O output;
    private final Throwable failure;

    private LuminChunkJobResult(State state, O output, Throwable failure) {
        this.state = state;
        this.output = output;
        this.failure = failure;
    }

    public static <O extends LuminBuildOutput> LuminChunkJobResult<O> completed(O output) {
        return new LuminChunkJobResult<>(State.COMPLETED, output, null);
    }

    public static <O extends LuminBuildOutput> LuminChunkJobResult<O> cancelled() {
        return new LuminChunkJobResult<>(State.CANCELLED, null, null);
    }

    public static <O extends LuminBuildOutput> LuminChunkJobResult<O> failed(Throwable failure) {
        if (failure == null) {
            throw new NullPointerException("failure");
        }
        return new LuminChunkJobResult<>(State.FAILED, null, failure);
    }

    public State state() {
        return state;
    }

    /** 仅 {@link State#COMPLETED} 时可能非 null。 */
    public O output() {
        return output;
    }

    /** 仅 {@link State#FAILED} 时非 null。 */
    public Throwable failure() {
        return failure;
    }

    @Override
    public String toString() {
        return "LuminChunkJobResult{state=" + state
                + (output != null ? ", output=" + output : "")
                + (failure != null ? ", failure=" + failure : "") + "}";
    }
}
