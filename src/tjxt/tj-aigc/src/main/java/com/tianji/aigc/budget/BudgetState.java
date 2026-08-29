package com.tianji.aigc.budget;

import com.tianji.aigc.reason.ReasonCode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-request budget accounting. One instance per run (keyed by requestId), never shared
 * across sessions, so concurrent requests are isolated by construction.
 */
public class BudgetState {

    private final ExecutionBudgetProperties settings;
    private final long startedAtMillis;
    private final long deadlineMillis;

    private int modelCalls;
    private int toolCalls;
    private long toolCallsMillis;
    private int policyDeniedCount;
    private int inputTokens;
    private int outputTokens;
    private boolean modelFallbackUsed;
    private final Map<String, Integer> actionCounts = new LinkedHashMap<>();
    private final Deque<String> recentSignatures = new ArrayDeque<>();
    private final Map<String, String> lastFailure = new LinkedHashMap<>();

    public BudgetState(ExecutionBudgetProperties settings) {
        this.settings = settings;
        this.startedAtMillis = System.currentTimeMillis();
        this.deadlineMillis = startedAtMillis + settings.getMaxRuntimeSeconds() * 1000L;
    }

    public synchronized BudgetDecision beforeModelCall() {
        if (expired()) {
            return BudgetDecision.deny(ReasonCode.BUDGET_EXCEEDED, "运行预算超时");
        }
        if (modelCalls + 1 > settings.getMaxModelCalls()) {
            return BudgetDecision.deny(ReasonCode.TOO_MANY_MODEL_CALLS, "模型调用次数超出预算");
        }
        modelCalls++;
        return BudgetDecision.ok();
    }

    public synchronized BudgetDecision beforeToolCall() {
        if (expired()) {
            return BudgetDecision.deny(ReasonCode.BUDGET_EXCEEDED, "运行预算超时");
        }
        if (toolCalls + 1 > settings.getMaxToolCalls()) {
            return BudgetDecision.deny(ReasonCode.TOO_MANY_TOOL_CALLS, "工具调用次数超出预算");
        }
        toolCalls++;
        return BudgetDecision.ok();
    }

    /**
     * Check before executing an action: count per-signature duplicates and detect
     * A-B-A-B style loops. Signatures use actionType + hash of normalized arguments,
     * never raw user content.
     */
    public synchronized BudgetDecision beforeAction(String actionType, String signature) {
        if (expired()) {
            return BudgetDecision.deny(ReasonCode.BUDGET_EXCEEDED, "运行预算超时");
        }
        int count = actionCounts.merge(signature, 1, Integer::sum);
        if (count > settings.getMaxSameAction()) {
            return BudgetDecision.deny(ReasonCode.DUPLICATE_ACTION,
                    "同一动作重复执行超过阈值: " + actionType);
        }
        recentSignatures.addLast(signature);
        while (recentSignatures.size() > settings.getLoopWindow()) {
            recentSignatures.removeFirst();
        }
        if (detectAbAbLoop(recentSignatures)) {
            return BudgetDecision.deny(ReasonCode.ACTION_LOOP,
                    "检测到 A-B-A-B 循环调用: " + actionType);
        }
        return BudgetDecision.ok();
    }

    private boolean detectAbAbLoop(Deque<String> signatures) {
        int n = signatures.size();
        if (n < 4) {
            return false;
        }
        String[] s = signatures.toArray(new String[0]);
        for (int i = 0; i + 3 < n; i++) {
            if (s[i].equals(s[i + 2]) && s[i + 1].equals(s[i + 3])
                    && !s[i].equals(s[i + 1])) {
                return true;
            }
        }
        return false;
    }

    public synchronized void afterToolCall(long millis) {
        toolCallsMillis += millis;
        if (toolCallsMillis > settings.getMaxToolMillis()) {
            lastFailure.put("toolTimeout", "工具累计耗时超出预算: " + toolCallsMillis + "ms");
        }
    }

    public synchronized void recordTokens(int in, int out) {
        if (in > 0) {
            inputTokens += in;
        }
        if (out > 0) {
            outputTokens += out;
        }
    }

    public synchronized void recordPolicyDenied() {
        policyDeniedCount++;
    }

    public synchronized void recordModelFallback() {
        modelFallbackUsed = true;
    }

    public synchronized long remainingMillis() {
        return deadlineMillis - System.currentTimeMillis();
    }

    public synchronized boolean expired() {
        return System.currentTimeMillis() >= deadlineMillis;
    }

    public synchronized boolean toolBudgetExceeded() {
        return !lastFailure.containsKey("toolTimeout") && toolCallsMillis > settings.getMaxToolMillis();
    }

    public synchronized long startedAtMillis() {
        return startedAtMillis;
    }

    public synchronized long deadlineMillis() {
        return deadlineMillis;
    }

    public synchronized int modelCalls() {
        return modelCalls;
    }

    public synchronized int toolCalls() {
        return toolCalls;
    }

    public synchronized long toolCallsMillis() {
        return toolCallsMillis;
    }

    public synchronized int inputTokens() {
        return inputTokens;
    }

    public synchronized int outputTokens() {
        return outputTokens;
    }

    public synchronized int policyDeniedCount() {
        return policyDeniedCount;
    }

    public synchronized boolean modelFallbackUsed() {
        return modelFallbackUsed;
    }

    public synchronized boolean budgetExceeded() {
        return expired();
    }

    /** Safe opaque signature: type + short hash of normalized arguments. */
    public static String signature(String actionType, String normalizedArgs) {
        return actionType + ":" + shortHash(normalizedArgs == null ? "{}" : normalizedArgs);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .substring(0, 12);
        }
        catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
