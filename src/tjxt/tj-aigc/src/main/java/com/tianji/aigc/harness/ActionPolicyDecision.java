package com.tianji.aigc.harness;

public record ActionPolicyDecision(boolean allowed, String reason, String reasonCode) {

    public static ActionPolicyDecision allow(String reason) {
        return new ActionPolicyDecision(true, reason, null);
    }

    public static ActionPolicyDecision deny(String reason) {
        return new ActionPolicyDecision(false, reason, null);
    }

    public static ActionPolicyDecision deny(String reasonCode, String reason) {
        return new ActionPolicyDecision(false, reason, reasonCode);
    }
}
