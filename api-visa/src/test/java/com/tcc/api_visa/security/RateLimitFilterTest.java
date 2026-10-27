package com.tcc.api_visa.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(2, Duration.ofMinutes(1)));

    @Test
    void letsRequestsThroughUntilTheLimit() throws Exception {
        assertThat(send("203.0.113.1").getStatus()).isEqualTo(200);
        MockHttpServletResponse second = send("203.0.113.1");

        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(second.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void answersTooManyRequestsWithRetryAfterOverTheLimit() throws Exception {
        send("203.0.113.2");
        send("203.0.113.2");

        MockHttpServletResponse refused = send("203.0.113.2");

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getContentType()).startsWith("application/problem+json");
        assertThat(refused.getContentAsString()).contains("\"status\":429");
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, 60L);
    }

    @Test
    void keepsASeparateLimitPerIp() throws Exception {
        send("203.0.113.3");
        send("203.0.113.3");

        assertThat(send("203.0.113.3").getStatus()).isEqualTo(429);
        assertThat(send("198.51.100.7").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse send(String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/countries");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
