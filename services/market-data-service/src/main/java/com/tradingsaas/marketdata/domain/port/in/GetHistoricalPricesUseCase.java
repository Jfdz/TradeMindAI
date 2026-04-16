package com.tradingsaas.marketdata.domain.port.in;

import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetHistoricalPricesUseCase {

    Page<StockPrice> getHistoricalPrices(
            String ticker, TimeFrame timeFrame, LocalDate from, LocalDate to, Pageable pageable);
}
