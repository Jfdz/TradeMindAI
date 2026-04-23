package com.tradingsaas.marketdata.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "symbols", schema = "market_data")
public class SymbolEntity {

    @Id
    @Column(name = "ticker", length = 16, nullable = false, updatable = false)
    private String ticker;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "sector", nullable = false)
    private String sector;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected SymbolEntity() {}

    public SymbolEntity(String ticker, String name, String exchange, String sector, boolean active) {
        this.ticker = ticker;
        this.name = name;
        this.exchange = exchange;
        this.sector = sector;
        this.active = active;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
