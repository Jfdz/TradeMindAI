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
import java.time.LocalDate;
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

    @Column(name = "exit_price", precision = 18, scale = 4)
    private BigDecimal exitPrice;

    @Column(name = "fees", nullable = false, precision = 10, scale = 4)
    private BigDecimal fees = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected PortfolioPositionJpaEntity() {}

    public PortfolioPositionJpaEntity(UUID id, PortfolioJpaEntity portfolio, String symbolTicker,
                                      BigDecimal quantity, BigDecimal entryPrice, BigDecimal fees,
                                      String notes, LocalDate purchaseDate,
                                      String status, Instant openedAt) {
        this.id = id;
        this.portfolio = portfolio;
        this.symbolTicker = symbolTicker;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.fees = fees != null ? fees : BigDecimal.ZERO;
        this.notes = notes;
        this.purchaseDate = purchaseDate;
        this.status = status;
        this.openedAt = openedAt;
    }

    public UUID getId() { return id; }
    public PortfolioJpaEntity getPortfolio() { return portfolio; }
    public String getSymbolTicker() { return symbolTicker; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public BigDecimal getFees() { return fees != null ? fees : BigDecimal.ZERO; }
    public String getNotes() { return notes; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public String getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }

    public void update(BigDecimal quantity, BigDecimal entryPrice, BigDecimal fees,
                       String notes, LocalDate purchaseDate) {
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.fees = fees != null ? fees : BigDecimal.ZERO;
        this.notes = notes;
        this.purchaseDate = purchaseDate;
    }

    public void close(BigDecimal exitPrice, BigDecimal additionalFees, Instant closedAt) {
        this.exitPrice = exitPrice;
        if (additionalFees != null) {
            this.fees = this.fees.add(additionalFees);
        }
        this.status = "CLOSED";
        this.closedAt = closedAt;
    }
}
