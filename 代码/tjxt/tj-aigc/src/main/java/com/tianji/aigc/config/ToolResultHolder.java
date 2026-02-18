package com.tianji.aigc.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具结果保持器，用来存储tools中得到的结果，请求id 作为key， value为键值对数据
 *
 * @author zzj
 * @version 1.0
 */
public class ToolResultHolder {
    private static final long EXPIRE_MILLIS = 10 * 60 * 1000L;

    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> EXPIRE_AT = new ConcurrentHashMap<>();


    private ToolResultHolder() {
    }

    public static void put(String key, String field, Object result) {
        if (null == key || null == field) {
            return;
        }
        cleanupExpiredEntries();
        HANDLER_MAP.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(field, result);
        EXPIRE_AT.put(key, System.currentTimeMillis() + EXPIRE_MILLIS);
    }

    public static Map<String, Object> get(String key) {
        if (key == null) {
            return null;
        }
        if (isExpired(key)) {
            remove(key);
            return null;
        }
        return HANDLER_MAP.get(key);
    }

    public static Object get(String key, String field) {
        if (null == key || null == field) {
            return null;
        }
        if (isExpired(key)) {
            remove(key);
            return null;
        }
        return Optional.ofNullable(HANDLER_MAP.get(key))
                .map(map -> map.get(field))
                .orElse(null);
    }

    public static void remove(String key) {
        if (null == key) {
            return;
        }
        HANDLER_MAP.remove(key);
        EXPIRE_AT.remove(key);
    }

    private static boolean isExpired(String key) {
        Long expireAt = EXPIRE_AT.get(key);
        return expireAt != null && expireAt < System.currentTimeMillis();
    }

    private static void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        EXPIRE_AT.forEach((key, expireAt) -> {
            if (expireAt < now) {
                HANDLER_MAP.remove(key);
                EXPIRE_AT.remove(key);
            }
        });
    }

}
