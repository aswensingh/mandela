package com.marketinghub.ai;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the AI reply queue. The Jackson converter + RabbitTemplate are
 * already declared by {@link com.marketinghub.campaign.worker.AmqpConfig}; here we only
 * add the new queue + exchange + binding.
 */
@Configuration
public class AIAmqpConfig {

    public static final String AI_REPLY_QUEUE = "ai.reply";
    public static final String AI_REPLY_EXCHANGE = "ai.reply.exchange";
    public static final String AI_REPLY_ROUTING_KEY = "ai.reply";

    @Bean
    public Queue aiReplyQueue() {
        return new Queue(AI_REPLY_QUEUE, true);
    }

    @Bean
    public DirectExchange aiReplyExchange() {
        return new DirectExchange(AI_REPLY_EXCHANGE, true, false);
    }

    @Bean
    public Binding aiReplyBinding(Queue aiReplyQueue, DirectExchange aiReplyExchange) {
        return BindingBuilder.bind(aiReplyQueue).to(aiReplyExchange).with(AI_REPLY_ROUTING_KEY);
    }
}
