package com.tianji.aigc.harness;

public record ActionPolicyDecision(boolean allowed, String reason) {

    public static ActionPolicyDecision allow(String reason) {
        return new ActionPolicyDecision(true, reason);
    }

    public static ActionPolicyDecision deny(String reason) {
        return new ActionPolicyDecision(false, reason);
    }
}
