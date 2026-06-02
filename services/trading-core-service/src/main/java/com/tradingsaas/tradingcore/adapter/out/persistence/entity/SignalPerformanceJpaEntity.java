package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signal_performance", schema = "trading_core")
public class SignalPerformanceJpaEntity {

    @Id
    @Column(name = "signal_id", updatable = false, nullable = false)
    private UUID signalId;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "entry_price", precision = 18, scale = 6)
    private BigDecimal entryPrice;

    @Column(name = "price_1d", precision = 18, scale = 6)
    private BigDecimal price1d;

    @Column(name = "price_3d", precision = 18, scale = 6)
    private BigDecimal price3d;

    @Column(name = "price_7d", precision = 18, scale = 6)
    private BigDecimal price7d;

    @Column(name = "price_30d", precision = 18, scale = 6)
    private BigDecimal price30d;

    @Column(name = "max_profit", precision = 8, scale = 4)
    private BigDecimal maxProfit;

    @Column(name = "max_drawdown", precision = 8, scale = 4)
    private BigDecimal maxDrawdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 8)
    private SignalOutcome outcome;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SignalPerformanceJpaEntity() {}

    public SignalPerformanceJpaEntity(UUID signalId, String ticker, Instant generatedAt, BigDecimal entryPrice,
                                      BigDecimal price1d, BigDecimal price3d, BigDecimal price7d, BigDecimal price30d,
                                      BigDecimal maxProfit, BigDecimal maxDrawdown, SignalOutcome outcome,
                                      Instant resolvedAt, Instant evaluatedAt, Instant updatedAt) {
        this.signalId = signalId;
        this.ticker = ticker;
        this.generatedAt = generatedAt;
        this.entryPrice = entryPrice;
        this.price1d = price1d;
        this.price3d = price3d;
        this.price7d = price7d;
        this.price30d = price30d;
        this.maxProfit = maxProfit;
        this.maxDrawdown = maxDrawdown;
        this.outcome = outcome;
        this.resolvedAt = resolvedAt;
        this.evaluatedAt = evaluatedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getSignalId() { return signalId; }
    public String getTicker() { return ticker; }
    public Instant getGeneratedAt() { return generatedAt; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getPrice1d() { return price1d; }
    public BigDecimal getPrice3d() { return price3d; }
    public BigDecimal getPrice7d() { return price7d; }
    public BigDecimal getPrice30d() { return price30d; }
    public BigDecimal getMaxProfit() { return maxProfit; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public SignalOutcome getOutcome() { return outcome; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
