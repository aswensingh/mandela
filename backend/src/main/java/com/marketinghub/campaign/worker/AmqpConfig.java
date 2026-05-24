package com.marketinghub.campaign.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    public static final String CAMPAIGN_SEND_QUEUE = "campaign.send";
    public static final String CAMPAIGN_SEND_EXCHANGE = "campaign.send.exchange";
    public static final String CAMPAIGN_SEND_ROUTING_KEY = "campaign.send";

    @Bean
    public Queue campaignSendQueue() {
        return new Queue(CAMPAIGN_SEND_QUEUE, true);
    }

    @Bean
    public DirectExchange campaignSendExchange() {
        return new DirectExchange(CAMPAIGN_SEND_EXCHANGE, true, false);
    }

    @Bean
    public Binding campaignSendBinding(Queue campaignSendQueue, DirectExchange campaignSendExchange) {
        return BindingBuilder.bind(campaignSendQueue)
            .to(campaignSendExchange)
            .with(CAMPAIGN_SEND_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
