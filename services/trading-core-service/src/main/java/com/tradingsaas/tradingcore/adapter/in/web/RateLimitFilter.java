package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "trading-core:rate-limit:";
    private static final Map<SubscriptionPlan, Long> PLAN_LIMITS = Map.of(
            SubscriptionPlan.FREE, 5L,
            SubscriptionPlan.BASIC, 50L
    );

    private final LettuceBasedProxyManager<String> proxyManager;

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.equals("/api/v1/subscriptions/plans")
                || path.equals("/api/v1/symbols")
                || path.startsWith("/api/v1/prices/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TokenClaims claims)) {
            chain.doFilter(request, response);
            return;
        }

        SubscriptionPlan plan = SubscriptionPlan.valueOf(claims.subscriptionPlan());
        if (plan == SubscriptionPlan.PREMIUM) {
            chain.doFilter(request, response);
            return;
        }

        long limit = PLAN_LIMITS.get(plan);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String key = KEY_PREFIX + claims.userId() + ":" + today;

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, Duration.ofDays(1))
                        .build())
                .build();

        Bucket bucket = proxyManager.builder().build(key, () -> config);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long remaining = Math.max(0, probe.getRemainingTokens());
        long resetEpoch = LocalDate.now(ZoneOffset.UTC)
                .plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpoch));

        if (!probe.isConsumed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded. Upgrade your plan for higher limits.\","
                    + "\"path\":\"" + request.getRequestURI() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
