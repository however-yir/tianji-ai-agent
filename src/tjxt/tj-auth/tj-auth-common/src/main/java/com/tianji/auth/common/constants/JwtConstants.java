package com.tianji.auth.common.constants;

import java.time.Duration;

public class JwtConstants {
    public static final String PAYLOAD_USER_KEY = "user";
    public static final String PAYLOAD_JTI_KEY = "jti";

    public static final String JWT_REDIS_KEY_PREFIX = "jwt:uid:";
    // token过期时间：access-token 短 TTL（30分钟）。
    // 过期后由客户端携带 refresh-token（HttpOnly Cookie，见 JwtTool.createRefreshToken / AccountController 刷新接口）换发新 token；
    // 不能恢复此前测试期使用的 30 天长 TTL——access-token 无吊销机制，长期有效会放大泄露风险。
    public static final Duration JWT_TOKEN_TTL = Duration.ofMinutes(30);
    public static final Duration JWT_REFRESH_TTL = Duration.ofMinutes(30);

    public static final Duration JWT_REMEMBER_ME_TTL = Duration.ofDays(7);

    public static final String JWT_ALGORITHM = "rs256";
    public static final String AUTHORIZATION_HEADER = "authorization";
    public static final String REFRESH_HEADER = "refresh";
    public static final String ADMIN_REFRESH_HEADER = "admin-refresh";

    public static final String USER_HEADER = "user-info";
    public static final String TOKEN_HEADER = "token-info";

    /* 权限缓存 KEY  begin */
    public static final String AUTH_PRIVILEGE_KEY = "auth:privileges";
    public static final String AUTH_PRIVILEGE_VERSION_KEY = "version";
    public static final String LOCK_AUTH_PRIVILEGE_KEY = "lock:auth:privileges";
    /* 权限缓存 KEY  end */

}
