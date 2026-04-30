package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.application.usecase.SubscriptionUsageLedgerService;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

class SubscriptionUsageLedgerInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SubscriptionUsageLedgerService ledgerService;

    SubscriptionUsageLedgerInterceptor(SubscriptionUsageLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TokenClaims claims = authenticatedClaims();
        if (claims == null) {
            return;
        }

        String featureKey = featureKey(request.getMethod(), request.getRequestURI());
        if (featureKey == null) {
            return;
        }

        ledgerService.record(claims, featureKey, request.getMethod(), request.getRequestURI(), response.getStatus());
    }

    private TokenClaims authenticatedClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof TokenClaims claims ? claims : null;
    }

    private String featureKey(String method, String path) {
        if ("GET".equals(method) && PATH_MATCHER.match("/api/v1/prices/*/history", path)) {
            return "historical_prices";
        }
        if ("POST".equals(method) && "/api/v1/backtests".equals(path)) {
            return "backtest_submit";
        }
        if ("POST".equals(method) && "/api/v1/strategies".equals(path)) {
            return "strategy_create";
        }
        if ("PUT".equals(method) && PATH_MATCHER.match("/api/v1/strategies/*", path)) {
            return "strategy_update";
        }
        return null;
    }
}
