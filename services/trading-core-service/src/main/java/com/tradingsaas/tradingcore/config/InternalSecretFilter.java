package com.tradingsaas.tradingcore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the service-to-service routes under {@code /api/v1/internal/**}
 * via a shared secret header. ai-engine is the primary caller; the web
 * app never reaches these endpoints. The configured secret is the same
 * one trading-core already presents to market-data — both ends derive
 * it from {@code INTERNAL_API_SECRET}.
 */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Secret";
    private static final String INTERNAL_API_PATH = "/api/v1/internal/";

    private final String internalSecret;

    public InternalSecretFilter(@Value("${trading-core.internal-secret:}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(INTERNAL_API_PATH)) {
            if (internalSecret == null || internalSecret.isBlank()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Internal auth not configured");
                return;
            }
            String provided = request.getHeader(HEADER);
            if (!internalSecret.equals(provided)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid X-Internal-Secret");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
