package com.tradingsaas.tradingcore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.in.web.JitProvisioningFilter;
import com.tradingsaas.tradingcore.adapter.in.web.RateLimitFilter;
import com.tradingsaas.tradingcore.domain.port.out.UserRepository;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final LettuceBasedProxyManager<String> rateLimitProxyManager;
    private final UserRepository userRepository;
    private final String[] allowedCorsOrigins;
    private final long rateLimitFreePm;
    private final long rateLimitBasicPm;
    private final long rateLimitPremiumPm;
    private final String clerkIssuerUri;

    SecurityConfig(ObjectMapper objectMapper,
                   LettuceBasedProxyManager<String> rateLimitProxyManager,
                   UserRepository userRepository,
                   @Value("${trading-core.cors.allowed-origins}") String[] allowedCorsOrigins,
                   @Value("${trading-core.rate-limit.free-per-minute:5}") long rateLimitFreePm,
                   @Value("${trading-core.rate-limit.basic-per-minute:50}") long rateLimitBasicPm,
                   @Value("${trading-core.rate-limit.premium-per-minute:500}") long rateLimitPremiumPm,
                   @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String clerkIssuerUri) {
        this.objectMapper = objectMapper;
        this.rateLimitProxyManager = rateLimitProxyManager;
        this.userRepository = userRepository;
        this.allowedCorsOrigins = allowedCorsOrigins;
        this.rateLimitFreePm = rateLimitFreePm;
        this.rateLimitBasicPm = rateLimitBasicPm;
        this.rateLimitPremiumPm = rateLimitPremiumPm;
        this.clerkIssuerUri = clerkIssuerUri;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JitProvisioningFilter jitFilter = new JitProvisioningFilter(userRepository);
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
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                .requestMatchers("/api/v1/subscriptions/plans").permitAll()
                .requestMatchers("/api/v1/backtests/symbols/*/available").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/prices/latest", "/api/v1/prices/*/latest").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/ingestion/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/models/**").hasRole("ADMIN")
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
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .addFilterAfter(jitFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, JitProvisioningFilter.class);

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withIssuerLocation(clerkIssuerUri)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(clerkIssuerUri);
        decoder.setJwtValidator(issuerValidator);
        return decoder;
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

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = grantedAuthoritiesConverter.convert(jwt);
            if (authorities == null || authorities.isEmpty()) {
                return List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
            }
            return authorities;
        });
        return converter;
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
