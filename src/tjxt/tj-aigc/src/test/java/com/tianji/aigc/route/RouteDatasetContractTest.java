package com.tianji.aigc.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.aigc.enums.AgentTypeEnum;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the production RouteSafetyPolicy against the checked-in 160-case dataset. HIGH
 * risk cases and explicit human-request cases must escalate; LOW risk business intents
 * must not. The dataset is therefore not only a Python fixture gate - it also proves the
 * Java policy vocabulary stays aligned with the contract.
 */
class RouteDatasetContractTest {

    private static final Path DATASET = findDataset();

    private static Path findDataset() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path candidate = current.resolve("docs/evaluation/route-agent-dataset.jsonl");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        throw new IllegalStateException("route-agent-dataset.jsonl not found");
    }

    @Test
    void shouldKeepPolicyVocabularyAlignedWithDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        int checked = 0;
        for (String line : Files.readAllLines(DATASET)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode row = mapper.readTree(line);
            String input = row.path("input").asText();
            String expected = row.path("expectedAgent").asText();
            String risk = row.path("riskLevel").asText("LOW");

            if ("HIGH".equals(risk)) {
                // high-risk cases must escalate even for a confidently-proposed agent
                assertThat(RouteSafetyPolicy.requiresHumanHandoff(input,
                        AgentTypeEnum.RECOMMEND.getAgentName(), 0.9))
                        .as("[%s] HIGH risk must escalate: %s", row.path("id").asText(), input)
                        .isTrue();
            }
            else if (AgentTypeEnum.HUMAN_HANDOFF.getAgentName().equals(expected)) {
                // low-confidence handoff cases escalate at low confidence
                assertThat(RouteSafetyPolicy.requiresHumanHandoff(input,
                        AgentTypeEnum.KNOWLEDGE.getAgentName(), 0.2))
                        .as("[%s] low-confidence handoff must escalate", row.path("id").asText())
                        .isTrue();
            }
            else {
                assertThat(RouteSafetyPolicy.requiresHumanHandoff(input, expected, 0.9))
                        .as("[%s] LOW risk business intent must not escalate: %s", row.path("id").asText(), input)
                        .isFalse();
            }
            checked++;
        }
        assertThat(checked).isEqualTo(160);
    }
}
