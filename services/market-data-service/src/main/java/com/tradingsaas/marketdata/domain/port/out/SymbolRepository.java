package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.Symbol;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence port for tracked symbols.
 */
public interface SymbolRepository {

    Page<Symbol> findAll(Pageable pageable);

    Optional<Symbol> findByTicker(String ticker);
}
