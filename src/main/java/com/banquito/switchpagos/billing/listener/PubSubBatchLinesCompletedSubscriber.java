package com.banquito.switchpagos.billing.listener;

import com.banquito.switchpagos.billing.dto.event.BatchLinesCompletedEvent;
import com.banquito.switchpagos.billing.service.BillingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "messaging.provider", havingValue = "pubsub")
public class PubSubBatchLinesCompletedSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubBatchLinesCompletedSubscriber.class);

    private final ObjectMapper objectMapper;
    private final BillingService billingService;
    private final String projectId;
    private final String subscriptionName;
    private Subscriber subscriber;

    public PubSubBatchLinesCompletedSubscriber(
            ObjectMapper objectMapper,
            BillingService billingService,
            @Value("${pubsub.project-id}") String projectId,
            @Value("${pubsub.subscription.billing-batch-lines-completed}") String subscriptionName) {
        this.objectMapper = objectMapper;
        this.billingService = billingService;
        this.projectId = projectId;
        this.subscriptionName = subscriptionName;
    }

    @PostConstruct
    public void start() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT es obligatorio cuando MESSAGING_PROVIDER=pubsub");
        }
        MessageReceiver receiver = this::receive;
        subscriber = Subscriber.newBuilder(ProjectSubscriptionName.of(projectId, subscriptionName), receiver).build();
        subscriber.startAsync().awaitRunning();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (subscriber != null) {
            subscriber.stopAsync().awaitTerminated(30, TimeUnit.SECONDS);
        }
    }

    private void receive(PubsubMessage message, AckReplyConsumer consumer) {
        try {
            BatchLinesCompletedEvent event = objectMapper.readValue(message.getData().toStringUtf8(), BatchLinesCompletedEvent.class);
            billingService.processBatchLinesCompleted(event);
            consumer.ack();
        } catch (Exception ex) {
            LOG.error("Error procesando BATCH_LINES_COMPLETED desde Pub/Sub. messageId={}", message.getMessageId(), ex);
            consumer.nack();
        }
    }
}
