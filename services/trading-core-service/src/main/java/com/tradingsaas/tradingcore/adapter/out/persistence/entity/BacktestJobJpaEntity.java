package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "backtest_jobs", schema = "trading_core")
public class BacktestJobJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "symbol", nullable = false, length = 32)
    private String symbol;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BacktestStatus status;

    @Column(name = "result_payload", columnDefinition = "TEXT")
    private String resultPayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BacktestJobJpaEntity() {}

    public BacktestJobJpaEntity(UUID id,
                                String symbol,
                                LocalDate fromDate,
                                LocalDate toDate,
                                int quantity,
                                BacktestStatus status,
                                String resultPayload,
                                String errorMessage,
                                Instant createdAt,
                                Instant updatedAt) {
        this.id = id;
        this.symbol = symbol;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.quantity = quantity;
        this.status = status;
        this.resultPayload = resultPayload;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public int getQuantity() {
        return quantity;
    }

    public BacktestStatus getStatus() {
        return status;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
