package com.tradingsaas.tradingcore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingsaas.tradingcore.adapter.in.web.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.Map;

@Configuration
class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    SecurityConfig(JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeUnauthorized(response, request.getRequestURI()))
                .accessDeniedHandler((request, response, e) ->
                    writeForbidden(response, request.getRequestURI()))
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("GET",  "/actuator/health").permitAll()
                .requestMatchers("GET",  "/api/v1/symbols").permitAll()
                .requestMatchers("GET",  "/api/v1/prices/**").permitAll()
                .requestMatchers("POST", "/api/v1/auth/**").permitAll()
                // Admin-only endpoints
                .requestMatchers("POST", "/api/v1/ingestion/**").hasRole("ADMIN")
                .requestMatchers("POST", "/api/v1/models/**").hasRole("ADMIN")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws java.io.IOException {
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

    private void writeForbidden(HttpServletResponse response, String path) throws java.io.IOException {
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
