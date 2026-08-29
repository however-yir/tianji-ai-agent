package com.tianji.aigc.budget;

import com.tianji.aigc.reason.ReasonCode;

public record BudgetDecision(boolean allowed, ReasonCode reasonCode, String message) {

    public static BudgetDecision ok() {
        return new BudgetDecision(true, ReasonCode.NOT_APPLICABLE, "");
    }

    public static BudgetDecision deny(ReasonCode code, String message) {
        return new BudgetDecision(false, code, message);
    }
}
