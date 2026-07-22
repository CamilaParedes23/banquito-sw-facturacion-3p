package com.banquito.switchpagos.billing.service.impl;

import com.banquito.switchpagos.billing.dto.event.BillingCompletedEvent;
import com.banquito.switchpagos.billing.service.BillingCompletedEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "pubsub")
public class PubSubBillingCompletedEventPublisher implements BillingCompletedEventPublisher {

    private final ObjectMapper objectMapper;
    private final String projectId;
    private final String topicName;
    private final String schemaVersion;
    private Publisher publisher;

    public PubSubBillingCompletedEventPublisher(
            ObjectMapper objectMapper,
            @Value("${pubsub.project-id}") String projectId,
            @Value("${pubsub.topic.billing}") String topicName,
            @Value("${pubsub.schema-version}") String schemaVersion) {
        this.objectMapper = objectMapper;
        this.projectId = projectId;
        this.topicName = topicName;
        this.schemaVersion = schemaVersion;
    }

    @PostConstruct
    public void start() throws Exception {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT es obligatorio cuando MESSAGING_PROVIDER=pubsub");
        }
        publisher = Publisher.newBuilder(ProjectTopicName.of(projectId, topicName)).build();
    }

    @PreDestroy
    public void stop() throws Exception {
        if (publisher != null) {
            publisher.shutdown();
            publisher.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void publish(BillingCompletedEvent event) {
        try {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("eventType", "BILLING_COMPLETED");
            attributes.put("sourceService", "billing-service");
            attributes.put("schemaVersion", schemaVersion);
            attributes.put("correlationId", event.getCorrelationId().toString());
            attributes.put("batchId", event.getBatchId().toString());
            PubsubMessage message = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(objectMapper.writeValueAsString(event)))
                    .putAllAttributes(attributes)
                    .build();
            publisher.publish(message).get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo publicar BILLING_COMPLETED en Pub/Sub", ex);
        }
    }
}
