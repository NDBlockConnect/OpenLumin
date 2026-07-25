package io.github.openlumin.shim.com.mojang.blaze3d.pipeline;

import io.github.openlumin.shim.com.mojang.blaze3d.platform.CompareOp;

/**
 * NeoForge适配层：模拟Fabric的DepthStencilState API
 */
public class DepthStencilState {

    private final boolean depthTestEnabled;
    private final boolean depthWriteEnabled;
    private final CompareOp depthCompareOp;

    public DepthStencilState(CompareOp depthCompareOp, boolean depthWriteEnabled) {
        this.depthTestEnabled = true;
        this.depthWriteEnabled = depthWriteEnabled;
        this.depthCompareOp = depthCompareOp;
    }

    public DepthStencilState(boolean depthTestEnabled, boolean depthWriteEnabled, CompareOp depthCompareOp) {
        this.depthTestEnabled = depthTestEnabled;
        this.depthWriteEnabled = depthWriteEnabled;
        this.depthCompareOp = depthCompareOp;
    }

    public boolean isDepthTestEnabled() {
        return depthTestEnabled;
    }

    public boolean isDepthWriteEnabled() {
        return depthWriteEnabled;
    }

    public CompareOp getDepthCompareOp() {
        return depthCompareOp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean depthTestEnabled = true;
        private boolean depthWriteEnabled = true;
        private CompareOp depthCompareOp = CompareOp.LESS;

        public Builder depthTest(boolean enabled) {
            this.depthTestEnabled = enabled;
            return this;
        }

        public Builder depthWrite(boolean enabled) {
            this.depthWriteEnabled = enabled;
            return this;
        }

        public Builder depthCompareOp(CompareOp op) {
            this.depthCompareOp = op;
            return this;
        }

        public DepthStencilState build() {
            return new DepthStencilState(depthTestEnabled, depthWriteEnabled, depthCompareOp);
        }
    }
}
