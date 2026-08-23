package com.tianji.aigc.attachment;

import cn.hutool.core.util.StrUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AttachmentContextHolder {

    /**
     * 附件上下文只为一次 /chat 请求服务，正常会被 take/clear 移除；
     * 会话被放弃时靠该 TTL 兜底回收，避免静态 Map 无界增长。
     */
    private static final long EXPIRE_MILLIS = 60L * 60L * 1000L;

    private static final Map<String, TimestampedContext> CONTEXT_MAP = new ConcurrentHashMap<>();

    private AttachmentContextHolder() {
    }

    public static void put(String sessionId, AttachmentContext context) {
        if (StrUtil.isBlank(sessionId) || context == null || !context.hasSources()) {
            return;
        }
        purgeExpired();
        CONTEXT_MAP.put(sessionId, new TimestampedContext(context, System.currentTimeMillis()));
    }

    public static AttachmentContext peek(String sessionId) {
        TimestampedContext entry = getUnexpired(sessionId);
        return entry == null ? null : entry.context();
    }

    public static AttachmentContext take(String sessionId) {
        TimestampedContext entry = getUnexpired(sessionId);
        if (entry == null) {
            return null;
        }
        CONTEXT_MAP.remove(sessionId);
        return entry.context();
    }

    public static void clear(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        CONTEXT_MAP.remove(sessionId);
    }

    private static TimestampedContext getUnexpired(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return null;
        }
        TimestampedContext entry = CONTEXT_MAP.get(sessionId);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            CONTEXT_MAP.remove(sessionId);
            return null;
        }
        return entry;
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        CONTEXT_MAP.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > EXPIRE_MILLIS);
    }

    private record TimestampedContext(AttachmentContext context, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRE_MILLIS;
        }
    }
}
