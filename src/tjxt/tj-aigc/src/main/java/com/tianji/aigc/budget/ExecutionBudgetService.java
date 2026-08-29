package com.tianji.aigc.budget;

import com.tianji.aigc.config.ToolResultHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Keeps the per-request budget state in the existing request-scoped holder, so the same
 * state is visible from the streaming thread and the tool-calling threads.
 */
@Component
public class ExecutionBudgetService {

    public static final String BUDGET_FIELD = "agentBudgetState";

    private final ExecutionBudgetProperties settings;

    public ExecutionBudgetService(ExecutionBudgetProperties settings) {
        this.settings = settings;
    }

    public BudgetState start(String requestId) {
        BudgetState state = new BudgetState(settings);
        ToolResultHolder.put(requestId, BUDGET_FIELD, state);
        return state;
    }

    public Optional<BudgetState> current(String requestId) {
        Object state = ToolResultHolder.get(requestId, BUDGET_FIELD);
        return state instanceof BudgetState budget ? Optional.of(budget) : Optional.empty();
    }

    public void clear(String requestId) {
        ToolResultHolder.remove(requestId);
    }
}
