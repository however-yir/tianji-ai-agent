package com.tianji.aigc.config;

/**
 * 请求级别的模型选项持有器，基于 ThreadLocal 实现。
 * 用于在单次请求链路中传递用户选择的 provider / model / temperature，
 * 避免修改 ChatService 接口签名。
 */
public final class ModelOptionsHolder {

    private static final ThreadLocal<ModelOptions> HOLDER = new ThreadLocal<>();

    private ModelOptionsHolder() {}

    public static void set(ModelOptions options) {
        HOLDER.set(options);
    }

    public static ModelOptions get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record ModelOptions(String provider, String model, Double temperature) {

        public boolean hasProvider() {
            return provider != null && !provider.isBlank();
        }

        public boolean hasModel() {
            return model != null && !model.isBlank();
        }

        public boolean hasTemperature() {
            return temperature != null;
        }
    }
}
