package com.tianji.aigc.knowledgeops;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tj.ai.knowledgeops")
public class KnowledgeOpsProperties {
    private String baseUrl = "http://localhost:8080";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private String apiKey = "";
    private boolean enabled = true;
}
