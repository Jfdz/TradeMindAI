package com.tradingsaas.tradingcore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.in.web.JwtAuthenticationFilter;
import com.tradingsaas.tradingcore.adapter.in.web.RateLimitFilter;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final LettuceBasedProxyManager<String> rateLimitProxyManager;
    private final String[] allowedCorsOrigins;
    private final long rateLimitFreePm;
    private final long rateLimitBasicPm;
    private final long rateLimitPremiumPm;

    SecurityConfig(JwtAuthenticationFilter jwtFilter,
                   ObjectMapper objectMapper,
                   LettuceBasedProxyManager<String> rateLimitProxyManager,
                   @org.springframework.beans.factory.annotation.Value("${trading-core.cors.allowed-origins}") String[] allowedCorsOrigins,
                   @org.springframework.beans.factory.annotation.Value("${trading-core.rate-limit.free-per-minute:5}") long rateLimitFreePm,
                   @org.springframework.beans.factory.annotation.Value("${trading-core.rate-limit.basic-per-minute:50}") long rateLimitBasicPm,
                   @org.springframework.beans.factory.annotation.Value("${trading-core.rate-limit.premium-per-minute:500}") long rateLimitPremiumPm) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
        this.rateLimitProxyManager = rateLimitProxyManager;
        this.allowedCorsOrigins = allowedCorsOrigins;
        this.rateLimitFreePm = rateLimitFreePm;
        this.rateLimitBasicPm = rateLimitBasicPm;
        this.rateLimitPremiumPm = rateLimitPremiumPm;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitProxyManager, rateLimitFreePm, rateLimitBasicPm, rateLimitPremiumPm);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeUnauthorized(response, request.getRequestURI()))
                .accessDeniedHandler((request, response, e) ->
                    writeForbidden(response, request.getRequestURI()))
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                .requestMatchers("/api/v1/symbols").permitAll()
                .requestMatchers("/api/v1/prices/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/subscriptions/plans").permitAll()
                .requestMatchers("/api/v1/backtests/symbols/*/available").permitAll()
                // Admin-only endpoints
                .requestMatchers("/api/v1/ingestion/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/models/**").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; object-src 'none'; " +
                    "img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; " +
                    "connect-src 'self' https: ws: wss: http://localhost:* http://127.0.0.1:*"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedCorsOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
            "status", 401,
            "error", "Unauthorized",
            "message", "Authentication required",
            "timestamp", Instant.now().toString(),
            "path", path
        )));
    }

    private void writeForbidden(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
            "status", 403,
            "error", "Forbidden",
            "message", "Insufficient permissions",
            "timestamp", Instant.now().toString(),
            "path", path
        )));
    }
}
