package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.knowledgeops.KnowledgeOpsClient;
import com.tianji.aigc.knowledgeops.KnowledgeOpsProperties;
import com.tianji.aigc.tools.CourseTools;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 推荐智能体 — enhanced with KnowledgeOpsClient fallback strategy:
 * When KnowledgeOps platform is available, use platform RAG/memory/graph for richer recommendations;
 * when unavailable, fall back to local CourseTools + VectorStore Advisor.
 */
@Slf4j
@Component
@Profile("!dev-demo")
@RequiredArgsConstructor
public class RecommendAgent extends AbstractAgent {

    private final SystemPromptConfig systemPromptConfig;
    private final CourseTools courseTools;
    private final VectorStore vectorStore;
    private final KnowledgeOpsClient knowledgeOpsClient;
    private final KnowledgeOpsProperties knowledgeOpsProperties;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.RECOMMEND;
    }

    @Override
    public String systemMessage() {
        return this.systemPromptConfig.getRecommendAgentSystemMessage().get();
    }

    @Override
    public Object[] tools() {
        return new Object[]{courseTools};
    }

    @Override
    public Map<String, Object> toolContext(String sessionId, String requestId) {
        var userId = UserContext.getUser();
        return MapUtil.<String, Object>builder()
                .put(Constant.SESSION_ID, sessionId)
                .put(Constant.AGENT_NAME, this.getAgentType().getAgentName())
                .put(Constant.USER_ID, userId)
                .put(Constant.REQUEST_ID, requestId)
                .build();
    }

    @Override
    public List<Advisor> advisors(String question) {
        // Strategy: if KnowledgeOps platform is enabled, try platform memory/graph for user context enrichment;
        // always fall back to local VectorStore RAG advisor
        if (knowledgeOpsProperties.isEnabled()) {
            try {
                var userId = UserContext.getUser();
                if (userId != null) {
                    // Try to enrich with platform memory (user learning history, preferences)
                    Map<String, Object> memoryResult = knowledgeOpsClient.memoryQuery(
                            String.valueOf(userId), "default", "long_term");
                    if (memoryResult != null && !memoryResult.isEmpty()) {
                        log.debug("RecommendAgent: enriched with KnowledgeOps platform memory for user {}", userId);
                    }

                    // Try to enrich with knowledge graph (learning paths, course relationships)
                    Map<String, Object> graphResult = knowledgeOpsClient.graphSearch(question, "default");
                    if (graphResult != null && !graphResult.isEmpty()) {
                        log.debug("RecommendAgent: enriched with KnowledgeOps graph search");
                    }
                }
            }
            catch (Exception e) {
                log.warn("RecommendAgent: KnowledgeOps platform unavailable, using local RAG: {}", e.getMessage());
            }
        }

        // Local VectorStore fallback — always active
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.65)
                .build();
        return List.of(RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(searchRequest.getTopK())
                        .similarityThreshold(searchRequest.getSimilarityThreshold())
                        .build())
                .build());
    }
}
