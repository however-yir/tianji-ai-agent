package com.tianji.aigc.run;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configurable price table. Absent pricing means the run records {costKnown=false}
 * instead of inventing a 0-cost estimate.
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.model-pricing")
public class ModelPricingProperties {

    private final Map<String, Price> providers = new LinkedHashMap<>();

    public Map<String, Price> getProviders() {
        return providers;
    }

    public static class Price {

        private Map<String, ModelPrice> models = new LinkedHashMap<>();

        public Map<String, ModelPrice> getModels() {
            return models;
        }
    }

    public static class ModelPrice {

        private double inputPerMillion;
        private double outputPerMillion;
        private String currency = "USD";

        public double getInputPerMillion() {
            return inputPerMillion;
        }

        public void setInputPerMillion(double inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public double getOutputPerMillion() {
            return outputPerMillion;
        }

        public void setOutputPerMillion(double outputPerMillion) {
            this.outputPerMillion = outputPerMillion;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    /** Best-effort price lookup; returns null when no price is configured for the pair. */
    public ModelPrice price(String provider, String model) {
        ModelPricingProperties.Price price = providers.get(provider);
        if (price == null) {
            return null;
        }
        return price.getModels().get(model);
    }
}
