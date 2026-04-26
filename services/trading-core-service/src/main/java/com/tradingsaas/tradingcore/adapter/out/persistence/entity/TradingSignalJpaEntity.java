package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
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
@Table(name = "trading_signals", schema = "trading_core")
public class TradingSignalJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "symbol_id", nullable = false)
    private UUID symbolId;

    @Column(name = "ticker", length = 32)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 10)
    private SignalType signalType;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe", nullable = false, length = 20)
    private Timeframe timeframe;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "stop_loss_pct", precision = 5, scale = 2)
    private BigDecimal stopLossPct;

    @Column(name = "take_profit_pct", precision = 5, scale = 2)
    private BigDecimal takeProfitPct;

    @Column(name = "predicted_change_pct", precision = 8, scale = 4)
    private BigDecimal predictedChangePct;

    protected TradingSignalJpaEntity() {}

    public TradingSignalJpaEntity(UUID id, UUID symbolId, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct) {
        this(id, symbolId, null, signalType, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null);
    }

    public TradingSignalJpaEntity(UUID id, UUID symbolId, String ticker, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal predictedChangePct) {
        this.id = id;
        this.symbolId = symbolId;
        this.ticker = ticker;
        this.signalType = signalType;
        this.confidence = confidence;
        this.timeframe = timeframe;
        this.generatedAt = generatedAt;
        this.stopLossPct = stopLossPct;
        this.takeProfitPct = takeProfitPct;
        this.predictedChangePct = predictedChangePct;
    }

    public UUID getId() { return id; }
    public UUID getSymbolId() { return symbolId; }
    public String getTicker() { return ticker; }
    public SignalType getSignalType() { return signalType; }
    public BigDecimal getConfidence() { return confidence; }
    public Timeframe getTimeframe() { return timeframe; }
    public Instant getGeneratedAt() { return generatedAt; }
    public BigDecimal getStopLossPct() { return stopLossPct; }
    public BigDecimal getTakeProfitPct() { return takeProfitPct; }
    public BigDecimal getPredictedChangePct() { return predictedChangePct; }
}
