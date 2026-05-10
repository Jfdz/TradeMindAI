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

    public static final String REASONING_QUEUE = "trading-core.signal.reasoning.requested";
    static final String REASONING_EXCHANGE = "signal.reasoning.requested";
    static final String REASONING_DLX = "dlx.signal.reasoning.requested";
    static final String REASONING_DLQ = "dlq.signal.reasoning.requested";
    static final String REASONING_DLX_ROUTING_KEY = "dead";

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
    DirectExchange reasoningDlx() {
        return new DirectExchange(REASONING_DLX, true, false);
    }

    @Bean
    Queue reasoningDlq() {
        return QueueBuilder.durable(REASONING_DLQ).build();
    }

    @Bean
    Binding reasoningDlqBinding(Queue reasoningDlq, DirectExchange reasoningDlx) {
        return BindingBuilder.bind(reasoningDlq).to(reasoningDlx).with(REASONING_DLX_ROUTING_KEY);
    }

    @Bean
    DirectExchange reasoningExchange() {
        return new DirectExchange(REASONING_EXCHANGE, true, false);
    }

    @Bean
    Queue reasoningQueue() {
        return QueueBuilder.durable(REASONING_QUEUE)
                .withArgument("x-dead-letter-exchange", REASONING_DLX)
                .withArgument("x-dead-letter-routing-key", REASONING_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding reasoningQueueBinding(Queue reasoningQueue, DirectExchange reasoningExchange) {
        return BindingBuilder.bind(reasoningQueue).to(reasoningExchange).with(REASONING_QUEUE);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
