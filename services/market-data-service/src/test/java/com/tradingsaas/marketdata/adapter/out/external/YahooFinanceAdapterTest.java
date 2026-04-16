package com.tradingsaas.marketdata.adapter.out.external;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class YahooFinanceAdapterTest {

    private WireMockServer wireMockServer;
    private YahooFinanceAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        WebClient webClient = WebClient.builder().baseUrl(wireMockServer.baseUrl()).build();
        adapter = new YahooFinanceAdapter(webClient);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void fetchHistoricalDataParsesYahooChartResponse() {
        stubFor(get(urlPathEqualTo("/v8/finance/chart/AAPL"))
                .willReturn(okJson("""
                        {
                          "chart": {
                            "result": [{
                              "timestamp": [1713225600, 1713312000],
                              "indicators": {
                                "quote": [{
                                  "open": [10.0, 11.0],
                                  "high": [12.0, 13.0],
                                  "low": [9.5, 10.5],
                                  "close": [11.5, 12.5],
                                  "volume": [1000, 1200]
                                }],
                                "adjclose": [{
                                  "adjclose": [11.4, 12.4]
                                }]
                              }
                            }],
                            "error": null
                          }
                        }
                        """)));

        List<StockPrice> prices = adapter.fetchHistoricalData(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                TimeFrame.DAILY,
                LocalDate.of(2024, 4, 16),
                LocalDate.of(2024, 4, 17));

        assertEquals(2, prices.size());
        assertEquals(new BigDecimal("11.4"), prices.get(0).adjustedClose());
        assertEquals(LocalDate.of(2024, 4, 16), prices.get(0).date());
        assertEquals(new BigDecimal("12.5"), prices.get(1).ohlcv().close());
    }

    @Test
    void fetchHistoricalDataRejectsInvertedDateRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.fetchHistoricalData(
                        new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                        TimeFrame.DAILY,
                        LocalDate.of(2024, 4, 17),
                        LocalDate.of(2024, 4, 16)));
    }
}
