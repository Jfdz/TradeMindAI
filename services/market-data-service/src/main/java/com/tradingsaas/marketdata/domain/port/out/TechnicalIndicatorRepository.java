package com.tradingsaas.marketdata.domain.port.out;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for technical indicators.
 */
public interface TechnicalIndicatorRepository {

    List<TechnicalIndicator> saveAll(List<TechnicalIndicator> indicators);

    List<TechnicalIndicator> findBySymbolAndDate(Symbol symbol, LocalDate date);

    List<TechnicalIndicator> findBySymbolAndDateRange(Symbol symbol, LocalDate from, LocalDate to);

    Optional<TechnicalIndicator> findBySymbolAndDateAndType(Symbol symbol, LocalDate date, TechnicalIndicatorType type);

    List<TechnicalIndicator> findLatestByTicker(String ticker, List<TechnicalIndicatorType> types);
}
