package com.tradingsaas.marketdata.adapter.out.messaging;

import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import com.tradingsaas.marketdata.config.MarketDataMessagingProperties;
import com.tradingsaas.marketdata.domain.port.out.MarketDataEventPublisher;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqMarketDataEventPublisher implements MarketDataEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String routingKey;

    public RabbitMqMarketDataEventPublisher(
            RabbitTemplate rabbitTemplate,
            MarketDataMessagingProperties properties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.routingKey = Objects.requireNonNull(
                properties.pricesUpdatedRoutingKey() == null || properties.pricesUpdatedRoutingKey().isBlank()
                        ? "market-data.prices.updated"
                        : properties.pricesUpdatedRoutingKey(),
                "routingKey must not be null");
    }

    @Override
    public void publishPricesUpdated(Symbol symbol, TimeFrame timeFrame, LocalDate from, LocalDate to, int count) {
        MarketDataPricesUpdatedEvent event = new MarketDataPricesUpdatedEvent(
                symbol.ticker(),
                timeFrame.name(),
                from,
                to,
                count);
        rabbitTemplate.convertAndSend(routingKey, event);
    }
}
