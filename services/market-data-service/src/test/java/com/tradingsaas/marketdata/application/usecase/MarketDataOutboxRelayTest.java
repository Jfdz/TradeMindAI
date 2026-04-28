package com.tradingsaas.marketdata.application.usecase;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.MarketDataOutboxJpaEntity;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataOutboxRelayTest {

    @Test
    void publishPendingMarksEventPublishedAfterSuccessfulSend() {
        MarketDataOutboxService outboxService = mock(MarketDataOutboxService.class);
        MarketDataEventPublisher eventPublisher = mock(MarketDataEventPublisher.class);
        MarketDataOutboxRelay relay = new MarketDataOutboxRelay(outboxService, eventPublisher);
        MarketDataOutboxJpaEntity event = event(7L);
        when(outboxService.findPendingBatch()).thenReturn(List.of(event));

        relay.publishPending();

        verify(eventPublisher).publishPricesUpdated(
                org.mockito.ArgumentMatchers.argThat(symbol -> "AAPL".equals(symbol.ticker())),
                org.mockito.ArgumentMatchers.eq(TimeFrame.DAILY),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 4, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 4, 16)),
                org.mockito.ArgumentMatchers.eq(5));
        verify(outboxService).markPublished(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void publishPendingMarksFailureWhenPublisherThrows() {
        MarketDataOutboxService outboxService = mock(MarketDataOutboxService.class);
        MarketDataEventPublisher eventPublisher = mock(MarketDataEventPublisher.class);
        MarketDataOutboxRelay relay = new MarketDataOutboxRelay(outboxService, eventPublisher);
        MarketDataOutboxJpaEntity event = event(9L);
        when(outboxService.findPendingBatch()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("rabbit unavailable")).when(eventPublisher)
                .publishPricesUpdated(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt());

        relay.publishPending();

        verify(outboxService).findPendingBatch();
        verify(outboxService).markFailed(9L, "rabbit unavailable");
    }

    private static MarketDataOutboxJpaEntity event(Long id) {
        return new MarketDataOutboxJpaEntity(
                id,
                MarketDataOutboxService.PRICES_UPDATED_EVENT,
                "AAPL",
                TimeFrame.DAILY,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 16),
                5,
                Instant.parse("2026-04-28T10:00:00Z"),
                null,
                0,
                null
        );
    }
}
