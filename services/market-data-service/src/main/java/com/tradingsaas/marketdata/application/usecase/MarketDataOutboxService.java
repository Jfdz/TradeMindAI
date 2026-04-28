package com.tradingsaas.marketdata.application.usecase;

import com.tradingsaas.marketdata.adapter.out.persistence.MarketDataOutboxJpaRepository;
import com.tradingsaas.marketdata.adapter.out.persistence.entity.MarketDataOutboxJpaEntity;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketDataOutboxService {

    static final String PRICES_UPDATED_EVENT = "MARKET_DATA_PRICES_UPDATED";

    private final MarketDataOutboxJpaRepository repository;

    public MarketDataOutboxService(MarketDataOutboxJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Transactional
    public void enqueuePricesUpdated(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to, int count) {
        repository.save(new MarketDataOutboxJpaEntity(
                null,
                PRICES_UPDATED_EVENT,
                symbol.ticker(),
                timeFrame,
                from,
                to,
                count,
                Instant.now(),
                null,
                0,
                null
        ));
    }

    @Transactional(readOnly = true)
    public List<MarketDataOutboxJpaEntity> findPendingBatch() {
        return repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional
    public void markPublished(Long id, Instant publishedAt) {
        repository.findById(id)
                .map(event -> new MarketDataOutboxJpaEntity(
                        event.getId(),
                        event.getEventType(),
                        event.getSymbolTicker(),
                        event.getTimeFrame(),
                        event.getRangeFrom(),
                        event.getRangeTo(),
                        event.getPriceCount(),
                        event.getCreatedAt(),
                        publishedAt,
                        event.getAttemptCount() + 1,
                        null
                ))
                .ifPresent(repository::save);
    }

    @Transactional
    public void markFailed(Long id, String errorMessage) {
        repository.findById(id)
                .map(event -> new MarketDataOutboxJpaEntity(
                        event.getId(),
                        event.getEventType(),
                        event.getSymbolTicker(),
                        event.getTimeFrame(),
                        event.getRangeFrom(),
                        event.getRangeTo(),
                        event.getPriceCount(),
                        event.getCreatedAt(),
                        null,
                        event.getAttemptCount() + 1,
                        errorMessage
                ))
                .ifPresent(repository::save);
    }
}
