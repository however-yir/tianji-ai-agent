package com.tianji.aigc.budget;

import com.tianji.aigc.reason.ReasonCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetStateTest {

    private ExecutionBudgetProperties settings() {
        ExecutionBudgetProperties p = new ExecutionBudgetProperties();
        p.setMaxModelCalls(4);
        p.setMaxToolCalls(8);
        p.setMaxSameAction(2);
        p.setMaxRuntimeSeconds(120);
        return p;
    }

    @Test
    void shouldAllowNormalRequestWithinBudget() {
        BudgetState state = new BudgetState(settings());

        for (int i = 0; i < 3; i++) {
            assertThat(state.beforeModelCall().allowed()).isTrue();
        }
        for (int i = 0; i < 5; i++) {
            BudgetDecision d = state.beforeToolCall();
            assertThat(d.allowed()).isTrue();
            assertThat(state.beforeAction("course.query",
                    BudgetState.signature("course.query", "id=" + (i + 1))).allowed()).isTrue();
        }
        assertThat(state.budgetExceeded()).isFalse();
    }

    @Test
    void shouldBlockWhenModelCallBudgetExceeded() {
        BudgetState state = new BudgetState(settings());

        for (int i = 0; i < 4; i++) {
            assertThat(state.beforeModelCall().allowed()).isTrue();
        }
        BudgetDecision denied = state.beforeModelCall();

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo(ReasonCode.TOO_MANY_MODEL_CALLS);
    }

    @Test
    void shouldBlockWhenToolCallBudgetExceeded() {
        BudgetState state = new BudgetState(settings());

        for (int i = 0; i < 8; i++) {
            state.beforeToolCall();
        }
        BudgetDecision denied = state.beforeToolCall();

        assertThat(denied.reasonCode()).isEqualTo(ReasonCode.TOO_MANY_TOOL_CALLS);
    }

    @Test
    void shouldBlockRepeatedSameAction() {
        BudgetState state = new BudgetState(settings());
        String s = BudgetState.signature("course.query", "id=1589905661084430337");

        assertThat(state.beforeAction("course.query", s).allowed()).isTrue();
        assertThat(state.beforeAction("course.query", s).allowed()).isTrue();
        BudgetDecision third = state.beforeAction("course.query", s);

        assertThat(third.allowed()).isFalse();
        assertThat(third.reasonCode()).isEqualTo(ReasonCode.DUPLICATE_ACTION);
    }

    @Test
    void shouldDetectAbAbLoop() {
        BudgetState state = new BudgetState(settings());
        String a = BudgetState.signature("course.query", "id=1");
        String b = BudgetState.signature("knowledgeops.rag_query", "q=java");

        state.beforeAction("course.query", a);
        state.beforeAction("knowledgeops.rag_query", b);
        state.beforeAction("course.query", a);
        BudgetDecision fourth = state.beforeAction("knowledgeops.rag_query", b);

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.reasonCode()).isEqualTo(ReasonCode.ACTION_LOOP);
    }

    @Test
    void shouldAllowDifferentArgumentsOfSameAction() {
        BudgetState state = new BudgetState(settings());

        String id1 = BudgetState.signature("course.query", "id=1");
        String id2 = BudgetState.signature("course.query", "id=2");

        assertThat(state.beforeAction("course.query", id1).allowed()).isTrue();
        assertThat(state.beforeAction("course.query", id2).allowed()).isTrue();
        assertThat(state.beforeAction("course.query", id1).allowed()).isTrue();
    }

    @Test
    void shouldRejectWhenDeadlineExceeded() throws InterruptedException {
        ExecutionBudgetProperties p = settings();
        p.setMaxRuntimeSeconds(1);
        BudgetState state = new BudgetState(p);

        Thread.sleep(1100);

        BudgetDecision denied = state.beforeModelCall();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reasonCode()).isEqualTo(ReasonCode.BUDGET_EXCEEDED);
    }

    @Test
    void shouldTrackTokensAndDeniedCount() {
        BudgetState state = new BudgetState(settings());

        state.recordTokens(12, 34);
        state.recordTokens(5, 6);
        state.recordPolicyDenied();

        assertThat(state.inputTokens()).isEqualTo(17);
        assertThat(state.outputTokens()).isEqualTo(40);
        assertThat(state.policyDeniedCount()).isEqualTo(1);
    }

    @Test
    void shouldNotIncludeRawUserContentInSignature() {
        String s = BudgetState.signature("course.query", "{\"courseId\":\"13812345678\"}");

        assertThat(s).doesNotContain("13812345678");
    }
}
