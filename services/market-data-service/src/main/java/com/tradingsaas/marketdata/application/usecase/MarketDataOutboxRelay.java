package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.adapter.out.persistence.entity.MarketDataOutboxJpaEntity;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataOutboxRelay {

    private final MarketDataOutboxService outboxService;
    private final MarketDataEventPublisher eventPublisher;

    public MarketDataOutboxRelay(MarketDataOutboxService outboxService, MarketDataEventPublisher eventPublisher) {
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Scheduled(fixedDelayString = "${market-data.events.outbox-poll-delay-ms:5000}")
    public void publishPending() {
        for (MarketDataOutboxJpaEntity event : outboxService.findPendingBatch()) {
            publishOne(event);
        }
    }

    void publishOne(MarketDataOutboxJpaEntity event) {
        try {
            eventPublisher.publishPricesUpdated(
                    new Symbol(event.getSymbolTicker(), event.getSymbolTicker(), "UNKNOWN"),
                    event.getTimeFrame(),
                    event.getRangeFrom(),
                    event.getRangeTo(),
                    event.getPriceCount()
            );
            outboxService.markPublished(event.getId(), Instant.now());
        } catch (RuntimeException ex) {
            outboxService.markFailed(event.getId(), ex.getMessage());
        }
    }
}
