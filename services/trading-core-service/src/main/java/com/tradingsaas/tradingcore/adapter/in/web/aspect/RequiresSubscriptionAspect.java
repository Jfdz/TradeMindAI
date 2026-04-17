package com.tradingsaas.tradingcore.adapter.in.web.aspect;

import com.tradingsaas.tradingcore.adapter.in.web.annotation.RequiresSubscription;
import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.model.SubscriptionPlan;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
class RequiresSubscriptionAspect {

    @Around("@annotation(requiresSubscription)")
    public Object checkSubscription(ProceedingJoinPoint pjp,
                                    RequiresSubscription requiresSubscription) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TokenClaims claims)) {
            throw new AccessDeniedException("Authentication required");
        }

        SubscriptionPlan userPlan = SubscriptionPlan.valueOf(claims.subscriptionPlan());
        SubscriptionPlan requiredPlan = requiresSubscription.value();

        if (userPlan.ordinal() < requiredPlan.ordinal()) {
            throw new InsufficientSubscriptionException(requiredPlan);
        }

        return pjp.proceed();
    }
}
