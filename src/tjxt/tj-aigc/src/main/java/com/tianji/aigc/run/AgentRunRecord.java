package com.tianji.aigc.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured run record for one Agent execution. Persists operational evidence only.
 * Never stores: model reasoning, raw authorization, credentials, payment data or
 * complete sensitive user input.
 */
public class AgentRunRecord {

    private final String runId;
    private final String traceId;
    private final String sessionId;
    private final String route;
    private final String riskLevel;
    private final String promptId;
    private final String promptVersion;
    private final String promptChecksum;
    private final boolean promptOverridden;
    private final String modelProfile;
    private final String provider;
    private final String model;
    private final Instant startTime;
    private final Instant endTime;
    private final long latencyMillis;
    private final int modelCallCount;
    private final int toolCallCount;
    private final int policyDeniedCount;
    private final int inputTokens;
    private final int outputTokens;
    private final Double estimatedCost;
    private final boolean costKnown;
    private final String terminalStatus;
    private final boolean handoff;
    private final boolean budgetExceeded;
    private final List<ActionTraceEntry> actions;

    private AgentRunRecord(Builder b) {
        this.runId = b.runId;
        this.traceId = b.traceId;
        this.sessionId = b.sessionId;
        this.route = b.route;
        this.riskLevel = b.riskLevel;
        this.promptId = b.promptId;
        this.promptVersion = b.promptVersion;
        this.promptChecksum = b.promptChecksum;
        this.promptOverridden = b.promptOverridden;
        this.modelProfile = b.modelProfile;
        this.provider = b.provider;
        this.model = b.model;
        this.startTime = b.startTime;
        this.endTime = b.endTime;
        this.latencyMillis = b.latencyMillis;
        this.modelCallCount = b.modelCallCount;
        this.toolCallCount = b.toolCallCount;
        this.policyDeniedCount = b.policyDeniedCount;
        this.inputTokens = b.inputTokens;
        this.outputTokens = b.outputTokens;
        this.estimatedCost = b.estimatedCost;
        this.costKnown = b.costKnown;
        this.terminalStatus = b.terminalStatus;
        this.handoff = b.handoff;
        this.budgetExceeded = b.budgetExceeded;
        this.actions = List.copyOf(b.actions);
    }

    public static Builder builder(String runId, String traceId, String sessionId) {
        return new Builder(runId, traceId, sessionId);
    }

    public String runId() {
        return runId;
    }

    public String traceId() {
        return traceId;
    }

    public String sessionId() {
        return sessionId;
    }

    public String route() {
        return route;
    }

    public String riskLevel() {
        return riskLevel;
    }

    public String promptId() {
        return promptId;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String promptChecksum() {
        return promptChecksum;
    }

    public boolean promptOverridden() {
        return promptOverridden;
    }

    public String modelProfile() {
        return modelProfile;
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public long latencyMillis() {
        return latencyMillis;
    }

    public int modelCallCount() {
        return modelCallCount;
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public int policyDeniedCount() {
        return policyDeniedCount;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public Double estimatedCost() {
        return estimatedCost;
    }

    public boolean costKnown() {
        return costKnown;
    }

    public String terminalStatus() {
        return terminalStatus;
    }

    public boolean handoff() {
        return handoff;
    }

    public boolean budgetExceeded() {
        return budgetExceeded;
    }

    public List<ActionTraceEntry> actions() {
        return actions;
    }

    public static final class Builder {

        private final String runId;
        private final String traceId;
        private final String sessionId;
        private String route;
        private String riskLevel;
        private String promptId;
        private String promptVersion;
        private String promptChecksum;
        private boolean promptOverridden;
        private String modelProfile;
        private String provider;
        private String model;
        private Instant startTime = Instant.now();
        private Instant endTime;
        private long latencyMillis;
        private int modelCallCount;
        private int toolCallCount;
        private int policyDeniedCount;
        private int inputTokens;
        private int outputTokens;
        private Double estimatedCost;
        private boolean costKnown;
        private String terminalStatus;
        private boolean handoff;
        private boolean budgetExceeded;
        private final List<ActionTraceEntry> actions = new ArrayList<>();

        public Builder(String runId, String traceId, String sessionId) {
            this.runId = runId;
            this.traceId = traceId;
            this.sessionId = sessionId;
        }

        public Builder route(String route) {
            this.route = route;
            return this;
        }

        public Builder riskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder prompt(String promptId, String version, String checksum, boolean overridden) {
            this.promptId = promptId;
            this.promptVersion = version;
            this.promptChecksum = checksum;
            this.promptOverridden = overridden;
            return this;
        }

        public Builder model(String profile, String provider, String model) {
            this.modelProfile = profile;
            this.provider = provider;
            this.model = model;
            return this;
        }

        public String provider() {
            return provider;
        }

        public String model() {
            return model;
        }

        public Instant startTime() {
            return startTime;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder latencyMillis(long latencyMillis) {
            this.latencyMillis = latencyMillis;
            return this;
        }

        public Builder modelCallCount(int count) {
            this.modelCallCount = count;
            return this;
        }

        public Builder toolCallCount(int count) {
            this.toolCallCount = count;
            return this;
        }

        public Builder policyDeniedCount(int count) {
            this.policyDeniedCount = count;
            return this;
        }

        public Builder tokens(int inputTokens, int outputTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder estimatedCost(Double cost, boolean known) {
            this.estimatedCost = cost;
            this.costKnown = known;
            return this;
        }

        public Builder terminalStatus(String status) {
            this.terminalStatus = status;
            return this;
        }

        public Builder handoff(boolean handoff) {
            this.handoff = handoff;
            return this;
        }

        public Builder budgetExceeded(boolean exceeded) {
            this.budgetExceeded = exceeded;
            return this;
        }

        public Builder recordAction(ActionTraceEntry entry) {
            this.actions.add(entry);
            return this;
        }

        public AgentRunRecord build() {
            return new AgentRunRecord(this);
        }
    }
}
