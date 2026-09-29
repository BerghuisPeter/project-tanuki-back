package io.github.peterberghuis.goshuin.listener;

import io.github.peterberghuis.gcp.config.GcpPubSubProperties;
import io.github.peterberghuis.gcp.pubsub.PubSubPublisherService;
import io.github.peterberghuis.goshuin.event.EnrichmentJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrichmentJobEventListener {

    private final PubSubPublisherService pubSubPublisherService;
    private final GcpPubSubProperties pubSubProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEnrichmentJobCreated(EnrichmentJobCreatedEvent event) {
        try {
            String jsonPayload = String.format(
                    "{\"jobId\":\"%s\",\"resourceType\":\"%s\",\"resourceId\":\"%s\"}",
                    event.jobId(),
                    event.resourceType().name(),
                    event.resourceId()
            );

            Map<String, String> attributes = Map.of(
                    "jobId", event.jobId().toString(),
                    "resourceType", event.resourceType().name(),
                    "resourceId", event.resourceId().toString()
            );

            String topicName = pubSubProperties.getTopicName() != null && !pubSubProperties.getTopicName().isBlank()
                    ? pubSubProperties.getTopicName()
                    : "enrichment-requests";

            pubSubPublisherService.publish(topicName, jsonPayload, attributes);
            log.info("Dispatched enrichment job event to topic {}: jobId={}, resourceType={}, resourceId={}",
                    topicName, event.jobId(), event.resourceType(), event.resourceId());
        } catch (Exception e) {
            log.error("Failed to publish enrichment job event for jobId {}: {}", event.jobId(), e.getMessage(), e);
        }
    }
}
