package com.tianji.aigc.harness;

import com.tianji.aigc.config.ToolResultHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HarnessEventRecorder {

    public static final String TRACE_FIELD = "agentHarnessTrace";

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
        Object current = ToolResultHolder.get(observation.requestId(), TRACE_FIELD);
        List<Object> traces = new ArrayList<>();
        if (current instanceof List<?> list) {
            traces.addAll(list);
        }
        else if (current != null) {
            traces.add(current);
        }
        Map<String, Object> trace = observation.toTraceMap();
        traces.add(trace);
        ToolResultHolder.put(observation.requestId(), TRACE_FIELD, traces);
    }
}
