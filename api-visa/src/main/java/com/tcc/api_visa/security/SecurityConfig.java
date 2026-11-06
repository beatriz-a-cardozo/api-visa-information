package com.tcc.api_visa.security;

import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API: no sessions, cookies or login forms. Public data is read-only ({@code GET});
 * {@code /admin/**} requires the administrator key; everything else is refused.
 *
 * <p>Besides Spring Security's default headers ({@code nosniff}, {@code X-Frame-Options: DENY},
 * {@code Cache-Control: no-store} and HSTS over https), every response carries a Content Security
 * Policy that forbids loading anything, so a JSON response can never run as a page. Swagger UI gets
 * a policy that allows only its own scripts and styles.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    static final String SWAGGER_CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline';"
            + " frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, AdminProperties adminProperties, CorsProperties corsProperties) throws Exception {
        RequestMatcher swaggerUi = new OrRequestMatcher(
                PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
                PathPatternRequestMatcher.pathPattern("/swagger-ui.html"));
        return http.csrf(AbstractHttpConfigurer::disable) // no cookies or sessions, so nothing to forge
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                swaggerUi, new ContentSecurityPolicyHeaderWriter(SWAGGER_CSP)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(swaggerUi), new ContentSecurityPolicyHeaderWriter(API_CSP))))
                .addFilterBefore(new AdminApiKeyFilter(adminProperties), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasRole(AdminApiKeyFilter.ROLE)
                        .requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    /**
     * Closed by default. Origins listed in {@code CORS_ALLOWED_ORIGINS} may read the public API
     * ({@code GET /api/**}) without credentials; {@code /admin} is never open to browsers.
     */
    static CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!properties.allowedOrigins().isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(properties.allowedOrigins());
            config.setAllowedMethods(List.of(HttpMethod.GET.name()));
            config.setAllowCredentials(false);
            config.setExposedHeaders(List.of("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
            config.setMaxAge(Duration.ofHours(1));
            source.registerCorsConfiguration("/api/**", config);
        }
        return source;
    }

    /** No users or passwords: replaces the default user Spring Boot would generate. */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
