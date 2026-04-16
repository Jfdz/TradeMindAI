package com.tradingsaas.marketdata.adapter.out.persistence.entity;

import com.tradingsaas.marketdata.domain.model.TimeFrame;
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

@Entity
@Table(name = "stock_prices", schema = "market_data")
public class StockPriceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "symbol_ticker", nullable = false)
    private SymbolEntity symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_frame", nullable = false, length = 16)
    private TimeFrame timeFrame;

    @Column(name = "open", nullable = false, precision = 19, scale = 6)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 19, scale = 6)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 19, scale = 6)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    @Column(name = "adjusted_close", nullable = false, precision = 19, scale = 6)
    private BigDecimal adjustedClose;

    @Column(name = "volume", nullable = false)
    private long volume;

    protected StockPriceEntity() {}

    public StockPriceEntity(
            SymbolEntity symbol,
            LocalDate date,
            TimeFrame timeFrame,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal adjustedClose,
            long volume) {
        this.symbol = symbol;
        this.date = date;
        this.timeFrame = timeFrame;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.adjustedClose = adjustedClose;
        this.volume = volume;
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

    public TimeFrame getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(TimeFrame timeFrame) {
        this.timeFrame = timeFrame;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getAdjustedClose() {
        return adjustedClose;
    }

    public void setAdjustedClose(BigDecimal adjustedClose) {
        this.adjustedClose = adjustedClose;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }
}
