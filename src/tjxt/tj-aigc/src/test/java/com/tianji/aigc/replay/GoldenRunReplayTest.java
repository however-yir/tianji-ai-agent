package com.tianji.aigc.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.aigc.harness.ActionPolicyDecision;
import com.tianji.aigc.harness.ActionPolicyGuard;
import com.tianji.aigc.harness.AgentAction;
import com.tianji.aigc.reason.ReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays every golden run fixture: policy decisions and reason codes must match the
 * recorded contract. Change the fixture when the agent behavior intentionally changes -
 * git diff then shows the behavior change.
 */
class GoldenRunReplayTest {

    private static final Path GOLDEN_DIR = findGoldenDir();

    private static Path findGoldenDir() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path candidate = current.resolve("tests/golden-runs");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        throw new IllegalStateException("tests/golden-runs 未找到 (cwd=" + System.getProperty("user.dir") + ")");
    }

    private final ActionPolicyGuard policyGuard = new ActionPolicyGuard();

    static Stream<Path> fixtures() throws Exception {
        try (Stream<Path> paths = Files.list(GOLDEN_DIR)) {
            return paths.filter(p -> p.toString().endsWith(".json")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void shouldReplayGoldenRunContract(Path fixture) throws Exception {
        JsonNode golden = new ObjectMapper().readTree(Files.readString(fixture));
        for (JsonNode expectation : golden.path("policy")) {
            String actionType = expectation.path("actionType").asText();
            boolean expectedAllowed = expectation.path("expectedAllowed").asBoolean();
            AgentAction action = new AgentAction(
                    actionType, "CONSULT", "Tools.x", "replay-" + fixture.getFileName(),
                    "replay-action", "session-replay", 10001L,
                    Map.of("courseId", 1L, "courseIds", List.of(1L), "query", "java"));

            ActionPolicyDecision decision = policyGuard.decide(action);

            assertThat(decision.allowed())
                    .as("[%s] %s", fixture.getFileName(), actionType)
                    .isEqualTo(expectedAllowed);
            if (!expectedAllowed && expectation.hasNonNull("reasonCode")) {
                assertThat(decision.reasonCode())
                        .as("[%s] %s", fixture.getFileName(), actionType)
                        .isEqualTo(expectation.path("reasonCode").asText());
            }
        }
        assertThat(golden.path("terminal").asText()).isEqualTo("STOP");
        assertThat(golden.path("id").asText()).isNotBlank();
    }

    @Test
    void shouldExposeReasonCodeContract() {
        assertThat(ReasonCode.DUPLICATE_ACTION.name()).isEqualTo("DUPLICATE_ACTION");
        assertThat(ReasonCode.ACTION_LOOP.name()).isEqualTo("ACTION_LOOP");
        assertThat(ReasonCode.BUDGET_EXCEEDED.name()).isEqualTo("BUDGET_EXCEEDED");
    }
}
