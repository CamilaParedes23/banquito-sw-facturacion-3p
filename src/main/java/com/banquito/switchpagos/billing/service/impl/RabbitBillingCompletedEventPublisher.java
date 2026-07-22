package com.banquito.switchpagos.billing.service.impl;

import com.banquito.switchpagos.billing.dto.event.BillingCompletedEvent;
import com.banquito.switchpagos.billing.service.BillingCompletedEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class RabbitBillingCompletedEventPublisher implements BillingCompletedEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String billingExchange;
    private final String billingCompletedRoutingKey;

    public RabbitBillingCompletedEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${rabbit.exchange.billing}") String billingExchange,
            @Value("${rabbit.routing-key.billing-completed}") String billingCompletedRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.billingExchange = billingExchange;
        this.billingCompletedRoutingKey = billingCompletedRoutingKey;
    }

    @Override
    public void publish(BillingCompletedEvent event) {
        rabbitTemplate.convertAndSend(billingExchange, billingCompletedRoutingKey, event);
    }
}
