package com.tianji.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.model")
public class ModelProviderProperties {

    /**
     * 默认供应商
     */
    private String defaultProvider = "dashscope";

    /**
     * 默认温度
     */
    private double defaultTemperature = 0.7;

    /**
     * 可用供应商及其配置
     * 示例:
     *   providers:
     *     dashscope:
     *       label: 阿里云百炼
     *       default-model: qwen-plus
     *       models:
     *         - name: qwen-plus
     *           label: Qwen-Plus
     *         - name: qwen-max
     *           label: Qwen-Max
     *     openai:
     *       label: OpenAI
     *       default-model: gpt-4o
     *       models:
     *         - name: gpt-4o
     *           label: GPT-4o
     *         - name: gpt-4o-mini
     *           label: GPT-4o Mini
     */
    private Map<String, ProviderConfig> providers = Map.of();

    @Data
    public static class ProviderConfig {
        private String label;
        private String defaultModel;
        private List<ModelConfig> models;
    }

    @Data
    public static class ModelConfig {
        private String name;
        private String label;
    }
}
