package com.tianji.aigc.budget;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Deterministic per-request execution budget. These limits are enforced in the
 * runtime layer (harness / agent streaming), never via prompt instructions.
 */
@Configuration
@ConfigurationProperties(prefix = "tj.ai.execution-budget")
public class ExecutionBudgetProperties {

    private int maxModelCalls = 4;

    private int maxToolCalls = 8;

    /** Consecutive calls with the same action signature allowed before blocking. */
    private int maxSameAction = 2;

    /** Detection window for A-B-A-B style loops. */
    private int loopWindow = 6;

    private int maxRuntimeSeconds = 120;

    private long maxToolMillis = 15_000;

    /** 0 disables the limit (provider may not support it). */
    private int maxOutputTokens = 0;

    /** Upper bound on estimated run cost when pricing is configured; 0 disables. */
    private double maxEstimatedCost = 0;

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxSameAction() {
        return maxSameAction;
    }

    public void setMaxSameAction(int maxSameAction) {
        this.maxSameAction = maxSameAction;
    }

    public int getLoopWindow() {
        return loopWindow;
    }

    public void setLoopWindow(int loopWindow) {
        this.loopWindow = loopWindow;
    }

    public int getMaxRuntimeSeconds() {
        return maxRuntimeSeconds;
    }

    public void setMaxRuntimeSeconds(int maxRuntimeSeconds) {
        this.maxRuntimeSeconds = maxRuntimeSeconds;
    }

    public long getMaxToolMillis() {
        return maxToolMillis;
    }

    public void setMaxToolMillis(long maxToolMillis) {
        this.maxToolMillis = maxToolMillis;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getMaxEstimatedCost() {
        return maxEstimatedCost;
    }

    public void setMaxEstimatedCost(double maxEstimatedCost) {
        this.maxEstimatedCost = maxEstimatedCost;
    }
}
