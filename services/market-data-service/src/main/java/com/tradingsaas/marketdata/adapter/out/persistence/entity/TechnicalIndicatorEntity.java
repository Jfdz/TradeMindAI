package com.tradingsaas.marketdata.adapter.out.persistence.entity;

import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "technical_indicators", schema = "market_data")
public class TechnicalIndicatorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "symbol_ticker", nullable = false)
    private SymbolEntity symbol;

    @Column(name = "indicator_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 32)
    private TechnicalIndicatorType type;

    @Column(name = "value", nullable = false, precision = 19, scale = 6)
    private BigDecimal value;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    protected TechnicalIndicatorEntity() {}

    public TechnicalIndicatorEntity(
            SymbolEntity symbol,
            LocalDate date,
            TechnicalIndicatorType type,
            BigDecimal value,
            Map<String, String> metadata) {
        this.symbol = symbol;
        this.date = date;
        this.type = type;
        this.value = value;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SymbolEntity getSymbol() {
        return symbol;
    }

    public void setSymbol(SymbolEntity symbol) {
        this.symbol = symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public TechnicalIndicatorType getType() {
        return type;
    }

    public void setType(TechnicalIndicatorType type) {
        this.type = type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
