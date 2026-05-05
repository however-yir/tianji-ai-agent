package com.tianji.aigc.knowledgeops;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for KnowledgeOps platform integration.
 * Disabled by default — requires explicit opt-in and a reachable KnowledgeOps Agent backend.
 *
 * When enabled and reachable, agents use platform RAG/memory/graph for enrichment.
 * When disabled or unreachable, agents fall back to local VectorStore + Advisor.
 */
@Data
@Component
@ConfigurationProperties(prefix = "tj.ai.knowledgeops")
public class KnowledgeOpsProperties {
    private String baseUrl = "http://localhost:8080";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private String apiKey = "";
    /**
     * Whether KnowledgeOps platform integration is enabled.
     * Defaults to false — set to true and configure baseUrl to enable cross-repo collaboration.
     */
    private boolean enabled = false;
}
