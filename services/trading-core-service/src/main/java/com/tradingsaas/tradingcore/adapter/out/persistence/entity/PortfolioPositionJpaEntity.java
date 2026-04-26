package com.tradingsaas.tradingcore.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions", schema = "trading_core")
public class PortfolioPositionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private PortfolioJpaEntity portfolio;

    @Column(name = "symbol_ticker", nullable = false, length = 16)
    private String symbolTicker;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected PortfolioPositionJpaEntity() {}

    public PortfolioPositionJpaEntity(UUID id, PortfolioJpaEntity portfolio, String symbolTicker, BigDecimal quantity,
                                      BigDecimal entryPrice, String status, Instant openedAt, Instant closedAt) {
        this.id = id;
        this.portfolio = portfolio;
        this.symbolTicker = symbolTicker;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.status = status;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
    }

    public UUID getId() {
        return id;
    }

    public PortfolioJpaEntity getPortfolio() {
        return portfolio;
    }

    public String getSymbolTicker() {
        return symbolTicker;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public String getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
