package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trading_signals", schema = "trading_core")
public class TradingSignalJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "symbol_id")
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

    @Column(name = "entry_price", precision = 18, scale = 6)
    private BigDecimal entryPrice;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(name = "reasoning_status", nullable = false, length = 16)
    private ReasoningStatus reasoningStatus;

    @Column(name = "reasoning_generated_at")
    private Instant reasoningGeneratedAt;

    // -- C6 audit columns (V20). All nullable. Populated by ai-engine
    // -- via PUT /api/v1/internal/signals/{id}/reasoning.

    @Column(name = "reasoning_outcome", length = 50)
    private String reasoningOutcome;

    @Column(name = "reasoning_provider", length = 50)
    private String reasoningProvider;

    @Column(name = "reasoning_model_version", length = 100)
    private String reasoningModelVersion;

    @Column(name = "reasoning_retry_count", nullable = false)
    private int reasoningRetryCount;

    @Column(name = "reasoning_refusal_reason", columnDefinition = "TEXT")
    private String reasoningRefusalReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning_facts_snapshot", columnDefinition = "JSONB")
    private Map<String, Object> reasoningFactsSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning_price_refs", columnDefinition = "JSONB")
    private List<String> reasoningPriceRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning_news_refs", columnDefinition = "JSONB")
    private List<String> reasoningNewsRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning_validator_violations", columnDefinition = "JSONB")
    private List<Map<String, Object>> reasoningValidatorViolations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasoning_raw_audit", columnDefinition = "JSONB")
    private Map<String, Object> reasoningRawAudit;

    protected TradingSignalJpaEntity() {}

    public TradingSignalJpaEntity(UUID id, UUID symbolId, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct) {
        this(id, symbolId, null, signalType, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, null, null, null, ReasoningStatus.PENDING, null);
    }

    public TradingSignalJpaEntity(UUID id, UUID symbolId, String ticker, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal predictedChangePct) {
        this(id, symbolId, ticker, signalType, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, predictedChangePct, null, null, ReasoningStatus.PENDING, null);
    }

    public TradingSignalJpaEntity(UUID id, UUID symbolId, String ticker, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal predictedChangePct,
                                  BigDecimal entryPrice) {
        this(id, symbolId, ticker, signalType, confidence, timeframe, generatedAt, stopLossPct, takeProfitPct, predictedChangePct, entryPrice, null, ReasoningStatus.PENDING, null);
    }

    public TradingSignalJpaEntity(UUID id, UUID symbolId, String ticker, SignalType signalType, BigDecimal confidence,
                                  Timeframe timeframe, Instant generatedAt,
                                  BigDecimal stopLossPct, BigDecimal takeProfitPct, BigDecimal predictedChangePct,
                                  BigDecimal entryPrice, String reasoning, ReasoningStatus reasoningStatus,
                                  Instant reasoningGeneratedAt) {
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
        this.entryPrice = entryPrice;
        this.reasoning = reasoning;
        this.reasoningStatus = reasoningStatus;
        this.reasoningGeneratedAt = reasoningGeneratedAt;
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
    public BigDecimal getEntryPrice() { return entryPrice; }
    public String getReasoning() { return reasoning; }
    public ReasoningStatus getReasoningStatus() { return reasoningStatus; }
    public Instant getReasoningGeneratedAt() { return reasoningGeneratedAt; }

    public String getReasoningOutcome() { return reasoningOutcome; }
    public String getReasoningProvider() { return reasoningProvider; }
    public String getReasoningModelVersion() { return reasoningModelVersion; }
    public int getReasoningRetryCount() { return reasoningRetryCount; }
    public String getReasoningRefusalReason() { return reasoningRefusalReason; }
    public Map<String, Object> getReasoningFactsSnapshot() { return reasoningFactsSnapshot; }
    public List<String> getReasoningPriceRefs() { return reasoningPriceRefs; }
    public List<String> getReasoningNewsRefs() { return reasoningNewsRefs; }
    public List<Map<String, Object>> getReasoningValidatorViolations() { return reasoningValidatorViolations; }
    public Map<String, Object> getReasoningRawAudit() { return reasoningRawAudit; }

    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public void setReasoningStatus(ReasoningStatus reasoningStatus) { this.reasoningStatus = reasoningStatus; }
    public void setReasoningGeneratedAt(Instant reasoningGeneratedAt) { this.reasoningGeneratedAt = reasoningGeneratedAt; }

    public void setReasoningOutcome(String reasoningOutcome) { this.reasoningOutcome = reasoningOutcome; }
    public void setReasoningProvider(String reasoningProvider) { this.reasoningProvider = reasoningProvider; }
    public void setReasoningModelVersion(String reasoningModelVersion) { this.reasoningModelVersion = reasoningModelVersion; }
    public void setReasoningRetryCount(int reasoningRetryCount) { this.reasoningRetryCount = reasoningRetryCount; }
    public void setReasoningRefusalReason(String reasoningRefusalReason) { this.reasoningRefusalReason = reasoningRefusalReason; }
    public void setReasoningFactsSnapshot(Map<String, Object> reasoningFactsSnapshot) { this.reasoningFactsSnapshot = reasoningFactsSnapshot; }
    public void setReasoningPriceRefs(List<String> reasoningPriceRefs) { this.reasoningPriceRefs = reasoningPriceRefs; }
    public void setReasoningNewsRefs(List<String> reasoningNewsRefs) { this.reasoningNewsRefs = reasoningNewsRefs; }
    public void setReasoningValidatorViolations(List<Map<String, Object>> reasoningValidatorViolations) { this.reasoningValidatorViolations = reasoningValidatorViolations; }
    public void setReasoningRawAudit(Map<String, Object> reasoningRawAudit) { this.reasoningRawAudit = reasoningRawAudit; }
}
