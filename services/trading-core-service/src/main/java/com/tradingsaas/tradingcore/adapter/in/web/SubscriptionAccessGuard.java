package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionAccessGuard {

    private static final long FREE_HISTORY_DAYS = 365;

    public void requireHistoricalPriceAccess(LocalDate from, LocalDate to) {
        SubscriptionPlan userPlan = currentPlan();
        long requestedDays = ChronoUnit.DAYS.between(from, to);
        if (requestedDays > FREE_HISTORY_DAYS && userPlan.ordinal() < SubscriptionPlan.BASIC.ordinal()) {
            throw new InsufficientSubscriptionException(SubscriptionPlan.BASIC);
        }
    }

    private SubscriptionPlan currentPlan() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TokenClaims claims)) {
            throw new AccessDeniedException("Authentication required");
        }
        return SubscriptionPlan.valueOf(claims.subscriptionPlan());
    }
}
