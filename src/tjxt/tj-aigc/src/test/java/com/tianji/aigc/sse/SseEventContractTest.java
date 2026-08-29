package com.tianji.aigc.sse;

import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.vo.ChatEventVO;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventContractTest {

    @Test
    void shouldAppendStopWhenChildStreamCompletesWithoutOne() {
        List<ChatEventVO> events = SseEventContract.terminate(routeEvent(), Flux.just(dataEvent("课程推荐")), "s-1")
                .collectList()
                .block();

        assertThat(events).extracting(ChatEventVO::getEventType)
                .containsExactly(1004, 1001, 1002);
    }

    @Test
    void shouldRejectInvalidPayloadAndStillTerminate() {
        ChatEventVO invalidParam = ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.PARAM.getValue())
                .eventData("not-a-business-card")
                .build();

        List<ChatEventVO> events = SseEventContract.terminate(routeEvent(), Flux.just(invalidParam), "s-1")
                .collectList()
                .block();

        assertThat(SseEventContract.isValid(invalidParam)).isFalse();
        assertThat(events).extracting(ChatEventVO::getEventType).containsExactly(1004, 1002);
    }

    @Test
    void shouldConvertChildFailureToTraceAndStop() {
        List<ChatEventVO> events = SseEventContract.terminate(
                        routeEvent(), Flux.error(new RuntimeException("course service timeout")), "s-timeout")
                .collectList()
                .block();

        assertThat(events).extracting(ChatEventVO::getEventType).containsExactly(1004, 1005, 1002);
        assertThat(events.get(1).getEventData()).isEqualTo(Map.of(
                "sessionId", "s-timeout",
                "actionType", "sse.stream",
                "status", "FAILURE",
                "policyDecision", "NOT_APPLICABLE",
                "errorReason", "agent_stream_failed"
        ));
    }

    @Test
    void shouldNotEmitTwoStopsWhenChildAlreadyTerminates() {
        List<ChatEventVO> events = SseEventContract.terminate(
                        routeEvent(), Flux.just(dataEvent("done"), stopEvent()), "s-1")
                .collectList()
                .block();

        assertThat(events).extracting(ChatEventVO::getEventType).containsExactly(1004, 1001, 1002);
    }

    private ChatEventVO routeEvent() {
        return ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.ROUTE.getValue())
                .eventData(Map.of("nextAgent", "RECOMMEND"))
                .build();
    }

    private ChatEventVO dataEvent(String text) {
        return ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData(text)
                .build();
    }

    private ChatEventVO stopEvent() {
        return ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();
    }
}
