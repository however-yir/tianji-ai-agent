package com.tianji.aigc.sse;

import com.tianji.aigc.agent.AbstractAgent;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.vo.ChatEventVO;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The server-side contract for one chat SSE response. It keeps invalid payloads out of the
 * stream and appends exactly one terminal STOP event when a child agent omits one.
 */
public final class SseEventContract {

    private static final Set<Integer> EVENT_TYPES = Arrays.stream(ChatEventTypeEnum.values())
            .map(ChatEventTypeEnum::getValue)
            .collect(Collectors.toUnmodifiableSet());

    private SseEventContract() {
    }

    public static Flux<ChatEventVO> terminate(ChatEventVO routeEvent, Flux<ChatEventVO> childStream,
                                                String sessionId) {
        AtomicBoolean stopEmitted = new AtomicBoolean(false);
        Flux<ChatEventVO> validChildStream = childStream
                .filter(SseEventContract::isValid)
                .takeUntil(SseEventContract::isStop)
                .doOnNext(event -> {
                    if (isStop(event)) {
                        stopEmitted.set(true);
                    }
                })
                .onErrorResume(error -> Flux.just(streamFailure(sessionId)));

        return Flux.concat(Flux.just(routeEvent), validChildStream)
                .concatWith(Flux.defer(() -> stopEmitted.get()
                        ? Flux.empty()
                        : Flux.just(AbstractAgent.STOP_EVENT)));
    }

    public static boolean isValid(ChatEventVO event) {
        if (event == null || !EVENT_TYPES.contains(event.getEventType())) {
            return false;
        }
        Object payload = event.getEventData();
        return switch (event.getEventType()) {
            case 1001 -> payload instanceof String text && !text.isEmpty();
            case 1002 -> true;
            case 1003 -> payload instanceof Map<?, ?>;
            case 1004 -> payload != null;
            case 1005 -> payload instanceof Map<?, ?> || payload instanceof List<?>;
            case 1006, 1007 -> payload instanceof Map<?, ?> || payload instanceof List<?>;
            default -> false;
        };
    }

    private static boolean isStop(ChatEventVO event) {
        return event.getEventType() == ChatEventTypeEnum.STOP.getValue();
    }

    private static ChatEventVO streamFailure(String sessionId) {
        return ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.TRACE.getValue())
                .eventData(Map.of(
                        "sessionId", sessionId == null ? "UNKNOWN_SESSION" : sessionId,
                        "actionType", "sse.stream",
                        "status", "FAILURE",
                        "policyDecision", "NOT_APPLICABLE",
                        "errorReason", "agent_stream_failed"
                ))
                .build();
    }
}
