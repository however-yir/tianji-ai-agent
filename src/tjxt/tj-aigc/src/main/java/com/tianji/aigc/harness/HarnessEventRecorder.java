package com.tianji.aigc.harness;

import com.tianji.aigc.config.ToolResultHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class HarnessEventRecorder {

    public static final String TRACE_FIELD = "agentHarnessTrace";

    /**
     * Per-request locks so concurrent tool calls in the same model response
     * (Spring AI may invoke tools on parallel threads) cannot drop traces
     * by losing the read-modify-write race against {@link ToolResultHolder}.
     */
    private final ConcurrentHashMap<String, ReentrantLock> requestLocks = new ConcurrentHashMap<>();

    public void record(AgentObservation observation) {
        if (observation == null || !StringUtils.hasText(observation.requestId())) {
            return;
        }
        if (observation.success() && observation.result() != null && StringUtils.hasText(observation.resultField())) {
            ToolResultHolder.put(observation.requestId(), observation.resultField(), observation.result());
        }
        appendTrace(observation);
    }

    private void appendTrace(AgentObservation observation) {
        ReentrantLock lock = requestLocks.computeIfAbsent(observation.requestId(), k -> new ReentrantLock());
        lock.lock();
        try {
            Object current = ToolResultHolder.get(observation.requestId(), TRACE_FIELD);
            List<Object> traces = new ArrayList<>();
            if (current instanceof List<?> list) {
                traces.addAll(list);
            }
            else if (current != null) {
                traces.add(current);
            }
            traces.add(observation.toTraceMap());
            ToolResultHolder.put(observation.requestId(), TRACE_FIELD, traces);
        }
        finally {
            lock.unlock();
        }
    }
}
