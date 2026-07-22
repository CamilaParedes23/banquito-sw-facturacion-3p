package com.banquito.switchpagos.billing.listener;

import com.banquito.switchpagos.billing.dto.event.BatchLinesCompletedEvent;
import com.banquito.switchpagos.billing.service.BillingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "rabbitmq", matchIfMissing = true)
public class BatchLinesCompletedListener {

    private final BillingService billingService;

    public BatchLinesCompletedListener(BillingService billingService) {
        this.billingService = billingService;
    }

    @RabbitListener(queues = "${rabbit.queue.billing.batch-completed}")
    public void onBatchLinesCompleted(BatchLinesCompletedEvent event) {
        billingService.processBatchLinesCompleted(event);
    }
}
