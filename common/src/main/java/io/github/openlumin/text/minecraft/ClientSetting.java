package io.github.openlumin.text.minecraft;

/**
 * 客户端设置存根 — common模块使用，平台实现可替换。
 */
public class ClientSetting {

    public static final ClientSetting INSTANCE = new ClientSetting();

    public final BooleanSetting fontAntiAliasing = new BooleanSetting(true);

    private ClientSetting() {}

    public static final class BooleanSetting {
        private final boolean defaultValue;

        BooleanSetting(boolean defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean getValue() {
            return defaultValue;
        }
    }
}
