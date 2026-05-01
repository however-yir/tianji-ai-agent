package com.tianji.aigc.demo;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Profile("dev-demo")
@RequiredArgsConstructor
public class DevDemoSessionStore {

    private static final String DEFAULT_SESSION_TITLE = "新的对话";

    private final SessionProperties sessionProperties;
    private final Map<String, DemoSessionRecord> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (!sessions.isEmpty()) {
            return;
        }

        DemoSessionRecord first = createSessionRecord(
                "课程咨询工作流演示",
                LocalDateTime.now().minusMinutes(26)
        );
        first.getMessages().add(new DemoMessageRecord(
                MessageTypeEnum.USER,
                "帮我总结一下课程咨询助手应该覆盖哪些关键环节？",
                null,
                first.getUpdateTime()
        ));
        first.getMessages().add(new DemoMessageRecord(
                MessageTypeEnum.ASSISTANT,
                """
                        建议按 4 个环节组织：

                        1. 线索进入：识别来源、用户画像和咨询意图
                        2. 需求诊断：确认基础、预算、时间与目标
                        3. 方案推荐：匹配课程、服务、风险提示
                        4. 转化跟进：沉淀纪要、异议和待办

                        ```mermaid
                        flowchart LR
                        A["线索进入"] --> B["需求诊断"]
                        B --> C["方案推荐"]
                        C --> D["转化跟进"]
                        ```
                        """,
                Map.of(
                        "mode", "dev-demo",
                        "workflow", "consulting",
                        "leadScore", 88
                ),
                first.getUpdateTime().plusSeconds(1)
        ));
        sessions.put(first.getSessionId(), first);

        DemoSessionRecord second = createSessionRecord(
                "前端升级建议",
                LocalDateTime.now().minusMinutes(12)
        );
        second.getMessages().add(new DemoMessageRecord(
                MessageTypeEnum.USER,
                "把这个聊天项目完善到更像正式产品，需要先做什么？",
                null,
                second.getUpdateTime()
        ));
        second.getMessages().add(new DemoMessageRecord(
                MessageTypeEnum.ASSISTANT,
                """
                        你可以优先做这三件事：

                        - 一键启动：前端、后端、MySQL、Redis 直接拉起
                        - dev-demo：无模型 Key 也能完整跑通
                        - 附件问答：把“聊天壳子”升级成可展示业务价值的 AI 应用

                        如果要量化一个简化评分，可以写成：

                        $$Score = 0.45 \\times Intent + 0.35 \\times Context + 0.20 \\times Action$$
                        """,
                Map.of(
                        "mode", "dev-demo",
                        "focus", List.of("compose", "mock", "attachment")
                ),
                second.getUpdateTime().plusSeconds(1)
        ));
        sessions.put(second.getSessionId(), second);
    }

    public SessionVO createSession(Integer num) {
        DemoSessionRecord sessionRecord = createSessionRecord(DEFAULT_SESSION_TITLE, LocalDateTime.now());
        sessions.put(sessionRecord.getSessionId(), sessionRecord);
        return SessionVO.builder()
                .sessionId(sessionRecord.getSessionId())
                .title(sessionRecord.getTitle())
                .describe(resolveDescribe())
                .examples(pickExamples(num))
                .build();
    }

    public String ensureSession(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            SessionVO created = createSession(3);
            return created.getSessionId();
        }
        sessions.computeIfAbsent(sessionId, key -> createSessionRecord(DEFAULT_SESSION_TITLE, LocalDateTime.now()));
        return sessionId;
    }

    public void addUserMessage(String sessionId, String question) {
        DemoSessionRecord sessionRecord = sessions.get(ensureSession(sessionId));
        LocalDateTime now = LocalDateTime.now();
        sessionRecord.getMessages().add(new DemoMessageRecord(MessageTypeEnum.USER, question, null, now));
        if (DEFAULT_SESSION_TITLE.equals(sessionRecord.getTitle()) || StrUtil.isBlank(sessionRecord.getTitle())) {
            sessionRecord.setTitle(shortTitle(question));
        }
        sessionRecord.setUpdateTime(now);
    }

    public void addAssistantMessage(String sessionId, String content, Map<String, Object> params) {
        DemoSessionRecord sessionRecord = sessions.get(ensureSession(sessionId));
        LocalDateTime now = LocalDateTime.now();
        sessionRecord.getMessages().add(new DemoMessageRecord(MessageTypeEnum.ASSISTANT, content, params, now));
        sessionRecord.setUpdateTime(now);
    }

    public List<SessionVO.Example> hotExamples(Integer num) {
        return pickExamples(num);
    }

    public List<MessageVO> queryBySessionId(String sessionId) {
        DemoSessionRecord sessionRecord = sessions.get(sessionId);
        if (sessionRecord == null) {
            return List.of();
        }
        return sessionRecord.getMessages().stream()
                .map(message -> MessageVO.builder()
                        .type(message.getType())
                        .content(message.getContent())
                        .params(message.getParams())
                        .build())
                .toList();
    }

    public void update(String sessionId, String title) {
        DemoSessionRecord sessionRecord = sessions.get(sessionId);
        if (sessionRecord == null || StrUtil.isBlank(title)) {
            return;
        }
        sessionRecord.setTitle(StrUtil.sub(title.trim(), 0, 100));
        sessionRecord.setUpdateTime(LocalDateTime.now());
    }

    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        List<DemoSessionRecord> sortedSessions = sessions.values().stream()
                .sorted((left, right) -> right.getUpdateTime().compareTo(left.getUpdateTime()))
                .toList();

        if (sortedSessions.isEmpty()) {
            return Map.of();
        }

        Map<String, List<ChatSessionVO>> grouped = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (DemoSessionRecord session : sortedSessions) {
            String bucket = resolveBucket(today, session.getUpdateTime().toLocalDate());
            grouped.computeIfAbsent(bucket, key -> new ArrayList<>())
                    .add(ChatSessionVO.builder()
                            .sessionId(session.getSessionId())
                            .title(session.getTitle())
                            .updateTime(session.getUpdateTime())
                            .build());
        }
        return grouped;
    }

    public Map<String, Object> defaultParamPayload(String sessionId) {
        return new LinkedHashMap<>(Map.of(
                "mode", "dev-demo",
                "sessionId", sessionId,
                "profile", "dev-demo"
        ));
    }

    private List<SessionVO.Example> pickExamples(Integer num) {
        List<SessionVO.Example> examples = sessionProperties.getExamples();
        if (examples == null || examples.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(num == null ? 3 : num, examples.size()));
        return RandomUtil.randomEleList(examples, size);
    }

    private String resolveDescribe() {
        if (StrUtil.isNotBlank(sessionProperties.getDescribe())) {
            return sessionProperties.getDescribe();
        }
        return "dev-demo 模式下的天机 AI 助手，无需模型 Key 也能演示完整对话流程。";
    }

    private DemoSessionRecord createSessionRecord(String title, LocalDateTime updateTime) {
        return new DemoSessionRecord(
                IdUtil.simpleUUID(),
                title,
                updateTime,
                new CopyOnWriteArrayList<>()
        );
    }

    private String shortTitle(String question) {
        if (StrUtil.isBlank(question)) {
            return DEFAULT_SESSION_TITLE;
        }
        String normalized = question.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 20) {
            return normalized;
        }
        return normalized.substring(0, 20);
    }

    private String resolveBucket(LocalDate today, LocalDate updateDate) {
        long between = Math.abs(ChronoUnit.DAYS.between(updateDate, today));
        if (between == 0) {
            return "当天";
        }
        if (between <= 30) {
            return "最近30天";
        }
        if (between <= 365) {
            return "最近1年";
        }
        return "1年以上";
    }

    @Getter
    @AllArgsConstructor
    private static class DemoMessageRecord {
        private final MessageTypeEnum type;
        private final String content;
        private final Map<String, Object> params;
        private final LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class DemoSessionRecord {
        private final String sessionId;
        private String title;
        private LocalDateTime updateTime;
        private final CopyOnWriteArrayList<DemoMessageRecord> messages;
    }
}
