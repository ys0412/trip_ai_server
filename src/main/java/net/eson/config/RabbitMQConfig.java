package net.eson.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_NAME = "ai.plan.queue";
    public static final String EXCHANGE_NAME = "ai.plan.exchange";
    public static final String ROUTING_KEY = "ai.plan.key";

    @Bean
    public Queue planQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public DirectExchange planExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding planBinding() {
        return BindingBuilder.bind(planQueue())
                .to(planExchange())
                .with(ROUTING_KEY);
    }
}
