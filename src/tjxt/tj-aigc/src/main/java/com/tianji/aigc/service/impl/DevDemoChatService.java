package com.tianji.aigc.service.impl;

import com.tianji.aigc.attachment.AttachmentContext;
import com.tianji.aigc.attachment.AttachmentContextHolder;
import com.tianji.aigc.attachment.AttachmentSource;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.DevDemoProperties;
import com.tianji.aigc.demo.DevDemoSessionStore;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@Primary
@Profile("dev-demo")
@RequiredArgsConstructor
public class DevDemoChatService implements ChatService {

    private final DevDemoSessionStore devDemoSessionStore;
    private final DevDemoProperties devDemoProperties;
    private final Map<String, AtomicBoolean> stopSignals = new ConcurrentHashMap<>();

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        String resolvedSessionId = devDemoSessionStore.ensureSession(sessionId);
        AttachmentContext attachmentContext = AttachmentContextHolder.take(resolvedSessionId);
        devDemoSessionStore.addUserMessage(resolvedSessionId, question);

        String answer = buildReply(question, attachmentContext);
        Map<String, Object> params = buildParams(question, resolvedSessionId, attachmentContext);
        List<String> chunks = split(answer, 28);
        AtomicBoolean stopSignal = stopSignals.computeIfAbsent(resolvedSessionId, key -> new AtomicBoolean(false));
        stopSignal.set(false);

        return Flux.create(sink -> {
            sink.onDispose(() -> stopSignal.set(true));
            CompletableFuture.runAsync(() -> {
                StringBuilder delivered = new StringBuilder();
                try {
                    for (String chunk : chunks) {
                        if (stopSignal.get() || sink.isCancelled()) {
                            break;
                        }
                        delivered.append(chunk);
                        sink.next(ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.DATA.getValue())
                                .eventData(chunk)
                                .build());
                        Thread.sleep(devDemoProperties.getStreamDelayMs());
                    }

                    if (stopSignal.get()) {
                        params.put("stopped", true);
                        params.put("stoppedAt", LocalDateTime.now().toString());
                    }

                    String finalAnswer = delivered.length() > 0 ? delivered.toString() : "已停止生成。";
                    devDemoSessionStore.addAssistantMessage(resolvedSessionId, finalAnswer, params);

                    if (!sink.isCancelled()) {
                        sink.next(ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .eventData(params)
                                .build());
                        sink.next(ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.STOP.getValue())
                                .eventData("stop")
                                .build());
                    }
                    sink.complete();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sink.error(e);
                } catch (Exception e) {
                    log.error("dev-demo 流式响应失败", e);
                    sink.error(e);
                } finally {
                    stopSignals.remove(resolvedSessionId);
                }
            });
        });
    }

    @Override
    public void stop(String sessionId) {
        stopSignals.computeIfAbsent(sessionId, key -> new AtomicBoolean(false)).set(true);
    }

    @Override
    public String chatText(String question) {
        return buildReply(question, null);
    }

    private Map<String, Object> buildParams(String question, String sessionId, AttachmentContext attachmentContext) {
        Map<String, Object> payload = new LinkedHashMap<>(devDemoSessionStore.defaultParamPayload(sessionId));
        payload.put("timestamp", LocalDateTime.now().toString());
        if (attachmentContext != null && attachmentContext.hasSources()) {
            payload.putAll(attachmentContext.toParamMap());
            payload.put("capability", "attachment-qa");
        } else if (question.contains("附件上下文")) {
            payload.put("sources", extractAttachmentNames(question));
            payload.put("capability", "attachment-demo");
        } else if (question.contains("流程") || question.toLowerCase().contains("mermaid")) {
            payload.put("capability", "mermaid-demo");
        } else if (question.contains("公式") || question.toLowerCase().contains("math")) {
            payload.put("capability", "math-demo");
        } else {
            payload.put("capability", "general-demo");
        }
        return payload;
    }

    private String buildReply(String question, AttachmentContext attachmentContext) {
        String normalized = question == null ? "" : question.toLowerCase();
        if (attachmentContext != null && attachmentContext.hasSources()) {
            String sourceSummary = attachmentContext.getSources().stream()
                    .limit(3)
                    .map(this::formatSourceLine)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("- 暂无可引用片段");
            return """
                    我已经基于你上传的真实附件内容整理出一版回答。

                    当前问题：%s

                    最相关的附件片段如下：
                    %s

                    你可以继续追问更细的问题，例如“请提炼风险”“输出待办”或“按汇报口径重写”。
                    """.formatted(question, sourceSummary);
        }

        if (normalized.contains("附件上下文") || normalized.contains("pdf") || normalized.contains("图片")) {
            List<String> attachments = extractAttachmentNames(question);
            String attachmentText = attachments.isEmpty() ? "未识别出附件名称" : String.join("、", attachments);
            return """
                    我已经根据你传入的附件上下文生成了一版演示回答。

                    当前识别到的附件有：%s

                    建议的真实落地方向：
                    1. 先做文档解析和 OCR
                    2. 再做切片、向量检索和引用返回
                    3. 最后把引用片段展示到前端消息卡片里

                    这是 dev-demo 模式下的模拟结果，已经足够用来演示“基于附件提问”的完整交互。
                    """.formatted(attachmentText);
        }

        if (normalized.contains("流程") || normalized.contains("mermaid")) {
            return """
                    下面给你一个更适合汇报的流程示意：

                    ```mermaid
                    flowchart TD
                    U["用户问题"] --> R["意图识别"]
                    R --> P["方案规划"]
                    P --> T["工具调用 / 参数输出"]
                    T --> A["流式回答"]
                    ```

                    这种结构非常适合在面试时解释你的项目链路，因为它同时覆盖了路由、工具、流式响应和前后端协作。
                    """;
        }

        if (normalized.contains("公式") || normalized.contains("math")) {
            return """
                    可以，这里给你一个项目演示里很好讲的评分公式：

                    $$Score = 0.45 \\times Intent + 0.35 \\times Context + 0.20 \\times Action$$

                    它表达的是：意图识别越准、上下文越完整、行动建议越明确，整段对话的业务价值就越高。
                    """;
        }

        if (normalized.contains("docker") || normalized.contains("运行") || normalized.contains("启动")) {
            return """
                    这轮我优先把“一键启动”做成了可落地的路线：

                    - `docker compose up --build` 拉起前端、后端、MySQL、Redis
                    - 后端使用 `dev-demo` profile，不依赖真实模型 Key
                    - 前端默认仍保留 demo 模式，后端未启动也能先看 UI

                    这样你在面试、演示和团队协作时都会更轻松，因为环境准备成本会显著下降。
                    """;
        }

        return """
                当前是 `dev-demo` 模式下的模拟回答。

                我会保留和真实接口一致的交互形态，包括：
                - `/session` 新建会话
                - `/session/history` 历史列表
                - `/chat` SSE 流式输出
                - `/chat/stop` 停止生成

                这让你可以先把前后端流程、产品交互和演示链路跑通，再决定什么时候切回真实模型。
                """;
    }

    private List<String> split(String text, int chunkSize) {
        if (StrUtil.isBlank(text)) {
            return List.of("已完成。");
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(text.length(), i + chunkSize)));
        }
        return chunks;
    }

    private List<String> extractAttachmentNames(String question) {
        if (StrUtil.isBlank(question) || !question.contains("附件上下文")) {
            return List.of();
        }
        String[] lines = question.split("\\R");
        List<String> names = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("- ")) {
                continue;
            }
            int endIndex = trimmed.indexOf(" (");
            names.add(endIndex > 2 ? trimmed.substring(2, endIndex) : trimmed.substring(2));
        }
        return names;
    }

    private String formatSourceLine(AttachmentSource source) {
        return "- 《%s》片段 %s：%s".formatted(
                source.getAttachmentName(),
                source.getChunkIndex(),
                source.getExcerpt()
        );
    }
}
