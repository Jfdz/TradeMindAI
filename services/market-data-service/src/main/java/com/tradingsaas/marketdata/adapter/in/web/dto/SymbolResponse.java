package com.tradingsaas.marketdata.adapter.in.web.dto;

public record SymbolResponse(String ticker, String name, String exchange, String sector, boolean active) {
}
