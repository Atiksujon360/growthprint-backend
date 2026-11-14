package com.growthprint.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitConfig inquiryConfig = new RateLimitConfig();
        inquiryConfig.setMaxRequests(2);
        inquiryConfig.setWindow(Duration.ofMinutes(1));

        LoginRateLimitConfig loginConfig = new LoginRateLimitConfig();
        loginConfig.setMaxRequests(2);
        loginConfig.setWindow(Duration.ofMinutes(1));

        AccountRecoveryRateLimitConfig recoveryConfig = new AccountRecoveryRateLimitConfig();
        recoveryConfig.setMaxRequests(3);
        recoveryConfig.setWindow(Duration.ofMinutes(15));

        filter = new RateLimitFilter(new InMemoryRateLimiter(), inquiryConfig, loginConfig, recoveryConfig);
    }

    @Test
    void accountRecoveryEndpointsShareOnePerIpBucket() throws Exception {
        // forgot + verify + reset all count against the same bucket, so they cannot be combined for more tries
        assertThat(send("POST", "/api/v1/auth/password/forgot", "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/password/reset/verify", "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/invitations/accept", "3.3.3.3").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = send("POST", "/api/v1/auth/password/reset", "3.3.3.3");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("900");

        // Separate from the login bucket and from other clients
        assertThat(send("POST", RateLimitFilter.LOGIN_PATH, "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(send("POST", "/api/v1/auth/password/forgot", "4.4.4.4").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse send(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        assertThat(send("POST", RateLimitFilter.LOGIN_PATH, "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(send("POST", RateLimitFilter.LOGIN_PATH, "1.1.1.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = send("POST", RateLimitFilter.LOGIN_PATH, "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Rate limit exceeded");

        // A different client is unaffected
        assertThat(send("POST", RateLimitFilter.LOGIN_PATH, "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void inquiryAndLoginUseSeparateBuckets() throws Exception {
        send("POST", RateLimitFilter.PUBLIC_INQUIRIES_PATH, "1.1.1.1");
        send("POST", RateLimitFilter.PUBLIC_INQUIRIES_PATH, "1.1.1.1");
        assertThat(send("POST", RateLimitFilter.PUBLIC_INQUIRIES_PATH, "1.1.1.1").getStatus()).isEqualTo(429);

        assertThat(send("POST", RateLimitFilter.LOGIN_PATH, "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAndMethodsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(send("GET", RateLimitFilter.LOGIN_PATH, "1.1.1.1").getStatus()).isEqualTo(200);
            assertThat(send("POST", "/api/v1/clients", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }
}
