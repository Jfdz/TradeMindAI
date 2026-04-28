package com.tradingsaas.marketdata.adapter.out.persistence.entity;

import com.tradingsaas.marketdata.domain.model.TimeFrame;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_data_outbox", schema = "market_data")
public class MarketDataOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "symbol_ticker", nullable = false, length = 32)
    private String symbolTicker;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_frame", nullable = false, length = 16)
    private TimeFrame timeFrame;

    @Column(name = "range_from", nullable = false)
    private LocalDate rangeFrom;

    @Column(name = "range_to", nullable = false)
    private LocalDate rangeTo;

    @Column(name = "price_count", nullable = false)
    private int priceCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected MarketDataOutboxJpaEntity() {}

    public MarketDataOutboxJpaEntity(
            Long id,
            String eventType,
            String symbolTicker,
            TimeFrame timeFrame,
            LocalDate rangeFrom,
            LocalDate rangeTo,
            int priceCount,
            Instant createdAt,
            Instant publishedAt,
            int attemptCount,
            String lastError) {
        this.id = id;
        this.eventType = eventType;
        this.symbolTicker = symbolTicker;
        this.timeFrame = timeFrame;
        this.rangeFrom = rangeFrom;
        this.rangeTo = rangeTo;
        this.priceCount = priceCount;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.attemptCount = attemptCount;
        this.lastError = lastError;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSymbolTicker() {
        return symbolTicker;
    }

    public TimeFrame getTimeFrame() {
        return timeFrame;
    }

    public LocalDate getRangeFrom() {
        return rangeFrom;
    }

    public LocalDate getRangeTo() {
        return rangeTo;
    }

    public int getPriceCount() {
        return priceCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }
}
