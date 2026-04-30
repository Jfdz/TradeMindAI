package com.tradingsaas.marketdata.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Secret";
    private static final String INTERNAL_API_PATH = "/api/v1/";

    private final String internalSecret;

    public InternalSecretFilter(@Value("${market-data.internal-secret:}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith(INTERNAL_API_PATH)) {
            if (internalSecret.isBlank()) {
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
