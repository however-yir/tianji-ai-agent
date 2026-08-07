package com.tianji.aigc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebSecurityConfigTest {

    @Test
    void rateLimitUsesTransportPeerInsteadOfSpoofableForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.4");
        request.addHeader("X-Forwarded-For", "203.0.113.99");

        assertThat(new WebSecurityConfig.RateLimitFilter().getClientIp(request)).isEqualTo("10.0.0.4");
    }
}
