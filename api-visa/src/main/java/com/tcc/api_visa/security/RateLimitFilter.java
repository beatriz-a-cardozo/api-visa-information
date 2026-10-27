package com.tcc.api_visa.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token bucket per client IP. Over the limit, the client gets {@code 429} with {@code Retry-After}.
 *
 * <p>The IP is {@code request.getRemoteAddr()}, which already reflects {@code X-Forwarded-For} because
 * the app trusts the hosting proxy ({@code server.forward-headers-strategy}). Buckets of idle IPs are
 * dropped, and the number of IPs tracked is bounded, so a flood of addresses cannot exhaust memory.
 */
class RateLimitFilter extends OncePerRequestFilter {

    static final int MAX_TRACKED_CLIENTS = 100_000;

    private static final String BODY = """
            {"type":"about:blank","title":"Too Many Requests","status":429,\
            "detail":"Request limit reached for this IP; try again after the Retry-After seconds"}""";

    private final RateLimitProperties properties;
    private final Cache<String, Bucket> buckets;

    RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(properties.period().multipliedBy(2))
                .maximumSize(MAX_TRACKED_CLIENTS)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(properties.capacity()).refillGreedy(properties.capacity(), properties.period()))
                .build();
    }
}
