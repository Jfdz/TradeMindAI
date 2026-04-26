package com.tradingsaas.tradingcore.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PREDICTION_RESULT_QUEUE = "trading-core.prediction.result.completed";
    public static final String PREDICTION_RESULT_EXCHANGE = "prediction.result.completed";
    static final String PREDICTION_DLX = "dlx.prediction.result.completed";
    static final String PREDICTION_DLQ = "dlq.prediction.result.completed";
    static final String PREDICTION_DLX_ROUTING_KEY = "dead";

    @Bean
    DirectExchange predictionDlx() {
        return new DirectExchange(PREDICTION_DLX, true, false);
    }

    @Bean
    Queue predictionDlq() {
        return QueueBuilder.durable(PREDICTION_DLQ).build();
    }

    @Bean
    Binding predictionDlqBinding(Queue predictionDlq, DirectExchange predictionDlx) {
        return BindingBuilder.bind(predictionDlq).to(predictionDlx).with(PREDICTION_DLX_ROUTING_KEY);
    }

    @Bean
    FanoutExchange predictionResultExchange() {
        return new FanoutExchange(PREDICTION_RESULT_EXCHANGE, true, false);
    }

    @Bean
    Queue predictionResultQueue() {
        return QueueBuilder.durable(PREDICTION_RESULT_QUEUE)
                .withArgument("x-dead-letter-exchange", PREDICTION_DLX)
                .withArgument("x-dead-letter-routing-key", PREDICTION_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding predictionResultQueueBinding(Queue predictionResultQueue, FanoutExchange predictionResultExchange) {
        return BindingBuilder.bind(predictionResultQueue).to(predictionResultExchange);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
