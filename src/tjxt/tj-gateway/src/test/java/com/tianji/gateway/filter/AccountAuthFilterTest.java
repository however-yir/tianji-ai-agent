package com.tianji.gateway.filter;

import com.tianji.auth.common.constants.JwtConstants;
import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.gateway.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountAuthFilterTest {

    @Test
    void relaysVerifiedIdentityAndDropsClientSuppliedRelayHeaders() {
        AuthUtil authUtil = mock(AuthUtil.class);
        AuthProperties authProperties = mock(AuthProperties.class);
        when(authProperties.getExcludePath()).thenReturn(Set.of());

        LoginUserDTO user = new LoginUserDTO();
        user.setUserId(42L);
        user.setRoleId(7L);
        when(authUtil.parseToken("signed-token")).thenReturn(R.ok(user));
        org.mockito.Mockito.doNothing().when(authUtil).checkAuth(eq("GET:/ais/session/history"), any());

        AccountAuthFilter filter = new AccountAuthFilter(authUtil, authProperties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/ais/session/history")
                .header(JwtConstants.AUTHORIZATION_HEADER, "signed-token")
                .header(JwtConstants.USER_HEADER, "999")
                .header(JwtConstants.TOKEN_HEADER, "forged-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(JwtConstants.USER_HEADER)).isEqualTo("42");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(JwtConstants.TOKEN_HEADER))
                .isEqualTo("signed-token");
    }
}
