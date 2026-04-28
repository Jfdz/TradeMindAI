package com.tradingsaas.marketdata.adapter.out.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.config.MarketDataMessagingProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMqMarketDataEventPublisherTest {

    @Test
    void publishPricesUpdatedSendsEventToRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqMarketDataEventPublisher publisher = new RabbitMqMarketDataEventPublisher(
                rabbitTemplate,
                new MarketDataMessagingProperties("market-data.prices.updated"));

        publisher.publishPricesUpdated(
                new Symbol("AAPL", "Apple Inc.", "NASDAQ"),
                TimeFrame.DAILY,
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 16),
                42);

        verify(rabbitTemplate).convertAndSend(eq("market-data.prices"), eq(""), any(MarketDataPricesUpdatedEvent.class));
    }
}
