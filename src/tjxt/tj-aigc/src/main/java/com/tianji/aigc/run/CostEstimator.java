package com.tianji.aigc.run;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Computes estimated API cost only when a configured price exists; otherwise the run
 * records "cost unavailable". The explicit costKnown flag prevents 0-cost misreads.
 */
@Component
public class CostEstimator {

    private final ModelPricingProperties pricing;

    public CostEstimator(ModelPricingProperties pricing) {
        this.pricing = pricing;
    }

    public Optional<Estimate> estimate(String provider, String model, int inputTokens, int outputTokens) {
        ModelPricingProperties.ModelPrice price = pricing.price(provider, model);
        if (price == null || inputTokens < 0 || outputTokens < 0) {
            return Optional.empty();
        }
        double cost = (inputTokens / 1_000_000.0) * price.getInputPerMillion()
                + (outputTokens / 1_000_000.0) * price.getOutputPerMillion();
        return Optional.of(new Estimate(cost, price.getCurrency()));
    }

    public record Estimate(double amount, String currency) {
    }
}
