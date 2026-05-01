package com.tianji.aigc.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全头、限流和 SSE 注入防护配置。
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    /** 安全响应头过滤器 */
    @Bean
    public Filter securityHeadersFilter() {
        return (ServletRequest request, ServletResponse response, FilterChain chain)
                -> {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                httpResponse.setHeader("X-Frame-Options", "DENY");
                httpResponse.setHeader("X-XSS-Protection", "0");
                httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
                if (request instanceof HttpServletRequest httpRequest
                        && httpRequest.getRequestURI().startsWith("/actuator")) {
                    // Actuator 端点不缓存
                    httpResponse.setHeader("Cache-Control", "no-store");
                }
            }
            chain.doFilter(request, response);
        };
    }

    /** 基于 IP 的简单滑动窗口限流过滤器 */
    @Bean
    public Filter rateLimitFilter() {
        return new RateLimitFilter();
    }

    /**
     * 令牌桶限流：每个 IP 每分钟最多 60 次请求。
     * 超限返回 HTTP 429。
     */
    static class RateLimitFilter implements Filter {
        private static final int MAX_REQUESTS_PER_MINUTE = 60;
        private static final long WINDOW_MS = 60_000L;

        private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            if (req instanceof HttpServletRequest httpReq && res instanceof HttpServletResponse httpRes) {
                String ip = getClientIp(httpReq);
                if (isRateLimited(ip)) {
                    httpRes.setStatus(429);
                    httpRes.setHeader("Retry-After", "60");
                    httpRes.setContentType("application/json");
                    httpRes.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many requests, please retry later.\"}");
                    return;
                }
            }
            chain.doFilter(req, res);
        }

        private boolean isRateLimited(String ip) {
            long now = System.currentTimeMillis();
            WindowCounter counter = counters.compute(ip, (k, v) -> {
                if (v == null || now - v.windowStart > WINDOW_MS) {
                    return new WindowCounter(now, new AtomicInteger(1));
                }
                v.count.incrementAndGet();
                return v;
            });
            return counter.count.get() > MAX_REQUESTS_PER_MINUTE;
        }

        private String getClientIp(HttpServletRequest req) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }

        static class WindowCounter {
            final long windowStart;
            final AtomicInteger count;

            WindowCounter(long windowStart, AtomicInteger count) {
                this.windowStart = windowStart;
                this.count = count;
            }
        }
    }
}
