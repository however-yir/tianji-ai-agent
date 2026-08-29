package com.tianji.aigc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Business-level model profiles (not a model gateway): few named profiles, each mapping
 * an agent role to a deterministic provider/model/temperature configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.model-profiles")
public class ModelProfileProperties {

    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    /** Agent type (enum name) -> profile key, e.g. ROUTE -> router. */
    private final Map<String, String> agentMapping = new LinkedHashMap<>();

    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public Map<String, String> getAgentMapping() {
        return agentMapping;
    }

    public static class Profile {

        private String provider = "openai";

        private String model = "qwen-plus";

        private Double temperature = 0.7;

        private int maxTokens = 0;

        private int timeoutSeconds = 30;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
