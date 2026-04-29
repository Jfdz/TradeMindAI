package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.SubscriptionUsageLedgerJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.SubscriptionUsageLedgerJpaEntity;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class SubscriptionUsageLedgerServiceTest {

    @Test
    void recordsAllowedUsageWithNormalizedPlan() {
        SubscriptionUsageLedgerJpaRepository repository = Mockito.mock(SubscriptionUsageLedgerJpaRepository.class);
        SubscriptionUsageLedgerService service = new SubscriptionUsageLedgerService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(
                new TokenClaims(
                        java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "user@example.com",
                        "basic"),
                "backtest_submit",
                "POST",
                "/api/v1/backtests",
                202);

        ArgumentCaptor<SubscriptionUsageLedgerJpaEntity> captor =
                ArgumentCaptor.forClass(SubscriptionUsageLedgerJpaEntity.class);
        verify(repository).save(captor.capture());

        SubscriptionUsageLedgerJpaEntity saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("BASIC", saved.getSubscriptionPlan());
        assertEquals("backtest_submit", saved.getFeatureKey());
        assertEquals("POST", saved.getHttpMethod());
        assertEquals("/api/v1/backtests", saved.getRequestPath());
        assertEquals(202, saved.getResponseStatus());
        assertEquals("ALLOWED", saved.getOutcome());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void recordsDeniedUsageAsFreeWhenPlanMissing() {
        SubscriptionUsageLedgerJpaRepository repository = Mockito.mock(SubscriptionUsageLedgerJpaRepository.class);
        SubscriptionUsageLedgerService service = new SubscriptionUsageLedgerService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(
                new TokenClaims(
                        java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "user@example.com",
                        null),
                "historical_prices",
                "GET",
                "/api/v1/prices/AAPL/history",
                402);

        ArgumentCaptor<SubscriptionUsageLedgerJpaEntity> captor =
                ArgumentCaptor.forClass(SubscriptionUsageLedgerJpaEntity.class);
        verify(repository).save(captor.capture());

        SubscriptionUsageLedgerJpaEntity saved = captor.getValue();
        assertEquals("FREE", saved.getSubscriptionPlan());
        assertEquals("DENIED", saved.getOutcome());
        assertEquals(402, saved.getResponseStatus());
    }
}
