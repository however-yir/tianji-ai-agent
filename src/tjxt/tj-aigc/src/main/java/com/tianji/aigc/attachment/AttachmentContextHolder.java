package com.tianji.aigc.attachment;

import cn.hutool.core.util.StrUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentContextHolder {

    private static final Map<String, AttachmentContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    private AttachmentContextHolder() {
    }

    public static void put(String sessionId, AttachmentContext context) {
        if (StrUtil.isBlank(sessionId) || context == null || !context.hasSources()) {
            return;
        }
        CONTEXT_MAP.put(sessionId, context);
    }

    public static AttachmentContext peek(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        return CONTEXT_MAP.get(sessionId);
    }

    public static AttachmentContext take(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        return CONTEXT_MAP.remove(sessionId);
    }

    public static void clear(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        CONTEXT_MAP.remove(sessionId);
    }
}
