package com.tcc.api_visa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the administrator by the {@code X-Admin-Key} header. Keys are compared by their
 * SHA-256 digest in constant time, so the response time does not reveal how much of a guess was right.
 */
class AdminApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Admin-Key";
    static final String ROLE = "ADMIN";

    private final byte @Nullable [] expectedDigest;

    AdminApiKeyFilter(AdminProperties properties) {
        this.expectedDigest = properties.apiKey() == null ? null : sha256(properties.apiKey());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expectedDigest != null && provided != null && MessageDigest.isEqual(sha256(provided), expectedDigest)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
