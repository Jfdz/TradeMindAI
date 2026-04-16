package com.tradingsaas.marketdata.domain.port.in;

import com.tradingsaas.marketdata.domain.model.Symbol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetSymbolsUseCase {

    Page<Symbol> getSymbols(Pageable pageable);
}
