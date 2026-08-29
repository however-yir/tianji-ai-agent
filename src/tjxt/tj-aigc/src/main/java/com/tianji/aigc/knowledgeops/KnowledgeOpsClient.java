package com.tianji.aigc.knowledgeops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeOpsClient {

    private final KnowledgeOpsProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 装配 KnowledgeOpsProperties 中的连接/读取超时，
     * 避免默认无限等待的 RestTemplate 占死 Tomcat/流式线程。
     */
    @PostConstruct
    void configureTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        restTemplate.setRequestFactory(factory);
    }

    public Map<String, Object> ragSearch(String query, String tenantId, String chatId) {
        return post("/ai/rag/search", Map.of("prompt", query, "tenantId", tenantId, "chatId", chatId));
    }

    public Map<String, Object> memoryQuery(String userId, String tenantId, String type) {
        return get("/ai/memory/query?userId=" + encode(userId) + "&tenantId=" + encode(tenantId) + "&type=" + encode(type));
    }

    public Map<String, Object> memorySave(String userId, String tenantId, String type, String content, String source) {
        return post("/ai/memory/save", Map.of("userId", userId, "tenantId", tenantId, "type", type, "content", content, "source", source));
    }

    public Map<String, Object> graphSearch(String keyword, String tenantId) {
        // keyword 可能来自用户原始问题，必须编码后再拼 GET URL，避免特殊字符破坏查询串
        return get("/ai/graph/search?keyword=" + encode(keyword) + "&tenantId=" + encode(tenantId));
    }

    public Map<String, Object> createResearchTask(String topic, String tenantId) {
        return post("/ai/research/tasks", Map.of("topic", topic, "tenantId", tenantId));
    }

    public Map<String, Object> getResearchTask(String taskId) {
        return get("/ai/research/tasks/" + encode(taskId));
    }

    public Map<String, Object> getResearchReport(String taskId) {
        return get("/ai/research/tasks/" + encode(taskId) + "/report");
    }

    public Map<String, Object> getWorkflowTask(String taskId) {
        return get("/ai/workflow/tasks/" + encode(taskId));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTaskEvents(String taskId) {
        Map<String, Object> resp = get("/ai/workflow/tasks/" + encode(taskId) + "/events");
        if (resp != null && resp.containsKey("data")) {
            try {
                return (List<Map<String, Object>>) resp.get("data");
            }
            catch (Exception e) {
                log.warn("Failed to parse events", e);
            }
        }
        return Collections.emptyList();
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        if (!properties.isEnabled()) return Collections.emptyMap();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!properties.getApiKey().isEmpty()) headers.set("X-Api-Key", properties.getApiKey());
            ResponseEntity<String> resp = restTemplate.exchange(
                    properties.getBaseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            if (resp.getBody() != null) return objectMapper.readValue(resp.getBody(), new TypeReference<>() {});
        }
        catch (Exception e) {
            log.error("KnowledgeOps POST {} failed: {}", path, e.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> get(String path) {
        if (!properties.isEnabled()) return Collections.emptyMap();
        try {
            HttpHeaders headers = new HttpHeaders();
            if (!properties.getApiKey().isEmpty()) headers.set("X-Api-Key", properties.getApiKey());
            ResponseEntity<String> resp = restTemplate.exchange(
                    properties.getBaseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (resp.getBody() != null) return objectMapper.readValue(resp.getBody(), new TypeReference<>() {});
        }
        catch (Exception e) {
            log.error("KnowledgeOps GET {} failed: {}", path, e.getMessage());
        }
        return Collections.emptyMap();
    }
}
