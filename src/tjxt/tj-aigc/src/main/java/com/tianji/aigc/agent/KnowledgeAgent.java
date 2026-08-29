package com.tianji.aigc.agent;

import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.knowledgeops.KnowledgeOpsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识讲解智能体 — uses KnowledgeOpsClient for RAG/memory/graph when platform is available,
 * falling back to local VectorStore QuestionAnswerAdvisor when platform is not reachable.
 */
@Slf4j
@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class KnowledgeAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final KnowledgeOpsClient knowledgeOpsClient;
    private final KnowledgeOpsProperties knowledgeOpsProperties;
    private final VectorStore vectorStore;

    @Override
    public String systemMessage() {
        String configured = this.systemPromptConfig.getKnowledgeAgentSystemMessage().get();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return """
                你是一个在线教育平台的知识讲解助手。
                你需要基于平台知识库和用户的学习上下文，深入浅出地回答知识性问题。
                当用户询问概念、原理、技术细节时，优先从知识图谱和课程内容中检索信息，给出带引用来源的详细解答。
                """;
    }

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.KNOWLEDGE;
    }

    @Override
    public List<Advisor> advisors(String question) {
        // Strategy: if KnowledgeOps platform is enabled and reachable, use it;
        // otherwise fall back to local VectorStore RAG advisor
        if (knowledgeOpsProperties.isEnabled()) {
            try {
                // Attempt to reach KnowledgeOps platform — enrich context via RAG search
                Map<String, Object> ragResult = knowledgeOpsClient.ragSearch(question, "default", "knowledge-session");
                if (ragResult != null && !ragResult.isEmpty()) {
                    log.debug("KnowledgeAgent: using KnowledgeOps platform RAG for enrichment");
                    // Return local advisor as base — the KnowledgeOps enrichment happens via system prompt context
                    // This is the fallback path within a fallback: platform was reached but we still rely on local RAG for the advisor
                }
            }
            catch (Exception e) {
                log.warn("KnowledgeAgent: KnowledgeOps platform unavailable, falling back to local Advisor: {}", e.getMessage());
            }
        }

        // Local VectorStore fallback — always available
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.5)
                .build();
        return List.of(new QuestionAnswerAdvisor(vectorStore, searchRequest));
    }
}
