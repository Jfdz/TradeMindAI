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
import java.time.Instant;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "trading-core:rate-limit:";

    private final LettuceBasedProxyManager<String> proxyManager;
    private final Map<SubscriptionPlan, Long> planLimits;

    public RateLimitFilter(LettuceBasedProxyManager<String> proxyManager, long freePm, long basicPm, long premiumPm) {
        this.proxyManager = proxyManager;
        this.planLimits = Map.of(
                SubscriptionPlan.FREE, freePm,
                SubscriptionPlan.BASIC, basicPm,
                SubscriptionPlan.PREMIUM, premiumPm
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.equals("/api/v1/subscriptions/plans");
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
        long limit = planLimits.getOrDefault(plan, planLimits.get(SubscriptionPlan.FREE));

        // Per-minute bucket key — rolls over automatically when a new bucket is created
        String minuteKey = KEY_PREFIX + claims.userId() + ":" + (Instant.now().getEpochSecond() / 60);

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit)
                        .refillGreedy(limit, Duration.ofMinutes(1))
                        .build())
                .build();

        Bucket bucket = proxyManager.builder().build(minuteKey, () -> config);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long remaining = Math.max(0, probe.getRemainingTokens());
        long resetEpoch = (Instant.now().getEpochSecond() / 60 + 1) * 60;

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
